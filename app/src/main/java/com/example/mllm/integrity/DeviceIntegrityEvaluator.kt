package com.example.mllm.integrity

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import kotlin.coroutines.resume

interface DeviceIntegrityEvaluator {
    suspend fun evaluate(): Boolean

    companion object {
        fun create(context: Context): DeviceIntegrityEvaluator {
            return runCatching {
                PlayIntegrityEvaluator(
                    context.applicationContext,
                    IntegrityManagerFactory.create(context.applicationContext),
                )
            }.getOrElse { throwable ->
                Log.w(TAG, "Falling back to permissive integrity evaluator", throwable)
                AlwaysPassingIntegrityEvaluator
            }
        }
    }
}

private class PlayIntegrityEvaluator(
    private val context: Context,
    private val integrityManager: IntegrityManager,
) : DeviceIntegrityEvaluator {
    private val random = SecureRandom()

    override suspend fun evaluate(): Boolean = suspendCancellableCoroutine { continuation ->
        val nonce = ByteArray(32).also { random.nextBytes(it) }
        val request = IntegrityTokenRequest.builder()
            .setNonce(Base64.encodeToString(nonce, Base64.NO_WRAP))
            .build()

        integrityManager.requestIntegrityToken(request)
            .addOnSuccessListener { response ->
                // In production the token must be sent to a backend for verification.
                Log.d(TAG, "Received integrity token with length ${response.token().length}")
                continuation.resume(true)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Integrity check failed", error)
                continuation.resume(false)
            }
    }
}

private object AlwaysPassingIntegrityEvaluator : DeviceIntegrityEvaluator {
    override suspend fun evaluate(): Boolean = true
}

private const val TAG = "IntegrityEvaluator"
