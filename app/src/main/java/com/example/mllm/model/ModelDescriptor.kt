package com.example.mllm.model

data class ModelDescriptor(
    val assetPath: String,
    val encryption: ModelEncryption = ModelEncryption.AesGcm(),
)

sealed class ModelEncryption {
    data object None : ModelEncryption()
    data class AesGcm(val keyAlias: String = DEFAULT_KEY_ALIAS) : ModelEncryption()

    companion object {
        const val DEFAULT_KEY_ALIAS = "rag_model_master"
    }
}
