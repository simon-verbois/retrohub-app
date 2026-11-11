# Sourced by the scripts: local JDK and SDK installed by scripts/setup-sdk.sh.
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${RETROHUB_JDK:-$HOME/Android/jdk-21}"
export PATH="$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
