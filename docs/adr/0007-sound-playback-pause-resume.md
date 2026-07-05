# ADR 0007 — Sound playback uses pause/resume with in-process position cache

- **Status:** Accepted (cache key migrated from `Sound.name` to `Sound.id` by [ADR 0008](0008-stable-sound-id.md); the Vault immersive's cross-open resume dropped by [ADR 0022](0022-hybrid-playback-engines-media3.md) — sessions always start from 0)
- **Date:** 2026-05-13
- **Supersedes:** ADR 0005 (the "tap again to stop" Home semantic; the rest of ADR 0005 — unified player, single MediaPlayer, listener+StateFlow bridging — stands)

## Context

Before this ADR, `Sound` playback in Home and Explore was modelled as "tap to play, tap again to stop." `PlayerControllerImpl.startPlayingSound` always called `MediaPlayer.reset()` and re-prepared the data source on every tap, even if the user was returning to a sound they had just been listening to. Position was lost on every tap-to-stop and every cross-sound preemption. The Add Audio preview, by contrast, already supported pause/resume via the dedicated `pause()`/`resume()` API on the controller.

The user reported the inconsistency: tapping the playing button on the AddButton preview pauses (resumable); tapping it on My Sounds or Explore resets. They asked for unified pause/resume across all surfaces, with these constraints:

- Position must be preserved across pause/resume of the same sound.
- Position must be preserved when switching between sounds and coming back.
- Position must survive Activity recreation (rotation).
- Position is acceptable to lose on app/OS kill.
- `MediaPlayer.reset()` must not happen except when the audio finishes naturally.

## Decision drivers

1. **Single mental model across surfaces.** The same gesture (tap the playing icon) should always produce the same audible result.
2. **No regression in Add Audio preview.** That UX already worked; the new logic must subsume it without changing observable behavior there.
3. **Minimum churn in `SoundsViewModel` and the listener interface.** ADR 0005 deliberately bridged the listener + StateFlow worlds; revisiting that is out of scope. The VM/UI continue to treat "paused" and "stopped" identically.
4. **MediaPlayer reality.** A single `MediaPlayer` cannot hold two data sources at once. Cross-sound switching unavoidably requires `reset()` + `setDataSource()` + `prepare()`. The "no reset" constraint is interpreted *semantically* — the user never perceives position loss — rather than literally. Position is captured before `reset()` and restored via `seekTo()` on return.

## Decision

`PlayerControllerImpl` gains a state machine plus an in-memory position cache:

- A `target: PlaybackTarget?` field (sealed sub-types `SoundTarget(Sound)` and `UriTarget(Uri)`) tracks what is currently loaded into the `MediaPlayer`. When non-null the player is in STARTED or PAUSED. When `null` the player is IDLE.
- A `savedSoundPositions: MutableMap<String, Int>` (keyed by `Sound.name`) holds the position of any sound that is no longer the current target but might be returned to. This map lives in-process; it dies with the process.

The public API behavior changes:

| API | Before this ADR | After this ADR |
|---|---|---|
| `startPlayingSound(sound)` | Always `reset()` + prepare + start. | If `sound` is the paused current target → `MediaPlayer.start()` in place (no reset, no re-prepare). Else preempt the current target (save its position, fire `onPlayerPause(prev, ...)`), reset, prepare, seek to `savedSoundPositions[sound.name]` if cached, start. |
| `pause()` | No-op for `Sound`; only meaningful for `Uri`. | Pauses the current target (Sound or Uri). For Sound: saves `currentPosition` in `savedSoundPositions[t.sound.name]` and fires `onPlayerPause(t.sound, positionMs, durationMs)`. For Uri: updates `_playbackState.copy(isPlaying=false, positionMs=...)`. |
| `resume()` | No-op for `Sound`; only meaningful for `Uri`. | Resumes whichever target is paused, in place, via `MediaPlayer.start()`. Reports `onPlayerStart` for Sound (with the resumed position via the new `positionMs` parameter) or flips `_playbackState.isPlaying = true` for Uri. |
| `stopPlayingSound()` | Hard stop; emit listener / clear state. | Hard stop **and** clear the saved position for the current sound. Use for definitive stop (AudioPreview disposal). Pause/play toggle no longer routes through this. |
| `startPlayingUri(uri)` | Hard-stops any current Sound. | Preempts the current Sound by saving its position (fires `onPlayerPause(prev, ...)`), then loads the Uri. Symmetric to Sound→Sound switching. |
| `forgetSound(sound)` *(new)* | — | Drops the sound's saved position from the cache. If the sound is the current target, also stops and resets. Called by `SoundsViewModel.deleteSound` unconditionally so a future name-collision doesn't seekTo a stale position. |

The `PlayerControllerListener.onPlayerStart` signature grows a third parameter, `positionMs: Int = 0`, so the VM can initialize `_playbackProgress` at the resumed position and avoid a slider flicker before the first `progressRunnable` tick.

A new listener method `onPlayerPause(sound, positionMs, durationMs)` is added, distinct from `onPlayerStop`. **Why distinct:** a pause (or a preemption — switching to another sound) retains the position; a stop discards it. If both routed through `onPlayerStop`, the UI could not tell "paused at 0:02, keep the bar there" from "stopped, reset the bar to 0" — and a pause that visually snaps the progress bar to zero reads as a reset, which is exactly the bug this ADR fixes. So `pause()` and the Sound branch of `preemptCurrentTargetPreservingPosition()` fire `onPlayerPause`; only natural completion, `stopPlayingSound`, and `forgetSound` fire `onPlayerStop`. `SoundsViewModel` keeps a `_pausedProgress: Map<String, PlaybackProgress>` (keyed by sound name, in-memory) populated from `onPlayerPause` and cleared on `onPlayerStart`/`onPlayerStop`; the Home/Explore/Search UI reads it so a paused sound's progress bar stays where it was. Multiple sounds can be in this map at once (A paused, B paused, C playing) — it mirrors the controller's `savedSoundPositions`.

`SoundsViewModel.playOrStop(sound)` flips its dispatch:

- If `sound.isPlaying` → `controller.pause()` (was `stopPlayingSound()`).
- Else → `controller.startPlayingSound(sound)` (unchanged signature; the controller decides whether to resume in place or load fresh).

The UI does not differentiate "paused" from "stopped." `_playingSound` is null in both cases; the slider collapses; the play button shows the play icon. Position is silently preserved by the controller and surfaces only when the user re-taps and the controller emits `onPlayerStart(positionMs > 0)`.

## Why `Sound.name` as the cache key

The codebase already treats `Sound.name` as the effective primary key:

- `SoundsRepository.save()` upserts by name (last-write-wins, line 77–85).
- `SoundsViewModel._soundDurations: Map<String, Int>` is also keyed by name.
- 14+ identity-comparison sites use `it.name == sound.name`.

This decision is consistent with the rest of the codebase. The known weakness — two sounds with the same name cannot coexist in persistence, and a rename leaves the cache entry orphaned — is **out of scope** for this ADR and tracked separately as a follow-up refactor toward a stable `Sound.id`. See the handoff document `handoff/2026-05-13-2235-sound-stable-id-refactor.md`.

## Options considered (and rejected)

- **(A) Multiple `MediaPlayer` instances, one per "paused" sound.** Truly literal "no reset ever." Rejected: buffers, codecs, and file descriptors per instance, plus arbitrary growth as the user touches more sounds. Soundboards can touch dozens of sounds in a session. The cost-benefit doesn't justify.

- **(B) Persist `savedSoundPositions` to DataStore so positions survive process death.** Rejected: the user explicitly said losing position on process kill is acceptable, and writing every pause to disk would amplify the `_soundDurations` durability already in place. Reconsider if the product wants "resume where you left off" across launches.

- **(C) Add a public `toggleSound(sound)` API instead of overloading `startPlayingSound` with the "same sound paused → resume" branch.** Rejected: the VM's `playOrStop` already does the same/different decision via `sound.isPlaying`; pushing the toggle into the controller's `startPlayingSound` keeps the VM dumb and the test surface small. Calls to `startPlayingSound` from non-toggling code paths (e.g. autoplay flows in the future) still get the right behavior — same sound paused resumes; new sound switches with seek-restore.

- **(D) Block tap-while-playing entirely until UX agrees on pause vs stop.** Rejected as bikeshed; the user is the product owner and asked for pause.

## Invariants (preserved or strengthened)

- **At most one playback is active at any time** (preserved from ADR 0005). Preemption now pauses-with-saved-position instead of full-stops, but it is still a single-active-target invariant.
- **`_playingSound` is the single VM-side source of truth for "what is currently playing"** (preserved). Paused sounds are *not* in `_playingSound` — the cache is the controller's internal concern.
- **No-flicker:** `onPlayerStart` fires only after `MediaPlayer.start()` succeeds (preserved). Now with the additional `positionMs` so resume doesn't flash 0% before the first progress tick.

## Consequences

**Positive:**
- Consistent pause/resume across My Sounds, Explore, and the AddButton preview.
- No surprise position loss when the user momentarily switches sounds.
- Resume is cheaper than restart: skips reset + setDataSource + prepare, just `MediaPlayer.start()`.

**Negative / accepted costs:**
- An additional in-memory `Map<String, Int>` per process. Bounded by the number of sounds the user has touched in a session; small.
- The "name as identity" weakness now extends to playback state. If a user deletes a sound and creates a new one with the same name in the same session, the new sound's first play would seek to the stale position. Mitigated by `forgetSound(sound)` called from `deleteSound`. Fully resolved by the stable-ID refactor (separate scope).
- A subtle UX choice: the slider in `SoundItem` still hides when the sound is paused (we don't expose paused-with-position visually). If product wants paused-state visualization later, the controller already has the position — surfacing it is a UI-only change.

## Revisit criteria

Open a follow-up ADR if any of the following becomes true:

1. We want positions to persist across app launches (move from in-memory map to DataStore).
2. The stable-`Sound.id` refactor lands; the cache key migrates from `name` to `id`.
3. UX wants a "paused" state visually distinct from "stopped" (e.g. show a half-state slider on the paused sound).
4. A second listener / consumer of "now playing" appears that needs paused-state visibility — at that point, surface paused state in the listener interface or migrate Sound playback to the `playbackState` StateFlow as ADR 0005 already anticipates.
