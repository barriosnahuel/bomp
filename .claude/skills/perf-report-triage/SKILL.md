---
name: perf-report-triage
description: Triage a weekly Firebase Performance alarm (cold-start or frozen/slow-frame "regression" across releases) before assuming it's a code bug. Segments the field aggregate by device to separate a real same-device regression from a single-device / small-sample artifact. Use when a perf report flags a version-over-version slowdown.
---

# perf-report-triage

Codified procedure for the **first 10 minutes** of any "release X is slower than release Y" alarm from
the weekly Bomp performance report. The report aggregates **raw means over a tiny sample** (~24 cold
starts / ~60 screen traces per version, 60-day retention). A single bad-actor device or a post-update
install window can manufacture a 4× "regression" that **does not exist on any real device**. This skill
de-confounds the aggregate **before** anyone builds, profiles, or "fixes" code.

Worked precedent that motivated this skill: `docs/perf-investigations/0001-2.2.0-startup-false-alarm.md`
(a reported 4× cold-start + 10× frozen-frame regression that was entirely one low-tier device over ~2 days).

## When to use

- The weekly report flags a version-over-version cold-start (`_app_start`) or frame (slow/frozen)
  regression.
- Before scoping a perf fix on a hunch. **Confirm the regression is real and same-device first.**

## Prerequisites

- BigQuery access to `bomp-prod` (the `firebase_performance` dataset) — via the BigQuery MCP
  (`execute_sql_readonly`) or the `bq` CLI (`CONTRIBUTING.md` § *BigQuery export*).
- Field data is **release-only** — see `CONTRIBUTING.md` § *BigQuery export* (`bomp-debug` doesn't export).

## Procedure

### 1. Find the table + the versions in play
- Dataset `bomp-prod.firebase_performance`, single table:
  `com_github_barriosnahuel_vossosunboton_ANDROID`. (If renamed, `list_table_ids` to rediscover.)
- `app_build_version` = versionCode (e.g. `'6'`, `'7'`); `app_display_version` = versionName.
- Note the sample size — **if N per version is < ~50, treat every aggregate as fragile** and never
  trust a raw mean.

### 2. Cold start — segment, don't average
The report's "mean cold start" is the trap. Run the **same-device** comparison and an outlier-excluded
mean in one shot (replace the codes):

```sql
WITH s AS (
  SELECT app_build_version AS code, device_name, trace_info.duration_us/1000.0 AS ms
  FROM `bomp-prod.firebase_performance.com_github_barriosnahuel_vossosunboton_ANDROID`
  WHERE event_type='DURATION_TRACE' AND event_name='_app_start'
    AND app_build_version IN ('6','7'))
SELECT code, COUNT(*) n,
       ROUND(AVG(ms),0) mean_ms,
       ROUND(APPROX_QUANTILES(ms,2)[OFFSET(1)],0) median_ms
FROM s GROUP BY code ORDER BY code;
```

Then the **decisive** query — per device model, per version (a model in BOTH versions is the only
apples-to-apples signal):

```sql
SELECT device_name, app_build_version AS code, COUNT(*) n,
       ROUND(AVG(trace_info.duration_us/1000.0),0) mean_ms
FROM `bomp-prod.firebase_performance.com_github_barriosnahuel_vossosunboton_ANDROID`
WHERE event_type='DURATION_TRACE' AND event_name='_app_start' AND app_build_version IN ('6','7')
GROUP BY device_name, code ORDER BY device_name, code;
```

If one `device_name` carries most of the slow tail, re-run the mean with
`AVG(IF(device_name='<outlier>',NULL,ms))` to show the aggregate without it.

### 3. Frozen / slow frames — same move
Frozen = total frame ≥ 700 ms (incl GPU), **not** the debug gate's UI-thread metric (ADR 0016) — so a
field frozen frame can be GPU-bound, not a main-thread block.

```sql
WITH s AS (
  SELECT app_build_version AS code, device_name,
         trace_info.screen_info.frozen_frame_ratio frozen,
         trace_info.screen_info.slow_frame_ratio slow
  FROM `bomp-prod.firebase_performance.com_github_barriosnahuel_vossosunboton_ANDROID`
  WHERE event_type='SCREEN_TRACE' AND event_name='_st_LandingActivity'
    AND app_build_version IN ('6','7'))
SELECT code, COUNT(*) n, ROUND(AVG(frozen),3) mean_frozen, ROUND(AVG(slow),3) mean_slow
FROM s GROUP BY code ORDER BY code;
```
Then list `WHERE frozen_frame_ratio > 0` with `device_name` + `DATE(event_timestamp)` — clustering on
one device / a 2-day window after a rollout points to **install-time** (ART pre-AOT, before Play
compiles the Baseline Profile), not a code regression.

### 4. Verdict — apply these rules
- **Same device flat/better across versions ⇒ NOT a code regression.** The aggregate moved because the
  device-mix or sample moved.
- **A single device with a tight post-rollout window ⇒ install-time / cold-ART artifact**, transient and
  self-healing; the Baseline Profile is what mitigates it (watch for a slow device *improving*
  version-over-version — that's the profile working).
- **Same device regresses on the SAME model ⇒ real.** Only then go on-device: `:macrobenchmark`
  (StartupBenchmark / ScrollBenchmark) + Perfetto (`CONTRIBUTING.md` § *Performance*). For frozen-frame
  root-cause, inspect the UI thread in Perfetto — the field metric includes GPU.
- **No cold/warm/hot dimension exists** in this schema (`_app_start` is a single trace); don't claim a
  start-type shift you can't see.

### 5. Record it (privacy split)
- Add a record under `docs/perf-investigations/` (public conclusion) and put the raw per-device rows in
  the private counterpart. **The public/private split rule is canonical in
  `docs/perf-investigations/README.md` — follow it there** (don't restate it).
- If the alarm was a false positive, also note whether the **report itself** should switch from a raw
  mean to a median / per-device view to stop manufacturing alarms.
