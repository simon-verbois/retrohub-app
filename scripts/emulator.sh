#!/usr/bin/env bash
# Android test emulator: the SDK emulator (AVD "retrohub-test", API 35, x86_64, KVM),
# rendered by the host GPU. See docs/decisions.md: docker-android and SwiftShader rendering
# crash on this machine (kernel 7.1); "host" rendering works.
#   scripts/emulator.sh up      starts the emulator (headless; RETROHUB_EMULATOR_WINDOW=1 to show it)
#   scripts/emulator.sh wait    waits for full boot and configures the device for tests
#   scripts/emulator.sh down    stops the emulator
#   scripts/emulator.sh logs    follows the emulator log
#   scripts/emulator.sh serial  prints the adb serial number
#   scripts/emulator.sh windowed  true if the emulator is running with its window
set -euo pipefail
source "$(dirname "$0")/env.sh"

AVD="${RETROHUB_AVD:-retrohub-test}"
IMAGE="system-images;android-35;google_apis;x86_64"
PORT="${RETROHUB_EMULATOR_PORT:-5580}"
SERIAL="emulator-$PORT"
TIMEOUT="${RETROHUB_EMULATOR_TIMEOUT:-300}"
STATE="${XDG_STATE_HOME:-$HOME/.local/state}/retrohub-android"
# One set of files per port: a CI emulator never touches the one used for playing.
LOG="$STATE/emulator-$PORT.log"
PIDFILE="$STATE/emulator-$PORT.pid"
WINDOWFILE="$STATE/emulator-$PORT.window"
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
avdmanager="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"

ensure_avd() {
  [[ -x "$ANDROID_HOME/emulator/emulator" ]] || "$sdkmanager" --sdk_root="$ANDROID_HOME" emulator
  [[ -d "$ANDROID_HOME/system-images/android-35/google_apis/x86_64" ]] || "$sdkmanager" --sdk_root="$ANDROID_HOME" "$IMAGE"
  if ! "$ANDROID_HOME/emulator/emulator" -list-avds | grep -qx "$AVD"; then
    echo no | "$avdmanager" create avd -n "$AVD" -k "$IMAGE" -d pixel_6 >/dev/null
    # Phone screen, 4 GB of RAM, physical keyboard for navigation tests.
    cat >>"$HOME/.android/avd/$AVD.avd/config.ini" <<CONFIG
hw.ramSize=4096
hw.cpu.ncore=4
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=host
CONFIG
  fi
}

running() {
  [[ -f "$PIDFILE" ]] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null && return
  # Started some other way (Android Studio, another script): adb sees it on this port.
  adb -s "$SERIAL" get-state >/dev/null 2>&1
}

up() {
  if running; then
    echo "The emulator is already running ($SERIAL)."
    return
  fi
  [[ -w /dev/kvm ]] || { echo "/dev/kvm is not accessible: the emulator would be far too slow." >&2; exit 1; }
  ensure_avd
  mkdir -p "$STATE"
  local window=(-no-window) wipe=(-wipe-data)
  [[ "${RETROHUB_EMULATOR_WINDOW:-0}" == 1 ]] && window=()
  # Tests start from a clean device; RETROHUB_EMULATOR_WIPE=0 keeps the data (manual use).
  [[ "${RETROHUB_EMULATOR_WIPE:-1}" == 0 ]] && wipe=()
  nohup "$ANDROID_HOME/emulator/emulator" "@$AVD" -port "$PORT" -accel on -gpu host -no-snapshot -no-boot-anim \
    -no-audio "${wipe[@]}" "${window[@]}" >"$LOG" 2>&1 &
  echo $! >"$PIDFILE"
  echo "${RETROHUB_EMULATOR_WINDOW:-0}" >"$WINDOWFILE"
  echo "Emulator started ($SERIAL). Log: $LOG"
}

wait_ready() {
  local deadline=$((SECONDS + TIMEOUT))
  echo -n "Waiting for the emulator"
  until [[ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    running || { echo; echo "The emulator stopped. See $LOG" >&2; exit 1; }
    ((SECONDS < deadline)) || { echo; echo "The emulator did not start in time." >&2; exit 1; }
    echo -n "."
    sleep 3
  done
  echo
  # Stable tests: no animations, screen on, manual rotation, landscape by default.
  adb -s "$SERIAL" shell settings put global window_animation_scale 0
  adb -s "$SERIAL" shell settings put global transition_animation_scale 0
  adb -s "$SERIAL" shell settings put global animator_duration_scale 0
  adb -s "$SERIAL" shell settings put system accelerometer_rotation 0
  adb -s "$SERIAL" shell settings put secure show_ime_with_hard_keyboard 1
  adb -s "$SERIAL" shell settings put secure immersive_mode_confirmations confirmed
  adb -s "$SERIAL" shell svc power stayon true
  adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP
  adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  echo "Emulator ready: $SERIAL"
}

down() {
  adb -s "$SERIAL" emu kill >/dev/null 2>&1 || true
  if [[ -f "$PIDFILE" ]] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
    local pid
    pid="$(cat "$PIDFILE")"
    for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill "$pid" 2>/dev/null || true
  fi
  rm -f "$PIDFILE" "$WINDOWFILE"
  echo "Emulator stopped."
}

case "${1:-}" in
  up) up ;;
  wait) wait_ready ;;
  down) down ;;
  logs) tail -f "$LOG" ;;
  serial) echo "$SERIAL" ;;
  windowed) running && [[ "$(cat "$WINDOWFILE" 2>/dev/null)" == 1 ]] ;;
  *) echo "Usage: $0 up|wait|down|logs|serial" >&2; exit 2 ;;
esac
