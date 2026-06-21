# AGENTS.md

## Learned User Preferences

- Plan large work as a commit-by-commit list where each commit is plain-English and independently testable; report testing status before committing.
- Prefer full, true, end-to-end live tests as early as possible (run on emulator/simulator, screenshot, check logcat) over claiming success from compilation alone.
- Widgets must resize to ANY dimension and reflow cleanly with no empty gaps or clipping across all launcher sizes (1×1, 3×1, 1×5, 3×3); verify visually, ideally against real launcher screenshots, not just harness PNGs.
- Maintain a strict "Brutalist Sticker-Book" identity everywhere (app, widgets, icons): exactly two colors (canvas + ink), no third accent/gradient/shadow, oversized Fredoka numerals + uppercase JetBrains Mono micro-caps labels.

## Learned Workspace Facts

- RawTracker is a Kotlin Multiplatform + Compose Multiplatform app (Android + iOS); `applicationId`/bundle is `com.rawtracker.app` while the code namespace/package is `com.rawtracker`. Launch activities as `com.rawtracker.app/com.rawtracker.MainActivity` (and `.../com.rawtracker.widget.WidgetPreviewActivity`).
- Pinned stack (forced by ImagePickerKMP 1.0.41): Kotlin 2.3.20, Compose MP 1.10.3, Ktor 3.4.x, AGP 8.13.2, minSdk 26, compileSdk 36, iOS 15+; uses SQLDelight (`app.cash.sqldelight`), Glance 1.1.1 (Android-only), HealthKMP, BuildKonfig (Gemini key), Phosphor icons, `gemini-2.5-flash`. DevSrSouza `compose-icons` has no `phosphor` artifact and is Kotlin 1.9.x (breaks iOS klib ABI on 2.3.20).
- The persistent Cursor Shell aborts the rest of a command line at the first non-zero exit (so `ls`/`grep`/`pkill`/`pgrep` with no match kills following steps); guard each step with `|| true`.
- Do NOT separate shell commands with raw newlines (use `;` or `&&`); newlines outside quoted strings cause only the first line to run.
- The Android emulator must be launched fully detached (Python `subprocess.Popen(..., start_new_session=True)`); `nohup ... &`/`disown` is insufficient — children of the agent's transient shell get reaped ~60-90s after the launching shell tears down. Only `-no-window` headless boots reliably from the automation context.
- ANDROID_HOME is `/opt/homebrew/share/android-commandlinetools`; `emulator`/`adb` are often not on PATH in fresh shells, so use full paths (`$ANDROID_HOME/emulator/emulator`, `$ANDROID_HOME/platform-tools/adb`). AVD name: `rawtracker`.
- There is a debug `WidgetPreviewActivity` (`adb shell am start -n com.rawtracker.app/com.rawtracker.widget.WidgetPreviewActivity`) that renders the Glance widget at a matrix of DpSizes to PNGs under `/sdcard/Android/data/com.rawtracker.app/files/widget_preview` for visual verification.
- Glance constraints: renders to RemoteViews (not full Compose), no variable-font axis morphing, custom fonts are awkward (app Fredoka/JetBrains Mono live in Compose resources, not consumable directly), `FontWeight.Bold` only, no real border/stroke modifier, discrete `sp` steps + `maxLines=1`; emoji/text glyphs may be unacceptable — favor type/icon primitives. Plan stepped scale + content gating, not continuous stretch type.
- iOS widgets (WidgetKit) can't free-resize like Android — map sizes to discrete `WidgetFamily`; iOS lags Android on parity (hardcoded canvas/ink colors, ignores widget visibility prefs, different +FOOD/+WATER deep-link behavior). Extend App Group `UserDefaults` bridge in `IosPlatform.writeWidgetData()` for color/pref parity.
- Design system core lives in `shared/src/commonMain/kotlin/com/rawtracker/design/` (`Theme.kt`, `Components.kt` with the `inkBorder` 2px/5dp primitive, `Type.kt`, `Icons.kt` `RawIcons`, `ColorPicker.kt`); duotone is user-configurable (`DuotonePrefs` in SQLite, read by Android widgets).
- In `StrReplace`/edit content, avoid unicode escape sequences like `\u2026` mixed with literal newlines — they break JSON serialization; use the literal character (e.g. `…`) instead.
