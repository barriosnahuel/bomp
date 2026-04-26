#!/usr/bin/env bash
#
# Idempotently creates the AVD used by the local UI test suite.
# Run once per machine; re-running is a no-op when the AVD already exists.
#
# Requires the Android SDK CLI tools on PATH: sdkmanager, avdmanager, emulator.
# See: https://developer.android.com/tools
#
set -euo pipefail

AVD_NAME="push_me_test"
API_LEVEL="34"

ARCH="$(uname -m)"
case "$ARCH" in
  arm64|aarch64) IMG_ARCH="arm64-v8a" ;;
  x86_64|amd64)  IMG_ARCH="x86_64" ;;
  *)
    echo "✘ Unsupported host architecture: $ARCH" >&2
    exit 1
    ;;
esac
SYSTEM_IMAGE="system-images;android-${API_LEVEL};google_apis;${IMG_ARCH}"

# Auto-discover the Android SDK and prepend its tool dirs to PATH so each
# command (sdkmanager, avdmanager, emulator, adb) is reachable even if the
# user only has cmdline-tools on PATH.
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ -d "$SDK_ROOT" ]; then
  PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$PATH"
fi

for cmd in sdkmanager avdmanager emulator adb; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "✘ '$cmd' not found on PATH (looked under $SDK_ROOT). Install Android SDK command-line tools and platform-tools." >&2
    exit 1
  fi
done

if avdmanager list avd 2>/dev/null | grep -q "Name: ${AVD_NAME}$"; then
  echo "✔ AVD '${AVD_NAME}' already exists. Nothing to do."
  exit 0
fi

echo "→ Installing system image: ${SYSTEM_IMAGE}"
# `yes` keeps writing past sdkmanager's last prompt and dies with SIGPIPE — under
# `set -o pipefail` that fails the script even though the install succeeded. Send a
# bounded burst of "y" lines instead, enough to accept any pending licenses.
printf 'y\n%.0s' $(seq 1 50) | sdkmanager --install "${SYSTEM_IMAGE}" >/dev/null

echo "→ Creating AVD '${AVD_NAME}' (${IMG_ARCH}, API ${API_LEVEL})"
echo "no" | avdmanager create avd \
  --name "${AVD_NAME}" \
  --package "${SYSTEM_IMAGE}" \
  --device "pixel_6"

cat <<EOF
✔ AVD '${AVD_NAME}' ready.

Launch it before running the UI test suite:
  emulator -avd ${AVD_NAME} -no-snapshot-save -no-boot-anim &
  adb wait-for-device shell 'while [[ \$(getprop sys.boot_completed) != 1 ]]; do sleep 1; done'

Then run the suite:
  ./gradlew app:connectedDebugAndroidTest
EOF
