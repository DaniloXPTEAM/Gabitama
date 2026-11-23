package com.openminis.app.sandbox

/** Hard cap for one offload reply. No implicit truncation presented as success. */
internal object OffloadOutputPolicy {
    const val MAX_CHARS = 1_048_576
    fun bound(result: NativeOffloadResult): NativeOffloadResult =
        if (result.output.length <= MAX_CHARS) result
        else NativeOffloadResult(2, "native_offload: output exceeds reply limit\n")
}
