package com.example.mllm.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeLlmEngine(
    private val nativeBridge: NativeRagBridgeWrapper,
) : LlmEngine {
    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        nativeBridge.generate(prompt) ?: error("LLM engine returned null")
    }

    interface NativeRagBridgeWrapper {
        fun generate(prompt: String): String?
    }
}
