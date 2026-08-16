#!/usr/bin/env bash
#
# Behaviour test for scripts/run-instrumented-tests.sh — specifically its stall
# watchdog and exit-code contract.
#
# Pure bash + git, no network, no Android SDK and no emulator: it stages a fake
# repo (a copy of the script under test plus a `gradlew` stub), shims `adb` and
# `emulator` on PATH, and drives each scenario by fixture. The watchdog's clocks
# are turned down to seconds via STALL_TIMEOUT_SECONDS / HARD_CAP_SECONDS /
# WATCHDOG_POLL_SECONDS so the whole suite runs in well under a minute.
#
# What it guards, and why it is worth the ceremony: the watchdog's job is to kill
# a run it believes is hung. A bug in *that* is worse than the bug it fixes — it
# would kill healthy runs and read as a flaky suite. So the happy paths (a clean
# pass, honest test failures) are asserted just as hard as the stall paths.
#
# Run locally or in CI:  ./scripts/test-run-instrumented-tests.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WRAPPER="$SCRIPT_DIR/run-instrumented-tests.sh"

fails=0
pass() { echo "  ✓ $1"; }
fail() { echo "  ✘ $1"; fails=$((fails + 1)); }

assert_eq() {
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1 — expected [$3], got [$2]"; fi
}
assert_contains() {
  case "$2" in
    *"$3"*) pass "$1" ;;
    *) fail "$1 — expected to find: [$3]"; printf '    in output:\n%s\n' "$2" ;;
  esac
}
assert_not_contains() {
  case "$2" in
    *"$3"*) fail "$1 — did NOT expect: [$3]" ;;
    *) pass "$1" ;;
  esac
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

bindir="$tmp/bin"
mkdir -p "$bindir"
state="$tmp/state"
mkdir -p "$state"

# ── adb shim ───────────────────────────────────────────────────────────────
# Emulates just enough of adb for the wrapper: device listing backed by a state
# file (so `emu kill` actually removes the device, as the real one does), a boot
# property driven by a fixture, a TestRunner logcat stream replayed from a
# fixture, and a liveness probe that can be made to hang on demand.
cat >"$bindir/adb" <<'SHIM'
#!/usr/bin/env bash
alive="$STUB_STATE/emulator_alive"

case "$1" in
  devices)
    echo "List of devices attached"
    [ -f "$alive" ] && echo "emulator-5554	device"
    exit 0
    ;;
  kill-server|start-server|wait-for-device) exit 0 ;;
esac

# `adb -s <serial> ...`
if [ "$1" = "-s" ]; then
  shift 2
fi

case "$1 ${2:-}" in
  "emu avd")
    # STUB_CONSOLE_WEDGED=1 models a host-sleep-corrupted emulator whose console thread
    # is frozen: `emu avd name` returns nothing, exactly what a real timed-out probe
    # leaves behind.
    [ "${STUB_CONSOLE_WEDGED:-0}" = "1" ] && exit 0
    echo "${STUB_AVD_NAME:-FakeAVD}"; exit 0 ;;
  "emu kill")  rm -f "$alive"; exit 0 ;;
esac

