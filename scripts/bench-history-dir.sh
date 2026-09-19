#!/usr/bin/env bash
#
# Single source of truth for WHERE preserved benchmark runs live, sourced by run-tap-latency.sh.
# Mirrors instrumented-history-dir.sh, and exists for the same reason: the rule has more than one
# consumer over time (the runner today, whatever reads the runs later), and two copies drift.
#
# What lands there is not a ledger — it is the raw `benchmarkData.json` of each run, copied out of
# `macrobenchmark/build/outputs/`, which the NEXT run overwrites. Without this copy a comparison is
# impossible by construction: you can never hold two runs at once. It also keeps the device-state
# files the runner needs across invocations (the known-good stay-awake value, the last OS build id).
#
# It lives in the PRIMARY worktree (via `git rev-parse --git-common-dir`) so runs launched from
# sibling worktrees — which is how a baseline tag gets measured (ADR 0014) — all land in one place
# instead of fragmenting across checkouts. BOMP_BENCH_HISTORY_DIR overrides it for tests.
resolve_bench_history_dir() {
  if [ -n "${BOMP_BENCH_HISTORY_DIR:-}" ]; then
    printf '%s\n' "$BOMP_BENCH_HISTORY_DIR"
    return 0
  fi
  local common_dir primary
  common_dir="$(git rev-parse --git-common-dir 2>/dev/null)" || return 1
  primary="$(cd "$common_dir/.." 2>/dev/null && pwd)" || return 1
  printf '%s\n' "$primary/.bench-history"
}
