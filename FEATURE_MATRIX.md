# FEATURE_MATRIX.md — *The Gadget*, web → native Android

Honest status of every user-facing feature in the web app (`src/**`) against the native Android
port in `android/`.

Legend:

| Symbol | Meaning |
| --- | --- |
| ✅ | Ported and wired to real data/APIs; compiles in CI |
| 🟡 | Ported but incomplete, or functionally equivalent with a different presentation |
| ❌ | Not ported |
| ⬜ | Not applicable on Android (the web behaviour has no native counterpart) |

"Compiles in CI" means the GitHub Actions `Android build` workflow produced debug + release APKs
and passed unit tests. It does **not** mean pixel parity has been measured — see
`NATIVE_PARITY_SPEC.md` §"verification" and the gaps at the bottom of this file.

---

## 1. Shell and chrome

| Web | Native | Status | Notes |
| --- | --- | --- | --- |
| `App.tsx` z-order: Backdrop → Hub → PageHost → MiniPlayer → TopBar → y2k → toasts → Onboarding | `shell/Root.kt` same order | ✅ | `ErrorBoundary` equivalent sits outermost |
| `hub/Backdrop.tsx` (photo wash, gradients, particles) | `shell/Backdrop.kt` | 🟡 | Gradients + extracted `wire.Wireframe` SVG + particles ported; the `bg-photo` layer and `useParallax` are not |
| `hub/Wireframe.tsx` + `hub.css .wire` strokes | `ui/SvgRender.kt` + `ui/theme/Css.kt` | ✅ | Exact extracted path data; `vector-effect: non-scaling-stroke` honoured |
| `hub/Hub.tsx` + `Chains.tsx` + `Glyphs.tsx` + `Mascot*` | `shell/Hub.kt`, `shell/Glyphs.kt` | 🟡 | Orbs, chains and mascot drawn on Canvas from measured values; the iris/boot re-timing of node entry is partial |
| `hub/TopBar.tsx` status pill (back arrow, avatar, name, clock) | `shell/Chrome.kt` | ✅ | Clock ticks on the minute boundary like `useClock` |
| `ui/MiniPlayer.tsx` + `.mini` CSS | `shell/MiniPlayer.kt` | ✅ | 64 px pill, 46 px spinning art (22 s), 3 px progress bar inset 18/60 px |
| `ErrorBoundary.tsx` crash card | `shell/Overlays.kt` `CrashCard` + `GadgetApp` handler | ✅ | Same kicker/title/actions; writes and clears `gadget:crash` |
| Boot rings + flash (`hub.css .boot`) | `shell/Overlays.kt` `BootOverlay` | ✅ | 40 vmin rings 1.4 s `--ease`, second delayed 0.14 s, 26 vmin flash 0.9 s |
| `.toast` / `.toast--mini` | `shell/Overlays.kt` `ToastHost` | 🟡 | Rendered and driven by app state; the per-action toast variants (confirm dialogs) are not all wired |
| `html[data-y2k=on] .y2k-fx` scanlines | `shell/Overlays.kt` `Y2kOverlay` | 🟡 | Scanline + column rasters ported; the CSS uses `mix-blend-mode: overlay`, Compose draws it as a plain alpha layer |
| `Onboarding.tsx` | `screens/SystemScreens.kt` `OnboardingScreen` | 🟡 | 3-step flow gating `gadget:onboarded`; the web's per-step artwork/mascot staging is simplified |
| Immersive (`requestFullscreen`) | `MainActivity` + `WindowInsetsControllerCompat` | ✅ | On first tap when `settings.autoImmersive`, transient bars on swipe |
| Wake lock (`navigator.wakeLock`) | `FLAG_KEEP_SCREEN_ON` + Media3 `setWakeMode` | ✅ | Driven by `settings.keepAwake` |
| Service worker / offline | ⬜ | ⬜ | A native app is offline by construction; no assets are fetched at runtime |

## 2. Navigation

| Web | Native | Status | Notes |
| --- | --- | --- | --- |
| `state/nav.tsx` stack + History mirror | `core/NavStack.kt` + `BackHandler` | ✅ | Back steps exactly one level; back at the hub exits; `home()` clears |
| `routeKey()` | `Route.key()` | ✅ | Parameterised routes keyed by id |
| Iris transition opening from the tapped node (`iris-open`/`iris-close`) | `shell/Root.kt` circle clip | ✅ | 660 ms open from the tapped node's centre/radius, 500 ms close back into it; radii from `Iris.start`/`Iris.end` in the reference stage units, `--ease` easing |
| `surface-in` / `view-fwd` / `view-back` transitions | `shell/Root.kt` | 🟡 | `surface-in` (560 ms, scale 0.92→1, fade) and `view-back` (420 ms, scale 1.04→1, fade) are implemented; the forward `view-fwd` variant is not distinguished from `surface-in` |
| Escape / browser back | system back + predictive back (`enableOnBackInvokedCallback`) | ✅ | |

