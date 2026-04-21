package com.gemini.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lightweight markdown renderer — enough for Gemini's output without pulling
 * a full markdown dependency. Handles fenced code blocks, inline code, bold,
 * italics, links, headings, and bullet / numbered lists.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        val segments = splitCodeFences(text)
        segments.forEachIndexed { i, seg ->
            if (i > 0) Spacer(Modifier.padding(vertical = 2.dp))
            when (seg) {
                is Segment.Code -> CodeBlock(seg.language, seg.body, context)
                is Segment.Text -> renderProse(seg.body, color)
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String?, body: String, context: Context) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.small
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language.orEmpty().ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { copyToClipboard(context, body) }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    body.trimEnd('\n'),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun renderProse(body: String, baseColor: androidx.compose.ui.graphics.Color) {
    val lines = body.split('\n')
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trimStart()
        // Tables: a header row followed by a separator of dashes (GFM).
        if (trimmed.startsWith("|") && i + 1 < lines.size) {
            val sep = lines[i + 1].trimStart()
            if (isTableSeparator(sep)) {
                val rows = mutableListOf<List<String>>()
                rows += parseTableRow(trimmed)
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows += parseTableRow(lines[i].trimStart())
                    i++
                }
                MarkdownTable(rows, baseColor)
                continue
            }
        }
        when {
            trimmed.startsWith("### ") -> ProseText(
                annotateInline(trimmed.removePrefix("### "), baseColor),
                style = MaterialTheme.typography.titleSmall,
                color = baseColor,
                modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
            )
            trimmed.startsWith("## ") -> ProseText(
                annotateInline(trimmed.removePrefix("## "), baseColor),
                style = MaterialTheme.typography.titleMedium,
                color = baseColor,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            trimmed.startsWith("# ") -> ProseText(
                annotateInline(trimmed.removePrefix("# "), baseColor),
                style = MaterialTheme.typography.titleLarge,
                color = baseColor,
                modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
            )
            trimmed.startsWith("> ") -> Row(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.width(3.dp).height(20.dp)
                ) {}
                Spacer(Modifier.width(8.dp))
                ProseText(
                    annotateInline(trimmed.removePrefix("> "), baseColor),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> Surface(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 6.dp)
            ) {}
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") ||
                trimmed.startsWith("- [X] ") -> {
                val checked = trimmed[3] != ' '
                val label = trimmed.removeRange(0, 6)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (checked) "☑ " else "☐ ", color = baseColor)
                    ProseText(
                        annotateInline(label, baseColor),
                        color = baseColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text("•  ", color = baseColor)
                ProseText(
                    annotateInline(trimmed.drop(2), baseColor),
                    color = baseColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Regex("^\\d+\\. ").containsMatchIn(trimmed) -> {
                val m = Regex("^(\\d+)\\. (.*)").find(trimmed)
                if (m != null) {
                    Row {
                        Text("${m.groupValues[1]}. ", color = baseColor)
                        ProseText(
                            annotateInline(m.groupValues[2], baseColor),
                            color = baseColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    ProseText(annotateInline(raw, baseColor), color = baseColor)
                }
            }
            trimmed.isEmpty() -> Spacer(Modifier.padding(vertical = 3.dp))
            else -> ProseText(
                annotateInline(raw, baseColor),
                color = baseColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        i++
    }
}

private fun isTableSeparator(line: String): Boolean {
    // | --- | :---: | ---: | — dashes with optional colons, pipe-separated.
    if (!line.startsWith("|")) return false
    val cells = line.trim().trim('|').split("|").map { it.trim() }
    if (cells.isEmpty()) return false
    return cells.all { it.matches(Regex(":?-{2,}:?")) }
}

private fun parseTableRow(line: String): List<String> =
    line.trim().trim('|').split("|").map { it.trim() }

@Composable
private fun MarkdownTable(
    rows: List<List<String>>,
    baseColor: androidx.compose.ui.graphics.Color
) {
    if (rows.isEmpty()) return
    val header = rows.first()
    val body = rows.drop(1)
    val cols = rows.maxOf { it.size }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                header.forEachIndexed { idx, cell ->
                    TableCell(cell, baseColor, header = true, last = idx == cols - 1)
                }
                repeat(cols - header.size) { TableCell("", baseColor, header = true, last = false) }
            }
            body.forEach { row ->
                Surface(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxWidth().height(1.dp)
                ) {}
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { idx, cell ->
                        TableCell(cell, baseColor, header = false, last = idx == cols - 1)
                    }
                    repeat(cols - row.size) {
                        TableCell("", baseColor, header = false, last = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    baseColor: androidx.compose.ui.graphics.Color,
    header: Boolean,
    last: Boolean
) {
    ProseText(
        annotateInline(text, baseColor),
        style = if (header) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.bodySmall,
        color = if (header) baseColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

// Text with clickable URL annotations (`URL_TAG`). On tap, launches ACTION_VIEW
// for the URL under the touch point. Falls back to a plain Text when the string
// has no URL annotations so regular prose doesn't eat long-press / selection
// gestures unnecessarily.
@Composable
private fun ProseText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    val hasLinks = text.getStringAnnotations(URL_TAG, 0, text.length).isNotEmpty()
    if (!hasLinks) {
        Text(text, modifier = modifier, style = style, color = color)
        return
    }
    val context = LocalContext.current
    val effectiveStyle = if (color == androidx.compose.ui.graphics.Color.Unspecified) style
        else style.copy(color = color)
    ClickableText(
        text = text,
        modifier = modifier,
        style = effectiveStyle,
        onClick = { offset ->
            text.getStringAnnotations(URL_TAG, offset, offset).firstOrNull()
                ?.let { ann -> openUrl(context, ann.item) }
        }
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val normalised = if (url.startsWith("http://") || url.startsWith("https://")) url
            else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalised))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Cannot open link: $url", Toast.LENGTH_SHORT).show()
    }
}

private sealed interface Segment {
    data class Text(val body: String) : Segment
    data class Code(val language: String?, val body: String) : Segment
}

private fun splitCodeFences(text: String): List<Segment> {
    val out = mutableListOf<Segment>()
    val lines = text.lines()
    var i = 0
    val buf = StringBuilder()
    while (i < lines.size) {
        val line = lines[i]
        val fence = Regex("^\\s*```([\\w+-]*)\\s*$").matchEntire(line)
        if (fence != null) {
            if (buf.isNotEmpty()) {
                out += Segment.Text(buf.toString().trimEnd('\n'))
                buf.clear()
            }
            val lang = fence.groupValues[1].ifBlank { null }
            val code = StringBuilder()
            i++
            while (i < lines.size) {
                val inner = lines[i]
                if (Regex("^\\s*```\\s*$").matchEntire(inner) != null) { i++; break }
                code.appendLine(inner)
                i++
            }
            out += Segment.Code(lang, code.toString())
            continue
        }
        if (buf.isNotEmpty()) buf.append('\n')
        buf.append(line)
        i++
    }
    if (buf.isNotEmpty()) out += Segment.Text(buf.toString())
    return out
}

private fun annotateInline(line: String, baseColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '`' -> {
                    val end = line.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(line.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(c); i++ }
                }
                c == '*' && i + 1 < line.length && line[i + 1] == '*' -> {
                    val end = line.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(c); i++ }
                }
                c == '_' || (c == '*' && (i == 0 || line[i - 1] != '*')) -> {
                    val end = line.indexOf(c, i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(line.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(c); i++ }
                }
                c == '[' -> {
                    val close = line.indexOf(']', i + 1)
                    val paren = if (close > 0 && close + 1 < line.length && line[close + 1] == '(')
                        line.indexOf(')', close + 2) else -1
                    if (close > 0 && paren > 0) {
                        val label = line.substring(i + 1, close)
                        val url = line.substring(close + 2, paren)
                        pushStringAnnotation(tag = URL_TAG, annotation = url)
                        withStyle(
                            SpanStyle(
                                color = MaterialThemeLink,
                                textDecoration = TextDecoration.Underline
                            )
                        ) { append(label) }
                        pop()
                        i = paren + 1
                    } else { append(c); i++ }
                }
                // Bare URLs (http://, https://).
                c == 'h' && line.startsWith("http", i) && run {
                    val rest = line.substring(i)
                    rest.startsWith("http://") || rest.startsWith("https://")
                } -> {
                    val end = findUrlEnd(line, i)
                    val url = line.substring(i, end)
                    pushStringAnnotation(tag = URL_TAG, annotation = url)
                    withStyle(
                        SpanStyle(
                            color = MaterialThemeLink,
                            textDecoration = TextDecoration.Underline
                        )
                    ) { append(url) }
                    pop()
                    i = end
                }
                else -> { append(c); i++ }
            }
        }
    }
}

private fun findUrlEnd(line: String, start: Int): Int {
    var j = start
    while (j < line.length) {
        val ch = line[j]
        // Break on whitespace, closing brackets, and trailing punctuation that
        // is almost never part of a URL.
        if (ch.isWhitespace() || ch in ")]>\"'`") break
        j++
    }
    // Strip trailing . , ; : ! ? — common sentence punctuation right after URLs.
    while (j > start + 1 && line[j - 1] in ".,;:!?") j--
    return j
}

private const val URL_TAG = "URL"

// Link colour — kept as a top-level constant so annotateInline stays pure (no
// @Composable context needed). Matches Material 3 primary on both themes
// reasonably well; the underline decoration carries most of the affordance.
private val MaterialThemeLink = androidx.compose.ui.graphics.Color(0xFF4285F4)

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("code", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
