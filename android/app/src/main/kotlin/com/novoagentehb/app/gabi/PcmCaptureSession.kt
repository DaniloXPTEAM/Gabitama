package com.novoagentehb.app.gabi

/**
 * One microphone capture session over a [PcmCapture] port.
 *
 * Ownership rules:
 *  - [interrupt] unblocks a pending read (AudioRecord.stop) and may be called from any thread.
 *  - [close] stops (if not already stopped) and releases the recorder exactly once. Only the thread that
 *    runs [pump] calls it, after the loop has ended, so release never races with a read.
 *  - After [close], nothing is stopped or released again, and [interrupt] is a no-op.
 *  - [pump] never forwards audio read after its active check turned false (stale session).
 */
internal class PcmCaptureSession(private val capture: PcmCapture) {
    private val lock = Any()
    private var stopped = false
    private var closed = false

    /** Platform audio session id, used only to attach the echo canceler. */
    val audioSessionId: Int get() = capture.audioSessionId

    /** Fails when the platform did not initialize the recorder. */
    fun checkInitialized() {
        check(capture.isInitialized) { "Microfone não inicializado." }
    }

    /** Starts recording. Fails when the platform did not enter the recording state. */
    fun startRecording() {
        synchronized(lock) {
            check(!closed) { "Sessão de microfone já encerrada." }
            capture.startRecording()
            check(capture.isRecording) { "Microfone não iniciou gravação." }
        }
    }

    /**
     * Reads PCM16 until [active] becomes false. Platform read errors and sink failures are thrown,
     * never swallowed. A zero-length read ends the loop only through [active].
     */
    fun pump(buffer: ByteArray, active: () -> Boolean, sink: (ByteArray) -> Boolean) {
        while (active()) {
            if (synchronized(lock) { closed }) return
            val count = PcmIoContracts.requireReadResult(capture.read(buffer, 0, buffer.size))
            if (count > 0) {
                if (!active()) return
                if (!sink(buffer.copyOf(count))) error("Falha ao enviar áudio do microfone.")
            }
        }
    }

    /** Unblocks a pending read. Idempotent, safe from any thread. */
    fun interrupt() {
        synchronized(lock) {
            if (closed || stopped) return
            stopped = true
            runCatching { capture.stop() }
        }
    }

    /** Stops if needed and releases the recorder once. Later calls do nothing. */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            if (!stopped) {
                stopped = true
                runCatching { capture.stop() }
            }
            runCatching { capture.release() }
        }
    }
}
