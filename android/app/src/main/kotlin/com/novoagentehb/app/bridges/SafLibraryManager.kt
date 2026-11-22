package com.novoagentehb.app.bridges

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class LibraryEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val uriOrPath: String,
    val scopeLabel: String,
    val mimeType: String = "*/*"
)

data class WorkspaceWriteOutcome(
    val success: Boolean,
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val error: String? = null
)

@Singleton
class SafLibraryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workspaceRoot: File by lazy {
        File(context.filesDir, "agent_workspace").apply { mkdirs() }
    }

    fun writeWorkspaceFile(relativePath: String, content: String): WorkspaceWriteOutcome {
        val sanitized = sanitizeRelative(relativePath)
        if (sanitized.isBlank()) {
            return WorkspaceWriteOutcome(false, "", 0L, "", "Nome de arquivo inválido.")
        }
        val target = workspaceTarget(sanitized)
            ?: return WorkspaceWriteOutcome(false, sanitized, 0L, "", "Caminho fora do workspace.")
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeText(content, Charsets.UTF_8)
            val verified = target.exists() && target.isFile
            val bytes = target.readBytes()
            val sha = sha256Hex(bytes)
            WorkspaceWriteOutcome(
                success = verified,
                relativePath = sanitized,
                sizeBytes = bytes.size.toLong(),
                sha256 = sha
            )
        }.getOrElse { err ->
            WorkspaceWriteOutcome(false, sanitized, 0L, "", err.message)
        }
    }

    fun readWorkspaceFile(relativePath: String): String? {
        val sanitized = sanitizeRelative(relativePath)
        if (sanitized.isBlank()) return null
        val target = workspaceTarget(sanitized) ?: return null
        return if (target.exists() && target.isFile) target.readText(Charsets.UTF_8) else null
    }

    fun listWorkspaceFiles(): List<LibraryEntry> {
        return workspaceRoot.walkTopDown()
            .onEnter { directory -> directory == workspaceRoot || !java.nio.file.Files.isSymbolicLink(directory.toPath()) }
            .filter { it.isFile && workspaceTarget(it.relativeTo(workspaceRoot).path) != null }
            .sortedByDescending { it.lastModified() }
            .map {
                LibraryEntry(
                    name = it.relativeTo(workspaceRoot).path,
                    isDirectory = false,
                    sizeBytes = it.length(),
                    uriOrPath = it.absolutePath,
                    scopeLabel = "Workspace do agente",
                    mimeType = guessMime(it.name)
                )
            }
            .toList()
    }

    fun deleteWorkspaceFile(relativePath: String): Boolean {
        val sanitized = sanitizeRelative(relativePath)
        if (sanitized.isBlank()) return false
        val target = workspaceTarget(sanitized) ?: return false
        return target.exists() && target.delete()
    }

    fun shareWorkspaceFile(relativePath: String): Boolean {
        val sanitized = sanitizeRelative(relativePath)
        if (sanitized.isBlank()) return false
        val target = workspaceTarget(sanitized) ?: return false
        if (!target.exists() || !target.isFile) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = guessMime(target.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartilhar ${target.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun listSafTree(treeUriString: String): List<LibraryEntry> {
        if (treeUriString.isBlank()) return emptyList()
        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) }.getOrNull()
            ?: return emptyList()
        return listSafDirectory(tree)
    }

    fun listSafSubfolder(folderUriString: String): List<LibraryEntry> {
        if (folderUriString.isBlank()) return emptyList()
        val folder = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(folderUriString)) }.getOrNull()
            ?: return emptyList()
        return listSafDirectory(folder)
    }

    private fun listSafDirectory(dir: DocumentFile): List<LibraryEntry> {
        return dir.listFiles()
            .sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })
            .map { doc ->
                LibraryEntry(
                    name = doc.name ?: "(sem nome)",
                    isDirectory = doc.isDirectory,
                    sizeBytes = doc.length(),
                    uriOrPath = doc.uri.toString(),
                    scopeLabel = "Pasta autorizada SAF",
                    mimeType = doc.type ?: guessMime(doc.name.orEmpty())
                )
            }
    }

    fun createSafDirectory(parentUriString: String, folderName: String): Boolean {
        val clean = folderName.trim()
        if (parentUriString.isBlank() || clean.isBlank()) return false
        val dir = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(parentUriString)) }.getOrNull()
            ?: return false
        return dir.createDirectory(clean) != null
    }

    fun renameSafEntry(uriString: String, newName: String): Boolean {
        val clean = newName.trim()
        if (uriString.isBlank() || clean.isBlank()) return false
        val doc = runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uriString)) }.getOrNull()
            ?: runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriString)) }.getOrNull()
            ?: return false
        return doc.renameTo(clean)
    }

    fun deleteSafEntry(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val doc = runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uriString)) }.getOrNull()
            ?: runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriString)) }.getOrNull()
            ?: return false
        return doc.delete()
    }

    fun openSafEntry(uriString: String, mimeType: String): Boolean {
        if (uriString.isBlank()) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriString), mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun shareSafEntry(uriString: String, mimeType: String, name: String): Boolean {
        if (uriString.isBlank()) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartilhar $name").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    /** Reject paths whose resolved target escapes the app-private workspace, including symlinks. */
    private fun workspaceTarget(relative: String): File? = runCatching {
        val root = workspaceRoot.canonicalFile
        val target = File(root, relative).canonicalFile
        target.takeIf { it.path.startsWith(root.path + File.separator) }
    }.getOrNull()

    private fun sanitizeRelative(raw: String): String =
        raw.trim().replace("..", "").trimStart('/', '\\')

    private fun guessMime(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log") -> "text/plain"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".mp4") -> "video/mp4"
            else -> "*/*"
        }
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
