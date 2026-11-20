# Aidly: a proactive voice companion for older adults

Android app built for **#BuildHybridAI 2026** with **Firebase AI Logic** (hybrid on-device / cloud inference).
Aidly reads your messages aloud, tells you what any label, bottle or letter says, reminds you of medicines and appointments,
and lets your family know you are okay, sharing only what *you* choose.

> Built from scratch during the submission period (started 17 Nov 2026). The optional hardware dock in [`dock/`](dock/) is a
> **design prototype only: it has not been built or tested.**

## Why hybrid matters here
- **Private and instant when it can be:** short reminders and conversation run first with rules and Gemini Nano on the phone.
- **Cloud only when needed, and only if the person turns it on:** voice transcription, photos, web search. The first screen asks for consent and
  Settings > Privacy can turn the cloud off or delete all data at any time.
- Every answer shows a badge with **where it came from** (on your phone, in Google's cloud, or phone rules) ([`Motor.kt`](app/src/main/java/com/aidly/app/Motor.kt)).
- The on-device path uses `firebase-ai-ondevice` (latest, 16.0.0-beta08), which runs the phone's AICore Gemini Nano through the ML Kit Prompt API; which Nano version runs depends on the phone.

## How Firebase AI Logic is used at runtime
| Feature | Inference mode | Code |
|---|---|---|
| Short conversation | `PREFER_ON_DEVICE` (Nano, cloud fallback); `ONLY_ON_DEVICE` when the cloud is off | [`AsistenteVoz.kt`](app/src/main/java/com/aidly/app/AsistenteVoz.kt) |
| Voice to structured reminders and appointments (schema-constrained JSON) | `PREFER_ON_DEVICE` then `ONLY_IN_CLOUD`; local rules first | [`Interprete.kt`](app/src/main/java/com/aidly/app/Interprete.kt), [`ParserLocal.kt`](app/src/main/java/com/aidly/app/ParserLocal.kt) |
| "What does this say?" (photo of a label, bottle, letter, message) | `PREFER_IN_CLOUD` multimodal, structured JSON with a confidence field | [`AnalizaValidador.kt`](app/src/main/java/com/aidly/app/AnalizaValidador.kt), [`MedicinaValidador.kt`](app/src/main/java/com/aidly/app/MedicinaValidador.kt) |
| Speech to text | Gemini (cloud) | [`GeminiStt.kt`](app/src/main/java/com/aidly/app/GeminiStt.kt) |
| Web questions | Gemini with Google Search grounding | [`Busqueda.kt`](app/src/main/java/com/aidly/app/Busqueda.kt) |
| On-device model status and background download | `FirebaseAIOnDevice.checkStatus()` and `download()` at app start; shown in Settings > Privacy | [`ModeloLocal.kt`](app/src/main/java/com/aidly/app/ModeloLocal.kt) |

Also used: **Firebase App Check** (debug provider for development and submission), **Anonymous Auth + Cloud Firestore** (consent-based family circle,
rules in [`firestore.rules`](firestore.rules)) and **Firebase Hosting** (family web page in [`familia/`](familia/)).

## Features
Voice assistant (messages and email by notification, reminders with exact alarms, calendar, web questions, call/open apps) · "What does this say?" camera reader ·
daily check-in · consent-based family circle with a code · SOS · big-key dialer · optional caller announcement · animated face · widgets · Spanish and English UI.

## Privacy
[`docs/PRIVACY.md`](docs/PRIVACY.md) (ES/EN), [`docs/PRIVACY_DATA_FLOW.md`](docs/PRIVACY_DATA_FLOW.md) (every data flow and its source file) and
[`docs/PLAY_DATA_SAFETY.md`](docs/PLAY_DATA_SAFETY.md). No ads, analytics or crash reporting. Sensitive local data is encrypted with the Android Keystore.

## Build and run
Requirements: Android Studio or JDK 17 and the Android SDK, minSdk 26, an Android phone (a Gemini Nano / AICore capable phone is needed to see the on-device path).

1. Create a Firebase project and enable **Firebase AI Logic** (Gemini Developer API), **Authentication (Anonymous)**, **Firestore** and **App Check**.
2. Add an Android app with package `com.aidly.app`, download `google-services.json` and place it at `app/google-services.json`
   (see `app/google-services.json.example`; the real file is git-ignored).
3. `./gradlew assembleDebug` (`gradlew.bat` on Windows) and install `app/build/outputs/apk/debug/app-debug.apk`.
4. On first launch, read the debug App Check token from logcat (`DebugAppCheckProvider`) and register it in *Firebase console > App Check > Apps > Manage debug tokens*.
5. Family page: copy `familia/config.example.js` to `familia/config.js` with your web app config, deploy with `firebase deploy --only hosting,firestore:rules`.

Checks: `python pruebas/validar_textos.py`, `python pruebas/validar_idiomas.py`, `python pruebas/verificar_release.py`.

## Third-party
Atkinson Hyperlegible font (SIL Open Font License 1.1, `app/src/main/assets/licencias/`), AndroidX, Jetpack Compose, Glance, CameraX and Firebase SDKs (Apache-2.0).

## License
[Apache License 2.0](LICENSE).
