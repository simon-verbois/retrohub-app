#!/usr/bin/env bash
# Downloads the libretro cores (buildbot nightly) and the Dolphin Sys folder, checks their
# SHA-256 against cores.lock (tracked) and puts them in app/src/main/jniLibs/<abi>/ (ignored by Git).
#
#   scripts/fetch-cores.sh            installs exactly the versions in cores.lock
#   scripts/fetch-cores.sh --update   takes the latest nightlies and rewrites cores.lock
#
# The buildbot only keeps "latest": verified files are kept in a content-addressed cache
# (~/.cache/retrohub-android/cores) so builds can be reproduced exactly.
# Variables: RETROHUB_ABIS (default "arm64-v8a x86_64").
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
lock="$root/cores.lock"
jni="$root/app/src/main/jniLibs"
assets="$root/app/src/main/assets"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/retrohub-android/cores"
abis=(${RETROHUB_ABIS:-arm64-v8a x86_64})
# Same names as the RetroHub server (consoles.py) for X-Emulator-Core.
cores=(fceumm snes9x gambatte mgba genesis_plus_gx pcsx_rearmed swanstation melonds stella2014 parallel_n64 dolphin flycast)
base="https://buildbot.libretro.com/nightly/android/latest"
dolphin_sys="https://buildbot.libretro.com/assets/system/Dolphin.zip"
update=false
[[ "${1:-}" == "--update" ]] && update=true

mkdir -p "$cache"
touch "$lock"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

locked() { awk -v k="$1" '$1 == k { print $2 }' "$lock"; }
record() {
  local key="$1" sha="$2"
  grep -v "^$key " "$lock" >"$tmp/lock" || true
  echo "$key $sha" >>"$tmp/lock"
  sort -o "$lock" "$tmp/lock"
}

# fetch <key> <url> <name in the zip, or empty> → path of the verified file in the cache
fetch() {
  local key="$1" url="$2" member="$3" expected
  expected="$(locked "$key")"
  if [[ -n "$expected" && "$update" == false && -f "$cache/$expected" ]]; then
    echo "$cache/$expected"
    return
  fi
  curl -fsSL --retry 3 -o "$tmp/download" "$url"
  local file="$tmp/download"
  if [[ -n "$member" ]]; then
    rm -rf "$tmp/x" && mkdir "$tmp/x"
    unzip -q -o "$tmp/download" "$member" -d "$tmp/x"
    file="$tmp/x/$member"
  fi
  local sha
  sha="$(sha256sum "$file" | cut -d' ' -f1)"
  if [[ -n "$expected" && "$update" == false && "$sha" != "$expected" ]]; then
    echo "✗ $key: the nightly changed ($sha instead of $expected)." >&2
    echo "  Run again with --update to take the new version, then test and commit cores.lock." >&2
    exit 1
  fi
  cp "$file" "$cache/$sha"
  record "$key" "$sha"
  echo "$cache/$sha"
}

for abi in "${abis[@]}"; do
  mkdir -p "$jni/$abi"
  for core in "${cores[@]}"; do
    so="${core}_libretro_android.so"
    path="$(fetch "$abi/$core" "$base/$abi/$so.zip" "$so")"
    # Android only extracts "lib*.so" libraries.
    install -m 644 "$path" "$jni/$abi/lib$so"
    echo "✓ $abi/$core"
  done
done

mkdir -p "$assets"
path="$(fetch "dolphin-sys" "$dolphin_sys" "")"
install -m 644 "$path" "$assets/dolphin-sys.zip"
echo "✓ Dolphin Sys"
