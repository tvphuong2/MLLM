package com.example.mllm.llm

interface LlmEngine {
    suspend fun generate(prompt: String): String
}
