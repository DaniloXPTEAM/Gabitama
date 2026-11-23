package com.novoagentehb.app.isolationfixture

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.ParcelFileDescriptor
import java.io.File

/** Disposable debug fixture. No shell, network, credentials or arbitrary command execution. */
class IsolatedProbeService : Service() {
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { request ->
        val path = request.data.getString("probePath").orEmpty()
        val reply = Message.obtain()
        reply.data.putInt("uid", Process.myUid())
        reply.data.putBoolean("canReadPrivate", runCatching { File(path).readText() == "fixture" }.getOrDefault(false))
        reply.data.putBoolean("ownDataDirWritable", runCatching { filesDir.canWrite() }.getOrDefault(false))
        reply.data.putBoolean("internetPermission", checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED)
        @Suppress("DEPRECATION")
        val pipe = request.data.getParcelable<ParcelFileDescriptor>("readPipe")
        val readGrantedData = runCatching {
            pipe != null && ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use { it.readText() } == "fixture-pipe"
        }.getOrDefault(false)
        reply.data.putBoolean("readGrantedData", readGrantedData)
        request.replyTo.send(reply)
        true
    })
    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
