#!/usr/bin/env bash
#
# Runs the local instrumented UI test suite on a freshly cold-booted emulator.
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
# It also pins the emulator serial (-port 5554) and exports ANDROID_SERIAL, so
# `connectedDebugAndroidTest` targets the emulator only — never a physical
# device that happens to be plugged in (Gradle would otherwise run on all
# connected devices in parallel and corrupt the run).
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
# Environment overrides: AVD_NAME, RUNS, EMULATOR_PORT, BOOT_TIMEOUT_SECONDS.
#
# Requires the Android SDK CLI on PATH (emulator, adb) and the AVD created by
# ./scripts/setup-test-emulator.sh.
#
set -euo pipefail

AVD_NAME="${AVD_NAME:-Android_14_API_34}"
RUNS="${RUNS:-1}"
EMULATOR_PORT="${EMULATOR_PORT:-5554}"
BOOT_TIMEOUT_SECONDS="${BOOT_TIMEOUT_SECONDS:-300}"
EMULATOR_SERIAL="emulator-${EMULATOR_PORT}"

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

EMULATOR_LOG="${TMPDIR:-/tmp}/push-me-instrumented-emulator.log"

# Kills any running emulator instance backing $AVD_NAME and blocks until its
# serial is fully gone from `adb devices`. `adb emu kill` is asynchronous — the
# process keeps holding its console/adb port for a moment after the call
# returns — so reusing the port immediately makes the fresh `emulator -port`
# collide with the dying instance (adb then attaches to the ghost and the boot
# poll never completes). A different AVD the developer happens to be running is
# left untouched.
kill_existing_emulator() {
  local serial name killed=0
  for serial in $(adb devices | awk '/^emulator-/{print $1}'); do
    name="$(adb -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
    if [ "$name" = "$AVD_NAME" ]; then
      echo "→ Killing running emulator $serial ($AVD_NAME)"
      adb -s "$serial" emu kill >/dev/null 2>&1 || true
      killed=1
    fi
  done
  [ "$killed" -eq 0 ] && return 0

  local waited=0
  while adb devices | grep -qE "^${EMULATOR_SERIAL}[[:space:]]"; do
    if [ "$waited" -ge 60 ]; then
      echo "✘ $EMULATOR_SERIAL still present 60s after 'adb emu kill' — kill it manually" >&2
      exit 1
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
  # stdout/stderr -> $EMULATOR_LOG so a failed boot is diagnosable instead of silent.
  nohup emulator -avd "$AVD_NAME" -port "$EMULATOR_PORT" \
    -no-snapshot -wipe-data -no-boot-anim >"$EMULATOR_LOG" 2>&1 &

  ANDROID_SERIAL="$EMULATOR_SERIAL" adb wait-for-device
  local waited=0
  until [ "$(ANDROID_SERIAL="$EMULATOR_SERIAL" adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    if [ "$waited" -ge "$BOOT_TIMEOUT_SECONDS" ]; then
      echo "✘ $AVD_NAME did not finish booting within ${BOOT_TIMEOUT_SECONDS}s" >&2
      echo "  emulator log: $EMULATOR_LOG" >&2
      tail -20 "$EMULATOR_LOG" >&2 2>/dev/null || true
      exit 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
  echo "✔ $AVD_NAME booted ($EMULATOR_SERIAL)"
}

overall_status=0
for run in $(seq 1 "$RUNS"); do
  if [ "$RUNS" -gt 1 ]; then
    echo ""
    echo "================ instrumented run ${run} / ${RUNS} ================"
  fi
  kill_existing_emulator
  cold_boot_emulator

  echo "→ ANDROID_SERIAL=$EMULATOR_SERIAL ./gradlew :app:connectedDebugAndroidTest $*"
  if (cd "$REPO_ROOT" && ANDROID_SERIAL="$EMULATOR_SERIAL" ./gradlew :app:connectedDebugAndroidTest "$@"); then
    echo "✔ run ${run}/${RUNS} passed"
  else
    echo "✘ run ${run}/${RUNS} failed — report: app/build/reports/androidTests/connected/debug/index.html" >&2
    overall_status=1
  fi
done

if [ "$RUNS" -gt 1 ]; then
  echo ""
  if [ "$overall_status" -eq 0 ]; then
    echo "✔ all ${RUNS} runs passed"
  else
    echo "✘ at least one of ${RUNS} runs failed"
  fi
fi

exit "$overall_status"
