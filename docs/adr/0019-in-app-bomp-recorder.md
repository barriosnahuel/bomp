# ADR 0019 — In-app Bomp recorder (full-screen native capture)

- **Status:** Accepted
- **Date:** 2026-06-21
- **Supersedes:** —
- **Amended:** 2026-06-23 (§ Draft recovery) · 2026-07-11 (§ Entry point & screen host — the retrofit into the Nav3 graph landed) · 2026-10-02 (§ Microphone-less devices) · 2026-10-03 (§ Microphone-less devices: file browser on every surface, measurement)

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

> **Amended 2026-07-11 — the retrofit landed (ADR 0024 D4).** `RecordingActivity` is gone; the recorder
> is the `RecorderRoute` destination (`RecorderHost`), and "Use this" pushes the naming destination on
> top of it rather than starting an Activity — so back from naming returns to the Review instead of
> tearing the flow down. Two consequences for the decisions below: the "clear at save-completion, not
> handoff" rule (§ Draft recovery) now matters *more*, since backing out of naming lands the user back on
> their take; and the `singleTask` launcher-relaunch hazard that motivated persisting the draft is gone
> with the second Activity, but the draft persistence stays — it is what still survives process death.

### Permission
Add `RECORD_AUDIO` to the manifest. First record → an on-brand priming screen, then the system
request. Denied → snackbar with "Open settings" CTA (detected via
`shouldShowRequestPermissionRationale`; permanent-deny routes to `ACTION_APPLICATION_DETAILS_SETTINGS`)
**and** an "import a file instead" escape — never a dead end. This is the app's first runtime
permission; the pattern established here is the precedent for future ones.

### Microphone-less devices (amendment 2026-10-02)
Declaring `RECORD_AUDIO` makes Google Play assume `android.hardware.microphone` is **required**, so the
release that shipped the recorder silently dropped ~20 device families with no mic (Android TV, some
tablets, ChromeOS profiles) from the catalogue. The filtering happens at the store, never in the merged
manifest, which is why no build or CI step caught it. Recording is one way to add a Bomp, not the core:
collecting, playing and bompear all work without a mic, so there is no product reason to exclude those
devices.

