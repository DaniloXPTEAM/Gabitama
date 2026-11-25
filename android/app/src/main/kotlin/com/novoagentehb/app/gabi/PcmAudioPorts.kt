package com.novoagentehb.app.gabi

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder

/**
 * Minimal ports over the Android PCM classes used by the Gemini Live audio path.
 * The production implementation delegates 1:1 to AudioRecord/AudioTrack; the ports exist only so
 * that lifecycle tests can observe creation, start, read/write, stop and release without a device.
 */
internal interface PcmCapture {
    val isInitialized: Boolean
    val isRecording: Boolean
    val audioSessionId: Int
    fun startRecording()
    fun read(buffer: ByteArray, offset: Int, size: Int): Int
    fun stop()
    fun release()
}

internal interface PcmPlayback {
    val isInitialized: Boolean
    val isPlaying: Boolean
    fun play()
    fun write(bytes: ByteArray, offset: Int, size: Int): Int
    fun pause()
    fun flush()
    fun release()
}

internal interface PcmAudioFactory {
    /** Minimum capture buffer as reported by the platform (may be <= 0 when unsupported). */
    fun minCaptureBufferSize(sampleRate: Int): Int
    /** Minimum playback buffer as reported by the platform (may be <= 0 when unsupported). */
    fun minPlaybackBufferSize(sampleRate: Int): Int
    fun createCapture(sampleRate: Int, bufferBytes: Int): PcmCapture
    fun createPlayback(sampleRate: Int, bufferBytes: Int): PcmPlayback
}

/** Production factory: identical parameters to the previous inline AudioRecord/AudioTrack construction. */
internal object AndroidPcmAudioFactory : PcmAudioFactory {
    override fun minCaptureBufferSize(sampleRate: Int): Int =
        AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    override fun minPlaybackBufferSize(sampleRate: Int): Int =
        AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)

    @SuppressLint("MissingPermission")
    override fun createCapture(sampleRate: Int, bufferBytes: Int): PcmCapture =
        AndroidPcmCapture(
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferBytes
            )
        )

    override fun createPlayback(sampleRate: Int, bufferBytes: Int): PcmPlayback =
        AndroidPcmPlayback(
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(bufferBytes).setTransferMode(AudioTrack.MODE_STREAM).build()
        )
}

private class AndroidPcmCapture(private val record: AudioRecord) : PcmCapture {
    override val isInitialized: Boolean get() = record.state == AudioRecord.STATE_INITIALIZED
    override val isRecording: Boolean get() = record.recordingState == AudioRecord.RECORDSTATE_RECORDING
    override val audioSessionId: Int get() = record.audioSessionId
    override fun startRecording() = record.startRecording()
    override fun read(buffer: ByteArray, offset: Int, size: Int): Int = record.read(buffer, offset, size)
    override fun stop() = record.stop()
    override fun release() = record.release()
}

private class AndroidPcmPlayback(private val track: AudioTrack) : PcmPlayback {
    override val isInitialized: Boolean get() = track.state == AudioTrack.STATE_INITIALIZED
    override val isPlaying: Boolean get() = track.playState == AudioTrack.PLAYSTATE_PLAYING
    override fun play() = track.play()
    override fun write(bytes: ByteArray, offset: Int, size: Int): Int = track.write(bytes, offset, size)
    override fun pause() = track.pause()
    override fun flush() = track.flush()
    override fun release() = track.release()
}
