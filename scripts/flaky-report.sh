#!/usr/bin/env bash
#
# Reads the local instrumented run history and answers the question you actually
# have when the suite goes red: **is this a known flaky, or did I just break it?**
#
# Without history that question is unanswerable, so it gets answered by vibes — and
# the two wrong answers are both expensive. Call a real regression "flaky", re-run
# it until it's green, and ship the bug. Call a flaky a "regression" and lose an
# afternoon debugging a test that was already lying to you last week.
#
# The taxonomy here deliberately mirrors the one in CONTRIBUTING § *Is the red the
# emulator, or a real bug?* rather than inventing a second, competing one:
#
#   flaky        — passed AND failed within a single commit. That is the only thing
#                  that proves non-determinism: the code was identical across the
#                  pass and the fail, so the test itself is unreliable.
#   consistent   — failed every run of the commit it most recently failed on (and
#                  that commit was run more than once). NOT flaky. Re-running will
#                  not help; this is a real red.
#   unknown      — seen failing, but only once for that commit. Not enough to tell
#                  the two apart, and saying either would be a guess with a cost.
#
# **The partition by commit is the whole algorithm.** Pooling runs across commits is
# not a simplification, it is the bug: a test that was green for a month and that you
# broke today has "passed on other runs", so a pooled view calls it FLAKY and invites
# you to re-run a real regression until it goes green. The comparison is only
# meaningful between runs of the *same code*.
#   stalls       — runs that never finished. Grouped by where they died and whether
#                  the HOST had gone to sleep — because that turned out to be the
#                  cause of the original "the emulator hangs past test 100"
#                  (ADR 0001 § Bounded termination). A stall is not a test result:
#                  the tests after it never ran, so they cannot be judged.
#   slowest      — per-test p95. This is what says whether STALL_TIMEOUT_SECONDS
#                  still has headroom over the real suite, so it stays honest as
#                  tests are added.
#
# Scope: the instrumented suite only. The known JVM flakies (SoundsViewModelSearch's
# vault-flip race, SoundsViewModelVisibility's timeout) live in `./gradlew test` and
# are not recorded here.
#
# Usage:
#   ./scripts/flaky-report.sh           # all recorded runs
#   ./scripts/flaky-report.sh 10        # only the last 10
#
set -uo pipefail

LAST_N="${1:-0}"

# shellcheck source=scripts/instrumented-history-dir.sh
. "$(dirname "${BASH_SOURCE[0]}")/instrumented-history-dir.sh"
HISTORY_DIR="$(resolve_instrumented_history_dir)" || true
if [ -z "$HISTORY_DIR" ]; then
  echo "✘ not in a git repo — cannot locate the run history" >&2
  exit 1
fi

RUNS_JSONL="$HISTORY_DIR/runs.jsonl"

if [ ! -f "$RUNS_JSONL" ]; then
  echo "No run history yet at $HISTORY_DIR."
  echo "It fills up on its own: every ./scripts/run-instrumented-tests.sh appends to it."
  exit 0
fi

# Flat, single-line JSON by construction (see record-instrumented-run.sh), so a
# field can be pulled with sed without a JSON parser — the CI image has no jq.
field() { echo "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p"; }
num_field() { echo "$1" | sed -n "s/.*\"$2\":\([0-9-]*\).*/\1/p"; }

# The reds of a run, straight out of its ledger line, as `test<TAB>exception`. The
# ledger is the long-term source here on purpose: artefacts are pruned at 30 runs, so
# reading the reds from disk would quietly shorten the flaky memory to the last month.
# `has_failures_key` distinguishes "this run had no reds" from "this line predates the
# field" — the latter still has its reds on disk, and dropping them would rewrite
# history as greener than it was.
has_failures_key() { case "$1" in *'"failures":'*) return 0 ;; *) return 1 ;; esac; }
failures_of() {
  echo "$1" |
    sed -n 's/.*"failures":\[\(.*\)\],"last_test".*/\1/p' |
    tr '}' '\n' |
    sed -n 's/.*"test":"\([^"]*\)","exception":"\([^"]*\)".*/\1	\2/p'
}

runs_file="$(mktemp)"
trap 'rm -f "$runs_file"' EXIT
if [ "$LAST_N" -gt 0 ] 2>/dev/null; then
  tail -n "$LAST_N" "$RUNS_JSONL" >"$runs_file"
