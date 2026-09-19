# Macrobenchmark results log

Append-only history of on-device benchmark runs — one row per run, newest last. This file is the
"before / today / tomorrow" series for the manual gates (the raw JSON/Perfetto outputs under
`macrobenchmark/build/outputs/` are gitignored and ephemeral). Add a row on every pre-release run
(CONTRIBUTING § *Pre-release checklist*) and whenever a playback-engine / tap-path change is
validated (§ *Performance → What it measures*).

| Date | Device | Branch / context | Benchmark | Min (ms) | Median (ms) | Max (ms) | Iterations |
|---|---|---|---|---|---|---|---|
| 2026-07-04 | Pixel 8 (API 16/36) | `test/tap-latency-guardrail` — baseline pre-Media3, MediaPlayer engine | TapLatencyBenchmark.tapToSoundFirstTap | 31.3 | 53.5 | 80.9 | 5 |
| 2026-07-13 | Pixel 8 (API 16/36) | `test/tap-latency-seed-pinned` — v2026.07.1 pre-release gate, post-Media3 (tap path still MediaPlayer, ADR 0022) | TapLatencyBenchmark.tapToSoundFirstTap | 38.6 | 49.0 | 140.1 | 15 |
| 2026-09-17 | Pixel 8 (Android 17 / API 37, CP2A.260805.005) | `develop` @ `3b460ef8` — after v2026.08.1 (trimmer, spring motion on SoundItem, listen-session analytics) | TapLatencyBenchmark.tapToSoundFirstTap | 39.8 | 48.9 | 58.2 | 15 |
| 2026-09-17 | Pixel 8 (Android 17 / API 37, CP2A.260805.005) | **control** — `v2026.07.1` (`6200a148`) re-measured the same evening on the same device | TapLatencyBenchmark.tapToSoundFirstTap | 38.2 | 58.1 | 77.7 | 15 |
| 2026-09-19 | Pixel 8 (Android 17 / API 37, CP2A.260805.005) | `develop` @ `558eff77` — first run through `scripts/run-tap-latency.sh` | TapLatencyBenchmark.tapToSoundFirstTap | 33.6 | 46.0 | 68.3 | 15 |
| 2026-09-19 | Pixel 8 (Android 17 / API 37, CP2A.260805.005) | **control** — `v2026.07.1` (`6200a148`) again, two days after the row above | TapLatencyBenchmark.tapToSoundFirstTap | 34.0 | 48.1 | 67.3 | 15 |

**The 2026-09-17 pair is one experiment, not two rows.** The July row above was measured on
**Android 16**; this phone has since moved to **Android 17**. Comparing today's 48.9 ms against it
would have read as a regression — the session in fact opened with a run at 65.9 ms and a suspected
+34%. So `v2026.07.1` was checked out in a sibling worktree and re-measured the same evening on the
same device: **July's code measures 58.1 ms on today's OS**. The delta against the July row is the
platform, not this codebase. **When the device's OS has changed since the row you want to compare
against, re-measure that ref before reading any delta** — that row is otherwise a different bench,
which is why the Device column now carries the OS build id.

**What this bench can and cannot resolve — measured, not estimated.** `v2026.07.1` was measured
twice on the same phone two days apart: **58.1 ms** on the 17th and **48.1 ms** on the 19th. Same
commit, same device, same OS build — **10 ms apart between sessions**. Within a single run the
spread is wider still (the 17th's control ranges 38.2-77.7 ms, CoV 0.20). So a delta of a few ms
between two rows is not a signal, and the 2026-09-19 pair says it plainly: `develop` at 46.0 ms
against July's code at 48.1 ms is **no difference this bench can see**.

Other runs on the 17th landed at 65.9 / 57.3 / 54.4 ms, but `develop` moved across several commits
during that session — including bumps to `androidx.tracing`, which emits the measured span, and
`androidx.benchmark`, which measures it — so those are **not** a same-code spread and are
deliberately not rows. There is no automatic verdict: the runner prints the numbers and a person
compares them.

**Sample count.** `TraceSectionMetric(Mode.First)` reports *nothing* for an iteration whose section
never opened, and the harness summary prints only min/median/max — so a run can quietly take its
median over 12 of its 15 iterations, as the `develop` row above did. `scripts/run-tap-latency.sh`
prints `samples/iterations` for exactly this reason: a run that dropped iterations is a weaker
number, not an invalid one.

**On the 2026-07-04 baseline (53.5 ms):** [ADR 0022](../docs/adr/0022-hybrid-playback-engines-media3.md)
describes that run as "10 iterations each"; the row here is the run's own record and says 5. Read it
as a 5-iteration median — the regime `DEFAULT_ITERATIONS`' KDoc documents as moving 13% between
runs — so treat 53.5 ms as a soft anchor, not a reference value.

**Reading the `Max` column:** `StartupMode.COLD` makes **iteration 1 a warmup outlier** — it is the
`Max` in every run so far. A 30-iteration run on the same build measured **263.4 ms on the first sample
and 35–64 ms on the other 29** (p95 = 63.5 ms; the only sample over 100 ms was the first). Its median,
49.9 ms, matches the 15-iteration median above (49.0 ms) — which is why `DEFAULT_ITERATIONS` is 15.
**Read the median.** A high `Max` here is warmup, not a latency regression.
