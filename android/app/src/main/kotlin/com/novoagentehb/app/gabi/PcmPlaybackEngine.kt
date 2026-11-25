package com.novoagentehb.app.gabi

import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the PCM24k playback track used by Gemini Live. It keeps the exact order and locking of the
 * previous inline code in VoiceEngineCoordinator (the queue of chunks stays in the coordinator):
 *  - chunks carry the generation they were queued in; invalidate() makes older chunks inert;
 *  - the track is created lazily, written in 1920-byte slices under [lock] and kept until release();
 *  - each blocking write is performed while holding [lock] (known behaviour, covered by a test).
 * It only wraps the Android playback mechanism. The voice is still produced by Gemini Live.
 */
internal class PcmPlaybackEngine(
    private val sampleRate: Int,
    private val factory: () -> PcmAudioFactory,
) {
    private val lock = Any()
    private val gen = AtomicLong()
    private var track: PcmPlayback? = null

    /** Current generation. Chunks queued with an older value are discarded. */
    val generation: Long get() = gen.get()

    /** Writes one chunk. Throws on initialization or write failure; the caller reports it. */
    fun write(chunkGeneration: Long, bytes: ByteArray) {
        if (bytes.isEmpty() || chunkGeneration != gen.get()) return
        val minBuf = PcmIoContracts.requireBufferSize(factory().minPlaybackBufferSize(sampleRate)).coerceAtLeast(4800)
        synchronized(lock) {
            if (chunkGeneration != gen.get()) return
            if (track == null) {
                track = factory().createPlayback(sampleRate, maxOf(minBuf * 2, bytes.size))
            }
            val current = checkNotNull(track)
            check(current.isInitialized) { "Reprodução não inicializada." }
            if (!current.isPlaying) current.play()
        }
        PcmIoContracts.writeFully(bytes.size) { offset, remaining ->
            synchronized(lock) {
                if (chunkGeneration != gen.get()) return
                checkNotNull(track).write(bytes, offset, minOf(remaining, 1920))
            }
        }
    }

    /** Makes every chunk queued before this call inert. Call before draining the queue. */
    fun invalidate() {
        synchronized(lock) { gen.incrementAndGet() }
    }

    /** Pauses and flushes the track, keeping it for reuse. Call after the queue has been drained. */
    fun silence() {
        synchronized(lock) {
            runCatching {
                track?.pause()
                track?.flush()
            }
        }
    }

    /** Pauses, flushes and releases the track. The next write creates a new one. */
    fun release() {
        synchronized(lock) {
            runCatching {
                track?.pause()
                track?.flush()
                track?.release()
            }
            track = null
        }
    }
}
