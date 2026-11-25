package com.novoagentehb.app.gabi

import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Synthetic AudioRecord/AudioTrack ports for CI emulators. They replace only the Android audio hardware
 * adapters; the voice still comes from Gemini Live through MockWebServer. Not audible audio, not a device test.
 */
internal class SyntheticCapture(private val initOk: Boolean) : PcmCapture {
    override val isInitialized: Boolean get() = initOk && !released
    @Volatile override var isRecording = false
        private set
    override val audioSessionId: Int = 0
    val starts = AtomicInteger()
    val stops = AtomicInteger()
    val releases = AtomicInteger()
    @Volatile private var released = false
    @Volatile private var stopped = false
    private var counter = 0L

    override fun startRecording() {
        starts.incrementAndGet()
        isRecording = true
    }

    /** Byte at global position p is (p % 256), so a test can verify order and content of what was sent. */
    override fun read(buffer: ByteArray, offset: Int, size: Int): Int {
        if (stopped) return 0 // AudioRecord.stop() unblocks read()
        Thread.sleep(15)
        if (stopped) return 0
        val n = minOf(size, 640)
        for (i in 0 until n) buffer[offset + i] = ((counter + i) % 256).toByte()
        counter += n
        return n
    }

    override fun stop() {
        stops.incrementAndGet()
        stopped = true
        isRecording = false
    }

    override fun release() {
        releases.incrementAndGet()
        released = true
    }
}

internal class SyntheticPlayback : PcmPlayback {
    @Volatile override var isInitialized = true
        private set
    @Volatile override var isPlaying = false
        private set
    /** Every call answers zero: the buffer never drains (used to keep a chunk pending). */
    @Volatile var stuck = false
    /** Negative platform code answered by every write, or null. */
    @Volatile var errorCode: Int? = null
    val releases = AtomicInteger()
    val flushes = AtomicInteger()
    val writesAfterRelease = AtomicInteger()
    private val received = ByteArrayOutputStream()
    @Volatile private var released = false

    fun receivedBytes(): ByteArray = synchronized(received) { received.toByteArray() }

    override fun play() {
        isPlaying = true
    }

    override fun writeNonBlocking(bytes: ByteArray, offset: Int, size: Int): Int {
        if (released) {
            writesAfterRelease.incrementAndGet()
            return -6
        }
        errorCode?.let { return it }
        if (stuck) return 0
        val n = minOf(size, 4800)
        synchronized(received) { received.write(bytes, offset, n) }
        return n
    }

    override fun pause() {
        isPlaying = false
    }

    override fun flush() {
        flushes.incrementAndGet()
    }

    override fun release() {
        releases.incrementAndGet()
        released = true
        isInitialized = false
    }
}

internal class SyntheticAudioFactory : PcmAudioFactory {
    val captures = CopyOnWriteArrayList<SyntheticCapture>()
    val playbacks = CopyOnWriteArrayList<SyntheticPlayback>()
    @Volatile var failCaptureInit = false
    @Volatile var playbackStuck = false
    @Volatile var playbackErrorCode: Int? = null

    override fun minCaptureBufferSize(sampleRate: Int): Int = 3200
    override fun minPlaybackBufferSize(sampleRate: Int): Int = 4800

    override fun createCapture(sampleRate: Int, bufferBytes: Int): PcmCapture =
        SyntheticCapture(initOk = !failCaptureInit).also { captures += it }

    override fun createPlayback(sampleRate: Int, bufferBytes: Int): PcmPlayback =
        SyntheticPlayback().also {
            it.stuck = playbackStuck
            it.errorCode = playbackErrorCode
            playbacks += it
        }
}
