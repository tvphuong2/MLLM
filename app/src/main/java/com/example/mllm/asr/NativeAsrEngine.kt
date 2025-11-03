package com.example.mllm.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeAsrEngine(
    private val nativeBridge: NativeRagBridgeWrapper,
) : AsrEngine {
    override suspend fun transcribe(buffer: ShortArray, sampleRate: Int): String = withContext(Dispatchers.Default) {
        nativeBridge.transcribe(buffer, sampleRate) ?: error("ASR engine returned null")
    }

    interface NativeRagBridgeWrapper {
        fun transcribe(buffer: ShortArray, sampleRate: Int): String?
    }
}
