# NATIVE_PARITY_SPEC.md — *The Gadget*, web → native Android

Source of truth: the web app in this repository (`src/**`, `public/**`, `index.html`).
Nothing in this document comes from a previous native attempt; every value below was read
out of the web source, or measured from the running web build (see `parity/`).

Reference build used for measurement: `npm run build` → `dist/` (single-file bundle) rendered in
headless Chromium at 390×844, 393×873, 412×915 and 844×390 with `reduceMotion` on, a frozen
clock and seeded `localStorage`.

---

## 1. Application shell

| Item | Web implementation | Native implementation |
| --- | --- | --- |
| Shell | `App.tsx` → `.scene` grid, always-mounted `Backdrop`, `Hub`, `PageHost`, `MiniPlayer`, `TopBar`, `y2k-fx`, toasts, `Onboarding` | `GadgetRoot` composable: same z-order (backdrop → boot → hub → page host → mini player → top bar → y2k overlay → toasts → onboarding) |
| Providers | `AppProvider` (state) → `NavProvider` (history) → `ErrorBoundary` outermost | `AppViewModel` (single source of truth) + `NavController` state holder + `CrashBoundary` on top |
| Immersive | first tap after onboarding calls `requestFullscreen({navigationUI:'hide'})` when `settings.autoImmersive` | first tap → `WindowInsetsControllerCompat.hide(systemBars)` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, re-applied on resume |
| Wake lock | `navigator.wakeLock.request('screen')` while `settings.keepAwake && playing`, re-acquired on `visibilitychange` | `PowerManager.WakeLock` (SCREEN_BRIGHT_WAKE_LOCK? no — `FLAG_KEEP_SCREEN_ON` equivalent via `Window.addFlags`) held from the playback service while playing (Media3 also sets `setWakeMode`) |
| Boot animation | `.boot` rings + flash, 1500 ms, only when `onboarded && !reduceMotion`; `html[data-boot=on]` re-times hub-in/node-in/chain-draw | `BootOverlay` composable: 2 rings (`40vmin`, border 1.5 px, `boot-ring` 1.4 s `--ease`, second delayed 0.14 s) + flash (`26vmin`, 0.9 s); while active the hub uses the boot entry timings |
| Notifications prompt | `.toast` dialog after first tap (not before), stored `gadget:notify-asked` | `POST_NOTIFICATIONS` runtime request + same toast UI |
| Crash handling | `ErrorBoundary` → `.crash` card: kicker `the gadget`, title `something broke`, `error.message`, buttons `reload` / `reset data`; writes `gadget:crash` | `CrashBoundary` composable at the root; same strings; "reload" restarts the activity, "reset data" clears DataStore/Room and recreates |

### 1.1 Motion preferences
`html[data-motion=off]` forces every animation to 0.001 ms and every transition to 0.001 ms —
i.e. **all motion becomes instant**, it is not merely shortened.
`data-ambient=off` removes streaks + motes (`.ambient`), `data-sway=off` disables chain sway,
`data-y2k=on` adds the scanline overlay, `data-art=off` replaces cover art with the CSS disc gradient.
Native: a `MotionSpec` provided by the app state; when motion is off, all `Animatable`/`animate*`
targets jump (duration 0) and infinite transitions are removed from the composition entirely.

---

## 2. Stage metrics (the coordinate system)

`src/utils/metrics.ts` — **the single most important port**. The reference composition is
**736 × 736 reference pixels**; `--u` = one reference pixel in real pixels.

```
portrait = h > w * 1.02
portrait:  sw = min(w, 1000);  sh = min(h, sw * (w >= 700 ? 1.3 : 2.05))
landscape: sh = min(h, w, 1000); sw = sh
fx = (w - sw) / 2;  fy = (h - sh) / 2
boost = min(w,h) < 520 ? 1.2 : min(w,h) < 800 ? 1.08 : 1
u = (sw / 736) * boost
```

`.frame` is absolutely positioned at `(fx, fy)` with size `(sw, sh)`; everything on the hub is
laid out inside that frame, so the composition never re-flows — it only scales.
Written to `:root` as `--sw/--sh/--fx/--fy/--u` on first paint and on resize (rAF-debounced).

Native equivalent: `GadgetMetrics` (pure Kotlin, unit-tested) + `StageBox` composable that
resolves the same numbers from the Compose `BoxWithConstraints` size and exposes them through a
`CompositionLocal` (`LocalStage`). No adaptive Material layout is used on the hub.

Measured on the reference viewports:

| viewport | portrait | sw | sh | fx | fy | boost | u |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 390×844 | yes | 390 | 799.5 | 0 | 22.25 | 1.2 | 0.6359 |
| 393×873 | yes | 393 | 805.65 | 0 | 33.675 | 1.2 | 0.6408 |
| 412×915 | yes | 412 | 844.6 | 0 | 35.2 | 1.2 | 0.6717 |
| 844×390 | no | 390 | 390 | 227 | 0 | 1.2 | 0.6359 |

