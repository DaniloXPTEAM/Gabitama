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
        assertEquals(files, shell("ls -d $files").trim())
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
            log("a01 tree: ${shell("ls $rootfs").trim().replace('\n', ' ')}")
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
            // Injected fault: stale staging dir with a zero-byte busybox (no redirection is possible via run-as).
            shell("mkdir -p $files/alpine-staging-junk/bin")
            shell("touch $files/alpine-staging-junk/bin/busybox")
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
            assertEquals(emptyList<String>(), names(files, "alpine-staging-"))
            assertEquals(emptyList<String>(), names(files, "alpine-trash-"))
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
                assertFalse(exists(rootfs))
                assertEquals(emptyList<String>(), names(files, "alpine-staging-"))
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
            assertEquals(emptyList<String>(), names(files, "alpine-staging-"))
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

    /**
     * Runs a command as the helper UID via run-as. UiAutomation.executeShellCommand does NOT use a shell:
     * quotes, redirections, pipes, globs and `;` become literal arguments, which made every earlier read
     * come back empty. So only plain words are passed; stdout is the only channel (stderr is dropped),
     * and "missing" is observed as empty output from `ls`.
     */
    private fun shell(command: String): String {
        val fd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand("run-as $helper $command")
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun exists(path: String): Boolean = shell("ls -d $path").trim().isNotEmpty()

    private fun names(dir: String, prefix: String): List<String> =
        shell("ls $dir").lines().map { it.trim() }.filter { it.startsWith(prefix) }

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
