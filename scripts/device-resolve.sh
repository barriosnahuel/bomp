#!/usr/bin/env bash
#
# Single source of truth for PICKING THE TARGET DEVICE, sourced by every script that drives one
# (generate-baseline-profile.sh, run-tap-latency.sh). Kept in one place for the same reason as
# instrumented-history-dir.sh: two copies of a rule drift, and here the drift is silent — a script
# that picks a different device than its sibling measures a different phone and nobody notices.
#
# `resolve_android_serial` exports ANDROID_SERIAL and returns 0, or prints why it could not and
# returns 1. It HONOURS an ANDROID_SERIAL already in the environment without second-guessing it:
# CONTRIBUTING § Performance documents that variable as the way to choose among several attached
# devices, and a benchmark refusing to run because an emulator happens to be up would fight the
# documented workflow.
#
# `adb devices` is read ONCE so a device (dis)connecting between the count and the pick cannot
# leave us with an empty serial.
resolve_android_serial() {
  if [ -n "${ANDROID_SERIAL:-}" ]; then
    export ANDROID_SERIAL
    return 0
  fi
  local serials n
  serials="$(adb devices | awk 'NR>1 && $2=="device"{print $1}')"
  n="$(printf '%s\n' "$serials" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [ "$n" != "1" ]; then
    echo "✘ Need exactly one attached device, or set ANDROID_SERIAL (found $n)." >&2
    return 1
  fi
  ANDROID_SERIAL="$serials"
  export ANDROID_SERIAL
  return 0
}

# True when the resolved serial is an emulator. Latency numbers from an emulator are meaningless
# (audio/disk starvation — TapLatencyBenchmark's KDoc, ADR 0015), so measuring scripts refuse it;
# the check lives here because the serial is resolved here.
android_serial_is_emulator() {
  case "${ANDROID_SERIAL:-}" in
    emulator-*) return 0 ;;
  esac
  # A physical device reached over adb-wireless has an mDNS-style serial, not `emulator-*`, so the
  # serial alone is not proof. Ask the device what it is.
  local qemu
  qemu="$(adb -s "${ANDROID_SERIAL}" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r\n')"
  [ "$qemu" = "1" ]
}
