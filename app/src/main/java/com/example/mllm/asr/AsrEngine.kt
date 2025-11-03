package com.example.mllm.asr

interface AsrEngine {
    suspend fun transcribe(buffer: ShortArray, sampleRate: Int): String
}
