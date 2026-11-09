package com.example.voiceassistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.example.voiceassistant.overlay.AssistantOverlayService;
import com.example.voiceassistant.screen.ScreenCaptureService;
import com.example.voiceassistant.stt.WhisperSttEngine;
import com.example.voiceassistant.tts.PiperTtsEngine;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceEngine {
    private static final String TAG = "VoiceEngine";
    
    private Context context;
    private Activity activity;  // For MediaProjection
    
    // Recognition engines
    private SpeechRecognizer systemSpeechRecognizer;
    private WhisperSttEngine whisperSttEngine;
    private boolean useWhisper = false;
    
    // TTS engines
    private TextToSpeech systemTextToSpeech;
    private PiperTtsEngine piperTtsEngine;
    private boolean usePiper = false;
    
    // Overlay service
    private AssistantOverlayService overlayService;
    
    // Screen capture
    private ScreenCaptureService screenCaptureService;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    
    private VoiceCallback callback;
    private boolean isListening = false;
    private int pendingMediaProjectionRequest = 0;

    static {
        System.loadLibrary("voice_engine");
    }

    public interface VoiceCallback {
        void onVoiceResult(String result);
        void onError(String error);
        void onListeningStart();
        void onListeningStop();
        void onScreenCaptureRequested();  // New: request screen capture permission
    }

    public VoiceEngine(Context context, VoiceCallback callback) {
        this.context = context;
        this.callback = callback;
        
        // Try to get activity for MediaProjection
        if (context instanceof Activity) {
            this.activity = (Activity) context;
            mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
        
        initSpeechRecognizers();
        initTTSEngines();
        nativeInit();
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
        if (mediaProjectionManager == null) {
            mediaProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
    }

    public void setOverlayService(AssistantOverlayService service) {
        this.overlayService = service;
        if (service != null) {
            service.setVoiceEngine(this);
        }
    }

    private native void nativeInit();
    private native void nativeStartListening();
    private native void nativeStopListening();
    private native void nativeProcessCommand(String command);
    private native void nativeSpeak(String text);

    private void initSpeechRecognizers() {
        // System SpeechRecognizer (fallback)
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            systemSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            systemSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(android.os.Bundle params) {
                    Log.d(TAG, "System: Ready for speech");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "System: Beginning of speech");
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "System: End of speech");
                }

                @Override
                public void onError(int error) {
                    String errorMsg = getErrorText(error);
                    Log.e(TAG, "System speech error: " + errorMsg);
                    isListening = false;
                    if (callback != null) {
                        callback.onListeningStop();
                        callback.onError(errorMsg);
                    }
                }

                @Override
                public void onResults(android.os.Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);
                        Log.d(TAG, "System recognized: " + result);
                        nativeProcessCommand(result);
                    }
                }

                @Override
                public void onPartialResults(android.os.Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, android.os.Bundle params) {}
            });
        }

        // Whisper.cpp (offline, primary if available)
        whisperSttEngine = new WhisperSttEngine();
        whisperSttEngine.initialize(
            context,
            result -> {
                if (callback != null) callback.onVoiceResult(result);
                speak(result);
            },
            error -> {
                if (callback != null) callback.onError(error);
            },
            () -> {
                if (callback != null) callback.onListeningStart();
            },
            () -> {
                if (callback != null) callback.onListeningStop();
            },
            partial -> {
                // Optional: handle partial results
            }
        );
        
        useWhisper = whisperSttEngine.isWhisperAvailable();
    }

    private void initTTSEngines() {
        // System TTS (fallback)
        systemTextToSpeech = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = systemTextToSpeech.setLanguage(new Locale("ru", "RU"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Russian language not supported");
                    systemTextToSpeech.setLanguage(Locale.US);
                }
            } else {
                Log.e(TAG, "System TTS initialization failed");
            }
        });

        // Piper TTS (offline, primary if available)
        piperTtsEngine = new PiperTtsEngine();
        piperTtsEngine.initialize(
            context,
            () -> {
                Log.i(TAG, "Piper TTS ready");
                usePiper = true;
            },
            error -> {
                Log.w(TAG, "Piper TTS failed: " + error);
                usePiper = false;
            }
        );
    }

    public void startListening() {
        if (isListening) return;
        
        if (useWhisper) {
            whisperSttEngine.startListening();
        } else if (systemSpeechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            
            isListening = true;
            nativeStartListening();
            systemSpeechRecognizer.startListening(intent);
        }
    }

    public void stopListening() {
        if (!isListening) return;
        
        if (useWhisper) {
            whisperSttEngine.stopListening();
        } else if (systemSpeechRecognizer != null) {
            isListening = false;
            systemSpeechRecognizer.stopListening();
            nativeStopListening();
        }
    }

    public void speak(String text) {
        if (usePiper && piperTtsEngine != null) {
            piperTtsEngine.speak(text);
        } else if (systemTextToSpeech != null) {
            systemTextToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    // Screen capture integration
    public void requestScreenCapture() {
        if (activity == null || mediaProjectionManager == null) {
            if (callback != null) callback.onError("Screen capture requires Activity context");
            return;
        }
        
        if (mediaProjection != null) {
            startRegionSelection();
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            activity.startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
            pendingMediaProjectionRequest = REQUEST_MEDIA_PROJECTION;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            startRegionSelection();
        }
    }

    private void startRegionSelection() {
        if (screenCaptureService == null) {
            screenCaptureService = new ScreenCaptureService();
        }
        
        screenCaptureService.startRegionSelection(
            mediaProjection,
            this,
            bitmap -> {
                // TODO: Send bitmap to vision model or analyze locally
                // For now, just notify
                if (callback != null) {
                    callback.onVoiceResult("Область экрана захвачена. Анализ пока не реализован.");
                }
            }
        );
    }

    // Engine switching
    public void setUseWhisper(boolean use) {
        useWhisper = use && whisperSttEngine != null && whisperSttEngine.isWhisperAvailable();
    }

    public void setUsePiper(boolean use) {
        usePiper = use && piperTtsEngine != null && piperTtsEngine.isPiperAvailable();
    }

    public boolean isWhisperAvailable() {
        return whisperSttEngine != null && whisperSttEngine.isWhisperAvailable();
    }

    public boolean isPiperAvailable() {
        return piperTtsEngine != null && piperTtsEngine.isPiperAvailable();
    }

    public void downloadWhisperModel(String model, VoiceCallback progressCallback) {
        if (whisperSttEngine != null) {
            whisperSttEngine.downloadWhisperModel(model, progress -> {
                // Update progress
            });
        }
    }

    public void downloadPiperModel(String language, String variant, VoiceCallback progressCallback) {
        if (piperTtsEngine != null) {
            piperTtsEngine.downloadPiperModel(language, variant, progress -> {
                // Update progress
            });
        }
    }

    // JNI callbacks
    public void onVoiceResult(String result) {
        if (callback != null) {
            callback.onVoiceResult(result);
        }
        speak(result);
    }

    public void onError(String error) {
        if (callback != null) {
            callback.onError(error);
        }
    }

    public void onListeningStart() {
        if (callback != null) {
            callback.onListeningStart();
        }
    }

    public void onListeningStop() {
        if (callback != null) {
            callback.onListeningStop();
        }
    }

    public void destroy() {
        if (systemSpeechRecognizer != null) {
            systemSpeechRecognizer.destroy();
        }
        if (systemTextToSpeech != null) {
            systemTextToSpeech.shutdown();
        }
        if (whisperSttEngine != null) {
            whisperSttEngine.destroy();
        }
        if (piperTtsEngine != null) {
            piperTtsEngine.shutdown();
        }
        if (screenCaptureService != null) {
            // ScreenCaptureService cleans up itself
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Speech timeout";
            default: return "Unknown error: " + errorCode;
        }
    }
}