package com.novoagentehb.app.computer.helper

import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.computerhelper.contract.HelperContract
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Proof of concept, GabiTama side. Runs in the GabiTama debug process (target app) against the
 * separate experimental helper package. Only fixed operations; no shell; synthetic data only.
 */
@RunWith(AndroidJUnit4::class)
class ComputerHelperPocAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val helper = HelperContract.HELPER_PACKAGE

    @Test
    fun separatePackageHasOwnUidAndNoInternet(): Unit = runBlocking<Unit> {
        val pm = context.packageManager
        val helperUid = pm.getApplicationInfo(helper, 0).uid
        log("uids gabi=${Process.myUid()} helper=$helperUid")
        assertNotEquals(Process.myUid(), helperUid)
        assertEquals(PackageManager.PERMISSION_DENIED, pm.checkPermission("android.permission.INTERNET", helper))
    }

    @Test
    fun authorizedClientReceivesStructuredStatusFromOwnTree(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(context)
        try {
            val status = client.status()
            log("status code=${status.code} text=${status.text}")
            assertEquals(HelperContract.CODE_OK, status.code)
            assertTrue(status.text, status.text.contains("internet=false"))
        } finally {
            client.close()
        }
    }

    @Test
    fun writeReadAndDigestInsideHelperTree(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(context)
        try {
            val payload = "synthetic-poc-${UUID.randomUUID()}".toByteArray()
            val path = "poc/data.txt"
            assertEquals(HelperContract.CODE_OK, client.write(path, payload).code)
            val read = client.read(path)
            assertEquals(HelperContract.CODE_OK, read.code)
            assertTrue(read.bytes.contentEquals(payload))
            val digest = client.sha256(path)
            assertEquals(HelperContract.CODE_OK, digest.code)
            assertEquals(sha256(payload), digest.text)
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectsTraversalAbsoluteAndOversizedRequests(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(context)
        try {
            assertEquals(HelperContract.CODE_BAD_PATH, client.write("../escape.txt", byteArrayOf(1)).code)
            assertEquals(HelperContract.CODE_BAD_PATH, client.write("/abs.txt", byteArrayOf(1)).code)
            assertEquals(HelperContract.CODE_BAD_PATH, client.read("a/../../x").code)
            assertEquals(HelperContract.CODE_TOO_LARGE, client.write("poc/big.bin", ByteArray(HelperContract.MAX_PAYLOAD_BYTES + 1)).code)
            assertEquals(HelperContract.CODE_NOT_FOUND, client.read("poc/absent.txt").code)
        } finally {
            client.close()
        }
    }

    @Test
    fun helperCannotReadGabiPrivateDirectories(): Unit = runBlocking<Unit> {
        val marker = "private-marker-${UUID.randomUUID()}"
        val secret = File(context.filesDir, "computer-poc-private-marker.txt").apply { writeText(marker) }
        val client = ComputerHelperClient(context)
        try {
            val probe = client.accessProbe()
            log("accessProbe code=${probe.code} text=${probe.text}")
            assertEquals(HelperContract.CODE_OK, probe.code)
            // Denial property: no target is readable. The errno may be EACCES (path visible, access
            // refused) or ENOENT (path not visible to the helper's namespace); both mean no access.
            // The exact errno per API is recorded in the log line above.
            assertFalse(probe.text, probe.text.contains("readable"))
            for (target in HelperContract.PROBE_TARGETS) {
                assertTrue(probe.text, probe.text.contains("$target=EACCES") || probe.text.contains("$target=ENOENT"))
            }
            assertFalse(probe.text.contains(marker))
        } finally {
            client.close()
            secret.delete()
        }
    }

    @Test
    fun concurrentCallsAreBoundedAndNeverCrash(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(context)
        try {
            val codes = (1..5).map { async { client.status().code } }.awaitAll()
            log("concurrent codes=$codes")
            assertTrue(codes.all { it == HelperContract.CODE_OK || it == HelperContract.CODE_BUSY })
            assertTrue(codes.any { it == HelperContract.CODE_OK })
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationDoesNotBreakTheClient(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(context)
        try {
            val job = launch { client.write("poc/cancelled.bin", ByteArray(60 * 1024)) }
            job.cancelAndJoin()
            assertEquals(HelperContract.CODE_OK, client.status().code)
        } finally {
            client.close()
        }
    }

    @Test
    fun dataSurvivesHelperDeathAndClientRecovers(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(context)
        try {
            val payload = "persist-${UUID.randomUUID()}".toByteArray()
            assertEquals(HelperContract.CODE_OK, client.write("poc/persist.txt", payload).code)
            forceStopHelper()
            // After the helper dies, a read-only call must recover by rebinding. Allow a few attempts
            // because the system may still be tearing down the previous process.
            var status = client.status()
            repeat(3) { if (status.code != HelperContract.CODE_OK) status = client.status() }
            log("after force-stop status=${status.code}")
            assertEquals(HelperContract.CODE_OK, status.code)
            val read = client.read("poc/persist.txt")
            assertEquals(HelperContract.CODE_OK, read.code)
            assertTrue(read.bytes.contentEquals(payload))
        } finally {
            client.close()
        }
    }

    @Test
    fun absentHelperIsReportedWithoutCrashing(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(context, helperPackage = "com.novoagentehb.computerhelper.absent")
        try {
            assertEquals(HelperContract.CODE_NOT_INSTALLED, client.status().code)
        } finally {
            client.close()
        }
    }

    @Test
    fun clientRebindsAfterExplicitClose(): Unit = runBlocking<Unit> {
        val client = ComputerHelperClient(context)
        try {
            assertEquals(HelperContract.CODE_OK, client.status().code)
            client.close()
            assertEquals(HelperContract.CODE_OK, client.status().code)
        } finally {
            client.close()
        }
    }

    private fun forceStopHelper() {
        instrumentation.uiAutomation.executeShellCommand("am force-stop $helper").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun log(message: String) {
        Log.i("TamaIsolationFixture", "pocHelper: $message")
    }
}
