#!/usr/bin/env bash
#
# Runs the local instrumented UI test suite on a freshly cold-booted emulator,
# under a watchdog that guarantees the run always terminates.
#
# Why a wrapper instead of `./gradlew :app:connectedDebugAndroidTest` directly:
# the suite (ADR 0001) is run repeatedly on a long-lived AVD, and a *warm*
# emulator degrades across back-to-back runs. system_server's
# InputManagerService watchdog eventually ANRs, Choreographer skips hundreds of
# frames, and the instrumentation loses contact with the app process — surfacing
# as ComposeTimeout / ComposeNotIdle flakes, "Process crashed", or the run
# failing to start at all. A cold boot with wiped userdata resets that state: a
# clean run finishes in ~3 min, a degraded one takes 15+ min or never completes.
# This wrapper guarantees every run starts from that clean state.
#
# Why a watchdog: "never completes" was literal. A frozen emulator whose adb
# socket stays open emits neither data nor EOF, so ddmlib's test runner blocks
# forever (its no-output timeout defaults to "wait indefinitely"), Gradle waits
# on ddmlib, and this script waited on Gradle. Nothing timed out and nothing
# printed — a hung run and a healthy one looked identical from the outside
# (silence), so a hang was only noticed hours later, by hand. The watchdog below
# makes termination bounded and the outcome self-announcing.
# Rationale: docs/adr/0001-local-ui-test-suite.md § Bounded termination.
#
# It also pins the emulator serial (-port 5554) and exports ANDROID_SERIAL, so
# `connectedDebugAndroidTest` targets the emulator only — never a physical
# device that happens to be plugged in (Gradle would otherwise run on all
# connected devices in parallel and corrupt the run).
#
# Exit codes — the contract callers (humans, agents, CI) key off. The point of
# having four is that only ONE of them means "go read the test report":
#   0   all runs passed
#   1   real test failures — a test is red; debug the test
#   124 stall or hard cap — the emulator hung; NOT a test failure; re-run
#   2   the emulator never booted
#   3   the build/install never reached the tests — no test report exists to read
#
# Usage:
#   ./scripts/run-instrumented-tests.sh
#       one clean run, full suite
#   ./scripts/run-instrumented-tests.sh \
#       -Pandroid.testInstrumentationRunnerArguments.class=com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest
#       single class — any extra args are passed through to Gradle
#   RUNS=3 ./scripts/run-instrumented-tests.sh
#       three runs, each preceded by its own cold boot (use to hunt flakes)
#
# Environment overrides: AVD_NAME, RUNS, EMULATOR_PORT, BOOT_TIMEOUT_SECONDS,
# EMULATOR_GPU, EMULATOR_CORES, EMULATOR_MEMORY, STALL_TIMEOUT_SECONDS,
# BUILD_STALL_TIMEOUT_SECONDS, HARD_CAP_SECONDS, WATCHDOG_POLL_SECONDS,
# GRADLE_TERM_GRACE_SECONDS, KEEP_EMULATOR.
#
# Requires the Android SDK CLI on PATH (emulator, adb) and the AVD created by
# ./scripts/setup-test-emulator.sh.
#
set -uo pipefail

AVD_NAME="${AVD_NAME:-Android_14_API_34}"
RUNS="${RUNS:-1}"
EMULATOR_PORT="${EMULATOR_PORT:-5554}"
BOOT_TIMEOUT_SECONDS="${BOOT_TIMEOUT_SECONDS:-300}"
EMULATOR_SERIAL="emulator-${EMULATOR_PORT}"

# Watchdog clocks. Calibrated from 8 cold-booted runs — docs/adr/0001 § Bounded
# termination.
#   STALL_TIMEOUT_SECONDS : no sign of life — no new TestRunner event, no new
#     Gradle output — for this long ⇒ the emulator is hung. The floor is NOT the
#     slowest honest test (35s measured); it is `timeout_msec` in app/build.gradle
#     (300s), because a test deadlocked on-device emits nothing until its own
#     timeout fires and names it. Undercut that and the watchdog kills the run
#     blind at the very moment the runner was about to hand us the culprit. Keep
#     STALL_TIMEOUT_SECONDS > timeout_msec, and move it if timeout_msec moves.
#   HARD_CAP_SECONDS : total wall clock for a single run, whatever it is doing.
#     Backstop for a hang that somehow keeps dribbling output — the STALL clock, not
#     this one, is the primary stall detector, so this stays deliberately generous.
#     Erring high only delays catching that exotic hang; erring low false-aborts a
#     healthy cold-cache/slow-network run as a hang — the failure mode the whole PR
#     exists to avoid. 2.3× the slowest complete run measured (1186s), and the 8
#     calibration runs were warm-cache same-commit, so they under-measure a cold build.
STALL_TIMEOUT_SECONDS="${STALL_TIMEOUT_SECONDS:-420}"
HARD_CAP_SECONDS="${HARD_CAP_SECONDS:-2700}"

# The build phase gets its own, far larger silence budget. Gradle's output is not a
# tty here, so there is no progress bar: dependency resolution on a slow network, a
# cold daemon, or a cold Kotlin/Compose compile are legitimately silent for minutes
# and would trip the test-phase clock. Applied to the build, that clock kills
# healthy runs — and cascades, since each kill leaves the next run with a cold
# daemon to rebuild from.
BUILD_STALL_TIMEOUT_SECONDS="${BUILD_STALL_TIMEOUT_SECONDS:-900}"

# How often the watchdog wakes to parse progress and check its clocks. Only the
# behaviour test overrides it, to keep its fake clocks in the seconds range.
WATCHDOG_POLL_SECONDS="${WATCHDOG_POLL_SECONDS:-5}"

# How long a killed Gradle gets to shut down cleanly on SIGTERM before SIGKILL.
GRADLE_TERM_GRACE_SECONDS="${GRADLE_TERM_GRACE_SECONDS:-15}"

# How long an emulator gets to honour `adb emu kill` before we SIGKILL its qemu
# process by AVD name. Overridable only so the behaviour test can turn it down.
EMULATOR_KILL_WAIT_SECONDS="${EMULATOR_KILL_WAIT_SECONDS:-60}"

