# ADR 0015 — Macrobenchmark module + benchmark-variant synthetic corpus seeding

- **Status:** Accepted
- **Date:** 2026-06-07
- **Supersedes:** —

## Context

The LandingActivity jank investigation needs **on-device, repeatable** measurement: Firebase
Performance reports the entry screen at ~24.8% slow frames but cannot attribute jank to a code path
or a phase. A `:macrobenchmark` module (`com.android.test`) was added (#1201) with a cold-start
`StartupTimingMetric` and a scroll `FrameTimingMetric`, targeting the app's release-like `benchmark`
build type so numbers match what users get.

The scroll benchmark must answer hypothesis **H2 — does scroll jank scale with the number of
sounds?** That needs a **controlled corpus of exactly N** (few / medium / many). Seeding N synthetic
sounds into the release-like `benchmark` build ran into three non-obvious constraints (each caused a
real bug caught in review before merge):

1. **`SoundsRepository.delete()` is disk-coupled** — it removes a row from the store only if the
   audio file was deleted from disk. File-less synthetic rows can never satisfy that, so a
   per-item `save`/`delete` seeder can grow the corpus but never **shrink** it. With JUnit's
   hash-based method order, a "5-item" run could then measure a stale 200-item list.
2. **Incremental seeding pollutes the metric** — N separate `save()` writes emit N times, recomposing
   the list mid-measurement and mixing seeding work into the frame timing.
3. **The scroll render never reads the file** — the row renders from the *cached* duration map and the
   `isBundled()` branch (a non-null `file` already routes to the "custom sound" path). So real audio
   files do **not** make the render measurement more faithful; only a populated cached duration does.

The debug source set already has a seeder (`DebugSoundSeeder`/`DebugSeedCorpus`), but it is not
compiled into the `benchmark` build type, seeds a fixed corpus (not a parameterised N), and the debug
build's `debuggable=true` would distort the numbers.

## Decision drivers

1. **Release-like numbers.** Measure on the non-debuggable, minified `benchmark` build, not debug.
2. **Deterministic exact-N before measurement.** The measured scroll must traverse exactly N items,
   with no stale or half-seeded state and no seeding work overlapping the metric.
3. **Render fidelity.** Seeded rows must do the same per-frame work as a real sound (which is cached
   duration + the custom-sound branch — not file I/O).
4. **Minimal blast radius, zero production-data risk.** No `src/debug` changes (the screenshot corpus
   depends on it); nothing reaches release; benchmark runs never touch `bomp-prod` Firebase.

## Decision

- The `:macrobenchmark` module targets the app's **`benchmark`** build type (`initWith release`,
  non-debuggable, profileable via `app/src/benchmark/AndroidManifest.xml`). Firebase config for that
  build type is a **scrubbed dummy** (`app/src/benchmark/google-services.json`) so benchmark runs
  never report to `bomp-prod`.
- Seeding is triggered by a launch-intent extra and handled by the **benchmark build type's**
  `CustomBuildTypeApplication` (`app/src/benchmark/…`), which overrides the release source set's
  no-op `seedDebugSoundsIfRequested` seam — reusing the same `(application as? …)` seam
  `LandingActivity` already calls. The synthetic seeding therefore exists only in the benchmark APK.
- Seeding runs **synchronously** (`runBlocking`) in `onCreate`. Because the benchmark launch intent
  carries no data URI, `handleDeeplink` early-returns without instantiating the lazy ViewModel, so
  the seed write completes before composition creates the VM and reads the store — the measured
  scroll always sees exactly N.
- Persistence goes through a new atomic primitive
  **`SoundsRepository.replaceSyntheticCorpus(idPrefix, sounds, durationMs)`**: one `mutate` replaces
  every id-prefixed row with exactly the given list, stamping each with `durationMs`. One write ⇒
  exact-N (idempotent, shrinkable), a single recomposition (no metric pollution), and — since sounds
  and their cached duration share one DataStore key — populated durations (render fidelity).

## Options considered (and rejected)

- **Real files for synthetic rows** — the scroll render never reads the file, so this adds I/O and
  file-path coupling without improving fidelity; its only effect would be making the disk-coupled
  `delete()` work, which the atomic primitive makes moot.
- **Target the debug build** — `debuggable=true` distorts the numbers and forces a test-module variant
  matrix (two target applicationIds).
- **Per-item `save()` + `delete()`** — cannot shrink (constraint 1) and pollutes the metric
  (constraint 2).

## Consequences

- The scroll benchmark answers H2 with release-like numbers across 5 / 50 / 200 items.
- `SoundsRepository` gains one **public, benchmark-only** method. It is intentionally **not**
  `@VisibleForTesting(otherwise = NONE)` (unlike `clearForTest` / `setRawJsonForTest`): the caller is
  the benchmark build type in `:app`, a different module, where Lint would reject a test-restricted
  symbol. The grep guard below replaces that compile-time fence.
- `runBlocking` appears in `app/src/benchmark`. **This does not extend [ADR 0004](0004-datastore-sync-api-cache-prime.md).** ADR 0004's
  "do not generalize" scoped exception governs **production** `runBlocking` (`src/main`, enforced by a
  grep over `MAIN_DIRS`). `src/benchmark` is benchmark infra, never shipped, and outside that scope —
  a separate context, not a new entry on 0004's allowlist.
- The seed runs on the main thread in `onCreate`, but only in the `benchmark` build and only during
  setup (unmeasured); the startup benchmark passes no extra, so it is unaffected.

## Invariants

Enforced by `scripts/check-adr-invariants.sh` (CircleCI job `adr-invariants`):

- **`replaceSyntheticCorpus` is benchmark-only.** Its single declaration lives in
  `model/src/main/.../SoundsRepository.kt` and its only call-site is `app/src/benchmark`; the model
  unit test exercises it. Any reference from another production/debug/release source set fails the
  check — that fence stands in for the `@VisibleForTesting` one Lint can't provide cross-module.

## Cross-references

- `CLAUDE.md` § *Project-specific overrides → Threading* and [ADR 0004](0004-datastore-sync-api-cache-prime.md) (the production `runBlocking` rule this is **not** an exception to).
- `CONTRIBUTING.md` § *Performance* (how to run).
- AndroidX Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- PRs #1201 (module + startup/scroll harness) and #1203 (seeded scroll across list sizes).
