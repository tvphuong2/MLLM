package com.example.mllm.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class StubAsrEngine : AsrEngine {
    override suspend fun transcribe(buffer: ShortArray, sampleRate: Int): String = withContext(Dispatchers.Default) {
        delay(150)
        "(stub transcription)"
    }
}