# A poll loop that sleeps WATCHDOG_POLL_SECONDS and comes back to find *this* much
# wall clock gone did not run slowly — it did not run at all, because the host
# suspended. Comfortably above any plausible scheduling delay, comfortably below the
# stall budgets.
HOST_SLEEP_TICK_THRESHOLD="${HOST_SLEEP_TICK_THRESHOLD:-60}"

# Hardware/GPU knobs for the cold boot. The defaults stop the AVD from rendering
# in software and starving on too few cores/RAM (the main source of the watchdog
# ANRs / frozen frames the cold boot already fights). See docs/adr/0001 § Cold boot.
#   EMULATOR_GPU=host : host GPU (MoltenVK/Vulkan on Apple Silicon); "auto" is the safe fallback.
#   EMULATOR_CORES    : virtual CPU cores (AVD default is typically 2-4).
#   EMULATOR_MEMORY   : guest RAM in MB (AVD default is typically 2048).
EMULATOR_GPU="${EMULATOR_GPU:-host}"
EMULATOR_CORES="${EMULATOR_CORES:-6}"
EMULATOR_MEMORY="${EMULATOR_MEMORY:-4096}"

EXIT_OK=0
EXIT_TEST_FAILURE=1
EXIT_BOOT_FAILURE=2
EXIT_INFRA_FAILURE=3
EXIT_STALL=124

# Auto-discover the Android SDK and prepend its tool dirs to PATH (mirrors
# scripts/setup-test-emulator.sh) so the script works with only cmdline-tools on PATH.
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ -d "$SDK_ROOT" ]; then
  PATH="$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$PATH"
fi

for cmd in emulator adb; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "✘ '$cmd' not found on PATH (looked under $SDK_ROOT). Run ./scripts/setup-test-emulator.sh first." >&2
    exit 1
  fi
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Run from the repo root rather than wrapping Gradle in a `(cd … && …)` subshell:
# with a subshell, `$!` is the subshell's pid, and SIGKILLing that leaves the real
# Gradle — and the instrumentation it is driving — running orphaned against an
# emulator we are about to destroy.
cd "$REPO_ROOT" || exit 1

# The stall clock's floor is the runner's on-device per-test timeout, not the slowest
# test: a test deadlocked on-device is silent until timeout_msec fires and names it, so
# a STALL_TIMEOUT below it kills the run blind — exit 124 ("re-run") — exactly when the
# runner was about to hand over the culprit as a real red (exit 1). The env override can
# undercut that, so enforce it here rather than trust the default. Read from build.gradle
# so the two never drift (the same value flaky-report.sh checks in the run-history PR).
# If it can't be read, warn-less skip rather than clamp against a guessed floor.
runner_timeout_ms="$(sed -n "s/.*timeout_msec: '\([0-9]*\)'.*/\1/p" "$REPO_ROOT/app/build.gradle" 2>/dev/null)"
if [ -n "$runner_timeout_ms" ]; then
  RUNNER_TIMEOUT_SECONDS=$(( runner_timeout_ms / 1000 ))
  if [ "$STALL_TIMEOUT_SECONDS" -le "$RUNNER_TIMEOUT_SECONDS" ]; then
    echo "⚠ STALL_TIMEOUT_SECONDS=${STALL_TIMEOUT_SECONDS}s does not clear the on-device per-test timeout (${RUNNER_TIMEOUT_SECONDS}s); raising it to $((RUNNER_TIMEOUT_SECONDS + 60))s so a deadlocked test is named, not blind-killed" >&2
    STALL_TIMEOUT_SECONDS=$((RUNNER_TIMEOUT_SECONDS + 60))
  fi
fi

RUN_TMP="${TMPDIR:-/tmp}/push-me-instrumented"
mkdir -p "$RUN_TMP"
EMULATOR_LOG="$RUN_TMP/emulator.log"
GRADLE_LOG="$RUN_TMP/gradle.log"
LOGCAT_RAW="$RUN_TMP/testrunner-logcat.log"
HEARTBEAT="$RUN_TMP/heartbeat.log"
WATCHDOG_LOG="$RUN_TMP/watchdog.log"
DIAGNOSTICS_DIR="$RUN_TMP/diagnostics"

# ── portability helpers ────────────────────────────────────────────────────
# `timeout(1)` is GNU coreutils: present in the CircleCI Linux image, absent on a
# stock macOS host — where this suite actually runs. Both helpers are pure bash so
# the watchdog behaves identically in either place.

# file_mtime <path> — epoch seconds of last modification; 0 when absent.
#
# Order matters, and not for the reason it looks like. GNU's `-f` is
# `--file-system`, NOT BSD's "format" — given `-f %m` coreutils prints `?` and
# exits *zero*, so a BSD-first `||` chain never reaches the GNU branch and hands
# back a non-numeric `?`. BSD `stat` rejects `-c` outright (non-zero), so
# GNU-first falls through correctly on both. The result is fed straight into
# arithmetic, so it is validated rather than trusted: a non-integer here would
# either abort the watchdog mid-run or, under `set -u`, kill the shell.
file_mtime() {
  [ -e "$1" ] || { echo 0; return; }
  local m
  m="$(stat -c %Y "$1" 2>/dev/null || stat -f %m "$1" 2>/dev/null)"
  case "$m" in
    '' | *[!0-9]*) echo 0 ;;
    *) echo "$m" ;;
  esac
}

# run_bounded <seconds> <command...> — run a command, kill it if it outlives the
# budget; returns the command's status, or 124 when killed. Every adb call issued
# once we already suspect the device is wedged goes through this: an unbounded
# `adb shell` against a frozen emulator hangs exactly like the bug being fixed.
# Polls in fractions of a second so that bounding a *fast* call (the boot poll
# runs one every 2s) doesn't itself cost a second each time.
run_bounded() {
  local budget="$1"
  shift
  "$@" &
  local pid=$!
  local deadline=$(($(date +%s) + budget))
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$(date +%s)" -ge "$deadline" ]; then
      kill -9 "$pid" 2>/dev/null
      wait "$pid" 2>/dev/null
      return 124
    fi
    sleep 0.2
  done
  wait "$pid"
}