else
  cp "$RUNS_JSONL" "$runs_file"
fi

total_runs=$(wc -l <"$runs_file" | tr -d ' ')
[ "$total_runs" -eq 0 ] && { echo "No runs recorded yet."; exit 0; }

passes=0
test_failures=0
stalls=0
infra=0
boot=0
truncated=0
truncated_worst=""
while IFS= read -r line; do
  outcome="$(field "$line" outcome)"
  case "$outcome" in
    pass) passes=$((passes + 1)) ;;
    failure) test_failures=$((test_failures + 1)) ;;
    stall) stalls=$((stalls + 1)) ;;
    infra) infra=$((infra + 1)) ;;
    boot) boot=$((boot + 1)) ;;
  esac

  # A run that reported fewer tests than the runner announced did not finish the suite:
  # the instrumentation process died under a test (a StrictMode kill, a native crash) and
  # everything after it never ran. It exits 1 like any red, so nothing else says so — and
  # a "1 failure" that silently skipped 110 tests reads far greener than it was.
  case "$outcome" in
    pass | failure)
      t="$(num_field "$line" tests)"
      tt="$(num_field "$line" test_total)"
      if [ "${t:-0}" -gt 0 ] && [ "${tt:-0}" -gt 0 ] && [ "$t" -lt "$tt" ]; then
        truncated=$((truncated + 1))
        [ -z "$truncated_worst" ] && truncated_worst="$t/$tt"
      fi
      ;;
  esac
done <"$runs_file"

echo "════════════════════════════════════════════════════════════════"
echo " Instrumented suite health — last $total_runs run(s)"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "  (every red is kept forever in the ledger; the artefacts behind them — XML,"
echo "   heartbeat, logcat — are pruned to the newest 30 runs)"
echo ""
printf "  %-22s %s\n" "green" "$passes"
printf "  %-22s %s\n" "red (test failures)" "$test_failures"
printf "  %-22s %s\n" "stalled (never ended)" "$stalls"
printf "  %-22s %s\n" "build/install failed" "$infra"
printf "  %-22s %s\n" "emulator never booted" "$boot"
echo ""
if [ "$truncated" -gt 0 ]; then
  printf "  ⚠  %s run(s) never finished the suite (one recorded only %s tests): the\n" "$truncated" "$truncated_worst"
  echo "     instrumentation process died under a test and everything after it never ran."
  echo "     Those runs prove nothing about the tests they never reached."
  echo ""
fi

# ── per-test reliability ───────────────────────────────────────────────────
# A test is only judged against the runs that actually reached it: a run that
# stalled at test 26 says nothing about test 90, and counting it as a "pass" for
# test 90 would quietly inflate everyone's reliability. So the denominator is the
# runs whose artefacts recorded that test at all.
appearances="$(mktemp)"
failures="$(mktemp)"
trap 'rm -f "$runs_file" "$appearances" "$failures"' EXIT

while IFS= read -r line; do
  ts="$(field "$line" ts)"
  sha="$(field "$line" sha)"
  outcome="$(field "$line" outcome)"
  run_dir="$HISTORY_DIR/$(field "$line" artefacts)"

  # A run that never executed tests contributes NO evidence about any test. Counting
  # its (stale, left over from the previous run) XML as "everyone passed" would
  # fabricate passes and dilute a real red — the exact opposite of the point.
  case "$outcome" in
    pass | failure) ;;
    *) continue ;;
  esac

  # The reds: from the ledger, so they outlive retention. Pre-`failures` lines fall
  # back to the artefact they still point at.
  if has_failures_key "$line"; then
    reds="$(failures_of "$line")"
  else
    reds="$(cat "$run_dir/failures.txt" 2>/dev/null)"
  fi
  if [ -n "$reds" ]; then
    while IFS=$'\t' read -r test kind; do
      [ -z "$test" ] && continue
      printf '%s\t%s\t%s\t%s\n' "$test" "${kind:-unknown}" "$ts" "$sha" >>"$failures"
    done <<EOF
