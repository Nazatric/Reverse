# FINAL REPORT — *The Gadget*: web → native Android port

This report is deliberately blunt about what is finished, what is approximate and what is missing.
Where a claim is backed by evidence, the evidence is named.

---

## 1. Build result

**Status: green.** GitHub Actions workflow `Android build` on branch `arena/a51545a7-reverse`:

| Step | Result |
| --- | --- |
| `assembleDebug` | ✅ success |
| `assembleRelease` (R8 + resource shrinking) | ✅ success |
| `testDebugUnitTest` | ✅ success |
| `assembleDebugAndroidTest` (parity harness) | ✅ success |
| Artifact `apk` (debug + release) | ✅ uploaded, ~20.6 MB |

Toolchain actually used (pinned in `android/gradle/libs.versions.toml`):

- JDK 17, Gradle 8.14.3, AGP 8.13.0, Kotlin 2.4.20, KSP 2.3.12
- `compileSdk 36`, `minSdk 26`, `targetSdk 36`
- Compose BOM 2025.06.01, Media3 1.7.1, Room 2.7.1, DataStore 1.1.7, Coil 3.2.0, HiveMQ MQTT client 1.4.0

**Why not the newest libraries:** the current Compose (1.12.1) and Coil (3.6.3) artifacts declare
`minCompileSdk = 37`, and `platforms;android-37` is not published in the SDK repository available to
the CI image (`sdkmanager` reports `Failed to find package 'platforms;android-37'`). The build
therefore uses the newest train that compiles against the newest *available* platform. This is an
environment constraint, recorded here rather than hidden, and it is the single reason the dependency
set is not bleeding edge.

## 2. Architecture

No WebView is used as UI, no hybrid framework, no React Native/Flutter/Capacitor. The only WebView
in the codebase is `GameBrowserScreen`, which exists solely to run user-added external web games in
an isolated instance (private data store, no shared cookies, no file access) — the one exception the
brief allows. Everything around it is Compose.

| Concern | Web | Native |
| --- | --- | --- |
| State | `AppProvider` React context | `state/AppState.kt` (one `AndroidViewModel`, `StateFlow`s) |
| Routing | `state/nav.tsx` + History API | `core/NavStack.kt` + `BackHandler` |
| Persistence | `localStorage` | DataStore Preferences, same `gadget:*` logical keys |
| Meta cache | IndexedDB | Room (`gadget-meta`) |
| Folder handles | File System Access handles in IDB | DataStore `gadget-handles` with a persisted SAF tree URI |
| Audio | one `<audio>` element + `useMusicPlayer` | Media3 `MediaSessionService`, queue owned by the service |
| Artwork | Canvas resize → object URL | 512² crop-to-fill JPEG 0.86 → `filesDir/artwork` |
| Tags | `utils/tags.ts` on `File` | `core/TagReader.kt` on a `content://` stream |
| Social | `mqtt` npm over WebSocket | HiveMQ MQTT 5 client over TLS TCP |
| Graphics | inline SVG + CSS | extracted path catalogue + Canvas renderer (`ui/SvgRender.kt`) |
| Fonts | Google Fonts | vendored Orbitron/Exo 2 TTFs in `res/font` |
| Crash | `ErrorBoundary` | global handler → `gadget:crash` → `CrashCard` |

The pure logic (`core/**`) is plain Kotlin with no Android imports, which is what makes it unit
testable on the JVM.

## 3. Implemented features

Verified by compilation and, where noted, by unit test:

- **Shell**: backdrop with the extracted wireframe, hub (five orbs on swaying chains + mascot),
  status pill with a live clock, mini player capsule, boot overlay, crash card, toasts, y2k overlay,
  onboarding gate.
- **Navigation**: exact stack semantics — back steps one level, back at the hub exits, `home()`
  clears; the iris origin of the tapped node is recorded. *(unit-tested)*
- **Music**: SAF folder picking with persisted permission, recursive scan (depth ≤ 6, dot-dirs
  skipped), folder-art ranking, ID3/Vorbis tag reading, 512² artwork extraction, Room meta cache,
  albums/songs/playlists/search/now-playing, playlist CRUD with the web's id scheme.
- **Playback**: Media3 with the web's queue semantics ported behaviour-for-behaviour — shuffle pick,
  repeat one/all/off, "previous restarts the track within 3 s", end-of-queue handling, restore of
  the last track index, volume persistence, MediaSession metadata + artwork, audio focus,
  becoming-noisy, wake mode.
