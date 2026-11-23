package com.novoagentehb.app.computer.runtime

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/** Lifecycle-only isolated endpoint. Deliberately exposes no execution IPC. */
class IsolatedRuntimeService : Service() {
    private val binder = Binder()
    override fun onBind(intent: Intent?): IBinder = binder
}