$reds
EOF
  fi

  # The denominator — which runs actually reached which test — can only come from the
  # artefacts, so it is the one figure that narrows as they are pruned. A dangling
  # reference is normal, not corruption: the reds above still count, and the awk below
  # floors the denominator at the number of failures, so a pruned run never turns a
  # known red into a division by zero.
  if [ -f "$run_dir/durations.tsv" ]; then
    cut -f1 "$run_dir/durations.tsv" 2>/dev/null | sort -u |
      while IFS= read -r t; do printf '%s\t%s\n' "$t" "$sha"; done >>"$appearances"
  fi
done <"$runs_file"

if [ -s "$failures" ]; then
  echo "──────────────────────────────────────────────────────────────"
  echo " Failing tests"
  echo "──────────────────────────────────────────────────────────────"
  echo ""
  # Verdicts are decided PER COMMIT and then summarised, because only same-commit
  # evidence can distinguish a flaky test from one you just broke.
  #   FLAKY       — some commit shows both a pass and a fail. Non-determinism, proven.
  #   CONSISTENT  — the commit it most recently failed on ran >1 time and it failed
  #                 every time. Re-running will not help.
  #   UNKNOWN     — only one run of that commit. Not enough to say, so it doesn't.
  #
  # `-v marker` rather than the NR==FNR idiom: NR==FNR is true for the first record
  # of the *second* file whenever the first file is empty, which silently eats a
  # failure and turns every remaining verdict into a bogus CONSISTENT.
  awk -F'\t' -v app_file="$appearances" '
    FILENAME == app_file { seen[$1 SUBSEP $2]++; next }
    {
      t = $1; kind = $2; ts = $3; sha = $4
      fails[t SUBSEP sha]++
      total_fails[t]++
      kind_count[t SUBSEP kind]++
      if (kind_count[t SUBSEP kind] > best_kind_n[t]) { best_kind_n[t] = kind_count[t SUBSEP kind]; kind_of[t] = kind }
      if (first[t] == "") first[t] = ts
      last[t] = ts
      if (ts > last_fail_ts[t]) { last_fail_ts[t] = ts; last_fail_sha[t] = sha }
      shas[t SUBSEP sha] = 1
    }
    END {
      for (key in fails) {
        split(key, p, SUBSEP)
        t = p[1]; sha = p[2]
        runs = seen[t SUBSEP sha]
        # A failure always implies the test ran, even if durations.tsv missed it.
        if (runs < fails[key]) runs = fails[key]
        if (fails[key] > 0 && fails[key] < runs) proven_flaky[t] = 1
        if (sha == last_fail_sha[t]) { last_runs[t] = runs; last_fails[t] = fails[key] }
      }
      for (t in total_fails) {
        if (proven_flaky[t]) {
          verdict = "FLAKY"
        } else if (last_runs[t] > 1 && last_fails[t] == last_runs[t]) {
          verdict = "CONSISTENT"
        } else {
          verdict = "UNKNOWN"
        }
        printf "%s\t%d/%d\t%s\t%s\t%s\t%s\t%s\n", verdict, last_fails[t], last_runs[t], \
          substr(last_fail_sha[t], 1, 8), t, kind_of[t], first[t], last[t]
      }
    }
  ' "$appearances" "$failures" | sort | while IFS=$'\t' read -r verdict ratio sha test kind first last; do
    case "$verdict" in
      FLAKY)
        printf "  🟡 FLAKY       %s\n" "$test"
        printf "     %s · %s runs of %s · first seen %s · last %s\n" "$kind" "$ratio" "$sha" "${first%%T*}" "${last%%T*}"
        printf "     → it passed AND failed on the same commit. Non-deterministic; re-running is legitimate.\n\n"
        ;;
      CONSISTENT)
        printf "  🔴 CONSISTENT  %s\n" "$test"
        printf "     %s · failed %s runs of %s · first seen %s\n" "$kind" "$ratio" "$sha" "${first%%T*}"
        printf "     → failed every run of that commit. Re-running will NOT help: fix the test or the code.\n\n"
        ;;
      *)
        printf "  ⚪ UNKNOWN     %s\n" "$test"
        printf "     %s · failed %s runs of %s · last %s\n" "$kind" "$ratio" "$sha" "${last%%T*}"
        printf "     → only one run of that commit. Re-run it: that is what tells a flaky from a regression.\n\n"
        ;;
    esac
  done
else
  echo "  ✓ no failing tests in this window"
  echo ""
fi

