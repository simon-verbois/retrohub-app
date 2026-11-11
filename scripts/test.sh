#!/usr/bin/env bash
# App tests, by level:
#   scripts/test.sh unit           JVM tests (API contract, sync, emulation, UI), no emulator
#   scripts/test.sh instrumented   Room, WorkManager, controller navigation (emulator running)
#   scripts/test.sh e2e            full flow against the real RetroHub backend (emulator running)
#   scripts/test.sh all            all three, in this order (default)
# Start the emulator with scripts/emulator.sh up && scripts/emulator.sh wait.
# The e2e level starts the disposable backend (../retrohub/scripts/e2e-backend.py) itself if it does not respond.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
source scripts/env.sh

level="${1:-all}"
retrohub="${RETROHUB_REPO:-$root/../retrohub}"
server_port=8011
app_id="ovh.verbois.retrohub.debug"
abis="${RETROHUB_TEST_ABIS:-x86_64}"
variant="${RETROHUB_TEST_VARIANT:-debug}"
case "$variant" in debug|benchmark) ;; *) echo "Invalid test variant: $variant" >&2; exit 2 ;; esac
variant_task="${variant^}"
gradle=(./gradlew --console=plain "-Pretrohub.abis=$abis" "-Pretrohub.testVariant=$variant")
export ANDROID_SERIAL="${ANDROID_SERIAL:-$(scripts/emulator.sh serial)}"
backend_pid=""

cleanup() {
  if [[ -n "$backend_pid" ]]; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

need_device() {
  adb -s "$ANDROID_SERIAL" get-state >/dev/null 2>&1 || {
    echo "No emulator ($ANDROID_SERIAL). Run: scripts/emulator.sh up && scripts/emulator.sh wait" >&2
    exit 1
  }
}

ensure_cores() {
  [[ -f "app/src/main/jniLibs/x86_64/libfceumm_libretro_android.so" ]] || RETROHUB_ABIS="arm64-v8a x86_64" scripts/fetch-cores.sh
}

unit() {
  echo "== JVM unit tests"
  "${gradle[@]}" :shared:jvmTest
}

instrumented() {
  echo "== Instrumented tests (Room, WorkManager, controller navigation)"
  need_device
  ensure_cores
  local reports="app/build/outputs/androidTest-results/connected/$variant"
  rm -rf "$reports"
  local result=0
  "${gradle[@]}" :app:connected${variant_task}AndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    -Pandroid.testInstrumentationRunnerArguments.notPackage=ovh.verbois.retrohub.e2e || result=$?
  mkdir -p "build/screens-instrumented/$variant"
  adb -s "$ANDROID_SERIAL" pull "/sdcard/Android/data/$app_id/files/test-screens/." "build/screens-instrumented/$variant/" >/dev/null 2>&1 || true
  ((result == 0)) || return "$result"
  # Some runner crashes return a successful Gradle exit code: verify the actual reports too.
  if ! grep -q '<testcase ' "$reports"/TEST-*.xml ||
     grep -Eq '<failure|<error|failures="[1-9]|errors="[1-9]' "$reports"/TEST-*.xml; then
    echo "Instrumented tests did not complete successfully ($reports)." >&2
    exit 1
  fi
}

start_backend() {
  if curl -fsS "http://127.0.0.1:$server_port/api/v1/native/status" >/dev/null 2>&1; then
    echo "Test backend already running on :$server_port"
    return
  fi
  [[ -f "$retrohub/scripts/e2e-backend.py" ]] || { echo "RetroHub repository not found: $retrohub" >&2; exit 1; }
  mkdir -p build
  uv run --directory "$retrohub/backend" python "$retrohub/scripts/e2e-backend.py" >build/e2e-backend.log 2>&1 &
  backend_pid=$!
  for _ in $(seq 1 120); do
    curl -fsS "http://127.0.0.1:$server_port/api/v1/native/status" >/dev/null 2>&1 && return
    kill -0 "$backend_pid" 2>/dev/null || { echo "The test backend stopped (build/e2e-backend.log)." >&2; exit 1; }
    sleep 1
  done
  echo "The test backend is not responding." >&2
  exit 1
}

e2e() {
  echo "== End-to-end against the real backend"
  need_device
  ensure_cores
  start_backend
  adb -s "$ANDROID_SERIAL" reverse "tcp:$server_port" "tcp:$server_port"
  "${gradle[@]}" :app:install${variant_task} :app:install${variant_task}AndroidTest
  # Fresh app: first launch, pairing, initial settings.
  adb -s "$ANDROID_SERIAL" shell pm clear "$app_id" >/dev/null
  # The notification prompt (Android 13+) would cover the app during the flow.
  adb -s "$ANDROID_SERIAL" shell pm grant "$app_id" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb -s "$ANDROID_SERIAL" shell svc wifi enable
  adb -s "$ANDROID_SERIAL" shell svc data enable
  local output
  output="$(adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
    -e package ovh.verbois.retrohub.e2e \
    -e retrohubServer "http://127.0.0.1:$server_port" \
    "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r')"
  echo "$output" | grep -E "INSTRUMENTATION_STATUS: (test|stack)=|^OK|FAILURES|Tests run" | sed 's/INSTRUMENTATION_STATUS: //' | head -60
  # Full failure trace, to understand it without rerunning.
  echo "$output" | grep -Eq '^OK \([1-9][0-9]* tests?\)' || echo "$output" | sed -n '/stack=/,/INSTRUMENTATION_STATUS_CODE/p' | grep -E "^\s+at ovh\.|Caused by|stack=" | head -25
  rm -rf build/screens && mkdir -p build/screens
  adb -s "$ANDROID_SERIAL" pull "/sdcard/Android/data/$app_id/files/screens/." build/screens/ >/dev/null 2>&1 || true
  echo "Screenshots: $(ls build/screens 2>/dev/null | tr '\n' ' ')"
  if ! echo "$output" | grep -Eq '^OK \([1-9][0-9]* tests?\)'; then
    adb -s "$ANDROID_SERIAL" logcat -d -t 3000 > build/e2e-failure.log 2>&1 || true
  fi
  for screen in welcome-portrait setup-play-portrait setup-comfort-portrait setup-ready-portrait settings-ds-portrait appearance-portrait home-landscape library-landscape selected-landscape selected-portrait game-landscape game-portrait conflict-landscape signed-out-landscape; do
    [[ -s "build/screens/$screen.png" ]] || { echo "Missing end-to-end screenshot: $screen" >&2; exit 1; }
  done
  echo "$output" | grep -Eq '^OK \([1-9][0-9]* tests?\)' || { echo "End-to-end flow failed." >&2; exit 1; }
}

case "$level" in
  unit) unit ;;
  instrumented) instrumented ;;
  e2e) e2e ;;
  all) unit; instrumented; e2e ;;
  *) echo "Usage: $0 [unit|instrumented|e2e|all]" >&2; exit 2 ;;
esac
