# ADR 0005 — Unified audio player across Home and previews

- **Status:** Accepted (tap-again-to-stop semantic superseded by [ADR 0007](0007-sound-playback-pause-resume.md); single-engine premise amended by [ADR 0022](0022-hybrid-playback-engines-media3.md) — the controller now fronts two engines)
- **Date:** 2026-05-09
- **Supersedes:** —

## Context

Until this ADR, audio playback in the app lived in two parallel implementations:

1. **`PlayerController` (singleton, listener-based)** — owns one `MediaPlayer` instance, plays
   `Sound`-bound audio, reports start/stop/progress via `PlayerControllerListener` to
   `SoundsViewModel`. Drives the Home/Explore/Favorites lists.
2. **A second `MediaPlayer` instantiated locally inside the `AudioPreview` Composable in
   `AddButtonScreen`** — Edit-mode pre-existing, Create-mode added in `feature/addbutton-create-preview`.
   Plays a local `Uri` (file or `content://`), independent of `PlayerController`. Did not advance the
   slider during playback (no progress polling) and could play **concurrently** with whatever the Home
   list was playing.

The duplication was structural debt: any change to playback semantics (e.g., audio focus, gain
normalization, waveform visualization) had to be applied in two places. The Edit-mode preview also
quietly suffered the same broken slider, only invisible because most users don't watch the thumb in
the rename flow.

## Decision drivers

1. **Single source of truth for "what is playing."** The user should never hear two audio sources
   at once from the same app. Today nothing enforces that.
2. **Consistent slider/play behavior across surfaces.** A play/pause + seek bar should feel the
   same whether the user is on Home or in the preview card.
3. **Future preview surfaces inherit the fix.** Settings test sound, AI voice prompt, sample audio
   in Explore, etc. — anything that's not a `Sound`-bound list item benefits from a Uri-aware
   playback path that already handles concurrency, lifecycle, and progress.
4. **Minimize blast radius right now.** `SoundsViewModel` implements `PlayerControllerListener` and
   has 22 direct test calls to `viewModel.onPlayer*`. A clean break (refactor everyone to a
   `StateFlow`-based API in one PR) would multiply the test surface and risk regressing Home, the
   most critical UX. We accept living with two delivery channels (listener for Home, StateFlow for
   previews) for now in exchange for shipping the unification of the **engine** without churn in
   the consumers that already work.

## Decision

**`PlayerController` becomes the single audio engine for the entire app.** Its public contract gains
three things, in addition to the existing `Sound`-bound API:

1. **`val playbackState: StateFlow<PlaybackState?>`** — emits a non-null value while a
   `Uri`-bound (preview/Stream) playback is in progress, with `(uri, positionMs, durationMs,
   isPlaying)`. Null when no Stream playback is active. Today this StateFlow is only emitted to
   during Stream playback; Home keeps using the listener path. Future PR can migrate Home consumers
   to read this same StateFlow and the listener interface can be retired.