- **Manifest:** `<uses-feature android:name="android.hardware.microphone" android:required="false" />`
  overrides the implied requirement ([permissions that imply feature requirements](https://developer.android.com/guide/topics/manifest/uses-feature-element#permissions-features)).
  It only affects store filtering; capture still needs the runtime grant.
  Android TV stays out of the catalogue for other reasons (no `LEANBACK_LAUNCHER`, implied touchscreen,
  Play Console opt-in) — that is a separate decision, not covered here.
- **Runtime gate on hardware, not on the grant:** `PackageManager.hasSystemFeature(FEATURE_MICROPHONE)`
  is a separate axis from the permission flow above. A device *with* a mic and a denied grant keeps the
  Settings / import-escape path; a device *without* one is never offered capture.
- **Skip the Hub, don't hide its row.** With the two-path Hub (record / bring), a mic-less device would
  get a one-row sheet — a tap with no choice in it. The single Hub entry opens the bring guide directly
  instead; every "add a Bomp" surface (FAB, empty state, onboarding finish) goes through that entry.
  The funnel's entry event still fires, so "wanted to add a Bomp" keeps counting on these devices.
- **Recorder as last line of defense:** if the destination is reached anyway, a fresh visit shows a
  no-microphone message with at most the import escape (see the file-browser rule below) — no priming, no Settings CTA (there is nothing to
  grant). A restored draft still opens its Review, without the permission gate (review only plays back)
  and without "Re-record" (it would lead into a capture that cannot start).
- **No file browser, no import option — on every surface.** Every "import a file" option (this screen's
  escape, the mic-denied screen's escape, the bring guide's "find it on your phone") is offered only when
  a system file browser resolves; TV-like and managed builds can ship without one. A button that launches
  nothing is hidden, not shown, and copy that promised it drops the promise. The launch still guards
  `ActivityNotFoundException`, for a handler disabled between the check and the tap.
- **Measured per situation, not per device.** `import_hub_opened {hub_skipped}`, `record_mic_unavailable`
  and `import_option_hidden {surface}` fire only when a limited device reaches that surface, so a phone
  with both never pays for them. User properties (`has_microphone`, …) were rejected: a ~constant value
  repeated on every event row of every user, for a base that may be tiny. Accepted cost: a limited user
  who never reaches those surfaces stays invisible.

### Data model — internal `SoundSource`
The recorded clip saves to the **same destination and naming flow as an import** (no Vault pre-mark,
contra the design mock — we don't impose a privacy semantic the user didn't ask for). But the origin
is recorded **internally** so a future PR can decide on a UI treatment without a second migration:

- New enum `SoundSource { RECORDED, IMPORTED, BUNDLED }` on `Sound` and (persisted) `StoredSound`,
  defaulting to `IMPORTED`. With `encodeDefaults = false` (existing `SoundsRepository.json` config),
  pre-existing payloads carry no `source` field and decode as `IMPORTED` — correct, since *all*
  pre-recorder user content was imported. Bundled audio is `BUNDLED` (derivable: `file == null`, set
  by the bundled constructor and re-derived in `mergeWithBundled`). The recorder save path sets
  `RECORDED`. A missing/unknown enum coerces to the default via `coerceInputValues = true`, so the
  field can never break ADR 0018's recovery.
- **No backfill migration.** Unlike `isVisibleInMySounds` (ADR 0012), where the correct legacy value
  (`false` for private-only) differs from the default (`true`) and *must* be seeded, `source`'s
  correct legacy value (`IMPORTED`) **equals** the default — so every existing audio already reads the
  right value with no sweep (file-backed → `IMPORTED` default; bundled → constructor). A
  `migrateSourceIfNeeded()` was prototyped and dropped: it rewrote bytes nothing reads (the only value
  it persisted, `BUNDLED` on file-less stubs, is never read — bundled domain `Sound`s come from
  `PackagedAudios`). The default-on-read mechanism is the same one the codebase already trusts for
  `durationMs`/`isFavorite`/`isPinned`. If a future need to pin a physical value arises (e.g. a default
  change, or a raw out-of-app reader), add a targeted migration then.

### Out of scope (v1)
Background/foreground-service recording (a Bomp is short and foreground), trimming (that's the
trimmer, `v2.3.0-04`), filters/noise-cancel/effects, multi-track, and a preview waveform (reuses the
trimmer's waveform lib when it lands — preview is play/pause + timer for now).

### Draft recovery (amendment 2026-06-23)
The original design treated an unsaved clip as *ephemeral*: the temp file was blanket-purged on
recorder entry and dropped in `onCleared`. On device this lost work in a normal flow — capturing a
clip, leaving the app via **Home**, then re-opening from the **launcher** (not Recents). Because both
`LandingActivity` and `RecordingActivity` are `singleTask` in one task, the launcher intent re-targets
the root (`LandingActivity`) and `clearTop`s `RecordingActivity` — destroying the in-progress Review.
A process death has the same effect. This is the natural extension of this ADR's existing **auto-stop
*and preserve*** stance (§ Pause/resume): "preserve" now survives the Activity/process, not just an
audio-focus blip. It is **not** background recording (still out of scope) and **not** resume-from-where-
you-left of an in-progress capture (still no `MediaRecorder.pause()`); the recovered clip lands in
**Review**, exactly where the user left it.

- **What persists:** a *pending draft* = the temp clip's filename + its `durationMs`, in a dedicated
  DataStore Preferences file (`RecorderDraftStore`). Persisted on entering Review; cleared on save
  (handoff), re-record, explicit discard, or too-short. The clip bytes already live in
  `cacheDir/recordings/`; the draft only points at them.
- **Recovery UX:** a non-intrusive banner on the My Sounds list ("unsaved recording — Continue /
  Discard"); Continue relaunches `RecordingActivity` with a resume flag → restores Review (file spared
  from the purge); Discard deletes the clip + clears the draft.
- **`cacheDir`, not `filesDir`:** the clip stays OS-evictable. If the OS reclaims the cache under real
  storage pressure while the user is away, the draft self-heals to "none" (existence re-validated on
  every read) rather than dangling. Accepted trade-off: a draft is a short-lived "you were mid-thing,"
  not durable storage — and the alternative (`filesDir`) would need its own GC for abandoned clips.
- **Durability of the clear:** `RecorderDraftStore` writes on a process-lived,
  `limitedParallelism(1)` scope so a save-then-clear can't reorder and a clear survives the Activity
  finishing immediately after (back-discard / handoff). A single process-singleton instance
  (`RecorderDraftStoreProvider`) is shared by the recorder and the Landing banner so the two never race
  across separate scopes.
- **Clear at save-completion, not handoff:** "Use this" hands the clip to `AddButtonActivity` but does
  **not** clear the draft; the save pipeline clears it only once the `Sound` is actually persisted. So
  backing out of the name/save screen still leaves the clip recoverable from the banner.
- **Best-effort boundary (backgrounding *mid-recording*):** if the OS reclaims the stopped Activity
  *during* the auto-stop (before `engine.stop()` finalizes the `.m4a` moov atom), the clip is
  unrecoverable — an unfinalized MediaRecorder file is corrupt regardless of any metadata we persist.
  Guaranteeing this would need a foreground service, which is explicitly out of scope (recording is
  foreground-only). Draft recovery covers the common cases (Review reached, launcher re-entry, process
  death *after* a clip exists); the narrow mid-finalize kill is accepted.

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
  ear (spec § 9.3). Headless tests cover the model + `source` provenance, permission-denial logic, and
  state machine; the capture/preview loop is verified on a real device in a supervised pass.
- **Ship gate (legal, not technical):** `privacy-policy.html` + `data-safety.html` in
  `push-me-ghpages` must be updated to disclose mic capture **before** release. Coordinate that merge
  with the ship.
- **`RECORDED` has no producer until the recorder PR** — the enum value + the `source` field land
  first (model groundwork) so the recorder PR only sets `source = RECORDED`.

## Invariants

- The Vault must still never use a live `Visualizer`/`RECORD_AUDIO` for *playback* visuals
  (`WaveformExtractor.kt`); `RECORD_AUDIO` is for explicit capture only.
- `SoundSource` is **internal** — no UI branches on it until a separate, deliberate decision.
- Recorded files enter `AddButtonFeature.saveNewButtonAsync`; the recorder does not fork the
  persistence/validation path.
- A draft (§ Draft recovery) is a *pointer* to a `cacheDir` clip, never durable storage — it must
  re-validate file existence on read and self-heal to "none", and it must be cleared on every terminal
  outcome (save, discard, re-record, too-short) so the banner never offers a clip the user resolved.
- Every path into capture checks microphone **hardware** (`FEATURE_MICROPHONE`), not just the
  `RECORD_AUDIO` grant — the manifest declares the mic optional, so mic-less devices install the app.
- Every "import a file" option checks a system file browser resolves before it renders.

## Revisit criteria

- **True pause/resume** — add an API-24+ `pause()`/`resume()` path (with a 23 auto-stop fallback) if
  interruption-resume turns out to matter to users.
- **Format tuning** — if the average recorded Bomp exceeds ~500 KB, drop the sample rate/bitrate or
  evaluate OPUS on devices that support it.
- **Push-to-talk** — add as an optional setting if capture-speed data justifies it.
- **Nav3** — fold `RecordingActivity` into the graph once `v2.3.0-02` migrates the rest. The draft
  banner's launcher-re-entry problem (§ Draft recovery) is a task-model artifact of the standalone
  Activity; a Nav3 destination would not `clearTop` the recorder, so revisit whether the draft store is
  still needed (the process-death case would remain — likely keep it).
- **Durable drafts** — if telemetry shows OS cache eviction loses drafts often enough to matter, move
  the clip to `filesDir` with its own abandoned-clip GC.

## Cross-references

- Backlog spec: `../push-me-backlog/backlog/v2.3.0-03-bomp-recorder.md` (the "why" + estimates).
- [ADR 0012](0012-explicit-my-sounds-visibility.md) (`migrateVisibilityIfNeeded`, the one-shot
  backfill precedent this decision deliberately diverges from) and
  [ADR 0018](0018-legacy-sounds-schema-migration.md) (the read-time recovery the `source` default
  rides on).
- `WaveformExtractor.kt` (the Vault Visualizer/`RECORD_AUDIO` note this ADR clarifies).
- `MediaRecorder`: https://developer.android.com/reference/android/media/MediaRecorder ;
  runtime permissions: https://developer.android.com/training/permissions/requesting
