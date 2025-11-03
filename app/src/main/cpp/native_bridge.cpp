#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>

namespace {
constexpr const char *kTag = "RagAgentStub";
bool g_asrReady = false;
bool g_llmReady = false;
std::mutex g_mutex;

static void logDebug(const char *message) {
    __android_log_print(ANDROID_LOG_DEBUG, kTag, "%s", message);
}

}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_mllm_nativebridge_NativeRagBridge_nativeInitWhisper(
        JNIEnv *env, jclass, jint fd, jlong length, jstring language) {
    (void) env;
    (void) fd;
    (void) length;
    (void) language;
    std::scoped_lock lock(g_mutex);
    g_asrReady = true;
    logDebug("Initialized stub Whisper model");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_mllm_nativebridge_NativeRagBridge_nativeTranscribe(
        JNIEnv *env, jclass, jshortArray audioBuffer, jint sampleRate) {
    if (!g_asrReady) {
        return nullptr;
    }
    jsize length = env->GetArrayLength(audioBuffer);
    const auto seconds = static_cast<double>(length) / static_cast<double>(sampleRate);
    std::string transcript = "[stub] Độ dài âm thanh: " + std::to_string(seconds) + "s";
    return env->NewStringUTF(transcript.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_mllm_nativebridge_NativeRagBridge_nativeReleaseWhisper(
        JNIEnv *, jclass) {
    std::scoped_lock lock(g_mutex);
    g_asrReady = false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_mllm_nativebridge_NativeRagBridge_nativeInitLlm(
        JNIEnv *env, jclass, jint fd, jlong length, jint threads, jint contextLength) {
    (void) env;
    (void) fd;
    (void) length;
    (void) threads;
    (void) contextLength;
    std::scoped_lock lock(g_mutex);
    g_llmReady = true;
    logDebug("Initialized stub LLM model");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_mllm_nativebridge_NativeRagBridge_nativeGenerate(
        JNIEnv *env, jclass, jstring prompt) {
    if (!g_llmReady) {
        return nullptr;
    }
    const char *promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string response = "[stub] Trả lời cho: ";
    if (promptChars != nullptr) {
        response += promptChars;
        env->ReleaseStringUTFChars(prompt, promptChars);
    }
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_mllm_nativebridge_NativeRagBridge_nativeReleaseLlm(
        JNIEnv *, jclass) {
    std::scoped_lock lock(g_mutex);
    g_llmReady = false;
}