---

## 3. Hub

Source: `components/hub/{Hub,Backdrop,Chains,Wireframe,Glyphs,TopBar}.tsx`, `Mascot(Face).tsx`,
`styles/hub.css`, `nodes.ts`.

### 3.1 Node geometry (percent of stage; `d`, `ly` in reference px)
| node | x % | y % | d | ly | glyph |
| --- | --- | --- | --- | --- | --- |
| hub (mascot) | 49.5 | 52 | 172 | — | yellow mascot |
| music | 18.5 | 22.8 | 172 | 23 | warp-lines + note |
| games | 74.6 | 31.1 | 108 | 14 | gamepad |
| config | 77.6 | 65.5 | 108 | 24 | two gears |
| homies | 27.2 | 73.8 | 106 | 26 | crowd |
| account | 53 | 79.5 | 94 | 16 | grey mascot / avatar |

Orb size on screen: `max(d * u * orbScale, 54px)` (hub: `… * hubScale, 96px`).
Labels: `top: 100% + ly*u`, centred, `translate(-50%,-50%) skewX(-12deg) scaleX(1.1)`,
`font: 900 max(12px, 18*u*labelScale)/1 Orbitron`, colour `#f6f6f3`, text-shadow
`0 1px 2px rgba(0,0,0,.95), 0 0 .55em rgba(255,255,255,.5)`. Labels are lowercase.

### 3.2 Orb body
```
background:
  radial-gradient(70% 38% at 50% 10%, rgba(255,255,255,.64), rgba(255,255,255,.14) 55%, transparent 100%),
  radial-gradient(circle at 50% 56%, #bdbdba 0, #a3a4a1 20%, #7a7b78 42%, #4a4b49 62%, #232423 80%, #0d0d0d 100%);
box-shadow:
  inset 0 0 0 1.5px rgba(240,241,237,.96),
  inset 0 0 0 5px rgba(8,8,8,.62),
  inset 0 0 0 6px rgba(255,255,255,.2),
  inset 0 3px 12px 2px rgba(255,255,255,.4),
  inset 0 -14px 22px rgba(0,0,0,.65);
```
plus `.node-body::after` (bottom gloss ellipse, 14% inset, 26% tall) and the `.node-halo` ring
(`transparent 0 74%, rgba(255,255,255,.72) 76.8%, rgba(238,240,235,.42) 81%, rgba(222,224,218,.15) 90%, transparent 100%`,
inset −15%, opacity `0.6 + 0.35*glow`, 8 s breathe, per-index −1.4 s delay) and `::before` glow
disc (inset −55%, `rgba(255,255,255,.13) → transparent 62%`, opacity = glow).

Glyph sizes inside the orb: default 62%, music **76%**, games 70%, homies 70%, config 70%,
account (mascot) 78%; glyph colour `#383937` (music `#262826`), opacity .85 (music .94),
`drop-shadow(0 1.5px 0 rgba(255,255,255,.4))`.

### 3.3 Interaction
* tap → `ui.open()` sound, `is-opening` on that orb for 700 ms (halo scale 1.35, 0.5 s), push route
  with `origin` = orb rect centre/radius when the stack was empty.
* press → `.node-body` scale .955 / 120 ms; release relaxes over 320 ms `--ease-spring`.
* centre mascot: `hub-in` entry; press scale .965; **hold ≥ 480 ms** toggles playback (with
  `hub-press` spring keyframe, 0.5 s) and swallows the following click; `is-playing` adds
  `halo-pulse` 2.8 s.
* ambient entry order: chains draw 0.85 s @0.18 s, mascot 1.1 s @0.1 s (`.hub-mascot`), nodes
  `node-in` 0.9 s with delay `180ms + i*90ms`.
* when a page is open (`away`): `.hub-zoom` → `scale(1.55)`, opacity 0 (0.62 s `--ease-io`, opacity
  0.4 s) and every animation inside the hub is paused.

### 3.4 Chains
One repeating SVG tile (64×40) per branch: flat link (`ellipse 21.5×13`, gradient a,
stroke `#0b0b0b` 2.2, inner `#060606` 13×5.4, highlight ellipse 8×2 `#fff` .62) + edge-on link
(`rect 15×30 rx 7.5`, gradient b, stroke 2, inner 5×20, highlight 2.4×13 .6) + drop shadow ellipse.
Branch geometry: from hub centre to node centre, `H = max(26, 40*u)`, `left = hx`,
`top = hy - H/2`, `width = hypot(dx,dy)`, `rotate(atan2(dy,dx))`, `background-size: auto 100%`,
mask fade `linear-gradient(90deg,#000 0,#000 60%,rgba(0,0,0,.35) 100%)`, opacity .92.
Sway: `rotate(±(0.7°,0.9°))` 9 s alternate, per-branch delay `-i*1.9s`; a light pulse (24% wide
gradient) sweeps every 9 s from −110% to 430%.

