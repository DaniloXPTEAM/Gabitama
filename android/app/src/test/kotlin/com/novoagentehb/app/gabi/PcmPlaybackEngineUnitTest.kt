package com.novoagentehb.app.gabi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Playback lifecycle with simulated AudioTrack ports (no device, no audio hardware).
 * These tests prove the engine's own sequencing: creation, partial writes, invalidation,
 * pause/flush/release and reuse. They do NOT prove audible output or real AudioTrack behaviour.
 */
class PcmPlaybackEngineUnitTest {

    private class FakeTrack(
        val id: Int,
        private val log: MutableList<String>,
        private val maxPerWrite: Int,
        private val gate: CountDownLatch?,
    ) : PcmPlayback {
        override var isInitialized = true
        override var isPlaying = false
        val received = ByteArrayOutputStream()
        private var gateUsed = false

        override fun play() { isPlaying = true; log += "play$id" }
        override fun write(bytes: ByteArray, offset: Int, size: Int): Int {
            if (gate != null && !gateUsed) {
                gateUsed = true
                // Simulates a blocking AudioTrack.write() on a full buffer.
                gate.await(5, TimeUnit.SECONDS)
            }
            val n = minOf(size, maxPerWrite)
            received.write(bytes, offset, n)
            return n
        }
        override fun pause() { isPlaying = false; log += "pause$id" }
        override fun flush() { log += "flush$id" }
        override fun release() { log += "release$id"; isInitialized = false }
    }

    private class FakeFactory(
        private val minPlayback: Int = 3200,
        private val maxPerWrite: Int = 1000,
        private val gate: CountDownLatch? = null,
    ) : PcmAudioFactory {
        val log = CopyOnWriteArrayList<String>()
        val tracks = CopyOnWriteArrayList<FakeTrack>()
        val bufferRequests = CopyOnWriteArrayList<Int>()

        override fun minCaptureBufferSize(sampleRate: Int): Int = error("capture not used")
        override fun minPlaybackBufferSize(sampleRate: Int): Int = minPlayback
        override fun createCapture(sampleRate: Int, bufferBytes: Int): PcmCapture = error("capture not used")
        override fun createPlayback(sampleRate: Int, bufferBytes: Int): PcmPlayback {
            bufferRequests += bufferBytes
            val track = FakeTrack(tracks.size + 1, log, maxPerWrite, gate)
            tracks += track
            return track
        }
    }

    private fun pcm(size: Int) = ByteArray(size) { (it % 251).toByte() }

    @Test
    fun createsOneTrackWithPlatformBufferAndStartsBeforeWriting() {
        val factory = FakeFactory(minPlayback = 3200)
        val engine = PcmPlaybackEngine(24_000) { factory }
        val input = pcm(3840)

        engine.write(engine.generation, input)

        // minBuf = max(platform 3200, 4800 floor) = 4800; buffer = max(minBuf*2, chunk) = 9600
        assertEquals(listOf(9600), factory.bufferRequests)
        assertEquals(1, factory.tracks.size)
        assertEquals(listOf("play1"), factory.log.toList())
        assertArrayEquals(input, factory.tracks[0].received.toByteArray())
    }

    @Test
    fun partialWritesAreContinuedUntilTheWholeChunkIsWritten() {
        val factory = FakeFactory(maxPerWrite = 7)
        val engine = PcmPlaybackEngine(24_000) { factory }
        val input = pcm(1000)

        engine.write(engine.generation, input)

        assertArrayEquals(input, factory.tracks[0].received.toByteArray())
    }

    @Test
    fun zeroProgressWriteFailsExplicitlyInsteadOfSpinning() {
        val factory = FakeFactory(maxPerWrite = 0)
        val engine = PcmPlaybackEngine(24_000) { factory }

        val failure = runCatching { engine.write(engine.generation, pcm(100)) }.exceptionOrNull()

        assertTrue("zero-progress write must throw", failure is IllegalStateException)
    }

    @Test
    fun chunkQueuedBeforeInvalidationIsDiscarded() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000) { factory }
        val stale = engine.generation

        engine.invalidate()
        engine.write(stale, pcm(500))

        assertTrue("no track may be created for a stale chunk", factory.tracks.isEmpty())
        assertTrue(factory.log.isEmpty())
    }

    @Test
    fun silenceKeepsTheTrackAndReleaseDoesNotLetItBeReused() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000) { factory }
        engine.write(engine.generation, pcm(500))

        engine.invalidate()
        engine.silence()
        engine.write(engine.generation, pcm(500))
        assertEquals("silence must not create a second track", 1, factory.tracks.size)

        val beforeRelease = factory.log.size
        engine.release()
        val releasedBytes = factory.tracks[0].received.size()
        engine.write(engine.generation, pcm(500))

        assertEquals("after release a new track is created", 2, factory.tracks.size)
        assertEquals("the released track receives nothing more", releasedBytes, factory.tracks[0].received.size())
        // release() is pause -> flush -> release on the track; those three events come first after beforeRelease.
        assertEquals(listOf("pause1", "flush1", "release1"), factory.log.subList(beforeRelease, beforeRelease + 3).toList())
    }

    /**
     * Characterization of the CURRENT synchronization (not a desired behaviour): a blocking
     * AudioTrack.write() runs under the same lock used by silence()/invalidate()/release().
     * While a write is blocked, a stop path called from the main thread waits for it.
     * If the locking is changed later, this test must be updated with the new evidence.
     */
    @Test
    fun stopPathWaitsWhileABlockingWriteHoldsTheLock() {
        val gate = CountDownLatch(1)
        val factory = FakeFactory(maxPerWrite = 1920, gate = gate)
        val engine = PcmPlaybackEngine(24_000) { factory }

        val writer = Thread { engine.write(engine.generation, pcm(1920)) }
        writer.start()
        // Wait until the writer is actually inside the blocked write.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (factory.tracks.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5)

        val stopStarted = System.nanoTime()
        val stopper = Thread { engine.silence() }
        stopper.start()
        stopper.join(400)
        val stopStillBlocked = stopper.isAlive

        gate.countDown()
        stopper.join(5_000)
        writer.join(5_000)
        val stopElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStarted)

        assertTrue("stop must wait while the blocking write holds the lock", stopStillBlocked)
        assertTrue("stop completed after the write was released", !stopper.isAlive)
        assertTrue("elapsed >= the blocked window", stopElapsedMs >= 400)
    }
}