# Kills any running emulator instance backing $AVD_NAME and blocks until its
# serial is fully gone from `adb devices`. `adb emu kill` is asynchronous — the
# process keeps holding its console/adb port for a moment after the call
# returns — so reusing the port immediately makes the fresh `emulator -port`
# collide with the dying instance (adb then attaches to the ghost and the boot
# poll never completes). A different AVD the developer happens to be running is
# left untouched.
kill_existing_emulator() {
  local serial name
  local killed_serials=""
  for serial in $(adb devices | awk '/^emulator-/{print $1}'); do
    name="$(run_bounded 10 adb -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
    if [ "$name" = "$AVD_NAME" ]; then
      echo "→ Killing running emulator $serial ($AVD_NAME)"
      run_bounded 10 adb -s "$serial" emu kill >/dev/null 2>&1
      killed_serials="$killed_serials $serial"
    elif [ -z "$name" ] && [ "$serial" = "$EMULATOR_SERIAL" ]; then
      # `emu avd name` talks to the emulator console — the very thread that freezes
      # when the host sleeps mid-run. A timed-out probe leaves the name empty, so a
      # console-wedged emulator on OUR port would never be identified as ours and the
      # SIGKILL fallback below (written precisely for the wedged case) would never
      # run. A device on $EMULATOR_PORT that will not answer its console is almost
      # certainly our own AVD; mark it for the escalation. `emu kill` is skipped — it
      # needs the same dead console — and the pkill matches the qemu command line
      # (`-avd $AVD_NAME`), which does not, so it still cannot touch another AVD.
      echo "→ Emulator $serial on our port isn't answering its console — will SIGKILL by AVD name"
      killed_serials="$killed_serials $serial"
    fi
  done
  [ -z "$killed_serials" ] && return 0

  # Wait on the serials actually killed, not on $EMULATOR_SERIAL: if another AVD
  # happens to hold the default port, waiting on that serial would never end and
  # would then escalate to SIGKILL against a device that was never ours.
  local waited=0 still_up
  while true; do
    still_up=""
    for serial in $killed_serials; do
      adb devices | grep -qE "^${serial}[[:space:]]" && still_up="$still_up $serial"
    done
    [ -z "$still_up" ] && return 0

    if [ "$waited" -ge "$EMULATOR_KILL_WAIT_SECONDS" ]; then
      # A wedged emulator ignores `adb emu kill` — its console thread is exactly
      # what froze. SIGKILL the qemu process rather than leaving the operator to
      # clean up by hand, which is the state this whole watchdog exists to avoid.
      # Matched on the AVD name only: a port-based pattern would happily kill an
      # unrelated emulator the developer has on that port, which is precisely the
      # invariant this function promises to uphold.
      echo "→$still_up survived 'adb emu kill' after ${EMULATOR_KILL_WAIT_SECONDS}s — SIGKILLing the $AVD_NAME emulator process"
      pkill -9 -f "qemu-system.*${AVD_NAME}" 2>/dev/null
      pkill -9 -f "emulator.*-avd ${AVD_NAME}" 2>/dev/null
      sleep 5
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
}

