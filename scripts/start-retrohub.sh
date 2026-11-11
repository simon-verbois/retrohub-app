#!/usr/bin/env bash
# Starts the emulator with its window, in landscape, and opens the RetroHub app in it (debug build).
#   scripts/start-retrohub.sh             builds, installs and launches the app
#   scripts/start-retrohub.sh --backend   also starts the disposable RetroHub server from ../retrohub
#                                         (http://127.0.0.1:8011, e2e-admin / test-browser-password-only)
#   scripts/start-retrohub.sh --fresh     clears the app data (first launch)
# The emulator stays open; scripts/emulator.sh down stops it.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
source scripts/env.sh

backend=false
fresh=false
for arg in "$@"; do
  case "$arg" in
    --backend) backend=true ;;
    --fresh) fresh=true ;;
    *) echo "Usage: $0 [--backend] [--fresh]" >&2; exit 2 ;;
  esac
done

app_id="ovh.verbois.retrohub.debug"
serial="$(scripts/emulator.sh serial)"
retrohub="${RETROHUB_REPO:-$root/../retrohub}"

echo "→ Setup (SDK, cores, build)"
scripts/setup-sdk.sh >/dev/null
RETROHUB_ABIS="arm64-v8a x86_64" scripts/fetch-cores.sh >/dev/null
./gradlew --console=plain -q :app:assembleDebug -Pretrohub.abis=x86_64

echo "→ Emulator"
if adb -s "$serial" get-state >/dev/null 2>&1 && ! scripts/emulator.sh windowed; then
  # A headless emulator (tests) is already running: restart it with its window.
  scripts/emulator.sh down >/dev/null
fi
# Visible window, data kept between launches.
RETROHUB_EMULATOR_WINDOW=1 RETROHUB_EMULATOR_WIPE=0 scripts/emulator.sh up
scripts/emulator.sh wait >/dev/null

# Restore system animations (tests turn them off): the app background is animated.
for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb -s "$serial" shell settings put global "$scale" 1
done
# Landscape, auto-rotation off.
adb -s "$serial" shell settings put system accelerometer_rotation 0
adb -s "$serial" shell settings put system user_rotation 1

if [[ "$backend" == true ]]; then
  echo "→ Disposable RetroHub server"
  if ! curl -fsS http://127.0.0.1:8011/api/v1/native/status >/dev/null 2>&1; then
    mkdir -p build
    nohup uv run --directory "$retrohub/backend" python "$retrohub/scripts/e2e-backend.py" >build/dev-backend.log 2>&1 &
    for _ in $(seq 1 120); do
      curl -fsS http://127.0.0.1:8011/api/v1/native/status >/dev/null 2>&1 && break
      sleep 1
    done
  fi
  echo "  Server: http://127.0.0.1:8011 (log build/dev-backend.log)"
fi
# The host server (port 8011) is reachable from the app at http://127.0.0.1:8011.
adb -s "$serial" reverse tcp:8011 tcp:8011 >/dev/null

echo "→ Install and launch"
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
[[ "$fresh" == true ]] && adb -s "$serial" shell pm clear "$app_id" >/dev/null
adb -s "$serial" shell am start -n "$app_id/ovh.verbois.retrohub.MainActivity" >/dev/null
echo "RetroHub is running on $serial, in landscape."
