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
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.app.isolationfixture.IsolatedProbeService
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
        val file = File.createTempFile("isolated-probe-", ".txt", context.cacheDir).apply { writeText("fixture") }
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
            val request = Message.obtain().apply { data.putString("probePath", file.absolutePath); replyTo = reply }
            service.get().send(request)
            assertTrue("isolated service did not respond", answered.await(15, TimeUnit.SECONDS))
            val data = result.get()
            val distinct = data.getInt("uid") != Process.myUid()
            val read = data.getBoolean("canReadPrivate")
            val writable = data.getBoolean("ownDataDirWritable")
            val internet = data.getBoolean("internetPermission")
            Log.i("TamaIsolationFixture", "distinctUid=$distinct privateReadable=$read ownDataDirWritable=$writable internetPermission=$internet")
            assertTrue("isolated UID must be distinct", distinct)
            assertFalse("private target file was readable", read)
            assertFalse("isolated UID unexpectedly has INTERNET", internet)
        } finally {
            if (bound) context.unbindService(conn)
            thread.quitSafely()
            file.delete()
        }
    }
}
