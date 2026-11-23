package com.novoagentehb.app.computer.runtime

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Restricted endpoint of the isolated computer worker. It accepts no paths, commands, shell input
 * or arguments. Transactions:
 *  - IDENTITY: UID only (kept for the existing health check);
 *  - STATUS: UID, PID, INTERNET permission state, execution state;
 *  - PROBE_REGULAR_FILE: SHA-256 of ONE descriptor, only if it is a regular file within limits;
 *  - DIAGNOSTIC_DIRECTORY: fixed-name write probe plus a fixed PRoot `-r` realpath probe, reporting
 *    evidence only. Neither transaction executes user-supplied code.
 */
class IsolatedRuntimeService : Service() {
    private val inFlight = AtomicBoolean(false)

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_IDENTITY -> {
                    data.enforceInterface(DESCRIPTOR)
                    if (reply == null) return false
                    reply.writeNoException()
                    reply.writeInt(Process.myUid())
                    true
                }
                TRANSACTION_STATUS -> {
                    data.enforceInterface(DESCRIPTOR)
                    if (reply == null) return false
                    val internet = checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
                    reply.writeNoException()
                    reply.writeInt(Process.myUid())
                    reply.writeInt(Process.myPid())
                    reply.writeInt(if (internet) 1 else 0)
                    reply.writeString(STATE_NO_EXECUTION)
                    true
                }
                TRANSACTION_PROBE_REGULAR_FILE -> {
                    data.enforceInterface(DESCRIPTOR)
                    if (reply == null) return false
                    @Suppress("DEPRECATION")
                    val descriptor = data.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader)
                    val probe = probe(descriptor)
                    reply.writeNoException()
                    reply.writeInt(probe.code)
                    reply.writeLong(probe.length)
                    reply.writeString(probe.sha256)
                    true
                }
                TRANSACTION_DIAGNOSTIC_DIRECTORY -> {
                    data.enforceInterface(DESCRIPTOR)
                    if (reply == null) return false
                    @Suppress("DEPRECATION")
                    val directory = data.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader)
                    val detail = diagnoseDirectory(directory)
                    reply.writeNoException()
                    reply.writeString(detail)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private class Probe(val code: Int, val length: Long, val sha256: String)

    /** Verifies and digests one regular-file descriptor. Always closes the descriptor it receives. */
    private fun probe(descriptor: ParcelFileDescriptor?): Probe {
        if (descriptor == null) return Probe(CODE_NO_DESCRIPTOR, 0, "")
        try {
            if (!inFlight.compareAndSet(false, true)) return Probe(CODE_BUSY, 0, "")
            try {
                val stat = Os.fstat(descriptor.fileDescriptor)
                if (!OsConstants.S_ISREG(stat.st_mode)) return Probe(CODE_NOT_REGULAR, 0, "")
                if (stat.st_size > MAX_PROBE_BYTES) return Probe(CODE_TOO_LARGE, 0, "")
                // Not closed here: the descriptor is owned by the finally block below.
                val input = FileInputStream(descriptor.fileDescriptor)
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var total = 0L
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROBE_TIMEOUT_SECONDS)
                while (true) {
                    if (System.nanoTime() > deadline) return Probe(CODE_TIMEOUT, total, "")
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_PROBE_BYTES) return Probe(CODE_TOO_LARGE, total, "")
                    digest.update(buffer, 0, n)
                }
                val hex = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                return Probe(CODE_OK, total, hex)
            } finally {
                inFlight.set(false)
            }
        } catch (_: Exception) {
            return Probe(CODE_ERROR, 0, "")
        } finally {
            descriptor.close()
        }
    }

    /**
     * Evidence-only: can this process write through a granted directory descriptor, and does the
     * PRoot binary resolve the descriptor as its `-r` root? The PRoot child receives no input and a
     * fixed argv; output is capped and the process is killed after a deadline.
     */
    private fun diagnoseDirectory(directory: ParcelFileDescriptor?): String {
        if (directory == null) return "no-descriptor"
        try {
            val fd = directory.fd
            val lines = mutableListOf<String>()
            lines += "pid=${Process.myPid()} uid=${Process.myUid()} fd=$fd"
            lines += try {
                File("/proc/self/fd/$fd/$DIAGNOSTIC_FILE").writeText("diagnostic-ok")
                "write=" + if (File("/proc/self/fd/$fd/$DIAGNOSTIC_FILE").readText() == "diagnostic-ok") "ok" else "mismatch"
            } catch (failure: Exception) {
                "write=${failure.javaClass.simpleName}:${(failure.message ?: "").take(160)}"
            }
            lines += try {
                "canonical=" + File("/proc/self/fd/$fd").canonicalPath
            } catch (failure: Exception) {
                "canonical=${failure.javaClass.simpleName}:${(failure.message ?: "").take(160)}"
            }
            lines += "proot=" + prootRootProbe("/proc/${Process.myPid()}/fd/$fd")
            return lines.joinToString("\n")
        } finally {
            directory.close()
        }
    }

    private fun prootRootProbe(rootPath: String): String {
        val proot = File(applicationInfo.nativeLibraryDir, "libproot.so")
        if (!proot.canExecute()) return "absent"
        val child = ProcessBuilder(proot.absolutePath, "-r", rootPath, "-w", "/", "/bin/true")
            .redirectErrorStream(true)
            .apply { environment()["PROOT_TMP_DIR"] = rootPath }
            .start()
        try {
            val finished = child.waitFor(PROOT_PROBE_SECONDS, TimeUnit.SECONDS)
            if (!finished) return "timeout"
            val output = child.inputStream.bufferedReader().use { it.readText() }.take(MAX_PROBE_OUTPUT)
            return "exit=${child.exitValue()} out=" + output.replace('\n', ' ')
        } finally {
            if (child.isAlive) child.destroyForcibly()
        }
    }

    companion object {
        internal const val DESCRIPTOR = "com.novoagentehb.app.computer.runtime.IsolatedRuntimeService"
        internal const val TRANSACTION_IDENTITY = IBinder.FIRST_CALL_TRANSACTION
        internal const val TRANSACTION_STATUS = IBinder.FIRST_CALL_TRANSACTION + 1
        internal const val TRANSACTION_PROBE_REGULAR_FILE = IBinder.FIRST_CALL_TRANSACTION + 2
        internal const val TRANSACTION_DIAGNOSTIC_DIRECTORY = IBinder.FIRST_CALL_TRANSACTION + 3

        internal const val STATE_NO_EXECUTION = "no-execution"
        internal const val DIAGNOSTIC_FILE = "isolated-diagnostic.txt"
        internal const val MAX_PROBE_BYTES = 64 * 1024
        internal const val PROBE_TIMEOUT_SECONDS = 5L
        internal const val PROOT_PROBE_SECONDS = 5L
        internal const val MAX_PROBE_OUTPUT = 2048

        internal const val CODE_OK = 0
        internal const val CODE_NO_DESCRIPTOR = 1
        internal const val CODE_NOT_REGULAR = 2
        internal const val CODE_TOO_LARGE = 3
        internal const val CODE_TIMEOUT = 4
        internal const val CODE_BUSY = 5
        internal const val CODE_ERROR = 6

        /** Startup diagnostic only; a compromised worker could forge its reply. */
        internal fun endpointUid(binder: IBinder): Int {
            val request = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                request.writeInterfaceToken(DESCRIPTOR)
                check(binder.transact(TRANSACTION_IDENTITY, request, reply, 0)) { "Runtime identity transaction rejected" }
                reply.readException()
                return reply.readInt()
            } finally {
                request.recycle()
                reply.recycle()
            }
        }
    }
}
