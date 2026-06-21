# RawTracker

An AI-powered, local-first macro logger built with **Kotlin Multiplatform** + **Compose Multiplatform**. Snap a photo or type a sentence; Gemini parses it into structured macros; everything is stored on-device in SQLite and (optionally) mirrored into Apple Health / Android Health Connect.

> Design language: **Brutalist Sticker-Book** — high-contrast duotone (cream `Canvas` / violet `Ink`), `Fredoka` display + `JetBrains Mono` body, 2px borders, Phosphor Fill icons.

---

## Features

| Area | What it does |
| --- | --- |
| **iMessage-style input** | One text field + camera + gallery + send. Type "two eggs and toast" or attach a photo. |
| **AI parsing** | `gemini-2.5-flash` with a strict JSON response schema → `{ name, calories, protein, carbs, fat }`. Clean, human-readable error banners on failure. |
| **Confirm sheet** | Parsed result shown in an editable sheet before it's committed. |
| **Local-first storage** | SQLDelight (native SQLite on iOS, Android SQLite on Android). Meals, goals, and a pending-retry queue. |
| **Static goals** | Calorie + macro targets set in Settings, persisted, drive the home progress ring/cards. |
| **Offline queue** | Failed parses are queued, badged in the header (`N queued`), and auto-drained on reconnect. |
| **Health sync (write-only)** | Android Health Connect (`NutritionRecord` + `HydrationRecord`) and Apple HealthKit (`HKQuantitySample`). Silent; degrades gracefully if unavailable. |
| **CSV export** | One-tap export of all meals to a local CSV file. |
| **Home-screen widgets** | Android Glance + iOS WidgetKit: a 1×1 camera quick-action (deep-links `rawtracker://capture`) and a 2×2 today-progress + water quick-add. |

---

## Architecture

```
shared/                         Kotlin Multiplatform module (all business logic + UI)
  commonMain/com/rawtracker/
    App.kt                      Compose app shell + navigation
    AppContainer.kt             Manual DI container (driver, repo, client, connectivity, health)
    RawTrackerController.kt     UI state holder (Flow-based); parse/save/water/retry/health
    ai/GeminiClient.kt          Ktor client, structured schema, multimodal (text+image)
    data/                       Models, MealRepository, SQLDelight, Platform interfaces
    design/                     Theme, Type, Icons (Phosphor), Components (brutalist primitives)
    ui/                         InputScreen, ParseSheet, SettingsScreen, CameraCapture
  androidMain/                  AndroidPlatform (driver, Connectivity, Health Connect), container
  iosMain/                      IosPlatform (native driver, Connectivity, HealthKit), App Group writer

androidApp/                     Android host + Glance widgets (ProgressWidget, CameraWidget)
iosApp/                         iOS host (SwiftUI) + RawTrackerWidget extension (WidgetKit)
```

Platform-specific behaviour lives behind `commonMain` interfaces (`Connectivity`, `HealthSync`) implemented per-platform. The widget data bridge differs by platform: Android Glance reads SQLite directly; iOS writes totals/goals to a shared App Group `UserDefaults` (`group.com.rawtracker.app`) that the WidgetKit extension reads.

---

## Prerequisites

- **JDK 21**
- **Android SDK** + an emulator or device (Health Connect requires API 34+ / the Health Connect app)
- **Xcode** (for iOS) with an iOS 17+ simulator/device
- A **Gemini API key** ([Google AI Studio](https://aistudio.google.com/app/apikey))

## Setup

1. Put your Gemini key in `local.properties` at the repo root:

   ```properties
   GEMINI_API_KEY=your_key_here
   ```

   It's injected at build time via BuildKonfig — it never ships in source.

2. (Android) Make sure the SDK location is set in `local.properties` (`sdk.dir=...`).

---

## Build & run

### Android

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$PATH"

./gradlew :androidApp:installDebug
adb shell am start -n com.rawtracker.app/com.rawtracker.MainActivity
```

### iOS

```bash
DEV=<your-simulator-udid>            # xcrun simctl list devices
xcrun simctl boot "$DEV"; open -a Simulator

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination "id=$DEV" -derivedDataPath /tmp/dd \
  CODE_SIGNING_ALLOWED=NO build

xcrun simctl install "$DEV" /tmp/dd/Build/Products/Debug-iphonesimulator/RawTracker.app
xcrun simctl launch "$DEV" com.rawtracker.app
```

> Health Connect / HealthKit writes and App-Group-backed widget sharing require a **signed build on a real device** (or a provisioning profile). The code degrades gracefully and the data pipeline is identical on simulator.

---

## Testing

```bash
./gradlew :shared:allTests                   # JVM + Android unit + iOS native
./gradlew :shared:testDebugUnitTest          # JVM/Android only (fast)
./gradlew :shared:iosSimulatorArm64Test      # iOS native target only
```

Coverage:

- **`GeminiClientTest`** — Ktor `MockEngine`: schema request shape, success parse, API-error surfacing, multimodal payload.
- **`MealRepositoryTest`** — in-memory JDBC SQLite: CRUD, today-totals aggregation, goals, pending queue.
- **`ParseAndSaveFlowTest`** — full parse → save → totals integration.

Both `:androidApp:assembleDebug` and `:shared:linkDebugFrameworkIosSimulatorArm64` are part of the cross-platform build check.

---

## Deep links

`rawtracker://capture` launches the app and immediately opens the camera — used by both the Android and iOS camera widgets.

---

## Notes

- AI output is constrained by a strict JSON schema, so malformed responses are caught and surfaced rather than crashing.
- Goals are static by design (no dynamic recalculation); change them in Settings.
- Health sync is **write-only** — RawTracker never reads your health data.
