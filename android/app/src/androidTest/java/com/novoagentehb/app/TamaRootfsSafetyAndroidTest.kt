package com.novoagentehb.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openminis.app.sandbox.RootfsManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** Synthetic tar entries only; these tests never install or reset a real rootfs. */
@RunWith(AndroidJUnit4::class)
class TamaRootfsSafetyAndroidTest {
    private fun tar(vararg entries: Triple<String, Char, String>): ByteArray {
        val out = ByteArrayOutputStream()
        entries.forEach { (name, type, value) ->
            val body = if (type == '0') value.toByteArray(Charsets.UTF_8) else byteArrayOf()
            val h = ByteArray(512)
            fun field(offset: Int, value: String) { val bytes = value.toByteArray(Charsets.UTF_8); System.arraycopy(bytes, 0, h, offset, bytes.size) }
            field(0, name); field(100, "0000755"); field(124, body.size.toString(8).padStart(11, '0'))
            h[156] = type.code.toByte()
            if (type == '2' || type == '1') field(157, value)
            out.write(h); out.write(body)
            if (body.size % 512 != 0) out.write(ByteArray(512 - body.size % 512))
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    @Test fun originalExtractorHandlesFilesAndGuestAbsoluteSymlinkWithinRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "rootfs-test-").toFile()
        try {
            val input = tar(Triple("./", '5', ""), Triple("./bin/", '5', ""), Triple("./bin/busybox", '0', "example"), Triple("./bin/sh", '2', "/bin/busybox"))
            RootfsManager.getInstance(context).extractTar(ByteArrayInputStream(input), root)
            assertEquals("example", File(root, "bin/busybox").readText())
            assertTrue(Files.isSymbolicLink(File(root, "bin/sh").toPath()))
            assertEquals("example", File(root, "bin/sh").readText())
            assertTrue(File(root, "bin/sh").canonicalPath.startsWith(root.canonicalPath + File.separator))
        } finally { root.deleteRecursively() }
    }

    @Test fun extractionRejectsTraversalSymlinkEscapeAndTruncatedFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parent = Files.createTempDirectory(context.cacheDir.toPath(), "rootfs-test-").toFile()
        val manager = RootfsManager.getInstance(context)
        try {
            val cases = listOf(
                tar(Triple("../outside.txt", '0', "escape")),
                tar(Triple("escape", '2', "../../outside")),
                tar(Triple("link", '2', "internal"), Triple("link/file.txt", '0', "escape")),
                tar(Triple("payload", '0', "abc")).copyOf(513)
            )
            cases.forEachIndexed { index, data ->
                val dir = File(parent, "test$index").apply { mkdirs() }
                try { manager.extractTar(ByteArrayInputStream(data), dir); fail("Unsafe tar accepted: $index") }
                catch (_: IllegalArgumentException) { }
            }
            assertFalse(File(parent.parentFile, "outside.txt").exists())
        } finally { parent.deleteRecursively() }
    }
}
