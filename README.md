# Aidly: a proactive voice companion for older adults

Android app built for **#BuildHybridAI 2026** with **Firebase AI Logic** (hybrid on-device / cloud inference).
Aidly reads your messages aloud, tells you what a medicine box or any label, bottle or letter says, reminds you of medicines and appointments,
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
| **Take a photo** (one camera, one button): Aidly decides what it sees. A medicine box or bottle goes to a detailed card (name, dose, what the box says, spoken questions with a safety gate, add a reminder); anything else (letters, receipts, signs, possible scams) is read and explained | `PREFER_IN_CLOUD` multimodal, structured JSON with a confidence field | [`AnalizaValidador.kt`](app/src/main/java/com/aidly/app/AnalizaValidador.kt), [`MedicinaValidador.kt`](app/src/main/java/com/aidly/app/MedicinaValidador.kt) |
| Speech to text | Gemini (cloud) | [`GeminiStt.kt`](app/src/main/java/com/aidly/app/GeminiStt.kt) |
| **For you** (cards on what the person likes, read aloud, with follow-up conversation) | One Gemini call with Google Search grounding for fresh items; when the free search quota is out, a no-search call for general-knowledge cards (labelled as such); built-in cards when offline or with the cloud off. Follow-ups use `PREFER_ON_DEVICE` | [`FeedTarjetas.kt`](app/src/main/java/com/aidly/app/FeedTarjetas.kt), [`ParaTiActivity.kt`](app/src/main/java/com/aidly/app/ParaTiActivity.kt), [`AsistenteVoz.kt`](app/src/main/java/com/aidly/app/AsistenteVoz.kt) |
| Web questions | Gemini with Google Search grounding | [`Busqueda.kt`](app/src/main/java/com/aidly/app/Busqueda.kt) |
| On-device model status and background download | `FirebaseAIOnDevice.checkStatus()` and `download()` at app start; shown in Settings > Privacy | [`ModeloLocal.kt`](app/src/main/java/com/aidly/app/ModeloLocal.kt) |

Also used: **Firebase App Check** (debug provider for development and submission), **Anonymous Auth + Cloud Firestore** (consent-based family circle,
rules in [`firestore.rules`](firestore.rules)) and **Firebase Hosting** (family web page in [`familia/`](familia/)).

## Features
**For you** (the assistant brings things the person likes, with source and a safety filter; optional spoken offers in dock mode) · Voice assistant (messages and email by notification, reminders with exact alarms, calendar, web questions, call/open apps) · one-button **Take a photo** reader that recognises medicines (with "add a reminder", e.g. "blood pressure pill at 9:00 AM") and reads letters, receipts and signs ·
daily check-in · **dock mode** (USB-C code for the dock included, unit-tested against a simulated port only; big animated face, voice through the dock, optional on-device presence detection with MediaPipe that asks "are you okay?" if it stops seeing you after a conversation; no images are stored or sent) · consent-based family circle with a code · SOS · big-key dialer · optional caller announcement · widgets · Spanish and English UI **and voice** (commands, spoken answers, speech recognition and AI prompts follow the app language).

## How it is organised
- **UI:** Jetpack Compose, one activity per screen, shared components in [`Componentes.kt`](app/src/main/java/com/aidly/app/Componentes.kt); high contrast, large type that scales to 200 %, Android 15 edge-to-edge handled in [`Tema.kt`](app/src/main/java/com/aidly/app/Tema.kt).
- **Voice pipeline:** speech recognition (Gemini when the cloud is on, the system recognizer otherwise) → [`Agente.kt`](app/src/main/java/com/aidly/app/Agente.kt) routes the sentence using the per-language vocabulary in [`Lexico.kt`](app/src/main/java/com/aidly/app/Lexico.kt) → local rules → on-device or cloud model only when rules are not enough → text to speech in the app language. A safety gate in the medicine validators refuses to give dosing advice and points to a professional.
- **Languages:** Spanish and English share the same code paths. Per-language settings live in [`idiomas.json`](app/src/main/assets/config/idiomas.json) and the `values` / `values-en` string resources.
- **Dock (prototype):** [`dock/PROTOCOLO.md`](dock/PROTOCOLO.md) defines a small text protocol over USB serial; [`ProtocoloDock.kt`](app/src/main/java/com/aidly/app/ProtocoloDock.kt) and [`DockUsb.kt`](app/src/main/java/com/aidly/app/DockUsb.kt) implement the phone side. The attentive-companion rules (ask "are you okay?" after a silence, never detect falls) are a pure state machine in [`CompaniaAtenta.kt`](app/src/main/java/com/aidly/app/CompaniaAtenta.kt) with unit tests.

