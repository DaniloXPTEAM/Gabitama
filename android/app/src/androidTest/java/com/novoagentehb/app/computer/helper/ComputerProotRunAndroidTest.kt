package com.novoagentehb.app.computer.helper

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.computerhelper.contract.HelperContract
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Fixed PRoot operations inside the helper (debug/test builds only): the ORIGINAL PRoot binary built
 * from third_party for Android x86_64 by scripts/android/proot-original. Runs on the x86_64 emulator
 * only; it is not evidence of ARM64 execution. The helper receives an operation id, never a command.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ComputerProotRunAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun p01_originalProotVersionRunsInsideHelper(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            val start = client.prootStart(HelperContract.PROOT_OP_VERSION)
            log("p01 start code=${start.code} text=${start.text}")
            assertEquals(HelperContract.CODE_OK, start.code)
            val terminal = awaitTerminal(client)
            log("p01 terminal=${terminal.text}")
            assertTrue(terminal.text, terminal.text.contains("state=EXITED"))
            assertTrue(terminal.text, terminal.text.contains("exit=0"))
            assertTrue(terminal.text, terminal.text.contains("proot", ignoreCase = true))
        } finally {
            client.close()
        }
    }

    @Test
    fun p02_cancelKillsRunningTraceeWithoutSurvivors(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            val start = client.prootStart(HelperContract.PROOT_OP_HOST_SLEEP)
            assertEquals(HelperContract.CODE_OK, start.code)
            assertTrue(start.text, start.text.contains("state=RUNNING"))
            delay(RUN_BEFORE_CANCEL_MS)
            val cancel = client.prootCancel()
            log("p02 cancel=${cancel.text}")
            val terminal = awaitTerminal(client)
            log("p02 terminal=${terminal.text}")
            assertTrue(terminal.text, terminal.text.contains("state=CANCELLED"))
            assertTrue(terminal.text, terminal.text.contains("leftover=0"))
        } finally {
            client.close()
        }
    }

    @Test
    fun p03_deadlineKillsTraceeAndReportsTimeout(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            val start = client.prootStart(HelperContract.PROOT_OP_HOST_SLEEP)
            assertEquals(HelperContract.CODE_OK, start.code)
            val terminal = awaitTerminal(client)
            log("p03 terminal=${terminal.text}")
            assertTrue(terminal.text, terminal.text.contains("state=TIMEOUT"))
            assertTrue(terminal.text, terminal.text.contains("leftover=0"))
        } finally {
            client.close()
        }
    }

    /**
     * First real guest execution: the ORIGINAL PRoot runs /bin/sh of the Alpine x86_64 TEST rootfs.
     * Evidence required: guest markers printed, /bin/sh is a link to Alpine busybox, release 3.21.3,
     * exit 0, and no survivors. The output must not come from /system/bin.
     */
    @Test
    fun p04_alpineX86GuestProgramRunsUnderOriginalProot(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            val provision = awaitProvision(client)
            log("p04 provision=${provision.text}")
            assertTrue(provision.text, provision.text.contains("state=INSTALLED"))
            val start = client.prootStart(HelperContract.PROOT_OP_ALPINE_X86_PROBE)
            log("p04 start code=${start.code} text=${start.text}")
            assertEquals(HelperContract.CODE_OK, start.code)
            val terminal = awaitTerminal(client)
            log("p04 terminal=${terminal.text}")
            val text = terminal.text
            assertTrue(text, text.contains("state=EXITED"))
            assertTrue(text, text.contains("exit=0"))
            assertTrue(text, text.contains(HelperContract.GUEST_PROBE_BEGIN))
            assertTrue(text, text.contains("3.21.3"))
            // Only the Alpine tree has these files; /system/bin on Android does not.
            assertTrue(text, text.contains("NAME=\"Alpine Linux\""))
            assertTrue(text, text.contains("leftover=0"))
        } finally {
            client.close()
        }
    }

    private suspend fun awaitProvision(client: ComputerHelperClient): ComputerHelperClient.Reply {
        val first = client.testX86ProvisionStart()
        log("p04 provisionStart code=${first.code} text=${first.text}")
        val deadline = System.currentTimeMillis() + PROVISION_DEADLINE_MS
        var status = client.testX86ProvisionStatus()
        while (status.text.contains("state=RUNNING") && System.currentTimeMillis() < deadline) {
            delay(POLL_MS)
            status = client.testX86ProvisionStatus()
        }
        return status
    }

    private suspend fun awaitTerminal(client: ComputerHelperClient): ComputerHelperClient.Reply {
        val deadline = System.currentTimeMillis() + TERMINAL_DEADLINE_MS
        while (true) {
            val status = client.prootStatus()
            if (!status.text.contains("state=RUNNING") || System.currentTimeMillis() > deadline) return status
            delay(POLL_MS)
        }
    }

    private fun log(message: String) {
        Log.i("TamaIsolationFixture", "pocHelper: proot $message")
    }

    companion object {
        private const val RUN_BEFORE_CANCEL_MS = 1_000L
        private const val TERMINAL_DEADLINE_MS = 60_000L
        private const val POLL_MS = 500L
        private const val PROVISION_DEADLINE_MS = 120_000L
    }
}
