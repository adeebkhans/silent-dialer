# Silent Dialer

A working native Android dialer (Kotlin) that can be set as your device's **default phone app**. It behaves like a normal dialer for every number, with one special behavior:

> For a **single configurable target number**, when **you dial it** (outgoing only), the app automatically **removes that call's entry from the system call log** after the call ends. Every other number logs normally. Incoming calls from the target number are **not** affected.

The target number is **write-only**: the Settings screen lets you set/overwrite or clear it, but never displays the number it has stored.

Built to be compiled entirely in the cloud with **GitHub Actions** — no Android Studio and no local build tools required. You edit files in the GitHub web UI from your phone, the Action builds a debug-signed APK, and you download it from the Actions tab.

---

## What's in here

- Kotlin, `minSdk 26`, `compileSdk/targetSdk 35`, Gradle 8.9 + Android Gradle Plugin 8.6.1.
- Registers as a dialer and requests the **default-dialer role** via `RoleManager` (Android 10+) with a `TelecomManager` fallback for older versions.
- `InCallService` + a complete in-call UI: incoming/outgoing calls, live state + duration timer, and answer / reject / hang-up / mute / speaker / hold wired to the `Call` object, with minimal multi-call (hold) handling.
- Dial-pad main screen and a write-only Settings screen (target number persisted in `SharedPreferences`).
- Call-log deletion that **waits and retries** (Android writes the log row *after* disconnect) and matches numbers by their **last 10 digits** so `+91…`, `0…`, and bare formats all match.
- A GitHub Actions workflow that builds and uploads an installable **debug-signed** APK.

---

## 1. Create the repo and add the files from a phone browser

You cannot upload the binary `gradle-wrapper.jar` in the GitHub web editor — so this project **doesn't need you to**. The build workflow regenerates the Gradle wrapper in the cloud. You only ever paste text files.

1. Go to **github.com** → sign in → tap **+** (top right) → **New repository**.
2. Name it (e.g. `silent-dialer`), choose **Private** or **Public**, tick **Add a README**, then **Create repository**.
3. For every file in this project: open the repo → **Add file** → **Create new file** → type the **exact path** (including folders, using `/`, e.g. `app/src/main/AndroidManifest.xml`) into the filename box → paste the file's contents → **Commit changes**.
   - Typing `app/src/main/java/com/silentdialer/MainActivity.kt` automatically creates the folders.
4. Add **all** files listed in the "File list" section below. The `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.properties` files are plain text — paste them like any other file. There is **no** `gradle-wrapper.jar` to add.

> Tip: the GitHub mobile web editor is easier in **Desktop site** mode (browser menu → "Desktop site").

---

## 2. Trigger the build

The workflow runs automatically on every push (i.e. every time you commit a file). You can also run it manually:

1. Open the repo → **Actions** tab.
2. If prompted, click **I understand my workflows, enable them**.
3. Select **Build Debug APK** on the left → **Run workflow** → **Run workflow**.

Watch the run: green check = success, red X = failure (see Troubleshooting).

---

## 3. Download the APK

1. **Actions** tab → click the most recent successful **Build Debug APK** run.
2. Scroll to the **Artifacts** section at the bottom → tap **app-debug**.
3. It downloads a `app-debug.zip`. Open it with your phone's Files app and extract `app-debug.apk`.

---

## 4. Sideload and set as default dialer