# Cold-boots $AVD_NAME on $EMULATOR_PORT with wiped userdata and blocks until the
# device reports sys.boot_completed.
cold_boot_emulator() {
  echo "→ Cold-booting $AVD_NAME on port $EMULATOR_PORT (-no-snapshot -wipe-data)"
  # -no-snapshot  : ignore any saved snapshot and don't save one on exit — boot from scratch
  # -wipe-data    : reset userdata, the part that accumulates the cruft this wrapper exists to avoid
  # -no-boot-anim : shave a few seconds off the boot
  # -gpu/-cores/-memory : override the AVD's hardware-qemu.ini so the guest renders on the host
  #   GPU and gets enough CPU/RAM (defaults above). No -no-audio: this is a soundboard and the
  #   suite exercises playback, so the guest keeps its audio device.
  # stdout/stderr -> $EMULATOR_LOG so a failed boot is diagnosable instead of silent.
  nohup emulator -avd "$AVD_NAME" -port "$EMULATOR_PORT" \
    -no-snapshot -wipe-data -no-boot-anim \
    -gpu "$EMULATOR_GPU" -cores "$EMULATOR_CORES" -memory "$EMULATOR_MEMORY" \
    >"$EMULATOR_LOG" 2>&1 &

  # Bounded, like every other adb call. An unbounded `wait-for-device` blocks
  # forever when the emulator dies during launch (bad AVD, port already bound, no
  # HVF/KVM, corrupt snapshot) — it would hang before BOOT_TIMEOUT_SECONDS is ever
  # consulted and before the watchdog exists, reintroducing the exact
  # never-terminates bug this script promises to have removed.
  if ! run_bounded "$BOOT_TIMEOUT_SECONDS" adb -s "$EMULATOR_SERIAL" wait-for-device; then
    echo "✘ $EMULATOR_SERIAL never appeared to adb within ${BOOT_TIMEOUT_SECONDS}s" >&2
    echo "  emulator log: $EMULATOR_LOG" >&2
    tail -20 "$EMULATOR_LOG" >&2 2>/dev/null
    return "$EXIT_BOOT_FAILURE"
  fi
  local waited=0
  until [ "$(run_bounded 10 adb -s "$EMULATOR_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    if [ "$waited" -ge "$BOOT_TIMEOUT_SECONDS" ]; then
      echo "✘ $AVD_NAME did not finish booting within ${BOOT_TIMEOUT_SECONDS}s" >&2
      echo "  emulator log: $EMULATOR_LOG" >&2
      tail -20 "$EMULATOR_LOG" >&2 2>/dev/null
      return "$EXIT_BOOT_FAILURE"
    fi
    sleep 2
    waited=$((waited + 2))
  done
  echo "✔ $AVD_NAME booted ($EMULATOR_SERIAL)"
}

# ── progress heartbeat ─────────────────────────────────────────────────────
# AndroidJUnitRunner narrates itself to logcat under the TestRunner tag:
#   I TestRunner: run started: 130 tests
#   I TestRunner: started: pinsACustomSound(….SearchOverlayTest)
#   I TestRunner: finished: pinsACustomSound(….SearchOverlayTest)
#   E TestRunner: failed: …          (priority E — still matched by TestRunner:I)
# That stream is the only per-test progress signal that exists: Gradle prints
# nothing per test, so without it a healthy run and a hung one are both just
# silence. Parsing it gives the watchdog something to miss, the operator
# something to watch, and the run history something to record.
HEARTBEAT_TOTAL="?"
HEARTBEAT_INDEX=0
HEARTBEAT_LAST_TEST="(none yet)"
LOGCAT_CURSOR=0
LOGCAT_LAST_STARTED=""
# Which phase the run is in. Everything before the runner's `run started:` line is
# the build: compiling, dexing, installing two APKs. It is a different regime with
# a different silence budget — see the two clocks in supervise_gradle.
TESTS_STARTED=0

# Consumes the logcat lines that appeared since the last call, updating the
# heartbeat file and echoing progress. Reading incrementally from the raw capture
# — rather than piping through a background parser — keeps the watchdog a single
# loop with no extra processes to reap.
#
# The capture is being appended to *while* this reads it, which makes the window
# [cursor+1, total] explicit rather than "everything from cursor to EOF": `wc -l`
# counts newlines, so `total` is a count of complete lines, and `head` clamps the
# read to exactly those. Without the clamp, lines written between the count and
# the read would be consumed but not counted — and so replayed on the next pass,
# double-counting tests.
parse_new_logcat_lines() {
  [ -f "$LOGCAT_RAW" ] || return 0
  local total_lines
  total_lines=$(wc -l <"$LOGCAT_RAW" 2>/dev/null | tr -d ' ')
  [ -z "$total_lines" ] && return 0
  [ "$total_lines" -le "$LOGCAT_CURSOR" ] && return 0

  local line test_id
  while IFS= read -r line; do
    case "$line" in
      *"TestRunner: run started:"*)
        HEARTBEAT_TOTAL="$(echo "$line" | sed -n 's/.*run started: \([0-9]*\) tests.*/\1/p')"
        [ -z "$HEARTBEAT_TOTAL" ] && HEARTBEAT_TOTAL="?"
        TESTS_STARTED=1
        echo "→ suite started: $HEARTBEAT_TOTAL tests"
        printf '%s suite started: %s tests\n' "$(date '+%H:%M:%S')" "$HEARTBEAT_TOTAL" >>"$HEARTBEAT"
        ;;
      *"TestRunner: started:"*)
        # `started: method(fully.qualified.Class)` → `Class#method`
        test_id="$(echo "$line" | sed -n 's/.*started: \([^(]*\)(\(.*\)).*/\2#\1/p' | sed 's/^.*\.\([A-Za-z0-9_]*#\)/\1/')"
        [ -z "$test_id" ] && test_id="(unparsed)"
        # Dedup a replayed `started:`. A reattach after the stream died pulls the most
        # recent buffered line back in (adb `-T 1`), and adb prints its own
        # "--------- beginning of main" between it and the live tail, so a byte-adjacency
        # check would miss it. AndroidJUnitRunner never starts the same test twice, so a
        # `started:` whose id equals the last one counted is that replay — skip it, or
        # HEARTBEAT_INDEX runs past the total ("died at test 4/3").
        if [ "$test_id" != "(unparsed)" ] && [ "$test_id" = "$LOGCAT_LAST_STARTED" ]; then
          continue
        fi
        LOGCAT_LAST_STARTED="$test_id"
        HEARTBEAT_INDEX=$((HEARTBEAT_INDEX + 1))
        HEARTBEAT_LAST_TEST="$test_id"
        printf '%s %s/%s %s\n' "$(date '+%H:%M:%S')" "$HEARTBEAT_INDEX" "$HEARTBEAT_TOTAL" "$test_id" >>"$HEARTBEAT"
        printf '  %s/%s %s\n' "$HEARTBEAT_INDEX" "$HEARTBEAT_TOTAL" "$test_id"
        ;;
      *"TestRunner: failed:"*)
        printf '%s FAILED %s\n' "$(date '+%H:%M:%S')" "$HEARTBEAT_LAST_TEST" >>"$HEARTBEAT"
        printf '  ✘ FAILED %s\n' "$HEARTBEAT_LAST_TEST"
        ;;
    esac
  done < <(head -n "$total_lines" "$LOGCAT_RAW" | tail -n "+$((LOGCAT_CURSOR + 1))")

  LOGCAT_CURSOR="$total_lines"
}

# Attaches the TestRunner capture. `$1` carries extra logcat flags — empty on the
# first attach (the buffer was just cleared), `-T 1` when reattaching after the stream
# died, which bounds the replay to a single line instead of the whole ring buffer. That
# one line IS still replayed (adb `-T 1` prints the most recent line before following),
# so parse_new_logcat_lines dedups it by byte-equality — the two together are what keep
# a reattach from double-counting a test. Appends, so the cursor stays valid across it.
LOGCAT_PID=0
start_logcat_stream() {
  # shellcheck disable=SC2086 # $1 is a flag list, word-splitting is the point
  adb -s "$EMULATOR_SERIAL" logcat $1 -s TestRunner:I >>"$LOGCAT_RAW" 2>&1 &
  LOGCAT_PID=$!
}

# Gradle exits non-zero for far more than "a test is red": a Kotlin compile error,
# INSTALL_FAILED_*, an install timeout, "no connected devices", a daemon that
# disappeared, a device that vanished mid-run. Reporting all of those as exit 1
# ("debug the test") sends the operator to a test report that is empty or absent —
# the same wasted hour the exit-code contract exists to prevent. So the failure is
# classified by what actually happened, using two facts the watchdog already has:
# whether the tests ever started, and whether the device is still there.
classify_gradle_failure() {
  local status="$1"

  # Probe more than once before concluding the device is gone. Right after a non-zero
  # Gradle exit the machine is often loaded (a failing build/install churns adb), and a
  # single slow `adb shell true` (> 15s under contention) would misread a live device as
  # a hung one — turning a real compile/install/test failure into a spurious stall and
  # sending the operator to re-run instead of reading the actual error.
  local probe_alive=0 attempt
  for attempt in 1 2 3; do
    if run_bounded 15 adb -s "$EMULATOR_SERIAL" shell true >/dev/null 2>&1; then
      probe_alive=1
      break
    fi
    sleep 2
  done
  if [ "$probe_alive" -eq 0 ]; then
    echo "" >&2
    echo "✘ THE EMULATOR IS GONE — Gradle exited ${status} because the device stopped answering, not because a test failed." >&2
    return "$EXIT_STALL"
  fi

  if [ "$TESTS_STARTED" -eq 0 ]; then
    echo "" >&2
    echo "✘ BUILD/INSTALL FAILURE — Gradle exited ${status} before a single test ran. Not a test failure." >&2
    echo "  Gradle log: $GRADLE_LOG" >&2
    tail -15 "$GRADLE_LOG" >&2 2>/dev/null
    return "$EXIT_INFRA_FAILURE"
  fi

  return "$EXIT_TEST_FAILURE"
}