case "$1" in
  shell)
    case "${2:-}" in
      getprop)
        # STUB_BOOT_COMPLETED=never keeps the device un-booted forever.
        # STUB_BOOT_FAIL_ON_RUN=n fails the boot only on the n-th run, so a mixed
        # multi-run outcome can be asserted.
        if [ "${STUB_BOOT_COMPLETED:-1}" = "never" ]; then echo "0"; exit 0; fi
        if [ -n "${STUB_BOOT_FAIL_ON_RUN:-}" ]; then
          # boot_count is bumped by the emulator shim, so it already names the run
          # currently booting.
          boots="$(cat "$STUB_STATE/boot_count" 2>/dev/null || echo 0)"
          if [ "$boots" -ge "$STUB_BOOT_FAIL_ON_RUN" ]; then echo "0"; exit 0; fi
        fi
        echo "1"
        exit 0
        ;;
      true)
        # The liveness probe. STUB_DEVICE_FROZEN=1 makes it hang, exactly like a
        # wedged emulator whose adb socket is still open — the wrapper must bound
        # it rather than block.
        if [ "${STUB_DEVICE_FROZEN:-0}" = "1" ]; then sleep 300; fi
        exit 0
        ;;
      dumpsys) echo "fake dumpsys"; exit 0 ;;
    esac
    exit 0
    ;;
  logcat)
    case "${2:-}" in
      -c) exit 0 ;;
      -d) echo "fake full logcat"; exit 0 ;;
    esac
    # Streaming capture: replay the fixture, then stay alive but silent (a real
    # logcat blocks waiting for more lines; a frozen device simply never sends any).
    last_line_file="$STUB_STATE/logcat_last_line"
    reattach=0
    [ "${2:-}" = "-T" ] && reattach=1
    echo "--------- beginning of main"
    if [ "$reattach" = "1" ]; then
      # `-T 1` is how the wrapper reattaches after the stream dies. Real adb prints the
      # most recent buffered line before it starts following — reproduced here verbatim
      # so the parser's replay dedup is actually exercised, rather than the shim
      # pretending a reattach never repeats a line. The "beginning of main" marker above
      # lands between that replay and the live tail, just as real adb emits it.
      [ -f "$last_line_file" ] && cat "$last_line_file"
      sleep 300
      exit 0
    fi
    if [ -f "$STUB_LOGCAT_FIXTURE" ]; then
      emitted=0
      while IFS= read -r l; do
        # STUB_LOGCAT_DIE_AFTER=n: the stream drops dead after n lines, as it does
        # when the machine-wide adb server is restarted under it.
        if [ -n "${STUB_LOGCAT_DIE_AFTER:-}" ] && [ "$emitted" -ge "$STUB_LOGCAT_DIE_AFTER" ]; then
          exit 1
        fi
        echo "$l"
        printf '%s\n' "$l" >"$last_line_file"
        emitted=$((emitted + 1))
        sleep "${STUB_LOGCAT_LINE_DELAY:-0}"
      done <"$STUB_LOGCAT_FIXTURE"
    fi
    sleep 300
    exit 0
    ;;
esac
exit 0
SHIM
chmod +x "$bindir/adb"

# ── emulator shim ──────────────────────────────────────────────────────────
cat >"$bindir/emulator" <<'SHIM'
#!/usr/bin/env bash
# Counts boots so a fixture can fail the n-th one (see STUB_BOOT_FAIL_ON_RUN).
boots="$(cat "$STUB_STATE/boot_count" 2>/dev/null || echo 0)"
echo "$((boots + 1))" >"$STUB_STATE/boot_count"
touch "$STUB_STATE/emulator_alive"
sleep 300
SHIM
chmod +x "$bindir/emulator"

# ── fake repo: the script under test + a gradlew stub ──────────────────────
# The wrapper resolves REPO_ROOT from its own location and runs `./gradlew` from
# there, so staging a fake repo root is what lets the stub take over.
fake_repo="$tmp/repo"
mkdir -p "$fake_repo/scripts"
cp "$WRAPPER" "$fake_repo/scripts/run-instrumented-tests.sh"
chmod +x "$fake_repo/scripts/run-instrumented-tests.sh"

cat >"$fake_repo/gradlew" <<'SHIM'
#!/usr/bin/env bash
[ "${1:-}" = "--stop" ] && exit 0

# Marks that a real Gradle process existed, so a test can assert the watchdog
# actually killed it rather than orphaning it behind a dead subshell.
echo $$ >"$STUB_STATE/gradle_pid"
trap 'rm -f "$STUB_STATE/gradle_pid"; exit 143' TERM

case "$STUB_GRADLE_MODE" in
  pass)
    sleep "${STUB_PASS_SECONDS:-2}"
    echo "BUILD SUCCESSFUL"
    rm -f "$STUB_STATE/gradle_pid"
    exit 0
    ;;
  fail)
    sleep 2
    echo "FAILURE: There were failing tests."
    rm -f "$STUB_STATE/gradle_pid"
    exit 1
    ;;
  slow-silent-build)
    # A HEALTHY run whose build phase is silent for longer than the test-phase
    # stall timeout — a cold daemon, a cold Kotlin compile, dependency resolution
    # on a slow network. Nothing is wrong. Killing this is the single worst thing
    # the watchdog can do, so it is asserted like a first-class scenario.
    sleep "${STUB_SILENT_BUILD_SECONDS:-9}"
    echo "BUILD SUCCESSFUL"
    rm -f "$STUB_STATE/gradle_pid"
    exit 0
    ;;
  build-failure)
    # Exits non-zero *before* any test runs: a compile error, INSTALL_FAILED, no
    # device. There is no test report to send anyone to.
    sleep 1
    echo "FAILURE: Execution failed for task ':app:compileDebugKotlin'."
    rm -f "$STUB_STATE/gradle_pid"
    exit 1
    ;;
  hang)
    # Blocks forever without printing again — Gradle waiting on ddmlib waiting on
    # a frozen emulator.
    sleep 600
    exit 0
    ;;
  chatty-hang)
    # Never finishes, but keeps dribbling output, so the stall clock keeps being
    # reset and only the hard cap can catch it.
    while true; do
      echo "still working..."
      sleep 1
    done
    ;;
