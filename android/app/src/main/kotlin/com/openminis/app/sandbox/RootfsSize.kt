package com.openminis.app.sandbox

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes

/** Counts regular file bytes only. Never follows guest-created links out of the rootfs. */
internal object RootfsSize {
    fun measure(root: File): Long {
        val path = root.toPath()
        if (!Files.isDirectory(path, NOFOLLOW_LINKS)) return 0L
        var total = 0L
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) {
                    total = if (Long.MAX_VALUE - total < attrs.size()) Long.MAX_VALUE else total + attrs.size()
                }
                return FileVisitResult.CONTINUE
            }
        })
        return total
    }
}
