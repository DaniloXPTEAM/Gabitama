package com.novoagentehb.app.app

/** Android per-user app IDs: regular app IDs are below 90000; isolated app
 * zygote and isolated service UIDs occupy 90000..99999 on supported Android.
 * Treat the full high range conservatively as isolated, including on API 26. */
internal object IsolatedUid {
    fun isIsolated(uid: Int): Boolean = uid >= 0 && uid % 100_000 >= 90_000
}
