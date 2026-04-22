package com.gemini.bridge.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Workspace rooted at either a [File] on the app's external files dir (the
 * default, requires no permission) or a SAF tree URI the user picked. Every
 * tool path is resolved as a relative path under the root — absolute paths
 * and `..` traversal are rejected.
 */
class Workspace(private val context: Context) {

    data class Entry(val name: String, val path: String, val isDir: Boolean, val size: Long)

    private var root: DocumentFile? = null
    private var label: String = "(no folder)"

    init {
        val defaultDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "workspace")
        if (!defaultDir.exists()) defaultDir.mkdirs()
        setFile(defaultDir)
    }

    fun rootLabel(): String = label

    /**
     * URI of the current root so the UI can offer "open in files app".
     * `file://...` for the default internal folder, `content://...` (tree) for
     * a user-picked SAF location. Null only before init() has run.
     */
    fun rootUri(): Uri? = root?.uri

    fun setFile(file: File) {
        val created = file.apply { if (!exists()) mkdirs() }
        root = DocumentFile.fromFile(created)
        label = created.absolutePath
    }

    fun setTreeUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        val tree = DocumentFile.fromTreeUri(context, uri)
            ?: error("Cannot open folder: $uri")
        root = tree
        label = tree.name ?: uri.toString()
    }

    /**
     * Real filesystem path of the workspace root when it can be reached from
     * outside this app's sandbox (typically Termux). Returns null when the
     * root is a SAF URI that isn't backed by a path Termux can see — in that
     * case, shell commands keep running in Termux's $HOME and files have to
     * travel through the workspace tools.
     */
    fun absolutePath(): String? = resolution().path

    /**
     * Returns a user-facing explanation of why the workspace can't be reached
     * from Termux, or null when it is reachable. Use this in UI badges and in
     * error hints so the user knows whether the fix is Termux-side (grant
     * storage, bootstrap) or app-side (pick a different folder).
     */
    fun unreachableReason(): String? = resolution().reason

    private data class Resolution(val path: String?, val reason: String?)

    private fun resolution(): Resolution {
        val uri = root?.uri
            ?: return Resolution(null, "No workspace root set.")
        if (uri.scheme == "file") return resolveFile(uri.path)

        val authority = uri.authority.orEmpty()
        if (authority == "com.android.externalstorage.documents") return resolveExternal(uri)

        return Resolution(
            null,
            "The workspace is backed by a storage provider Termux can't see " +
                "($authority). Pick a folder under /storage/emulated/0/ (for " +
                "example Documents/) via Settings → Workspace → Pick folder."
        )
    }

    private fun resolveFile(raw: String?): Resolution {
        if (raw == null) return Resolution(null, "Workspace URI has no path.")
        // App-private /Android/data/... is a sandbox peer, not reachable from
        // Termux no matter how much we `termux-setup-storage`.
        if (raw.startsWith("/storage/emulated/0/Android/") ||
            raw.startsWith("/sdcard/Android/")
        ) {
            return Resolution(
                null,
                "The workspace is in Android app-private storage ($raw), which " +
                    "Termux can never read. Pick a folder under " +
                    "/storage/emulated/0/ (for example Documents/ or Download/) " +
                    "via Settings → Workspace → Pick folder."
            )
        }
        if (raw.startsWith("/storage/") || raw.startsWith("/sdcard")) {
            return Resolution(raw, null)
        }
        return Resolution(
            null,
            "The workspace path ($raw) isn't on shared storage. Pick a folder " +
                "under /storage/emulated/0/ via Settings → Workspace → Pick folder."
        )
    }

    private fun resolveExternal(uri: Uri): Resolution {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return Resolution(null, "Cannot read the workspace document id.")
        val parts = id.split(':', limit = 2)
        val volume = parts.getOrNull(0).orEmpty()
        val sub = parts.getOrNull(1).orEmpty()
        // Some OEM document providers use a `raw:` prefix with an absolute
        // filesystem path — honor it when it points into shared storage.
        if (volume.equals("raw", ignoreCase = true)) return resolveFile(sub)
        val base = when {
            volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0"
            volume.equals("home", ignoreCase = true) -> "/storage/emulated/0/Documents"
            volume.isNotBlank() -> "/storage/$volume"
            else -> return Resolution(null, "Unrecognised workspace volume ($id).")
        }
        val candidate = if (sub.isBlank()) base else "$base/$sub"
        if (candidate.startsWith("/storage/emulated/0/Android/")) {
            return Resolution(
                null,
                "The workspace is in Android app-private storage ($candidate), " +
                    "which Termux can never read. Pick a folder under " +
                    "/storage/emulated/0/ via Settings → Workspace → Pick folder."
            )
        }
        return Resolution(candidate, null)
    }

    fun resolve(path: String): DocumentFile? {
        val safe = sanitize(path) ?: return null
        if (safe.isEmpty()) return root
        var cur = root ?: return null
        for (segment in safe) {
            val next = cur.findFile(segment) ?: return null
            cur = next
        }
        return cur
    }

    fun read(path: String): String {
        val file = resolve(path) ?: throw IllegalArgumentException("Not found: $path")
        if (file.isDirectory) throw IllegalArgumentException("Is a directory: $path")
        return context.contentResolver.openInputStream(file.uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Cannot read $path")
    }

    fun write(path: String, content: String): Entry {
        val parts = sanitize(path) ?: throw IllegalArgumentException("Invalid path: $path")
        if (parts.isEmpty()) throw IllegalArgumentException("Refusing to write to root")
        var cur = root ?: throw IllegalStateException("No workspace root")
        val fileName = parts.last()
        for (segment in parts.dropLast(1)) {
            cur = cur.findFile(segment) ?: cur.createDirectory(segment)
                ?: throw IllegalStateException("Cannot create directory: $segment")
        }
        val existing = cur.findFile(fileName)
        val target = existing ?: cur.createFile(mimeFor(fileName), fileName)
            ?: throw IllegalStateException("Cannot create file: $fileName")

        context.contentResolver.openOutputStream(target.uri, "wt")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot write $path")

        return Entry(
            name = target.name ?: fileName,
            path = parts.joinToString("/"),
            isDir = false,
            size = target.length()
        )
    }

    fun list(path: String): List<Entry> {
        val dir = resolve(path) ?: throw IllegalArgumentException("Not found: $path")
        if (!dir.isDirectory) throw IllegalArgumentException("Not a directory: $path")
        val prefix = sanitize(path)?.joinToString("/")?.ifEmpty { null }
        return dir.listFiles().map {
            val name = it.name ?: "?"
            Entry(
                name = name,
                path = if (prefix == null) name else "$prefix/$name",
                isDir = it.isDirectory,
                size = if (it.isDirectory) 0L else it.length()
            )
        }.sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun delete(path: String): Boolean {
        val file = resolve(path) ?: return false
        return file.delete()
    }

    fun walk(path: String, maxEntries: Int = 2000): Sequence<Entry> = sequence {
        val start = resolve(path) ?: return@sequence
        val startParts = sanitize(path) ?: return@sequence
        val stack = ArrayDeque<Pair<DocumentFile, List<String>>>()
        stack.addLast(start to startParts)
        var count = 0
        while (stack.isNotEmpty() && count < maxEntries) {
            val (doc, parts) = stack.removeLast()
            for (child in doc.listFiles()) {
                val name = child.name ?: continue
                val childParts = parts + name
                val childPath = childParts.joinToString("/")
                val isDir = child.isDirectory
                yield(Entry(name, childPath, isDir, if (isDir) 0 else child.length()))
                count++
                if (isDir) stack.addLast(child to childParts)
                if (count >= maxEntries) break
            }
        }
    }

    // SAF's createFile appends the MIME's canonical extension when it doesn't
    // match the supplied one (so "README.md" + "text/plain" becomes
    // "README.md.txt"). Picking a MIME that matches the filename's extension
    // keeps the name intact on every Android provider.
    private fun mimeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    private fun sanitize(path: String): List<String>? {
        val normalized = path.trim().trim('/')
        if (normalized.isEmpty()) return emptyList()
        val parts = normalized.split('/')
        for (p in parts) if (p.isEmpty() || p == "." || p == "..") return null
        return parts
    }
}
