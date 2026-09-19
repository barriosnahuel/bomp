#!/usr/bin/env bash
#
# Runs TapLatencyBenchmark on a physical device, but only once the device can actually produce a
# number — and says which condition stopped it when it cannot.
#
# Why a wrapper instead of the Gradle task: every failure mode below was paid for in a single
# session (2026-09-17), ~20 min of builds thrown away on conditions adb answers in two seconds.
# Worse, most of them do not name themselves. A locked screen surfaces 15 s in as "Playable seeded
# sound didn't render within 15000 ms" — the symptom, never the cause. A low battery is reported by
# AndroidX only AFTER the ~4 min build. A versionCode downgrade (measuring an older tag as a
# control) dies at install time. None of them are code problems, and all of them read like one.
#
# Exit codes — the contract callers key off. Same spirit as run-instrumented-tests.sh: only ONE of
# them means "go look at the app".
#   0   a valid measurement — numbers printed, row ready to paste
#   1   the benchmark ran and failed for a REAL reason (no samples, the seeded row never rendered
#       on an unlocked device, the app crashed) — a code problem
#   2   refused, or invalidated mid-run — the bench could not measure. NOT a regression, NOT a red.
#       Fix the condition named in the message and re-run
#   3   the build/install never reached the benchmark — no numbers exist to read
#
# Deliberate omission vs. the sibling: there is no 124. That code exists there because a cold-booted
# emulator can hang with its adb socket open; this runs against a physical device with no cold boot,
# under the harness's own timeouts. Also note the `2` here is WIDER than the sibling's ("the emulator
# never booted"): here it is any condition that made the bench unable to measure.
#
# Usage:
#   ./scripts/run-tap-latency.sh                 one gated run
#   ANDROID_SERIAL=<serial> ./scripts/run-tap-latency.sh
#
# From a worktree checked out at an OLDER TAG (the control side of a comparison) this script does
# not exist there — invoke it by absolute path from the primary worktree:
#   /path/to/push-me/scripts/run-tap-latency.sh
#
# Thresholds are env-overridable, which is also how the failure paths are exercised without
# provoking the condition: MIN_BATTERY_PERCENT, MAX_THERMAL_STATUS, REQUIRED_LOCALE_PREFIX.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

# The numbers printed here get pasted into RESULTS.md, whose table uses a decimal POINT. awk and
# printf honour the shell's locale, so on an es-AR machine they emit "47,9" and quietly corrupt the
# committed series. Pin the numeric locale rather than trusting the environment.
export LC_ALL=C

MIN_BATTERY_PERCENT="${MIN_BATTERY_PERCENT:-30}"   # AndroidX aborts under 25 even while charging
MAX_THERMAL_STATUS="${MAX_THERMAL_STATUS:-1}"      # 0 NONE, 1 LIGHT, 2 MODERATE…
REQUIRED_LOCALE_PREFIX="${REQUIRED_LOCALE_PREFIX:-en}"
TARGET_PKG="com.github.barriosnahuel.vossosunboton.debug"
BENCH_CLASS="com.github.barriosnahuel.vossosunboton.macrobenchmark.TapLatencyBenchmark"

EXIT_OK=0; EXIT_REAL=1; EXIT_REFUSED=2; EXIT_NO_RUN=3

# shellcheck source=scripts/device-resolve.sh
. "$(dirname "${BASH_SOURCE[0]}")/device-resolve.sh"
# shellcheck source=scripts/bench-history-dir.sh
. "$(dirname "${BASH_SOURCE[0]}")/bench-history-dir.sh"

die() { echo "✘ $*" >&2; exit "$EXIT_REFUSED"; }
say() { echo "▶ $*"; }
warn() { echo "⚠ $*" >&2; }

adbs() { adb -s "$ANDROID_SERIAL" "$@"; }

# ── P0-P1: repo + tooling + config ────────────────────────────────────────────────────────────
command -v adb >/dev/null || die "'adb' not found on PATH."
./scripts/check-profileable-google-services.sh

