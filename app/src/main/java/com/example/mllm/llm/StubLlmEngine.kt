package com.example.mllm.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class StubLlmEngine : LlmEngine {
    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        delay(300)
        "(stub answer for) $prompt"
    }
}
