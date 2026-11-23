package com.novoagentehb.app.computer.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privileged-side client of [IsolatedRuntimeService].
 *
 * One shared binding is kept per process so concurrent calls do not race with per-call unbinding
 * (which previously let the platform stop the worker under an in-flight transaction). If the worker
 * dies or disconnects, the binding is invalidated and the next call rebinds, with exactly one retry.
 * No path, command or shell input is sent; file access goes through [RootfsGrantBroker].
 */
@Singleton
class IsolatedRuntimeClient @Inject constructor(@ApplicationContext private val context: Context) {

    data class Status(val uid: Int, val pid: Int, val internetGranted: Boolean, val state: String)

    data class Probe(val code: Int, val length: Long, val sha256: String)

    private class Connection(val connection: ServiceConnection, val binder: IBinder)

    private val lock = Mutex()
    @Volatile private var current: Connection? = null

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

    /** Releases the shared binding. The next call binds a new worker instance. */
    suspend fun close() {
        lock.withLock {
            val held = current ?: return
            current = null
            unbind(held)
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
        for (attempt in 1..2) {
            val binder = bindShared()
            try {
                return withTimeout(CALL_TIMEOUT_MS) { withContext(Dispatchers.IO) { block(binder) } }
            } catch (dead: RemoteException) {
                invalidate(binder)
                if (attempt == 2) throw dead
            }
        }
        error("unreachable")
    }

    private suspend fun bindShared(): IBinder = lock.withLock {
        current?.let { return it.binder }
        val connected = CompletableDeferred<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) connected.completeExceptionally(IllegalStateException("No service binder"))
                else connected.complete(service)
            }
            override fun onServiceDisconnected(name: ComponentName?) = drop(this, "Service disconnected")
            override fun onBindingDied(name: ComponentName?) = drop(this, "Service binding died")
            override fun onNullBinding(name: ComponentName?) = drop(this, "Service returned no binder")

            private fun drop(self: ServiceConnection, reason: String) {
                if (!connected.isCompleted) connected.completeExceptionally(IllegalStateException(reason))
                if (current?.connection === self) current = null
            }
        }
        val bound = withContext(Dispatchers.Main.immediate) {
            context.bindService(Intent(context, IsolatedRuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
        try {
            check(bound) { "Serviço isolado não vinculado" }
            val binder = withTimeout(CONNECT_TIMEOUT_MS) { connected.await() }
            current = Connection(connection, binder)
            binder
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.Main.immediate) { runCatching { context.unbindService(connection) } }
            throw failure
        }
    }

    /** Drops the shared binding after a dead transaction, so the next call binds a fresh worker. */
    private suspend fun invalidate(binder: IBinder) {
        lock.withLock {
            val held = current ?: return
            if (held.binder !== binder) return
            current = null
            unbind(held)
        }
    }

    private suspend fun unbind(held: Connection) {
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            runCatching { context.unbindService(held.connection) }
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val CALL_TIMEOUT_MS = 20_000L
    }
}
