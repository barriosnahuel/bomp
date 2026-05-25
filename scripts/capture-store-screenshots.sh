#!/usr/bin/env bash
#
# Captures the Play Store phone screenshots from the running app, one set per locale, into
# store-listing/real-screenshots/ as raw 1080×2400 PNGs (later composed into the marketing SVGs).
#
# Why this exists instead of `connectedDebugAndroidTest`: that Gradle task UNINSTALLS the app after
# the run, taking the app-scoped screenshot files with it. This drives the capture with
# `am instrument` (no uninstall) so the PNGs survive to be pulled. And because the app reads the
# *system* locale (no AppCompat per-app override), each locale needs the emulator's system locale
# flipped + a reboot — done here with root `setprop persist.sys.locale` + `adb reboot`.
#
# Prereqs: a booted, ROOTABLE emulator (Google APIs image), the debug + androidTest APKs buildable.
# Run the AVD via ./scripts/run-instrumented-tests.sh first if you want a clean cold boot, or just
# have any emulator running. Usage:
#   ./scripts/capture-store-screenshots.sh
#   LOCALES="es-AR en-US pt-BR" ./scripts/capture-store-screenshots.sh   # override the locale set
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
read -r -a LOCALES <<<"${LOCALES:-es-AR en-US}"
PKG="com.github.barriosnahuel.vossosunboton.debug"
TEST_PKG="$PKG.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
CLASS="com.github.barriosnahuel.vossosunboton.screenshots.StoreScreenshotCaptureTest"
BOOT_TIMEOUT_SECONDS="${BOOT_TIMEOUT_SECONDS:-180}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_ROOT/store-listing/real-screenshots"
DEVICE_DIR="/sdcard/Android/data/$PKG/files/screenshots"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ -d "$SDK_ROOT" ]; then
  PATH="$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$PATH"
fi

adb_d() { adb -s "$SERIAL" "$@"; }

wait_for_boot() {
  adb_d wait-for-device
  local waited=0
  until [ "$(adb_d shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    if [ "$waited" -ge "$BOOT_TIMEOUT_SECONDS" ]; then
      echo "✘ device did not finish booting within ${BOOT_TIMEOUT_SECONDS}s" >&2
      exit 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
}

echo "→ Installing debug + androidTest APKs (no auto-uninstall)"
(cd "$REPO_ROOT" && ANDROID_SERIAL="$SERIAL" ./gradlew :app:installDebug :app:installDebugAndroidTest)

mkdir -p "$OUT_DIR"
for loc in "${LOCALES[@]}"; do
  echo "→ [$loc] setting system locale + rebooting"
  adb_d root >/dev/null 2>&1 || true
  sleep 2
  adb_d shell "setprop persist.sys.locale $loc"
  adb_d reboot
  wait_for_boot
  # A freshly-rebooted, loaded emulator can throw a "System UI isn't responding" ANR dialog that
  # dims the screen and lands in the capture. Suppress error dialogs and give SystemUI time to settle.
  adb_d shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
  adb_d shell wm dismiss-keyguard >/dev/null 2>&1 || true
  sleep 20
  echo "→ [$loc] capturing"
  adb_d shell am instrument -w -e class "$CLASS" "$TEST_PKG/$RUNNER"
done

echo "→ Pulling PNGs into $OUT_DIR"
adb_d pull "$DEVICE_DIR/." "$OUT_DIR/"
echo "✔ Done. Captured:"
ls -1 "$OUT_DIR"/*-??-??.png 2>/dev/null || ls -1 "$OUT_DIR"
