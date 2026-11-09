#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <stdlib.h>

#define LOG_TAG "VoiceEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef struct {
    JavaVM* jvm;
    jobject java_obj;
    jmethodID on_voice_result;
    jmethodID on_error;
    jmethodID on_listening_start;
    jmethodID on_listening_stop;
} VoiceEngine;

static VoiceEngine g_engine = {0};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_engine.jvm = vm;
    LOGI("VoiceEngine loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("VoiceEngine unloaded");
}

JNIEXPORT void JNICALL
Java_com_example_voiceassistant_VoiceEngine_nativeInit(JNIEnv* env, jobject thiz) {
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    g_engine.java_obj = (*env)->NewGlobalRef(env, thiz);
    g_engine.on_voice_result = (*env)->GetMethodID(env, clazz, "onVoiceResult", "(Ljava/lang/String;)V");
    g_engine.on_error = (*env)->GetMethodID(env, clazz, "onError", "(Ljava/lang/String;)V");
    g_engine.on_listening_start = (*env)->GetMethodID(env, clazz, "onListeningStart", "()V");
    g_engine.on_listening_stop = (*env)->GetMethodID(env, clazz, "onListeningStop", "()V");
    LOGI("Native init complete");
}

JNIEXPORT void JNICALL
Java_com_example_voiceassistant_VoiceEngine_nativeStartListening(JNIEnv* env, jobject thiz) {
    LOGI("Starting voice recognition");
    if (g_engine.on_listening_start) {
        (*env)->CallVoidMethod(env, g_engine.java_obj, g_engine.on_listening_start);
    }
}

JNIEXPORT void JNICALL
Java_com_example_voiceassistant_VoiceEngine_nativeStopListening(JNIEnv* env, jobject thiz) {
    LOGI("Stopping voice recognition");
    if (g_engine.on_listening_stop) {
        (*env)->CallVoidMethod(env, g_engine.java_obj, g_engine.on_listening_stop);
    }
}

JNIEXPORT void JNICALL
Java_com_example_voiceassistant_VoiceEngine_nativeProcessCommand(JNIEnv* env, jobject thiz, jstring command) {
    const char* cmd = (*env)->GetStringUTFChars(env, command, NULL);
    LOGI("Processing command: %s", cmd);
    
    char* response = NULL;
    if (strstr(cmd, "привет") || strstr(cmd, "hello") || strstr(cmd, "hi")) {
        response = strdup("Привет! Чем могу помочь?");
    } else if (strstr(cmd, "время") || strstr(cmd, "time")) {
        response = strdup("Сейчас не реализовано получение времени.");
    } else if (strstr(cmd, "погода") || strstr(cmd, "weather")) {
        response = strdup("Погода пока недоступна. Нужно подключить API.");
    } else if (strstr(cmd, "как дела") || strstr(cmd, "how are")) {
        response = strdup("У меня всё отлично! А у тебя?");
    } else if (strstr(cmd, "спасибо") || strstr(cmd, "thanks")) {
        response = strdup("Пожалуйста!");
    } else if (strstr(cmd, "кто ты") || strstr(cmd, "who are")) {
        response = strdup("Я — твой голосовой ассистент в стиле Gemini.");
    } else {
        response = strdup("Понял. Но пока не умею выполнять эту команду.");
    }
    
    if (g_engine.on_voice_result && response) {
        jstring jresponse = (*env)->NewStringUTF(env, response);
        (*env)->CallVoidMethod(env, g_engine.java_obj, g_engine.on_voice_result, jresponse);
        (*env)->DeleteLocalRef(env, jresponse);
    }
    
    (*env)->ReleaseStringUTFChars(env, command, cmd);
    free(response);
}

JNIEXPORT void JNICALL
Java_com_example_voiceassistant_VoiceEngine_nativeSpeak(JNIEnv* env, jobject thiz, jstring text) {
    LOGI("TTS requested from native");
    // TTS будет обрабатываться в Java слое
}