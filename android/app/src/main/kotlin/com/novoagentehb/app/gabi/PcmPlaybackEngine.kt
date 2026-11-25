package com.novoagentehb.app.gabi

import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the PCM24k playback track used by Gemini Live. The chunk queue stays in the coordinator.
 *
 * Synchronization contract:
 *  - [lock] is held only for a single non-blocking write call, a flag check, or a pause/flush/release.
 *    It is never held while waiting for buffer space, so Stop/invalidate/release never wait for playback.
 *  - When the track has no room, the writer backs off outside the lock ([WAIT_MILLIS]), and gives up
 *    with an explicit error after [stallTimeoutNanos] without progress. No busy-wait.
 *  - Every write re-checks the chunk generation and the track under the lock. A released track is
 *    never written again, and chunks queued before [invalidate] stop mid-write.
 * It wraps only the Android playback mechanism. Gemini Live still produces the voice.
 */
internal class PcmPlaybackEngine(
    private val sampleRate: Int,
    private val stallTimeoutNanos: Long = 2_000_000_000L,
    private val factory: () -> PcmAudioFactory,
) {
    private val lock = Any()
    private val gen = AtomicLong()
    private var track: PcmPlayback? = null

    /** Current generation. Chunks queued with an older value are discarded. */
    val generation: Long get() = gen.get()

    /** Writes one chunk. Throws on initialization failure, platform error or a stalled track. */
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
        var offset = 0
        var stalledSince = -1L
        while (offset < bytes.size) {
            val accepted = synchronized(lock) {
                if (chunkGeneration != gen.get()) return
                val current = track ?: return
                current.writeNonBlocking(bytes, offset, minOf(bytes.size - offset, WRITE_SLICE))
            }
            check(accepted >= 0) { "Falha de reprodução de áudio (código $accepted)." }
            if (accepted > 0) {
                offset += accepted
                stalledSince = -1L
                continue
            }
            val now = System.nanoTime()
            if (stalledSince < 0) stalledSince = now
            check(now - stalledSince < stallTimeoutNanos) { "Reprodução de áudio sem progresso." }
            Thread.sleep(WAIT_MILLIS)
        }
    }

    /** Makes every chunk queued before this call inert, including a write already in progress. */
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

    /** Pauses, flushes and releases the track. Nothing is written to it afterwards. */
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

    internal companion object {
        const val WRITE_SLICE = 1920
        const val WAIT_MILLIS = 5L
    }
}
