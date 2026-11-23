package com.novoagentehb.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.app.isolationfixture.IsolatedProbeService
import com.novoagentehb.app.computer.runtime.IsolatedRuntimeService
import com.novoagentehb.app.app.NovoAgenteApplication
import com.novoagentehb.app.app.IsolatedUid
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class IsolatedProbeAndroidTest {
    @Test fun isolatedAndroidUidCannotReadAppPrivateDataOrUseInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Main process retains its real Hilt application and initialized Gabi graph.
        val application = context.applicationContext as NovoAgenteApplication
        assertNotNull(application.taskOrchestrator)
        assertNotNull(application.hbCharacterController)
        val runtimeReady = CountDownLatch(1)
        val runtimeBinder = AtomicReference<IBinder>()
        val runtimeConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { runtimeBinder.set(binder); runtimeReady.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(context.bindService(Intent(context, IsolatedRuntimeService::class.java), runtimeConnection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("production isolated runtime failed to start", runtimeReady.await(30, TimeUnit.SECONDS))
            val workerUid = IsolatedRuntimeService.endpointUid(runtimeBinder.get())
            assertNotEquals("production service shares host UID", Process.myUid(), workerUid)
            assertTrue("production service UID is not isolated", IsolatedUid.isIsolated(workerUid))
        }
        finally { context.unbindService(runtimeConnection) }
        val file = File.createTempFile("isolated-probe-", ".txt", context.cacheDir).apply { writeText("fixture") }
        val directory = createFixtureDirectory(context)
        val connected = CountDownLatch(1)
        val answered = CountDownLatch(1)
        val service = AtomicReference<Messenger>()
        val result = AtomicReference<android.os.Bundle>()
        val thread = HandlerThread("isolated-probe-result").apply { start() }
        val reply = Messenger(Handler(thread.looper) { msg -> result.set(msg.data); answered.countDown(); true })
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { service.set(Messenger(binder)); connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        var bound = false
        try {
            bound = context.bindService(Intent(context, IsolatedProbeService::class.java), conn, Context.BIND_AUTO_CREATE)
            assertTrue("bind failed", bound)
            assertTrue("isolated service did not start", connected.await(30, TimeUnit.SECONDS))
            val (readPipe, writePipe) = ParcelFileDescriptor.createPipe()
            ParcelFileDescriptor.AutoCloseOutputStream(writePipe).use { it.write("fixture-pipe".toByteArray()) }
            try {
                val request = Message.obtain().apply {
                    data.putString("probePath", file.absolutePath)
                    data.putParcelable("readPipe", readPipe)
                    replyTo = reply
                }
                service.get().send(request)
            } finally { readPipe.close() }
            assertTrue("isolated service did not respond", answered.await(15, TimeUnit.SECONDS))
            val data = result.get()
            val distinct = data.getInt("uid") != Process.myUid()
            val read = data.getBoolean("canReadPrivate")
            val writable = data.getBoolean("ownDataDirWritable")
            val internet = data.getBoolean("internetPermission")
            val grantedData = data.getBoolean("readGrantedData")
            Log.i("TamaIsolationFixture", "distinctUid=$distinct privateReadable=$read ownDataDirWritable=$writable internetPermission=$internet grantedPipeReadable=$grantedData")
            assertTrue("isolated UID must be distinct", distinct)
            assertFalse("private target file was readable", read)
            assertFalse("isolated UID unexpectedly has INTERNET", internet)
            assertTrue("isolated service could not read explicitly granted pipe", grantedData)

            // Separate exploratory transaction: Android may refuse transfer of a
            // directory descriptor or terminate the peer. Neither result is proof
            // that an isolated rootfs can be provisioned safely.
            val directoryReply = CountDownLatch(1)
            val directoryResult = AtomicReference<android.os.Bundle>()
            val directoryMessenger = Messenger(Handler(thread.looper) { msg ->
                directoryResult.set(msg.data); directoryReply.countDown(); true
            })
            val directoryFd = ParcelFileDescriptor.open(directory, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val request = Message.obtain().apply {
                    this.data.putParcelable("directoryFd", directoryFd)
                    replyTo = directoryMessenger
                }
                try {
                    service.get().send(request)
                    if (directoryReply.await(15, TimeUnit.SECONDS)) {
                        val wrote = directoryResult.get().getBoolean("directoryWrite")
                        assertEquals("directory FD outcome mismatch", wrote, File(directory, "isolated-fixture.txt").exists())
                        val error = directoryResult.get().getString("directoryError").orEmpty()
                        val present = directoryResult.get().getBoolean("directoryPresent")
                        Log.i("TamaIsolationFixture", "directoryFdPresent=$present directoryFdWritable=$wrote directoryError=$error")
                    } else Log.i("TamaIsolationFixture", "directoryTransfer=no-reply")
                } catch (failure: android.os.RemoteException) {
                    Log.i("TamaIsolationFixture", "directoryTransfer=${failure.javaClass.name}")
                } catch (failure: SecurityException) {
                    Log.i("TamaIsolationFixture", "directoryTransfer=${failure.javaClass.name}")
                }
            } finally { directoryFd.close() }
        } finally {
            if (bound) context.unbindService(conn)
            thread.quitSafely()
            file.delete()
            File(directory, "isolated-fixture.txt").delete()
            android.system.Os.chmod(directory.absolutePath, 448) // 0700
            directory.delete()
        }
    }

    private fun createFixtureDirectory(context: Context): File {
        val dir = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "isolated-dir-").toFile()
        // Only this synthetic directory is writable; its app-private parent is not shared.
        android.system.Os.chmod(dir.absolutePath, 511) // 0777, test only
        return dir
    }
}