### 3.5 Wireframe
`viewBox 0 0 800 800`, `preserveAspectRatio:none`, opacity .75, mask
`radial-gradient(ellipse 62% 56% at 58% 44%, #000 18%, transparent 78%)`, drift 38 s alternate
(`translate(-6px,2px) scale(1)` → `translate(8px,-4px) scale(1.02)`).
22 bezier paths (`stroke rgba(222,226,218,.3)`, width .8, non-scaling) + 2 ellipses
(`cx 444 cy 441 rx 274 ry 158 rotate(-19)`, `cx 438 cy 441 rx 312 ry 195 rotate(-10)`,
strokes `rgba(232,238,228,.16)` / `rgba(208,217,205,.1)`). Path data is copied verbatim in
`Wireframe.kt`.

### 3.6 Backdrop (`Backdrop.tsx`)
* `.bg-photo` — `atmosphere.jpg`, inset −5%, `center/cover`, `grayscale(1) contrast(1.14) brightness(.68)`,
  opacity .5, `mix-blend-mode: screen`, drift 70 s alternate (`scale(1.02) translate(-.5%,-.3%)` → `scale(1.07) translate(.6%,.4%)`), parallax depth 6.
* `.bg-glow` — 4 radial pools (38% 31% @ .2 / 75% 30% @ .14 / 50% 68% @ .1 / 8% 54% @ .13),
  breathe 16 s alternate (opacity .82→1).
* `.bg-streaks` — two streaks: `left −25%`, `top 36% / 58%`, `width 150%`, `height 14%`,
  `linear-gradient(90deg, transparent, rgba(255,255,255,.07) 38%, rgba(255,255,255,.03) 62%, transparent)`,
  rotate −7° / 5°, sweep ∓7% 30 s / 42 s alternate.
* `.bg-vignette` — `radial-gradient(ellipse 82% 72% at 50% 46%, transparent 38%, rgba(0,0,0,.55) 76%, rgba(0,0,0,.92) 100%)`.
* 16 motes — deterministic from `rnd(n)=fract(sin(n*127.1+311.7)*43758.5453)`: `left=rnd(i+1)*100%`,
  `top=18+rnd(i+31)*78 %`, `size=1.4+rnd(i+61)*2.2 px`, `dur=18+rnd(i+91)*20 s`,
  `delay=−rnd(i+121)*36 s`, `dx=(rnd(i+151)−.5)*70 px`; white 75%, `0 0 6px rgba(255,255,255,.5)`,
  rise −130 px, opacity 0→.55→.35→0, parallax depth 9.

### 3.7 Parallax
`[data-depth]` elements are translated by `depth * pointer` where pointer is normalised to
±1 from the viewport centre, eased with `cx += (tx-cx)*0.09` per rAF, and the loop stops when it
settles. Touch pointer events are ignored (that path is for the gyro): `deviceorientation`
gamma/beta relative to the value at focus, divided by 22, clamped ±1. Parallax is disabled while a
page is open and when `parallax` or `reduceMotion` is off.
Native: pointer moves (mouse/trackpad), drag deltas and the rotation-vector sensor are the three
sources; same 0.09 easing, same depths (photo 6, motes 9, wireframe 10, chains 13, mascot 17).

### 3.8 Glyph geometry
Copied path-for-path into `Glyphs.kt` / `Icons.kt`:
music (4 arcs + stem + head + two quavers), games (gamepad body + D-pad + 2 buttons),
homies (3 figures), config (two generated gears: `gear(56,82,40,32,9,15)` + `gear(106,38,24,19,8,8)`
with the same 0.31/0.17 tooth construction and even-odd hole), play, note, star, plus the 32
24×24 UI icons (stroke 1.9, round caps/joins).

### 3.9 Mascot
`MascotFace.tsx` — a 200×200 SVG reproduced exactly in `Mascot.kt` (DrawScope):
bezel `r99 #ecede9` / `r96 #0b0b0b` / `r93` gradient `#9a9b98→#2b2c2b 40%→#575856` / `r88.5 #050505`;
face `r85` radial (`44% 26%`, r84: `#fffdb4 → #eef43d .24 → #c2cf29 .58 → #7f8c16 .86 → #434d0b 1`);
highlight ellipses (100,190/74×28 .1), (92,46/62×34 .26), (68,34/27×9 .42 rotated −18°);
brows `M34 82C46 56 76 56 100 94L96 100C76 74 54 74 40 90Z` mirrored, ink `#14150e`; eye irises
`ellipse 5.5×10` at (66,102)/(134,102) rotated ±12°; flare star + `r24` radial at (100,90)
(`flare` 4.4 s alternate, opacity .78→1, scale .94→1.06); mouth `M28 116Q100 140 172 116L168 136Q158 176 100 181Q42 176 32 136Z`
(`#0a0a07`) with the tooth plate path + 7 tooth strokes (x 53…147, `#15160f` 3.2) clipped to the
plate, plus rim strokes. `blink` 6.4 s: scaleY .12 for 3 % of the cycle. `mono` variant swaps the
face gradient for greys and ink `#161716`. `bare` drops the bezel (status pill avatar).