# ── stall diagnostics ──────────────────────────────────────────────────────
# Everything a post-mortem needs, captured *before* the emulator is killed —
# after the kill the evidence is gone. Bounded, because the device being
# interrogated is the one already suspected of being wedged.
dump_diagnostics() {
  local reason="$1"
  # Per-run, not a single fixed path: with RUNS=n the stalls *are* the data, and a
  # later stall must not wipe the evidence of an earlier one.
  DIAGNOSTICS_DIR="$RUN_TMP/diagnostics/run-${CURRENT_RUN:-1}"
  rm -rf "$DIAGNOSTICS_DIR"
  mkdir -p "$DIAGNOSTICS_DIR"

  {
    echo "reason:        $reason"
    echo "captured_at:   $(date '+%Y-%m-%dT%H:%M:%S%z')"
    echo "phase:         $([ "$TESTS_STARTED" -eq 1 ] && echo 'tests running' || echo 'build/install (tests had not started)')"
    echo "last_test:     $HEARTBEAT_LAST_TEST"
    echo "test_index:    $HEARTBEAT_INDEX/$HEARTBEAT_TOTAL"
    echo "stall_timeout: ${STALL_TIMEOUT_SECONDS}s (test phase) / ${BUILD_STALL_TIMEOUT_SECONDS}s (build phase)"
    echo "hard_cap:      ${HARD_CAP_SECONDS}s"
    if [ "${HOST_SLEPT_SECONDS:-0}" -gt 0 ]; then
      echo "host_slept:    ~${HOST_SLEPT_SECONDS}s — the machine suspended mid-run. The guest froze with it and"
      echo "               the instrumentation link may not survive the wake. Suspect the host, not the emulator:"
      echo "               check 'pmset -g log | grep Sleep'. The run was re-armed after the wake and still stalled."
    else
      echo "host_slept:    no — the machine stayed awake, so this is genuinely about the device or Gradle"
    fi
  } >"$DIAGNOSTICS_DIR/summary.txt"

  # Liveness probe: does the device answer at all? This separates "the emulator
  # froze" (probe times out) from "the device is fine, Gradle/ddmlib is wedged"
  # (probe answers) — two different bugs, indistinguishable from the outside
  # without asking.
  if run_bounded 15 adb -s "$EMULATOR_SERIAL" shell true >/dev/null 2>&1; then
    echo "device_probe:  ALIVE — adb shell answered; the device is up and Gradle/ddmlib is the stuck party" \
      >>"$DIAGNOSTICS_DIR/summary.txt"
    run_bounded 30 adb -s "$EMULATOR_SERIAL" logcat -d >"$DIAGNOSTICS_DIR/logcat-full.log" 2>&1
    run_bounded 30 adb -s "$EMULATOR_SERIAL" shell dumpsys activity processes >"$DIAGNOSTICS_DIR/dumpsys-processes.txt" 2>&1
  else
    echo "device_probe:  FROZEN — adb shell did not answer within 15s; the emulator itself is wedged" \
      >>"$DIAGNOSTICS_DIR/summary.txt"
  fi

  cp "$WATCHDOG_LOG" "$DIAGNOSTICS_DIR/watchdog.log" 2>/dev/null
  cp "$HEARTBEAT" "$DIAGNOSTICS_DIR/heartbeat.log" 2>/dev/null
  cp "$LOGCAT_RAW" "$DIAGNOSTICS_DIR/testrunner-logcat.log" 2>/dev/null
  cp "$GRADLE_LOG" "$DIAGNOSTICS_DIR/gradle.log" 2>/dev/null
  cp "$EMULATOR_LOG" "$DIAGNOSTICS_DIR/emulator.log" 2>/dev/null

  echo "  diagnostics: $DIAGNOSTICS_DIR" >&2
  sed 's/^/  /' "$DIAGNOSTICS_DIR/summary.txt" >&2
}

# Tears down a run the watchdog decided to kill.
#
# SIGTERM before SIGKILL, because SIGTERM to the Gradle client is what Ctrl-C
# sends: the client asks the daemon to cancel the build, so the daemon lets go of
# its ddmlib connection instead of being orphaned mid-instrumentation. `gradlew`
# is launched directly (not inside a subshell) precisely so this signal reaches
# Gradle and not a shell wrapper that would die while leaving Gradle running.
#
# Deliberately NOT `./gradlew --stop`: that stops every daemon on the machine for
# this Gradle version — including the one the developer's IDE is mid-sync on.
# Killing the emulator (the caller does that next) closes the adb socket, which is
# what actually frees a daemon wedged in ddmlib.
abort_run() {
  local logcat_pid="$1" gradle_pid="$2"
  CURRENT_GRADLE_PID=""
  CURRENT_LOGCAT_PID=""
  kill "$logcat_pid" 2>/dev/null
  wait "$logcat_pid" 2>/dev/null

  kill -TERM "$gradle_pid" 2>/dev/null
  local waited=0
  while kill -0 "$gradle_pid" 2>/dev/null; do
    if [ "$waited" -ge "$GRADLE_TERM_GRACE_SECONDS" ]; then
      kill -9 "$gradle_pid" 2>/dev/null
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
  wait "$gradle_pid" 2>/dev/null
}

