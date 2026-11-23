package com.novoagentehb.app.computer.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import com.novoagentehb.app.app.IsolatedUid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** On-demand lifecycle diagnostic only; it does not authorize execution. */
class IsolatedRuntimeHealth @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun isStartedAndIsolated(): Boolean = withContext(Dispatchers.Main.immediate) {
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
        var bound = false
        try {
            bound = context.bindService(Intent(context, IsolatedRuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) return@withContext false
            withTimeout(10_000) {
                val binder = connected.await()
                val uid = withContext(Dispatchers.IO) { IsolatedRuntimeService.endpointUid(binder) }
                uid != Process.myUid() && IsolatedUid.isIsolated(uid)
            }
        } catch (_: TimeoutCancellationException) {
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            if (bound) runCatching { context.unbindService(connection) }
        }
    }
}
