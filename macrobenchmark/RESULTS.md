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

**Reading the `Max` column:** `StartupMode.COLD` makes **iteration 1 a warmup outlier** — it is the
`Max` in every run so far. A 30-iteration run on the same build measured **263.4 ms on the first sample
and 35–64 ms on the other 29** (p95 = 63.5 ms; the only sample over 100 ms was the first). Its median,
49.9 ms, matches the 15-iteration median above (49.0 ms) — which is why `DEFAULT_ITERATIONS` is 15.
**Read the median.** A high `Max` here is warmup, not a latency regression.
