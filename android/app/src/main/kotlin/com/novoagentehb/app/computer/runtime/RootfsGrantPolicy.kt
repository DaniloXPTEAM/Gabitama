package com.novoagentehb.app.computer.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Pure policy (JVM-testable) that decides whether ONE regular file under a fixed root may be granted.
 *
 * Rules: relative paths only; no empty, `.`, `..` or repeated separators; no backslashes or NUL;
 * bounded length and depth; every component, including the final one, must not be a symbolic link;
 * the root itself must be a real directory; directories and missing entries are never granted.
 *
 * This is a precondition check, not the whole guarantee. Callers must open the file through
 * [RootfsGrantBroker], which verifies the opened descriptor against the validated path.
 */
object RootfsGrantPolicy {
    const val MAX_PATH_LENGTH = 1024
    const val MAX_COMPONENTS = 16

    private val NO_FOLLOW = arrayOf(LinkOption.NOFOLLOW_LINKS)

    sealed class Decision {
        data class Granted(val file: File) : Decision()
        data class Rejected(val reason: String) : Decision()
    }

    fun resolveRegularFile(root: File, relative: String): Decision {
        if (relative.isEmpty() || relative.length > MAX_PATH_LENGTH) return Decision.Rejected("length")
        if (relative.startsWith("/") || relative.indexOf('\u0000') >= 0 || relative.indexOf('\\') >= 0) {
            return Decision.Rejected("absolute-or-invalid-char")
        }
        val parts = relative.split('/')
        if (parts.size > MAX_COMPONENTS) return Decision.Rejected("depth")
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return Decision.Rejected("traversal")

        val rootPath = root.toPath()
        if (!Files.isDirectory(rootPath, *NO_FOLLOW)) return Decision.Rejected("root")

        var current: Path = rootPath
        for (part in parts) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) return Decision.Rejected("symlink")
        }
        if (!Files.isRegularFile(current, *NO_FOLLOW)) return Decision.Rejected("not-regular-file")
        return Decision.Granted(current.toFile())
    }
}
