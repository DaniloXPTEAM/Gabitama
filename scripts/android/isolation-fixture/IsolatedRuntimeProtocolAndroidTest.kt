package com.novoagentehb.app

import android.system.Os
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.app.app.IsolatedUid
import com.novoagentehb.app.computer.runtime.IsolatedRuntimeClient
import com.novoagentehb.app.computer.runtime.IsolatedRuntimeService
import kotlinx.coroutines.Dispatchers
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
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

/**
 * Exercises the PRODUCTION IsolatedRuntimeService through IsolatedRuntimeClient on the isolated
 * UID: granted-descriptor verification, refusals before IPC, worker-side rejection of non-regular
 * descriptors, bounded concurrency, cancellation and reconnect. Also records the PRoot `-r` and
 * directory-descriptor diagnostic as evidence (logcat tag TamaRuntimeProtocol).
 */
@RunWith(AndroidJUnit4::class)
class IsolatedRuntimeProtocolAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val client = IsolatedRuntimeClient(context)

    private fun newRoot(): File = File(context.noBackupFilesDir, "runtime-probe-" + UUID.randomUUID()).apply { mkdirs() }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun productionWorkerVerifiesOnlyGrantedRegularFiles() = runBlocking<Unit> {
        val root = newRoot()
        val links = mutableListOf<File>()
        try {
            val content = "gabitama-probe-v1".toByteArray()
            val file = File(root, "probe.txt").apply { writeBytes(content) }
            File(root, "link.txt").also { links += it }.let { Files.createSymbolicLink(it.toPath(), file.toPath()) }
            File(root, "big.bin").writeBytes(ByteArray(IsolatedRuntimeService.MAX_PROBE_BYTES + 1))

            val status = client.status()
            assertNotEquals("worker shares host UID", android.os.Process.myUid(), status.uid)
            assertTrue("worker UID not isolated", IsolatedUid.isIsolated(status.uid))
            assertFalse("worker has INTERNET permission", status.internetGranted)
            assertEquals(IsolatedRuntimeService.STATE_NO_EXECUTION, status.state)

            val granted = client.probeGrantedFile(root, "probe.txt")
            assertEquals(IsolatedRuntimeService.CODE_OK, granted.code)
            assertEquals(content.size.toLong(), granted.length)
            assertEquals(sha256Hex(content), granted.sha256)

            // Broker refusals happen in the privileged process; no descriptor reaches the worker.
            for (rel in listOf("link.txt", "", "../probe.txt", ".", "probe.txt/", "missing.txt")) assertRefused { client.probeGrantedFile(root, rel) }

            // Worker-side checks, independent of the broker.
            val dir = ParcelFileDescriptor.open(root, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                assertEquals(IsolatedRuntimeService.CODE_NOT_REGULAR, client.probeDescriptor(dir).code)
            } finally { dir.close() }
            assertEquals(IsolatedRuntimeService.CODE_NO_DESCRIPTOR, client.probeDescriptor(null).code)
            assertEquals(IsolatedRuntimeService.CODE_TOO_LARGE, client.probeGrantedFile(root, "big.bin").code)

            // Evidence only: directory descriptor write and PRoot -r resolution from the worker.
            val diagDir = File(context.noBackupFilesDir, "runtime-diag-" + UUID.randomUUID()).apply { mkdirs() }
            Os.chmod(diagDir.absolutePath, 511)
            try {
                Log.i("TamaRuntimeProtocol", "directoryDiagnostic:\n" + client.directoryDiagnostic(diagDir))
            } finally {
                File(diagDir, IsolatedRuntimeService.DIAGNOSTIC_FILE).delete()
                diagDir.delete()
            }
        } finally {
            client.close()
            links.forEach { Files.deleteIfExists(it.toPath()) }
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentCallsStayBoundedAndWorkerSurvivesCancelAndReconnect() = runBlocking<Unit> {
        val root = newRoot()
        try {
            File(root, "probe.txt").writeBytes(ByteArray(60_000) { 7 })
            val results = (1..4).map { async(Dispatchers.IO) { client.probeGrantedFile(root, "probe.txt") } }.awaitAll()
            assertTrue("unexpected codes: " + results.map { it.code }, results.all {
                it.code == IsolatedRuntimeService.CODE_OK || it.code == IsolatedRuntimeService.CODE_BUSY
            })
            assertTrue("no concurrent call completed", results.any { it.code == IsolatedRuntimeService.CODE_OK })
            Log.i("TamaRuntimeProtocol", "concurrentCodes=" + results.map { it.code })

            val cancelled = launch(Dispatchers.IO) { client.probeGrantedFile(root, "probe.txt") }
            cancelled.cancelAndJoin()

            // Same shared binding -> same worker instance; after close, a new instance must be isolated.
            val first = client.status()
            val second = client.status()
            assertEquals("worker instance changed while binding was shared", first.pid, second.pid)
            client.close()
            val third = client.status()
            assertTrue("reconnected worker UID not isolated", IsolatedUid.isIsolated(third.uid))
            assertEquals("worker not reachable after cancellation", IsolatedRuntimeService.CODE_OK, client.probeGrantedFile(root, "probe.txt").code)
        } finally {
            client.close()
            root.deleteRecursively()
        }
    }

    private suspend fun assertRefused(block: suspend () -> Unit) {
        val refused = try {
            block()
            false
        } catch (_: SecurityException) {
            true
        }
        assertTrue("request was not refused before IPC", refused)
    }
}
