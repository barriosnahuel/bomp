# ADR 0016 — Debug-only JankStats frozen-frame crash gate

- **Status:** Accepted
- **Date:** 2026-06-09
- **Supersedes:** —

## Context

The LandingActivity jank investigation was **reactive**: Firebase Performance flagged the entry
screen at ~24.8% slow frames weeks after release, and only then did we build the Macrobenchmark
([ADR 0015](0015-macrobenchmark-seeding-architecture.md)) and fix cold start with a Baseline Profile.
This ADR is about **shift-left**: catch the *next* main-thread-block regression in development, at the
lowest operational cost — no "remember to read logcat", it just fails, the way `StrictModeConfigurator`
crashes on a disk-on-main violation.

PR #1202 added debug-only JankStats (`androidx.metrics:metrics-performance`, `debugImplementation`): a
`JankStatsLogger` that installs `JankStats.createAndTrack` per Activity and logs janky frames by
screen. As log-only it has the same problem the repo already rejected for StrictMode violations — a
diagnostic nobody watches proactively.

## Decision drivers

1. **Fail loud, like StrictMode** — an egregious main-thread block should crash a debug run, not wait
   for production telemetry.
2. **Not flaky** — jank is a non-deterministic spectrum (GC, thermal, emulator). A gate that fires on
   ordinary jitter is worse than no gate.
3. **Catch main-thread blocks specifically** — the crash-worthy class is a blocked UI thread, not
   GPU-bound slowness (a real but different problem, and not crash-worthy).
4. **Cheap** — reuse the frames the app already renders; no new tests, no human watching logcat.

## Decision

A **frozen frame** — a frame whose **UI-thread** duration (`FrameData.frameDurationUiNanos`) reaches
**700 ms** — crashes the process, via `Tracker.track(FrozenFrameException(...))` + a main-looper
`throw` (the exact `StrictModeConfigurator` kill shape; posted to the main looper because JankStats
delivers frames on a background `HandlerThread`). The decision logic lives in a pure, unit-tested
`FrozenFrameGate`; `JankStatsLogger` wires the frame source and the kill.

Three mechanisms keep it from being flaky:

- **Per-screen startup window** — frozen frames within 5 s of a *screen's first frame* don't count
  (cold composition + Firebase init legitimately exceed 700 ms). Re-armed per screen, keyed by the
  attributed `screen` state — not once per process, so an Activity opened late gets the same grace.
- **Sustained-block tolerance** — crash on the **2nd** frozen frame within a 5 s window; a lone frozen
  frame is absorbed as a one-off hiccup, and two frozen frames far apart don't accumulate.
- **Allowlist** — `KnownHeavyFrame` matchers exempt known-legit-heavy renders (the frozen-frame
  analogue of StrictMode's `KnownThirdPartyViolation`).

**The crash is suppressed under instrumentation** (`isRunningUnderInstrumentation()`, reflection on
the androidTest-only `androidx.test`): instrumented runs keep the diagnostic **log** but not the kill.
Slow-frame jank stays log-only everywhere; its regression gate is the Macrobenchmark `FrameTimingMetric`
(ADR 0015), not this.

## Rejected alternatives

- **Gate on `isJank`** — JankStats defines jank as ~2× the refresh interval (any frame over ~33 ms at
  60 Hz). Debug builds jank routinely; crashing on it would fire on nearly every interaction
  (driver 2). `isJank` is the right signal for the *log* path, not the gate.
- **Gate on total frame duration** (the Android Vitals "frozen > 700 ms" definition, which includes
  GPU/composition) — GPU-bound slowness isn't a main-thread block; crashing for it is a false positive
  (driver 3). `frameDurationUiNanos` (UI/CPU-centric; `frameDurationCpuNanos` is its API-24+
  refinement) is the correct signal.
- **Tolerance = 1** — a single 700 ms frame is too often an environmental hiccup (GC, thermal);
  crashing on it reintroduces the flakiness driver 2 forbids.
- **Arm under instrumentation** ("the existing pre-PR suite becomes the gate for free") — empirically
  does not survive the test emulator. A degraded cold-boot AVD (routine, per
  [ADR 0001](0001-local-ui-test-suite.md)) emits multi-second frozen frames from its own starvation
  (system_server ANR, Choreographer skipping hundreds of frames) — observed up to 3.5 s — which no
  per-frame threshold can tell apart from a real block. A real device produces none in the same flows
  (verified on a Pixel 8: zero frozen frames, gate never fired, across the full suite). So the "free
  coverage" idea is dropped; the gate's home is manual / real-device debug.

## Consequences

- The crash fires in manual / real-device debug use; the instrumented suite is **not** the gate (it
  keeps the log). This deliberately narrows PR #1202's original "debug + instrumented" scope.
- A real main-thread block introduced on any screen the developer exercises by hand on a real device
  fails loudly, like a StrictMode violation — without scripting a benchmark for that screen.
- Calibration is emulator-sensitive; the per-screen window / sustained tolerance / allowlist are the
  knobs. A frozen-frame crash on real hardware is triaged like a StrictMode violation (CONTRIBUTING.md
  § *Performance → Frozen-frame crash gate*): fix the prod block, scope-allow a known-legit render via
  the allowlist, or — last resort — tune the constants.
- **Revisit if:** real-device CI becomes available (instrumented arming could return); Vitals
  total-duration semantics are wanted (`frameDurationUiNanos` → `FrameDataApi31.frameOverrunNanos`,
  accepting GPU causes); or per-phase attribution (`list_scroll`, `playback`) is added (production
  JankStats, deferred).

## Invariants

Debug-only **by source set** — `FrozenFrameGate` / `JankStatsLogger` live in `app/src/debug`, the
dependency is `debugImplementation`; neither can reach a release build, so **no grep guard is needed**
(the source-set boundary is the fence, the same reason `StrictModeConfigurator` has none). The gate's
calibration is fenced by the `FrozenFrameGateTest` unit suite, not a grep.

## Cross-references

- [ADR 0001](0001-local-ui-test-suite.md) (the instrumented suite this gate deliberately does **not**
  arm in) and [ADR 0015](0015-macrobenchmark-seeding-architecture.md) (the slow-frame regression gate
  this complements).
- `CLAUDE.md` § *JankStats frozen-frame gate* and § *StrictMode debug audit* (the parallel fail-loud
  debug gate).
- `CONTRIBUTING.md` § *Performance* (the tool-trio orientation + triage).
- JankStats: https://developer.android.com/topic/performance/jankstats ; render / frozen frames:
  https://developer.android.com/topic/performance/vitals/render
- PR #1202.
