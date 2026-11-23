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
            fun runFixed(path: String, vararg args: String): String = runCatching {
                val child = ProcessBuilder(listOf(path) + args).redirectErrorStream(true).start()
                val capture = java.io.ByteArrayOutputStream()
                val drain = kotlin.concurrent.thread(isDaemon = true, name = "fixture-drain") {
                    runCatching {
                        child.inputStream.use { input ->
                            val bytes = ByteArray(1024)
                            while (true) {
                                val n = input.read(bytes)
                                if (n < 0) break
                                synchronized(capture) {
                                    val accepted = minOf(n, 16384 - capture.size())
                                    if (accepted > 0) capture.write(bytes, 0, accepted)
                                }
                            }
                        }
                    }
                }
                try {
                    if (!child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) "timeout"
                    else {
                        drain.join(500)
                        val output = synchronized(capture) { capture.toString("UTF-8") }
                        val reason = when {
                            output.contains("PROOT_TMP_DIR", ignoreCase = true) -> "tmp"
                            output.contains("Permission denied", ignoreCase = true) -> "EACCES"
                            output.contains("No such file or directory", ignoreCase = true) -> "ENOENT"
                            output.contains("ptrace", ignoreCase = true) -> "ptrace"
                            output.contains("unknown option", ignoreCase = true) -> "unknown-option"
                            output.contains("not found", ignoreCase = true) -> "not-found"
                            output.contains("not executable", ignoreCase = true) -> "not-executable"
                            output.contains("can't sanitize", ignoreCase = true) -> "sanitize"
                            output.contains("can't create glue rootfs", ignoreCase = true) -> "glue-rootfs"
                            output.contains("getcwd", ignoreCase = true) -> "getcwd"
                            output.contains("execve(", ignoreCase = true) -> "execve"
                            output.contains("fork()", ignoreCase = true) -> "fork"
                            else -> "other"
                        }
                        "exit:" + child.exitValue() + if (child.exitValue() != 0) ":" + reason else ""
                    }
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
            reply.data.putString("fixedPtrace", runFixed(File(applicationInfo.nativeLibraryDir, "libfixture-ptrace.so").absolutePath))
            // PRoot version only: no guest shell, no user input, no filesystem mount.
            reply.data.putString("fixedProotVersion", runFixed(File(applicationInfo.nativeLibraryDir, "libproot.so").absolutePath, "--version"))
            // Wrapper has fixed argv, a process-group kill deadline and no user input.
            reply.data.putString("fixedProotExec", runFixed(File(applicationInfo.nativeLibraryDir, "libfixture-proot.so").absolutePath))
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
        request.data.getString("directorySocket")?.let { socketName ->
            // Fixture-only Unix FD transfer; no arbitrary paths or commands.
            val transfer = runCatching {
                android.net.LocalSocket().use { socket ->
                    socket.soTimeout = 5000
                    socket.connect(android.net.LocalSocketAddress(socketName))
                    check(socket.inputStream.read() == 1)
                    val descriptors = socket.ancillaryFileDescriptors.orEmpty()
                    check(descriptors.size == 1)
                    try {
                        ParcelFileDescriptor.dup(descriptors[0]).use { received ->
                            File("/proc/self/fd/${received.fd}/isolated-unix.txt").let {
                                it.writeText("fixture-unix-directory")
                                check(it.readText() == "fixture-unix-directory")
                            }
                        }
                    } finally { android.system.Os.close(descriptors[0]) }
                }
                "writable"
            }.getOrElse { failure ->
                val reason = when {
                    failure.message?.contains("EACCES") == true -> "EACCES"
                    failure.message?.contains("EPERM") == true -> "EPERM"
                    failure.message?.contains("ENOENT") == true -> "ENOENT"
                    else -> "other"
                }
                failure.javaClass.simpleName + ":" + reason
            }
            reply.data.putString("unixDirectory", transfer)
        }
        request.replyTo.send(reply)
        true
    })
    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
