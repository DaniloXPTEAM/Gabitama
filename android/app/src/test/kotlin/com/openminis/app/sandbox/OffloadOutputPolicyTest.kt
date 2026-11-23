package com.openminis.app.sandbox

import org.junit.Assert.*
import org.junit.Test

class OffloadOutputPolicyTest {
    @Test fun rejectsOversizedOutputWithoutTurningItIntoSuccess() {
        val boundary = NativeOffloadResult(7, "x".repeat(OffloadOutputPolicy.MAX_CHARS))
        assertSame(boundary, OffloadOutputPolicy.bound(boundary))
        val refused = OffloadOutputPolicy.bound(NativeOffloadResult(0, "x".repeat(OffloadOutputPolicy.MAX_CHARS + 1)))
        assertEquals(2, refused.exitCode)
        assertEquals("native_offload: output exceeds reply limit\n", refused.output)
    }
}