1. **Enable unknown sources:** when you tap the APK, Android asks to allow installs from that app (your browser/Files app). Go to **Settings → Apps → Special access → Install unknown apps** → pick the app you're installing from → **Allow**. Then reopen the APK and **Install**.
2. Open **Silent Dialer**. It will request the phone/call-log/contacts/notification permissions — **Allow** them all (call-log access is required for the deletion feature).
3. Tap **Set as default dialer** (or Android's prompt) and confirm. On Android 10+ this is the system **Default apps → Phone app** chooser.
4. Open **Settings** (gear icon, top-right) → type the number you want hidden → **Save**. (The field stays blank afterwards by design — the number is stored but never shown.)
5. Place a call to that number from the dial pad. After you hang up, its entry is removed from the phone's call log within a few seconds.

---

## File list

```
.github/workflows/build.yml
.gitignore
build.gradle
settings.gradle
gradle.properties
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.properties
README.md
app/build.gradle
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/com/silentdialer/MainActivity.kt
app/src/main/java/com/silentdialer/SettingsActivity.kt
app/src/main/java/com/silentdialer/InCallActivity.kt
app/src/main/java/com/silentdialer/SilentInCallService.kt
app/src/main/java/com/silentdialer/CallManager.kt
app/src/main/java/com/silentdialer/CallLogCleaner.kt
app/src/main/java/com/silentdialer/CallNotifier.kt
app/src/main/java/com/silentdialer/Prefs.kt
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/activity_settings.xml
app/src/main/res/layout/activity_incall.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/styles.xml
app/src/main/res/values/themes.xml
app/src/main/res/drawable/ic_call.xml
app/src/main/res/drawable/ic_call_end.xml
app/src/main/res/drawable/ic_backspace.xml
app/src/main/res/drawable/ic_settings.xml
app/src/main/res/drawable/ic_mic_off.xml
app/src/main/res/drawable/ic_speaker.xml
app/src/main/res/drawable/ic_pause.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/bg_call_button.xml
app/src/main/res/drawable/bg_reject_button.xml
app/src/main/res/drawable/bg_dial_key.xml
app/src/main/res/drawable/bg_control_button.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

---

## Troubleshooting first builds (from the web UI only)

You have no local tooling, so here's how to fix the failures that actually happen, using only the GitHub web editor.

**How to read a failure:** Actions tab → click the red run → click the **build** job → expand the step with the red X. The last ~20 lines of that log say what broke.

1. **"Could not find gradle-wrapper.jar" / wrapper errors.**
   This project's workflow runs `gradle wrapper --gradle-version 8.9` *before* `./gradlew`, so the jar is generated in the cloud — you never commit it. If you see this, make sure you copied `.github/workflows/build.yml` exactly, and that `gradle/wrapper/gradle-wrapper.properties` says `gradle-8.9-bin.zip`.

2. **Gradle / Android Gradle Plugin version mismatch** (e.g. "Minimum supported Gradle version is X" or "Android Gradle plugin requires Java …").
   The pinned combo here is **Gradle 8.9 + AGP 8.6.1 + JDK 17** — a known-good match. If you change one, change the others to a compatible set. To fix from the web UI, edit **three** places to matching versions:
   - `build.gradle` → `id 'com.android.application' version 'AGP_VERSION'`
   - `.github/workflows/build.yml` → the two `8.9` values (`gradle-version:` and `--gradle-version`)
   - `gradle/wrapper/gradle-wrapper.properties` → `gradle-<version>-bin.zip`

3. **"Failed to install the following SDK components" / license errors.**
   The GitHub `ubuntu-latest` runner already has the Android SDK and accepts licenses. This project uses `compileSdk 35`, which is available. If a future SDK isn't installed on the runner, lower `compileSdk`/`targetSdk` in `app/build.gradle` to 34.

4. **"SDK location not found" / `local.properties`.**
   Never commit `local.properties` (it's git-ignored here). The runner sets the SDK path itself. If you accidentally created it, delete it via the web UI.

5. **Build is green but the app won't install ("App not installed").**
   You likely have another debug build of the same app installed, or a corrupt download. Uninstall any previous copy, re-download the artifact, and re-extract the `.apk` from the zip.

6. **The call log entry isn't deleted.**
   - The app must be the **default dialer** and you must have granted **call-log** permissions.
   - Deletion only applies to **outgoing** calls **you dial** to the saved number — not incoming calls.
   - The saved number must match by its **last 10 digits**. Re-save it in Settings if unsure.

---

## How the "hide" logic works (quick tour)

- `Prefs.kt` stores the target number and normalizes any number to its **last 10 digits** for comparison.
- `SilentInCallService` (the `InCallService`) forwards each `Call` to `CallManager`, which records whether the call was **outgoing** (via `Call.Details.callDirection` on API 29+, else the initial call state).
- When a call ends, if it was outgoing **and** its last-10-digits match the saved target, `CallLogCleaner.deleteWithRetry(...)` runs. It polls the `CallLog.Calls` provider every ~1.2s (up to 8 tries) because Android writes the log row *after* disconnect, then deletes every matching row.
- The Settings screen never reads the stored value back — it only overwrites or clears it.

> This is a legitimate feature of an app **you install and control** on **your own device**, acting on **your own** call log. It cannot hide anything on the other party's phone or on the carrier's records.
#   s i l e n t - d i a l e r  
 