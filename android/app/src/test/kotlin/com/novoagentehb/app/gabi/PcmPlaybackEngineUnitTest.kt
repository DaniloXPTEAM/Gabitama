package com.novoagentehb.app.gabi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Playback lifecycle with simulated AudioTrack ports. Proves the engine's sequencing, the lock
 * contract (Stop never waits for a stalled write), release safety and reuse. It does NOT prove audible
 * output or real AudioTrack behaviour.
 */
class PcmPlaybackEngineUnitTest {

    private class FakeTrack(
        val id: Int,
        private val log: MutableList<String>,
    ) : PcmPlayback {
        override var isInitialized = true
        override var isPlaying = false
        val received = ByteArrayOutputStream()
        @Volatile var released = false
        @Volatile var writesAfterRelease = 0
        /** Number of initial zero-progress answers (buffer full), then accepts up to [maxPerWrite]. */
        @Volatile var zeroAnswersLeft = 0
        /** When true, every write answers zero: the buffer never drains. */
        @Volatile var stuck = false
        /** Negative platform code returned by every write, or null. */
        @Volatile var errorCode: Int? = null
        var maxPerWrite = 1000
        /** Called after each accepted write (lets a test interleave Stop/interruption deterministically). */
        var afterAccept: (() -> Unit)? = null
        @Volatile var writeCalls = 0

        override fun play() { isPlaying = true; log += "play$id" }

        override fun writeNonBlocking(bytes: ByteArray, offset: Int, size: Int): Int {
            writeCalls++
            if (released) { writesAfterRelease++; return -6 }
            errorCode?.let { return it }
            if (stuck) return 0
            if (zeroAnswersLeft > 0) { zeroAnswersLeft--; return 0 }
            val n = minOf(size, maxPerWrite)
            received.write(bytes, offset, n)
            afterAccept?.invoke()
            return n
        }
        override fun pause() { isPlaying = false; log += "pause$id" }
        override fun flush() { log += "flush$id" }
        override fun release() { log += "release$id"; released = true; isInitialized = false }
    }

    private class FakeFactory(private val minPlayback: Int = 3200) : PcmAudioFactory {
        val log = CopyOnWriteArrayList<String>()
        val tracks = CopyOnWriteArrayList<FakeTrack>()
        val bufferRequests = CopyOnWriteArrayList<Int>()
        /** Applied to each new track; lets a test script the first track. */
        var configure: (FakeTrack) -> Unit = {}

        override fun minCaptureBufferSize(sampleRate: Int): Int = error("capture not used")
        override fun minPlaybackBufferSize(sampleRate: Int): Int = minPlayback
        override fun createCapture(sampleRate: Int, bufferBytes: Int): PcmCapture = error("capture not used")
        override fun createPlayback(sampleRate: Int, bufferBytes: Int): PcmPlayback {
            bufferRequests += bufferBytes
            val track = FakeTrack(tracks.size + 1, log)
            configure(track)
            tracks += track
            return track
        }
    }

    private fun pcm(size: Int, seed: Int = 0) = ByteArray(size) { ((it + seed) % 251).toByte() }

    private fun writerThread(engine: PcmPlaybackEngine, bytes: ByteArray, errors: MutableList<Throwable>): Thread =
        Thread { try { engine.write(engine.generation, bytes) } catch (t: Throwable) { errors += t } }

    private fun awaitTrack(factory: FakeFactory, timeoutMs: Long = 5_000): FakeTrack? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (factory.tracks.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(2)
        return factory.tracks.firstOrNull()
    }

    @Test
    fun createsOneTrackWithPlatformBufferAndStartsBeforeWriting() {
        val factory = FakeFactory(minPlayback = 3200)
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        val input = pcm(3840)

        engine.write(engine.generation, input)

        // minBuf = max(platform 3200, 4800 floor) = 4800; buffer = max(minBuf*2, chunk) = 9600
        assertEquals(listOf(9600), factory.bufferRequests.toList())
        assertEquals(1, factory.tracks.size)
        assertEquals(listOf("play1"), factory.log.toList())
        assertArrayEquals(input, factory.tracks[0].received.toByteArray())
    }

    @Test
    fun partialWritesAreContinuedUntilTheWholeChunkIsWritten() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        factory.configure = { it.maxPerWrite = 7 }
        val input = pcm(1000)

        engine.write(engine.generation, input)

