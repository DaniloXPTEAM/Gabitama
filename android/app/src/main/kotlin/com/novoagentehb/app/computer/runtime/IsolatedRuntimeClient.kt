package com.novoagentehb.app.computer.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject

/**
 * Privileged-side client of [IsolatedRuntimeService]. Each call binds, performs one fixed
 * transaction under a timeout, and unbinds exactly once in a non-cancellable finally block.
 * No path, command or shell input is ever sent; file access goes through [RootfsGrantBroker].
 */
class IsolatedRuntimeClient @Inject constructor(@ApplicationContext private val context: Context) {

    data class Status(val uid: Int, val pid: Int, val internetGranted: Boolean, val state: String)

    data class Probe(val code: Int, val length: Long, val sha256: String)

    suspend fun status(): Status = withService { binder ->
        call(binder, IsolatedRuntimeService.TRANSACTION_STATUS, {}) { reply ->
            val uid = reply.readInt()
            val pid = reply.readInt()
            val internet = reply.readInt() == 1
            Status(uid, pid, internet, reply.readString().orEmpty())
        }
    }

    /** Opens [relative] under [root] through the broker and asks the worker to digest it. */
    suspend fun probeGrantedFile(root: File, relative: String): Probe {
        val descriptor = RootfsGrantBroker.openRegularFile(root, relative)
        try {
            return probeDescriptor(descriptor)
        } finally {
            descriptor.close()
        }
    }

    /**
     * Sends a descriptor the caller already holds. The worker validates it independently (regular
     * file, size, single in-flight probe), so this is not a trust boundary by itself.
     */
    suspend fun probeDescriptor(descriptor: ParcelFileDescriptor?): Probe = withService { binder ->
        call(binder, IsolatedRuntimeService.TRANSACTION_PROBE_REGULAR_FILE, { it.writeParcelable(descriptor, 0) }) { reply ->
            val code = reply.readInt()
            val length = reply.readLong()
            Probe(code, length, reply.readString().orEmpty())
        }
    }

    /** Evidence-only diagnostic for a granted directory descriptor. Returns the worker's report. */
    suspend fun directoryDiagnostic(directory: File): String {
        val descriptor = ParcelFileDescriptor.open(directory, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            return withService { binder ->
                call(binder, IsolatedRuntimeService.TRANSACTION_DIAGNOSTIC_DIRECTORY, { it.writeParcelable(descriptor, 0) }) { reply ->
                    reply.readString().orEmpty()
                }
            }
        } finally {
            descriptor.close()
        }
    }

    private fun <T> call(binder: IBinder, code: Int, write: (Parcel) -> Unit, read: (Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(IsolatedRuntimeService.DESCRIPTOR)
            write(data)
            check(binder.transact(code, data, reply, 0)) { "Transação recusada pelo serviço isolado" }
            reply.readException()
            return read(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private suspend fun <T> withService(block: suspend (IBinder) -> T): T {
        val connected = CompletableDeferred<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) connected.completeExceptionally(IllegalStateException("No service binder"))
                else connected.complete(service)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                connected.completeExceptionally(IllegalStateException("Service disconnected"))
            }
            override fun onBindingDied(name: ComponentName?) {
                connected.completeExceptionally(IllegalStateException("Service binding died"))
            }
            override fun onNullBinding(name: ComponentName?) {
                connected.completeExceptionally(IllegalStateException("Service returned no binder"))
            }
        }
        val bound = withContext(Dispatchers.Main.immediate) {
            context.bindService(Intent(context, IsolatedRuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
        try {
            check(bound) { "Serviço isolado não vinculado" }
            val binder = withTimeout(CONNECT_TIMEOUT_MS) { connected.await() }
            return withTimeout(CALL_TIMEOUT_MS) { withContext(Dispatchers.IO) { block(binder) } }
        } finally {
            if (bound) {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    runCatching { context.unbindService(connection) }
                }
            }
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val CALL_TIMEOUT_MS = 20_000L
    }
}
