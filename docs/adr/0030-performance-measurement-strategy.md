# ADR 0030 — Performance measurement strategy: smoothness and responsiveness as separate axes

- **Status:** Accepted
- **Date:** 2026-11-23
- **Supersedes:** —

## Context

The weekly exec report flagged LandingActivity with 60–100% "slow frames" (Firebase Performance screen
trace, frames > 16 ms) on low-tier devices. The on-device investigation found the number was driven by
the onboarding tour's infinite pulse animation, and that it did **not** describe what users feel:

- Firebase Performance counts a frame as slow when FrameMetrics `TOTAL_DURATION` exceeds a **fixed
  16 ms** (`firebase-perf` `FrameMetricsCalculator` + `Constants.SLOW_FRAME_TIME`), per Activity
  window. `TOTAL_DURATION` includes the wait for a free buffer (`dequeueBuffer`), so any continuous
  animation that keeps the pipeline full ("Buffer Stuffing") reads ~100% slow even when every frame
  reaches the screen on time. It ignores `FrameMetrics.DEADLINE` and the display refresh rate; the
  platform's own `JankTracker` (gfxinfo) forgives that wait, Firebase does not. RenderThread-driven
  animation frames (e.g. `AnimatedVectorDrawable`) are counted the same way — verified by experiment.
  Upstream: firebase-android-sdk #3386 (closed 2022, threshold kept "to align with Android Vitals").
- Lab measurement on a moto g04 (Test Lab, 90 Hz): with the pulse on, the app missed its frame
  deadline on ~1% of frames; a tap's response reached the screen in p50 62 ms vs 47 ms with the pulse
  off (`InteractionToNextPaintBenchmark`) — ~1.4 frames, inside INP's "good" band.
- Android has **no platform responsiveness metric** between "fine" and ANR: Play vitals covers crashes,
  ANR (input not handled in 5 s), start-up and slow/frozen frames (smoothness), but nothing like web
  [INP](https://web.dev/articles/inp) for the 100–500 ms band users perceive as "slow app".

So one smoothness number was being read as a UX verdict, and responsiveness wasn't measured at all.

## Decision

1. **Two axes, never read one with the other's metric.**
   - **Smoothness** — slow frames (> 16 ms) and frozen frames (> 700 ms). Both stay in force; neither
     threshold is relaxed.
   - **Responsiveness** — **Interaction to Next Paint (INP)**, Android analog: tap's `ACTION_UP`
     reaching the system → presentation of the first app frame drawn after the app handled it. Same
     span as web INP (excludes touch-panel latency and display scan-out), so it is judged against
     **INP thresholds: ≤ 200 ms good**, not against the ~100 ms end-to-end "feels instant" budget.
     Span diagram: CONTRIBUTING.md § *Performance*.
2. **Where each number comes from.**
   - **Lab (source of truth for diagnosis):** Macrobenchmark on a physical low-tier device via
     Firebase Test Lab (moto g04 `lion`, Galaxy A16 `a16x`) — start-up, frame timing, first-run
     journey, INP. Perfetto traces per iteration for root cause.
   - **Field (trend signal):** Firebase Performance screen traces. A slow-frame spike triggers a lab
     run before any conclusion; frozen frames and ANR stay direct alarms.
   - **UI test suite on the emulator:** correctness only, never performance numbers (emulator rendering
     makes every frame read slow; see [ADR 0001](0001-local-ui-test-suite.md),
     [ADR 0016](0016-jankstats-frozen-frame-crash-gate.md)).
3. **When.** The Test Lab benchmark set runs before each release cut and whenever the exec report
   raises a performance alarm, within the Test Lab free daily quota.
4. **How the exec report reads it.** A screen's slow-frame ratio is reported next to its lab INP and
   frozen-frame rate; a high slow-frame ratio with good INP and no frozen frames is reported as
   "smoothness metric inflated, no perceived delay", not as a regression.

## Options considered (and rejected)

- **Judge by frozen frames only.** Rejected: drops the smoothness bar; slow frames remain a valid
  signal, they just aren't a responsiveness verdict.
- **Change the animation to lower the Firebase number** (RenderThread AVD — tried, no effect; separate
  window/`Popup`; pause after N cycles). Rejected for this case: optimizes the metric, not the UX, which
  the INP measurement showed is fine.
- **Perfetto traces in production** (`ProfilingManager`, API 35+). Rejected as a metric source: partial
  device coverage on exactly the low-tier models, MB-sized private traces, no upload path. Kept as a
  future on-demand diagnostic.
- **Perfetto on every UI test.** Rejected: emulator numbers are meaningless and tracing slows a fragile
  suite; correctness and performance stay separate.

## Consequences

- Responsiveness gets a number and a threshold; a regression in tap latency is now detectable in lab.
- Field INP does not exist yet. Measuring it on real users would need in-app code (input timestamp +
  FrameMetrics present time, sent as a custom trace) — a separate decision.
- The lab runs depend on Test Lab, which Google shuts down on 2027-09-30 (successor: Developer Device
  Platform); the strategy holds, the runner changes.

## Revisit criteria

- Firebase Performance or Play vitals ship a deadline- or refresh-rate-aware slow-frame metric, or a
  responsiveness metric → adopt it as the field source.
- We decide to measure INP in production → amend § 2 with the field INP source and its threshold.
- Test Lab shutdown → move the runner, keep the device tier.

## Cross-references

- [ADR 0015](0015-macrobenchmark-seeding-architecture.md) — Macrobenchmark harness.
- [ADR 0016](0016-jankstats-frozen-frame-crash-gate.md) — debug frozen-frame gate.
- CONTRIBUTING.md § *Performance* — how to run each benchmark, Test Lab command, INP span diagram.