esac
exit 0
SHIM
chmod +x "$fake_repo/gradlew"

# A minimal app/build.gradle so the wrapper's stall-clock guard can read the real
# on-device per-test timeout. 1000ms (1s) keeps it below every scenario's turned-down
# STALL_TIMEOUT, so the guard stays quiet except in the test that deliberately trips it.
mkdir -p "$fake_repo/app"
cat >"$fake_repo/app/build.gradle" <<'EOF'
android {
    defaultConfig {
        testInstrumentationRunnerArguments notAnnotation: 'x.ScreenshotCapture',
                                           timeout_msec: '1000'
    }
}
EOF

# ── logcat fixtures ────────────────────────────────────────────────────────
# The real format, verbatim from a live AVD run.
full_run="$tmp/logcat-full.txt"
cat >"$full_run" <<'EOF'
07-13 01:27:32.065  3913  4405 I TestRunner: run started: 3 tests
07-13 01:27:32.072  3913  4405 I TestRunner: started: pinsACustomSound(com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest)
07-13 01:27:37.360  3913  4405 I TestRunner: finished: pinsACustomSound(com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest)
07-13 01:27:37.373  3913  4405 I TestRunner: started: playsOnTap(com.github.barriosnahuel.vossosunboton.ui.home.HomeTabFlowTest)
07-13 01:27:46.063  3913  4405 I TestRunner: finished: playsOnTap(com.github.barriosnahuel.vossosunboton.ui.home.HomeTabFlowTest)
07-13 01:27:46.073  3913  4405 I TestRunner: started: opensAbout(com.github.barriosnahuel.vossosunboton.ui.about.AboutScreenFlowTest)
07-13 01:27:52.120  3913  4405 I TestRunner: finished: opensAbout(com.github.barriosnahuel.vossosunboton.ui.about.AboutScreenFlowTest)
EOF

# A run that dies mid-flight: two tests announced, then the device goes quiet.
partial_run="$tmp/logcat-partial.txt"
cat >"$partial_run" <<'EOF'
07-13 01:27:32.065  3913  4405 I TestRunner: run started: 3 tests
07-13 01:27:32.072  3913  4405 I TestRunner: started: pinsACustomSound(com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest)
07-13 01:27:37.360  3913  4405 I TestRunner: finished: pinsACustomSound(com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest)
07-13 01:27:37.373  3913  4405 I TestRunner: started: playsOnTap(com.github.barriosnahuel.vossosunboton.ui.home.HomeTabFlowTest)
EOF

failing_run="$tmp/logcat-failing.txt"
cat >"$failing_run" <<'EOF'
07-13 01:27:32.065  3913  4405 I TestRunner: run started: 2 tests
07-13 01:27:32.072  3913  4405 I TestRunner: started: pinsACustomSound(com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest)
07-13 01:27:37.360  3913  4405 E TestRunner: failed: pinsACustomSound(com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest)
07-13 01:27:37.373  3913  4405 I TestRunner: started: playsOnTap(com.github.barriosnahuel.vossosunboton.ui.home.HomeTabFlowTest)
07-13 01:27:46.063  3913  4405 I TestRunner: finished: playsOnTap(com.github.barriosnahuel.vossosunboton.ui.home.HomeTabFlowTest)
EOF

# A run whose tests never start: the build phase, silent by definition.
empty_run="$tmp/logcat-empty.txt"
: >"$empty_run"

