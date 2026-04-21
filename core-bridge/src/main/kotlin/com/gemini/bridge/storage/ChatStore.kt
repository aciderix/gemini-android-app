package com.gemini.bridge.storage

import android.content.Context
import com.gemini.domain.GeminiMessage
import com.gemini.domain.MessageRole
import com.gemini.domain.ToolCall
import com.gemini.domain.ToolCallResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists conversations as JSON under <filesDir>/chats/<slug>.json.
 * Stores both the Gemini API turns (so the model keeps context on resume) and
 * the user-facing message list.
 */
class ChatStore(context: Context) {

    private val root: File = File(context.filesDir, "chats").also { it.mkdirs() }
    // Single slot used by the auto-save feature; kept outside `root` so it
    // cannot collide with a user-named chat and is not listed in list().
    private val currentFile: File = File(context.filesDir, "chat-current.json")

    data class Snapshot(
        val turns: List<JSONObject>,
        val messages: List<GeminiMessage>
    )

    data class Entry(val name: String, val updatedAt: Long, val messageCount: Int)

    fun list(): List<Entry> = root.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".json") }
        ?.mapNotNull { f ->
            runCatching {
                val root = JSONObject(f.readText())
                Entry(
                    name = f.nameWithoutExtension,
                    updatedAt = f.lastModified(),
                    messageCount = root.optJSONArray("messages")?.length() ?: 0
                )
            }.getOrNull()
        }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    fun save(name: String, snapshot: Snapshot) {
        val safe = slug(name)
        if (safe.isBlank()) return
        val turnsJson = JSONArray().apply { snapshot.turns.forEach { put(it) } }
        val msgsJson = JSONArray().apply {
            snapshot.messages.forEach { put(messageToJson(it)) }
        }
        val root = JSONObject()
            .put("name", safe)
            .put("savedAt", System.currentTimeMillis())
            .put("turns", turnsJson)
            .put("messages", msgsJson)
        File(this.root, "$safe.json").writeText(root.toString())
    }

    fun load(name: String): Snapshot? {
        val safe = slug(name)
        val f = File(root, "$safe.json")
        if (!f.exists()) return null
        val obj = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return null
        val turnsArr = obj.optJSONArray("turns") ?: JSONArray()
        val turns = (0 until turnsArr.length()).map { turnsArr.getJSONObject(it) }
        val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until msgsArr.length()).mapNotNull {
            messageFromJson(msgsArr.getJSONObject(it))
        }
        return Snapshot(turns, messages)
    }

    fun delete(name: String): Boolean {
        val safe = slug(name)
        return File(root, "$safe.json").delete()
    }

    fun saveCurrent(snapshot: Snapshot) {
        if (snapshot.messages.isEmpty() && snapshot.turns.isEmpty()) {
            clearCurrent(); return
        }
        val turnsJson = JSONArray().apply { snapshot.turns.forEach { put(it) } }
        val msgsJson = JSONArray().apply {
            snapshot.messages.forEach { put(messageToJson(it)) }
        }
        val root = JSONObject()
            .put("savedAt", System.currentTimeMillis())
            .put("turns", turnsJson)
            .put("messages", msgsJson)
        runCatching { currentFile.writeText(root.toString()) }
    }

    fun loadCurrent(): Snapshot? {
        if (!currentFile.exists()) return null
        val obj = runCatching { JSONObject(currentFile.readText()) }.getOrNull() ?: return null
        val turnsArr = obj.optJSONArray("turns") ?: JSONArray()
        val turns = (0 until turnsArr.length()).map { turnsArr.getJSONObject(it) }
        val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until msgsArr.length()).mapNotNull {
            messageFromJson(msgsArr.getJSONObject(it))
        }
        if (turns.isEmpty() && messages.isEmpty()) return null
        return Snapshot(turns, messages)
    }

    fun clearCurrent() {
        runCatching { currentFile.delete() }
    }

    private fun messageToJson(m: GeminiMessage): JSONObject {
        val json = JSONObject()
            .put("id", m.id)
            .put("text", m.text)
            .put("isUser", m.isUser)
            .put("timestamp", m.timestamp)
            .put("role", m.role.name)
        m.toolCall?.let { tc ->
            json.put(
                "toolCall",
                JSONObject()
                    .put("id", tc.id)
                    .put("name", tc.name)
                    .put("arguments", JSONObject(tc.arguments as Map<*, *>))
            )
        }
        m.toolResult?.let { tr ->
            json.put(
                "toolResult",
                JSONObject()
                    .put("callId", tr.callId)
                    .put("ok", tr.ok)
                    .put("output", tr.output)
            )
        }
        if (m.attachmentPaths.isNotEmpty()) {
            json.put(
                "attachmentPaths",
                JSONArray().apply { m.attachmentPaths.forEach { put(it) } }
            )
        }
        return json
    }

    @Suppress("UNCHECKED_CAST")
    private fun messageFromJson(o: JSONObject): GeminiMessage? {
        val id = o.optString("id").ifBlank { return null }
        val role = runCatching { MessageRole.valueOf(o.optString("role", "USER")) }
            .getOrDefault(MessageRole.USER)
        val toolCall = o.optJSONObject("toolCall")?.let { tc ->
            ToolCall(
                id = tc.optString("id"),
                name = tc.optString("name"),
                arguments = jsonToMap(tc.optJSONObject("arguments") ?: JSONObject())
            )
        }
        val toolResult = o.optJSONObject("toolResult")?.let { tr ->
            ToolCallResult(
                callId = tr.optString("callId"),
                ok = tr.optBoolean("ok", true),
                output = tr.optString("output")
            )
        }
        val attachmentPaths = o.optJSONArray("attachmentPaths")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } ?: emptyList()
        return GeminiMessage(
            id = id,
            text = o.optString("text"),
            isUser = o.optBoolean("isUser", false),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            role = role,
            toolCall = toolCall,
            toolResult = toolResult,
            attachmentPaths = attachmentPaths
        )
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        for (key in obj.keys()) {
            out[key] = when (val v = obj.get(key)) {
                is JSONObject -> jsonToMap(v)
                is JSONArray -> (0 until v.length()).map { v.get(it) }
                JSONObject.NULL -> null
                else -> v
            }
        }
        return out
    }

    private fun slug(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-").take(64)
}
