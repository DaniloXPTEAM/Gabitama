package com.novoagentehb.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openminis.app.sandbox.ExecutionCoordinator
import com.openminis.app.sandbox.PRootKernel
import com.openminis.app.sandbox.PersistentShell
import com.openminis.app.sandbox.RootfsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Internal ARM-only fixture. No user/agent input, network or arbitrary scripts. */
@RunWith(AndroidJUnit4::class)
class Arm64OriginalShellAndroidTest {
    @Test fun originalArm64ShellAndCoordinatorFixedCommands() = runBlocking {
        assertTrue("requires a real arm64 ABI", Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = RootfsManager.getInstance(context)
        assertFalse("only a fresh disposable test app is allowed", root.rootfsDir.exists())
        PRootKernel.boot(context)
        assertTrue(root.isInstalled)
        val shell = PersistentShell(context, "fixture-arm64", emptyMap())
        try {
            val first = shell.executeCommand("export ARM_FIXTURE=ok; cd /tmp", 30000)
            assertEquals(first.first, 0, first.second)
            val second = shell.executeCommand("printf '%s:%s\\n' \"\$ARM_FIXTURE\" \"\$PWD\"", 30000)
            assertEquals(second.first, 0, second.second)
            assertTrue(second.first.contains("ok:/tmp"))
            val external = shell.executeCommand("/bin/busybox echo ARM_EXTERNAL_OK", 30000)
            assertEquals(external.first, 0, external.second)
            assertTrue(external.first.contains("ARM_EXTERNAL_OK"))
            assertEquals(1, shell.executeCommand("false", 30000).second)
        } finally { shell.stop() }
        ExecutionCoordinator.init(context)
        try {
            val result = ExecutionCoordinator.execute("fixture-arm64-coordinator", "printf ARM_COORDINATOR_OK", 30000)
            assertEquals(result.output, 0, result.exitCode)
            assertTrue(result.output.contains("ARM_COORDINATOR_OK"))
        } finally { ExecutionCoordinator.sessionDidTerminate("fixture-arm64-coordinator") }
        val timeout = PersistentShell(context, "fixture-arm64-timeout", emptyMap())
        val late = File(root.rootfsDir, "tmp/arm-fixture-late")
        try {
            timeout.ensureStarted()
            assertTrue(timeout.isAlive)
            val result = timeout.executeCommand("touch /tmp/arm-fixture-started; sleep 4; touch /tmp/arm-fixture-late", 2000)
            assertEquals(result.first, 124, result.second)
            assertTrue(File(root.rootfsDir, "tmp/arm-fixture-started").exists())
            delay(5000)
            assertFalse(late.exists())
        } finally { timeout.stop() }
    }
}
