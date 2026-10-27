#!/usr/bin/env bash
# Builds the Revive TV launcher APK on a clean Linux machine.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HOME/buildtools"
mkdir -p "$TOOLS"
cd "$TOOLS"

if ! command -v javac >/dev/null 2>&1 || ! javac -version 2>&1 | grep -q " 17"; then
  echo "== Installing JDK 17"
  if command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y -q java-17-amazon-corretto-devel unzip >/dev/null
  else
    sudo apt-get update -qq && sudo apt-get install -y -qq openjdk-17-jdk-headless unzip >/dev/null
  fi
fi
command -v unzip >/dev/null 2>&1 || (sudo dnf install -y -q unzip || sudo apt-get install -y -qq unzip)

export ANDROID_HOME="$TOOLS/android"
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  echo "== Downloading Android command line tools"
  curl -sSLo cmdline.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q cmdline.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
echo "== Installing Android SDK packages"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35" "build-tools;35.0.0" >/dev/null

GRADLE_VERSION=8.11.1
if [ ! -d "$TOOLS/gradle-$GRADLE_VERSION" ]; then
  echo "== Downloading Gradle $GRADLE_VERSION"
  curl -sSLo gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q gradle.zip
fi

echo "== Building APK"
cd "$ROOT"
echo "sdk.dir=$ANDROID_HOME" > local.properties
"$TOOLS/gradle-$GRADLE_VERSION/bin/gradle" assembleRelease --no-daemon --console=plain -q

mkdir -p "$ROOT/out"
cp app/build/outputs/apk/release/app-release.apk "$ROOT/out/revive-tv.apk"
ls -la "$ROOT/out"
echo "== Done"
