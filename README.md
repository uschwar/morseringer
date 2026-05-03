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
└──────────┬─────────────┘  – extracts phone number
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
│ CallAudioPlayer        │  – TelephonyCallback observes RINGING → IDLE
│                        │  – stops playback once the call is answered
└──────────┬─────────────┘  – hard 60 s safety timeout
           │
           ▼
┌────────────────────────┐
│ SineWaveAudioGenerator │  – AudioTrack (PCM 16-bit, 44.1 kHz)
│ (AudioPlayer port)     │  – sine tone with click-suppressing fade envelope
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
| **domain**   | `com.uschwar.morseringer.domain`                          | Pure Kotlin — `MorseEncoder`, `MorseSettings`, ports, use cases |
| **data**     | `com.uschwar.morseringer.data`                            | Android implementations — `SineWaveAudioGenerator`, repos       |
| **service**  | `com.uschwar.morseringer.service`                         | Telephony glue — `IncomingCallService`, `CallAudioPlayer`       |
| **ui**       | `com.uschwar.morseringer.ui`                              | Jetpack Compose — `MainScreen`, `SettingsViewModel`             |
| **DI**       | `MorseRingerApp.AppContainer`                             | Manual constructor injection (no DI framework)                  |

### Key design points

- **`AudioPlayer` port** (`domain.port.AudioPlayer`) decouples the domain from `android.media.AudioTrack`. Tests can substitute a fake.
- **`MorseSettings`** owns all timing / range constants (defaults, min/max for sliders, the `1200/WPM` unit-duration formula). Both UI and persistence layers depend on these constants instead of duplicating numbers.
- **`CallAudioPlayer`** receives its dependencies (`AudioPlayer`, `SettingsRepository`, `ProcessIncomingCallUseCase`) via the constructor — no service-locator lookups inside.
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
- Android SDK 37 (compileSdk) / minSdk 31 / targetSdk 36
- Android Gradle Plugin 9.2.0 · Kotlin 2.2.10

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
    │   │   │   ├── port/AudioPlayer.kt
    │   │   │   ├── repo/ContactRepository.kt
    │   │   │   └── usecase/               ← TextToMorseUseCase, ProcessIncomingCallUseCase
    │   │   ├── data/
    │   │   │   ├── audio/SineWaveAudioGenerator.kt
    │   │   │   └── repo/                  ← SettingsRepository, ContactRepositoryImpl
    │   │   ├── service/                   ← IncomingCallService, CallAudioPlayer
    │   │   └── ui/                        ← MainActivity, MainScreen, SettingsViewModel, theme/
    │   └── res/                           ← strings (en, de), themes, icons
    └── test/java/                         ← JUnit 5 unit tests
```

## License

Personal project — no license has been declared.
