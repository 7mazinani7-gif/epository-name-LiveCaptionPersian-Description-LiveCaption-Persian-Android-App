# LiveCaption Persian — v0.9.4

A Persian-first Android live-caption app with a transparent floating overlay, internal playback capture for compatible apps, microphone speech recognition, and on-device translation.

## Implemented in source
- Draggable floating captions with a fully transparent background (no black panel).
- Original text, translation, or both at once.
- Adjustable font size, text color, outline color/width, and shadow.
- RTL-aware caption rendering and the app launcher icon in the in-app header.
- Separate start/stop controls for playback capture, microphone recognition, and the floating overlay.
- Android Playback Capture audio is passed to Vosk for local speech recognition.
- Selectable Vosk input models for Persian, English, and Arabic.
- ML Kit language identification and on-device translation after model download.
- Creator credit: Mazinani7 & AI.

## Limitations
- Android captures playback only from apps that allow Audio Playback Capture. DRM-protected content, calls, and some apps cannot be captured.
- First-time Vosk and ML Kit model downloads require internet access. Recognition and translation are local after model download.
- Microphone recognition uses Android SpeechRecognizer; language support and offline availability depend on the device's installed speech service.
- The Arabic Vosk model is large and may be slow or storage-intensive on lower-end devices.
- Appearance settings are runtime settings and are not all persisted across app restarts yet.
- The source archive has been inspected and patched, but no APK has been built in this environment and no real-device test on Samsung Galaxy A30 / Android 11 has been completed.

## Build configuration
- Android Gradle Plugin 8.7.3; Kotlin 2.0.21; Gradle 8.9; JDK 17
- compileSdk 35; minSdk 29; targetSdk 30
- versionCode 12; versionName 0.9.4

GitHub Actions builds a debug APK on pushes to `main` or via manual `workflow_dispatch`, then uploads `app/build/outputs/apk/debug/app-debug.apk` as an artifact. See [README_FA.md](README_FA.md) for detailed Persian notes.
