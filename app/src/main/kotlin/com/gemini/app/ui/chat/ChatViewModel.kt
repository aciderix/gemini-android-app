package com.gemini.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemini.bridge.Attachment
import com.gemini.bridge.RestGeminiCore
import com.gemini.domain.GeminiEvent
import com.gemini.domain.GeminiMessage
import com.gemini.domain.GeminiResult
import com.gemini.domain.MessageRole
import com.gemini.domain.ToolCall
import com.gemini.domain.ToolDecision
import com.gemini.domain.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(private val core: RestGeminiCore) : ViewModel() {

    private val _messages = mutableStateListOf<GeminiMessage>()
    val messages: List<GeminiMessage> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pendingCall = MutableStateFlow<ToolCall?>(null)
    val pendingCall: StateFlow<ToolCall?> = _pendingCall.asStateFlow()

    private val _model = MutableStateFlow(core.currentModel())
    val model: StateFlow<String> = _model.asStateFlow()

    private val _autoApprove = MutableStateFlow(core.isAutoApprove())
    val autoApprove: StateFlow<Boolean> = _autoApprove.asStateFlow()

    private val _workspaceLabel = MutableStateFlow(core.workspace.rootLabel())
    val workspaceLabel: StateFlow<String> = _workspaceLabel.asStateFlow()

    private val _workspacePath = MutableStateFlow(core.workspace.absolutePath())
    val workspacePath: StateFlow<String?> = _workspacePath.asStateFlow()

    private val _workspaceReason = MutableStateFlow(core.workspace.unreachableReason())
    val workspaceReason: StateFlow<String?> = _workspaceReason.asStateFlow()

    private val _availableModels = MutableStateFlow(core.listModels())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _thinking = MutableStateFlow<String?>(null)
    val thinking: StateFlow<String?> = _thinking.asStateFlow()

    private val _tokenUsage = MutableStateFlow(
        TokenUsageState(core.currentTokenUsage().first, core.currentTokenUsage().second)
    )
    val tokenUsage: StateFlow<TokenUsageState> = _tokenUsage.asStateFlow()

    private val _autoCompressEnabled = MutableStateFlow(core.isAutoCompressEnabled())
    val autoCompressEnabled: StateFlow<Boolean> = _autoCompressEnabled.asStateFlow()

    private val _autoCompressThreshold = MutableStateFlow(core.autoCompressThreshold())
    val autoCompressThreshold: StateFlow<Float> = _autoCompressThreshold.asStateFlow()

    private val _compressing = MutableStateFlow(false)
    val compressing: StateFlow<Boolean> = _compressing.asStateFlow()

    private val _autoSaveEnabled = MutableStateFlow(core.isAutoSaveEnabled())
    val autoSaveEnabled: StateFlow<Boolean> = _autoSaveEnabled.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()

    private var sendJob: Job? = null
    private var lastUserPrompt: String? = null

    val availableTools: List<ToolSpec> get() = core.availableTools()
    val termuxInstalled: Boolean get() = core.termux.isInstalled()

    fun isTermuxGuideShown(): Boolean = core.isTermuxGuideShown()
    fun markTermuxGuideShown() = core.markTermuxGuideShown()

    suspend fun testTermuxShell(): String {
        val r = core.termux.run("echo hello from termux && uname -a")
        return if (r.ok) r.stdout.ifBlank { "ok" }
        else buildString {
            append("exit=").append(r.exitCode)
            if (r.stderr.isNotBlank()) append('\n').append(r.stderr)
        }
    }

    init {
        viewModelScope.launch {
            core.events.collect { ev ->
                when (ev) {
                    is GeminiEvent.MessageAdded -> _messages.add(ev.message)
                    is GeminiEvent.MessageUpdated -> {
                        val idx = _messages.indexOfLast { it.id == ev.message.id }
                        if (idx >= 0) _messages[idx] = ev.message else _messages.add(ev.message)
                    }
                    is GeminiEvent.ToolCallPending -> _pendingCall.value = ev.call
                    is GeminiEvent.ToolCallCompleted -> {
                        if (_pendingCall.value?.id == ev.result.callId) _pendingCall.value = null
                    }
                    is GeminiEvent.Thinking -> _thinking.value = ev.label
                    is GeminiEvent.Notice -> _error.value = ev.message
                    is GeminiEvent.TokenUsage ->
                        _tokenUsage.value = TokenUsageState(ev.total, ev.limit)
                }
            }
        }
    }

    fun initCore(config: Map<String, Any>) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = core.init(config)) {
                is GeminiResult.Success -> {
                    _isReady.value = true
                    _model.value = core.currentModel()
                    _availableModels.value = core.listModels()
                    // Resume the previous conversation if autosave is on and the
                    // in-memory session is still empty.
                    if (core.isAutoSaveEnabled() && _messages.isEmpty()) {
                        core.resumeCurrentSession()
                    }
                }
                is GeminiResult.Error -> _error.value = result.message
            }
            _isLoading.value = false
        }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        core.setAutoSaveEnabled(enabled)
        _autoSaveEnabled.value = enabled
    }

    fun refreshModels() {
        viewModelScope.launch {
            _availableModels.value = core.refreshModels()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            core.signOut()
            _messages.clear()
            _pendingAttachments.value = emptyList()
            _model.value = core.currentModel()
            _availableModels.value = core.listModels()
            _isReady.value = false
        }
    }

    fun hasPersistedSession(): Boolean = core.hasPersistedSession()

    fun tryAutoLogin() {
        val saved = core.persistedApiKey() ?: return
        if (saved.isBlank()) return
        initCore(mapOf("api_key" to saved, "remember" to true))
    }

    // --- chat persistence ---
    fun listSavedChats(): List<com.gemini.bridge.storage.ChatStore.Entry> = core.listChats()
    fun saveChat(name: String) { core.saveChat(name) }
    fun deleteChat(name: String) { core.deleteChat(name) }
    fun resumeChat(name: String) {
        viewModelScope.launch {
            _messages.clear()
            val ok = core.resumeChat(name)
            if (!ok) _error.value = "Could not resume \"$name\""
        }
    }

    fun sendMessage(text: String) {
        val attachments = _pendingAttachments.value
        if (text.isBlank() && attachments.isEmpty()) return
        if (sendJob?.isActive == true) return
        lastUserPrompt = text
        val payload = attachments.map { Attachment(it.bytes, it.mimeType, it.localPath) }
        _pendingAttachments.value = emptyList()
        sendJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = if (payload.isEmpty()) core.sendMessage(text)
                    else core.sendMessage(text, payload)
                when (result) {
                    is GeminiResult.Success -> {}
                    is GeminiResult.Error -> _error.value = result.message
                }
            } catch (_: CancellationException) {
                // Cancelled by user — silent.
            } finally {
                _isLoading.value = false
                _thinking.value = null
                sendJob = null
                maybeAutoCompress()
            }
        }
    }

    fun attachImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { readAttachment(context, uri) }
            if (loaded == null) {
                _error.value = "Could not read the selected image"
                return@launch
            }
            if (loaded.bytes.size > MAX_ATTACHMENT_BYTES) {
                _error.value = "Image too large (max 15 MB)"
                return@launch
            }
            _pendingAttachments.value = _pendingAttachments.value + loaded
        }
    }

    fun removeAttachment(id: String) {
        _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == id }
    }

    fun clearAttachments() { _pendingAttachments.value = emptyList() }

    private fun readAttachment(context: Context, uri: Uri): PendingAttachment? {
        return runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/*"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            val displayName = queryDisplayName(context, uri)
                ?: "image.${mime.substringAfter('/').take(4)}"
            val id = "att-${System.nanoTime()}"
            val localPath = persistAttachment(context, id, bytes, mime)
            PendingAttachment(
                id = id,
                bytes = bytes,
                mimeType = mime,
                displayName = displayName.take(40),
                sizeBytes = bytes.size,
                localPath = localPath
            )
        }.getOrNull()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    // Copy the picked image into app-owned cache so the chat bubble can show a
    // thumbnail without holding an Android content:// permission that may be
    // revoked, and so reloads from ChatStore can still find the file.
    private fun persistAttachment(
        context: Context,
        id: String,
        bytes: ByteArray,
        mime: String
    ): String? = runCatching {
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("heic") -> "heic"
            mime.contains("heif") -> "heif"
            else -> "jpg"
        }
        val dir = java.io.File(context.filesDir, "attachments").also { it.mkdirs() }
        val file = java.io.File(dir, "$id.$ext")
        file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()

    private fun maybeAutoCompress() {
        if (!_autoCompressEnabled.value) return
        if (_compressing.value) return
        val (total, limit) = _tokenUsage.value.let { it.total to it.limit }
        if (limit == null || limit <= 0 || total <= 0) return
        val ratio = total.toFloat() / limit.toFloat()
        if (ratio < _autoCompressThreshold.value) return
        compressSession(auto = true)
    }

    fun cancelSend() {
        sendJob?.cancel()
        sendJob = null
        _isLoading.value = false
        _thinking.value = null
        _pendingCall.value = null
    }

    fun regenerateLast() {
        val prompt = lastUserPrompt ?: return
        if (sendJob?.isActive == true) return
        // Drop the last model/tool bubbles so the re-run looks fresh.
        while (_messages.isNotEmpty() && _messages.last().role != MessageRole.USER) {
            _messages.removeAt(_messages.size - 1)
        }
        if (_messages.isNotEmpty() && _messages.last().role == MessageRole.USER) {
            _messages.removeAt(_messages.size - 1)
        }
        sendMessage(prompt)
    }

    fun approve(callId: String, always: Boolean) {
        val decision = if (always) ToolDecision.AlwaysApprove else ToolDecision.Approve
        _pendingCall.value = null
        viewModelScope.launch {
            core.resolveToolDecision(callId, decision)
            if (always) _autoApprove.value = true
        }
    }

    fun reject(callId: String, reason: String = "user declined") {
        _pendingCall.value = null
        viewModelScope.launch {
            core.resolveToolDecision(callId, ToolDecision.Reject(reason))
        }
    }

    fun resetSession() {
        viewModelScope.launch {
            core.resetSession()
            _messages.clear()
        }
    }

    fun clearMessages() { _messages.clear() }

    fun setModel(model: String) {
        core.setModel(model)
        _model.value = core.currentModel()
    }

    fun setAutoApprove(enabled: Boolean) {
        core.setAutoApprove(enabled)
        _autoApprove.value = enabled
    }

    fun setProjectFolder(uri: String) {
        viewModelScope.launch {
            when (val r = core.setProjectFolder(uri)) {
                is GeminiResult.Error -> _error.value = r.message
                is GeminiResult.Success -> {
                    _workspaceLabel.value = core.workspace.rootLabel()
                    _workspacePath.value = core.workspace.absolutePath()
                    _workspaceReason.value = core.workspace.unreachableReason()
                }
            }
        }
    }

    fun clearError() { _error.value = null }

    fun lastAssistantText(): String? =
        _messages.lastOrNull { it.role == MessageRole.MODEL && !it.text.isBlank() }?.text

    fun stats(): ChatStats {
        val userCount = _messages.count { it.role == MessageRole.USER }
        val modelCount = _messages.count { it.role == MessageRole.MODEL }
        val toolCount = _messages.count { it.role == MessageRole.TOOL }
        val chars = _messages.sumOf { it.text.length }
        return ChatStats(userCount, modelCount, toolCount, chars)
    }

    fun compressSession(auto: Boolean = false) {
        if (_compressing.value) return
        viewModelScope.launch {
            val snapshot = _messages.toList()
            if (snapshot.isEmpty()) return@launch
            _compressing.value = true
            try {
                val prompt = buildString {
                    append("Summarise the following conversation. ")
                    append("Keep the key decisions, open questions, and file changes. Output 10 bullets max.\n\n")
                    snapshot.forEach { msg ->
                        when (msg.role) {
                            MessageRole.USER -> append("[user] ").append(msg.text).append('\n')
                            MessageRole.MODEL -> append("[assistant] ").append(msg.text).append('\n')
                            else -> Unit
                        }
                    }
                }
                core.resetSession()
                _messages.clear()
                // Inline call instead of sendMessage() to avoid re-entering
                // auto-compress on the summary response.
                _isLoading.value = true
                try {
                    when (val r = core.sendMessage(prompt)) {
                        is GeminiResult.Success -> {}
                        is GeminiResult.Error -> _error.value = r.message
                    }
                } catch (_: CancellationException) {
                    // silent
                } finally {
                    _isLoading.value = false
                    _thinking.value = null
                }
            } finally {
                _compressing.value = false
            }
        }
    }

    fun setAutoCompressEnabled(enabled: Boolean) {
        core.setAutoCompressEnabled(enabled)
        _autoCompressEnabled.value = enabled
    }

    fun setAutoCompressThreshold(fraction: Float) {
        core.setAutoCompressThreshold(fraction)
        _autoCompressThreshold.value = core.autoCompressThreshold()
    }
}

private const val MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024

data class PendingAttachment(
    val id: String,
    val bytes: ByteArray,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Int,
    val localPath: String? = null
) {
    override fun equals(other: Any?) = other is PendingAttachment && id == other.id
    override fun hashCode() = id.hashCode()
}

data class TokenUsageState(val total: Int, val limit: Int?)

fun ChatViewModel.exportAsMarkdown(): String {
    val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        .format(java.util.Date())
    val sb = StringBuilder()
    sb.append("# Gemini conversation\n\n")
    sb.append("_Model: ").append(model.value).append("_  \n")
    sb.append("_Exported: ").append(ts).append("_\n\n")
    messages.forEach { msg ->
        when (msg.role) {
            MessageRole.USER -> {
                sb.append("## User\n\n").append(msg.text.trim()).append("\n\n")
            }
            MessageRole.MODEL -> {
                sb.append("## Gemini\n\n").append(msg.text.trim()).append("\n\n")
            }
            MessageRole.TOOL -> {
                msg.toolCall?.let { call ->
                    sb.append("### Tool · ").append(call.name).append("\n\n")
                    call.arguments.forEach { (k, v) ->
                        val s = v?.toString().orEmpty().take(400)
                        sb.append("- **").append(k).append("**: `")
                            .append(s.replace("`", "\\`")).append("`\n")
                    }
                    sb.append('\n')
                }
                msg.toolResult?.let { res ->
                    val status = if (res.ok) "✅" else "❌"
                    sb.append("**Result** ").append(status).append("\n\n")
                    sb.append("```\n").append(res.output.trim()).append("\n```\n\n")
                }
            }
            MessageRole.SYSTEM -> Unit
        }
    }
    return sb.toString()
}

data class ChatStats(
    val userMessages: Int,
    val modelMessages: Int,
    val toolEvents: Int,
    val totalChars: Int
)
