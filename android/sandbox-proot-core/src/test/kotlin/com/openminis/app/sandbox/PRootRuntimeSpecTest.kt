package com.openminis.app.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PRootRuntimeSpecTest {

    private fun spec(
        installed: Boolean = true,
        execution: PRootRuntimeSpec.Execution = PRootRuntimeSpec.Execution.FIXED_OPERATIONS_ONLY,
        root: String? = "/data/alpine",
    ) = PRootRuntimeSpec(
        rootfsDir = root,
        rootfsInstalled = installed,
        rootfsArch = "x86_64",
        prootBinary = "/lib/libproot.so",
        nativeLibDir = "/lib",
        loaderPath = "/lib/libproot-loader.so",
        loader32Path = "",
        tmpDir = "/cache/proot-tmp",
        execution = execution,
    )

    @Test fun buildsCommandOnlyWhenInstalledAndFixed() {
        val argv = spec().commandFor("echo ok", listOf("--kill-on-exit"))
        assertTrue(argv != null && argv.contains("/lib/libproot.so"))
        assertEquals("echo ok", argv!!.last())
        assertTrue(argv.contains("--kill-on-exit"))
    }

    @Test fun refusesWhenRootfsIsNotInstalled() {
        assertNull(spec(installed = false).commandFor("echo ok"))
    }

    @Test fun refusesWhenExecutionIsDisabled() {
        assertNull(spec(execution = PRootRuntimeSpec.Execution.DISABLED).commandFor("echo ok"))
    }

    @Test fun refusesWithoutRootfsDir() {
        assertNull(spec(root = null).commandFor("echo ok"))
    }

    @Test fun environmentComesFromSharedBlock() {
        val env = spec().prootEnvironment(path = null)
        assertEquals("/cache/proot-tmp", env["PROOT_TMP_DIR"])
        assertEquals("/lib", env["LD_LIBRARY_PATH"])
        assertEquals("/lib/libproot-loader.so", env["PROOT_LOADER"])
        assertTrue(!env.containsKey("PATH") && !env.containsKey("PROOT_LOADER_32"))
    }

    @Test fun componentsStateReflectsRealStatus() {
        assertTrue(spec().components().contains("rootfs=INSTALLED arch=x86_64 execution=FIXED_OPERATIONS_ONLY"))
        assertTrue(spec(installed = false).components().contains("rootfs=ABSENT"))
    }
}
