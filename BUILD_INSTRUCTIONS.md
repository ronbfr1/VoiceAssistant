# Voice Assistant - Build & Install Instructions

## Requirements

1. **Java JDK 17+** (required for Android Gradle Plugin 8.2+)
   - Download: https://adoptium.net/temurin/releases/
   - Set `JAVA_HOME` environment variable

2. **Android SDK** (API 34)
   - Via Android Studio: https://developer.android.com/studio
   - Or command line tools: https://developer.android.com/studio#command-tools
   - Set `ANDROID_HOME` environment variable

3. **Gradle 8.5+** (will be downloaded via wrapper)

## Quick Start

### 1. Initialize Gradle Wrapper (one-time)

```bash
# If you have gradle installed globally
gradle wrapper

# Or use the included gradlew (after step 2)
```

### 2. Build Debug APK

```bash
# Linux/macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

### 3. Install on Device

```bash
# Make sure device is connected via USB with USB debugging enabled
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
VoiceAssistant/
├── app/
│   ├── src/main/
│   │   ├── cpp/
│   │   │   ├── voice_engine.c      # Native C voice processing
│   │   │   └── CMakeLists.txt      # NDK build config
│   │   ├── java/com/example/voiceassistant/
│   │   │   ├── VoiceEngine.java    # JNI bridge + SpeechRecognizer/TTS
│   │   │   ├── VoiceEngine.kt      # (if created)
│   │   │   ├── MainActivity.kt     # Jetpack Compose UI (Gemini style)
│   │   │   ├── VoiceService.kt     # Foreground service
│   │   │   └── VoiceAssistantApplication.kt
│   │   ├── res/
│   │   │   ├── values/strings.xml
│   │   │   ├── values/colors.xml   # Gemini color scheme
│   │   │   ├── values/themes.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── gradle/wrapper/gradle-wrapper.properties
```

## Features Implemented

### Native C Layer (voice_engine.c)
- JNI bridge for voice commands
- Simple command processor (Russian/English)
- Extensible for ML models (Whisper, etc.)

### Java/Kotlin Layer
- **SpeechRecognizer** - Android 13 on-device speech recognition
- **TextToSpeech** - Russian/English TTS
- **Foreground Service** - Background listening
- **Runtime Permissions** - MICROPHONE, FOREGROUND_SERVICE_MICROPHONE

### UI (Jetpack Compose - Material 3)
- Gemini-style dark theme
- Animated microphone button with ripple effect
- Speaking indicator with animated dots
- Message bubbles (user/assistant)
- Listening animation

## Commands Supported

| Command (RU) | Command (EN) | Response |
|--------------|--------------|----------|
| привет | hello/hi | Привет! Чем могу помочь? |
| время | time | (placeholder) |
| погода | weather | (placeholder - needs API) |
| как дела | how are | У меня всё отлично! |
| спасибо | thanks | Пожалуйста! |
| кто ты | who are | Я — твой голосовой ассистент |

## Extending the Assistant

### Add New Commands
Edit `voice_engine.c` in `Java_com_example_voiceassistant_VoiceEngine_nativeProcessCommand`:

```c
if (strstr(cmd, "your_keyword")) {
    response = strdup("Your response");
}
```

### Add ML Model (Whisper/Coqui)
1. Add model files to `app/src/main/assets/`
2. Link TensorFlow Lite / ONNX Runtime in CMakeLists.txt
3. Implement inference in `voice_engine.c`

### Connect to LLM API
1. Add HTTP client (OkHttp) in Java layer
2. Send recognized text to your API
3. Stream response to TTS

## Permissions (Android 13+)

The app requests:
- `RECORD_AUDIO` - Microphone access
- `FOREGROUND_SERVICE` - Background operation
- `FOREGROUND_SERVICE_MICROPHONE` - Mic in foreground service

## Troubleshooting

### Build fails with "JAVA_HOME not set"
```bash
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot"
[Environment]::SetEnvironmentVariable("JAVA_HOME", $env:JAVA_HOME, "User")

# Linux/macOS
export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
echo 'export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk' >> ~/.bashrc
```

### Build fails with "SDK not found"
```bash
# Windows
$env:ANDROID_HOME = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"

# Linux/macOS
export ANDROID_HOME=$HOME/Android/Sdk
```

### ADB device not found
```bash
adb kill-server
adb start-server
adb devices
# Make sure USB debugging is ON on device
```

### Microphone permission denied
- Settings > Apps > Voice Assistant > Permissions > Microphone > Allow
- Or grant via: `adb shell pm grant com.example.voiceassistant android.permission.RECORD_AUDIO`

## Next Steps

1. **Add Wake Word** - Porcupine, Precise, or custom TensorFlow Lite model
2. **Integrate LLM** - Connect to local (Ollama) or cloud API
3. **Add Skills** - Weather, calendar, smart home, etc.
4. **Offline STT** - Whisper.cpp or Vosk for fully offline
5. **Custom Voice** - Piper TTS or Coqui for custom voices

## License

MIT - Feel free to modify and distribute.