        assertArrayEquals(input, factory.tracks[0].received.toByteArray())
    }

    @Test
    fun bufferFullAnswersBackOffAndThenContinue() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        factory.configure = { it.zeroAnswersLeft = 5 }
        val input = pcm(2000)

        engine.write(engine.generation, input)

        val track = factory.tracks[0]
        assertArrayEquals(input, track.received.toByteArray())
        // Bounded attempts: no spinning on a full buffer.
        assertTrue("write attempts must stay bounded, was ${track.writeCalls}", track.writeCalls < 20)
    }

    @Test
    fun stalledTrackFailsExplicitlyAfterTheTimeout() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, stallTimeoutNanos = 100_000_000L, factory = { factory })
        factory.configure = { it.stuck = true }

        val started = System.nanoTime()
        val failure = runCatching { engine.write(engine.generation, pcm(500)) }.exceptionOrNull()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertTrue("stall must surface as IllegalStateException", failure is IllegalStateException)
        assertTrue("stall must end near the timeout, was ${elapsedMs}ms", elapsedMs < 2_000)
    }

    @Test
    fun platformErrorCodeFailsExplicitly() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        factory.configure = { it.errorCode = -6 }

        val failure = runCatching { engine.write(engine.generation, pcm(500)) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("-6"))
    }

    @Test
    fun chunkQueuedBeforeInvalidationIsDiscarded() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        val stale = engine.generation

        engine.invalidate()
        engine.write(stale, pcm(500))

        assertTrue("no track may be created for a stale chunk", factory.tracks.isEmpty())
        assertTrue(factory.log.isEmpty())
    }

    @Test
    fun invalidateStopsAWriteAlreadyInProgress() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        factory.configure = { it.stuck = true }
        val errors = CopyOnWriteArrayList<Throwable>()
        val writer = writerThread(engine, pcm(4000), errors)
        writer.start()
        assertTrue(awaitTrack(factory) != null)

        engine.invalidate()
        writer.join(1_000)

        assertTrue("writer must exit after invalidation", !writer.isAlive)
        assertTrue("invalidation is not a failure", errors.isEmpty())
    }

    /**
     * Regression for the former lock-hold: Stop/invalidate/silence/release must not wait for a
     * stalled AudioTrack write. The stalled writer exits by itself, and nothing reaches a released track.
     */
    @Test
    fun stopPathIsNotBlockedByAStalledWriteAndNothingReachesAReleasedTrack() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        factory.configure = { it.stuck = true }
        val errors = CopyOnWriteArrayList<Throwable>()
        val writer = writerThread(engine, pcm(4000), errors)
        writer.start()
        val track = awaitTrack(factory)!!

        val stopStarted = System.nanoTime()
        engine.invalidate()
        engine.silence()
        engine.release()
        val stopMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStarted)
        writer.join(1_000)

        assertTrue("stop path must not wait for the stalled write, took ${stopMs}ms", stopMs < 200)
        assertTrue("writer must exit", !writer.isAlive)
        assertTrue("no exception reaches the stop path or the writer", errors.isEmpty())
        assertEquals("nothing is written to a released track", 0, track.writesAfterRelease)
        assertTrue(track.released)
    }

    @Test
    fun silenceKeepsTheTrackAndReleaseDoesNotLetItBeReused() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
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
        assertEquals(listOf("pause1", "flush1", "release1"), factory.log.subList(beforeRelease, beforeRelease + 3).toList())
    }

    /** Barge-in: the remainder of an interrupted chunk is discarded; the next session starts clean. */
    @Test
    fun interruptionDiscardsThePendingRemainderAndNextSessionDoesNotInheritIt() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        factory.configure = { track ->
            track.maxPerWrite = 1000
            track.afterAccept = {
                if (track.received.size() == 1000) engine.invalidate()
            }
        }
        val oldChunk = pcm(3000, seed = 1)
        engine.write(engine.generation, oldChunk)
        engine.silence()

        val newChunk = pcm(800, seed = 9)
        engine.write(engine.generation, newChunk)

        val expected = oldChunk.copyOfRange(0, 1000) + newChunk
        assertArrayEquals(expected, factory.tracks[0].received.toByteArray())
    }

    @Test
    fun readyAfterReleaseCreatesAFreshTrackWithoutOldBytes() {
        val factory = FakeFactory()
        val engine = PcmPlaybackEngine(24_000, factory = { factory })
        val first = pcm(600, seed = 3)
        engine.write(engine.generation, first)
        engine.invalidate(); engine.release()

        val second = pcm(600, seed = 7)
        engine.write(engine.generation, second)

        assertArrayEquals(first, factory.tracks[0].received.toByteArray())
        assertArrayEquals(second, factory.tracks[1].received.toByteArray())
        assertNull(factory.tracks[1].errorCode)
    }
}
