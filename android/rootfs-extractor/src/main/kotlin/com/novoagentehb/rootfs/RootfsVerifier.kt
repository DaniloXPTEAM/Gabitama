package com.novoagentehb.rootfs

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Structural check of an extracted Linux rootfs. Reads metadata and the first bytes of one ELF
 * header; never executes anything from the tree. Android-free (JDK only).
 */
object RootfsVerifier {
    data class Summary(val release: String, val arch: String, val files: Int, val bytes: Long)

    /**
     * Throws IllegalStateException with a short fixed reason when the tree is not a valid rootfs.
     * Walks without following links: every symlink must resolve inside [root].
     */
    fun verify(
        root: File,
        expectedRelease: String,
        maxEntries: Int,
        maxBytes: Long,
        expectedArch: String = "aarch64",
    ): Summary {
        check(Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)) { "root-missing" }
        val canonicalRoot = root.canonicalPath
        val prefix = canonicalRoot + File.separator
        var entries = 0
        var bytes = 0L
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(++entries <= maxEntries) { "too-many-entries" }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(++entries <= maxEntries) { "too-many-entries" }
                if (attrs.isSymbolicLink) {
                    val canonical = file.toFile().canonicalPath
                    check(canonical == canonicalRoot || canonical.startsWith(prefix)) { "symlink-escapes-root" }
                } else if (attrs.isRegularFile) {
                    bytes += attrs.size()
                    check(bytes <= maxBytes) { "too-many-bytes" }
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult =
                throw IllegalStateException("unreadable-entry")
        })

        val release = File(root, "etc/alpine-release").takeIf { it.isFile }?.readText()?.trim()
            ?: throw IllegalStateException("release-missing")
        check(release == expectedRelease) { "release-mismatch" }

        val busybox = File(root, "bin/busybox")
        check(Files.isRegularFile(busybox.toPath(), LinkOption.NOFOLLOW_LINKS)) { "busybox-missing" }
        val arch = elfArch(busybox)
        check(arch == expectedArch) { "busybox-arch-mismatch" }

        // Both must exist and resolve inside the tree (links followed here on purpose).
        val loader = File(root, "lib/ld-musl-$expectedArch.so.1")
        check(loader.exists() && loader.canonicalPath.startsWith(prefix)) { "musl-loader-missing" }
        val sh = File(root, "bin/sh")
        check(sh.exists() && sh.canonicalPath.startsWith(prefix)) { "sh-missing" }

        return Summary(release = release, arch = arch, files = entries, bytes = bytes)
    }

    /** Returns "aarch64" (EM_AARCH64) or "x86_64" (EM_X86_64) for a 64-bit little-endian ELF; throws otherwise. */
    fun elfArch(file: File): String {
        val header = ByteArray(20)
        file.inputStream().use { input ->
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                check(n > 0) { "elf-truncated" }
                read += n
            }
        }
        check(header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) { "not-elf" }
        check(header[4].toInt() == 2 && header[5].toInt() == 1) { "not-elf64-le" }
        val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        return when (machine) {
            0xB7 -> "aarch64"
            0x3E -> "x86_64"
            else -> "elf-machine-$machine"
        }
    }
}
