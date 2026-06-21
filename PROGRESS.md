# RawTracker — Status

_Last updated: Thu Jun 18 2026 — all roadmap items complete._

See [`README.md`](README.md) for full setup, build, and architecture docs. This file is the build-out log.

## Status: COMPLETE ✅

All PRD features implemented and verified cross-platform (Android emulator live e2e + iOS simulator build/launch/render/data-pipeline).

| Item | Status |
| --- | --- |
| Scaffold + brutalist duotone design system | ✅ Fredoka / JetBrains Mono / Phosphor Fill / 2px violet on cream |
| SQLDelight data layer (meal/goal/pending) + repo + CSV export | ✅ |
| Input screen, Settings (goals + duotone presets), App shell, DI | ✅ |
| GeminiClient (Ktor, structured JSON schema, multimodal) | ✅ live verified (burger photo → 1100cal → saved) |
| c7 Photo/multimodal parse (camera + gallery) | ✅ live verified |
| c9 Offline queue + queued/error states | ✅ badge + auto-drain-on-reconnect verified live |
| c10 Health sync | ✅ Android Health Connect live (perms + nutrition + water); iOS HealthKit implemented/builds |
| c11 Android Glance widgets (1×1 camera + 2×2 progress/water) | ✅ build + deep-link + HC water write |
| c12 iOS WidgetKit widgets (App Group shared data) | ✅ ext target added, app+ext builds, .appex embedded, 8-key data write verified |
| c13 README + final cross-platform QA pass | ✅ |

## Tests

`./gradlew :shared:allTests` → **green** (JVM + Android unit + iOS native).

- `GeminiClientTest`, `MealRepositoryTest` (in-memory JDBC sqlite), `ParseAndSaveFlowTest` (full parse→save→totals).

Final build check: `:androidApp:assembleDebug` ✅ and `:shared:linkDebugFrameworkIosSimulatorArm64` ✅.

## Handy commands

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$PATH"
cd /Users/stephen/RawTracker

./gradlew :shared:allTests                   # all tests
./gradlew :androidApp:installDebug           # build+install on emulator
adb shell am start -n com.rawtracker.app/com.rawtracker.MainActivity

# iOS
DEV=557B3F1A-6467-430F-AF50-0C229F8BA2CB
xcrun simctl boot $DEV; open -a Simulator
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination "id=$DEV" -derivedDataPath /tmp/dd CODE_SIGNING_ALLOWED=NO build
xcrun simctl install $DEV /tmp/dd/Build/Products/Debug-iphonesimulator/RawTracker.app
xcrun simctl launch $DEV com.rawtracker.app
```

## Known platform caveats

- Live Gemini parse needs a real `GEMINI_API_KEY` in `local.properties` (placeholder → clean "API key not valid" banner).
- Health Connect / HealthKit writes + App-Group widget sharing fully resolve only on a **signed/device build**; on simulator the data pipeline is identical and degrades gracefully.
- Interactive iOS widget placement needs `idb` (not installed); widget extension builds, embeds, and reads the verified shared data.
