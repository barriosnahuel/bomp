#!/usr/bin/env bash
#
# Installs the debug build and launches it with the debug-only "seed My Sounds" flag, so a clean
# debug install starts with a few sample bomps already saved to My Sounds instead of an empty
# board — saves manually re-adding real audios from WhatsApp after every reinstall.
#
# Debug-only: the release build ignores the flag entirely (the CustomBuildTypeApplication seam is a
# no-op). Seeding is idempotent — re-running never duplicates entries.
#
#   Usage: ./scripts/install-debug-seeded.sh
#
# With more than one device/emulator attached, set ANDROID_SERIAL (or pass `-s <serial>` via the
# ADB_OPTS env var) so adb targets the right one.
#
# The extra key below MUST match DebugSoundSeeder.EXTRA_SEED_MY_SOUNDS.

set -euo pipefail

cd "$(dirname "$0")/.."

APP_ID="com.github.barriosnahuel.vossosunboton.debug"
ACTIVITY="com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity"
SEED_EXTRA="debug_seed_my_sounds"

echo "==> Installing debug build…"
./gradlew :app:installDebug

echo "==> Launching with the seed flag…"
# shellcheck disable=SC2086 # ADB_OPTS is intentionally word-split into separate adb args.
adb ${ADB_OPTS:-} shell am start -n "$APP_ID/$ACTIVITY" --ez "$SEED_EXTRA" true

echo "✓ Debug build installed and launched — My Sounds is seeded with sample bomps."