## 3. Music

| Web | Native | Status | Notes |
| --- | --- | --- | --- |
| File System Access folder picker | SAF `OpenDocumentTree` + persisted permission | ✅ | `data/LibraryRepository.kt`; web's drag-and-drop and file-list mode are not reproduced |
| Recursive scan, depth ≤ 6, skip dot-dirs, audio extensions | same rules | ✅ | `core/LibraryModel.kt` constants shared with the port |
| Track id = `path/name`; `titleFromFile` | same | ✅ | Covered by unit tests |
| Folder art selection (`artRank`: cover > front > album > any) | same | ✅ | |
| ID3/Vorbis tag reading (`utils/tags.ts`) | `core/TagReader.kt` | ✅ | Reads over a `content://` stream |
| Cover extraction, 512² crop-to-fill JPEG 0.86 (`utils/image.ts`) | `writeArtwork` | ✅ | Written to `filesDir/artwork` |
| IndexedDB meta cache (`utils/metaStore.ts`) | Room `gadget-meta` | ✅ | |
| `useMusicPlayer` semantics: load/step/next/prev(3 s)/ended/repeat/shuffle/apply/restore | `service/PlaybackService.kt` | ✅ | Ported behaviour-for-behaviour; single authoritative engine |
| MediaSession metadata + artwork, lock-screen controls | Media3 `MediaSessionService` | ✅ | Notification is Media3's; not a hand-built layout |
| Albums / Songs / Playlists / Search / Now playing | `screens/MusicScreens.kt` | 🟡 | All routes exist and are data-wired; the `CollectionHero` glass orb, `VirtualList` virtualisation and per-page CSS layout are not reproduced |
| Playlist create/rename/delete | ✅ | ✅ | id = `String(Date.now())` as in the web |
| Volume persistence (`gadget:volume`, default 0.8) | ✅ | ✅ | |
| Listen-time stats + top tracks (`utils/stats.ts`) | `core/LibraryModel.kt` `topTracks` | 🟡 | Logic ported and stored (`gadget:trackStats`, `gadget:listenSec`); the Account page does not yet render it |
| Waveform/analyser visualiser (pointer:fine only) | ❌ | ❌ | Web only enables it for fine pointers; no native equivalent built |
| Audio focus / becoming noisy | ✅ | ✅ | `USAGE_MEDIA` + `handleAudioBecomingNoisy` |

## 4. Games

| Web | Native | Status | Notes |
| --- | --- | --- | --- |
| Games list, favourite, launches, last played | `screens/GamesScreens.kt` | ✅ | |
| Add / edit / delete game | ✅ | ✅ | |
| 2048 (`pages/games/Game2048.tsx`) | `core/Game2048.kt` + `Game2048Screen` | 🟡 | Engine ported with identical traversal/merge/spawn rules and unit-tested; the tile pop/merge animations and board chrome are simplified |
| Best score persistence | ✅ | ✅ | `gadget:best2048` |
| External web games (`GameBrowser.tsx`, iframe) | isolated `WebView` via `Route.GameBrowser` | ✅ | Private data store, no shared cookies, no file access; all surrounding chrome is native |
| `setActiveGame` presence broadcast | ❌ | ❌ | Presence is published, but the "now playing game" field is not wired |

## 5. Homies / social

| Web | Native | Status | Notes |
| --- | --- | --- | --- |
| Homie list, add/edit/delete local homies | `screens/SocialScreens.kt` | ✅ | id = `String(Date.now())` |
| Link by code (`hp…`), validation strings | ✅ | ✅ | Same messages ("That's your own code.", "Codes look like hp7k2m9a.", "Already linked.") |
| `getMyCode()` stable per install | `Ids.hostCode()` + DataStore | ✅ | `hp` + 6 chars from the same alphabet |
| MQTT 5 transport, two public brokers | `social/SocialRepository.kt` (HiveMQ MQTT client) | 🟡 | Protocol semantics ported (`gadget/v1/p/<code>` QoS1 retained + last-will tombstone, `gadget/v1/c/<a>/<b>` QoS1 chat, 200-message buffer). **Not yet exercised at runtime** — the web uses WebSocket brokers; the native client connects over TLS TCP |
| Presence (name/tagline/status/artwork/now/listenedSec/games/top) | `Presence` model | 🟡 | Core fields published; `now`, `listenedSec`, `games`, `top` are not populated |
| Chat view (buffer, timestamps, own-message echo) | `ChatScreen` | 🟡 | Functional; the web's presence line and read-state chrome are simplified |
| Notifications when a message arrives (`utils/notify.ts`) | ❌ | ❌ | Permission is requested; no notification is posted |