# run_wrapper <gradle-mode> <logcat-fixture> [extra env assignments...]
#
# The clocks are turned down to seconds, keeping the real-world *ordering* intact:
# test-phase stall (6s) « build-phase stall (25s) « hard cap (40s). If the build
# budget were not the larger one, a slow build would read as a hung emulator —
# which is the bug `slow-silent-build` below exists to catch.
run_wrapper() {
  local mode="$1" fixture="$2"
  shift 2
  rm -f "$state/emulator_alive" "$state/gradle_pid" "$state/boot_count" "$state/logcat_last_line"
  env -i \
    PATH="$bindir:/usr/bin:/bin:/usr/sbin:/sbin" \
    HOME="$tmp" \
    TMPDIR="$tmp/run-tmp" \
    ANDROID_HOME="$tmp/no-sdk" \
    STUB_STATE="$state" \
    STUB_GRADLE_MODE="$mode" \
    STUB_LOGCAT_FIXTURE="$fixture" \
    STUB_AVD_NAME="FakeAVD" \
    AVD_NAME="FakeAVD" \
    STALL_TIMEOUT_SECONDS=6 \
    BUILD_STALL_TIMEOUT_SECONDS=25 \
    HARD_CAP_SECONDS=40 \
    WATCHDOG_POLL_SECONDS=1 \
    GRADLE_TERM_GRACE_SECONDS=2 \
    BOOT_TIMEOUT_SECONDS=6 \
    "$@" \
    bash "$fake_repo/scripts/run-instrumented-tests.sh" 2>&1
}

echo "── a clean pass exits 0 and reports progress ──────────────────────────"
out="$(run_wrapper pass "$full_run")"
status=$?
assert_eq "clean run exits 0" "$status" "0"
assert_contains "announces the suite size from the runner's own stream" "$out" "suite started: 3 tests"
assert_contains "prints per-test progress with a running counter" "$out" "1/3 SearchOverlayTest#pinsACustomSound"
assert_contains "counts up as tests advance" "$out" "3/3 AboutScreenFlowTest#opensAbout"
assert_contains "declares the pass unambiguously" "$out" "✔ suite passed"
assert_not_contains "a healthy run is never reported as a stall" "$out" "STALL"

echo ""
echo "── honest test failures exit 1, not 124 ───────────────────────────────"
out="$(run_wrapper fail "$failing_run")"
status=$?
assert_eq "test failures exit 1" "$status" "1"
assert_contains "surfaces the failing test as it happens" "$out" "✘ FAILED SearchOverlayTest#pinsACustomSound"
assert_contains "points at the report" "$out" "test failures"
assert_not_contains "a red test is never mistaken for a hung emulator" "$out" "EMULATOR HUNG"

echo ""
echo "── a hung emulator exits 124 and says where it died ───────────────────"
out="$(run_wrapper hang "$partial_run")"
status=$?
assert_eq "a stalled run exits 124" "$status" "124"
assert_contains "names the stall for what it is" "$out" "EMULATOR STALL"
assert_contains "reports the test it died on" "$out" "died at test 2/3: HomeTabFlowTest#playsOnTap"
assert_contains "tells the operator it is not a test failure" "$out" "NOT a test failure"
assert_contains "dumps diagnostics" "$out" "diagnostics:"
assert_contains "classifies a responsive device as the innocent party" "$out" "device_probe:  ALIVE"

echo ""
echo "── a frozen device is classified apart from a wedged Gradle ───────────"
out="$(run_wrapper hang "$partial_run" STUB_DEVICE_FROZEN=1)"
status=$?
assert_eq "a frozen device still exits 124" "$status" "124"
assert_contains "names the emulator itself as the stuck party" "$out" "device_probe:  FROZEN"

echo ""
echo "── output without progress still hits the hard cap ────────────────────"
out="$(run_wrapper chatty-hang "$partial_run")"
status=$?
assert_eq "a chatty hang exits 124 via the hard cap" "$status" "124"
assert_contains "names the hard cap, not the stall clock" "$out" "HARD CAP"
assert_not_contains "the stall clock never fires while output keeps flowing" "$out" "EMULATOR STALL"

echo ""
echo "── a stall in one run does not abort the remaining runs ───────────────"
out="$(run_wrapper hang "$partial_run" RUNS=2)"
status=$?
assert_eq "the overall status is still 124" "$status" "124"
assert_contains "run 1 is attempted" "$out" "instrumented run 1 / 2"
assert_contains "run 2 is attempted even though run 1 stalled" "$out" "instrumented run 2 / 2"

