# ADR 0020 — Shared `EnvelopeWaveform` for listen + recorder-review

- **Status:** Accepted
- **Date:** 2026-06-26
- **Supersedes:** —

## Context

Two near-identical envelope-with-progress waveforms had grown in parallel: the Vault listen
screen's `ImmersiveWaveform` (drag-to-seek, 56 bars) and the recorder review's `ReviewWaveform`
(read-only, 48 bars), shipped one release apart (ADR 0019). Both draw the same thing — a real
amplitude envelope (0..1) normalized to the loudest bar with a `0.06f` floor, left portion filled
with `primary`, the rest dimmed, exposed via `ProgressBarRangeInfo` semantics. The clones diverged
only in bar count, bar width, the played-boundary test, and whether a drag gesture was wired.

The `0.06f` floor was duplicated four times (`PeakAccumulator.MIN_BAR`, `RecorderEnvelope.MIN_BAR`,
each waveform's `WAVEFORM_MIN_FRACTION`), and the "normalize to loudest, floor each bar" loop twice.
The code review on #1248 flagged the `PeakAccumulator` ↔ `buildRecorderEnvelope` normalize
duplication and deferred the dedup to here.

## Decision drivers

1. **One renderer, not two clones** — a fix or a11y tweak should land once, on both surfaces.
2. **Protect the shipped, tested Vault** — the listen screen is the regression-sensitive surface;
   the reconciliation must not change its behavior.
3. **Per-surface tuning stays visible** — bar count / width are genuine design choices per screen,
   not accidents to flatten.

## Decision

- A single `feature/waveform/EnvelopeWaveform.kt` Composable replaces both clones. Per-surface
  differences are parameters: `barCount`, `barFill`, a `contentDescription`, and `onSeek`.
- `onSeek: ((Float) -> Unit)? = null` carries the seek capability: non-null wires drag-to-scrub +
  the `SetProgress` semantics action (Vault); null renders a read-only wave (recorder).
- The played boundary is unified to the Vault's left-edge test (`i / barCount < progress`) so the
  regression-sensitive surface is pixel-identical; the recorder's fill boundary shifts by a sub-bar
  fraction (was bar-center `(i + 0.5) / barCount <= progress`) — visually imperceptible during
  playback, and the recorder progress test asserts semantics, not pixels.
- The `0.06f` floor + normalize loop collapse into `feature/waveform/WaveformNormalization.kt`
  (`WAVEFORM_MIN_BAR`, `normalizeEnvelope`), reused by `PeakAccumulator`, `buildRecorderEnvelope`,
  and the renderer. The all-silent branch is NOT folded in — its callers disagree on purpose
  (`PeakAccumulator` returns a flat baseline; `buildRecorderEnvelope` returns `null` to decode).

## Out of scope

`LiveWaveform` (the recorder's scrolling live input meter) and onboarding's decorative `Waveform`
are different concepts — not envelope-with-progress waves — and stay independent.

## Revisit criteria

If a third surface needs a materially different envelope (log scale, stereo split, mirrored bars),
re-evaluate whether parameters still beat separate components before piling on flags.
