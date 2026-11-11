#!/usr/bin/env bash
# Full pipeline, like a local CI: lint, unit tests, debug build, emulator, test backend,
# instrumented and end-to-end tests, screenshots. Cleans up everything, even on failure.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
source scripts/env.sh

started_emulator=false
cleanup() {
  local status=$?
  adb -s "$(scripts/emulator.sh serial)" reverse --remove-all >/dev/null 2>&1 || true
  if [[ "$started_emulator" == true ]]; then scripts/emulator.sh down || true; fi
  if ((status == 0)); then echo "✓ CI passed"; else echo "✗ CI failed ($status)" >&2; fi
}
trap cleanup EXIT

step() { echo; echo "━━ $*"; }

step "Prerequisites (SDK, JDK, libretro cores)"
scripts/setup-sdk.sh >/dev/null
scripts/fetch-cores.sh >/dev/null

step "Lint: ktlint (Spotless) and Android Lint"
./gradlew --console=plain spotlessCheck :app:lintDebug

step "JVM unit tests"
scripts/test.sh unit

step "Debug build (arm64-v8a and x86_64)"
./gradlew --console=plain :app:assembleDebug
ls -lh app/build/outputs/apk/debug/*.apk

step "Emulator"
if ! adb -s "$(scripts/emulator.sh serial)" get-state >/dev/null 2>&1; then
  scripts/emulator.sh up
  started_emulator=true
fi
scripts/emulator.sh wait

step "Instrumented tests"
scripts/test.sh instrumented

step "End-to-end against the real backend"
scripts/test.sh e2e