echo ""
echo "── an emulator that never boots is its own failure, not a stall ───────"
out="$(run_wrapper pass "$full_run" STUB_BOOT_COMPLETED=never)"
status=$?
assert_eq "a boot failure exits 2" "$status" "2"
assert_contains "says the emulator never booted" "$out" "never booted"

echo ""
echo "── a slow, silent BUILD is not a stall ────────────────────────────────"
# The build phase legitimately goes quiet for longer than the test-phase stall
# timeout (cold daemon, cold compile, dependency resolution). Killing it would be
# the watchdog's worst failure: it murders a healthy run, and because the kill
# leaves the next run with a cold daemon to rebuild, it cascades into a suite of
# fake stalls. STUB_SILENT_BUILD_SECONDS (9s) sits above STALL_TIMEOUT (6s) and
# below BUILD_STALL_TIMEOUT (25s) — exactly the window where the two clocks disagree.
out="$(run_wrapper slow-silent-build "$empty_run" STUB_SILENT_BUILD_SECONDS=9)"
status=$?
assert_eq "a silent build that outlives the test-phase clock still exits 0" "$status" "0"
assert_not_contains "a slow build is never called an emulator stall" "$out" "EMULATOR STALL"
assert_not_contains "a slow build is never called a build stall either" "$out" "BUILD STALL"

echo ""
echo "── a build that fails before any test is not a red test ───────────────"
out="$(run_wrapper build-failure "$empty_run")"
status=$?
assert_eq "a build/install failure exits 3, not 1" "$status" "3"
assert_contains "says no test ever ran" "$out" "BUILD/INSTALL FAILURE"
assert_not_contains "does not send the operator to a test report that does not exist" "$out" "index.html"

echo ""
echo "── the watchdog kills Gradle itself, not a shell wrapping it ──────────"
run_wrapper hang "$partial_run" >/dev/null 2>&1
if [ -f "$state/gradle_pid" ] && kill -0 "$(cat "$state/gradle_pid")" 2>/dev/null; then
  fail "Gradle survived the stall — it was orphaned, still driving a doomed emulator"
  kill -9 "$(cat "$state/gradle_pid")" 2>/dev/null
else
  pass "no Gradle process survives a stall"
fi

echo ""
echo "── a dead logcat stream is recovered, not blamed on the emulator ──────"
# The adb server is machine-wide: Android Studio opening, an `adb kill-server` in
# another terminal, or a flaky wireless device dropping will take this stream down
# while the emulator carries on fine. A frozen capture must not read as a hung device.
# The Gradle stub is kept alive well past the stream's death (STUB_PASS_SECONDS)
# so the watchdog gets a chance to notice; the stall clock is widened for the same
# reason, since the point here is the recovery, not the clocks.
out="$(run_wrapper pass "$full_run" \
  STUB_LOGCAT_DIE_AFTER=2 STUB_LOGCAT_LINE_DELAY=1 STUB_PASS_SECONDS=10 STALL_TIMEOUT_SECONDS=30)"
status=$?
assert_eq "a healthy run survives its logcat stream dying" "$status" "0"
assert_contains "reports the reattach instead of silently mis-blaming the device" "$out" "logcat stream died"

echo ""
echo "── mixed outcomes across runs resolve by severity, not by order ───────"
out="$(run_wrapper fail "$failing_run" RUNS=2 STUB_BOOT_FAIL_ON_RUN=2)"
status=$?
assert_eq "a run-2 boot failure does not bury a genuine red test from run 1" "$status" "1"

echo ""
echo "── a host that fell asleep is not an emulator that hung ───────────────"
# The failure mode that motivated the watchdog turned out to be this one: a full run
# outlasts macOS's idle-sleep timer, so starting the suite and walking away suspends
# the machine mid-run. Every clock jumps forward while the guest was frozen along
# with everything else — and a naive watchdog reads that as a wedged emulator and
# tells you to go blame a device that was never at fault.
#
# The suspend is simulated by making the poll interval enormous relative to the
# threshold: the loop comes back to find far more wall clock gone than it slept for,
# which is exactly the shadow a real suspend leaves.
out="$(run_wrapper pass "$full_run" \
  STUB_PASS_SECONDS=12 WATCHDOG_POLL_SECONDS=8 HOST_SLEEP_TICK_THRESHOLD=3 STALL_TIMEOUT_SECONDS=6)"
