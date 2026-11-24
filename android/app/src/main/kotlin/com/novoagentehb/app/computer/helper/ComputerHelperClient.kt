package com.novoagentehb.app.computer.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.novoagentehb.computerhelper.contract.HelperContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Client used by the GabiTama app to call the experimental computer helper. Only fixed operations.
 *
 * Guarantees:
 * - explicit intent restricted to the helper package; the signature-level permission is enforced by Android;
 * - every call has a hard deadline: a scheduler completes the caller even while the Binder transaction
 *   is still blocked, and the binding is dropped so the next call rebinds;
 * - cancellation of the caller cancels the pending call;
 * - only read-only operations are retried after a dead binder; writes are never retried;
 * - an absent helper yields NOT_INSTALLED instead of an exception.
 */
class ComputerHelperClient(
    private val context: Context,
    private val helperPackage: String = HelperContract.HELPER_PACKAGE,
) {
    data class Reply(val code: Int, val text: String = "", val bytes: ByteArray = ByteArray(0))

    private val lock = Mutex()
    @Volatile private var connection: Connection? = null
    private val callers: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "helper-call").apply { isDaemon = true }
    }
    private val deadlines: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "helper-deadline").apply { isDaemon = true }
    }

    private class Connection(val binder: IBinder, val callback: ServiceConnection)

    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(helperPackage, 0)
        true
    } catch (ignored: PackageManager.NameNotFoundException) {
        false
    }

    suspend fun status(): Reply = transact(HelperContract.TX_STATUS, idempotent = true) {}

    suspend fun write(relative: String, payload: ByteArray): Reply {
        if (payload.size > HelperContract.MAX_PAYLOAD_BYTES) return Reply(HelperContract.CODE_TOO_LARGE)
        // Writes are never retried after a dead binder: the first attempt may already have taken effect.
        return transact(HelperContract.TX_WRITE, idempotent = false) {
            it.writeString(relative)
            it.writeByteArray(payload)
        }
    }

    suspend fun read(relative: String): Reply =
        transact(HelperContract.TX_READ, idempotent = true) { it.writeString(relative) }

    suspend fun sha256(relative: String): Reply =
        transact(HelperContract.TX_SHA256, idempotent = true) { it.writeString(relative) }

    /** Metadata-only access results about THIS app's private directories, as seen by the helper. */
    suspend fun accessProbe(): Reply = transact(HelperContract.TX_ACCESS_PROBE, idempotent = true) {
        it.writeString(context.packageName)
    }

    /**
     * Starts the fixed Alpine provisioning job in the helper. Takes no arguments. Idempotent: a running
     * or completed job is reported, not repeated, so a retried call after a dead binder is safe.
     */
    suspend fun provisionStart(): Reply = transact(HelperContract.TX_PROVISION_START, idempotent = true) {}

    suspend fun provisionStatus(): Reply = transact(HelperContract.TX_PROVISION_STATUS, idempotent = true) {}

    suspend fun provisionCancel(): Reply = transact(HelperContract.TX_PROVISION_CANCEL, idempotent = true) {}

    /**
     * Starts one fixed PRoot operation in the helper. Only an operation id is sent. Not idempotent:
     * a dead binder is reported instead of retried, so a second run is never started silently.
     */
    suspend fun prootStart(operation: Int): Reply = transact(HelperContract.TX_PROOT_START, idempotent = false) {
        it.writeInt(operation)
    }

    suspend fun prootStatus(): Reply = transact(HelperContract.TX_PROOT_STATUS, idempotent = true) {}

    suspend fun prootCancel(): Reply = transact(HelperContract.TX_PROOT_CANCEL, idempotent = true) {}

    /** Test-only Alpine x86_64 variant (debug/emulator proof). Same semantics as provisionStart. */
    suspend fun testX86ProvisionStart(): Reply =
        transact(HelperContract.TX_TEST_X86_PROVISION_START, idempotent = true) {}

    suspend fun testX86ProvisionStatus(): Reply =
        transact(HelperContract.TX_TEST_X86_PROVISION_STATUS, idempotent = true) {}

    /** Releases the binding. The next call binds again. */
    suspend fun close() {
        lock.withLock {
            val held = connection ?: return
            connection = null
            runCatching { context.unbindService(held.callback) }
        }
    }

    private suspend fun transact(code: Int, idempotent: Boolean, write: (Parcel) -> Unit): Reply {
        if (!isInstalled()) return Reply(HelperContract.CODE_NOT_INSTALLED)
        val first = attempt(code, write)
        if (first.code != CODE_DEAD) return first
        if (!idempotent) return Reply(HelperContract.CODE_IO)
        val second = attempt(code, write)
        return if (second.code == CODE_DEAD) Reply(HelperContract.CODE_IO) else second
    }

    private suspend fun attempt(code: Int, write: (Parcel) -> Unit): Reply {
        val binder = bind() ?: return Reply(HelperContract.CODE_DENIED)
        val future = CompletableFuture<Reply>()
        val job = callers.submit(Runnable {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(HelperContract.DESCRIPTOR)
                write(data)
                val ok = binder.transact(code, data, reply, 0)
                future.complete(if (ok) parse(reply) else Reply(HelperContract.CODE_IO))
            } catch (ignored: DeadObjectException) {
                future.complete(Reply(CODE_DEAD))
            } catch (failure: Exception) {
                Log.w(TAG, "helper call failed: ${failure.javaClass.simpleName}")
                future.complete(Reply(HelperContract.CODE_IO))
            } finally {
                data.recycle()
                reply.recycle()
            }
        })
        val deadline = deadlines.schedule(Runnable {
            if (future.complete(Reply(HelperContract.CODE_TIMEOUT))) {
                // The transaction may still be blocked; drop the binding so the next call starts fresh.
                job.cancel(true)
                dropConnection()
            }
        }, HelperContract.OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        return suspendCancellableCoroutine { cont ->
            future.whenComplete { result, _ ->
                deadline.cancel(false)
                if (result?.code == CODE_DEAD) dropConnection()
                cont.resume(result ?: Reply(HelperContract.CODE_IO))
            }
            cont.invokeOnCancellation {
                job.cancel(true)
                future.cancel(true)
                deadline.cancel(false)
            }
        }
    }

    private fun parse(reply: Parcel): Reply {
        reply.readException()
        val code = reply.readInt()
        val text = reply.readString().orEmpty()
        val bytes = reply.createByteArray() ?: ByteArray(0)
        return Reply(code, text, bytes)
    }

    private suspend fun bind(): IBinder? = lock.withLock { bindLocked() }

    private suspend fun bindLocked(): IBinder? {
        connection?.takeIf { it.binder.isBinderAlive }?.let { return it.binder }
        val ready = CompletableDeferred<IBinder?>()
        val callback = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                ready.complete(service)
            }
            override fun onServiceDisconnected(name: ComponentName?) = dropCallback(this)
            override fun onBindingDied(name: ComponentName?) = dropCallback(this)
            override fun onNullBinding(name: ComponentName?) {
                ready.complete(null)
                dropCallback(this)
            }
        }
        val intent = Intent(HelperContract.ACTION_BIND).setPackage(helperPackage)
        val bound = try {
            context.bindService(intent, callback, Context.BIND_AUTO_CREATE)
        } catch (ignored: SecurityException) {
            false
        }
        if (!bound) return null
        val binder = withTimeoutOrNull(BIND_TIMEOUT_MS) { ready.await() }
        if (binder == null) {
            runCatching { context.unbindService(callback) }
            return null
        }
        connection = Connection(binder, callback)
        return binder
    }

    private fun dropCallback(callback: ServiceConnection) {
        val current = connection ?: return
        if (current.callback === callback) {
            connection = null
            runCatching { context.unbindService(callback) }
        }
    }

    private fun dropConnection() {
        val current = connection ?: return
        connection = null
        runCatching { context.unbindService(current.callback) }
    }

    companion object {
        private const val TAG = "ComputerHelperClient"
        private const val BIND_TIMEOUT_MS = 3_000L
        /** Internal marker for a dead binder. Never returned to callers. */
        private const val CODE_DEAD = -1
    }
}