# ── P2-P4: which device, and is it one we can measure ─────────────────────────────────────────
resolve_android_serial || exit "$EXIT_REFUSED"
[ "$(adbs get-state 2>/dev/null || echo unknown)" = "device" ] \
  || die "Device $ANDROID_SERIAL is not ready (unauthorized/offline?)."
if android_serial_is_emulator; then
  die "$ANDROID_SERIAL is an emulator. TapLatencyBenchmark is physical-device-only: emulator
  audio/disk starvation makes latency numbers meaningless (TapLatencyBenchmark KDoc; ADR 0015)."
fi

HISTORY_DIR="$(resolve_bench_history_dir)" || die "Not in a git repo — cannot resolve the history dir."
mkdir -p "$HISTORY_DIR"

# ── Device lock ───────────────────────────────────────────────────────────────────────────────
# Not a file lock: the danger is two RUNS sharing one phone. The two-worktree flow this measurement
# needs invites exactly that ("I have two checkouts, I'll run both and halve the time"), and the
# single-device check cannot see it — both runs see one device and both proceed, contaminating each
# other's numbers. mkdir is atomic and portable (macOS has no flock(1)).
LOCK_DIR="$HISTORY_DIR/.lock-$(printf '%s' "$ANDROID_SERIAL" | tr -c 'A-Za-z0-9_.-' '_')"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  holder="$(cat "$LOCK_DIR/holder" 2>/dev/null || echo 'unknown')"
  die "Device $ANDROID_SERIAL is already being measured by $holder.
  Two runs on one phone contaminate each other's numbers. Wait, or remove $LOCK_DIR if stale."
fi
printf 'pid %s from %s at %s\n' "$$" "$(pwd)" "$(date '+%Y-%m-%d %H:%M:%S')" > "$LOCK_DIR/holder"

# ── Anti-sleep: normalise on entry, not just restore on exit ──────────────────────────────────
# A trap cannot survive SIGKILL. If a previous run was killed, the phone is still pinned awake,
# drains overnight, and the NEXT run fails the battery check — 20 minutes lost looking at a cause
# with no visible link to the effect. So the known-good value is persisted once and re-applied at
# every start. (capture-store-screenshots.sh sets stayon and never restores it, so "whatever the
# device says now" is not a trustworthy baseline.)
STAY_ON_FILE="$HISTORY_DIR/.stay-on-original"
if [ ! -f "$STAY_ON_FILE" ]; then
  adbs shell settings get global stay_on_while_plugged_in 2>/dev/null | tr -d '\r\n' > "$STAY_ON_FILE" || true
fi
STAY_ON_ORIGINAL="$(cat "$STAY_ON_FILE" 2>/dev/null || echo 0)"
case "$STAY_ON_ORIGINAL" in ''|*[!0-9]*) STAY_ON_ORIGINAL=0 ;; esac

cleanup_on_exit() {
  adb -s "$ANDROID_SERIAL" shell settings put global stay_on_while_plugged_in "$STAY_ON_ORIGINAL" >/dev/null 2>&1 || true
  rm -rf "$LOCK_DIR" 2>/dev/null || true
}
trap 'cleanup_on_exit; exit 130' INT TERM
trap cleanup_on_exit EXIT

# ── P5: device identity (and the OS-drift warning) ────────────────────────────────────────────
SDK="$(adbs shell getprop ro.build.version.sdk | tr -d '\r\n')"
BUILD_ID="$(adbs shell getprop ro.build.id | tr -d '\r\n')"
RELEASE="$(adbs shell getprop ro.build.version.release | tr -d '\r\n')"
MODEL="$(adbs shell getprop ro.product.model | tr -d '\r\n')"
[ "${SDK:-0}" -ge 28 ] 2>/dev/null || die "API $SDK is below 28; the benchmark needs 28+ (29+ for reliable metrics)."

