cmake_minimum_required(VERSION 3.4.1)

# Voice Assistant Native Engine
# Core JNI bridge + command processor
add_library(
    voice_engine
    SHARED
    voice_engine.c
)

find_library(
    log_lib
    log
)

target_link_libraries(
    voice_engine
    ${log_lib}
)

# TODO: For Piper TTS integration
# - Add ONNX Runtime as prebuilt shared library
# - Create piper_jni.c with JNI bridge to ONNX Runtime
# - Link: onnxruntime, piper_phonemize (if available)
#
# Example:
# add_library(onnxruntime SHARED IMPORTED)
# set_target_properties(onnxruntime PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${ANDROID_ABI}/libonnxruntime.so)
#
# add_library(piper_jni SHARED piper_jni.c)
# target_link_libraries(piper_jni onnxruntime ${log_lib})
#
# TODO: For Whisper.cpp integration  
# - Add whisper.cpp as static library or prebuilt
# - Create whisper_jni.c with JNI bridge
# - Link: whisper, ggml
#
# add_library(whisper_jni SHARED whisper_jni.c)
# target_link_libraries(whisper_jni whisper ggml ${log_lib})