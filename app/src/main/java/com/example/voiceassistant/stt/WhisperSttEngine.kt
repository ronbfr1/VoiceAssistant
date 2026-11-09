package com.example.voiceassistant.stt

import android.content.Context
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.voiceassistant.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * Whisper.cpp STT Engine - offline, private, accurate speech recognition.
 * 
 * Whisper.cpp (https://github.com/ggerganov/whisper.cpp) is a C/C++ port of OpenAI's Whisper.
 * Models are quantized (ggml format) and run efficiently on CPU.
 * 
 * This implementation:
 * 1. Downloads ggml model on first use (~39MB for base, ~75MB for small)
 * 2. Uses JNI to call whisper.cpp functions
 * 3. Processes audio in real-time with VAD
 * 
 * For production: compile whisper.cpp as .so, create JNI bridge.
 * Here: skeleton with fallback to system SpeechRecognizer.
 */
class WhisperSttEngine {

    private val TAG = "WhisperSttEngine"
    
    private var context: Context? = null
    private var systemRecognizer: SpeechRecognizer? = null
    private var useWhisper = false
    private var whisperModelPath: String? = null
    private var isInitialized = false
    private var isListening = false
    
    // Callbacks
    private var onResultListener: ((String) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onListeningStartListener: (() -> Unit)? = null
    private var onListeningStopListener: (() -> Unit)? = null
    private var onPartialResultListener: ((String) -> Unit)? = null

    init {
        checkWhisperAssets()
    }

    fun initialize(
        context: Context,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStart: () -> Unit,
        onListeningStop: () -> Unit,
        onPartialResult: (String) -> Unit
    ) {
        this.context = context
        this.onResultListener = onResult
        this.onErrorListener = onError
        this.onListeningStartListener = onListeningStart
        this.onListeningStopListener = onListeningStop
        this.onPartialResultListener = onPartialResult
        
        if (useWhisper && whisperModelPath != null) {
            initializeWhisper()
        } else {
            initializeSystemRecognizer()
        }
    }

    private fun checkWhisperAssets() {
        context?.let { ctx ->
            // Check for whisper.cpp model (ggml format)
            val modelFile = File(ctx.filesDir, "whisper/ggml-base.bin")
            if (modelFile.exists()) {
                whisperModelPath = modelFile.absolutePath
                useWhisper = true
                Log.i(TAG, "Whisper model found locally")
            } else {
                Log.i(TAG, "Whisper model not found, will use system SpeechRecognizer")
                useWhisper = false
            }
        }
    }

    private fun initializeWhisper() {
        // TODO: Initialize whisper.cpp via JNI
        // Requires:
        // 1. whisper.cpp compiled as libwhisper.so
        // 2. JNI bridge class with native methods:
        //    - nativeInit(modelPath: String)
        //    - nativeStart()
        //    - nativeStop()
        //    - nativeProcessAudio(buffer: ByteArray)
        //    - nativeGetResult(): String
        // 3. VAD (Voice Activity Detection) for endpoints
        
        Log.i(TAG, "Whisper.cpp JNI not implemented yet, falling back to system SpeechRecognizer")
        useWhisper = false
        initializeSystemRecognizer()
    }

    private fun initializeSystemRecognizer() {
        context?.let { ctx ->
            if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
                systemRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
                systemRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    
                    override fun onBeginningOfSpeech() {
                        handler.post { onListeningStartListener?.invoke() }
                    }
                    
                    override fun onRmsChanged(rmsdB: Float) {}
                    
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    
                    override fun onEndOfSpeech() {
                        handler.post { onListeningStopListener?.invoke() }
                    }
                    
                    override fun onError(error: Int) {
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Unknown error: $error"
                        }
                        handler.post { onErrorListener?.invoke(errorMsg) }
                        isListening = false
                    }
                    
                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let {
                            handler.post { onResultListener?.invoke(it) }
                        }
                        isListening = false
                    }
                    
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let {
                            handler.post { onPartialResultListener?.invoke(it) }
                        }
                    }
                    
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
                isInitialized = true
            } else {
                onErrorListener?.invoke("Speech recognition not available")
            }
        }
    }

    fun startListening() {
        if (isListening) return
        
        if (useWhisper && isInitialized) {
            startWhisperListening()
        } else if (systemRecognizer != null && isInitialized) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000)
            }
            isListening = true
            systemRecognizer?.startListening(intent)
        }
    }

    private fun startWhisperListening() {
        // TODO: Implement whisper.cpp continuous listening
        // 1. Start audio recording (AudioRecord)
        // 2. Feed chunks to whisper.cpp via JNI
        // 3. Handle VAD for endpoint detection
        // 4. Return results via callback
        
        Log.w(TAG, "Whisper listening not implemented, using fallback")
        startListening()
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        
        if (useWhisper) {
            stopWhisperListening()
        } else {
            systemRecognizer?.stopListening()
        }
    }

    private fun stopWhisperListening() {
        // TODO: Stop whisper.cpp recording
    }

    fun cancel() {
        isListening = false
        systemRecognizer?.cancel()
    }

    fun destroy() {
        isListening = false
        systemRecognizer?.destroy()
        systemRecognizer = null
        isInitialized = false
    }

    fun isWhisperAvailable(): Boolean = useWhisper

    fun downloadWhisperModel(model: String = "base", onProgress: (Int) -> Unit) {
        // Models: tiny (~39MB), base (~75MB), small (~244MB), medium (~769MB)
        // For mobile: base or small recommended
        // Download from: https://huggingface.co/ggerganov/whisper.cpp
        
        scope.launch {
            try {
                val modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-${model}.bin"
                val modelFile = File(context!!.filesDir, "whisper/ggml-${model}.bin")
                modelFile.parentFile?.mkdirs()
                
                downloadWithProgress(modelUrl, modelFile, onProgress)
                
                whisperModelPath = modelFile.absolutePath
                useWhisper = true
                initializeWhisper()
                
                handler.post { onResultListener?.invoke("") } // Signal ready
            } catch (e: Exception) {
                handler.post { onErrorListener?.invoke("Download failed: ${e.message}") }
            }
        }
    }

    private fun downloadWithProgress(url: String, file: File, onProgress: (Int) -> Unit) {
        // Use OkHttp or DownloadManager
        // Placeholder
    }

    companion object {
        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    }
}