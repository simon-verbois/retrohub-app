#!/usr/bin/env bash
# Installs what is needed to build and test the app, without Android Studio:
# a JDK 21 (Temurin) for Gradle and the Android SDK in ~/Android/Sdk.
# Idempotent: running it again only downloads what is missing.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
JDK_DIR="${RETROHUB_JDK:-$HOME/Android/jdk-21}"
CMDLINE_TOOLS_BUILD=16111833
JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz"
JDK_SHA256="d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c"
PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;37.0.0")

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if [[ ! -x "$JDK_DIR/bin/java" ]]; then
  echo "→ JDK 21 in $JDK_DIR"
  curl -fL --retry 3 -o "$tmp/jdk.tar.gz" "$JDK_URL"
  echo "$JDK_SHA256  $tmp/jdk.tar.gz" | sha256sum -c -
  mkdir -p "$JDK_DIR"
  tar -xzf "$tmp/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
fi
export JAVA_HOME="$JDK_DIR"

if [[ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "→ command-line tools in $SDK"
  curl -fL --retry 3 -o "$tmp/tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest" "$tmp/cmdline-tools"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

sdkmanager="$SDK/cmdline-tools/latest/bin/sdkmanager"
missing=()
for package in "${PACKAGES[@]}"; do
  [[ -d "$SDK/${package//;//}" ]] || missing+=("$package")
done
if ((${#missing[@]})); then
  echo "→ SDK packages: ${missing[*]}"
  yes | "$sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
  "$sdkmanager" --sdk_root="$SDK" "${missing[@]}"
fi

project="$(cd "$(dirname "$0")/.." && pwd)"
if [[ ! -f "$project/local.properties" ]] || ! grep -q "^sdk.dir=$SDK$" "$project/local.properties"; then
  echo "sdk.dir=$SDK" >"$project/local.properties"
fi

cat <<INFO
SDK ready.
  export JAVA_HOME=$JDK_DIR
  export ANDROID_HOME=$SDK
  export PATH=\$ANDROID_HOME/platform-tools:\$PATH
(scripts/env.sh does these exports for the other scripts.)
INFO
