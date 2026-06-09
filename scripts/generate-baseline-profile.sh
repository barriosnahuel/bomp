#!/usr/bin/env bash
#
# Regenerates the committed Baseline Profile for the LandingActivity cold-start path
# (app/src/main/baseline-prof.txt), which AGP bakes into the release/benchmark APK.
#
# We deliberately do NOT use the androidx.baselineprofile plugin — it would auto-create variants that
# overlap our `benchmark` build type and rewire the :macrobenchmark module (consolidation is a deferred
# follow-up; see handoff). Instead the profile is generated here and committed. It must reflect the
# REAL RELEASE startup path with REAL (non-obfuscated) names (so AGP remaps them through R8 when it
# bakes the profile into the minified release), so generation runs against the `nonMinifiedRelease`
# build type: the real release code (its own CustomBuildTypeApplication, mirroring release) but
# un-minified. Generating against `debug`
# would capture debug-only code (StrictMode, seeder, debug Application); against the minified
# `benchmark` it would capture obfuscated names. Device type doesn't matter for generation — the
# profile is a code-path snapshot; a real device is only needed to VALIDATE timing afterwards.
#
# Run from anywhere in the repo. Set ANDROID_SERIAL to pick a device when several are attached.
# Regenerate when the startup path changes meaningfully. See CONTRIBUTING § Baseline Profile.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

TEST_PKG=com.github.barriosnahuel.vossosunboton.macrobenchmark
TEST_APK=macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk
DEST=app/src/main/baseline-prof.txt
REMOTE="/storage/emulated/0/Android/media/$TEST_PKG/BaselineProfileGenerator_generate-baseline-prof.txt"

# Resolve a single target device unless ANDROID_SERIAL is already set. Read `adb devices` ONCE so a
# device (dis)connecting between the count and the pick can't leave us with an empty serial.
if [ -z "${ANDROID_SERIAL:-}" ]; then
  serials="$(adb devices | awk 'NR>1 && $2=="device"{print $1}')"
  n="$(printf '%s\n' "$serials" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [ "$n" != "1" ]; then
    echo "✘ Need exactly one attached device, or set ANDROID_SERIAL (found $n)." >&2
    exit 1
  fi
  ANDROID_SERIAL="$serials"
  export ANDROID_SERIAL
fi
echo "▶ Device: $ANDROID_SERIAL"

# Fresh nonMinifiedRelease install: the profile must be captured against release code with REAL names,
# never the minified benchmark build that may currently occupy the .debug package.
echo "▶ Installing the nonMinifiedRelease build + the benchmark test apk…"
./gradlew :app:installNonMinifiedRelease :macrobenchmark:assembleBenchmark -q
adb -s "$ANDROID_SERIAL" install -r "$TEST_APK" >/dev/null

echo "▶ Generating the profile (cold-start collect)…"
# Delete any profile left by a previous run first: `am instrument` exits 0 even when the test fails
# (it only prints FAILURES!!!), so without this a failed generation would silently pull the STALE file.
adb -s "$ANDROID_SERIAL" shell rm -f "$REMOTE"
# `|| true`: am instrument exits non-zero when the instrumentation can't even START (e.g. a renamed
# class → INSTRUMENTATION_FAILED); without it `set -e` would kill the script at this assignment, before
# the diagnostic below ever prints. The OK/FAILURE verdict is read from the captured output instead.
instr_out="$(adb -s "$ANDROID_SERIAL" shell am instrument -w \
  -e class "$TEST_PKG.BaselineProfileGenerator" \
  "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" 2>&1 || true)"
echo "$instr_out" | tail -3
echo "$instr_out" | grep -q 'OK (1 test)' || {
  echo "✘ generation failed (am instrument did not report OK) — see output above." >&2
  exit 1
}

echo "▶ Pulling → $DEST"
adb -s "$ANDROID_SERIAL" pull "$REMOTE" "$DEST" >/dev/null
# The profile is only usable if it carries the real release startup path with REAL (non-obfuscated)
# names that AGP can remap through R8. A mere prefix check is NOT enough: R8 keeps the manifest
# components (MainApplication, LandingActivity) by name even in a MINIFIED build, so an accidentally
# profiled minified `.debug` (benchmark) build would still match the prefix. Require a high COUNT of
# app rules instead — nonMinifiedRelease yields ~2000+, a minified build only the handful of kept
# components. Fail loudly rather than commit a no-op (obfuscated) profile.
app_rules="$(grep -c 'Lcom/github/barriosnahuel/vossosunboton/' "$DEST" || true)"
if [ "${app_rules:-0}" -lt 200 ]; then
  echo "✘ profile has only ${app_rules:-0} app rules — a MINIFIED build was profiled (R8 keeps manifest" >&2
  echo "  components by name but obfuscates the rest). Generation must run against nonMinifiedRelease;" >&2
  echo "  check nothing else holds the .debug package." >&2
  exit 1
fi
echo "✓ $DEST updated ($(grep -c . "$DEST" || echo 0) lines). Review the diff, then commit."
echo "  Validate on a REAL device (emulators report inverted AOT numbers):"
echo "    ANDROID_SERIAL=<real-device> ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \\"
echo "      -Pandroid.testInstrumentationRunnerArguments.class=$TEST_PKG.StartupBenchmark"
