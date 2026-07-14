#!/usr/bin/env bash
#
# Appends one instrumented run to the local run history, and archives its artefacts.
#
# Why keep a history at all: a red test tells you nothing on its own. The question
# you actually have — "is this a known flaky, or did I just break something?" — is
# answerable only against the past. Without a record, every red starts that
# argument from zero, and a test that fails one run in five is indistinguishable
# from a test that broke today. scripts/flaky-report.sh reads what this writes.
#
# Where it lives, and why there: the PRIMARY worktree, resolved through
# `git rev-parse --git-common-dir`. Runs launched from sibling worktrees
# (../push-me-<topic>, ADR 0014) therefore append to the SAME history instead of
# each accumulating a private, useless fragment of it. It sits at the repo root
# rather than under build/ so `./gradlew clean` doesn't erase months of evidence,
# and it is gitignored: this is local observation, not a repo artefact — committing
# it would put a merge conflict in the path of every run.
#
# Invoked by scripts/run-instrumented-tests.sh; not meant to be run by hand.
# Input arrives as BOMP_RUN_* env vars so the caller doesn't have to thread a
# dozen positional arguments through.
#
# Behaviour-tested by scripts/test-flaky-report.sh.
#
set -uo pipefail

OUTCOME="${BOMP_RUN_OUTCOME:-unknown}"
DURATION="${BOMP_RUN_DURATION:-0}"
RUN_TMP="${BOMP_RUN_TMP:-}"
LAST_TEST="${BOMP_RUN_LAST_TEST:-}"
TEST_INDEX="${BOMP_RUN_INDEX:-0}"
TEST_TOTAL="${BOMP_RUN_TOTAL:-0}"
HOST_SLEPT="${BOMP_RUN_HOST_SLEPT:-0}"
XML_DIR="${BOMP_RUN_XML_DIR:-}"
# Written by the wrapper when the run starts. Anything in XML_DIR older than this is
# the *previous* run's output — see the freshness check below.
RUN_MARKER="${BOMP_RUN_MARKER:-}"

# The wrapper's counters are "?" until the runner announces `run started:`, which by
# definition never happens on a boot or build failure. Emitted raw, that "?" lands
# unquoted in the JSON (`"test_total":?`) and the line stops being parseable at all —
# so every numeric field is forced to a number before it reaches the ledger.
numeric_or_zero() {
  case "$1" in
    '' | *[!0-9]*) echo 0 ;;
    *) echo "$1" ;;
  esac
}
DURATION="$(numeric_or_zero "$DURATION")"
TEST_INDEX="$(numeric_or_zero "$TEST_INDEX")"
TEST_TOTAL="$(numeric_or_zero "$TEST_TOTAL")"
HOST_SLEPT="$(numeric_or_zero "$HOST_SLEPT")"
# shellcheck source=scripts/instrumented-history-dir.sh
. "$(dirname "${BASH_SOURCE[0]}")/instrumented-history-dir.sh"
# Best-effort: if we can't locate the history, recording must never fail an otherwise-fine run.
HISTORY_DIR="$(resolve_instrumented_history_dir)" || exit 0
[ -n "$HISTORY_DIR" ] || exit 0

RUNS_JSONL="$HISTORY_DIR/runs.jsonl"
# Retention applies to the per-run artefact directories only. runs.jsonl is one
# short line per run and is meant to grow without bound — it IS the long memory,
# and trimming it would silently shorten the window in which a rare flaky is
# visible, which is the one thing this exists to see.
KEEP_RUNS="${BOMP_HISTORY_KEEP_RUNS:-30}"

mkdir -p "$HISTORY_DIR" || exit 0

timestamp="$(date '+%Y-%m-%dT%H-%M-%S')"
run_dir="$HISTORY_DIR/${timestamp}-${OUTCOME}"
mkdir -p "$run_dir" || exit 0

sha="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
worktree="$(basename "$(git rev-parse --show-toplevel 2>/dev/null || pwd)")"

# ── results, from the XML AGP writes ───────────────────────────────────────
# The XML is authoritative for pass/fail; the heartbeat is not (it only knows what
# the runner announced before it stopped announcing). But on a stall there IS no
# XML — the run never got to write one — so both sources are needed, each for the
# case the other cannot cover.
tests=0
failures=0
skipped=0
: >"$run_dir/failures.txt"
: >"$run_dir/durations.tsv"

