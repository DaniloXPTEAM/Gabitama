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
        // Fixed fixture directory FD only; never receives or executes commands.
        @Suppress("DEPRECATION")
        val directory = request.data.getParcelable<ParcelFileDescriptor>("directoryFd")
        val directoryOutcome = runCatching {
            directory != null && File("/proc/self/fd/${directory.fd}/isolated-fixture.txt").let {
                it.writeText("fixture-directory")
                it.readText() == "fixture-directory"
            }
        }
        val directoryError = directoryOutcome.exceptionOrNull()?.let { failure ->
            val reason = when {
                failure.message?.contains("EACCES") == true -> "EACCES"
                failure.message?.contains("EPERM") == true -> "EPERM"
                failure.message?.contains("ENOENT") == true -> "ENOENT"
                else -> "other"
            }
            failure.javaClass.simpleName + ":" + reason
        }.orEmpty()
        directory?.close()
        reply.data.putBoolean("directoryWrite", directoryOutcome.getOrDefault(false))
        reply.data.putString("directoryError", directoryError)
        request.replyTo.send(reply)
        true
    })
    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
