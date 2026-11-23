package com.novoagentehb.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openminis.app.sandbox.PRootKernel
import com.openminis.app.sandbox.NativeOffloadServer
import com.openminis.app.sandbox.NativeOffloadResult
import com.openminis.app.sandbox.OffloadReplyFiles
import com.openminis.app.sandbox.PersistentShell
import com.openminis.app.sandbox.RootfsManager
import com.openminis.app.sandbox.TerminalSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Disposable AVD only. Commands are fixed test fixtures; never accepts agent or user input. */
@RunWith(AndroidJUnit4::class)
class OriginalShellRuntimeAndroidTest {
    @Test fun bootAndRunOriginalPersistentShellAndPty() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = RootfsManager.getInstance(context)
        assertFalse("AVD must start without any rootfs", root.rootfsDir.exists())
        val offloadSession = AtomicReference<String?>(null)
        NativeOffloadServer.register("fixture-offload") { request ->
            offloadSession.set(request.sessionId)
            NativeOffloadResult(0, "OFFLOAD_FIXTURE_OK\n")
        }
        coroutineScope {
            List(4) { async(Dispatchers.IO) { PRootKernel.boot(context) } }.awaitAll()
        }
        assertTrue(root.isInstalled)
        // Existing symlink at the final component must not redirect host writes.
        val victim = java.io.File(context.cacheDir, "offload-victim").apply { writeText("unchanged") }
        val link = java.io.File(root.rootfsDir, "tmp/offload-symlink-fixture")
        java.nio.file.Files.createSymbolicLink(link.toPath(), victim.toPath())
        try {
            try { OffloadReplyFiles.writeExclusive(link, "changed"); fail("followed reply symlink") }
            catch (_: android.system.ErrnoException) { }
            assertEquals("unchanged", victim.readText())
        } finally { link.delete(); victim.delete() }
        assertTrue(PRootKernel.isBooted)
        // A process not registered as a PRoot peer must be disconnected before
        // the server parses any protocol bytes or dispatches a handler.
        android.net.LocalSocket().use { untrusted ->
            untrusted.connect(android.net.LocalSocketAddress(NativeOffloadServer.socketName))
            untrusted.soTimeout = 5000
            assertEquals("unregistered offload socket should close", -1, untrusted.inputStream.read())
        }
        assertNull(PRootKernel.resolveHostPath("/../../outside"))
        assertNull(PRootKernel.resolveSessionHostPath("../outside", "/var/minis/workspace/file", context))
        val escape = java.io.File(root.rootfsDir, "tmp/fixture-escape")
        java.nio.file.Files.createSymbolicLink(escape.toPath(), context.filesDir.parentFile.toPath())
        assertNull(PRootKernel.resolveHostPath("/tmp/fixture-escape/anything"))
        repeat(10) {
            val interrupted = TerminalSession(context)
            interrupted.start()
            interrupted.stop()
            delay(50)
            assertEquals(TerminalSession.State.STOPPED, interrupted.state.value)
        }
        val forkTest = java.io.File(root.rootfsDir, "tmp/fork_regs")
        InstrumentationRegistry.getInstrumentation().context.assets.open("fork_regs.bin").use { source ->
            forkTest.outputStream().use { source.copyTo(it) }
        }
        assertTrue(forkTest.setExecutable(true, false))
        val shell = PersistentShell(context, "fixture-session", emptyMap())
        try {
            val first = shell.executeCommand("export FIXTURE_VALUE=works; cd /tmp; printf 'FIRST\\n'", 30000)
            assertEquals("first command: ${first.first}", 0, first.second)
            assertTrue(first.first.contains("FIRST"))
            val second = shell.executeCommand("printf '%s:%s\\n' \"\$FIXTURE_VALUE\" \"\$PWD\"", 30000)
            assertEquals("persistent command: ${second.first}", 0, second.second)
            assertTrue(second.first.contains("works:/tmp"))
            val external = shell.executeCommand("/bin/busybox echo EXTERNAL_OK", 30000)
            assertEquals("external result=${external.first}; shell exit=${shell.lastExitCode}", 0, external.second)
            assertTrue(external.first.contains("EXTERNAL_OK"))
            val offload = shell.executeCommand("MINIS_CHAT_SESSION_ID=forged fixture-offload", 30000)
            assertEquals("native offload result: ${offload.first}", 0, offload.second)
            assertTrue(offload.first.contains("OFFLOAD_FIXTURE_OK"))
            assertEquals("fixture-session", offloadSession.get())
            val registers = shell.executeCommand("/tmp/fork_regs", 30000)
            assertEquals("fork register check: ${registers.first}", 0, registers.second)
            val failure = shell.executeCommand("false", 30000)
            assertEquals(1, failure.second)
        } finally {
            shell.stop()
        }
        // Verify a timed-out command cannot mutate the guest later. This uses fixed,
        // synthetic files in a disposable AVD, never user-provided shell text.
        val timeoutShell = PersistentShell(context, "fixture-timeout", emptyMap())
        timeoutShell.ensureStarted()
        assertTrue("timeout fixture shell startup exit=${timeoutShell.lastExitCode}", timeoutShell.isAlive)
        val late = java.io.File(root.rootfsDir, "tmp/fixture-after-timeout")
        val timed = timeoutShell.executeCommand("touch /tmp/fixture-started; sleep 4; touch /tmp/fixture-after-timeout", 2000)
        assertEquals("timeout result=${timed.first}; shell exit=${timeoutShell.lastExitCode}", 124, timed.second)
        assertTrue(java.io.File(root.rootfsDir, "tmp/fixture-started").exists())
        delay(5000)
        assertFalse("timed-out process continued writing", late.exists())
        assertFalse(timeoutShell.isAlive)