2. **`fun startPlayingUri(context: Context, uri: Uri)`** — starts playback of an arbitrary
   `Uri`. If a `Sound` is currently playing in Home, it is stopped first (the existing listener
   fires `onPlayerStop(currentSound, completed = false)` so Home updates its UI). The new playback
   is reported via `playbackState` only — no listener events fire (the listener interface is
   `Sound`-bound and there's no `Sound` to pass).

3. **`fun pause()` and `fun resume()`** — needed by the preview card's pause/resume UX. The Home
   list does not use them today (its UX is "tap again to stop"); the API is available for future
   surfaces that want pause/resume semantics.

**`AudioPreview` (in `AddButtonScreen.kt`) drops its local `MediaPlayer`** and routes through
`PlayerControllerFactory.instance` for both playback and progress. Duration extraction stays
local via `MediaMetadataRetriever` — same pattern already in `AddButtonFeature.kt:80-87`.

## Threading model

`PlayerControllerImpl` takes two coroutine dependencies in its constructor:
`ioDispatcher` (default `Dispatchers.IO`) and `scope` (default
`CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())`). `startPlayingSound` and
`startPlayingUri` launch on `scope` and use `withContext(ioDispatcher)` to perform the only
blocking parts of the player lifecycle — `MediaPlayer.setDataSource(...)` and
`MediaPlayer.prepare()` — off the main thread. Everything else (`reset`, `start`, `pause`,
`seekTo`, `setOnCompletionListener`, `currentPosition` polling) runs on main because it is
non-blocking and listener callbacks are dispatched on the main looper.

Why this matters: `setDataSource(context, contentUri)` issues a synchronous binder call to the
provider that owns the URI; Android propagates the caller's `StrictMode.ThreadPolicy` over that
binder, so a `DiskReadViolation` in (e.g.) WhatsApp's `MediaProvider` surfaces in our
debug-only death-penalty path even though the I/O physically happens in the provider's process.
Wrapping the call with `StrictMode.allowThreadDiskReads()` would be a bypass — the prepare path
runs in response to a user tap, has no startup-cold-path constraint, and we own the call-site,
so per CLAUDE.md § *StrictMode debug audit* the right fix is option 1 ("top app-code frame is
ours: fix the production code") rather than option 2 ("scopable to a known-OK call-site").

Concurrency: each new `start*()` call cancels the in-flight prepare via `prepareJob?.cancel()`
and immediately calls `mediaPlayer.reset()`, which interrupts whatever the previous IO block
was doing. The cancelled coroutine, when its `withContext(ioDispatcher)` returns, checks
`isActive` and bails before mutating state — so it cannot fire `Tracker.track` or listener
events for work that was superseded.

## Concurrency policy (invariant)

At any time, at most one of the following is true:
- `PlayerController` is playing a `Sound` (Home/Explore list playback). Reflected in `_playingSound`
  and `_playbackProgress` of `SoundsViewModel`.
- `PlayerController` is playing a `Uri` (preview). Reflected in `playbackState`.
- Nothing is playing.

Starting either kind of playback while the other is in progress **stops the previous one**:
- New `Sound` start → previous `Sound` stop fires its listener; previous `Stream` clears
  `_playbackState` (no listener event, no consumer needs it).
- New `Stream` start → previous `Sound` stop fires its listener; previous `Stream` updates
  `_playbackState` to the new value.

This invariant is enforced inside `PlayerControllerImpl.startPlayingSound` /
`startPlayingUri`. Tests guarantee it (see `PlayerControllerImplTest`).

## Options considered (and rejected)

- **(A) Migrate everyone to a single `StateFlow<PlaybackState?>` in one PR.** Cleaner end state but
  forces 22+ test changes in `SoundsViewModelTest`/`SoundsViewModelAnalyticsTest`, plus the Sticker
  Cero auto-destruct logic depends on `onPlayerStop(sound, completed=true)` being invoked with the
  exact `Sound` that was playing — preserving that signal through a generic StateFlow needs careful
  modeling (a `PlayerEvent.Completed(source)` channel? a flag on `PlaybackState`?). Worth doing,
  but in a dedicated PR with its own ADR companion. Not now.

- **(B) Keep two `MediaPlayer` instances, share only the slider polling pattern via a Composable
  helper (`rememberAudioPlayerState(uri)`).** Cheapest fix but doesn't satisfy the "one player"
  goal — concurrency invariant remains unenforceable, future audio-focus / gain / waveform changes
  still have two homes. Rejected.

- **(C) Make `AudioPreview` register a transient `PlayerControllerListener` for the duration of the
  preview, swapping out the Home one.** Brittle (listener-swap dance, easy to leak). Rejected in
  favor of the parallel StateFlow.

## Invariants

These are grep-able and enforced by `scripts/check-adr-invariants.sh` (CircleCI job
`adr-invariants`):

- The string `MediaPlayer()` (constructor invocation) must NOT appear in
  `app/src/main/java/.../feature/addbutton/`. The only `MediaPlayer()` constructor in `app/src/main`
  must live inside `feature/playback/PlayerControllerImpl.kt`. New audio surfaces must route through
  `PlayerController`, not allocate their own player.

## Consequences

**Positive:**
- The user can never hear two audio sources from the app at once.
- The slider behaves identically in Home and in the preview card (both poll at 100ms via the same
  `Handler` in `PlayerControllerImpl`).
- `SoundsViewModel` and its tests are untouched. Home's playback path is preserved bit-for-bit.
- Adding a future audio surface (settings test sound, sample preview in Explore, etc.) is now a
  call to `PlayerController.startPlayingUri(...)` plus a `playbackState` collector — no new
  `MediaPlayer` boilerplate.

**Negative:**
- First-tap latency on the preview card increases by ~100-200ms (`MediaPlayer.prepare()` happens on
  tap rather than on Composable mount). Acceptable: matches Home's behavior, and prevents an idle
  `MediaPlayer` instance from being allocated on every preview view.
- Two delivery channels for playback events coexist temporarily (listener for Sound, StateFlow for
  Stream). Documented as intentional bridging; tracked for future unification.

## Revisit criteria

Open a follow-up ADR (and a refactor PR) when **any** of the following becomes true:

1. A second consumer of `playbackState` appears (e.g., a global "now playing" mini-player) — at
   that point the Sound playback should also emit to `playbackState` and the listener should be
   retired.
2. The audio-focus / `AudioFocusRequest` flow needs to be added (in-call interruption handling,
   Bluetooth headset transitions). The unified player is the right home for that logic.
3. The codebase moves to `androidx.media3.exoplayer` for any reason. ExoPlayer subsumes both
   `MediaPlayer` instances and the listener-vs-StateFlow distinction becomes irrelevant.
4. The Sticker Cero `completed: Boolean` semantic needs to apply to non-Sound playback (unlikely
   today, but if a "preview must play to completion to count as listened" gating ever appears).
