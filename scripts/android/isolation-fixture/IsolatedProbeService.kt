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
        if (request.data.getBoolean("probeFixedExec")) {
            // Two fixed, parameter-free binaries. No user-supplied commands.
            fun runFixed(path: String): String = runCatching {
                val child = ProcessBuilder(path).redirectErrorStream(true).start()
                try {
                    if (child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) "exit:" + child.exitValue()
                    else "timeout"
                } finally { if (child.isAlive) child.destroyForcibly() }
            }.getOrElse { failure ->
                val reason = when {
                    failure.message?.contains("error=13") == true -> "EACCES"
                    failure.message?.contains("error=1,") == true -> "EPERM"
                    failure.message?.contains("error=2,") == true -> "ENOENT"
                    else -> "other"
                }
                failure.javaClass.simpleName + ":" + reason
            }
            reply.data.putString("fixedExec", runFixed("/system/bin/true"))
            reply.data.putString("fixedNativeExec", runFixed(File(applicationInfo.nativeLibraryDir, "libfixture-exec.so").absolutePath))
        }
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
        reply.data.putBoolean("directoryPresent", directory != null)
        reply.data.putBoolean("directoryWrite", directoryOutcome.getOrDefault(false))
        reply.data.putString("directoryError", directoryError)
        request.replyTo.send(reply)
        true
    })
    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
