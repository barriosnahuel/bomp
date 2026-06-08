#!/usr/bin/env bash
#
# Scroll-jank diagnosis on a CPU-constrained emulator with a REAL GPU — the "correct setup" for
# approximating a lower-tier device (ADR 0015 / CONTRIBUTING § Performance benchmarking).
#
# Why these knobs:
#   * `-gpu host` (NOT swiftshader): software rendering saturates the GPU (~98% jank, GPU P99 ~5 s)
#     and masks the app's real per-frame cost. A real GPU keeps the bottleneck on CPU/UI-thread/
#     composition — where our jank would actually live. Needs a window, so this runs windowed.
#   * `-cores 2 -memory 2048`: constrains CPU/RAM vs the host (the representable low-tier lever;
#     the emulator still runs on the host CPU, so numbers are directional — a real device is
#     authoritative).
#
# What it does, per list size (few/medium/many): cold-launch the benchmark build (applicationId
# `.debug`, real bomp-debug Firebase config so it doesn't FirebaseSessions-fatal), seed exactly N
# synthetic sounds, confirm the list rendered (never measure the launcher), reset `dumpsys gfxinfo`,
# fling with `adb input swipe`, then print the jank summary. Captures a Perfetto system trace on the
# largest size for per-frame root-causing (open in https://ui.perfetto.dev or Android Studio).
#
# Usage: ./scripts/measure-scroll-jank.sh
# Env overrides: AVD, SYS_IMG, CORES, MEMORY, DEVICE_PROFILE, SIZES (space-separated).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

AVD="${AVD:-cond_api30}"
SYS_IMG="${SYS_IMG:-system-images;android-30;google_apis;arm64-v8a}"
DEVICE_PROFILE="${DEVICE_PROFILE:-pixel_4}"
CORES="${CORES:-2}"
MEMORY="${MEMORY:-2048}"
read -r -a SIZES <<< "${SIZES:-20 50 200}"

PKG=com.github.barriosnahuel.vossosunboton.debug
ACT="$PKG/com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity"
APK=app/build/outputs/apk/benchmark/app-benchmark.apk
OUT=macrobenchmark/build/jank-diagnosis
mkdir -p "$OUT"

echo "▶ Building benchmark APK…"
./gradlew :app:assembleBenchmark -q

avdmanager list avd 2>/dev/null | grep -q "Name: $AVD" || {
  echo "▶ Creating AVD $AVD ($SYS_IMG, $DEVICE_PROFILE)…"
  echo "no" | avdmanager create avd -n "$AVD" -k "$SYS_IMG" -d "$DEVICE_PROFILE" --force
}

echo "▶ Launching $AVD (windowed, -gpu host, -cores $CORES, -memory $MEMORY)…"
# Snapshot existing emulators so we can identify (and only ever kill) the one WE launch.
before="$(adb devices | awk '/^emulator-/{print $1}')"
nohup emulator -avd "$AVD" -gpu host -cores "$CORES" -memory "$MEMORY" \
  -no-snapshot -no-boot-anim -no-audio > "$OUT/emulator.log" 2>&1 &
EMU_PID=$!
trap 'if [ -n "${S:-}" ]; then adb -s "$S" emu kill >/dev/null 2>&1 || true; fi; kill "$EMU_PID" 2>/dev/null || true' EXIT

# Resolve the serial of the emulator this script launched (not a pre-existing one), with a timeout
# and a process-alive check so a boot failure fails fast instead of hanging forever.
S=""
for _ in $(seq 1 90); do
  for d in $(adb devices | awk '/^emulator-/{print $1}'); do
    case " $before " in *" $d "*) : ;; *) S="$d"; break ;; esac
  done
  [ -n "$S" ] && break
  kill -0 "$EMU_PID" 2>/dev/null || { echo "✘ emulator exited during launch — see $OUT/emulator.log" >&2; exit 1; }
  sleep 3
done
[ -n "$S" ] || { echo "✘ launched emulator never registered (timeout) — see $OUT/emulator.log" >&2; exit 1; }

echo "▶ $S booting…"
for _ in $(seq 1 90); do
  [ "$(adb -s "$S" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  kill -0 "$EMU_PID" 2>/dev/null || { echo "✘ emulator died during boot — see $OUT/emulator.log" >&2; exit 1; }
  sleep 3
done
[ "$(adb -s "$S" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] \
  || { echo "✘ emulator boot timed out — see $OUT/emulator.log" >&2; exit 1; }
adb -s "$S" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
adb -s "$S" install -r "$APK" >/dev/null
echo "▶ Installed. Measuring sizes: ${SIZES[*]}"

last=${SIZES[${#SIZES[@]}-1]}
for N in "${SIZES[@]}"; do
  echo "===== N=$N ====="
  adb -s "$S" shell am force-stop "$PKG"
  adb -s "$S" shell am start -n "$ACT" --ei benchmark_seed_count "$N" >/dev/null 2>&1
  ok=0
  for _ in $(seq 1 40); do
    pid=$(adb -s "$S" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)
    adb -s "$S" exec-out uiautomator dump /sdcard/u.xml >/dev/null 2>&1 || true
    # `grep -c` exits 1 on zero matches — the normal "not rendered yet" state on every early poll.
    # `|| true` keeps `set -euo pipefail` from aborting the wait loop before the list shows up.
    rendered=$(adb -s "$S" shell cat /sdcard/u.xml 2>/dev/null | grep -c 'Benchmark sound' || true)
    [ -n "$pid" ] && [ "${rendered:-0}" -ge 1 ] && { ok=1; break; }
    sleep 2
  done
  [ "$ok" -ne 1 ] && { echo "N=$N: app not ready (pid=$pid rendered=$rendered) — SKIP"; continue; }

  trace=""
  if [ "$N" = "$last" ]; then
    trace=/data/misc/perfetto-traces/scroll.perfetto-trace
    adb -s "$S" shell "perfetto -o $trace -t 12s -b 64mb gfx view wm am sched freq" >/dev/null 2>&1 &
    sleep 1
  fi

  adb -s "$S" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  for _ in $(seq 1 8); do adb -s "$S" shell input swipe 540 1500 540 500 250; sleep 0.6; done
  sleep 1
  adb -s "$S" shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo-$N.txt" 2>&1
  grep -E 'Total frames|Janky frames|percentile|Number Missed Vsync|Number Slow' "$OUT/gfxinfo-$N.txt" | sed 's/^/   /' || true

  if [ -n "$trace" ]; then
    sleep 12
    adb -s "$S" pull "$trace" "$OUT/scroll-$N.perfetto-trace" >/dev/null 2>&1 \
      && echo "   Perfetto trace → $OUT/scroll-$N.perfetto-trace (open in ui.perfetto.dev)"
  fi
done
echo "✓ Done. Reports in $OUT/"
