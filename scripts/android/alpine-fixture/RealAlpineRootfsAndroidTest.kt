package com.novoagentehb.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openminis.app.sandbox.RootfsManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/** Used only in the isolated fixture workflow, never included in normal test APKs. */
@RunWith(AndroidJUnit4::class)
class RealAlpineRootfsAndroidTest {
    @Test fun originalAlpineArchiveExtractsOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "alpine-fixture-").toFile()
        try {
            instrumentation.context.assets.open("alpine-3.21.3.tar.gz").use { asset ->
                GZIPInputStream(asset).use { RootfsManager.getInstance(context).extractTar(it, root) }
            }
            assertTrue(File(root, "bin/busybox").isFile)
            assertTrue(File(root, "bin/busybox").canExecute())
            assertTrue(File(root, "bin/sh").exists())
        } finally { root.deleteRecursively() }
    }
}
