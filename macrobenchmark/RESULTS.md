# Macrobenchmark results log

Append-only history of on-device benchmark runs — one row per run, newest last. This file is the
"before / today / tomorrow" series for the manual gates (the raw JSON/Perfetto outputs under
`macrobenchmark/build/outputs/` are gitignored and ephemeral). Add a row on every pre-release run
(CONTRIBUTING § *Pre-release checklist*) and whenever a playback-engine / tap-path change is
validated (§ *Performance → What it measures*).

| Date | Device | Branch / context | Benchmark | Min (ms) | Median (ms) | Max (ms) | Iterations |
|---|---|---|---|---|---|---|---|
| 2026-07-04 | Pixel 8 (API 16/36) | `test/tap-latency-guardrail` — baseline pre-Media3, MediaPlayer engine | TapLatencyBenchmark.tapToSoundFirstTap | 31.3 | 53.5 | 80.9 | 5 |
