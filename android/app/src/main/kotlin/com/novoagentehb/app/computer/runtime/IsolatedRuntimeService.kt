package com.novoagentehb.app.computer.runtime

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process

/** Lifecycle and identity endpoint only. No shell or arbitrary execution IPC. */
class IsolatedRuntimeService : Service() {
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != TRANSACTION_IDENTITY) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            if (reply == null) return false
            reply.writeNoException()
            reply.writeInt(Process.myUid())
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        internal const val DESCRIPTOR = "com.novoagentehb.app.computer.runtime.IsolatedRuntimeService"
        internal const val TRANSACTION_IDENTITY = IBinder.FIRST_CALL_TRANSACTION

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
