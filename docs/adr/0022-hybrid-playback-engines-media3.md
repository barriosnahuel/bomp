# ADR 0022 — Hybrid playback engines: MediaPlayer for taps, Media3 for listening sessions

- **Status:** Accepted
- **Date:** 2026-07-04
- **Supersedes:** Amends [ADR 0005](0005-unified-audio-player.md) (its revisit criterion #3 —
  "the codebase moves to `androidx.media3.exoplayer`" — fired; the unified-`PlayerController`
  principle and concurrency invariant stand, but the controller now owns **two** engines). Amends
  [ADR 0007](0007-sound-playback-pause-resume.md) (the immersive listen deliberately loses
  resume-on-reopen; pause/resume semantics stand everywhere else). Touches
  [ADR 0008](0008-stable-sound-id.md) only in that `savedSoundPositions` stays keyed by `Sound.id`
  on the MediaPlayer path. Informs [ADR 0019](0019-in-app-bomp-recorder.md) (`MediaRecorder` stays)
  and [ADR 0020](0020-shared-envelope-waveform.md) (waveform extractor decided separately — spec
  `002f`).

## Context

The Apps Experience Program (AEP) media guideline prohibits exactly four legacy framework APIs —
`MediaPlayer`, `MediaExtractor`, `MediaMuxer`, `MediaMetadataRetriever` (verbatim: *"Shouldn't use
legacy Android framework APIs"*) — and requires media sessions when playback is a core journey.
`SoundPool` and `MediaRecorder` are **not** on the prohibited list. Bomp's playback runs entirely on
`MediaPlayer` (`PlayerControllerImpl`), with `MediaMetadataRetriever` extracting durations and
`MediaExtractor`+`MediaCodec` decoding waveform envelopes.

The project has a hard UX budget: **≤ 100 ms tap-to-sound** — the classic HCI instantaneity
threshold (Miller 1968 / Nielsen), and the gesture that defines the product.

### Measurements (physical Pixel 8, 2026-07-04, 10 iterations each)

Tap-to-sound guardrail (Macrobenchmark, PR #1264): current engine cold first tap **median 53.5 ms**
(31.3–80.9). Engine spike rounds, all on the production-shaped path (one live player, new audio per
tap):

| Engine / scenario | Proxy | Median |
|---|---|---|
| `MediaPlayer` reset+setDataSource+prepare+start | call return | 41 ms |
| `MediaPlayer`, live player, new item | OS playback-active callback | **80 ms** |
| ExoPlayer, live player, new item | OS playback-active callback | **390 ms** (273–486) |
| ExoPlayer, same scenario | `playoutStartSystemTimeMs` (own audible estimate) | 566 ms |
| ExoPlayer, tuned `DefaultLoadControl` (minimal local-file buffers) | same | 593 ms — tuning does not help; the cost is renderer/AudioTrack pipeline, not buffering |
| ExoPlayer, absolute floor (same item already prepared, re-play) | audible estimate | ~270–280 ms |
| `SoundPool.play()` after `load()` | call return | ~1 ms (load pre-warm 339 ms) |

Same OS-level proxy for both engines in the comparison rows — apples to apples. ExoPlayer is ~4×
over the 100 ms budget (and ~5× today's 80 ms) and not tunable to it: Media3 is engineered for
long-form robustness (streaming, buffering), where ~400 ms startup is invisible; in a soundboard
tap it is lag.

### Hands-on perception test (owner, on device)

A debug build with +310 ms artificial tap delay (the delta from today's 80 ms to ExoPlayer's
390 ms median):

- **List rows (My Bomps):** the lag is clearly felt and rejected — tap→sound is direct
  manipulation, nothing masks the delay.
- **Vault immersive listen:** the same delay went unnoticed — the tap triggers a fullscreen
  transition whose visual feedback masks the audio start (perceived-performance effect; the
  100 ms threshold applies to direct feedback, context switches tolerate ~1 s).

## Decision

**Hybrid engines behind the single `PlayerController` (ADR 0005's unification stands).** Engine per
surface, chosen by what the gesture is — a tap or a listening session:

| Surface | Engine | Notes |
|---|---|---|
| List rows: My Bomps / Explore / Search (tap) | `MediaPlayer` | 80 ms measured; the product-defining gesture |
| Add/edit preview (`AudioPreview`) + duplicate-name hint | `MediaPlayer` | Quick verification taps in the add flow |
| Vault immersive listen | **ExoPlayer + `MediaSession`** | Fullscreen transition masks startup; always starts from 0 (see *Deliberate UX change*) |
| Recorder review (`RecordingActivity`) | **ExoPlayer + `MediaSession`** | Listening journey; latency non-critical |
| Duration extraction (today `MediaMetadataRetriever`) | Media3 metadata reader | One-shot read at import/preview; no latency path (spec `002e`) |
| Future: trimmer (spec 009), system sounds (spec 014) | Media3 | Born on the modern base |

- `PlayerController` grows a **long-form API** (e.g. `startListenSession(...)`) routing to the
  ExoPlayer engine and registering the `MediaSession`; `startPlayingSound` / `startPlayingUri` keep
  routing to `MediaPlayer`. The **at-most-one-active-playback invariant (ADR 0005) is enforced
  across both engines** inside the controller.
- `MediaSession` scope: **listening sessions only** (immersive + recorder review). A 2-second Bomp
  tap gets no media notification — it would appear and die in seconds, pure noise.

### Deliberate UX change (owner decision)

The immersive listen **always starts from 0**. Today, backing out pauses with retained position and
reopening resumes mid-clip (ADR 0007 cache); the owner judged that behavior unexpected for an
immersive player and accepted losing it, which also removes any cross-engine position-cache
coupling. In-session pause/resume inside the immersive stays (native ExoPlayer). The recorder review keeps
its resume-from-offset semantics (`startPositionMs` in today's `startPlayingUri` call) — only the
immersive's cross-open retention is dropped.

### AEP consciously relegated

Enrollment requires implementing **all** applicable guidelines and Google Play validates the app;
no documented exception channel exists. With `MediaPlayer` (and today's `MediaExtractor` in the
waveform) in the APK, Bomp must be assumed ineligible. The owner priced ~310 ms of lag on the core
gesture against the program's rate-card benefit and chose UX. This is reversible — see *Revisit
criteria*.

## Options considered (and rejected)

- **Media3 pure** — one engine, simplest maintenance, AEP-eligible. Rejected: 390 ms median
  (max 486 ms) on the tap, felt and rejected hands-on; buffer tuning demonstrably doesn't close the
  gap (the floor with everything pre-warmed is ~270 ms).
- **`SoundPool` for the tap** (the master spec's original hybrid) — ~1 ms taps, AEP-clean.
  Rejected on UX: the row is a mini-player (progress bar, drag-seek, positional pause, completion
  events — ADR 0007), and `SoundPool` is a blind trigger: no position, no seek, no completion
  callback, and full-PCM-in-RAM limits hostile to 60 s clips.
- **Custom `AudioTrack`+PCM engine for the tap** — potentially ~ms latency **and** full UX **and**
  AEP-clean (`AudioTrack`/`MediaCodec` are not prohibited); the PCM pipeline partially exists in
  `WaveformExtractor`. Deferred, not rejected: weeks of custom-engine build/maintenance; parked as
  a future evaluable behind the guardrail (see *Revisit criteria*).
- **Do nothing** — keeps today's UX but blocks `MediaSession`, keeps `MediaMetadataRetriever`, and
  leaves the recorder/trimmer/system-sounds wave growing on legacy.

## Consequences

- The tap path stays measured-fast (80 ms; the `TapLatencyBenchmark` guardrail — PR #1264, pending
  merge at this ADR's date — gates regressions permanently once landed) and its ~650 lines of
  playback-engine tests stay valid.
- Two engines live behind one controller: the cross-engine preemption seam (Sound tap while a
  session plays, and vice versa) is new logic that needs dedicated tests (spec `002c`/`002d`).
- Media3 (`media3-exoplayer`, `media3-session`) enters the production dependency graph (spec
  `002c`; third-party notices + `CreditEntryTest` update ship with it).
- `scripts/check-adr-invariants.sh`'s ADR 0005 guard (single `MediaPlayer()` constructor site)
  stays valid and gains a sibling for the ExoPlayer engine when `002c` lands — construction allowed
  only inside the playback engine implementations.
- AEP media-guideline compliance is partial by choice. Of the four prohibited APIs: `MediaMuxer`
  is already absent, `MediaMetadataRetriever` dies in `002e`, `MediaExtractor` awaits the `002f`
  *eval* (migrate vs documented exception — undecided), and `MediaPlayer` remains, justified by
  the numbers above.
- Observation for `002f`: the immersive's dominant *perceived* wait is the first-open waveform
  decode (full-file PCM, process-lifetime cache), not audio start — a persistent envelope cache
  would improve it regardless of engine choice.

## Revisit criteria

Reopen this decision when **any** of:

1. **Media3 ships a credible low-latency tap path** (measure against the PR #1264 guardrail:
   ≤ 100 ms tap-to-sound on mid-tier hardware) — the `MediaPlayer` exception falls and the hybrid
   collapses to Media3 pure.
2. **AEP is re-prioritized** (rate-card benefit outweighs the lag, or Google adds an exceptions
   channel) — evaluate the custom `AudioTrack`+PCM engine spike before surrendering the tap budget.
3. **A new surface needs both instant start and system awareness** — today no surface needs both;
   one that does breaks the tap/session dichotomy this ADR rests on.
4. **`MediaPlayer` breaks on an OS release or OEM** in a way we can't work around — the exception
   loses its cost-benefit basis.

## Cross-references

- Specs: `../push-me-backlog/backlog/002-media3-playback-migration.md` (+ sub-specs `002a`–`002f`).
- AEP media guideline: https://developer.android.com/distribute/aep/aep-req-media-3
- Jetpack Media3: https://developer.android.com/media/media3
- Guardrail: PR #1264 (`TapLatencyBenchmark`, baseline median 53.5 ms).
