#!/usr/bin/env bash
#
# Single source of truth for WHERE the instrumented run history lives, sourced by both
# the writer (record-instrumented-run.sh) and the reader (flaky-report.sh). Kept in one
# place on purpose: if the two resolved it independently and the rule ever changed (a
# worktree-layout change under ADR 0014, say), a maintainer could update one and not the
# other, and the reader would silently read an empty history while runs pile up elsewhere.
#
# `resolve_instrumented_history_dir` echoes the directory and returns 0, or echoes nothing
# and returns 1 when it can't (not in a git repo). The divergent handling of that failure
# stays at the call site — best-effort for the writer, fatal for the reader — because only
# the caller knows whether losing the history should abort.
#
# The history lives in the PRIMARY worktree (via `git rev-parse --git-common-dir`), so runs
# launched from sibling worktrees (`../push-me-<topic>`, ADR 0014) all append to the same
# ledger instead of fragmenting it. BOMP_HISTORY_DIR overrides it, so tests can point the
# whole thing at a scratch dir.
resolve_instrumented_history_dir() {
  if [ -n "${BOMP_HISTORY_DIR:-}" ]; then
    printf '%s\n' "$BOMP_HISTORY_DIR"
    return 0
  fi
  local common_dir primary
  common_dir="$(git rev-parse --git-common-dir 2>/dev/null)" || return 1
  primary="$(cd "$common_dir/.." 2>/dev/null && pwd)" || return 1
  printf '%s\n' "$primary/.instrumented-history"
}
