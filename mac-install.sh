#!/usr/bin/env bash
# Revive TV launcher: build on this Mac and install on the TV box over WiFi.
# Usage: bash mac-install.sh <box IP address>
# Everything it downloads lives in ~/revive-tv-build. Delete that folder to remove it all.
set -euo pipefail

BOX_IP="${1:-}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HOME/revive-tv-build"
LOG="$ROOT/build.log"
mkdir -p "$TOOLS"
exec > >(tee "$LOG") 2>&1

step() { printf "\n\033[1m== %s\033[0m\n" "$1"; }

case "$(uname -m)" in
  arm64) JARCH=aarch64 ;;
  *)     JARCH=x64 ;;
esac

# 1. Java 17 (private copy, does not touch the rest of the Mac)
if [ ! -d "$TOOLS/jdk" ]; then
  step "Downloading Java 17"
  curl -fL --progress-bar -o "$TOOLS/jdk.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/17/ga/mac/$JARCH/jdk/hotspot/normal/eclipse"
  mkdir -p "$TOOLS/jdk" && tar -xzf "$TOOLS/jdk.tar.gz" -C "$TOOLS/jdk" --strip-components 1
  rm "$TOOLS/jdk.tar.gz"
fi
export JAVA_HOME="$TOOLS/jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 2. Android SDK command line tools, build tools and adb
export ANDROID_HOME="$TOOLS/android"
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKM" ]; then
  step "Downloading Android tools"
  curl -fL --progress-bar -o "$TOOLS/cmdline.zip" \
    "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -qo "$TOOLS/cmdline.zip" -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm "$TOOLS/cmdline.zip"
fi
step "Installing Android SDK parts"
yes | "$SDKM" --licenses >/dev/null 2>&1 || true
"$SDKM" "platforms;android-35" "build-tools;35.0.0" "platform-tools" >/dev/null
ADB="$ANDROID_HOME/platform-tools/adb"

# 3. Gradle
GV=8.11.1
if [ ! -d "$TOOLS/gradle-$GV" ]; then
  step "Downloading Gradle"
  curl -fL --progress-bar -o "$TOOLS/gradle.zip" "https://services.gradle.org/distributions/gradle-$GV-bin.zip"
  unzip -qo "$TOOLS/gradle.zip" -d "$TOOLS"
  rm "$TOOLS/gradle.zip"
fi

# 4. Build
step "Building the launcher (first time takes a few minutes)"
cd "$ROOT"
echo "sdk.dir=$ANDROID_HOME" > local.properties
"$TOOLS/gradle-$GV/bin/gradle" assembleRelease --no-daemon --console=plain -q
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
cp "$APK" "$ROOT/revive-tv.apk"
echo "APK ready: $ROOT/revive-tv.apk"

# 5. Install over WiFi
if [ -z "$BOX_IP" ]; then
  echo
  echo "Built. To install, run again with the box IP:  bash mac-install.sh 192.168.1.X"
  exit 0
fi
step "Connecting to the box at $BOX_IP"
echo "If a prompt appears on the TV asking to allow debugging, choose Always allow, then OK."
"$ADB" connect "$BOX_IP:5555" || true
OK=no
for i in $(seq 1 20); do
  if [ "$("$ADB" -s "$BOX_IP:5555" get-state 2>/dev/null || true)" = "device" ]; then OK=yes; break; fi
  sleep 2
  "$ADB" connect "$BOX_IP:5555" >/dev/null 2>&1 || true
done
if [ "$OK" != yes ]; then
  echo "Could not reach the box. Check that Network debugging is on and the IP is right, then run this again."
  exit 1
fi
step "Installing"
"$ADB" -s "$BOX_IP:5555" install -r "$ROOT/revive-tv.apk"

step "Opening it"
"$ADB" -s "$BOX_IP:5555" shell am start -n com.revivecreates.tvlauncher/.MainActivity >/dev/null
echo
echo "Done. Revive TV is open on your box."
echo "Press the Home button on the remote. If the box asks which app to use, pick Revive TV and choose Always."
