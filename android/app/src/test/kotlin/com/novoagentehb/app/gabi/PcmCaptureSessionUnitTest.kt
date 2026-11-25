package com.novoagentehb.app.gabi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Microphone capture cycle over simulated AudioRecord ports. No microphone, no permission change.
 * Proves the session's sequencing and resource ownership; not audible capture on a device.
 */
class PcmCaptureSessionUnitTest {

    /** One scripted answer of read(): PCM bytes, or a negative platform error code. */
    private sealed class Step {
        class Data(val bytes: ByteArray) : Step()
        class Error(val code: Int) : Step()
    }

    private class FakeCapture(
        override var isInitialized: Boolean = true,
        private val startWorks: Boolean = true,
    ) : PcmCapture {
        override var isRecording = false
        override val audioSessionId: Int = 0
        val script = ArrayDeque<Step>()
        private val stopLatch = CountDownLatch(1)
        var startCalls = 0
        var stopCalls = 0
        var releaseCalls = 0
        var readCalls = 0
        val events = mutableListOf<String>()

        override fun startRecording() {
            startCalls++
            events += "start"
            isRecording = startWorks
        }

        override fun read(buffer: ByteArray, offset: Int, size: Int): Int {
            readCalls++
            return when (val step = script.pollFirst()) {
                is Step.Data -> {
                    val n = minOf(size, step.bytes.size)
                    System.arraycopy(step.bytes, 0, buffer, offset, n)
                    n
                }
                is Step.Error -> step.code
                // Empty script: behaves like AudioRecord with no data; blocks until stop() unblocks it.
                null -> { stopLatch.await(5, TimeUnit.SECONDS); 0 }
            }
        }

        override fun stop() {
            stopCalls++
            events += "stop"
            isRecording = false
            stopLatch.countDown()
        }

        override fun release() {
            releaseCalls++
            events += "release"
            isInitialized = false
        }
    }

    private fun pcm(size: Int, seed: Int) = ByteArray(size) { ((it + seed) % 251).toByte() }

    /** Runs pump on a thread; returns the thread and the bytes it forwarded. */
    private fun pumpAsync(
        session: PcmCaptureSession,
        active: () -> Boolean,
        out: ByteArrayOutputStream,
        errors: MutableList<Throwable>,
    ): Thread = Thread {
        try {
            session.pump(ByteArray(1280), active) { bytes -> synchronized(out) { out.write(bytes) }; true }
        } catch (t: Throwable) {
            errors += t
        }
    }.also { it.start() }

    @Test
    fun creationPublishesAnInitializedRecorder() {
        val capture = FakeCapture()
        val session = PcmCaptureSession(capture)
        session.checkInitialized()
        assertEquals(0, capture.startCalls)
    }

