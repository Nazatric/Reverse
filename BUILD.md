# Building *The Gadget* for Android

The native app lives in `android/` and is a standard Gradle + Kotlin + Jetpack Compose project.
There is no build step for the web app involved — the web sources in `src/` are the *reference*,
never a dependency of the APK.

## 1. Prerequisites

| Tool | Version used in CI | Notes |
| --- | --- | --- |
| JDK | 17 (Temurin) | AGP 8.13 requires 17+ |
| Android SDK platform | `android-36` | `compileSdk = 36`; **`android-37` is not required and is not available on current CI images** |
| Build tools | `36.0.0` | |
| Gradle | 8.14.3 | The wrapper (`android/gradlew`) pins the same line |
| Android Gradle Plugin | 8.13.0 | |
| Kotlin | 2.4.20 | Compose compiler plugin matches this version |

Android Studio (latest stable) works out of the box: open the `android/` directory as a project.

## 2. Command-line build

```bash
cd android
./gradlew assembleDebug            # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease          # -> app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest        # core parity unit tests (JVM, no device needed)
./gradlew assembleDebugAndroidTest # the parity screenshot harness
```

On a machine with a device or emulator attached:

```bash
./gradlew installDebug
adb shell am start -n com.thegadget.app.debug/com.thegadget.app.MainActivity
```

## 3. Signing

`assembleRelease` is wired to the **debug keystore** on purpose, so a fresh clone can produce a
release APK with no secrets. Before publishing, add a real keystore:

```kotlin
// android/app/build.gradle.kts
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("GADGET_KEYSTORE"))
        storePassword = System.getenv("GADGET_STORE_PASSWORD")
        keyAlias = System.getenv("GADGET_KEY_ALIAS")
        keyPassword = System.getenv("GADGET_KEY_PASSWORD")
    }
}
```

Minification (`R8`) and resource shrinking are on for release; keep rules live in
`android/app/proguard-rules.pro` and cover kotlinx.serialization, Room, Media3 and the HiveMQ
client.

## 4. Permissions and why

| Permission | Purpose |
| --- | --- |
| `INTERNET` | MQTT brokers for homies; external web games |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | The Media3 playback service |
| `POST_NOTIFICATIONS` | Media/notification controls (requested once at runtime) |
| `WAKE_LOCK` | Keeps the CPU alive during playback |
| `READ_MEDIA_AUDIO` | Only if the user picks individual files instead of a folder |

Music is read through the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), so no broad
storage permission is needed for the normal "choose a folder" flow.

## 5. Continuous integration

`.github/workflows/android.yml` runs on every push:

1. JDK 17 + `android-36` + build-tools 36.0.0 + Gradle 9.x/8.x as pinned
2. `assembleDebug`
3. `assembleRelease`
4. `testDebugUnitTest`
5. `assembleDebugAndroidTest`
6. uploads the `apk` artifact (debug + release) and the unit-test XML results

Because raw CI logs are not always reachable, the workflow re-emits compiler output as check-run
annotations: on failure it publishes a `FILECOUNTS` annotation (errors per file) and a `KOTLIN`
annotation (deduplicated messages). Read them with:

```bash
gh api repos/<owner>/Reverse/actions/runs/<run-id>/jobs --jq '.jobs[0].id'
gh api repos/<owner>/Reverse/check-runs/<job-id>/annotations --jq '.[].message'
```

## 6. Visual parity pipeline

```bash
# 1. reference screenshots from the web build (headless Chromium)
cd parity && npm install && node web/capture.mjs

# 2. native screenshots: run the instrumented harness on an emulator
cd ../android && ./gradlew connectedDebugAndroidTest   # writes PNGs to the device

# 3. pull them off the device and diff
adb pull /sdcard/Android/data/com.thegadget.app.debug/files/parity/out/native ../parity/out/native
cd ../parity && node compare.mjs --viewport 390x844
```

`compare.mjs` writes `reference.png` / `native.png` / `diff.png` / `overlay.png` per screen into
`parity/out/diff/<viewport>/` and prints a per-screen mismatch table.

The harness freezes the clock (`1790000400000`) and seeds the same localStorage-equivalent data the
web capture uses, so both sides render the same state.

## 7. Repository layout

```
android/
  app/src/main/kotlin/com/thegadget/app/
    core/       ports of src/utils + src/state (pure Kotlin, unit-tested)
    data/       DataStore, Room, SAF library scanner
    service/    Media3 playback service (authoritative queue)
    social/     MQTT homies transport
    state/      AppState (single observable model), PlayerFacade
    ui/         extracted SVG catalogue + Canvas renderer + design tokens
    shell/      Backdrop, Hub, Chrome, MiniPlayer, overlays
    screens/    one composable per web route
  app/src/androidTest/  parity screenshot harness
  app/src/test/         core parity unit tests
parity/                 reference capture + native/web diff tooling
NATIVE_PARITY_SPEC.md   measured values and the web→native mapping
FEATURE_MATRIX.md       honest per-feature status
```
