#!/usr/bin/env bash
#
# Build, install, and run the local instrumented UI test suite.
#
# Why a wrapper instead of `./gradlew app:connectedDebugAndroidTest`:
#   `:feature_addbutton` is a dynamic feature module (install-time delivery in
#   production). The base `installDebug` task only pushes `base.apk`, so any
#   AddButtonActivity test would crash with ClassNotFoundException. This script
#   builds the debug bundle, uses bundletool to install every split for the
#   connected device, then delegates to Gradle (skipping the base-only install)
#   so we still get the standard HTML / XML test reports.
#
# Requirements: a booted emulator or device, Android SDK CLI on PATH (for `adb`),
# a JDK on PATH (for `java`).
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

BUNDLETOOL_VERSION="1.18.3"
BUNDLETOOL_JAR="build/tools/bundletool-${BUNDLETOOL_VERSION}.jar"
BUNDLETOOL_URL="https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/bundletool-all-${BUNDLETOOL_VERSION}.jar"

# AGP signs debug builds (including the androidTest APK) with the standard Android
# debug keystore at ~/.android/debug.keystore. Bundletool MUST sign the bundle with
# the same keystore — otherwise `adb install -r` of the test APK afterwards trips
# INSTALL_FAILED_UPDATE_INCOMPATIBLE because the signatures don't match. The repo's
# debug_keystore.keystore is reserved for the release build's fallback signing.
KEYSTORE="$HOME/.android/debug.keystore"
KEYSTORE_PASS="android"
KEYSTORE_ALIAS="androiddebugkey"

BUNDLE_PATH="app/build/outputs/bundle/debug/app-debug.aab"
APKS_PATH="build/outputs/test.apks"
UNIVERSAL_APK_PATH="build/outputs/test-universal.apk"
TEST_APK_PATH="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "✘ '$1' not found on PATH." >&2
    exit 1
  fi
}

require_cmd adb
require_cmd java
require_cmd curl

# 1. Pre-checks: at least one device/emulator must be online.
if ! adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
  echo "✘ No device/emulator detected by adb. Boot the AVD first:" >&2
  echo "    ~/Library/Android/sdk/emulator/emulator -avd push_me_test -no-snapshot-save -no-boot-anim &" >&2
  echo "    adb wait-for-device shell 'while [[ \$(getprop sys.boot_completed) != 1 ]]; do sleep 1; done'" >&2
  exit 1
fi

# 2. Bundletool: download once, reuse afterwards.
if [ ! -f "$BUNDLETOOL_JAR" ]; then
  echo "→ Fetching bundletool ${BUNDLETOOL_VERSION}"
  mkdir -p "$(dirname "$BUNDLETOOL_JAR")"
  curl --fail --location --silent --show-error --output "$BUNDLETOOL_JAR" "$BUNDLETOOL_URL"
fi

if [ ! -f "$KEYSTORE" ]; then
  echo "✘ Debug keystore '$KEYSTORE' is missing." >&2
  echo "  Build the app once via Android Studio (or './gradlew app:assembleDebug')" >&2
  echo "  to let AGP create it, then re-run this script." >&2
  exit 1
fi

# 3. Build the bundle (base + features) and the androidTest APK.
echo "→ Building debug bundle + androidTest APK"
./gradlew app:bundleDebug app:assembleDebugAndroidTest

# 4. Generate a universal APK from the bundle. Splits mode (`--connected-device`)
#    leaves the dynamic feature dex outside the runtime classloader visible to
#    the test process, so the AddButton tests never find their Activity class.
#    Universal fuses everything into one APK that installs and loads cleanly.
echo "→ Generating universal APK from bundle"
mkdir -p "$(dirname "$APKS_PATH")"
java -jar "$BUNDLETOOL_JAR" build-apks \
  --bundle="$BUNDLE_PATH" \
  --output="$APKS_PATH" \
  --mode=universal \
  --ks="$KEYSTORE" \
  --ks-pass="pass:${KEYSTORE_PASS}" \
  --ks-key-alias="$KEYSTORE_ALIAS" \
  --key-pass="pass:${KEYSTORE_PASS}" \
  --overwrite
unzip -p "$APKS_PATH" universal.apk > "$UNIVERSAL_APK_PATH"

# 5. Install the fused app + the instrumentation APK. AGP's UTP-based
#    `connectedDebugAndroidTest` would re-install just the base APK before
#    running, undoing the universal install — so we run the tests via
#    `am instrument` directly and skip Gradle's test runner entirely. We
#    still get raw stdout (parsed below) plus an XML report.
APP_ID="com.github.barriosnahuel.vossosunboton.debug"
TEST_PKG="${APP_ID}.test"
INSTRUMENTATION="${TEST_PKG}/androidx.test.runner.AndroidJUnitRunner"

adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb uninstall "$TEST_PKG" >/dev/null 2>&1 || true

echo "→ Installing universal APK"
adb install -r "$UNIVERSAL_APK_PATH"

echo "→ Installing test APK"
adb install -r "$TEST_APK_PATH"

# 6. Run the suite and tee both stdout and a stable copy under build/reports.
REPORT_DIR="app/build/reports/androidTests/local-ui"
mkdir -p "$REPORT_DIR"
RAW_LOG="$REPORT_DIR/raw.txt"

echo "→ Running instrumentation"
adb shell am instrument -w -r --no-window-animation "$@" "$INSTRUMENTATION" \
  | tee "$RAW_LOG"

echo
echo "✔ Suite finished. Raw output: $RAW_LOG"

# 7. Decode pass/fail from AndroidJUnitRunner status codes:
#    1 start, 0 ok, -2 failure, -3 @Ignore, -4 Assume.* skipped.
PASSED=$(grep -c "INSTRUMENTATION_STATUS_CODE: 0" "$RAW_LOG" || true)
FAILED=$(grep -c "INSTRUMENTATION_STATUS_CODE: -2" "$RAW_LOG" || true)
IGNORED=$(grep -c "INSTRUMENTATION_STATUS_CODE: -3" "$RAW_LOG" || true)
ASSUMED=$(grep -c "INSTRUMENTATION_STATUS_CODE: -4" "$RAW_LOG" || true)
SKIPPED=$((IGNORED + ASSUMED))

printf "\nResults: %d passed, %d failed, %d skipped\n" \
  "$PASSED" "$FAILED" "$SKIPPED"

if [ "$FAILED" -gt 0 ]; then
  echo "✘ Suite FAILED"
  exit 1
fi
echo "✔ Suite PASSED"