# ── stalls ─────────────────────────────────────────────────────────────────
if [ "$stalls" -gt 0 ]; then
  echo "──────────────────────────────────────────────────────────────"
  echo " Stalls — runs that never finished"
  echo "──────────────────────────────────────────────────────────────"
  echo ""
  host_sleep_stalls=0
  while IFS= read -r line; do
    [ "$(field "$line" outcome)" = "stall" ] || continue
    ts="$(field "$line" ts)"
    lt="$(field "$line" last_test)"
    idx="$(num_field "$line" test_index)"
    tot="$(num_field "$line" test_total)"
    slept="$(num_field "$line" host_slept_s)"
    if [ "${slept:-0}" -gt 0 ]; then
      host_sleep_stalls=$((host_sleep_stalls + 1))
      printf "  💤 %s — died at %s/%s (%s)\n" "${ts%%T*}" "$idx" "$tot" "$lt"
      printf "     THE HOST SLEPT ~%ss. Not an emulator bug — check your power settings.\n\n" "$slept"
    else
      printf "  ⏱  %s — died at %s/%s (%s)\n\n" "${ts%%T*}" "$idx" "$tot" "$lt"
    fi
  done <"$runs_file"

  # The diagnosis that matters: is the hang bound to a test, or to elapsed time?
  # A test-bound hang means a specific test or leak; a time-bound one means the
  # environment (the host sleeping, cumulative guest degradation) and no amount of
  # staring at the test it "died on" will help, because that test is arbitrary.
  # Stalls that died BEFORE the runner announced a test carry the '(none yet)' placeholder
  # as last_test. Those are boot/build-time hangs with no test to blame, so they must be
  # counted apart — folding them in would let '(none yet)' pose as "the one test they all
  # died on" and send you chasing a test that never ran.
  stall_tests="$(grep '"outcome":"stall"' "$runs_file" | sed -n 's/.*"last_test":"\([^"]*\)".*/\1/p')"
  before_any=$(printf '%s\n' "$stall_tests" | grep -c '^(none yet)$')
  in_test=$(printf '%s\n' "$stall_tests" | grep -v '^(none yet)$' | grep -c .)
  distinct=$(printf '%s\n' "$stall_tests" | grep -v '^(none yet)$' | sort -u | grep -c .)
  echo "  Diagnosis:"
  if [ "$host_sleep_stalls" -gt 0 ]; then
    echo "    $host_sleep_stalls of $stalls stall(s) happened while the HOST was asleep — the emulator was never at fault."
  fi
  if [ "$before_any" -gt 0 ]; then
    echo "    $before_any stall(s) died before any test started (boot/build) → not test-bound; look at the environment."
  fi
  if [ "$distinct" -eq 1 ] && [ "$in_test" -gt 1 ]; then
    echo "    All in-test stalls died on the SAME test → test-bound. Suspect that test or a leak it triggers."
  elif [ "$in_test" -gt 1 ]; then
    echo "    In-test stalls died on $distinct DIFFERENT tests → time-bound, not test-bound. The test it died on is"
    echo "    incidental; suspect the environment (host sleep, cumulative guest degradation)."
  fi
  echo ""
fi

# ── slowest tests ──────────────────────────────────────────────────────────
# This is what keeps STALL_TIMEOUT_SECONDS honest. The clock must stay comfortably
# above the slowest legitimate test, or a slow-but-honest test reads as a hang —
# the one failure mode that would make the watchdog worse than the bug it fixes.
all_durations="$(mktemp)"
trap 'rm -f "$runs_file" "$appearances" "$failures" "$all_durations"' EXIT
red_keys="$(mktemp)"
trap 'rm -f "$runs_file" "$appearances" "$failures" "$all_durations" "$red_keys"' EXIT
while IFS= read -r line; do
  art="$(field "$line" artefacts)"
  durations="$HISTORY_DIR/$art/durations.tsv"
  [ -f "$durations" ] || continue

  # A red test's `time=` is not a duration, it is whatever clock ran out: a test killed
  # by `timeout_msec` reports a flat 300s. Left in, it becomes the "slowest test" — and
  # since that figure is what calibrates STALL_TIMEOUT_SECONDS, the timeout would be
  # calibrated against itself. Dropped per RUN, not globally: a test that deadlocks
  # sometimes and finishes in 4s the rest of the time still has an honest duration, and
  # it is worth knowing.
  if has_failures_key "$line"; then
    failures_of "$line" | cut -f1 >"$red_keys"
  else
    cut -f1 "$HISTORY_DIR/$art/failures.txt" 2>/dev/null >"$red_keys"
  fi
  if [ -s "$red_keys" ]; then
    awk -F'\t' -v red_file="$red_keys" '
      FILENAME == red_file { red[$1] = 1; next }
      !($1 in red)
    ' "$red_keys" "$durations" >>"$all_durations"
  else
    cat "$durations" >>"$all_durations"
  fi
done <"$runs_file"

if [ -s "$all_durations" ]; then
  echo "──────────────────────────────────────────────────────────────"
  echo " Slowest tests (p95 across runs) — the headroom on STALL_TIMEOUT_SECONDS"
  echo "──────────────────────────────────────────────────────────────"
  echo ""
  awk -F'\t' '
    { v[$1] = v[$1] " " $2 }
    END {
      for (t in v) {
        n = split(v[t], a, " ")
        # a[1] is the empty leading field from the join above.
        c = 0
        for (i = 1; i <= n; i++) if (a[i] != "") { d[++c] = a[i] + 0 }
        if (c == 0) continue
        for (i = 1; i < c; i++) for (j = i+1; j <= c; j++) if (d[j] < d[i]) { tmp=d[i]; d[i]=d[j]; d[j]=tmp }
        # Nearest-rank p95 = ceil(0.95 * n), not floor: with a handful of runs, floor
        # rounds a two-sample [5s, 300s] down to the 5s and hides the test that ran a
        # breath from the stall timeout — the exact thing this table exists to surface.
        r = 0.95 * c
        idx = int(r); if (idx < r) idx++
        if (idx < 1) idx = 1
        if (idx > c) idx = c
        printf "%.1f\t%s\n", d[idx], t
      }
    }
  ' "$all_durations" | sort -rn | head -8 | while IFS=$'\t' read -r secs test; do
    printf "  %6ss  %s\n" "$secs" "$test"
  done
  echo ""
  # Only tests that finished are in here (see record-instrumented-run.sh) — a red's
  # `time=` is the clock that ran out, not work done.
  slowest=$(awk -F'\t' '{ if ($2+0 > m) m = $2+0 } END { printf "%.0f", m }' "$all_durations")
  # Parsed, not hardcoded: two copies of this number would drift, and the one that
  # drifts is always the one in the report nobody re-reads.
  stall_default="$(sed -n 's/^STALL_TIMEOUT_SECONDS="${STALL_TIMEOUT_SECONDS:-\([0-9]*\)}"/\1/p' \
    "$(dirname "$0")/run-instrumented-tests.sh" 2>/dev/null)"
  stall_default="${stall_default:-420}"
  per_test_timeout="$(sed -n "s/.*timeout_msec: '\([0-9]*\)'.*/\1/p" \
    "$(dirname "$0")/../app/build.gradle" 2>/dev/null)"
  per_test_timeout=$((${per_test_timeout:-300000} / 1000))
  echo "  Slowest honest test observed: ${slowest}s. STALL_TIMEOUT_SECONDS: ${stall_default}s."
  if [ "${slowest:-0}" -gt 0 ] && [ "$slowest" -lt "$stall_default" ]; then
    echo "  Headroom over the suite: $((stall_default / slowest))×."
  elif [ "${slowest:-0}" -ge "$stall_default" ]; then
    echo "  ⚠  A legitimate test now outlasts the stall clock. Raise STALL_TIMEOUT_SECONDS,"
    echo "     or the next slow-but-honest run is killed as a hang."
  fi
  # The binding constraint is not the suite, it is the per-test timeout: a test
  # deadlocked on-device goes silent for exactly that long before the runner names it.
  if [ "$stall_default" -le "$per_test_timeout" ]; then
    echo "  ⚠  STALL_TIMEOUT_SECONDS (${stall_default}s) does not clear timeout_msec (${per_test_timeout}s)."
    echo "     The watchdog will kill the run blind just as the runner was about to name"
    echo "     the deadlocked test. Keep the stall clock above the per-test timeout."
  fi
  echo ""
fi

echo "  Raw history: $HISTORY_DIR"
