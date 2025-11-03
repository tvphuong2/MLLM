package com.example.mllm.agent

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mllm.asr.AsrEngine
import com.example.mllm.asr.NativeAsrEngine
import com.example.mllm.asr.StubAsrEngine
import com.example.mllm.audio.MicRecorder
import com.example.mllm.audio.SimpleVoiceActivityDetector
import com.example.mllm.integrity.DeviceIntegrityEvaluator
import com.example.mllm.llm.LlmEngine
import com.example.mllm.llm.NativeLlmEngine
import com.example.mllm.llm.StubLlmEngine
import com.example.mllm.model.ModelAssetLoader
import com.example.mllm.model.ModelDescriptor
import com.example.mllm.model.ModelEncryption
import com.example.mllm.model.ModelHandle
import com.example.mllm.nativebridge.NativeRagBridge
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentViewModel internal constructor(
    private val micRecorder: MicRecorder,
    private val vad: SimpleVoiceActivityDetector,
    private val modelLoader: ModelAssetLoader,
    private val integrityEvaluator: DeviceIntegrityEvaluator,
    private val nativeBridge: NativeRagBridge = NativeRagBridge,
    private val sampleRate: Int = MicRecorder.DEFAULT_SAMPLE_RATE,
    private val defaultThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private var asrEngine: AsrEngine = NativeAsrEngine(nativeBridge)
    private var llmEngine: LlmEngine = NativeLlmEngine(nativeBridge)

    private val audioChunks = mutableListOf<ShortArray>()
    private var whisperHandle: ModelHandle? = null
    private var llmHandle: ModelHandle? = null
    private val modelsLoaded = AtomicBoolean(false)

    fun onPermissionGranted() {
        if (_uiState.value.permissionGranted) return
        _uiState.update { it.copy(permissionGranted = true, errorMessage = null) }
        viewModelScope.launch { ensureModelsLoaded() }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(permissionGranted = false, errorMessage = "Microphone permission denied") }
    }

    fun onStartRecording() {
        if (!_uiState.value.permissionGranted) {
            _uiState.update { it.copy(errorMessage = "Microphone permission required") }
            return
        }
        if (_uiState.value.isRecording) return
        audioChunks.clear()
        vad.reset()
        try {
            micRecorder.start { buffer ->
                if (vad.shouldKeep(buffer)) {
                    synchronized(audioChunks) {
                        audioChunks.add(buffer.copyOf())
                    }
                }
            }
            _uiState.update { it.copy(isRecording = true, errorMessage = null) }
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Unable to start microphone", error)
            _uiState.update { it.copy(errorMessage = error.localizedMessage) }
        }
    }

    fun onStopRecording() {
        if (!_uiState.value.isRecording) return
        micRecorder.stop()
        _uiState.update { it.copy(isRecording = false) }
        val audio = synchronized(audioChunks) {
            val total = audioChunks.sumOf { it.size }
            val merged = ShortArray(total)
            var position = 0
            for (chunk in audioChunks) {
                chunk.copyInto(merged, destinationOffset = position)
                position += chunk.size
            }
            audioChunks.clear()
            merged
        }
        if (audio.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Không phát hiện giọng nói", transcription = "", response = "") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, transcription = "", response = "", errorMessage = null) }
            val transcript = runCatching { asrEngine.transcribe(audio, sampleRate) }.getOrElse { error ->
                Log.e(TAG, "Transcription failed", error)
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Lỗi nhận dạng giọng nói") }
                return@launch
            }
            _uiState.update { it.copy(transcription = transcript) }
            if (transcript.isBlank()) {
                _uiState.update { it.copy(isProcessing = false, response = "") }
                return@launch
            }
            val response = runCatching { llmEngine.generate(transcript) }.getOrElse { error ->
                Log.e(TAG, "Generation failed", error)
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Lỗi tạo phản hồi") }
                return@launch
            }
            _uiState.update { it.copy(isProcessing = false, response = response) }
        }
    }

    private suspend fun ensureModelsLoaded() {
        if (modelsLoaded.get()) return
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        val integrityOk = withContext(Dispatchers.IO) { integrityEvaluator.evaluate() }
        if (!integrityOk) {
            _uiState.update { it.copy(isProcessing = false, integrityVerified = false, errorMessage = "Không vượt qua kiểm tra bảo mật") }
            return
        }

        val whisper = withContext(Dispatchers.IO) { modelLoader.loadModel(WHISPER_MODEL) }
        val llm = withContext(Dispatchers.IO) { modelLoader.loadModel(LLM_MODEL) }

        val asrReady = whisper != null && nativeBridge.loadAsrModel(whisper, language = "vi")
        val llmReady = llm != null && nativeBridge.loadLlmModel(llm, threads = defaultThreads, contextLength = 2048)

        if (!asrReady) {
            asrEngine = StubAsrEngine()
            whisper?.close()
        } else {
            whisperHandle = whisper
        }

        if (!llmReady) {
            llmEngine = StubLlmEngine()
            llm?.close()
        } else {
            llmHandle = llm
        }

        modelsLoaded.set(true)
        val degradedComponents = buildList {
            if (!asrReady) add("ASR")
            if (!llmReady) add("LLM")
        }
        _uiState.update {
            it.copy(
                isProcessing = false,
                integrityVerified = integrityOk,
                isAsrReady = asrReady,
                isLlmReady = llmReady,
                errorMessage = if (degradedComponents.isNotEmpty()) {
                    "Không thể tải ${degradedComponents.joinToString(", ")} – đang dùng chế độ mô phỏng"
                } else {
                    it.errorMessage
                },
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        micRecorder.stop()
        whisperHandle?.close()
        llmHandle?.close()
        nativeBridge.release()
    }

    data class AgentUiState(
        val permissionGranted: Boolean = false,
        val integrityVerified: Boolean = false,
        val isRecording: Boolean = false,
        val isProcessing: Boolean = false,
        val transcription: String = "",
        val response: String = "",
        val errorMessage: String? = null,
        val isAsrReady: Boolean = false,
        val isLlmReady: Boolean = false,
    )

    companion object {
        private val WHISPER_MODEL = ModelDescriptor(
            assetPath = "models/whisper-tiny.bin.enc",
            encryption = ModelEncryption.AesGcm(keyAlias = "whisper_aes_key"),
        )
        private val LLM_MODEL = ModelDescriptor(
            assetPath = "models/qwen-1_5b-instruct-q4.bin.enc",
            encryption = ModelEncryption.AesGcm(keyAlias = "llm_aes_key"),
        )
        private const val TAG = "AgentViewModel"
    }
}

class AgentViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AgentViewModel::class.java))
        val appContext = context.applicationContext
        return AgentViewModel(
            MicRecorder(),
            SimpleVoiceActivityDetector(MicRecorder.DEFAULT_SAMPLE_RATE),
            ModelAssetLoader(appContext),
            DeviceIntegrityEvaluator.create(appContext),
        ) as T
    }
}
