package com.openminis.app.sandbox

import org.junit.Assert.assertEquals
import org.junit.Test

class PRootEnvironmentTest {

    @Test fun setsOnlyKnownValuesAndKeepsOriginalNames() {
        val env = PRootEnvironment.processEnv(
            path = "/bin:/usr/bin", tmpDir = "/cache/proot-tmp", nativeLibDir = "/lib/x86_64",
            loader = null, loader32 = "",
        )
        assertEquals(
            linkedMapOf(
                "PATH" to "/bin:/usr/bin",
                "PROOT_TMP_DIR" to "/cache/proot-tmp",
                "LD_LIBRARY_PATH" to "/lib/x86_64",
            ),
            env,
        )
    }

    @Test fun loaderOverridesAreWrittenOnlyWhenPresent() {
        val env = PRootEnvironment.processEnv(
            path = null, tmpDir = "/t", nativeLibDir = null, loader = "/l/loader", loader32 = "/l/loader32",
        )
        assertEquals(
            linkedMapOf("PROOT_TMP_DIR" to "/t", "PROOT_LOADER" to "/l/loader", "PROOT_LOADER_32" to "/l/loader32"),
            env,
        )
    }

    /** The three original paths (ShellExecutor, TerminalSession, PersistentShell) do not set PATH. */
    @Test fun pathIsOmittedWhenCallerPassesNull() {
        val env = PRootEnvironment.processEnv(path = null, tmpDir = "/t", nativeLibDir = "", loader = "", loader32 = "")
        assertEquals(linkedMapOf("PROOT_TMP_DIR" to "/t"), env)
    }
}