        val cancelledShell = PersistentShell(context, "fixture-cancel", emptyMap())
        val started = java.io.File(root.rootfsDir, "tmp/fixture-cancel-started")
        val afterCancel = java.io.File(root.rootfsDir, "tmp/fixture-after-cancel")
        val job = launch(Dispatchers.IO) {
            cancelledShell.executeCommand("touch /tmp/fixture-cancel-started; sleep 4; touch /tmp/fixture-after-cancel", 30000)
        }
        try {
            withTimeout(15000) { while (!started.exists()) delay(50) }
            job.cancelAndJoin()
            delay(5000)
            assertFalse("cancelled process continued writing", afterCancel.exists())
            assertFalse(cancelledShell.isAlive)
        } finally { job.cancel(); cancelledShell.stop() }

        val terminal = TerminalSession(context)
        val seen = AtomicReference("")
        val collector = launch(Dispatchers.IO) {
            terminal.outputBytes.collect { bytes -> seen.updateAndGet { it + String(bytes, Charsets.UTF_8) } }
        }
        try {
            terminal.start("fixture-terminal")
            withTimeout(30000) { while (!terminal.isRunning) { if (terminal.state.value == TerminalSession.State.STOPPED) error("PTY stopped during boot"); delay(50) } }
            terminal.sendText("printf '%s%s\\n' PTY_FIXTURE _OK\n")
            withTimeout(30000) { while (!seen.get().contains("PTY_FIXTURE_OK")) delay(50) }
            val ptyStarted = java.io.File(root.rootfsDir, "tmp/fixture-pty-started")
            val ptyLate = java.io.File(root.rootfsDir, "tmp/fixture-pty-after-stop")
            terminal.sendText(": > /tmp/fixture-pty-started; sleep 4; : > /tmp/fixture-pty-after-stop\n")
            withTimeout(15000) { while (!ptyStarted.exists()) delay(50) }
            terminal.stop()
            delay(5000)
            assertFalse("PTY guest continued after stop", ptyLate.exists())
        } finally {
            terminal.stop()
            collector.cancelAndJoin()
        }
    }
}
