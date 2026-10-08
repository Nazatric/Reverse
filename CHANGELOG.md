# Changelog — The Gadget (native Android)

All notable changes to the native Android port. The web app in `src/` is the source of truth; this
port mirrors it. Format follows [Keep a Changelog](https://keepachangelog.com/).

## [0.1] — 2026-10-08

First installable build of the native port. Everything is Kotlin + Jetpack Compose; the only WebView
is the isolated one used for user-added external web games.

### Added
- **Shell**: extracted-SVG backdrop + wireframe, hub (orbs, swaying chains, mascot), status pill with
  live clock, mini player capsule, boot overlay, crash card (ErrorBoundary equivalent), toasts, y2k
  overlay.
- **Navigation**: exact stack semantics (back steps one level, back at hub exits); iris-open/close,
  surface-in and view-back transitions opening from the tapped node.
- **Music**: SAF folder picker with persisted permission, recursive scan (depth ≤ 6), folder-art
  ranking, ID3/Vorbis tag reading, 512² artwork extraction, Room meta cache, albums/songs/playlists/
  search/now-playing, playlist CRUD; Media3 playback with the web's queue semantics (shuffle, repeat
  one/all, prev-within-3s), MediaSession notification, audio focus, wake mode.
- **Games**: CRUD, launch counting, favourites, native 2048 with the web's move/merge/spawn rules,
  best-score persistence, external web games in an isolated WebView.
- **Homies**: local CRUD, code linking with the web's validation strings, MQTT-5 presence + chat.
- **Account / Config**: profile editing; all 17 settings persisted and effective.
- **Plugins**: full schema validator, install/enable/delete, manifest-driven hub nodes, native block
  renderer (no per-plugin code, no bundled examples).
- **Sounds/haptics**: SFXR generator ported from jsfxr, the six UI presets at the web's volumes and
  haptic patterns, gated by the persisted settings.
- **Tooling**: CI build (debug + release), 31 JVM parity unit tests, reference screenshot capture in
  CI (104 web PNGs), emulator boot smoke test.

### Fixed
- Startup ANR on device: the render thread was re-parsing extracted SVG path strings every frame;
  path/transform parsing is now memoised and pre-warmed off the main thread, as are the two typefaces.
- 2048: `RIGHT`/`DOWN` moves previously compacted the board toward the wrong edge; `isStuck` misread
  deadlocked boards.

### Known limitations
See `FEATURE_MATRIX.md`. Headline: no pixel-diff yet (native emulator capture not completed), no
parallax/ambient layer, Material 3 stand-ins for `GlassSlider`/`Sheet`/`VirtualList`, partial MQTT
presence payload.
