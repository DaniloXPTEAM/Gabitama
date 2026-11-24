package com.novoagentehb.rootfs

import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Hardened tar extractor shared by the GabiTama app and the experimental computer helper.
 * Moved verbatim from RootfsManager (only the entry point was renamed to `extract`), so both
 * packages run the same code. Android-free: depends only on the JDK.
 *
 * Refuses absolute names, '..' escapes, symlinked ancestors, links leaving the root, device nodes,
 * unsupported types, bad checksums, excess entries (>50k) and excess bytes (>512MB expanded).
 */
object TarRootfsExtractor {
    fun extract(input: InputStream, targetDir: File) {
        val header = ByteArray(512)
        val root = targetDir.canonicalFile.toPath()
        require(targetDir.isDirectory && !java.nio.file.Files.isSymbolicLink(targetDir.toPath())) { "Diretório de extração inválido." }
        var entries = 0
        var totalBytes = 0L

        while (true) {
            val bytesRead = readFully(input, header)
            require(bytesRead == 512) { "Cabeçalho tar incompleto." }

            // Check for end-of-archive (two consecutive zero blocks)
            if (header.all { it == 0.toByte() }) {
                require(readFully(input, header) == 512 && header.all { it == 0.toByte() }) { "Fim de tar incompleto." }
                // Consume to EOF: this also makes GZIPInputStream verify the CRC and length.
                val trailing = ByteArray(8192)
                var totalTrailing = 0L
                while (true) {
                    val n = input.read(trailing)
                    if (n < 0) break
                    require(n > 0 && (0 until n).all { trailing[it] == 0.toByte() }) { "Dados após o fim do tar." }
                    totalTrailing += n
                    require(totalTrailing <= 1_048_576) { "Preenchimento tar excessivo." }
                }
                break
            }
            val expectedChecksum = extractString(header, 148, 8).trim().toLongOrNull(8)
                ?: throw IllegalArgumentException("Checksum tar inválido.")
            val actualChecksum = header.indices.sumOf { i -> if (i in 148..155) 32L else (header[i].toInt() and 255).toLong() }
            require(expectedChecksum == actualChecksum) { "Checksum tar incorreto." }

            val name = extractString(header, 0, 100)
            val modeOctal = extractString(header, 100, 8)
            val sizeOctal = extractString(header, 124, 12)
            val typeFlag = header[156].toInt().toChar()
            val linkName = extractString(header, 157, 100)
            val mode = modeOctal.trim().toIntOrNull(8) ?: 0

            // Handle GNU/POSIX long names via prefix field (bytes 345-500)
            val prefix = extractString(header, 345, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

            require(fullName.isNotEmpty()) { "Nome tar vazio." }

            // GabiTama: tar is treated as untrusted even when shipped as an APK asset.
            require(++entries <= 50_000) { "Entradas demais no rootfs." }
            require(!fullName.startsWith("/") && !fullName.contains('\u0000')) { "Caminho absoluto inválido no rootfs." }
            val path = root.resolve(fullName).normalize()
            require(path.startsWith(root)) { "Caminho fora do rootfs." }
            if (path == root) {
                require(typeFlag == '5' && (sizeOctal.trim().isEmpty() || sizeOctal.trim().toLongOrNull(8) == 0L)) { "Entrada raiz inválida." }
                continue
            }
            var component = root
            for (part in root.relativize(path.parent)) {
                component = component.resolve(part)
                require(!java.nio.file.Files.isSymbolicLink(component)) { "Diretório simbólico no caminho de extração." }
            }
            require(!java.nio.file.Files.isSymbolicLink(path)) { "Entrada simbólica já existente." }
            val size = sizeOctal.trim().let { if (it.isEmpty()) 0L else it.toLongOrNull(8) }
                ?: throw IllegalArgumentException("Tamanho tar inválido.")
            require(size in 0..268_435_456L && totalBytes <= 536_870_912L - size) { "Rootfs excede limite de extração." }
            totalBytes += size
            val outFile = path.toFile()

            when (typeFlag) {
                '5', 'D' -> {
                    // Directory
                    outFile.mkdirs()
                }
                '2' -> {
                    // Symbolic link
                    outFile.parentFile?.mkdirs()
                    require(linkName.isNotEmpty()) { "Link vazio no rootfs." }
                    // Absolute guest paths are rooted inside Alpine, not the host filesystem.
                    val target = if (linkName.startsWith("/")) root.resolve(linkName.removePrefix("/")).normalize()
                        else path.parent.resolve(linkName).normalize()
                    require(target.startsWith(root)) { "Link fora do rootfs." }
                    java.nio.file.Files.createSymbolicLink(path, path.parent.relativize(target))
                }
                '0', '\u0000' -> {
                    // Regular file (type '0' or null/legacy)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output ->
                        var remaining = size
                        val buf = ByteArray(8192)
                        while (remaining > 0) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val n = input.read(buf, 0, toRead)
                            require(n > 0) { "Arquivo tar incompleto." }
                            output.write(buf, 0, n)
                            remaining -= n
                        }
                    }
                    // Preserve executable permission from tar header
                    if (mode and 0b001_001_001 != 0) {
                        outFile.setExecutable(true, false)
                    }
                    // Skip padding to next 512-byte boundary
                    val remainder = (size % 512).toInt()
                    if (remainder != 0) {
                        skipFully(input, (512 - remainder).toLong())
                    }
                    continue // Already consumed data + padding
                }
                '1' -> {
                    // Hard link — create a copy
                    outFile.parentFile?.mkdirs()
                    require(!linkName.startsWith("/")) { "Hardlink absoluto inválido." }
                    val linkTarget = root.resolve(linkName).normalize()
                    require(linkTarget.startsWith(root) && java.nio.file.Files.isRegularFile(linkTarget, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { "Hardlink inválido." }
                    var linkParent = root
                    for (part in root.relativize(linkTarget.parent)) {
                        linkParent = linkParent.resolve(part)
                        require(!java.nio.file.Files.isSymbolicLink(linkParent)) { "Hardlink atravessa diretório simbólico." }
                    }
                    linkTarget.toFile().copyTo(outFile, overwrite = false)
                }
                else -> {
                    throw IllegalArgumentException("Tipo tar não suportado: $typeFlag")
                }
            }

            // Skip data blocks for non-file entries that we didn't consume above
            if (typeFlag != '0' && typeFlag != '\u0000' && size > 0) {
                val blocks = (size + 511) / 512 * 512
                skipFully(input, blocks)
            }
        }
    }

    private fun extractString(header: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, header.size)
        var actualEnd = offset
        for (i in offset until end) {
            if (header[i] == 0.toByte()) break
            actualEnd = i + 1
        }
        return String(header, offset, actualEnd - offset, Charset.forName("UTF-8"))
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n < 0) return offset
            require(n > 0) { "Fluxo tar sem progresso." }
            offset += n
        }
        return offset
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val n = input.read(buf, 0, toRead)
            require(n > 0) { "Padding tar incompleto." }
            remaining -= n
        }
    }
}