## What has and has not been verified
| Area | Status |
|---|---|
| Unit tests (attentive-companion state machine, dock protocol) | Passing (`./gradlew testDebugUnitTest`) |
| All screens, Spanish and English, normal and 200 % type, Android 15 emulator | Reviewed with screenshots; accessibility tree checked for unnamed or tiny touch targets |
| Voice routing | 25 English generated audio files route correctly through the real pipeline on the emulator (`pruebas/correr_audios.ps1 -Idioma en`); the Spanish suite predates the bilingual rewrite and is being re-run (free-tier quota) |
| Cloud features (Gemini via Firebase AI Logic, App Check, Firestore family circle) | Exercised on the emulator against the real Firebase project; subject to free-tier quotas |
| **Gemini Nano on a real phone** | **Not verified.** Neither the emulator nor the Galaxy A23 has AICore, so both show the "rules / cloud" fallback; a Nano-capable phone is still needed |
| Camera presence detection (MediaPipe) | Measured on a Galaxy A23 (Android 14, 3.6 GB RAM): about 4 analysed frames per second at 24 ms each, battery temperature unchanged after 40 s; found and fixed a CameraX binding bug that only showed on the real phone. Battery drain over longer runs not measured |
| Call announcer (Android 10+ call-screening role) | Not testable on the emulator image used |
| "For you": card parsing, safety filter, topic profile and offer policy | 21 unit tests passing; the cloud generator and the card conversation were run on the emulator (Google Search grounding had no free quota that day, so the fallback to no-search cards was the path exercised); not yet run on the Galaxy A23 |
| TalkBack walkthrough, USB audio and the dock hardware | Not yet verified; the dock is a design prototype |

## Test build and family page
A debug APK for judges will be linked from the Devpost submission. It uses the debug App Check provider.
The family page is served from Firebase Hosting at https://aidly-d108b.web.app (enter the code shown in the app under "My family").

## Limits and configuration: Google Search and the LLM key
Aidly never contains an LLM API key. Every Gemini call goes through **Firebase AI Logic** and uses the Gemini Developer API key that
Firebase creates and restricts inside *your* Firebase project (`app/google-services.json`, which is not in the repository). That means
**all usage counts against that project's quota**, including the judges' and testers' usage.

- **Gemini "Grounding with Google Search" has its own, small quota.** On the free tier of the submission project (`aidly-d108b`) it
  returned `QuotaExceededException` between 20 and 23 Nov 2026 with three different models, while ordinary chat, photo reading and
  speech-to-text kept working. Check your own limits at <https://ai.dev/rate-limit>.
- **What Aidly does when the search quota is out** (so nothing breaks):
  - *Weather questions* use Open-Meteo directly (free, no key).
  - *"For you" cards* come from public sources (Wikipedia "on this day" and BBC headlines), judged for suitability by Gemini
    (without search); then no-search general-knowledge cards from Gemini (labelled as such); then cards that ship inside the app.
  - *Other web questions* open Google with the question.
  - After a quota error the app pauses search attempts for 3 hours instead of retrying on every request.
- **To get full Google Search answers, either:**
  1. keep within the quota (ask fewer web questions; the cache and the pause help), or
  2. use **your own key/quota**: create your own Firebase project, enable Firebase AI Logic, put your `google-services.json` in
     `app/` (see *Build and run*) and, if you need more than the free allowance, enable billing on that project. The key is managed by
     Firebase in your project; nothing needs to be pasted into the source.
  3. To use a different LLM provider you would have to replace the two search-dependent files, [`Busqueda.kt`](app/src/main/java/com/aidly/app/Busqueda.kt)
     and [`FeedTarjetas.kt`](app/src/main/java/com/aidly/app/FeedTarjetas.kt); the rest of the app does not depend on Search.
- **Public sources used without Gemini Search:** Wikipedia REST API (text under CC BY-SA, shown as "Wikipedia"), BBC RSS feeds (headlines
  for non-commercial syndication, shown as "BBC News" / "BBC Mundo") and Open-Meteo (free, non-commercial use). They are only contacted
  when the person has the cloud switched on, and each source's address sees the phone's IP like any website would.

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
Atkinson Hyperlegible font (SIL Open Font License 1.1, `app/src/main/assets/licencias/`), AndroidX, Jetpack Compose, Glance, CameraX, Firebase SDKs and MediaPipe Tasks Vision with its BlazeFace and Pose Landmarker (lite) models (Apache-2.0).

## License
[Apache License 2.0](LICENSE).
