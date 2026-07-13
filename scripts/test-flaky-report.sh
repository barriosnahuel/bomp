#!/usr/bin/env bash
#
# Behaviour test for scripts/record-instrumented-run.sh + scripts/flaky-report.sh.
#
# Pure bash, no emulator and no Gradle: it fabricates a run history (AGP-shaped
# result XML, heartbeats, stall records) in a scratch dir, then asserts what the
# report concludes from it.
#
# What it is really guarding is the *verdict*, because the verdict is the whole
# product. A report that calls a real regression "flaky" invites you to re-run it
# until it's green and ship the bug; one that calls a known flaky a "regression"
# costs you an afternoon debugging a test that was already lying to you. Those two
# mistakes are asserted apart, explicitly.
#
# Run locally or in CI:  ./scripts/test-flaky-report.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RECORD="$SCRIPT_DIR/record-instrumented-run.sh"
REPORT="$SCRIPT_DIR/flaky-report.sh"

fails=0
pass() { echo "  ✓ $1"; }
fail() { echo "  ✘ $1"; fails=$((fails + 1)); }

assert_contains() {
  case "$2" in
    *"$3"*) pass "$1" ;;
    *) fail "$1 — expected to find: [$3]"; printf '    in report:\n%s\n' "$2" ;;
  esac
}
assert_not_contains() {
  case "$2" in
    *"$3"*) fail "$1 — did NOT expect: [$3]"; printf '    in report:\n%s\n' "$2" ;;
    *) pass "$1" ;;
  esac
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
history="$tmp/history"
xml_src="$tmp/xml"
run_tmp="$tmp/runtmp"
mkdir -p "$history" "$xml_src" "$run_tmp"

