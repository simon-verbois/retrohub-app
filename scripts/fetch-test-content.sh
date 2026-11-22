#!/usr/bin/env bash
# Upstream's public PSP graphics demo, pinned and packaged only in the test APK.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
checksum=15fa6f30d2fdc70a67b1cae646f82e6a4187d473ca4882c4631dc5b3fb43fc1d
cache="${XDG_CACHE_HOME:-$HOME/.cache}/retrohub-android/test-content"
mkdir -p "$cache" "$root/app/src/androidTest/assets"
if [[ ! -f "$cache/$checksum" ]]; then
  temporary="$(mktemp "$cache/cube.XXXXXX")"
  trap 'rm -f "$temporary"' EXIT
  curl -fsSL --retry 3 https://www.ppsspp.org/files/cube.elf -o "$temporary"
  printf '%s  %s\n' "$checksum" "$temporary" | sha256sum --check --status
  mv "$temporary" "$cache/$checksum"
fi
printf '%s  %s\n' "$checksum" "$cache/$checksum" | sha256sum --check --status
install -m 644 "$cache/$checksum" "$root/app/src/androidTest/assets/psp-cube.elf"