# Freshness matters more than it looks. Gradle leaves its result XML in place, so a
# run that never executed tests — a failed boot, a build error, a stall during the
# build — finds the *previous* run's XML sitting there. Copied blindly, that run
# would be recorded as having passed every test in it: fabricated passes, inflating
# the denominator of every reliability figure and diluting real reds. So only XML
# newer than this run's start marker counts as this run's.
# A boot failure or a build failure ran zero tests, by definition — there is nothing
# to read, and the only XML present is last run's. Belt and braces with the marker
# below: this one holds even when the caller forgets to stamp it.
reads_results=0
case "$OUTCOME" in
  pass | failure | stall) reads_results=1 ;;
esac

if [ "$reads_results" -eq 1 ] && [ -n "$XML_DIR" ] && [ -d "$XML_DIR" ]; then
  mkdir -p "$run_dir/xml"
  if [ -n "$RUN_MARKER" ] && [ -e "$RUN_MARKER" ]; then
    find "$XML_DIR" -maxdepth 1 -name '*.xml' -newer "$RUN_MARKER" -exec cp {} "$run_dir/xml/" \; 2>/dev/null
  else
    cp "$XML_DIR"/*.xml "$run_dir/xml/" 2>/dev/null
  fi

  for xml in "$run_dir/xml"/*.xml; do
    [ -f "$xml" ] || continue
    # One <testsuite> header per class, carrying the per-class totals.
    while IFS= read -r attrs; do
      t=$(echo "$attrs" | sed -n 's/.*[^a-z]tests="\([0-9]*\)".*/\1/p')
      f=$(echo "$attrs" | sed -n 's/.*failures="\([0-9]*\)".*/\1/p')
      e=$(echo "$attrs" | sed -n 's/.*errors="\([0-9]*\)".*/\1/p')
      s=$(echo "$attrs" | sed -n 's/.*skipped="\([0-9]*\)".*/\1/p')
      tests=$((tests + ${t:-0}))
      failures=$((failures + ${f:-0} + ${e:-0}))
      skipped=$((skipped + ${s:-0}))
    done < <(grep -o '<testsuite [^>]*' "$xml" 2>/dev/null)

    # Per-test durations: the raw material for the slowest-test view, which is what
    # tells you whether STALL_TIMEOUT_SECONDS still has headroom over the real suite.
    grep -o '<testcase name="[^"]*" classname="[^"]*" time="[^"]*"' "$xml" 2>/dev/null |
      sed -n 's/<testcase name="\([^"]*\)" classname="[^"]*\.\([A-Za-z0-9_]*\)" time="\([^"]*\)".*/\2#\1\t\3/p' \
        >>"$run_dir/durations.tsv"

    # A <testcase> is red when it contains a <failure> or <error> child, i.e. when it
    # is NOT self-closing. awk tracks the open testcase and attributes the failure to it.
    #
    # AGP writes no `type=` attribute — the exception class is the first token of the
    # element's text (`<failure>org.junit…TestTimedOutException: test timed out…`), so
    # that is where it is read from. Getting this right is what makes the report's
    # "dominant exception" column worth anything: `ComposeTimeoutException` and
    # `TestTimedOutException` and an `AssertionError` are three different diagnoses.
    awk '
      /<testcase /{
        name=""; cls=""
        if (match($0, /name="[^"]*"/))      { name=substr($0, RSTART+6, RLENGTH-7) }
        if (match($0, /classname="[^"]*"/)) { cls=substr($0, RSTART+11, RLENGTH-12) }
        sub(/.*\./, "", cls)
        cur = cls "#" name
        next
      }
      /<failure|<error/{
        if (cur == "") next
        text = $0
        sub(/.*<(failure|error)[^>]*>/, "", text)
        kind = ""
        # Fully-qualified throwable followed by ": message", or a bare class name.
        if (match(text, /[A-Za-z_][A-Za-z0-9_.$]*(Exception|Error|Throwable)/)) {
          kind = substr(text, RSTART, RLENGTH)
          sub(/.*\./, "", kind)
        }
        # An EMPTY <failure></failure> is how AGP records the instrumentation dying
        # under a test — the "Process crashed" flake, which has no throwable to name
        # because the process that would have thrown it is gone. Left as "unknown" it
        # would be indistinguishable from a parse miss; named, it is one of the most
        # informative verdicts the report can give.
        if (kind == "") { kind = "ProcessCrash" }
        print cur "\t" kind
        cur = ""
      }
    ' "$xml" 2>/dev/null >>"$run_dir/failures.txt"
  done
fi

# A stall leaves no XML, so the heartbeat is the only record of how far it got — and
# the test it died ON did not pass: it never finished. Counting it would overstate the
# run by exactly the one test the whole stall is about.
#
# STALL ONLY. A boot or build failure ran zero tests, so its count must be zero — but the
# wrapper resets HEARTBEAT_INDEX inside supervise_gradle, which a boot failure skips, so
# TEST_INDEX can still hold the *previous* run's index. Applying this fallback there would
# invent `passed` tests from a run that never started one, corrupting the permanent ledger.
if [ "$OUTCOME" = "stall" ] && [ "$tests" -eq 0 ] && [ "$TEST_INDEX" -gt 0 ]; then
  tests=$((TEST_INDEX - 1))
  [ "$tests" -lt 0 ] && tests=0
fi
passed=$((tests - failures - skipped))
[ "$passed" -lt 0 ] && passed=0

for f in heartbeat.log watchdog.log gradle.log; do
  [ -n "$RUN_TMP" ] && cp "$RUN_TMP/$f" "$run_dir/$f" 2>/dev/null
done
# The heavy forensics are only worth keeping for a run that actually died.
if [ "$OUTCOME" = "stall" ]; then
  [ -n "$RUN_TMP" ] && cp -R "$RUN_TMP/diagnostics" "$run_dir/diagnostics" 2>/dev/null
fi

# ── the record ─────────────────────────────────────────────────────────────
# One flat JSON line per run: greppable with awk/sed (no jq on the CI image) but
# still valid JSON, so `jq` remains an option for whoever has it.
# Backslash and quote must be escaped; tabs, newlines and other control characters
# must not survive at all, or the "one run per line" invariant the whole reader rests
# on quietly stops holding.
json_escape() {
  printf '%s' "$1" | tr -d '\000-\037' | sed 's/\\/\\\\/g; s/"/\\"/g'
}

# The reds ride INSIDE the ledger line, not only in the artefact folder next to it.
# Artefacts are pruned at 30 runs; the ledger is kept forever — and "which test failed,
# when, with what" is precisely the history that has to outlive the pruning, or the
# flaky verdicts silently narrow to the last month of runs.
failures_json() {
  [ -s "$run_dir/failures.txt" ] || { printf '[]'; return; }
  local first=1 test kind
  printf '['
  while IFS="$(printf '\t')" read -r test kind; do
    [ -n "$test" ] || continue
    [ "$first" -eq 1 ] || printf ','
    first=0
    printf '{"test":"%s","exception":"%s"}' "$(json_escape "$test")" "$(json_escape "${kind:-unknown}")"
  done <"$run_dir/failures.txt"
  printf ']'
}

{
  printf '{'
  printf '"ts":"%s",' "$(date '+%Y-%m-%dT%H:%M:%S%z')"
  printf '"outcome":"%s",' "$(json_escape "$OUTCOME")"
  printf '"sha":"%s",' "$(json_escape "$sha")"
  printf '"branch":"%s",' "$(json_escape "$branch")"
  printf '"worktree":"%s",' "$(json_escape "$worktree")"
  printf '"duration_s":%s,' "${DURATION:-0}"
  printf '"tests":%s,' "${tests:-0}"
  printf '"passed":%s,' "${passed:-0}"
  printf '"failed":%s,' "${failures:-0}"
  printf '"skipped":%s,' "${skipped:-0}"
  printf '"failures":%s,' "$(failures_json)"
  printf '"last_test":"%s",' "$(json_escape "$LAST_TEST")"
  printf '"test_index":%s,' "${TEST_INDEX:-0}"
  printf '"test_total":%s,' "${TEST_TOTAL:-0}"
  printf '"host_slept_s":%s,' "${HOST_SLEPT:-0}"
  printf '"artefacts":"%s"' "$(json_escape "$(basename "$run_dir")")"
  printf '}\n'
} >>"$RUNS_JSONL"

# ── retention ──────────────────────────────────────────────────────────────
# Drop the oldest artefact dirs beyond KEEP_RUNS. Announced rather than silent: a
# quiet cap reads as "everything is here" when it isn't.
# -mindepth 1 so the history dir can never match itself and get rm -rf'd along with
# the ledger — `-maxdepth 1` alone includes depth 0.
count=$(find "$HISTORY_DIR" -mindepth 1 -maxdepth 1 -type d -name '20*' 2>/dev/null | wc -l | tr -d ' ')
if [ "$count" -gt "$KEEP_RUNS" ]; then
  excess=$((count - KEEP_RUNS))
  echo "  (run history: pruning $excess artefact dir(s) beyond the newest $KEEP_RUNS; runs.jsonl keeps every run)" >&2
  find "$HISTORY_DIR" -mindepth 1 -maxdepth 1 -type d -name '20*' 2>/dev/null |
    sort | head -n "$excess" |
    while IFS= read -r old; do rm -rf "$old"; done
fi

exit 0
