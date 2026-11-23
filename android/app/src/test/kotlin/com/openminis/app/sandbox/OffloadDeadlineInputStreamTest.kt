package com.openminis.app.sandbox

import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class OffloadDeadlineInputStreamTest {
    @Test fun tricklingInputCannotExtendAbsoluteDeadline() {
        var now = 0L
        val timeouts = mutableListOf<Int>()
        val stream = OffloadDeadlineInputStream(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)), { timeouts.add(it) },
            clock = { now }, deadlineNanos = 15_000_000_000L, perReadMillis = 10_000,
        )
        assertEquals(1, stream.read())
        now = 9_000_000_000L
        assertEquals(2, stream.read())
        assertEquals(listOf(10_000, 6_000), timeouts)
        now = 15_000_000_000L
        try { stream.read(); fail("read beyond absolute deadline") }
        catch (_: SocketTimeoutException) { }
    }
}
