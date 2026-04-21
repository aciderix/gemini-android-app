package com.gemini.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gemini.app.R
import com.gemini.app.ui.settings.SettingsSheet
import com.gemini.app.ui.settings.TermuxSetupDialog
import com.gemini.app.ui.settings.ThemeMode
import com.gemini.domain.GeminiMessage
import com.gemini.domain.MessageRole
import com.gemini.ui.LocalGeminiColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {}
) {
    var textState by remember { mutableStateOf("") }
    var showActions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showChats by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showTermuxGuide by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!viewModel.isTermuxGuideShown()) {
            showTermuxGuide = true
            viewModel.markTermuxGuideShown()
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val messages = viewModel.messages
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val pendingCall by viewModel.pendingCall.collectAsState()
    val model by viewModel.model.collectAsState()
    val workspaceLabel by viewModel.workspaceLabel.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    val tokenUsage by viewModel.tokenUsage.collectAsState()
    val compressing by viewModel.compressing.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachImageFromUri(context, uri)
    }

    val listState = rememberLazyListState()
    val isNearBottom by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            last == null || last.index >= layout.totalItemsCount - 2
        }
    }

    // Only auto-scroll when the user is already near the bottom — otherwise
    // they get yanked out of whatever they were reading. The streamed text of
    // the last message grows without changing `messages.size`, so include it
    // as a key to keep the view pinned to the bottom during streaming.
    val tailText = messages.lastOrNull()?.text
    LaunchedEffect(messages.size, tailText, thinking, error) {
        if (isNearBottom) {
            val target = messages.size - 1 + extraTail(thinking, error)
            if (target >= 0) listState.animateScrollToItem(target)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawer(
                    viewModel = viewModel,
                    onClose = { scope.launch { drawerState.close() } },
                    onOpenSettings = { showSettings = true },
                    onOpenChats = { showChats = true },
                    onOpenAbout = { showAbout = true }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_gemini),
                                contentDescription = "Gemini",
                                modifier = Modifier.height(28.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    model,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        workspaceLabel.substringAfterLast('/'),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val tokenLabel = formatTokens(tokenUsage.total, tokenUsage.limit)
                                    if (tokenLabel != null) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "•",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            tokenLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                BottomChatBar(
                    text = textState,
                    enabled = !isLoading && pendingCall == null,
                    isLoading = isLoading,
                    attachments = pendingAttachments,
                    onTextChange = { textState = it },
                    onAddClick = { showActions = true },
                    onAttachImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onRemoveAttachment = { id -> viewModel.removeAttachment(id) },
                    onSend = {
                        if (textState.isNotBlank() || pendingAttachments.isNotEmpty()) {
                            viewModel.sendMessage(textState)
                            textState = ""
                        }
                    },
                    onStop = { viewModel.cancelSend() }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
                AnimatedVisibility(
                    visible = compressing,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    CompressingBanner()
                }
                if (messages.isEmpty()) {
                    EmptyState(onSuggestion = { suggestion ->
                        viewModel.sendMessage(suggestion)
                    })
                } else {
                    ChatList(
                        messages = messages,
                        thinking = thinking,
                        error = error,
                        listState = listState,
                        onCopy = { text -> copyText(context, text) },
                        onRegenerate = { viewModel.regenerateLast() },
                        onResend = { text ->
                            textState = text
                        },
                        onRetry = {
                            viewModel.clearError()
                            viewModel.regenerateLast()
                        },
                        onDismissError = { viewModel.clearError() }
                    )
                }

                if (!isNearBottom && messages.size > 1) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(messages.size - 1) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to latest")
                    }
                }

                pendingCall?.let { call ->
                    ToolApprovalCard(
                        name = call.name,
                        arguments = call.arguments,
                        onApprove = { viewModel.approve(call.id, always = false) },
                        onAlwaysApprove = { viewModel.approve(call.id, always = true) },
                        onReject = { viewModel.reject(call.id) }
                    )
                }

                if (showActions) {
                    QuickActionsSheet(
                        viewModel = viewModel,
                        onDismiss = { showActions = false }
                    )
                }

                if (showSettings) {
                    SettingsSheet(
                        viewModel = viewModel,
                        onDismiss = { showSettings = false },
                        themeMode = themeMode,
                        onThemeChange = onThemeChange
                    )
                }

                if (showChats) {
                    ChatsDialog(
                        viewModel = viewModel,
                        onDismiss = { showChats = false },
                        onResumed = { showChats = false }
                    )
                }

                if (showAbout) {
                    AboutDialog(
                        viewModel = viewModel,
                        onDismiss = { showAbout = false }
                    )
                }

                if (showTermuxGuide) {
                    TermuxSetupDialog(
                        viewModel = viewModel,
                        onDismiss = { showTermuxGuide = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompressingBanner() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulsingDots()
            Spacer(Modifier.width(10.dp))
            Text(
                "Compressing conversation…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun formatTokens(total: Int, limit: Int?): String? {
    if (total <= 0) return null
    val head = formatTokenNumber(total)
    return if (limit != null && limit > 0) "$head / ${formatTokenNumber(limit)}" else head
}

private fun formatTokenNumber(n: Int): String = when {
    n >= 1_000_000 -> {
        val m = n / 1_000_000f
        if (m >= 10f) "${m.toInt()}M" else String.format("%.1fM", m)
    }
    n >= 1_000 -> "${n / 1_000}k"
    else -> n.toString()
}

private val STARTER_CHIPS = listOf(
    "List the files at the workspace root" to "📁",
    "Create a README.md describing this project" to "📝",
    "Find TODOs in the code" to "🔎",
    "Run `uname -a` in Termux" to "💻"
)

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    val dusk = LocalGeminiColors.current.duskGradient
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Ambient halo — the only place (with Login) where gradients are allowed.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.TopCenter)
                .background(dusk)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "Ready when you are",
                style = MaterialTheme.typography.headlineMedium,
                color = LocalGeminiColors.current.textStrong
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Gemini can read, write, search or run shell commands. " +
                    "Pick a starter or type your own message.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                STARTER_CHIPS.forEach { (label, emoji) ->
                    SuggestionChip(
                        onClick = { onSuggestion(label) },
                        label = { Text("$emoji  $label") },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatList(
    messages: List<GeminiMessage>,
    thinking: String?,
    error: String?,
    listState: LazyListState,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onResend: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit
) {
    val grouped = groupMessages(messages)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(grouped, key = { it.key }) { item ->
            when (item) {
                is ChatItem.User -> MessageBubble(
                    message = item.message,
                    onCopy = onCopy,
                    onResend = { onResend(item.message.text) }
                )
                is ChatItem.Model -> MessageBubble(
                    message = item.message,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate
                )
                is ChatItem.Tool -> ToolBubble(
                    call = item.call,
                    result = item.result,
                    onCopy = onCopy
                )
            }
        }
        if (thinking != null) item("thinking") { ThinkingBubble(thinking) }
        if (error != null) item("error") {
            ErrorBubble(error, onRetry = onRetry, onDismiss = onDismissError)
        }
    }
}

private sealed class ChatItem {
    abstract val key: String
    data class User(val message: GeminiMessage) : ChatItem() {
        override val key = "u-${message.id}"
    }
    data class Model(val message: GeminiMessage) : ChatItem() {
        override val key = "m-${message.id}"
    }
    data class Tool(val call: GeminiMessage, val result: GeminiMessage?) : ChatItem() {
        override val key = "t-${call.id}"
    }
}

private fun groupMessages(messages: List<GeminiMessage>): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        when {
            m.role == MessageRole.TOOL && m.toolCall != null -> {
                val call = m.toolCall!!
                val next = messages.getOrNull(i + 1)
                val pairedResult = if (next != null
                    && next.role == MessageRole.TOOL
                    && next.toolResult?.callId == call.id
                ) next else null
                out += ChatItem.Tool(m, pairedResult)
                i += if (pairedResult != null) 2 else 1
            }
            m.role == MessageRole.TOOL && m.toolResult != null -> {
                out += ChatItem.Tool(m, m)
                i++
            }
            m.isUser || m.role == MessageRole.USER -> {
                out += ChatItem.User(m); i++
            }
            else -> { out += ChatItem.Model(m); i++ }
        }
    }
    return out
}

private fun extraTail(thinking: String?, error: String?): Int {
    var n = 0
    if (thinking != null) n++
    if (error != null) n++
    return n
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: GeminiMessage,
    onCopy: (String) -> Unit = {},
    onRegenerate: (() -> Unit)? = null,
    onResend: (() -> Unit)? = null
) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    // AI Studio convention: user turn = tonal-blue pill, model turn = no fill
    // (text flows on the canvas), tool result = card (handled elsewhere).
    val container = if (message.isUser) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent
    val content = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onBackground
    val shape = if (message.isUser) MaterialTheme.shapes.extraLarge
        else MaterialTheme.shapes.medium
    var menuOpen by remember(message.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = container,
            shape = shape,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { menuOpen = true }
            )
        ) {
            Box(
                modifier = Modifier.padding(
                    horizontal = if (message.isUser) 16.dp else 4.dp,
                    vertical = if (message.isUser) 10.dp else 4.dp
                )
            ) {
                if (message.isUser) {
                    Column {
                        if (message.attachmentPaths.isNotEmpty()) {
                            AttachmentThumbnails(message.attachmentPaths)
                            Spacer(Modifier.height(6.dp))
                        }
                        val visibleText = if (message.attachmentPaths.isNotEmpty() &&
                            message.text.startsWith("📎 ")) {
                            // Drop the "📎 image (png, 128KB)\n" prefix added by
                            // RestGeminiCore — the thumbnail already conveys it.
                            message.text.substringAfter('\n', "")
                        } else message.text
                        if (visibleText.isNotBlank()) {
                            Text(visibleText, color = content)
                        }
                    }
                } else {
                    MarkdownText(text = message.text, color = content)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        text = { Text("Copy") },
                        onClick = { onCopy(message.text); menuOpen = false }
                    )
                    if (onRegenerate != null) DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Autorenew, null) },
                        text = { Text("Regenerate") },
                        onClick = { onRegenerate(); menuOpen = false }
                    )
                    if (onResend != null) DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        text = { Text("Resend") },
                        onClick = { onResend(); menuOpen = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolBubble(
    call: GeminiMessage,
    result: GeminiMessage?,
    onCopy: (String) -> Unit = {}
) {
    val res = result?.toolResult
    val pending = res == null
    val ok = res?.ok ?: true
    val accent = when {
        pending -> MaterialTheme.colorScheme.primary
        ok -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val name = call.toolCall?.name ?: res?.callId ?: "tool"
    val rawBody = result?.text ?: call.text
    val (headLine, diffBody) = remember(rawBody) { extractDiff(rawBody) }
    val preview = when {
        pending -> call.text.lineSequence().firstOrNull().orEmpty().take(80)
        diffBody != null -> headLine.lineSequence().firstOrNull().orEmpty().take(80)
        else -> {
            val first = headLine.lineSequence()
                .firstOrNull { it.isNotBlank() && !it.startsWith("---") }
                .orEmpty().take(80)
            if (first.isNotBlank()) first else if (ok) "ok" else "failed"
        }
    }
    var expanded by remember(call.id) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = { onCopy(rawBody) }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (pending) "$name — running…" else name,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    if (preview.isNotBlank()) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (headLine.isNotBlank()) {
                        Text(
                            text = headLine,
                            style = MaterialTheme.typography.bodySmall
                                .copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    if (diffBody != null) {
                        DiffView(diffBody)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulsingDots()
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PulsingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing)
        ),
        label = "phase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val offset = i.toFloat() * 0.2f
            val t = ((phase - offset) % 1f + 1f) % 1f
            val alpha = 0.25f + 0.75f * kotlin.math.abs(1f - 2f * t)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

@Composable
private fun ErrorBubble(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                MaterialTheme.shapes.medium)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error)
            )
            Column(modifier = Modifier.padding(14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ErrorOutline, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Request failed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Retry")
                    }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

private fun copyText(context: android.content.Context, text: String) {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("chat", text))
    android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun ToolApprovalCard(
    name: String,
    arguments: Map<String, Any?>,
    onApprove: () -> Unit,
    onAlwaysApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.tertiary)
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Gemini wants to run: $name",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.size(6.dp))
                arguments.forEach { (k, v) ->
                    val str = v?.toString().orEmpty()
                    val rendered = if (str.length > 200) str.substring(0, 200) + "…" else str
                    Text(
                        "$k: $rendered",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("Reject") }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(4.dp)); Text("Approve") }
                }
                Spacer(Modifier.size(4.dp))
                AssistChip(
                    onClick = onAlwaysApprove,
                    label = { Text("Always approve this session") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun BottomChatBar(
    text: String,
    enabled: Boolean,
    isLoading: Boolean,
    attachments: List<PendingAttachment>,
    onTextChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onAttachImage: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    // AI Studio composer: one rounded pill, [+] on the left, text, and a
    // small circular send/stop button nested on the right — no separate FAB.
    // The pill sits on the canvas with a subtle outlineVariant stroke.
    val hasText = text.isNotBlank()
    val canSubmit = hasText || attachments.isNotEmpty()
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (attachments.isNotEmpty()) {
                AttachmentStrip(
                    attachments = attachments,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraLarge,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        IconButton(onClick = onAddClick, enabled = !isLoading) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Quick actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onAttachImage, enabled = !isLoading) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "Attach image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier.weight(1f),
                            enabled = enabled,
                            placeholder = {
                                Text(
                                    "Message Gemini…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            maxLines = 6,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        val canSend = enabled && canSubmit
                        val sendBg = when {
                            isLoading -> MaterialTheme.colorScheme.error
                            canSend -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val sendFg = when {
                            isLoading -> MaterialTheme.colorScheme.onError
                            canSend -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                        Surface(
                            color = sendBg,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.size(40.dp)
                        ) {
                            IconButton(
                                onClick = if (isLoading) onStop else onSend,
                                enabled = isLoading || canSend
                            ) {
                                Icon(
                                    imageVector = if (isLoading) Icons.Default.Stop else Icons.Default.Send,
                                    contentDescription = if (isLoading) "Stop" else "Send",
                                    tint = sendFg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<PendingAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
    ) {
        items(attachments, key = { it.id }) { att ->
            AttachmentChip(attachment = att, onRemove = { onRemove(att.id) })
        }
    }
}

@Composable
private fun AttachmentChip(attachment: PendingAttachment, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    attachment.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    formatBytes(attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatBytes(size: Int): String = when {
    size >= 1_000_000 -> String.format("%.1f MB", size / 1_000_000f)
    size >= 1_000 -> "${size / 1_000} KB"
    else -> "$size B"
}

// Small thumbnails of sent image attachments. Decodes each file once via a
// sampled BitmapFactory to keep memory low (targets ~320 px). If the file no
// longer exists (cache cleared, sideload restore), the slot is skipped.
@Composable
private fun AttachmentThumbnails(paths: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        paths.forEach { path ->
            val bitmap = remember(path) { decodeThumbnail(path) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
        }
    }
}

private fun decodeThumbnail(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    val file = java.io.File(path)
    if (!file.exists()) return null
    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, opts)
    val target = 320
    var sample = 1
    while (opts.outWidth / sample > target && opts.outHeight / sample > target) sample *= 2
    val load = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = android.graphics.BitmapFactory.decodeFile(path, load) ?: return null
    return bmp.asImageBitmap()
}