# ── the watchdog ───────────────────────────────────────────────────────────
# Runs Gradle in the background and supervises it against two clocks.
#
# The subtlety is that a run has two regimes with very different silence budgets,
# and conflating them is how a watchdog starts killing healthy runs:
#
#   build phase  — Gradle configures, compiles, dexes and installs two APKs. Its
#     output is not a tty, so there is no progress bar: a cold daemon, a cold
#     Kotlin/Compose compile, or dependency resolution on a slow network can each
#     be genuinely silent for minutes. No TestRunner line exists yet, by
#     definition. Budget: BUILD_STALL_TIMEOUT_SECONDS, against Gradle's log alone.
#
#   test phase   — from the runner's `run started:` onward. Now TestRunner narrates
#     every test, so silence is meaningful: a gap here really does mean the guest
#     stopped executing. Budget: STALL_TIMEOUT_SECONDS, an order of magnitude
#     tighter, against the newest of the TestRunner capture or the Gradle log.
#
# Applying the tight test-phase budget to the build phase was a real bug, and a
# self-reinforcing one: killing a run leaves the next one with a cold daemon, whose
# slow silent build then trips the same clock — one stall cascading into a suite of
# fake ones. HARD_CAP_SECONDS backstops the whole thing.
# Clears the per-run heartbeat counters and truncates the per-run logs. Called at the
# TOP of every run — not just inside supervise_gradle — because a run that fails to boot
# skips supervision entirely, and without this reset it would carry the *previous* run's
# heartbeat index (inventing passes in the ledger) and archive the previous run's logs.
reset_heartbeat_state() {
  : >"$GRADLE_LOG"
  : >"$LOGCAT_RAW"
  : >"$HEARTBEAT"
  : >"$WATCHDOG_LOG"
  LOGCAT_CURSOR=0
  LOGCAT_LAST_STARTED=""
  HEARTBEAT_TOTAL="?"
  HEARTBEAT_INDEX=0
  HEARTBEAT_LAST_TEST="(none yet)"
  TESTS_STARTED=0
}

supervise_gradle() {
  local started_at now last_life gradle_life idle elapsed gradle_pid logcat_pid status
  local budget phase

  reset_heartbeat_state

  run_bounded 15 adb -s "$EMULATOR_SERIAL" logcat -c >/dev/null 2>&1
  start_logcat_stream ""
  logcat_pid=$LOGCAT_PID

  ANDROID_SERIAL="$EMULATOR_SERIAL" ./gradlew :app:connectedDebugAndroidTest "$@" >"$GRADLE_LOG" 2>&1 &
  gradle_pid=$!

  # Published so the EXIT trap can tear these down if the script is killed from
  # outside rather than deciding to stop on its own.
  CURRENT_GRADLE_PID="$gradle_pid"
  CURRENT_LOGCAT_PID="$logcat_pid"

  started_at=$(date +%s)
  local last_tick tick_gap
  last_tick=$(date +%s)
  HOST_SLEPT_SECONDS=0
  while true; do
    if ! kill -0 "$gradle_pid" 2>/dev/null; then
      wait "$gradle_pid"
      status=$?
      parse_new_logcat_lines
      kill "$logcat_pid" 2>/dev/null
      wait "$logcat_pid" 2>/dev/null
      CURRENT_GRADLE_PID=""
      CURRENT_LOGCAT_PID=""
      [ "$status" -eq 0 ] && return "$EXIT_OK"
      classify_gradle_failure "$status"
      return $?
    fi

    parse_new_logcat_lines

    # The heartbeat is only as alive as the process producing it. The adb server is
    # a single machine-wide daemon: anything that restarts it — Android Studio
    # opening, a `adb kill-server` in another terminal, a flaky USB/wireless device
    # dropping — takes this stream down with it, while the emulator carries on
    # perfectly happily. Left unnoticed, that frozen capture reads as a wedged
    # emulator and kills a healthy run. So the streamer's own death is treated as an
    # event to recover from, not as evidence about the device.
    if ! kill -0 "$logcat_pid" 2>/dev/null; then
      wait "$logcat_pid" 2>/dev/null
      echo "  ⚠ logcat stream died (adb server restart?) — reattaching; progress may skip a test" >&2
      printf '%s watchdog: logcat stream died, reattaching\n' "$(date '+%H:%M:%S')" >>"$WATCHDOG_LOG"
      # -T 1 bounds the replay to one line (without it the whole ring buffer replays);
      # that one line is deduped by byte-equality in parse_new_logcat_lines, so no test
      # already counted is counted again.
      start_logcat_stream "-T 1"
      logcat_pid=$LOGCAT_PID
      # Credit the run with a fresh sign of life ONLY if the device actually says
      # it is alive. Touching the capture unconditionally would fabricate that
      # signal — and a wedged device is exactly the case where the stream dies
      # repeatedly, so a blind touch would reset the clock on every retry and the
      # watchdog would never fire. The probe is the difference between "we know it
      # is alive" and "we stopped being able to see".
      if run_bounded 15 adb -s "$EMULATOR_SERIAL" shell true >/dev/null 2>&1; then
        touch "$LOGCAT_RAW"
      else
        printf '%s watchdog: device did not answer after stream death — letting the clock run\n' \
          "$(date '+%H:%M:%S')" >>"$WATCHDOG_LOG"
      fi
    fi

    now=$(date +%s)

    # Did the host sleep? Wall-clock time is not the same thing as "time this run
    # was given a chance to make progress". If the machine suspends — and a full
    # suite outlasts the default idle-sleep timer, so this is the common case when
    # you start a run and walk away — the guest freezes with it and every clock here
    # jumps forward without anything having gone wrong on the device.
    #
    # We can't see the suspend directly, but we can see its shadow: a poll loop that
    # sleeps 5s and finds that 900s elapsed did not stall — it was not running. That
    # gap is not evidence about the emulator, so it is discounted from the idle
    # budget rather than charged to it. Without this, a sleeping laptop reads as a
    # hung emulator, and the exit code lies about which thing is broken.
    tick_gap=$((now - last_tick))
    if [ "$tick_gap" -gt "$HOST_SLEEP_TICK_THRESHOLD" ]; then
      HOST_SLEPT_SECONDS=$((HOST_SLEPT_SECONDS + tick_gap))
      echo "  ⚠ the host appears to have slept for ~${tick_gap}s — not counting it against the emulator" >&2
      printf '%s watchdog: host slept ~%ss (clock jumped); discounting it from the idle budget\n' \
        "$(date '+%H:%M:%S')" "$tick_gap" >>"$WATCHDOG_LOG"
      # Re-arm from now: the device gets a fresh budget to prove it survived the wake.
      touch "$GRADLE_LOG"
    fi
    last_tick=$now

    last_life=$(file_mtime "$GRADLE_LOG")
    if [ "$TESTS_STARTED" -eq 1 ]; then
      gradle_life=$(file_mtime "$LOGCAT_RAW")
      [ "$gradle_life" -gt "$last_life" ] && last_life="$gradle_life"
      budget="$STALL_TIMEOUT_SECONDS"
      phase="test"
    else
      budget="$BUILD_STALL_TIMEOUT_SECONDS"
      phase="build/install"
    fi
    idle=$((now - last_life))

    # The watchdog's own pulse. Without it, a late-firing stall is ambiguous: did
    # the supervision loop itself stall, or did something keep refreshing the
    # liveness signal? Logged only once the run is already halfway to its budget,
    # so a healthy run writes nothing.
    if [ "$idle" -ge $((budget / 2)) ]; then
      printf '%s watchdog: %s phase, idle %ss / %ss, at test %s/%s\n' \
        "$(date '+%H:%M:%S')" "$phase" "$idle" "$budget" "$HEARTBEAT_INDEX" "$HEARTBEAT_TOTAL" >>"$WATCHDOG_LOG"
    fi

    if [ "$idle" -ge "$budget" ]; then
      echo "" >&2
      if [ "$TESTS_STARTED" -eq 1 ]; then
        echo "✘ EMULATOR STALL — no test progress for ${idle}s (limit ${budget}s)" >&2
        echo "  died at test ${HEARTBEAT_INDEX}/${HEARTBEAT_TOTAL}: ${HEARTBEAT_LAST_TEST}" >&2
      else
        echo "✘ BUILD STALL — Gradle produced no output for ${idle}s (limit ${budget}s) and the tests never started" >&2
      fi
      dump_diagnostics "stall in ${phase} phase: idle ${idle}s at test ${HEARTBEAT_INDEX}/${HEARTBEAT_TOTAL} (${HEARTBEAT_LAST_TEST})"
      abort_run "$logcat_pid" "$gradle_pid"
      return "$EXIT_STALL"
    fi

    # Subtract the host-sleep gap here too, not only from the idle clock: the hard cap
    # measures "time the run was given to make progress", and a host suspend is time the
    # run was not running. Charging it would kill a healthy run on wake with exit 124 —
    # the exact device-vs-host misattribution the idle-clock discount exists to prevent.
    elapsed=$((now - started_at - HOST_SLEPT_SECONDS))
    if [ "$elapsed" -ge "$HARD_CAP_SECONDS" ]; then
      echo "" >&2
      echo "✘ HARD CAP — run exceeded ${HARD_CAP_SECONDS}s (still producing output, but never finishing)" >&2
      echo "  reached test ${HEARTBEAT_INDEX}/${HEARTBEAT_TOTAL}: ${HEARTBEAT_LAST_TEST}" >&2
      dump_diagnostics "hard cap: ${elapsed}s elapsed at test ${HEARTBEAT_INDEX}/${HEARTBEAT_TOTAL} (${HEARTBEAT_LAST_TEST})"
      abort_run "$logcat_pid" "$gradle_pid"
      return "$EXIT_STALL"
    fi

    sleep "$WATCHDOG_POLL_SECONDS"
  done
}