# ── AGP-shaped result XML ──────────────────────────────────────────────────
# write_xml <flaky-failed?> — SearchOverlayTest#pinsACustomSound is the flaky one:
# it fails on some runs and passes on others. AlwaysRedTest#brokenAssertion fails
# every single time. StableTest#alwaysGreen never fails.
write_xml() {
  local flaky_failed="$1"
  rm -f "$xml_src"/*.xml

  # NOTE the shape: AGP writes NO `type=` attribute — the throwable is the first
  # token of the element's text. The fixtures mirror that exactly, because a fixture
  # that is tidier than reality tests nothing (an earlier version of this test used
  # `type="…"` and happily passed while the real parser returned "unknown").
  if [ "$flaky_failed" = "yes" ]; then
    cat >"$xml_src/TEST-search.xml" <<'EOF'
<testsuite name="com.example.ui.home.SearchOverlayTest" tests="1" failures="1" errors="0" skipped="0" time="9.1">
<testcase name="pinsACustomSound" classname="com.example.ui.home.SearchOverlayTest" time="9.100">
<failure>androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms
at androidx.compose.ui.test.WaitKt.waitUntil(Wait.kt:44)
</failure>
</testcase>
</testsuite>
EOF
  else
    cat >"$xml_src/TEST-search.xml" <<'EOF'
<testsuite name="com.example.ui.home.SearchOverlayTest" tests="1" failures="0" errors="0" skipped="0" time="4.2">
<testcase name="pinsACustomSound" classname="com.example.ui.home.SearchOverlayTest" time="4.200" />
</testsuite>
EOF
  fi

  cat >"$xml_src/TEST-red.xml" <<'EOF'
<testsuite name="com.example.ui.home.AlwaysRedTest" tests="1" failures="1" errors="0" skipped="0" time="1.0">
<testcase name="brokenAssertion" classname="com.example.ui.home.AlwaysRedTest" time="1.000">
<failure>junit.framework.AssertionFailedError: expected 2 got 1
at junit.framework.Assert.fail(Assert.java:50)
</failure>
</testcase>
</testsuite>
EOF

  cat >"$xml_src/TEST-stable.xml" <<'EOF'
<testsuite name="com.example.ui.home.StableTest" tests="1" failures="0" errors="0" skipped="0" time="0.8">
<testcase name="alwaysGreen" classname="com.example.ui.home.StableTest" time="0.800" />
<testcase name="theSlowOne" classname="com.example.ui.home.StableTest" time="21.500" />
</testsuite>
EOF
}

# record <outcome> [last_test] [index] [slept] — a run of the CURRENT fake commit.
# The commit is faked by running each scenario inside a throwaway git repo whose HEAD
# we move, because the verdict is decided per commit and a test that cannot vary the
# commit cannot test the thing that matters.
record() {
  local outcome="$1" last_test="${2:-}" index="${3:-0}" slept="${4:-0}"
  ( cd "$fake_repo" && BOMP_HISTORY_DIR="$history" \
    BOMP_RUN_OUTCOME="$outcome" \
    BOMP_RUN_DURATION=300 \
    BOMP_RUN_TMP="$run_tmp" \
    BOMP_RUN_LAST_TEST="$last_test" \
    BOMP_RUN_INDEX="$index" \
    BOMP_RUN_TOTAL=134 \
    BOMP_RUN_HOST_SLEPT="$slept" \
    BOMP_RUN_XML_DIR="$xml_src" \
    bash "$RECORD" )
  # The timestamp is the artefact-dir name, at 1s resolution — keep runs distinct.
  sleep 1
}

# A throwaway repo so `git rev-parse HEAD` returns a real, movable sha.
fake_repo="$tmp/repo"
mkdir -p "$fake_repo"
(
  cd "$fake_repo" || exit 1
  git init -q 2>/dev/null
  git config user.email t@t.t
  git config user.name t
  echo one >f
  git add f
  git commit -qm one 2>/dev/null
) >/dev/null 2>&1

new_commit() {
  (
    cd "$fake_repo" || exit 1
    echo "$1" >f
    git add f
    git commit -qm "$1"
  ) >/dev/null 2>&1
}

echo "── 8 runs: one test flaky 3/8, one red 8/8, plus two stalls ───────────"
# Runs 1-8: the flaky test fails on 3 of them. The always-red test fails on all 8.
for i in 1 2 3 4 5 6 7 8; do
  case "$i" in
    2 | 5 | 7) write_xml yes ;;
    *) write_xml no ;;
  esac
  record failure
done

report="$(BOMP_HISTORY_DIR="$history" bash "$REPORT" 2>&1)"

assert_contains "a test that failed 3 of 8 runs is called FLAKY" "$report" "FLAKY"
assert_contains "the flaky test is named" "$report" "SearchOverlayTest#pinsACustomSound"
assert_contains "its rate is shown against the runs that reached it" "$report" "3/8"
assert_contains "it says re-running is legitimate — the code did not change" "$report" "Non-deterministic"

assert_contains "a test that failed all 8 runs is NOT called flaky" "$report" "CONSISTENT"
assert_contains "the consistently-red test is named" "$report" "AlwaysRedTest#brokenAssertion"
assert_contains "it warns that re-running will not help" "$report" "Re-running will NOT help"

assert_contains "the dominant exception is carried through" "$report" "ComposeTimeoutException"
assert_contains "and the consistent red's exception is too, unconfused with it" "$report" "AssertionFailedError"
assert_not_contains "no failure is filed as an unparsed 'unknown'" "$report" "unknown"

# Scoped to the failing-tests section: a green test legitimately shows up elsewhere
# in the report (it is in the slowest-tests table), so asserting against the whole
# document would be asserting the wrong thing.
failing_section="$(echo "$report" | awk '/ Failing tests/,/^──────.*$/ { print }' | tail -n +2)"
assert_not_contains "a test that never failed is not listed among the failures" "$failing_section" "StableTest#alwaysGreen"

echo ""
echo "── the slowest test is surfaced, to keep the stall timeout honest ─────"
assert_contains "the slowest test is named" "$report" "StableTest#theSlowOne"
assert_contains "the stall-timeout headroom is stated" "$report" "STALL_TIMEOUT_SECONDS"

echo ""
echo "── stalls are grouped, and a sleeping host is not blamed on the AVD ───"
record stall "VaultTabFlowTest#unlockingVault" 26 902
record stall "ExploreTabFlowTest#pinIcon" 61 0
report="$(BOMP_HISTORY_DIR="$history" bash "$REPORT" 2>&1)"

assert_contains "stalls get their own section" "$report" "Stalls"
assert_contains "a stall records where it died" "$report" "26/134"
assert_contains "a host-sleep stall is called out as such" "$report" "THE HOST SLEPT"
assert_contains "and it exonerates the emulator explicitly" "$report" "Not an emulator bug"
# Two stalls on two different tests means the hang is not bound to a test — the
# test it "died on" is incidental, and telling you to go stare at it would be a
# false lead. This is the distinction that took a real investigation to make.
assert_contains "stalls on different tests are diagnosed as time-bound, not test-bound" "$report" "time-bound"

echo ""
echo "── a regression you just introduced is NOT called flaky ───────────────"
# THE case the whole partition-by-commit design exists for, and the one a pooled
# report gets catastrophically wrong. A test is green for five runs; you change the
# code; now it fails every run of the new commit. Pooling the window sees "it passed
# on other runs" and says FLAKY — i.e. tells you to re-run a real regression until it
# goes green, and ship the bug. The verdict must key on the commit, not the window.
history3="$tmp/history3"
mkdir -p "$history3"
history_backup="$history"
history="$history3"

new_commit "green-era"
for i in 1 2 3 4 5; do
  write_xml no
  record pass
done

new_commit "the-one-that-broke-it"
for i in 1 2 3; do
  write_xml yes
  record failure
done

report3="$(BOMP_HISTORY_DIR="$history3" bash "$REPORT" 2>&1)"
history="$history_backup"

assert_contains "the newly-broken test is reported" "$report3" "SearchOverlayTest#pinsACustomSound"
assert_contains "it is called CONSISTENT — it failed every run of the commit that broke it" "$report3" "CONSISTENT"
assert_not_contains "it is NEVER called flaky just because older commits were green" "$report3" "🟡 FLAKY"
assert_contains "and it says plainly that re-running will not save you" "$report3" "Re-running will NOT help"

echo ""
echo "── one failure on one run of a commit is 'unknown', not a verdict ─────"
# Saying either "flaky" or "regression" off a single observation is a guess, and both
# guesses cost you: one ships a bug, the other burns an afternoon.
history4="$tmp/history4"
mkdir -p "$history4"
history_backup="$history"
history="$history4"
new_commit "fresh"
write_xml yes
record failure
report4="$(BOMP_HISTORY_DIR="$history4" bash "$REPORT" 2>&1)"
history="$history_backup"

assert_contains "a single failing run yields UNKNOWN" "$report4" "UNKNOWN"
assert_not_contains "it does not assert 'never passed' from one observation" "$report4" "Re-running will NOT help"
assert_contains "and it tells you what would settle it" "$report4" "Re-run it"

echo ""
echo "── a run that never reached the tests contributes no evidence ─────────"
# A failed boot leaves the PREVIOUS run's XML sitting in the results dir. Recorded
# blindly, that run would count as "every test passed" — fabricating passes, inflating
# every denominator, and diluting real reds into apparent flakes.
history5="$tmp/history5"
mkdir -p "$history5"
history_backup="$history"
history="$history5"
new_commit "boot-scenario"
write_xml no
record pass          # a real run: leaves XML behind
record boot          # never ran a single test — but the XML is still on disk
report5="$(BOMP_HISTORY_DIR="$history5" bash "$REPORT" 2>&1)"
history="$history_backup"

boot_line="$(grep '"outcome":"boot"' "$history5/runs.jsonl")"
case "$boot_line" in
  *'"tests":0'*) pass "a boot failure records zero tests, not the last run's results" ;;
  *) fail "a boot failure inherited the previous run's XML: $boot_line" ;;
esac
assert_contains "the boot failure is still counted as a run" "$report5" "emulator never booted"

echo ""
echo "── every ledger line is valid JSON ────────────────────────────────────"
# The wrapper passes "?" for the test counters until the runner announces itself, so
# a boot/infra failure used to emit `"test_total":?` — unparseable, and the file
# claims to be JSONL.
if command -v python3 >/dev/null 2>&1; then
  if python3 -c "
import json,sys
for path in ['$history/runs.jsonl', '$history5/runs.jsonl']:
    for i, line in enumerate(open(path), 1):
        line = line.strip()
        if line:
            json.loads(line)
" 2>/dev/null; then
    pass "runs.jsonl parses as JSON on every line, including boot/infra runs"
  else
    fail "runs.jsonl contains invalid JSON"
  fi
else
  echo "  (skipped: python3 unavailable)"
fi

echo ""
echo "── a green history reports no failures at all ─────────────────────────"
history2="$tmp/history2"
mkdir -p "$history2"
rm -f "$xml_src"/*.xml
cat >"$xml_src/TEST-stable.xml" <<'EOF'
<testsuite name="com.example.ui.home.StableTest" tests="1" failures="0" errors="0" skipped="0" time="0.8">
<testcase name="alwaysGreen" classname="com.example.ui.home.StableTest" time="0.800" />
</testsuite>
EOF
BOMP_HISTORY_DIR="$history2" BOMP_RUN_OUTCOME=pass BOMP_RUN_DURATION=240 BOMP_RUN_TMP="$run_tmp" \
  BOMP_RUN_INDEX=1 BOMP_RUN_TOTAL=1 BOMP_RUN_XML_DIR="$xml_src" bash "$RECORD"
report2="$(BOMP_HISTORY_DIR="$history2" bash "$REPORT" 2>&1)"
assert_contains "a clean history says so plainly" "$report2" "no failing tests"
assert_not_contains "and invents no flakies" "$report2" "FLAKY"

echo ""
echo "── artefact retention prunes old runs but never the ledger ────────────"
before_lines=$(wc -l <"$history/runs.jsonl" | tr -d ' ')
write_xml no
BOMP_HISTORY_KEEP_RUNS=3 record pass
dirs=$(find "$history" -maxdepth 1 -type d -name '20*' | wc -l | tr -d ' ')
after_lines=$(wc -l <"$history/runs.jsonl" | tr -d ' ')
if [ "$dirs" -le 3 ]; then
  pass "artefact dirs are pruned to the retention limit ($dirs kept)"
else
  fail "artefact dirs not pruned — expected ≤3, found $dirs"
fi
if [ "$after_lines" -gt "$before_lines" ]; then
  pass "runs.jsonl keeps every run even when artefacts are pruned ($after_lines lines)"
else
  fail "runs.jsonl lost history during pruning"
fi

echo ""
if [ "$fails" -eq 0 ]; then
  echo "✔ all run-history / flaky-report behaviour tests passed"
  exit 0
fi
echo "✘ $fails behaviour test(s) failed"
exit 1