LAST_BUILD_FILE="$HISTORY_DIR/.last-build-id"
if [ -f "$LAST_BUILD_FILE" ]; then
  last_build="$(cat "$LAST_BUILD_FILE")"
  if [ "$last_build" != "$BUILD_ID" ]; then
    warn "The last run on this device was on OS build $last_build; you are now on $BUILD_ID.
  Numbers from before that line are NOT comparable — re-measure your baseline ref on today's OS
  before reading any delta (CONTRIBUTING § Performance)."
  fi
fi

# ── P6: battery ───────────────────────────────────────────────────────────────────────────────
BATTERY="$(adbs shell dumpsys battery 2>/dev/null | sed -n 's/.*level: *\([0-9]*\).*/\1/p' | head -1)"
if [ -z "$BATTERY" ]; then
  warn "Could not read the battery level — skipping that check rather than guessing."
elif [ "$BATTERY" -lt "$MIN_BATTERY_PERCENT" ]; then
  die "Battery at ${BATTERY}%. AndroidX Macrobenchmark aborts below 25%
  (DeviceInfo.MINIMUM_BATTERY_PERCENT) — and it reports that only AFTER the ~4 min build. Below
  that the platform parks the big cores even while plugged in, so the numbers would be wrong even
  if nothing aborted. Charge to ${MIN_BATTERY_PERCENT}% and re-run.
  Do NOT pass androidx.benchmark.suppressErrors=LOW-BATTERY: that suppresses the error, not the
  throttling."
fi

# ── P7: thermal ───────────────────────────────────────────────────────────────────────────────
THERMAL="$(adbs shell dumpsys thermalservice 2>/dev/null | sed -n 's/.*mStatus= *\([0-9]*\).*/\1/p' | head -1)"
if [ -z "$THERMAL" ]; then
  warn "Could not read the thermal status — skipping that check rather than guessing."
elif [ "$THERMAL" -gt "$MAX_THERMAL_STATUS" ]; then
  die "Thermal status $THERMAL, above the allowed $MAX_THERMAL_STATUS — the SoC is throttling.
  Every number from this run would be high, and the next run would disagree with it. Let it cool
  ~5 min and re-run."
fi