    @Test
    fun initializationFailureIsReportedBeforeAnyRecording() {
        val capture = FakeCapture(isInitialized = false)
        val failure = runCatching { PcmCaptureSession(capture).checkInitialized() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(0, capture.startCalls)
    }

    @Test
    fun startFailureIsReportedAndNothingIsRead() {
        val capture = FakeCapture(startWorks = false)
        val failure = runCatching { PcmCaptureSession(capture).startRecording() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(0, capture.readCalls)
    }

    @Test
    fun recordingStartsAndPcm16ReachesTheSinkInOrder() {
        val capture = FakeCapture()
        capture.script += Step.Data(pcm(1280, 0))
        capture.script += Step.Data(pcm(1280, 40))
        val session = PcmCaptureSession(capture)
        session.startRecording()
        assertTrue(capture.isRecording)

        val out = ByteArrayOutputStream()
        var reads = 0
        session.pump(ByteArray(1280), active = { reads < 2 }) { bytes ->
            reads++
            out.write(bytes)
            true
        }

        assertArrayEquals(pcm(1280, 0) + pcm(1280, 40), out.toByteArray())
    }

    @Test
    fun partialReadForwardsOnlyTheBytesActuallyRead() {
        val capture = FakeCapture()
        capture.script += Step.Data(pcm(320, 5))
        val session = PcmCaptureSession(capture)
        session.startRecording()

        var forwarded: ByteArray? = null
        session.pump(ByteArray(1280), active = { forwarded == null }) { bytes -> forwarded = bytes; true }

        assertArrayEquals(pcm(320, 5), forwarded)
    }

    @Test
    fun readErrorIsThrownNotSwallowed() {
        val capture = FakeCapture()
        capture.script += Step.Error(-3)
        val session = PcmCaptureSession(capture)
        session.startRecording()

        val failure = runCatching { session.pump(ByteArray(1280), active = { true }) { true } }.exceptionOrNull()
        assertTrue("read error must surface", failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("-3"))
    }

    @Test
    fun sinkRefusalFailsTheSession() {
        val capture = FakeCapture()
        capture.script += Step.Data(pcm(100, 0))
        val session = PcmCaptureSession(capture)
        session.startRecording()

        val failure = runCatching { session.pump(ByteArray(1280), active = { true }) { false } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun cancellationDuringCaptureEndsTheLoopWithoutMoreAudio() {
        val capture = FakeCapture()
        val session = PcmCaptureSession(capture)
        session.startRecording()
        val out = ByteArrayOutputStream()
        val errors = mutableListOf<Throwable>()
        val active = AtomicBoolean(true)
        capture.script += Step.Data(pcm(200, 1))
        val thread = pumpAsync(session, { active.get() }, out, errors)

        Thread.sleep(50)
        active.set(false)
        session.interrupt()
        thread.join(3_000)

        assertFalse(thread.isAlive)
        assertTrue(errors.isEmpty())
    }

    /** Stop while a read is blocked: the read is unblocked, the loop ends, and no audio is forwarded afterwards. */
    @Test
    fun stopWhileAReadIsBlockedUnblocksItAndForwardsNothingAfter() {
        val capture = FakeCapture()
        val session = PcmCaptureSession(capture)
        session.startRecording()
        val out = ByteArrayOutputStream()
        val errors = mutableListOf<Throwable>()
        val active = AtomicBoolean(true)
        val thread = pumpAsync(session, { active.get() }, out, errors)

        Thread.sleep(50)           // the loop is now blocked inside read() (empty script)
        active.set(false)          // generation changed first, as in production
        session.interrupt()
        thread.join(3_000)

        assertFalse("pump must return after the read is unblocked", thread.isAlive)
        assertEquals("nothing forwarded after Stop", 0, out.size())
        assertEquals(1, capture.stopCalls)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun closeReleasesOnceAndRepeatedStopsAreHarmless() {
        val capture = FakeCapture()
        val session = PcmCaptureSession(capture)
        session.startRecording()

        session.interrupt()
        session.interrupt()
        session.close()
        session.close()
        session.interrupt()

        assertEquals("stop is called once", 1, capture.stopCalls)
        assertEquals("release is called once", 1, capture.releaseCalls)
        assertEquals(listOf("start", "stop", "release"), capture.events)
    }

    @Test
    fun closeWithoutAPriorInterruptStopsBeforeRelease() {
        val capture = FakeCapture()
        val session = PcmCaptureSession(capture)
        session.startRecording()

        session.close()

        assertEquals(listOf("start", "stop", "release"), capture.events)
    }

    @Test
    fun restartCreatesAFreshSessionAndTheOldOneCannotTouchTheNewRecorder() {
        val first = FakeCapture()
        val second = FakeCapture()
        val oldSession = PcmCaptureSession(first)
        oldSession.startRecording()
        oldSession.close()

        val newSession = PcmCaptureSession(second)
        newSession.startRecording()
        second.script += Step.Data(pcm(300, 2))
        val out = ByteArrayOutputStream()
        newSession.pump(ByteArray(1280), active = { out.size() == 0 }) { bytes -> out.write(bytes); true }

        oldSession.interrupt()
        oldSession.close()

        assertEquals("the old recorder was released once", 1, first.releaseCalls)
        assertEquals("the old session never stops the new recorder", 0, second.stopCalls)
        assertEquals("the new recorder was not released", 0, second.releaseCalls)
        assertArrayEquals(pcm(300, 2), out.toByteArray())
    }

    /** Stale-session audio: data read after the session became inactive is discarded, not sent. */
    @Test
    fun audioReadAfterTheSessionIsInactiveIsDiscarded() {
        val capture = FakeCapture()
        capture.script += Step.Data(pcm(640, 8))
        val session = PcmCaptureSession(capture)
        session.startRecording()
        var sent = 0
        session.pump(ByteArray(1280), active = { false }) { sent++; true }
        assertEquals(0, sent)
        assertEquals("an inactive session does not even read", 0, capture.readCalls)
    }
}