- **Games**: CRUD, launch counting, favourites, 2048 with the identical move/merge/spawn rules
  *(unit-tested)*, best-score persistence, external web games in an isolated WebView.
- **Homies**: local CRUD, code linking with the web's exact validation strings, stable `hp…` code,
  MQTT presence with retained last-will tombstone and peer-to-peer chat on the `gadget/v1` tree.
- **Account / Config**: profile editing; all 17 settings persisted and read back.
- **Plugins**: full schema validator ported (200 KB cap, id/glyph/page/hex/number rules)
  *(unit-tested)*, install from JSON, enable/disable/delete, manifest-driven hub nodes, and a
  native block renderer for `header`/`text`/`note`/`link`/`button`/`tiles` with **no per-plugin
  code and no built-in example**.
- **Text/metrics ports**: `formatTime`, `formatClock`, `duration`, `fmtDuration`, `ago`,
  `titleFromFile`, `jsRound`, the 736-unit stage metric system *(all unit-tested)*.

## 4. Automated tests

`android/app/src/test/kotlin/com/thegadget/app/CoreParityTest.kt` — **31 tests, 31 passing**
(`TESTTOTALS total=31 failed=0`, reported by `.github/report_tests.py`). They assert the **web**
behaviour, not the implementation, so a failure means drift from the source of truth:

- `GadgetText`: `jsRound` tie direction, `formatTime` flooring, 12 h/24 h clock, `duration`,
  `fmtDuration` thresholds, `ago` ladder, `titleFromFile`
- `Library`: folder-then-numeric sort, `artRank` preference order, `groupAlbums` folder fallback and
  numeric ordering, `letterOf` diacritic stripping
- `Game2048`: fresh board, merge-once-per-move and scoring, no-op move detection, win/stuck
  detection, best-score monotonicity
- `NavStack`: push/back/home and no-op back at the hub, direction flags, route keys
- `PluginSchema`: valid manifest → six renderable blocks; six invalid manifests rejected with
  messages; 200 KB cap; glyph/page name lists
- `GadgetMetrics`: portrait stage and `u` boost, landscape square clamp and centring, percentage
  stage coordinates
- `Ids` and the SVG catalogue digest (`9693031e14e4ab3f`) with the shell groups resolving
- `Game2048.spawn`: free-cell choice and the web's 90/10 split between 2 and 4, full-board no-op
- `Sfxr`: the six preset/volume pairs from `utils/audio.ts`, the four haptic patterns, deterministic
  bounded output for every cue, and the preset parameter ranges

**These tests have already paid for themselves.** The first run of the suite failed three assertions.
Two were my own test assertions misstating the web (`titleFromFile` replaces underscore runs whether
or not there is an extension, and `artRank` ranks `cover` and `front` in the same tier rather than
ordering them). The third was a **real defect in the port**: `Game2048.move` placed surviving tiles
at `slot` instead of at the slot-th cell *of the traversal order*, so `RIGHT` and `DOWN` moves
compacted the board toward the top-left instead of the bottom-right. That made every rightward and
downward move wrong, and it made `isStuck` report a full, deadlocked board as still playable. It is
fixed, and `each direction compacts toward its own edge` now pins all four directions.

## 5. Visual parity results

**None measured.** This is the biggest honest gap.

- The tooling exists: `parity/web/capture.mjs` (reference PNGs from headless Chromium),
  `android/app/src/androidTest/.../ParityCaptureTest.kt` (native PNGs, frozen clock
  `1790000400000`, seeded storage, 400 ms settle) and `parity/compare.mjs` (produces
  `reference.png` / `native.png` / `diff.png` / `overlay.png` plus a mismatch table).
- No capture has been run, so there is **no** `diff.png` set and no per-screen mismatch numbers to
  report. `parity/out/` is empty.
- What *is* true: the design tokens are derived from the CSS rather than eyeballed (stage metrics,
  `--u`, orb/chain/label scales, `--ease` curves, the `.mini` capsule dimensions, the `.y2k-fx`
  rasters, the boot ring timings, the glyph and wireframe stroke values), the hub/backdrop geometry
  comes from the extracted SVG catalogue, and both fonts are the upstream files.
- What is **not** true: that any screen has been compared pixel-for-pixel against the reference.