# ── P8: awake and unlocked (try to fix it first) ──────────────────────────────────────────────
is_locked() { adbs shell dumpsys window 2>/dev/null | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true"; }
is_asleep() { ! adbs shell dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake"; }
if is_asleep || is_locked; then
  adbs shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  sleep 1
  adbs shell input keyevent KEYCODE_MENU >/dev/null 2>&1 || true
  sleep 1
fi
if is_locked; then
  die "Device is locked (keyguard showing) and KEYCODE_WAKEUP did not dismiss it.
  The app would launch behind the keyguard, the seeded row would never render, and the run would
  die 15 s later as \"Playable seeded sound didn't render within 15000 ms\" — which names the
  symptom, never this. Unlock the device, leave it on the home screen, and re-run."
fi
# `true`, not `usb`: `stayon usb` only holds the screen while charging over USB, and this phone
# charges wirelessly — the guard silently did not apply and the device locked mid-run anyway.
# `true` covers AC, USB and wireless alike.
adbs shell svc power stayon true >/dev/null 2>&1 || true

# ── P10: locale ───────────────────────────────────────────────────────────────────────────────
LOCALE="$(adbs shell getprop persist.sys.locale | tr -d '\r\n')"
[ -n "$LOCALE" ] || LOCALE="$(adbs shell getprop ro.product.locale | tr -d '\r\n')"
case "$LOCALE" in
  "$REQUIRED_LOCALE_PREFIX"*) ;;
  *) die "Device locale is '$LOCALE'. The benchmark finds the Play control by its ENGLISH content
  description (\"Play\" — BenchmarkTargets.PLAY_BUTTON_DESC), so it would fail mid-run with
  \"No 'Play' button below the seeded row\". Switch the device to English and re-run." ;;
esac

# ── P11: versionCode skew (the control-side downgrade) ────────────────────────────────────────
LOCAL_VC="$(sed -n 's/.*versionCode[ =]*\([0-9][0-9]*\).*/\1/p' app/build.gradle | head -1)"
INSTALLED_VC="$(adbs shell dumpsys package "$TARGET_PKG" 2>/dev/null | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)"
if [ -n "$INSTALLED_VC" ] && [ -n "$LOCAL_VC" ] && [ "$INSTALLED_VC" -gt "$LOCAL_VC" ]; then
  say "Installed $TARGET_PKG is versionCode $INSTALLED_VC; this checkout builds $LOCAL_VC.
  Uninstalling first — a downgrade install dies with INSTALL_FAILED_VERSION_DOWNGRADE about 4 min
  into the run, after the build. (Normal when measuring an older tag as a control.)"
  adbs uninstall "$TARGET_PKG" >/dev/null 2>&1 || true
  adbs uninstall "com.github.barriosnahuel.vossosunboton.macrobenchmark" >/dev/null 2>&1 || true
fi

# ── P12: what exactly is being measured ───────────────────────────────────────────────────────
HEAD_SHA="$(git rev-parse --short HEAD)"
HEAD_DESC="$(git describe --tags --always 2>/dev/null || echo "$HEAD_SHA")"
DIRTY=false
# `git status` does NOT see skip-worktree files, and the google-services.json overrides are exactly
# that — so a "clean" tree is no proof the build inputs match. Hash the diff and the configs.
[ -z "$(git status --porcelain)" ] || DIRTY=true
DIFF_HASH="$(git diff HEAD | git hash-object --stdin | cut -c1-12)"
CONFIG_HASH="$(cat app/src/benchmark/google-services.json app/src/debug/google-services.json 2>/dev/null | git hash-object --stdin | cut -c1-12)"
[ "$DIRTY" = false ] || warn "Working tree is dirty — this number will be attributed to $HEAD_SHA, which does not contain it (diff $DIFF_HASH)."

say "Device: $MODEL · Android $RELEASE (API $SDK, $BUILD_ID) · battery ${BATTERY:-?}% · thermal ${THERMAL:-?}"
say "Measuring: $HEAD_DESC ($HEAD_SHA${DIRTY:+, dirty}) · configs $CONFIG_HASH"

# ── Run ───────────────────────────────────────────────────────────────────────────────────────
LOG="$(mktemp -t tap-latency)"
set +e
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -P android.testInstrumentationRunnerArguments.class="$BENCH_CLASS" "$@" 2>&1 | tee "$LOG"
GRADLE_RC="${PIPESTATUS[0]}"
set -e

JSON="$(find macrobenchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' 2>/dev/null | head -1)"

# ── Post-run reclassification: re-probe before deciding whose fault it was ────────────────────
if [ "$GRADLE_RC" -ne 0 ]; then
  if ! adbs get-state >/dev/null 2>&1; then
    die "The device disconnected during the run (adb no longer sees $ANDROID_SERIAL). Nothing was
  measured. This is the bench, not the app — reconnect and re-run."
  fi
  if grep -q "LOW-BATTERY" "$LOG"; then
    now="$(adbs shell dumpsys battery | sed -n 's/.*level: *\([0-9]*\).*/\1/p' | head -1)"
    die "Battery fell to ${now}% during the run (started at ${BATTERY}%). Charge and re-run."
  fi
  if grep -q "didn't render within" "$LOG"; then
    if is_locked; then
      die "The device locked itself mid-run, so the seeded row never rendered. Not the app."
    fi
    echo "✘ The seeded row genuinely never rendered and the device was unlocked — this one is the app." >&2
    exit "$EXIT_REAL"
  fi
  if grep -q "No 'Play' button" "$LOG"; then
    die "The Play control was not found. Device locale is '$LOCALE' — the harness needs English."
  fi
  if grep -q "INSTALL_FAILED_VERSION_DOWNGRADE" "$LOG"; then
    echo "✘ Install failed as a downgrade despite the pre-check. Uninstall $TARGET_PKG and re-run." >&2
    exit "$EXIT_NO_RUN"
  fi
  if grep -q "never flushed profiles" "$LOG"; then
    echo "✘ The run died on Firebase init — see ./scripts/check-profileable-google-services.sh." >&2
    exit "$EXIT_NO_RUN"
  fi
  if ! grep -q "connectedBenchmarkAndroidTest" "$LOG"; then
    echo "✘ The build never reached the benchmark — no numbers to read." >&2
    exit "$EXIT_NO_RUN"
  fi
  echo "✘ The benchmark failed. See the log above." >&2
  exit "$EXIT_REAL"
fi

if [ -z "$JSON" ]; then
  echo "✘ Gradle succeeded but produced no benchmarkData.json — nothing to read." >&2
  exit "$EXIT_REAL"
fi

# ── Read the numbers (sed/awk, never python: the CI image has no parser — flaky-report.sh:65) ──
FLAT="$(tr -d ' \n' < "$JSON")"
jnum() { printf '%s' "$FLAT" | sed -n "s/.*\"$1\":\([0-9.eE+-]*\).*/\1/p" | head -1; }
MEDIAN="$(jnum median)"; MINIMUM="$(jnum minimum)"; MAXIMUM="$(jnum maximum)"
CV="$(jnum coefficientOfVariation)"; ITERS="$(jnum repeatIterations)"; SLEEP_S="$(jnum thermalThrottleSleepSeconds)"
SAMPLES="$(printf '%s' "$FLAT" | sed -n 's/.*"runs":\[\([^]]*\)\].*/\1/p' | head -1 | awk -F, '{print NF}')"

if [ -z "$MEDIAN" ]; then
  echo "✘ The JSON has no BompTapToSound metric — the trace span is probably gone or renamed." >&2
  exit "$EXIT_REAL"
fi

# ── Preserve the JSON: build/outputs is overwritten by the next run ───────────────────────────
STAMP="$(date '+%Y-%m-%dT%H-%M-%S')"
DEST="$HISTORY_DIR/$STAMP-$HEAD_SHA"
mkdir -p "$DEST" && cp "$JSON" "$DEST/benchmarkData.json"
printf '%s\n' "$BUILD_ID" > "$LAST_BUILD_FILE"

fmt() { awk -v v="$1" 'BEGIN{printf "%.1f", v}'; }
echo
echo "─ Tap latency · $MODEL (Android $RELEASE / API $SDK, $BUILD_ID) · $HEAD_DESC @ $HEAD_SHA"
echo "   min $(fmt "$MINIMUM")   median $(fmt "$MEDIAN")   max $(fmt "$MAXIMUM")   samples ${SAMPLES}/${ITERS}   cv $(awk -v v="$CV" 'BEGIN{printf "%.2f", v}')"
[ "${SAMPLES:-0}" = "${ITERS:-0}" ] || echo "   ⚠ ${SAMPLES} of ${ITERS} iterations produced a sample — some taps opened no BompTapToSound span; the median is over ${SAMPLES}."
[ "${SLEEP_S:-0}" = "0" ] || echo "   ⚠ the harness slept ${SLEEP_S}s to cool the device — thermally influenced."
awk -v m="$MEDIAN" 'BEGIN{ if (m<=100) printf "   budget ≤100 ms (ADR 0022): %.1f ✓\n", m; else printf "   budget ≤100 ms (ADR 0022): %.1f ✘ OVER\n", m }'
echo "   json → $DEST/benchmarkData.json"
echo
echo "   Row for macrobenchmark/RESULTS.md:"
echo "| $(date '+%Y-%m-%d') | $MODEL (Android $RELEASE / API $SDK, $BUILD_ID) | \`$HEAD_DESC\` @ \`$HEAD_SHA\` | TapLatencyBenchmark.tapToSoundFirstTap | $(fmt "$MINIMUM") | $(fmt "$MEDIAN") | $(fmt "$MAXIMUM") | $ITERS |"
echo
echo "   One run cannot see a small difference: this bench's own spread is wide (RESULTS.md)."
echo "   To compare two builds, alternate runs A/B/A/B in one session and read the medians together."
exit "$EXIT_OK"