status=$?
assert_eq "a run that survives a host sleep still exits 0" "$status" "0"
assert_contains "names the host as the thing that slept" "$out" "host appears to have slept"
assert_not_contains "never blames the emulator for the host sleeping" "$out" "EMULATOR STALL"

echo ""
echo "── a reattach that replays a line does not double-count the test ──────"
# When the machine-wide adb server restarts, the wrapper reattaches with `-T 1`, and
# real adb replays the most recent buffered line. If that line was a `started:` event, a
# naive parser counts the test twice and the index runs past the total ("died at 4/3").
# The stream here dies right after the first test's `started:`, so the reattach replays
# exactly that line — the dedup must absorb it.
out="$(run_wrapper pass "$full_run" \
  STUB_LOGCAT_DIE_AFTER=2 STUB_LOGCAT_LINE_DELAY=1 STUB_PASS_SECONDS=8 STALL_TIMEOUT_SECONDS=30)"
status=$?
assert_eq "the run still passes through a replaying reattach" "$status" "0"
assert_contains "the first test is counted once" "$out" "1/3 SearchOverlayTest#pinsACustomSound"
assert_not_contains "the replayed started: is not counted a second time" "$out" "2/3 SearchOverlayTest#pinsACustomSound"

echo ""
echo "── a console-wedged emulator is SIGKILLed, not left holding the port ──"
# Identifying "our" emulator via `adb emu avd name` talks to the console — the very
# thread that freezes on a host-sleep-corrupted emulator. When that probe comes back
# empty, an emulator on our port must STILL be escalated to a SIGKILL by AVD name (which
# reads the qemu command line, not the console), or it survives and the next run's cold
# boot collides with it on the same port. Before this fix the SIGKILL fallback was
# unreachable for exactly the wedged case it was written for.
out="$(run_wrapper hang "$partial_run" STUB_CONSOLE_WEDGED=1 EMULATOR_KILL_WAIT_SECONDS=2)"
status=$?
assert_eq "the stalled run still exits 124" "$status" "124"
assert_contains "a console-wedged emulator on our port is recognised as ours" "$out" "isn't answering its console"
assert_contains "and escalated to a SIGKILL by AVD name" "$out" "SIGKILLing the FakeAVD emulator process"

echo ""
echo "── the stall clock is never left below the on-device per-test timeout ──"
# A test deadlocked on-device is silent until the runner's timeout_msec fires and names
# it. If STALL_TIMEOUT_SECONDS is set under that, the watchdog kills the run blind — as a
# stall to "re-run" — right when the runner was about to report the deadlock as a real
# red. The wrapper reads timeout_msec from build.gradle (1000ms in the fake repo) and
# refuses to sit at or below it.
out="$(run_wrapper pass "$full_run" STALL_TIMEOUT_SECONDS=1)"
status=$?
assert_eq "the run still passes after the guard raises the clock" "$status" "0"
assert_contains "an undercutting stall timeout is called out and raised" "$out" "does not clear the on-device per-test timeout"

echo ""
echo "── only red tests keep the emulator; every other outcome reclaims it ──"
# An AVD left up after a run nobody will inspect is pure waste — it holds GPU, cores and
# memory until someone notices it by hand. Red *tests* are the one outcome that earns the
# keep: the operator opens the app on that device and reads the state that failed. The
# other non-green outcomes look like they qualify and do not — a failed boot never
# produced a usable device, an infra failure ran no test, and a stall's AVD is wedged
# (already killed by the run loop). Survival is read off the shim's device-state file, which
# `emu kill` really clears, so a shutdown that is only *claimed* still fails here.
emulator_is_up() { [ -f "$state/emulator_alive" ] && echo up || echo down; }

out="$(run_wrapper pass "$full_run")"; status=$?
assert_eq "a green run still exits 0" "$status" "0"
assert_eq "a green run leaves no emulator behind" "$(emulator_is_up)" "down"
assert_contains "and says so, since the AVD vanishing is otherwise unexplained" "$out" "emulator shut down"
assert_not_contains "a green run never invites an inspection" "$out" "still up so you can inspect"

out="$(run_wrapper fail "$failing_run")"; status=$?
assert_eq "a red run still exits 1" "$status" "1"
assert_eq "red tests keep the emulator up for inspection" "$(emulator_is_up)" "up"
assert_contains "and announce it, so it is never left running unnoticed" "$out" "still up so you can inspect"
assert_contains "naming a kill command that targets our serial, not 'any device'" "$out" "adb -s emulator-5554 emu kill"
assert_not_contains "a kept emulator is never also reported as shut down" "$out" "emulator shut down"