# The watchdog cleans up after a run *it* decides to kill — but it can also be
# killed itself: Ctrl-C, a CI step timing out, a supervising agent giving up. Left
# alone, that abandons a Gradle process still driving instrumentation and an
# emulator still holding the port, so the next run collides with the wreckage of
# the last one. Whatever kills this script, the run it started dies with it.
CURRENT_GRADLE_PID=""
CURRENT_LOGCAT_PID=""
cleanup_on_exit() {
  [ -n "$CURRENT_LOGCAT_PID" ] && kill "$CURRENT_LOGCAT_PID" 2>/dev/null
  [ -n "$CURRENT_GRADLE_PID" ] && kill -TERM "$CURRENT_GRADLE_PID" 2>/dev/null
  return 0
}
trap 'cleanup_on_exit; exit 130' INT TERM
trap cleanup_on_exit EXIT

overall_status=$EXIT_OK
last_run_status=$EXIT_OK

# A stall in one run must not abort the remaining ones — when hunting a flake with
# RUNS=n, the stalls *are* the data. The worst outcome across runs wins, by an
# explicit precedence rather than "last one to speak":
#
#   stall (124) > test failure (1) > infra failure (3) > boot failure (2) > ok (0)
#
# A stall outranks everything because a hung emulator invalidates the run: its reds
# can't be trusted as test results. A *test* failure outranks the infra ones because
# it is the finding the operator most needs to act on — a run-2 boot failure must
# not overwrite a genuine red from run 1 and report the suite as merely broken
# infrastructure.
status_rank() {
  case "$1" in
    "$EXIT_STALL") echo 4 ;;
    "$EXIT_TEST_FAILURE") echo 3 ;;
    "$EXIT_INFRA_FAILURE") echo 2 ;;
    "$EXIT_BOOT_FAILURE") echo 1 ;;
    *) echo 0 ;;
  esac
}

record_status() {
  local status="$1"
  [ "$(status_rank "$status")" -gt "$(status_rank "$overall_status")" ] && overall_status="$status"
  # The emulator standing at the end belongs to the *last* run, and every run starts by
  # wiping and cold-booting a new one. So the decision to keep it must read this run's
  # outcome, not the worst-of-all: with RUNS=3 and only run 1 red, `overall_status` is
  # red while the surviving device is run 3's freshly-wiped green one — nothing to
  # inspect, and an invitation to inspect it would be a lie.
  last_run_status="$status"
  return 0
}

# Files this run away in the local history, whatever happened to it. Every outcome
# is recorded, not just the reds: a failure rate needs a denominator, and "how many
# times did this test pass" is exactly the thing you cannot reconstruct afterwards.
# Best-effort by design — losing the bookkeeping must never fail a run that is
# otherwise fine.
record_run_history() {
  local outcome="$1" started="$2"
  local script="$REPO_ROOT/scripts/record-instrumented-run.sh"
  [ -x "$script" ] || return 0
  BOMP_RUN_OUTCOME="$outcome" \
    BOMP_RUN_DURATION="$(($(date +%s) - started))" \
    BOMP_RUN_TMP="$RUN_TMP" \
    BOMP_RUN_LAST_TEST="$HEARTBEAT_LAST_TEST" \
    BOMP_RUN_INDEX="$HEARTBEAT_INDEX" \
    BOMP_RUN_TOTAL="$HEARTBEAT_TOTAL" \
    BOMP_RUN_HOST_SLEPT="${HOST_SLEPT_SECONDS:-0}" \
    BOMP_RUN_MARKER="$RUN_MARKER" \
    BOMP_RUN_XML_DIR="$REPO_ROOT/app/build/outputs/androidTest-results/connected/debug" \
    "$script" || true
}