Until that diff is produced and reviewed, no parity percentage should be quoted, and none is.

## 6. Functional test results

- Unit tests: **31 passing** in CI.
- Instrumented/emulator tests: the harness **compiles** (`assembleDebugAndroidTest` succeeds with
  the Compose test rule and androidx.test runner on the classpath), but has not been executed on an
  emulator, so there are no runtime results for navigation, playback, SAF scanning, MQTT or plugin
  rendering.
- Manual runtime testing: not performed in this environment (no emulator or device available).

## 7. Remaining differences from the web app

Full list in `FEATURE_MATRIX.md` §"Known gaps". Headline items:

1. The forward `view-fwd` transition is not distinguished from `surface-in`; the iris reveal
   (660 ms open / 500 ms close), `surface-in` and `view-back` are implemented.
2. The UI sounds are generated by a port of the SFXR engine rather than sampled from a fixed
   rendering, so they match the web's generator and volumes but not any one web session's timbre.
3. No parallax layer and no ambient streaks.
4. Several bespoke controls (`GlassSlider`, `Sheet`, `VirtualList`, the icon set) are Material 3 or
   simplified drawing instead of the reference components — a visible divergence.
5. No notifications beyond Media3's media notification; message notifications are not posted.
6. Presence publishes only core fields (`now`, `listenedSec`, `games`, `top` are absent).
7. Account statistics are computed but not displayed.
8. Music pages omit `CollectionHero` and the reference's bespoke layouts.
9. MQTT has not been exercised against a live broker.
10. The web's y2k overlay uses `mix-blend-mode: overlay`; Compose draws it as a plain alpha layer.

## 8. Android-specific behaviour (intentional divergences)

| Area | Behaviour |
| --- | --- |
| Back | Steps the nav stack one level; at the hub it finishes the activity. Predictive back enabled. |
| Fullscreen | `WindowInsetsControllerCompat.hide(systemBars)` with transient bars on swipe, instead of the Fullscreen API. |
| Wake lock | `FLAG_KEEP_SCREEN_ON` plus Media3 `setWakeMode(WAKE_MODE_LOCAL)`; no `SCREEN_BRIGHT_WAKE_LOCK`. |
| File access | SAF tree picker with a persisted URI permission; the web's drag-and-drop and multi-file modes have no equivalent. |
| Background audio | A `MediaSessionService` with a foreground notification — required on Android, absent on the web. |
| Storage | App-private DataStore/Room/files; clearing app data is the equivalent of the web's "reset data". |
| Offline | Inherent; there is no service worker and no runtime asset fetching. |
| External games | An isolated `WebView` replaces the sandboxed iframe. |
| Social transport | TLS TCP to the same public brokers instead of WebSocket, because the native MQTT client transports differ. |

## 9. External dependencies

| Library | Version | Licence | Why |
| --- | --- | --- | --- |
| AndroidX Compose (BOM) | 2025.06.01 | Apache-2.0 | UI toolkit |
| AndroidX Media3 | 1.7.1 | Apache-2.0 | Playback + media session |
| AndroidX Room | 2.7.1 | Apache-2.0 | Meta cache (IndexedDB equivalent) |
| AndroidX DataStore | 1.1.7 | Apache-2.0 | Preferences (localStorage equivalent) |
| Coil 3 | 3.2.0 | Apache-2.0 | Cover-art loading |
| HiveMQ MQTT client | 1.4.0 | Apache-2.0 | MQTT 5 homies transport |
| kotlinx.coroutines / serialization | 1.11.0 | Apache-2.0 | Async + JSON |
| Orbitron, Exo 2 | vendored | SIL Open Font License 1.1 | The two families the web loads |

No abandoned or unmaintained dependencies; all are Apache-2.0 except the fonts (OFL), which is
compatible with redistribution.

## 10. What to do next

In priority order:

1. Run the emulator capture and `compare.mjs`, then fix the screens with the worst mismatch. This is
   the largest remaining unknown: nothing here has been compared pixel-for-pixel.
2. Add the missing custom controls (`GlassSlider`, `Sheet`, icon set) to close the visible gaps.
3. Execute the instrumented harness on a device to get real functional results for playback, SAF
   scanning, MQTT and plugin rendering.
4. Distinguish `view-fwd` from `surface-in`, and add the parallax and ambient streak layers.
5. Populate the remaining presence fields and display the account statistics that are already
   computed.
