package com.novoagentehb.app.computer.helper

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.computerhelper.contract.HelperContract
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Experimental Alpine provisioning inside the separate helper package (debug/test builds only).
 * Runs in the GabiTama debug process and talks to the helper through fixed transactions. The helper's
 * own filesDir is inspected only with `run-as`, which works for debuggable builds, to inject
 * controlled faults (stale staging, corrupted install). Nothing here executes the rootfs.
 * Ordered: each step depends on the state left by the previous one; the workflow clears the helper first.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ComputerRootfsProvisionAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val helper = HelperContract.HELPER_PACKAGE
    private val files = "/data/user/0/$helper/files"
    private val rootfs = "$files/${HelperContract.ROOTFS_DIR}"

    @Test
    fun a01_provisionInstallsVerifiedAlpineInHelperPrivateStorage(): Unit = runBlocking<Unit> {
        // Probe: the shell channel must answer before any file-level assertion can mean anything.
        val probe = shell("echo probe").trim()
        log("a01 probe out=$probe rc=$lastShellRc")
        assertEquals("probe", probe)
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            val reply = client.provisionStart()
            log("a01 start code=${reply.code} text=${reply.text}")
            assertEquals(HelperContract.CODE_OK, reply.code)
            val final = awaitTerminal(client)
            log("a01 final=${final.text}")
            assertEquals(HelperContract.CODE_OK, final.code)
            assertTrue(final.text, final.text.startsWith("state=INSTALLED"))
            assertTrue(final.text, final.text.contains("release=${HelperContract.ALPINE_VERSION}"))
            assertTrue(final.text, final.text.contains("arch=aarch64"))
            assertTrue(final.text, final.text.contains("sha256=${HelperContract.ALPINE_SHA256}"))
            assertEquals(HelperContract.ALPINE_VERSION, shell("cat $rootfs/etc/alpine-release").trim())
            assertTrue(shell("ls -l $rootfs/bin/busybox").startsWith("-"))
            log("a01 tree: ${shell("find $rootfs -maxdepth 1 -mindepth 1 | sort | tr '\\n' ' '").trim()}")
        } finally {
            client.close()
        }
    }

    @Test
    fun a02_secondStartIsIdempotentAndDoesNotReextract(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            val before = shell("ls -li $rootfs/.gabitama-provision").trim()
            val reply = client.provisionStart()
            assertEquals(HelperContract.CODE_OK, reply.code)
            assertTrue(reply.text, reply.text.startsWith("state=INSTALLED"))
            val after = shell("ls -li $rootfs/.gabitama-provision").trim()
            log("a02 marker before=$before after=$after")
            assertEquals(before, after)
        } finally {
            client.close()
        }
    }

    @Test
    fun a03_installationSurvivesHelperProcessRestart(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            client.close()
            adb("am force-stop $helper")
            val status = client.provisionStatus()
            log("a03 after force-stop text=${status.text}")
            assertEquals(HelperContract.CODE_OK, status.code)
            assertTrue(status.text, status.text.startsWith("state=INSTALLED"))
            assertEquals(HelperContract.ALPINE_VERSION, shell("cat $rootfs/etc/alpine-release").trim())
        } finally {
            client.close()
        }
    }

    @Test
    fun a04_staleStagingAndCorruptedInstallAreRecoveredWithoutPartialActivation(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            shell("mkdir -p $files/alpine-staging-junk/bin && echo partial > $files/alpine-staging-junk/bin/busybox")
            shell("rm -f $rootfs/etc/alpine-release")
            adb("am force-stop $helper")
            val beforeStart = client.provisionStatus()
            log("a04 corrupted status=${beforeStart.text}")
            assertFalse(beforeStart.text, beforeStart.text.startsWith("state=INSTALLED"))
            val reply = client.provisionStart()
            assertEquals(HelperContract.CODE_OK, reply.code)
            val final = awaitTerminal(client)
            log("a04 final=${final.text}")
            assertTrue(final.text, final.text.startsWith("state=INSTALLED"))
            assertEquals(HelperContract.ALPINE_VERSION, shell("cat $rootfs/etc/alpine-release").trim())
            assertEquals("", shell("ls -d $files/alpine-staging-* 2>/dev/null").trim())
            assertEquals("", shell("ls -d $files/alpine-trash-* 2>/dev/null").trim())
        } finally {
            client.close()
        }
    }

    @Test
    fun a05_cancelLeavesNoPartialInstallAndRetryRecovers(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            shell("rm -rf $rootfs")
            assertEquals(HelperContract.CODE_OK, client.provisionStart().code)
            val cancel = client.provisionCancel()
            log("a05 cancel=${cancel.text}")
            val terminal = awaitTerminal(client)
            log("a05 terminal=${terminal.text}")
            if (terminal.text.startsWith("state=CANCELLED")) {
                assertEquals("", shell("ls -d $rootfs 2>/dev/null").trim())
                assertEquals("", shell("ls -d $files/alpine-staging-* 2>/dev/null").trim())
            } else {
                // Extraction finished before the cancel landed: the installation must then be complete.
                assertTrue(terminal.text, terminal.text.startsWith("state=INSTALLED"))
            }
            val retry = if (terminal.text.startsWith("state=INSTALLED")) {
                terminal
            } else {
                assertEquals(HelperContract.CODE_OK, client.provisionStart().code)
                awaitTerminal(client)
            }
            assertTrue(retry.text, retry.text.startsWith("state=INSTALLED"))
            assertEquals(HelperContract.ALPINE_VERSION, shell("cat $rootfs/etc/alpine-release").trim())
        } finally {
            client.close()
        }
    }

    @Test
    fun a06_concurrentStartsRunExactlyOneInstallation(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(instrumentation.targetContext)
        try {
            shell("rm -rf $rootfs")
            val replies = (1..5).map { async { client.provisionStart() } }.awaitAll()
            replies.forEach { reply ->
                log("a06 reply code=${reply.code} text=${reply.text}")
                assertEquals(HelperContract.CODE_OK, reply.code)
                assertTrue(reply.text, reply.text.startsWith("state=RUNNING") || reply.text.startsWith("state=INSTALLED"))
            }
            val final = awaitTerminal(client)
            assertTrue(final.text, final.text.startsWith("state=INSTALLED"))
            assertEquals("", shell("ls -d $files/alpine-staging-* 2>/dev/null").trim())
        } finally {
            client.close()
        }
    }

    private suspend fun awaitTerminal(client: ComputerHelperClient): ComputerHelperClient.Reply {
        val deadline = System.currentTimeMillis() + PROVISION_DEADLINE_MS
        while (true) {
            val status = client.provisionStatus()
            if (!status.text.startsWith("state=RUNNING") || System.currentTimeMillis() > deadline) return status
            delay(POLL_MS)
        }
    }

    /** Runs a shell command as the shell user (run-as for the helper), returning stdout. */
    private var lastShellRc: String? = null

    private fun shell(command: String): String {
        // stderr is merged into stdout and the exit code is appended, so an empty body can be told apart
        // from "run-as produced nothing at all".
        val wrapped = "run-as $helper sh -c '${command.replace("'", "'\\''")}' 2>&1; echo __rc=\$?"
        val fd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(wrapped)
        val raw = ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }
        val rc = Regex("__rc=(\\d+)").find(raw)?.groupValues?.get(1)
        val out = raw.replace(Regex("__rc=\\d+\\n?"), "")
        if (out.isBlank()) log("shell empty for: ${command.take(120)} rc=$rc raw=${raw.take(200)}")
        lastShellRc = rc
        return out
    }

    /** Runs a command with the shell user's privileges (needed for `am`), returning stdout. */
    private fun adb(command: String): String {
        val fd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun log(message: String) {
        Log.i("TamaIsolationFixture", "pocHelper: rootfs $message")
    }

    companion object {
        private const val PROVISION_DEADLINE_MS = 240_000L
        private const val POLL_MS = 1_000L
    }
}
