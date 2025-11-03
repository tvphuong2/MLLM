package com.example.mllm.model

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.Closeable
import java.security.GeneralSecurityException
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.thread

class ModelAssetLoader(
    private val context: Context,
    private val keyProvider: ModelKeyProvider = ModelKeyProvider(),
) {
    fun loadModel(descriptor: ModelDescriptor): ModelHandle? {
        val lengthHint = assetLength(descriptor.assetPath)
        return try {
            when (val encryption = descriptor.encryption) {
                is ModelEncryption.None -> pipeFromStream(openAssetStream(descriptor.assetPath), lengthHint)
                is ModelEncryption.AesGcm -> decryptToPipe(descriptor.assetPath, encryption.keyAlias, lengthHint)
            }
        } catch (ioe: IOException) {
            Log.e(TAG, "Failed to open model asset ${descriptor.assetPath}", ioe)
            null
        } catch (security: GeneralSecurityException) {
            Log.e(TAG, "Failed to decrypt model asset", security)
            null
        }
    }

    private fun decryptToPipe(assetPath: String, keyAlias: String, lengthHint: Long?): ModelHandle {
        val rawStream = openAssetStream(assetPath)
        val iv = ByteArray(GCM_IV_BYTES)
        val bytesRead = rawStream.read(iv)
        if (bytesRead != iv.size) {
            rawStream.close()
            throw IOException("Encrypted asset $assetPath is missing IV header")
        }
        val key = keyProvider.getOrCreate(keyAlias)
        val cipher = Cipher.getInstance(AES_GCM_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherStream = CipherInputStream(rawStream, cipher)
        return pipeFromStream(cipherStream, lengthHint)
    }

    private fun openAssetStream(path: String): InputStream =
        context.assets.open(path, android.content.res.AssetManager.ACCESS_STREAMING)

    private fun assetLength(path: String): Long? = try {
        context.assets.openFd(path).use { it.length }
    } catch (_: IOException) {
        null
    }

    private fun pipeFromStream(stream: InputStream, lengthHint: Long?): ModelHandle {
        val (readFd, writeFd) = ParcelFileDescriptor.createPipe()
        thread(name = "ModelAssetLoader-${threadIds.incrementAndGet()}", isDaemon = true) {
            stream.use { source ->
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { sink ->
                    source.copyTo(sink)
                }
            }
        }
        return ModelHandle.FileDescriptorHandle(readFd, lengthHint)
    }

    companion object {
        private const val TAG = "ModelAssetLoader"
        private const val AES_GCM_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private val threadIds = AtomicInteger(0)
    }
}

sealed interface ModelHandle : Closeable {
    fun fileDescriptor(): ParcelFileDescriptor?
    fun length(): Long?

    data class FileDescriptorHandle(
        private val descriptor: ParcelFileDescriptor,
        private val lengthHint: Long?,
    ) : ModelHandle {
        override fun fileDescriptor(): ParcelFileDescriptor = descriptor
        override fun length(): Long? = lengthHint
        override fun close() {
            runCatching { descriptor.close() }
        }
    }

    data object Stub : ModelHandle {
        override fun fileDescriptor(): ParcelFileDescriptor? = null
        override fun length(): Long? = null
        override fun close() = Unit
    }
}