## 6. Account, Config, Plugins

| Web | Native | Status | Notes |
| --- | --- | --- | --- |
| Profile (name, tagline, status, avatar, mascot) | `AccountScreen` | 🟡 | name/tagline/status editable and persisted; avatar and mascot pickers are not built |
| All 17 settings persisted and effective | `ConfigScreen` + `GadgetSettings` | 🟡 | All keys exist and persist; the Config page currently exposes the 5 scale sliders + hour24/auto-immersive/sounds/haptics/ambient/parallax/chainSway/reduceMotion/y2k/notify/keepAwake/artwork through `updateSetting`, but the page UI shows only a subset, and `sounds`/`haptics`/`notify` have no effect yet |
| Reset settings / reset all data | `resetSettings`, `resetAllData` | ✅ | |
| Cache size readout + clear cache | ⬜ | ⬜ | No HTTP cache on Android; the equivalent would be artwork/meta storage |
| Fullscreen toggle | ✅ | ✅ | Immersive toggle |
| Plugin install from JSON, schema validation | `core/PluginSchema.kt` + `PluginsScreen` | ✅ | Full validator ported (200 KB cap, id/glyph/page rules, hex colours, numeric bounds) with unit tests |
| Plugin enable/disable/delete, persisted | ✅ | ✅ | Stored verbatim as source JSON + an enabled map |
| Plugin hub nodes | ✅ | ✅ | Nodes come from the manifest and mount on the hub |
| Plugin pages rendered natively from blocks | `screens/PluginRenderer.kt` | ✅ | `header`/`text`/`note`/`link`/`button`/`tiles` — data-driven, no per-plugin code |
| Plugin theme (accent, orb ramp, scales, glow) | 🟡 | 🟡 | Scales + accent applied while the plugin page is open; the orb colour ramp is not |

## 7. Data and platform

| Web | Native | Status |
| --- | --- | --- |
| `localStorage` (`gadget:*`) | DataStore Preferences, same logical keys | ✅ |
| IndexedDB meta cache | Room | ✅ |
| FS Access handles (`gadget-handles`) | DataStore `gadget-handles` holding the persisted SAF tree URI | ✅ |
| Google Fonts (Orbitron, Exo 2) | vendored TTFs in `res/font` from the same upstream sources | ✅ |
| HTML `<audio>` | Media3 ExoPlayer | ✅ |
| WebAudio UI sounds (`utils/audio.ts`) | `core/Sfxr.kt` + `ui/Fx.kt` | ✅ |
| `navigator.vibrate` haptics | `ui/Fx.kt` | ✅ |

---

## Known gaps (the honest list)

These are the things the web app does that this port does **not** yet do. They are listed rather
than glossed over, and none of them is claimed as parity anywhere in this repository.

1. **Visual parity is unmeasured.** The reference half works: CI produces **104 web screenshots**
   (412×915 and 844×390) as the `parity-reference` artifact. The native half does not — the emulator
   job has not completed a capture — so no `diff.png`/`overlay.png` set exists and no screen has been
   verified pixel-for-pixel. Screen layouts use the token system and CSS-derived values, but
   per-component spacing and typography have not been diffed.
2. **The UI sounds are synthesised, not sampled.** `core/Sfxr.kt` ports the SFXR generator and the
   six jsfxr presets the web uses, at the web's volumes and haptic patterns. The web draws its
   randomness from `Math.random()`, so it renders a different variant of each preset on every page
   load; the native build uses a fixed seed so the six sounds are identical every launch. The
   timbres therefore match the generator, not any one particular web rendering.
3. **The forward `view-fwd` transition is not distinguished** from `surface-in`; the web applies a
   slightly different curve to pushes that do not come from the hub.
4. **No parallax and no ambient streak layer.**
5. **Custom controls are approximated.** `GlassSlider`, `Sheet`, `VirtualList` and the `Orb` icon
   set are replaced by Material 3 widgets or simplified drawing in several places, which is a
   visible divergence from the reference.
6. **Notifications** are only permission-gated; nothing is posted for messages or playback beyond
   Media3's own media notification.
7. **Presence payload is partial** (no `now`, `listenedSec`, `games`, `top`).
8. **MQTT has not been run against a live broker** in this environment.
9. **Account statistics are computed but not displayed.**
10. **`CollectionHero`, `Art` fallback disc, and the music pages' bespoke layouts** are simplified.
11. **Startup ANR on device (fixed, needs retest).** The first real-device run ANR'd entering the hub
    because the render thread parsed SVG path strings every frame. Fixed by caching + pre-warming;
    confirmed only by compilation, not yet by a fresh device run.
