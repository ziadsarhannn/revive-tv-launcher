#!/usr/bin/env bash
# Installs the latest Revive TV build on the TV box over WiFi, from a Mac.
# Usage:  curl -fsSL https://raw.githubusercontent.com/ziadsarhannn/revive-tv-launcher/main/install-from-mac.sh | bash -s <box IP>
# The box IP can include a port (192.168.1.20:5555). Everything lives in ~/revive-tv-build.
set -euo pipefail

TARGET="${1:-}"
if [ -z "$TARGET" ]; then
  echo "Add the box's IP address at the end, for example:  ... | bash -s 192.168.1.20"
  exit 1
fi
case "$TARGET" in *:*) ;; *) TARGET="$TARGET:5555" ;; esac

DIR="$HOME/revive-tv-build"
mkdir -p "$DIR"
cd "$DIR"

step() { printf "\n\033[1m== %s\033[0m\n" "$1"; }

if [ ! -x "$DIR/platform-tools/adb" ]; then
  step "Getting adb (Google's tool for talking to Android devices, about 15 MB)"
  curl -fL --progress-bar -o pt.zip "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
  unzip -qo pt.zip && rm pt.zip
fi
ADB="$DIR/platform-tools/adb"

step "Downloading the latest Revive TV"
curl -fL --progress-bar -o revive-tv.apk "https://github.com/ziadsarhannn/revive-tv-launcher/releases/download/latest/revive-tv.apk"

step "Connecting to the box at $TARGET"
echo "If the TV asks to allow debugging, tick Always allow and press OK."
"$ADB" connect "$TARGET" || true
OK=no
for i in $(seq 1 30); do
  if [ "$("$ADB" -s "$TARGET" get-state 2>/dev/null || true)" = "device" ]; then OK=yes; break; fi
  sleep 2
  "$ADB" connect "$TARGET" >/dev/null 2>&1 || true
done
if [ "$OK" != yes ]; then
  echo
  echo "Couldn't connect. Check that:"
  echo "  1. The Mac and the box are on the same WiFi"
  echo "  2. USB debugging and Network debugging are on in the box's Developer options"
  echo "  3. The IP is right (Settings, Network on the box)"
  echo "Then run the same command again."
  exit 1
fi

step "Installing"
"$ADB" -s "$TARGET" install -r "$DIR/revive-tv.apk"

step "Opening Revive TV"
"$ADB" -s "$TARGET" shell am start -n com.revivecreates.tvlauncher/.MainActivity >/dev/null 2>&1 || true

echo
echo "Done. Press Home on the remote. If the box asks which app to use, pick Revive TV and choose Always."
echo "Next update: run the same command again."
