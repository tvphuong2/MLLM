package com.example.mllm.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Lightweight PCM recorder that streams microphone audio into short buffers.
 */
class MicRecorder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val bufferDurationMs: Int = 200,
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MicRecorder").apply { isDaemon = true }
    }
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null

    val currentSampleRate: Int
        get() = sampleRate

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onBuffer: (ShortArray) -> Unit) {
        if (isRecording.get()) return

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val targetBufferSize = max(minBufferSize, sampleRate * bufferDurationMs / 1000)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            encoding,
            targetBufferSize,
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Failed to initialize AudioRecord")
        }

        recorder.startRecording()
        audioRecord = recorder
        isRecording.set(true)

        executor.execute {
            val buffer = ShortArray(targetBufferSize)
            while (isRecording.get() && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    onBuffer(buffer.copyOf(read))
                }
            }
        }
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) return
        audioRecord?.apply {
            try {
                stop()
            } catch (_: IllegalStateException) {
                // Ignore when already stopped
            }
            release()
        }
        audioRecord = null
    }

    fun isRecording(): Boolean = isRecording.get()

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16_000
    }
}