out="$(run_wrapper hang "$partial_run")"; status=$?
assert_eq "a stalled run still exits 124" "$status" "124"
assert_eq "a stalled run's wedged AVD stays killed, as the run loop left it" "$(emulator_is_up)" "down"
assert_not_contains "and is never advertised as inspectable — it is frozen" "$out" "still up so you can inspect"

out="$(run_wrapper pass "$full_run" STUB_BOOT_COMPLETED=never)"; status=$?
assert_eq "a boot failure still exits 2" "$status" "2"
assert_eq "a half-launched AVD is reclaimed, not left behind" "$(emulator_is_up)" "down"

out="$(run_wrapper build-failure "$empty_run")"; status=$?
assert_eq "an infra failure still exits 3" "$status" "3"
assert_eq "a run that never reached the tests has nothing to inspect" "$(emulator_is_up)" "down"

echo ""
echo "── with RUNS>1 the decision follows the run whose emulator survived ────"
# Every run cold-boots a fresh, wiped AVD, so the device standing at the end is the LAST
# run's. Keying the keep on the worst-of-all status would hand the operator run 3's clean
# green device while telling them to go inspect run 1's failure.
out="$(run_wrapper fail "$failing_run" RUNS=2 STUB_BOOT_FAIL_ON_RUN=2)"; status=$?
assert_eq "run 1's red tests still decide the exit code over run 2's boot failure" "$status" "1"
assert_eq "but the AVD is judged by run 2, which left nothing to inspect" "$(emulator_is_up)" "down"

echo ""
echo "── KEEP_EMULATOR overrides the outcome, and says when it cannot ────────"
# Both directions have a real caller: an unattended/chained run wants the host reclaimed
# even on red, and someone debugging a flake wants the device alive even on green.
out="$(run_wrapper pass "$full_run" KEEP_EMULATOR=1)"
assert_eq "KEEP_EMULATOR=1 keeps a green run's emulator" "$(emulator_is_up)" "up"
assert_contains "and still announces the emulator it left running" "$out" "still up so you can inspect"

out="$(run_wrapper fail "$failing_run" KEEP_EMULATOR=0)"; status=$?
assert_eq "KEEP_EMULATOR=0 shuts down even after red tests" "$(emulator_is_up)" "down"
assert_eq "and does not disturb the exit code" "$status" "1"

out="$(run_wrapper pass "$full_run" KEEP_EMULATOR=TRUE)"
assert_eq "a truthy spelling means what it reads like, not its opposite" "$(emulator_is_up)" "up"

# An unrecognised value silently doing the opposite of what it reads like is the trap
# this guards: it falls back to the default and says so, rather than guessing.
out="$(run_wrapper pass "$full_run" KEEP_EMULATOR=maybe)"
assert_contains "an unrecognised KEEP_EMULATOR is called out, not guessed at" "$out" "ignoring KEEP_EMULATOR='maybe'"
assert_eq "and the outcome-based default still applies" "$(emulator_is_up)" "down"

echo ""
echo "── file_mtime returns an integer on this platform's stat ──────────────"
# The one function the whole watchdog rests on: its result is fed straight into
# arithmetic. GNU's `stat -f` means --file-system (prints '?', exits 0) while BSD's
# means "format", so a wrong probe order silently yields a non-integer here — and
# under `set -u` that kills the shell mid-run rather than failing loudly.
sed -n '/^file_mtime()/,/^}/p' "$WRAPPER" >"$tmp/file_mtime.sh"
# shellcheck disable=SC1090
. "$tmp/file_mtime.sh"
probe="$(file_mtime "$WRAPPER")"
case "$probe" in
  '' | *[!0-9]*) fail "file_mtime returned a non-integer: [$probe]" ;;
  0) fail "file_mtime returned 0 for a file that exists" ;;
  *) pass "file_mtime returns an epoch integer ($probe)" ;;
esac
assert_eq "file_mtime returns 0 for a missing file" "$(file_mtime /nonexistent/path)" "0"

echo ""
if [ "$fails" -eq 0 ]; then
  echo "✔ all watchdog behaviour tests passed"
  exit 0
fi
echo "✘ $fails watchdog behaviour test(s) failed"
exit 1
