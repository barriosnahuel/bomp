# ADR 0019 — In-app Bomp recorder (full-screen native capture)

- **Status:** Accepted
- **Date:** 2026-06-21
- **Supersedes:** —

## Context

Today the only way to create a custom Bomp is to import an existing audio file — via the share
sheet (`AddButtonActivity`, `ACTION_SEND`) or the in-app import picker. That couples creation to a
second app (record elsewhere → export → share into Bomp) and, on some OEM/locale combinations, the
share path is flaky. The backlog spec `v2.3.0-03-bomp-recorder` asks for a native capture channel:
from impulse ("I want to make a joke with my voice") to a saveable sticker in under ~10 s, without
leaving the app.

This ADR fixes the **architectural decisions** the spec's § 8 left open and the points where the
design mock (`claude.ai/design`, `ui_kits/push-me-app/recording`) and the spec diverge, so the
implementation PRs have closed decisions to build against.

**Relationship to the Vault waveform stance.** `WaveformExtractor.kt` documents a deliberate refusal
to use a live `android.media.audiofx.Visualizer` because it would require `RECORD_AUDIO` — "a
non-starter for a privacy-first Vault". That note is scoped to **passive mic access for waveform
rendering** during playback; it is *not* a blanket no-recording stance. An explicit,
user-initiated, permission-primed capture flow does not contradict it. This ADR does not amend that
decision — the Vault still must not silently tap the mic for visuals.

## Decision drivers

1. **Low, predictable risk** — `MediaRecorder` is synchronous and stable; the output format is
   *chosen by us*, so there is no input-codec matrix (unlike the trimmer, `v2.3.0-04`).
2. **Reuse the proven save pipeline** — the recorded file must enter the same
   `AddButtonFeature.saveNewButtonAsync` path (validation, persistence, duration extraction) already
   in production.
3. **Accessibility is non-negotiable** — the capture gesture must satisfy WCAG 2.2 AA (§ CLAUDE.md
   *Accessibility*).
4. **`minSdk` 23 is a hard floor** — any API-24+ capability (notably `MediaRecorder.pause()`) needs a
   graceful path on 23, not a crash or a dead button.
5. **Ship without leaking a product decision** — the recorded origin must be *recoverable internally*
   without forcing a UI treatment now.

## Decision

### Capture format
**AAC-LC, mono, MPEG-4 (`.m4a`) container.** Starting encoder params: 44.1 kHz sample rate,
64 kbps — ≈ 480 KB for the 60 s cap, under the spec's ~500 KB watch-line. These are the *starting*
values; the on-device iteration pass (§ *Consequences*) validates gain/quality and may tune them.
Guaranteed by the platform since API 18, so no OEM fallback matrix. The trimmer-shared playback uses
the existing `MediaPlayer`, which decodes `.m4a` natively — no ExoPlayer/Media3.

### Duration bounds
Max **60 s** (auto-stop with a soft fade, no intrusive dialog); min **1 s** (a shorter stop discards
the temp file and shows a "too short, try again" snackbar). A Bomp over a minute is a message, not a
sticker.

### Interaction model — tap-to-toggle
Tap starts, tap stops (the hero button has three states: ready → recording → stopped→preview).
**Not** push-to-talk: hold-to-record would be the only mode and that violates WCAG 2.5.7 (Dragging
Movements) and excludes reduced-mobility users. Push-to-talk may return later as an *optional* setting
if data justifies it — never as the sole mode.

### Pause/resume — none in v1
The design mock shows a manual pause/resume control and the spec's Scenario B wants resume-from-where-
it-left after an interruption. Both rely on `MediaRecorder.pause()`/`resume()`, which are **API 24+**
while `minSdk` is 23. v1 ships **no manual pause**; on audio-focus loss / incoming call the recorder
**auto-stops and preserves** what was captured, offering "use what's recorded" or "re-record" — no
exact resume. This keeps one code path across API 23–24+ (no SDK-boundary branch, smaller bug
surface). True pause/resume (API-24+ with a 23 fallback) is a deferred enhancement, not a v1 gap to
paper over.

### Entry point & screen host — standalone Activity, pre-Nav3
The spec sequences the recorder *after* the Nav3 migration (`v2.3.0-02`) so its screens are born as
graph destinations. We are **not** blocking the recorder on that migration: it ships now as a
full-screen `RecordingActivity` (the same `singleTask`/`exported=false` shape as `AddButtonActivity`),
launched from the existing "Record" row in `ImportHubSheet` (today visible but inert, badged "Soon").
Retrofitting it into the Nav3 graph once `v2.3.0-02` lands is mechanical and tracked there.

### Permission
Add `RECORD_AUDIO` to the manifest. First record → an on-brand priming screen, then the system
request. Denied → snackbar with "Open settings" CTA (detected via
`shouldShowRequestPermissionRationale`; permanent-deny routes to `ACTION_APPLICATION_DETAILS_SETTINGS`)
**and** an "import a file instead" escape — never a dead end. This is the app's first runtime
permission; the pattern established here is the precedent for future ones.

### Data model — internal `SoundSource`
The recorded clip saves to the **same destination and naming flow as an import** (no Vault pre-mark,
contra the design mock — we don't impose a privacy semantic the user didn't ask for). But the origin
is recorded **internally** so a future PR can decide on a UI treatment without a second migration:

- New enum `SoundSource { RECORDED, IMPORTED, BUNDLED }` on `Sound` and (persisted) `StoredSound`,
  defaulting to `IMPORTED`. With `encodeDefaults = false` (existing `SoundsRepository.json` config),
  pre-existing payloads carry no `source` field and decode as `IMPORTED` — correct, since *all*
  pre-recorder user content was imported. Bundled audio is `BUNDLED` (derivable: `file == null`).
  The recorder save path sets `RECORDED`.
- A one-shot `migrateSourceIfNeeded()` sweeps existing records on next app open and persists the
  marking (file-backed → `IMPORTED`, file-less stub → `BUNDLED`, preserving any `RECORDED`), guarded
  by a `source_migrated_v1` key. This mirrors `migrateVisibilityIfNeeded` exactly (ADR 0012) and rides
  the recovery machinery of ADR 0018 (a missing/unknown enum coerces to the default via
  `coerceInputValues = true`, so it can never wipe the list).

### Out of scope (v1)
Background/foreground-service recording (a Bomp is short and foreground), trimming (that's the
trimmer, `v2.3.0-04`), filters/noise-cancel/effects, multi-track, and a preview waveform (reuses the
trimmer's waveform lib when it lands — preview is play/pause + timer for now).

## Options considered (and rejected)

- **Wait for Nav3 before building** — avoids a retrofit, but blocks an independent, low-risk creation
  channel on a larger migration. The retrofit is mechanical; the standalone Activity matches the
  existing `AddButtonActivity` precedent. Rejected in favor of shipping the channel now.
- **Push-to-talk as the capture gesture** — faster for very short clips, but as the sole mode it
  fails WCAG 2.5.7 and excludes reduced-mobility users (driver 3). Rejected as default; viable later
  as an opt-in setting.
- **Manual pause/resume in v1** — matches the mock, but needs an API-24-vs-23 SDK-boundary branch
  (driver 4) for a marginal v1 benefit. Deferred; auto-stop-and-preserve covers the interruption case
  uniformly.
- **Pre-mark recordings into the Vault** (the mock) — treats voice as inherently private, but adds a
  biometric step to the happy path and imposes a semantic the user didn't choose (driver 5). Rejected;
  same neutral destination as an import, origin tracked internally instead.
- **Transcode to a uniform `.mp3`** — keeps one file extension, but adds a heavy encoder dependency +
  APK growth for no functional gain; the pipeline already handles arbitrary container extensions.
  Rejected; let `MediaRecorder` emit `.m4a`.

## Consequences

- **First runtime-permission surface** in the app — the `RECORD_AUDIO` request/denial flow is the
  template for any future permission.
- **`MediaRecorder` lifecycle is the main care point** — it must be released in `onStop` (an
  unreleased recorder can hold the mic globally until reboot on some Samsung/Xiaomi devices); audio
  focus loss must auto-stop. The Activity smoke test covers `onStop` with an active recorder.
- **Device iteration is unavoidable and unaccelerable** — mic gain/quality across OEMs needs a human
  ear (spec § 9.3). Headless tests cover the model/migration, permission-denial logic, and state
  machine; the capture/preview loop is verified on a real device in a supervised pass.
- **Ship gate (legal, not technical):** `privacy-policy.html` + `data-safety.html` in
  `push-me-ghpages` must be updated to disclose mic capture **before** release. Coordinate that merge
  with the ship.
- **`RECORDED` has no producer until the recorder PR** — the enum value and migration land first
  (model groundwork) so the recorder PR only sets `source = RECORDED`.

## Invariants

- The Vault must still never use a live `Visualizer`/`RECORD_AUDIO` for *playback* visuals
  (`WaveformExtractor.kt`); `RECORD_AUDIO` is for explicit capture only.
- `SoundSource` is **internal** — no UI branches on it until a separate, deliberate decision.
- Recorded files enter `AddButtonFeature.saveNewButtonAsync`; the recorder does not fork the
  persistence/validation path.

## Revisit criteria

- **True pause/resume** — add an API-24+ `pause()`/`resume()` path (with a 23 auto-stop fallback) if
  interruption-resume turns out to matter to users.
- **Format tuning** — if the average recorded Bomp exceeds ~500 KB, drop the sample rate/bitrate or
  evaluate OPUS on devices that support it.
- **Push-to-talk** — add as an optional setting if capture-speed data justifies it.
- **Nav3** — fold `RecordingActivity` into the graph once `v2.3.0-02` migrates the rest.

## Cross-references

- Backlog spec: `../push-me-backlog/backlog/v2.3.0-03-bomp-recorder.md` (the "why" + estimates).
- [ADR 0012](0012-explicit-my-sounds-visibility.md) (`migrateVisibilityIfNeeded`, the one-shot
  backfill precedent) and [ADR 0018](0018-legacy-sounds-schema-migration.md) (the read-time recovery
  the `source` default rides on).
- [ADR 0008](0008-stable-sound-id.md) (the `Sound.id` scheme the migration keys on).
- `WaveformExtractor.kt` (the Vault Visualizer/`RECORD_AUDIO` note this ADR clarifies).
- `MediaRecorder`: https://developer.android.com/reference/android/media/MediaRecorder ;
  runtime permissions: https://developer.android.com/training/permissions/requesting
