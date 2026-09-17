# Aira — Personal AI Agent for Android

A real, functional Android AI assistant. Aira is built around Google's
**Gemini Flash** model (`gemini-3.8-flash` via `gemini-flash-latest`),
natural voice input/output, and legitimate Android automation through
the Accessibility Service, official APIs, and well-known deep links.

> ⚠️ **Model note.** "Gemini 3.6 Flash" is *not* an official API model
> identifier — it appears only in Google's deprecation tables as a
> replacement alias. As of 2026-09-17 the official, generally-available
> Flash model is **`gemini-3.8-flash`**, which is what Aira uses.
> The alias `gemini-flash-latest` always resolves to the current
> model. You can swap models in `app/build.gradle.kts`
> (`GEMINI_MODEL_PRIMARY`) — Aira's API code is model-agnostic.

---

## What's in here

| Module                          | What it does                                                       |
|---------------------------------|--------------------------------------------------------------------|
| `ai/GeminiEngine.kt`            | Real Gemini REST client with JSON-schema response                  |
| `ai/Action.kt` + `Plan.kt`      | Typed action system Gemini produces                                |
| `ai/ActionValidator.kt`         | Whitelist / clamp / sanitize every action                          |
| `ai/TaskPlanner.kt`             | Build a plan → validate → confirm → execute → report               |
| `ai/ActionExecutor.kt`          | Dispatch each action to the right Android subsystem                |
| `voice/VoiceAssistant.kt`       | STT + TTS, natural Hindi/English/Hinglish capable                 |
| `accessibility/AiraAccessibilityService.kt` | Real screen reading, tap, type, scroll, swipe      |
| `device/DeviceController.kt`    | Flash, brightness, volume, BT, alarm, media, files                  |
| `messaging/MessagingController.kt` | WhatsApp / Messenger / Instagram / Snapchat / SMS deep-links   |
| `messaging/CallingController.kt`  | Dial / call, contact lookup                                    |
| `web/BrowserController.kt`      | Open URL in default browser                                         |
| `web/SearchController.kt`       | Web search via deep links                                          |
| `media/YouTubeController.kt`    | YouTube search/play via `vnd.youtube://` URIs                      |
| `media/SpotifyController.kt`    | Spotify search/play via `spotify:` URI scheme                      |
| `memory/MemoryManager.kt`       | Persistent memories (DataStore JSON, no secrets)                   |
| `learning/PreferenceLearner.kt` | Lightweight preference learning from repeated commands             |
| `mood/MoodAnalyzer.kt`          | Mood estimation (label only — never an "intrusion")                |
| `background/BackgroundAssistantService.kt` | Optional, configurable, polite nudges          |
| `permissions/PermissionManager.kt` | Centralised, explanatory permission flow                          |
| `security/SecureStorage.kt`     | EncryptedSharedPreferences (AES-256-GCM) for the API key           |
| `errors/AiraError.kt`           | Typed errors with friendly messages                                |
| `multitasking/TaskExecutionManager.kt` | Concurrent task queue with cancellation                     |

UI is **Jetpack Compose + Material 3**, with five screens
(Home / Chat / Memory / Permissions / API key / Settings) all
connected through a single `AiraViewModel`.

---

## How Aira actually works

1. The user types or speaks a request.
2. `GeminiEngine.chat()` calls
   `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent`
   with `responseMimeType=application/json` and a strict schema.
3. Gemini returns a `Plan` (a list of typed `Action`s + a friendly reply).
4. `ActionValidator` checks every action against an allow-list — for
   example, `TAP_ELEMENT` is rejected if no text/resourceId/contentDesc
   is provided (no raw coordinates allowed), `OPEN_URL` is restricted
   to known safe hosts, and `MAKE_CALL`/`SEND_MESSAGE`/`SEND_SMS`
   require explicit confirmation.
5. `ActionExecutor` runs each action through the matching real
   subsystem: real intents for app launches, real `BluetoothAdapter`
   toggle, real `CameraManager.setTorchMode`, real `MediaSession`
   transport, real `SmsManager.sendTextMessage`, real WhatsApp
   `wa.me/` deep-link, real YouTube `vnd.youtube://` URI, real
   `spotify:` URI, etc.
6. UI updates from the `TaskExecutionManager` + `TaskPlanner` flows.

**Aira never claims success when the action didn't actually succeed.**
If WhatsApp doesn't open, if the user disabled Bluetooth, if SMS
delivery failed, Aira surfaces the real error.

---

## Real third-party-app reality check

