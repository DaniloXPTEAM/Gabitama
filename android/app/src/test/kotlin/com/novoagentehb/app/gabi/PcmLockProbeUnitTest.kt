package com.novoagentehb.app.gabi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Controlled probe of the locking of PcmPlaybackEngine as it was in bf041fc (lines 25-48: the blocking
 * track.write() ran inside synchronized(lock), the same lock used by invalidate() and silence()).
 * The blocking write is simulated with a latch; the probe demonstrates the coupling of the lock, not the
 * real blocking time of android.media.AudioTrack, which is not measured here.
 */
class PcmLockProbeUnitTest {

    /** Verbatim locking structure of the bf041fc write/invalidate, with the track write injected. */
    private class LegacyLockedWriter(private val blockingWrite: (Int) -> Int) {
        private val lock = Any()
        private val gen = AtomicLong()
        val generation: Long get() = gen.get()

        fun write(chunkGeneration: Long, size: Int) {
            synchronized(lock) {
                if (chunkGeneration != gen.get()) return
                blockingWrite(size) // bf041fc: checkNotNull(track).write(...) inside synchronized(lock)
            }
        }

        fun invalidate() {
            synchronized(lock) { gen.incrementAndGet() }
        }
    }

    @Test
    fun legacyInvalidateWaitsWhileAWriteIsBlockedUnderTheLock() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writer = LegacyLockedWriter { size ->
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            size
        }
        val writerThread = thread { writer.write(writer.generation, 1920) }
        assertTrue("the write must be inside the lock", entered.await(2, TimeUnit.SECONDS))

        val invalidated = CountDownLatch(1)
        val invalidator = thread {
            writer.invalidate()
            invalidated.countDown()
        }
        val returnedWhileBlocked = invalidated.await(300, TimeUnit.MILLISECONDS)
        assertFalse("bf041fc: invalidate() cannot run while the write holds the lock", returnedWhileBlocked)

        release.countDown()
        writerThread.join(2_000)
        invalidator.join(2_000)
        assertTrue("invalidate() completes once the write returns", invalidated.await(2, TimeUnit.SECONDS))
    }
}