# Stamped at the start of each run. Gradle leaves its result XML in place between
# runs, so a run that never reaches the tests (failed boot, build error) would
# otherwise be recorded against the *previous* run's results — inventing passes.
# The marker is what lets the recorder tell this run's output from last run's.
RUN_MARKER="$RUN_TMP/run.marker"

outcome_name() {
  case "$1" in
    "$EXIT_OK") echo pass ;;
    "$EXIT_TEST_FAILURE") echo failure ;;
    "$EXIT_STALL") echo stall ;;
    "$EXIT_INFRA_FAILURE") echo infra ;;
    "$EXIT_BOOT_FAILURE") echo boot ;;
    *) echo unknown ;;
  esac
}

# Hold off idle sleep for the whole session — armed here, before the first cold boot,
# so run 1's boot is covered too (not only the Gradle phase). A full run outlasts the
# default idle-sleep timer, so "start it and walk away" is exactly what suspends the
# host mid-run: the guest freezes, the instrumentation link dies on wake, and the hang
# looks like a wedged emulator but never was (ADR 0001 § Bounded termination). `-w $$`
# ties it to this script's life, so it needs no teardown. Absent on Linux (CI), hence
# the guard. Note this stops *idle* sleep only — a lid close still suspends, which is
# why the watchdog also discounts an observed sleep instead of trusting caffeinate alone.
if command -v caffeinate >/dev/null 2>&1; then
  caffeinate -i -w $$ &
fi

for run in $(seq 1 "$RUNS"); do
  CURRENT_RUN="$run"
  run_started_at=$(date +%s)
  # Anything in the XML dir older than this belongs to the previous run.
  touch "$RUN_MARKER"
  # Clear last run's heartbeat/logs first, so a boot failure here records zero tests
  # and its own (empty) forensics, not the previous run's index and logs.
  reset_heartbeat_state
  if [ "$RUNS" -gt 1 ]; then
    echo ""
    echo "================ instrumented run ${run} / ${RUNS} ================"
  fi
  kill_existing_emulator

  if ! cold_boot_emulator; then
    echo "✘ run ${run}/${RUNS}: emulator failed to boot" >&2
    record_status "$EXIT_BOOT_FAILURE"
    record_run_history boot "$run_started_at"
    continue
  fi

  supervise_gradle "$@"
  run_status=$?
  record_status "$run_status"
  record_run_history "$(outcome_name "$run_status")" "$run_started_at"

  case "$run_status" in
    "$EXIT_OK")
      echo "✔ run ${run}/${RUNS} passed"
      ;;
    "$EXIT_STALL")
      echo "✘ run ${run}/${RUNS} STALLED (emulator hung — not a test failure). Diagnostics: $DIAGNOSTICS_DIR" >&2
      # The emulator is wedged, not merely finished — leave nothing behind for the
      # next cold boot to collide with.
      kill_existing_emulator
      ;;
    "$EXIT_INFRA_FAILURE")
      echo "✘ run ${run}/${RUNS}: the build/install never reached the tests" >&2
      ;;
    *)
      echo "✘ run ${run}/${RUNS} failed — report: app/build/reports/androidTests/connected/debug/index.html" >&2
      ;;
  esac
done

# The AVD outlives the run only when there is something on it worth looking at, which
# is a narrower set than "the run was not green". Red *tests* qualify: the operator's
# first move is to open the app on that device and read the state that failed, and
# shutting it down destroys exactly that evidence. Nothing else does — a green run has
# nothing left to see; a boot failure never produced a usable device; an infra failure
# ran no test at all; and a stall's AVD is wedged (the run loop already killed it,
# since a frozen console cannot be inspected and its corpse would collide with the next
# cold boot's port). Everything outside that one case is reclaimed, because an emulator
# nobody is going to look at is just the host's RAM held hostage until someone notices.
# Deliberately not a `trap`: the INT/TERM handler exits before reaching this, so a run
# someone interrupted keeps its device — they hit Ctrl-C to go look at something.
# Rationale + overrides: docs/adr/0001-local-ui-test-suite.md § Emulator lifetime after the run.
shutdown_emulator_unless_inspectable() {
  local keep
  case "$(printf '%s' "${KEEP_EMULATOR:-}" | tr '[:upper:]' '[:lower:]')" in
    "")             keep=auto ;;
    1|true|yes)     keep=yes ;;
    0|false|no)     keep=no ;;
    *)
      echo "⚠ ignoring KEEP_EMULATOR='${KEEP_EMULATOR}' — expected 1/true/yes or 0/false/no" >&2
      keep=auto
      ;;
  esac
  if [ "$keep" = auto ]; then
    if [ "$last_run_status" = "$EXIT_TEST_FAILURE" ]; then keep=yes; else keep=no; fi
  fi

  if [ "$keep" = no ]; then
    kill_existing_emulator
    echo "→ $AVD_NAME emulator shut down"
  else
    # Always announced, never silent: an emulator left running is invisible until it is
    # competing for the host's RAM, and whoever ends up killing it is the one least
    # likely to know it is there. The serial is spelled out because a bare
    # `adb emu kill` has no target when a physical device or a second AVD is attached.
    echo "⚠ the $AVD_NAME emulator is still up so you can inspect the failure — 'adb -s $EMULATOR_SERIAL emu kill' when done (KEEP_EMULATOR=0 to always shut down)" >&2
  fi
}

echo ""
case "$overall_status" in
  "$EXIT_OK")
    if [ "$RUNS" -gt 1 ]; then echo "✔ all ${RUNS} runs passed"; else echo "✔ suite passed"; fi
    ;;
  "$EXIT_STALL")
    echo "✘ EMULATOR HUNG — the run never completed. This is NOT a test failure: re-run the suite." >&2
    echo "  Diagnostics: $RUN_TMP/diagnostics/" >&2
    ;;
  "$EXIT_BOOT_FAILURE")
    echo "✘ the emulator never booted — see $EMULATOR_LOG" >&2
    ;;
  "$EXIT_INFRA_FAILURE")
    echo "✘ BUILD/INSTALL FAILURE — no test ever ran, so there is no test report to read. See $GRADLE_LOG" >&2
    ;;
  *)
    echo "✘ test failures — report: app/build/reports/androidTests/connected/debug/index.html" >&2
    ;;
esac

shutdown_emulator_unless_inspectable

exit "$overall_status"
