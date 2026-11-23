package com.openminis.app.sandbox

import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Caps total protocol-read time even if a peer dribbles data between reads. */
internal class OffloadDeadlineInputStream(
    private val source: InputStream,
    private val setReadTimeout: (Int) -> Unit,
    private val clock: () -> Long = System::nanoTime,
    private val deadlineNanos: Long = TimeUnit.SECONDS.toNanos(15),
    private val perReadMillis: Int = 10_000,
) : InputStream() {
    private val startedNanos = clock()

    private fun prepareRead() {
        val elapsed = clock() - startedNanos
        val remaining = deadlineNanos - elapsed
        if (remaining <= 0) throw SocketTimeoutException("offload request deadline exceeded")
        val millis = ((remaining + 999_999L) / 1_000_000L).coerceAtMost(perReadMillis.toLong()).toInt()
        setReadTimeout(millis.coerceAtLeast(1))
    }

    override fun read(): Int {
        prepareRead()
        return source.read()
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        prepareRead()
        return source.read(bytes, offset, length)
    }
}