### 3.10 Chrome (`TopBar.tsx`)
* `.up` (back) — triangle SVG 72×58 at `left: safe+max(44u,22px)`, `top: safe+max(20u,12px)`,
  size `max(72u,50px) × max(58u,41px)`, drop-shadow `0 0 3px rgba(255,255,255,.9), 0 0 12px rgba(235,238,230,.55)`,
  `drop-in` 0.8 s @0.1 s. Tap: `ui.close()`; if the stack is non-empty → pop, else `bump` keyframe
  0.46 s (translateY −5 px scale 1.06 at 40 %). Press: translateY 1 px scale .94 / 100 ms.
* `.pill` (status) — `right: safe+max(14u,12px)`, `top: safe+max(28u,14px)`,
  `width max(286u,210px)`, `height max(54u,44px)`, gaps `max(10u,7px)`, padding
  `0 max(20u,14px) 0 max(7u,4px)`, glass gradient (r8a→#5a5a58 30 %→#333332 62 %→#1d1d1c),
  6 inset/outer shadows incl. `0 0 10px 2px rgba(255,255,255,.48)`, `drop-in` 0.8 s @0.18 s.
  Contents: avatar (76 % height, circle, 1.5 px white ring) → status dot → name
  (`500 max(11px,12u)`, ellipsis, italic+dim when empty, placeholder `set your name`) → clock
  (`300 max(13px,18u)`, tabular, `0 0 8px rgba(255,255,255,.5)`).
  Clock format `h:MM(AM|PM)` or `HH:MM`; re-computed at the next minute boundary and on resume.
  Tap → push `account` (unless already there) with `ui.tap()`.

### 3.11 Boot overlay timings (with `boot`)
rings: `boot-ring` 1.4 s `--ease` both, `0%: opacity 0 scale(.05)`, `18%: opacity 1`, `100%: opacity 0 scale(6.5)`;
second ring identical, +0.14 s. flash: 0.9 s, `26vmin`, `rgba(255,255,255,.5) → transparent 68%`,
`0%: opacity 0 scale(.4)`, `22%: opacity .9`, `100%: opacity 0 scale(2.6)`.
Hub entries while booting: mascot `hub-in` 0.9 s @0.22 s, nodes `node-in` 0.8 s @(300 ms + i*80 ms),
chains `chain-draw` 0.85 s @0.18 s (`rotate(a) scaleX(.02)→scaleX(1)`, opacity 0→.92).

---

## 4. Navigation

See `ROUTE_SPEC.md` for the full route table, params, push origins and back rules.
Key facts:
* a real stack mirrored into the History API — **back pops exactly one level**;
* `Escape` (hardware back on Android) → `history.back()` unless focus is in an input;
* the push origin is captured only when the stack was empty (the hub node → radial origin);
* `PageHost` keeps rendering the old route for **480 ms** after it becomes `null` so the iris can
  close;
* iris geometry: 600 px circle at `--ox/--oy`, `--iris-start = max(.06, origin.r/300)`,
  `--iris-end = (far/300)*1.06` with `far = hypot(max(origin.x, vw-origin.x), max(origin.y, vh-origin.y))`;
* timings: iris-open .66 s `--ease`, iris-close .5 s `--ease-io`, surface-in .56 s @0.1 s,
  surface-out .24 s, view-fwd/back .42 s (`from {scale 1.045}` / `from {scale .955}`).

Android hardware/gesture back is routed into the same `NavController.pop()`; when the stack is
empty the system back leaves the app (matching browser back on the hub, which does nothing because
the initial entry replaces state).

---

## 5. Music

### 5.1 Library access
Web: File System Access `showDirectoryPicker` (Chromium desktop, remembered in IndexedDB
`gadget-handles/handles/music-folder`) **or** `<input webkitdirectory>` / multi-file input
(phones) → `tracksFromFileList`.
Walk rules: depth ≤ 6, skip dot-folders, audio `mp3 m4a aac flac wav ogg oga opus weba webm aif aiff`
(or `audio/*` MIME), art files `^(cover|front|folder|album|art|artwork|thumbnail|albumart)….jpe?g|png|webp$`,
art rank `cover|front` 0 → `folder|album` 1 → other 2; title from filename (`_`→space, extension
dropped); sort by `folder` then numeric title; ids/webkitRelativePath keep the folder path.

Android: Storage Access Framework `ACTION_OPEN_DOCUMENT_TREE` with
`FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_PERSISTABLE_URI_PERMISSION` +
`takePersistableUriPermission` (this is the native equivalent of the persisted FileSystemHandle).
Optional secondary path: `MediaStore.Audio` bulk import for users who prefer "all music on device".
The walker, filters, ranking, sorting and id scheme are ported verbatim so album grouping matches.

### 5.2 Metadata
`tags.ts` is ported 1:1 (`TagReader.kt`): ID3v2.3/2.4 (`TIT2/TPE1/TALB/APIC`, extended header,
sync-safe sizes, UTF-16 LE/BE), ID3v1.1 (last 128 bytes), FLAC (`VORBIS_COMMENT` + `PICTURE`),
MP4/M4A (`moov/udta/meta/ilst` → `©nam/©ART/©alb/covr`). Covers are decoded lazily (2 at a time
in the web app), folder art seeds the track, embedded tag art overrides it.
Native: same parsing on `ContentResolver.openInputStream`, with ExoPlayer's metadata as a
*fallback only* — the ported reader remains authoritative so titles/artists match the web app.
Cover images are cached through Coil with `memoryCacheKey = trackId`, plus a bounded on-disk cache.

### 5.3 Playback semantics (`useMusicPlayer.ts`)
* one `HTMLAudioElement` → native: one `ExoPlayer` owned by `GadgetPlaybackService`
  (MediaSessionService) with **one** authoritative `PlayerState` in a repository; the UI never owns
  player state.
* `playQueue(list, start)`: replaces the queue, plays `start`.
* `toggle`: no source yet → `playQueue(library, 0)`; paused → play; playing → pause.
* `prev`: `currentTime > 3` → seek 0, else previous track.
* `next`: shuffle → random index ≠ current (only when queue > 1); else index+1, and from the end:
  if it was an `ended` event and repeat ≠ all → pause and keep the last track, else wrap to 0.
* `ended`: repeat `one` → seek 0 + play; else `step(1, fromEnded = true)`.
* repeat cycles `off → all → one → off`; shuffle is independent.
* volume 0..1 persisted (`gadget:volume`, default .8); `playQueue`/`toggle` never change it.
* restored on launch: last played track (`gadget:last`) becomes current (paused) when the folder
  restores; `library` empty → status `empty`.
* errors: an audio element error while a src is set → `This file couldn't be played on this device.`
* `status`: `loading` while walking, `needs-permission` when the folder handle lost permission,
  `empty`, `ready`. `error` is cleared on every new folder action.
* `AnalyserNode` only on `(pointer: fine)`; the phone keeps the native audio path (Android: the
  ring uses `Visualizer` if permission/battery allows — the ring falls back to progress-only ticks,
  which is what phones already do on the web).

### 5.4 Media session / notification
`MediaMetadata(title = tag.title || track.title, artist = tag.artist || folder basename,
album = tag.album || folder basename, artwork = cover 512²)`; actions play/pause/next/prev/seekto.
Android: Media3 `MediaSession` with the same metadata + `MediaStyle` notification
(`gadget-media` channel), `setShowPlayButtonIfSuppressed`, seek-to support, and `next/prev` from
headsets and the lock screen. `settings.notify` mirrors `notifications` — when off, the
notification is suppressed but the session stays alive.

### 5.5 Listening stats
Counted only while audio actually advances (`0 < Δ < 1.5 s` and not paused), flushed every ≥15 s
into `gadget:listenSec` and `gadget:trackStats[id]`. `topTracks`: entries ≥30 s, top 3.
`fmtDuration`: `<60 → "{s}s"`, `<3600 → "{m}m"`, else `"{h}h {m}m"` (or `"{h}h"` when m = 0).
Native: identical counters driven by the ExoPlayer progress stream, persisted in DataStore.

### 5.6 Music screens
| screen | contents |
| --- | --- |
| `music` (empty) | `music · nothing here yet` + `Empty(folder, "choose your music", …)` + `FolderPicker` |
| `music` | hero orb (album art, `min(62vw,260px,34dvh)`, halo, 4 px/5.5 px rings, `:active scale .965`), title/sub, prev/play/next orbs (52/78/52), `quad` grid of 4 orbs (76 px) labelled albums/songs/playlists/search (+count badge on playlists), library surface with folder name + compact picker, `reconnect` ghost button when status ≠ ready |
| `albums` | `Empty(disc,"no albums",…)` or `agrid` (2 → 3 @560 → 4 @860 cols, gap 28/14) of `acard`s: 150 px art orb + halo + `Art`/disc fallback, name (2-line clamp, skew −8°), `artist · N songs` |
| `album` | `CollectionHero` (chero-orb `min(58vw,230px,30dvh)`, title skew −10°, `N songs`, play 64 + shuffle 52) → `tlist` of `TrackRow`s |
| `songs` | virtual list (`VirtualList`, overscan 8, rows 66, letter heads 38, padBottom 130) sorted by title (numeric-aware), letter headers + `.rail` A–Z rail with 66 px bubble, per-row `playlist-add` orb → `AddToPlaylistSheet` |
| `playlists` | new-playlist inline form (name) + `plist` rows (54 px art, name, `N songs`); empty state |
| `playlist` | hero + `N songs` + play/shuffle orbs + track list |
| `search` | `Field(find, type=search, autofocus on pointer:fine)`; empty-query state, no-match state, `albums` row (12 max, `acard--sm` 112 px) then `songs` (150 max) |
| `now` | bare page: kicker `now playing`, `np-stage` (`min(88vw,46dvh,440px)`) with canvas ring (72 ticks, r0 = 34.5 %, max len 12 %, playhead tick +1.4 %, lit `rgba(255,255,255,.95)` / dim `.22`, lineWidth `max(2, size*.0075)`), halo 73 %, art 62 % with 3 px/4.5 px rings and `breathe` 4.5 s when playing; title/artist·album; scrubber (rail 8 px, thumb 22 px, times tabular 12 px); shuffle 46 / prev 58 / play 86 / next 58 / repeat 46; volume slider + `playlist-add` |
| mini player | hidden on `now` and on landscape ≤520 px height; `min(100vw-28px,480px)` × 64 px capsule, art 46 px (spins 22 s while playing), title/sub, play/pause orb 44, hairline progress 3 px `left 18 right 60 bottom 5`, `rise` .55 s |

---

## 6. Games

* `BUILTIN_2048` = `{id:"built-in-2048", name:"Y2K 2048", category:"Built-in", favorite:true}`.
* `Cover`: image → builtin plate (`2048` + `built-in`) → initial letter; 4:5 aspect, `rs 24`,
  5 ring/shadow layers, `cover-gloss` overlay (white .32 → transparent 36 %, plus bottom radial).
* Library: sub `N in your library` (N = games + 1), add orb 50 hot, `continue` feature card
  (62 px cover, kicker `continue`, name, `played {ago}` or `shipped with the app`, play orb 56) when
  `active === "all"`, chips `all` / `favorites` (only if any) / categories, `ggrid` (2 → 3 @560 → 4 @860
  cols) with builtin tile first (`built-in · offline`), star badge on favourites, sub `web · plays in app`.
* `open`: builtin → `g2048`; `http(s)` → in-app browser; else → detail.
* Detail: cover `min(54vw,230px)` + halo, name (skew −10°), tags (category, host with link icon,
  `favorite`), stats `launched N×` / `last played {ago}`, 100 px launch orb with `ring-pulse` 2.6 s
  when web, hint `plays fullscreen inside the app` or `opens the link` or `add a link to make it launchable`,
  actions favourite/edit/remove, remove sheet (`remove “{name}”?`).
* Form: cover picker (360×450 crop), `name` (48), `category` (24) + preset chips
  `PC, Console, Mobile, Web` (unioned with existing categories), `game link`, note
  `Web games play inside the app, no browser tab. Some sites block embedding — the app will tell you and offer a tab.`,
  cancel/save; new ids are `String(Date.now())`.
* Browser: full-screen overlay, bar with close/name/host/open-in-browser, `connecting to {host}…`
  spinner (44 px, `spin` .9 s), 6 s blank → note `If it stays blank, this site blocks embedding.` +
  `open in a new tab`, iframe `allow="autoplay; fullscreen; gamepad; accelerometer; gyroscope; clipboard-write"`,
  `referrerPolicy="no-referrer"`, registers the active game for presence, exits fullscreen on close.
  Native: WebView with media playback, `MediaPlaybackRequiresUserGesture(false)`, JS + DOM storage
  enabled, `allowFileAccess=false`, custom WebViewClient for the same error/blank detection.
* 2048 (`Game2048.tsx`): 4×4, spawn 2 (90 %) / 4 (10 %), tile ids increment, merge-into-new-tile
  with `pop`, `gained` added to score and to best (persisted `gadget:best2048`), 120 ms between the
  slide and the spawn, stuck = no direction moves (checked with 4 trial moves), keys
  arrows/WASD, swipe ≥28 px, `TONE` table 2…2048 (see tokens), `is-hot` ≥256, win at ≥2048,
  overlay `2048 · you win` / `no moves left` + play-again orb, foot orb + `merge the tiles to 2048`,
  board `min(88vw,420px,58dvh)`, cells at 8 % inset 21 % each, tiles 86 % face, radius 22 %,
  `tile-pop` 200 ms spring, board touch-action none, `ui.tap()` per successful move,
  `ui.confirm()` on restart, and `setActiveGame("Y2K 2048")` while mounted.

---

## 7. Homies / chat (MQTT)

Ported verbatim in `SocialClient.kt` (HiveMQ MQTT 5 client, WebSocket **wss** transport):

* brokers: `wss://broker.hivemq.com:8884/mqtt` → failover `wss://broker.emqx.io:8084/mqtt`;
  after **>4 errors** switch broker, drop the client, and retry after 6 s.
* root `gadget/v1`; presence topic `gadget/v1/p/<code>` **QoS1 retained**; chat
  `gadget/v1/c/<a>/<b>` with a/b sorted, QoS1, not retained.
* host code: `hp` + 6 chars from `abcdefghjkmnpqrstuvwxyz23456789`, persisted `gadget:hostcode`;
  `normalizeCode` = trim, lowercase, strip whitespace; `isValidCode = /^[a-z0-9]{3,16}$/`.
* client id `hpw<10 random>`; keepalive 30; clean session; reconnect 4000 ms; connect timeout 9000 ms;
  **last will** on the presence topic: `{"v":1,"t":<now>,"up":false}` retained.
* presence payload: `{v,t,up,name,tagline,status,avatar,now{kind:music|game,title?,artist?,name?},listenedSec,games,top[{title,artist,sec}]}`.
* publish every 25 s and debounced 350 ms on state changes (200 ms for `setActiveGame`).
* connection states `idle | connecting | online | offline` drive the UI strings
  `relay connected` / `connecting…` / `standby` / `relay offline`.
* chat messages `{id, from, name, text (≤500), t}`; per-peer buffer 200 messages; a message is
  echoed locally when sent; `follow`/subscribe list = linked homie codes.
* presence is consumed by the UI through `usePresence(code)`; linked homies adopt the remote name
  the first time presence arrives; a tombstone (`up === false`) renders `offline` + `last seen …`.
* Android specifics: a foreground-capable `Service`/`CoroutineScope` keeps the client alive while
  the app is visible; `ACCESS_NETWORK_STATE` + connectivity callbacks trigger `ensureConnection`;
  the identical wire format means a web and an Android user interoperate.

---

## 8. Plugins

`plugins/schema.ts` → `PluginSchema.kt` (validated 1:1, including every error string), with
differential tests against the TypeScript implementation.

* document `{id,name,version?,author?,description?,theme?,nodes?,pages?}`; `id`
  `/^[a-z0-9][a-z0-9._-]{1,59}$/i`; source ≤ 200 000 chars; version default `1.0.0`,
  author default `unknown`.
* nodes: `x,y ∈ [−10,110]`, `d ∈ [56,260]` default 96, `ly ∈ [4,60]` default 14, `label ≤ 24`,
  `icon ∈ {music,games,homies,config,account,play,note,star}`, `page` = built-in
  `{music,albums,songs,playlists,search,now,games,2048,homies,account,settings}` or a page id
  declared by the same plugin, ids unique and not clashing with built-ins, max 12 nodes.
* pages: `label ≤ 40`, `title` default label (≤60), `subtitle ≤ 120`, ≤ 40 blocks:
  `text|note value ≤ 600`, `header value ≤ 80`, `link {label ≤ 60, url http(s)://} `,
  `button {label ≤ 60, page xor url}`, `tiles {1..12 items, label ≤ 40, icon optional ∈ GLYPHS,
  page or url}`.
* theme: `accent` hex, `orb` 4 hex, `orbScale/hubScale ∈ [.7,1.5]`, `chainScale ∈ [.5,2]`,
  `labelScale ∈ [.7,1.7]`, `glow ∈ [.3,1.8]`; at least one of nodes/pages/theme must be present.
* store: installed records (`id,name,version,author,description,source,doc,enabled,installed`),
  re-install keeps `enabled` + `installed`, theme merged with `Object.assign` in install order
  (later plugin wins per property), only enabled plugins contribute.
* UI: header sub `N active · M installed`; actions import .json (≤200 KB) / paste json;
  URL form with `fetching…`; `installed` list (toggle row: name, `v{version} · {author}`, `on|off`
  pill, description, export/edit/remove) or the "No plugins yet…" note; `examples` list with
  `add|on|off`; editor sheet (textarea, monospace, `template` + `apply`).
* plugin pages: blocks → header/title/text/note/link/button/tiles exactly as
  `PageHost.PluginPageView` renders; unknown plugin id → `plugin page unavailable` empty state.
* theme application: accent colour, 4 orb stops, and the 4 scales + glow override the user's
  settings when the plugin defines them (`theme.X ?? settings.X`).

Guardrails for the native port: plugins remain **data only** — no scripting, no file access, no
native code; URLs are validated (`http(s)`) before opening externally; the rendered page id is
validated before navigation.

---

## 9. Storage map

| web | native |
| --- | --- |
| `localStorage` `gadget:profile` | DataStore `profile` (JSON) |
| `gadget:settings` (15 keys) | DataStore typed preferences |
| `gadget:games` | Room `games` |
| `gadget:homies` | Room `homies` |
| `gadget:playlists` | Room `playlists` + `playlist_tracks` (ordered) |
| `gadget:plugins` | Room `plugins` (source + doc JSON) |
| `gadget:volume`, `gadget:last`, `gadget:listenSec`, `gadget:trackStats`, `gadget:best2048`, `gadget:hostcode`, `gadget:onboarded`, `gadget:notify-asked` | DataStore |
| `gadget:crash` | `crash.log` in `filesDir` + `CrashBoundary` state |
| IndexedDB `gadget-handles/handles/music-folder` | persisted SAF tree URI (`content://…`) in DataStore |
| IndexedDB track metadata cache | Room `track_meta` (title/artist/album/coverUri/duration) |
| service-worker cache `gadget-v1` | Coil disk cache + bundled assets (nothing else is cacheable) |
| legacy key prefix `gadget-legacy:` | swept by "reset data" exactly like the web app |

`resetAll` (Config → reset everything) clears: library (folder link + metadata cache), profile,
settings, games, homies, playlists, listen stats, plugins — matching
`AppProvider.resetAll` + `clearLibrary`.
`clearCache` deletes the Coil disk cache and re-warms the shell.

---

## 10. Permissions & platform behaviour

| capability | web | Android |
| --- | --- | --- |
| music folder | File System Access / file inputs | `ACTION_OPEN_DOCUMENT_TREE`, persistable permission (no `READ_MEDIA_AUDIO` needed; optional MediaStore path requires `READ_MEDIA_AUDIO` on API 33+) |
| notifications | Notification API + SW | `POST_NOTIFICATIONS` (API 33+), `MediaSessionService` foreground service type `mediaPlayback` |
| background playback | page must stay open | foreground service + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| wake lock | Screen Wake Lock API | keep-screen-on while playing |
| fullscreen | Fullscreen API | `WindowInsetsController` immersive + edge-to-edge |
| haptics | `navigator.vibrate` | `HapticFeedbackConstants`/`Vibrator` for the same 5 events |
| share code | `navigator.share` / clipboard | `ACTION_SEND` chooser / `ClipboardManager` |
| open links | `window.open` | `ACTION_VIEW` (validated `http(s)`/scheme), or the in-app WebView for games |
| install prompt | `beforeinstallprompt` | not applicable — the Config row is replaced by a static note (documented) |

---

## 11. States each screen must handle

For every screen: initial (cold), loading, empty, populated, error, disabled, selected, pressed,
scrolling/long-press, keyboard-visible, permission-denied, offline, and returning from background.
Concrete examples implemented natively:
* `music`: `loading` (`looking for your music…`), `needs-permission` (`reconnect “<folder>”`),
  empty, ready, audio error notice, folder error notice (`That folder couldn't be opened.`).
* `songs`/`albums`: empty states (`Choose a music folder first.`), 5 000+ tracks via lazy list.
* `games`: empty shelf, missing cover, invalid URL (`add a link to make it launchable`),
  in-app browser blocked-embedding note, 2048 over/win.
* `homies`/`chat`: no homies, invalid/own/already-linked codes, relay offline (input disabled,
  `reconnecting…`), malformed MQTT frames ignored.
* `plugins`: invalid JSON/validation errors surfaced verbatim, disabled plugin pages,
  missing plugin page.
* `config`: cache size unknown → `unavailable`; notify denied/needs-install strings.
* global: crash panel, offline (nothing to fetch except the relay), permission denial for
  notifications, process death (all state rehydrates from DataStore/Room).

---

## 12. Documented, unavoidable platform differences

1. **Authored font metrics.** Web uses Orbitron/Exo 2 from Google Fonts; the native app bundles the
   same font files (SIL OFL, `parity/web/fonts/*`) so metrics match, but Android's hinting/AA
   differs by a fraction of a pixel.
2. **PWA install prompt** has no Android equivalent (row replaced by the same explanatory note).
3. **Service-worker-shell offline** is meaningless natively: the app is always offline-capable
   because its assets and data are local. The Config "offline cache" row reports the Coil disk
   cache instead of Cache Storage.
4. **`(pointer: fine)` spectrum analyser** — desktop-only on the web; on Android the ring uses
   progress-only ticks unless `Visualizer` is available, matching the phone behaviour of the web app.
5. **Browser fullscreen vs. Android immersive**: Android keeps the system gesture inset available
   as a transient swipe overlay (SWIPE behaviour), which the browser hides entirely.
6. **CSS `mix-blend-mode: screen`** for the atmosphere photo needs a custom `BlendMode.Screen`
   layer (implemented); GPU blending order can differ by ≤1 LSB per channel.
7. **Cold-start frame**: the web app renders the boot rings before the hub settles; Android shows
   the system splash for the same duration and then the identical ring animation.
