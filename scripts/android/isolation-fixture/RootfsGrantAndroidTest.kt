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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.app.computer.runtime.RootfsGrantBroker
import com.novoagentehb.app.computer.runtime.RootfsGrantPolicy
import com.novoagentehb.app.isolationfixture.IsolatedProbeService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Disposable fixture for the rootfs grant broker. Proves on the emulator that:
 * the isolated UID reads only the descriptor explicitly granted, cannot read the same file by path,
 * and the broker refuses traversal, symlinks and directories before any descriptor is sent.
 */
@RunWith(AndroidJUnit4::class)
class RootfsGrantAndroidTest {
    @Test
    fun isolatedServiceReadsOnlyBrokerGrantedRegularFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "rootfs-grant-" + java.util.UUID.randomUUID())
        val links = mutableListOf<File>()
        try {
            File(root, "granted").mkdirs()
            val granted = File(root, "granted/data.txt").apply { writeText("grant-data") }
            File(root, "private.txt").writeText("private-data")
            File(root, "linked").also { links += it }
                .let { Files.createSymbolicLink(it.toPath(), File(root, "granted").toPath()) }
            File(root, "private-link.txt").also { links += it }
                .let { Files.createSymbolicLink(it.toPath(), File(root, "private.txt").toPath()) }

            assertTrue("granted file rejected", RootfsGrantPolicy.resolveRegularFile(root, "granted/data.txt") is RootfsGrantPolicy.Decision.Granted)
            for (path in listOf("linked/data.txt", "private-link.txt", "../private.txt", "granted/../private.txt", "/etc/hosts", "granted//data.txt", "granted")) {
                assertRefused(root, path)
            }

            val descriptor = RootfsGrantBroker.openRegularFile(root, "granted/data.txt")
            val answered = CountDownLatch(1)
            val connected = CountDownLatch(1)
            val service = AtomicReference<Messenger>()
            val result = AtomicReference<android.os.Bundle>()
            val thread = HandlerThread("rootfs-grant-result").apply { start() }
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
                val request = Message.obtain().apply {
                    data.putParcelable("grantFd", descriptor)
                    data.putString("grantPath", granted.absolutePath)
                    replyTo = reply
                }
                service.get().send(request)
                descriptor.close()
                assertTrue("isolated service did not respond", answered.await(30, TimeUnit.SECONDS))
                val data = result.get()
                assertNotEquals("isolated service shares host UID", Process.myUid(), data.getInt("uid"))
                assertTrue("granted descriptor was not readable", data.getBoolean("grantRead"))
                assertFalse("granted file was readable by path", data.getBoolean("grantPathReadable"))
            } finally {
                if (bound) context.unbindService(conn)
                thread.quitSafely()
            }
        } finally {
            links.forEach { Files.deleteIfExists(it.toPath()) }
            root.deleteRecursively()
        }
    }

    private fun assertRefused(root: File, path: String) {
        val refused = try {
            RootfsGrantBroker.openRegularFile(root, path).close()
            false
        } catch (_: SecurityException) {
            true
        }
        assertTrue("broker accepted $path", refused)
    }
}
