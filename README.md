# MorseRinger

An Android app that turns incoming-caller identification into a **Morse code audio signal**. When your phone rings, MorseRinger looks up the caller's name in your contacts and plays it back as audible Morse on your phone's ringtone audio stream.

If the caller is not in your contacts, the literal string `Unknown` is keyed instead.

## Why does this exist?

For amateur-radio operators and Morse-code learners, the constant ringing of a phone is wasted training time. MorseRinger replaces the conventional ringtone with a short, repeating Morse transmission of who is calling, so you can practice copy in everyday situations and identify callers without looking at the screen.

## How it works

```
Incoming call
   │
   ▼
┌────────────────────────┐
│ IncomingCallService    │  Android CallScreeningService
│ (call screening role)  │  – respondToCall(allow)
└──────────┬─────────────┘  – extracts phone number, starts foreground service
           │
           ▼
┌────────────────────────┐
│ MorseForegroundService │  – FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
│                        │  – keeps the process alive while the phone rings
└──────────┬─────────────┘  – delegates playback to MorseCodeAudioPlayer
           │
           ▼
┌────────────────────────┐
│ ProcessIncomingCallUseCase │
│  ContactRepository → name  │
│  TextToMorseUseCase → dits │
└──────────┬─────────────────┘
           │  ".... .. / - .... . .-. ."
           ▼
┌────────────────────────┐
│ MorseCodeAudioPlayer   │  – TelephonyCallback observes RINGING → IDLE
│                        │  – stops playback once the call is answered
└──────────┬─────────────┘  – hard 60 s safety timeout
           │
           ▼
┌────────────────────────┐
│ MorseCodeGenerator     │  – AudioTrack (PCM 16-bit, 44.1 kHz)
│ (PCM buffer synthesis) │  – sine tone with click-suppressing fade envelope
└────────────────────────┘  – usage = NOTIFICATION_RINGTONE
```

The app **does not reject, log, or silence calls** — it only listens for the
RINGING state and plays audio in parallel with the system ringer. For the
cleanest experience, set the system ringtone to *Silent* (the in-app
*Ringtone Shortcut* card opens the relevant Settings screen).

## Architecture

The codebase follows a small Clean-Architecture-style layering inside a single Gradle module:

| Layer        | Package                                                   | Responsibility                                                  |
|--------------|-----------------------------------------------------------|-----------------------------------------------------------------|
| **domain**   | `com.uschwar.morseringer.domain`                          | Pure Kotlin — `MorseEncoder`, `MorseSettings`, repos, use cases |
| **data**     | `com.uschwar.morseringer.data`                            | Android implementations — `MorseCodeAudioPlayer`, `MorseCodeGenerator`, repos |
| **service**  | `com.uschwar.morseringer.service`                         | Telephony glue — `IncomingCallService`, `MorseForegroundService` |
| **ui**       | `com.uschwar.morseringer.ui`                              | Jetpack Compose — `MainScreen`, `SettingsViewModel`             |
| **DI**       | `MorseRingerApp.AppContainer`                             | Manual constructor injection (no DI framework)                  |

### Key design points

- **`MorseCodeGenerator`** isolates sine-wave PCM synthesis (and the click-suppressing fade envelope) from the `AudioTrack` playback hardware management in `MorseCodeAudioPlayer`. This keeps signal generation easy to unit-test and reuse for both the looping ringtone path and the one-shot in-app preview.
- **`MorseSettings`** owns all timing / range constants (defaults, min/max for sliders, the `1200/WPM` unit-duration formula). Both UI and persistence layers depend on these constants instead of duplicating numbers.
- **`MorseCodeAudioPlayer`** observes the telephony state via `TelephonyCallback` and cancels playback as soon as the call leaves the RINGING state (with a hard 60 s safety timeout). It is invoked from `MorseForegroundService`, which keeps the process alive during ringing via `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
- **Persistence**: WPM and pitch are stored in Jetpack DataStore (`morse_settings`), exposed as a `Flow<MorseSettings>`.

## Required permissions

| Permission                    | Purpose                                         |
|-------------------------------|-------------------------------------------------|
| `READ_CONTACTS`               | Resolve caller phone number → display name     |
| `READ_PHONE_STATE`            | Detect when the call is answered/declined      |
| `ANSWER_PHONE_CALLS`          | Required for the Call Screening role            |
| `POST_NOTIFICATIONS`          | System notifications (Android 13+)             |
| `MODIFY_AUDIO_SETTINGS`       | Configure the audio stream                      |

In addition, the user must grant the **Call Screening App** role, which is requested in-app via `RoleManager`.

A prominent disclosure dialog explains contact data usage before the runtime permission prompt.

## Privacy

Contact information is read **on-device only**, used solely to translate caller names to Morse code, and never persisted, logged, or transmitted off the device.

## Building

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # signed release APK (requires keystore.properties)
./gradlew :app:testDebugUnitTest      # JVM unit tests
```

Requirements:

- JDK 21
- Android SDK 37 (compileSdk) / minSdk 31 / targetSdk 37
- Android Gradle Plugin 9.2.0 · Kotlin 2.3.21

### Release signing

`app/build.gradle.kts` reads `keystore.properties` from the project root if present:

```properties
storeFile=morseringer-release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

If the file is missing the release build is unsigned (debug works regardless).

## Testing

| Tooling           | Where                                | Used for                           |
|-------------------|--------------------------------------|------------------------------------|
| JUnit 5 + MockK   | `app/src/test/java`                  | Pure-domain unit tests             |
| Compose UI Test   | `app/src/androidTest/java` *(future)*| Instrumented UI tests              |

Existing unit tests cover:

- `MorseEncoderTest` — full A–Z, 0–9, punctuation, unmapped-fallback, word separators
- `MorseSettingsTest` — `1200/WPM` formula, fallback for `wpm <= 0`, default values
- `TextToMorseUseCaseTest` — delegation to encoder
- `ProcessIncomingCallUseCaseTest` — known/unknown caller paths with mocked repository

### Adding more tests

When adding behavior to the **domain** layer, add a JUnit 5 test alongside the
existing ones — no Android dependencies required. For audio or telephony
behavior, prefer Robolectric or instrumented tests in `androidTest`.

## Project layout

```
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/uschwar/morseringer/
    │   │   ├── MorseRingerApp.kt          ← Application + AppContainer (DI)
    │   │   ├── domain/
    │   │   │   ├── MorseEncoder.kt
    │   │   │   ├── model/                 ← MorseSettings
    │   │   │   ├── repo/ContactRepository.kt
    │   │   │   └── usecase/               ← TextToMorseUseCase, ProcessIncomingCallUseCase
    │   │   ├── data/
    │   │   │   ├── audio/                 ← MorseCodeAudioPlayer, MorseCodeGenerator
    │   │   │   └── repo/                  ← SettingsRepository, ContactRepositoryImpl
    │   │   ├── service/                   ← IncomingCallService, MorseForegroundService
    │   │   └── ui/                        ← MainActivity, MainScreen, SettingsViewModel, theme/
    │   └── res/                           ← strings (en, de), themes, icons
    └── test/java/                         ← JUnit 5 unit tests
```

## License

Personal project — no license has been declared.
