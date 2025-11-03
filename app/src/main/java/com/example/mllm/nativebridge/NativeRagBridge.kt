package com.example.mllm.nativebridge

import android.util.Log
import com.example.mllm.asr.NativeAsrEngine
import com.example.mllm.llm.NativeLlmEngine
import com.example.mllm.model.ModelHandle
import java.util.concurrent.atomic.AtomicBoolean

object NativeRagBridge :
    NativeAsrEngine.NativeRagBridgeWrapper,
    NativeLlmEngine.NativeRagBridgeWrapper {

    private val libraryLoaded = AtomicBoolean(false)
    private val whisperReady = AtomicBoolean(false)
    private val llmReady = AtomicBoolean(false)

    init {
        loadLibraryIfNeeded()
    }

    fun loadAsrModel(handle: ModelHandle?, language: String? = null): Boolean {
        loadLibraryIfNeeded()
        val fd = handle?.fileDescriptor()?.fd ?: INVALID_FD
        val length = handle?.length() ?: 0L
        val success = runCatching { nativeInitWhisper(fd, length, language) }.getOrElse { error ->
            Log.e(TAG, "Failed to load ASR model", error)
            false
        }
        whisperReady.set(success)
        return success
    }

    fun loadLlmModel(handle: ModelHandle?, threads: Int, contextLength: Int): Boolean {
        loadLibraryIfNeeded()
        val fd = handle?.fileDescriptor()?.fd ?: INVALID_FD
        val length = handle?.length() ?: 0L
        val success = runCatching { nativeInitLlm(fd, length, threads, contextLength) }.getOrElse { error ->
            Log.e(TAG, "Failed to load LLM model", error)
            false
        }
        llmReady.set(success)
        return success
    }

    fun release() {
        runCatching { nativeReleaseWhisper() }
        runCatching { nativeReleaseLlm() }
        whisperReady.set(false)
        llmReady.set(false)
    }

    override fun transcribe(buffer: ShortArray, sampleRate: Int): String? {
        if (!whisperReady.get()) {
            return null
        }
        return runCatching { nativeTranscribe(buffer, sampleRate) }.getOrElse { error ->
            Log.e(TAG, "ASR transcription failed", error)
            null
        }
    }

    override fun generate(prompt: String): String? {
        if (!llmReady.get()) {
            return null
        }
        return runCatching { nativeGenerate(prompt) }.getOrElse { error ->
            Log.e(TAG, "LLM generation failed", error)
            null
        }
    }

    fun isAsrReady(): Boolean = whisperReady.get()

    fun isLlmReady(): Boolean = llmReady.get()

    private fun loadLibraryIfNeeded() {
        if (libraryLoaded.compareAndSet(false, true)) {
            runCatching { System.loadLibrary("rag-agent") }.onFailure { error ->
                Log.e(TAG, "Unable to load native library", error)
            }
        }
    }

    @JvmStatic
    private external fun nativeInitWhisper(fd: Int, length: Long, language: String?): Boolean

    @JvmStatic
    private external fun nativeTranscribe(buffer: ShortArray, sampleRate: Int): String?

    @JvmStatic
    private external fun nativeReleaseWhisper()

    @JvmStatic
    private external fun nativeInitLlm(fd: Int, length: Long, threads: Int, contextLength: Int): Boolean

    @JvmStatic
    private external fun nativeGenerate(prompt: String): String?

    @JvmStatic
    private external fun nativeReleaseLlm()

    private const val INVALID_FD = -1
    private const val TAG = "NativeRagBridge"
}
