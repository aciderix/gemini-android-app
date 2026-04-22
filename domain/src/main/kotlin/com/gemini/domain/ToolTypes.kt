package com.gemini.domain

/**
 * Declarative description of a tool the model is allowed to call. The schema
 * is a JSON-Schema-like map that will be serialized into Gemini's function
 * declaration payload.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val category: ToolCategory,
    val destructive: Boolean,
    val parameters: Map<String, Any?>
)

enum class ToolCategory { FILES, SEARCH, SHELL, MEMORY, OTHER }

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)

data class ToolCallResult(
    val callId: String,
    val ok: Boolean,
    val output: String,
    val truncated: Boolean = false,
    // Paths of image files produced by the tool (e.g. generate_image). The
    // chat bubble for the tool-result message renders them as thumbnails.
    val attachmentPaths: List<String> = emptyList()
)

/** User-level decision for a pending tool call surfaced by the model. */
sealed class ToolDecision {
    object Approve : ToolDecision()
    data class Reject(val reason: String) : ToolDecision()
    object AlwaysApprove : ToolDecision()
}

sealed class GeminiEvent {
    data class MessageAdded(val message: GeminiMessage) : GeminiEvent()
    data class MessageUpdated(val message: GeminiMessage) : GeminiEvent()
    data class ToolCallPending(val call: ToolCall) : GeminiEvent()
    data class ToolCallCompleted(val result: ToolCallResult) : GeminiEvent()
    /** High-level status for the UI — null label means clear. */
    data class Thinking(val label: String?) : GeminiEvent()
    data class Notice(val message: String) : GeminiEvent()
    /**
     * Token accounting for the current session, reported by Gemini via
     * `usageMetadata.totalTokenCount` on each streamed response. `limit` is
     * the model's `inputTokenLimit` when known. The UI uses the ratio to
     * render a context-fill indicator and to decide whether to show the
     * auto-compression banner.
     */
    data class TokenUsage(val total: Int, val limit: Int?) : GeminiEvent()
}
