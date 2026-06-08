#!/usr/bin/env bash
#
# Regenerates the committed Baseline Profile for the LandingActivity cold-start path
# (app/src/main/baseline-prof.txt), which AGP bakes into the release/benchmark APK.
#
# We deliberately do NOT use the androidx.baselineprofile plugin — it would add
# nonMinifiedRelease/benchmarkRelease app variants that each need their own google-services.json.
# Instead the profile is generated here and committed. Rules must carry REAL (non-obfuscated) method
# names so AGP can remap them through R8 at release time, so generation runs against the NON-MINIFIED
# `debug` build (the minified `benchmark` build would bake obfuscated names that don't match a fresh
# release). Device type doesn't matter for generation — the profile is a code-path snapshot; a real
# device is only needed to VALIDATE timing afterwards.
#
# Run from anywhere in the repo. Set ANDROID_SERIAL to pick a device when several are attached.
# Regenerate when the startup path changes meaningfully. See CONTRIBUTING § Baseline Profile.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

TEST_PKG=com.github.barriosnahuel.vossosunboton.macrobenchmark
TEST_APK=macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk
DEST=app/src/main/baseline-prof.txt
REMOTE="/storage/emulated/0/Android/media/$TEST_PKG/BaselineProfileGenerator_generate-baseline-prof.txt"

# Resolve a single target device unless ANDROID_SERIAL is already set.
if [ -z "${ANDROID_SERIAL:-}" ]; then
  n="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
  if [ "$n" != "1" ]; then
    echo "✘ Need exactly one attached device, or set ANDROID_SERIAL (found $n)." >&2
    exit 1
  fi
  ANDROID_SERIAL="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
  export ANDROID_SERIAL
fi
echo "▶ Device: $ANDROID_SERIAL"

# Fresh non-minified debug install: the profile must be captured against REAL names, never the
# minified benchmark build that may currently occupy the .debug package.
echo "▶ Installing the non-minified debug build + the benchmark test apk…"
./gradlew :app:installDebug :macrobenchmark:assembleBenchmark -q
adb -s "$ANDROID_SERIAL" install -r "$TEST_APK" >/dev/null

echo "▶ Generating the profile (cold-start collect)…"
adb -s "$ANDROID_SERIAL" shell am instrument -w \
  -e class "$TEST_PKG.BaselineProfileGenerator" \
  "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner"

echo "▶ Pulling → $DEST"
adb -s "$ANDROID_SERIAL" pull "$REMOTE" "$DEST" >/dev/null
echo "✓ $DEST updated ($(grep -c . "$DEST") lines). Review the diff, then commit."
echo "  Validate on a REAL device (emulators report inverted AOT numbers):"
echo "    ANDROID_SERIAL=<real-device> ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \\"
echo "      -Pandroid.testInstrumentationRunnerArguments.class=$TEST_PKG.StartupBenchmark"