| Capability                   | Real? | Notes                                                    |
|------------------------------|-------|----------------------------------------------------------|
| Open WhatsApp chat with text | ✅    | `wa.me/<phone>?text=...` deep-link — WhatsApp shows pre-filled chat, user taps send |
| Send WhatsApp automatically  | ❌    | WhatsApp requires user tap on send; we open the chat    |
| Open Spotify search          | ✅    | `spotify:search:...` opens Spotify app at the result    |
| Spotify autoplay             | ❌    | Spotify Android SDK requires OAuth; we open the screen  |
| YouTube search & play        | ✅    | `vnd.youtube://results?search_query=...`                |
| Auto-tap into YouTube player | ⚠️    | Possible via Accessibility Service if user enables it  |
| Bluetooth toggle             | ✅    | `BluetoothAdapter.enable()/disable()` with permission  |
| Wi-Fi toggle                 | ⚠️    | Android 10+ restricts programmatic toggling            |
| SMS                          | ✅    | `SmsManager.sendTextMessage()` — real delivery         |
| Phone calls                  | ✅    | `ACTION_CALL` if permission granted, else `ACTION_DIAL`|
| Flashlight                   | ✅    | `CameraManager.setTorchMode()`                         |
| Brightness                   | ✅    | `Settings.System.putInt(Settings.System.SCREEN_BRIGHTNESS,...)` |
| Set alarm                    | ✅    | `AlarmClock.ACTION_SET_ALARM` intent                    |
| Media control                | ✅    | `MediaSessionManager` active sessions                  |
| File search                  | ⚠️    | MediaStore only (Android 10+ scoped storage); we surface what we can find |

---

## Building

```bash
# from the repo root
./gradlew :app:assembleDebug

# install to a connected device
./gradlew :app:installDebug
```

Minimum SDK: 26 (Android 8.0)
Target SDK: 35 (Android 15)

---

## First-run setup

1. Launch Aira.
2. Tap **Settings → Gemini API key** and paste your key from
   <https://aistudio.google.com/app/apikey>.
3. Grant **Microphone** permission when prompted (for voice).
4. (Optional) Open **Permissions & access**, enable the ones you want
   Aira to use. For full automation, also enable the Aira Accessibility
   Service from system Settings — the screen explains why.
5. Tap the mic on the Home screen and try one of:
   - "Open Spotify"
   - "Turn on Bluetooth"
   - "Send Rahul a WhatsApp message saying I'll call you later"
   - "Set an alarm for 7 AM and open my files"

---

## Permissions & accessibility

Aira never silently obtains permissions. Every entry on the
**Permissions & access** screen has a human-readable title and a
short explanation of why it's needed. Revoking any of them only
limits the features that depend on it.

The Accessibility Service is the most sensitive permission. It is
declared in the manifest with `BIND_ACCESSIBILITY_SERVICE` and a
`accessibility_service_config.xml` that lists exactly which event
types Aira cares about. It only acts when you explicitly ask for a
screen-aware action (e.g. *"tap the send button"*), and it inspects
the accessibility tree by **semantic match** (text / resource-id /
content-description) — never by raw coordinates.

---

## Architecture

```
                 ┌────────────────────────┐
                 │    AiraApplication     │
                 │  (DI / singletons)     │
                 └──────────┬─────────────┘
                            │
        ┌───────────────────┼─────────────────────────┐
        ▼                   ▼                         ▼
   GeminiEngine      TaskPlanner.kt ────► ActionValidator
        │                   │                         │
        ▼                   ▼                         ▼
   (HTTPS JSON)      TaskExecutionManager  ───► ActionExecutor
                                                  │
                       ┌──────────────┬───────────┼───────────────┐
                       ▼              ▼           ▼               ▼
                  DeviceCtrl     MessagingCtrl  MediaCtrl     BrowserCtrl
                       │              │           │               │
                       └──────────────┴───────────┴───────────────┘
                                       │
                                       ▼
                                Voice (STT/TTS)
```

UI is a thin Compose layer over `AiraViewModel` which exposes a
single `AiraUiState` StateFlow.

---

## Security

- **API key**: stored in `EncryptedSharedPreferences` with an
  AES-256-GCM master key in the AndroidKeyStore. Never logged.
- **Memory**: persisted as JSON in DataStore; sensitive values
  (passwords / OTPs / API keys / tokens) are refused at write time.
- **Network**: HTTPS only. `usesCleartextTraffic="false"`.
- **Background data**: backup rules explicitly exclude the secure
  prefs and the memory database.
- **ProGuard**: shrink & obfuscate release builds while keeping
  Gemini response models intact.

---

## What's NOT here (and why)

- No rooting / no hidden APIs.
- No background screen recording.
- No silent auto-grant of permissions.
- No "fake success" buttons.

Everything that survived the cut actually does what it says it does
on a stock Android device, or it explicitly tells the user that
Android or the relevant third-party prevents it and offers the
closest legitimate alternative.

---

Made with care for natural, capable, honest AI assistance.