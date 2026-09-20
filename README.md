# Dialer

A native Android phone app, written in Kotlin, that can serve as your device's default dialer. It works like any ordinary dialer — with one deliberate capability:

> **Call-log suppression for a single configured number.** When you *place* an outgoing call to that number, its entry is automatically removed from the device's system call log once the call ends. Every other number is logged normally, and incoming calls are never affected.

The target number is stored **write-only**: it can be set, overwritten, or cleared, but it is never displayed back anywhere in the UI.

The project is designed to build entirely in CI — no Android Studio, no local SDK. Push to GitHub, and a workflow produces an installable debug-signed APK.

---

## Features

- Full default-dialer implementation: registers as a dialer and acquires the default-dialer role via `RoleManager` (Android 10+), with a `TelecomManager` fallback on older releases.
- `InCallService` with a complete in-call screen — incoming and outgoing calls, live call state and duration timer, and answer / reject / hang-up / mute / speaker / hold all bound to the `Call` object. Minimal multi-call (hold/swap) handling included.
- Dial-pad home screen and a write-only settings screen; the target number persists in `SharedPreferences`.
- Robust call-log suppression: matches numbers by their **last 10 digits** (so `+91…`, `0…`, and bare formats all resolve to the same number) and polls the call-log provider with retries, because Android writes the log row *after* the call disconnects.
- Material 3 dark UI.

## Tech

| | |
|---|---|
| Language | Kotlin |
| Min / Target SDK | 26 / 35 |
| Build | Gradle 8.9 · Android Gradle Plugin 8.6.1 · JDK 17 |
| Output | Debug-signed APK (`app-debug.apk`) |

---

## Build

Every push runs the **Build Debug APK** workflow (`.github/workflows/build.yml`). To run it manually: **Actions → Build Debug APK → Run workflow**.

The workflow checks out the source, provisions JDK 17 and Gradle 8.9, regenerates the Gradle wrapper, and runs `./gradlew assembleDebug`. The resulting APK is signed with the AGP debug key — no signing configuration required — and uploaded as the **app-debug** artifact, available at the bottom of each completed run.

> **Why the wrapper is generated in CI.** The binary `gradle-wrapper.jar` cannot be created through the GitHub web editor, so the workflow regenerates it (`gradle wrapper --gradle-version 8.9`) before invoking `./gradlew`. This keeps the entire repository editable from a browser — no binary files to upload.

To build locally instead:

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Install

1. Download the **app-debug** artifact from the latest successful run and extract `app-debug.apk`.
2. Install it (allow installs from your browser/file manager when prompted).
3. Launch **Dialer** and grant the phone, call-log, contacts, and notification permissions — call-log access is required for suppression.
4. Set it as the default dialer when prompted, or via **Settings → Apps → Default apps → Phone app**.
5. In-app **Settings** (gear icon): enter the number to suppress and save. The field clears on save by design; the stored number is never shown again.

Placing a call to that number now removes its call-log entry within a few seconds of hanging up.

---

## Architecture

- **`SilentInCallService`** — the bound `InCallService`; the single source of truth for live calls. Forwards call lifecycle to `CallManager` and drives the in-call UI.
- **`CallManager`** — process-wide holder for active `Call` objects and audio routing (mute/speaker). Records call direction via `Call.Details.callDirection` (API 29+), falling back to the initial call state, so only outgoing calls qualify for suppression.
- **`CallLogCleaner`** — deletes matching rows from `CallLog.Calls`, retrying on a short interval to absorb the write-after-disconnect delay.
- **`Prefs`** — write-only persistence for the target number, plus last-10-digit normalization.
- **`MainActivity` / `InCallActivity` / `SettingsActivity`** — dial pad, in-call UI, and configuration.

## Version compatibility

Gradle, AGP, and the JDK are pinned to a matched set (Gradle 8.9 / AGP 8.6.1 / JDK 17). If you change one, keep the others compatible — the versions live in `build.gradle` (AGP), `.github/workflows/build.yml` (Gradle), and `gradle/wrapper/gradle-wrapper.properties` (Gradle distribution). `compileSdk`/`targetSdk` are set in `app/build.gradle`.

## Scope & limitations

Suppression applies only to **outgoing calls you place** to the configured number, and acts only on the **local device call log** you own. It does not affect incoming calls, the other party's device, or carrier records — no installed app can.
