package com.example.mllm.audio

import kotlin.math.sqrt

/**
 * Minimal RMS-based voice activity detector. It is intentionally simple so it can be
 * replaced with a production-ready VAD such as WebRTC once native libraries are wired in.
 */
class SimpleVoiceActivityDetector(
    private val sampleRate: Int,
    private val rmsThreshold: Double = 28.0,
    private val hangoverDurationMs: Int = 300,
) {
    private var trailingSilenceMs = hangoverDurationMs
    private var hasSpeech = false

    fun shouldKeep(buffer: ShortArray): Boolean {
        if (buffer.isEmpty()) return false
        val rms = calculateRms(buffer)
        val durationMs = bufferDurationMs(buffer.size)
        val isSpeechFrame = rms > rmsThreshold
        if (isSpeechFrame) {
            hasSpeech = true
            trailingSilenceMs = 0
            return true
        }

        if (hasSpeech) {
            trailingSilenceMs += durationMs
            if (trailingSilenceMs < hangoverDurationMs) {
                return true
            }
            hasSpeech = false
        }
        return false
    }

    fun reset() {
        hasSpeech = false
        trailingSilenceMs = hangoverDurationMs
    }

    private fun bufferDurationMs(samples: Int): Int = (samples * 1000) / sampleRate

    private fun calculateRms(buffer: ShortArray): Double {
        var accumulator = 0.0
        for (value in buffer) {
            accumulator += (value.toDouble() * value.toDouble())
        }
        val mean = accumulator / buffer.size
        return sqrt(mean)
    }
}
