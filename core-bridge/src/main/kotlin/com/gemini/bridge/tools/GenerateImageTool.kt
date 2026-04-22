package com.gemini.bridge.tools

import com.gemini.domain.ToolCall
import com.gemini.domain.ToolCallResult
import com.gemini.domain.ToolCategory
import com.gemini.domain.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls Google's Imagen `:predict` endpoint to generate one or more images
 * from a text prompt. Bytes are saved to app-owned storage via `persist`; the
 * returned paths are injected into the tool result so the UI layer can pick
 * them up and attach them to the model's bubble.
 *
 * Constructed lazily from RestGeminiCore so the tool always reads the current
 * API key and Imagen model variant the user picked in settings.
 */
class GenerateImageTool(
    private val getApiKey: () -> String,
    private val getModel: () -> String,
    private val persist: (id: String, bytes: ByteArray, mime: String) -> String?
) : Tool {

    override val spec = ToolSpec(
        name = "generate_image",
        description = "Generate an image from a text prompt using Google's " +
            "Imagen. Returns absolute paths of PNGs saved in app storage. Use " +
            "this when the user asks to draw, illustrate, or create an image.",
        category = ToolCategory.FILES,
        destructive = false,
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "prompt" to stringProp(
                    "Description of the image to generate. Detailed prompts " +
                        "work best; include subject, style, lighting, etc."
                ),
                "aspect_ratio" to mapOf(
                    "type" to "string",
                    "description" to "Aspect ratio (1:1, 3:4, 4:3, 9:16, 16:9). Defaults to 1:1.",
                    "enum" to listOf("1:1", "3:4", "4:3", "9:16", "16:9")
                ),
                "number_of_images" to mapOf(
                    "type" to "integer",
                    "description" to "How many images to generate (1–4). Default 1.",
                    "minimum" to 1,
                    "maximum" to 4
                )
            ),
            "required" to listOf("prompt")
        )
    )

    override suspend fun execute(call: ToolCall): ToolCallResult = withContext(Dispatchers.IO) {
        val prompt = call.arguments["prompt"] as? String
        if (prompt.isNullOrBlank()) return@withContext ToolOutput.error(call.id, "prompt is required")
        val aspect = call.arguments["aspect_ratio"] as? String ?: "1:1"
        val count = (call.arguments["number_of_images"] as? Number)?.toInt()?.coerceIn(1, 4) ?: 1
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext ToolOutput.error(call.id, "Gemini API key is not configured")

        val model = getModel()
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:predict")
        val body = JSONObject()
            .put(
                "instances",
                JSONArray().put(JSONObject().put("prompt", prompt))
            )
            .put(
                "parameters",
                JSONObject()
                    .put("sampleCount", count)
                    .put("aspectRatio", aspect)
            ).toString()

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 60_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                return@withContext ToolOutput.error(
                    call.id,
                    "Imagen $code: ${raw.take(400)}"
                )
            }
            val predictions = JSONObject(raw).optJSONArray("predictions")
                ?: return@withContext ToolOutput.error(call.id, "No predictions in Imagen response")
            val paths = mutableListOf<String>()
            for (i in 0 until predictions.length()) {
                val pred = predictions.getJSONObject(i)
                val b64 = pred.optString("bytesBase64Encoded").orEmpty()
                if (b64.isBlank()) continue
                val mime = pred.optString("mimeType", "image/png")
                val bytes = runCatching {
                    android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                }.getOrNull() ?: continue
                val path = persist("imagen-${System.nanoTime()}-$i", bytes, mime)
                if (path != null) paths.add(path)
            }
            if (paths.isEmpty()) {
                return@withContext ToolOutput.error(call.id, "Imagen returned no decodable image")
            }
            val out = buildString {
                append("Generated ${paths.size} image").append(if (paths.size > 1) "s" else "")
                append(" with $model:\n")
                paths.forEach { append("  • ").append(it).append('\n') }
            }
            ToolCallResult(
                callId = call.id,
                ok = true,
                output = if (out.length > ToolOutput.MAX) out.take(ToolOutput.MAX) else out,
                truncated = out.length > ToolOutput.MAX,
                attachmentPaths = paths
            )
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}
