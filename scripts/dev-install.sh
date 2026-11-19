#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

APP_ID=com.buge.appmanager
LAUNCH_COMPONENT=$APP_ID/.MainActivity
APK=app/build/outputs/apk/debug/app-debug.apk
SHIZUKU_PKG=moe.shizuku.privileged.api
BUILD=1
LOG=1

for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    --no-log) LOG=0 ;;
    -h|--help)
      echo "usage: scripts/dev-install.sh [--no-build] [--no-log]"
      exit 0
      ;;
    *)
      echo "ERROR: unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb not found in PATH" >&2
  exit 1
fi

if [ "$BUILD" = "1" ]; then
  ./gradlew assembleDebug
fi

if [ ! -f "$APK" ]; then
  echo "ERROR: $APK not found, run without --no-build" >&2
  exit 1
fi

adb wait-for-device
adb install -r -g "$APK"
adb shell am force-stop "$APP_ID"

PACKAGES=$(adb shell pm list packages)

case "$PACKAGES" in
  *"package:$SHIZUKU_PKG"*)
    adb shell "sh /storage/emulated/0/Android/data/$SHIZUKU_PKG/start.sh" || true
    ;;
  *)
    echo "WARN: Shizuku not installed, skipping service start"
    ;;
esac

adb shell am start -n "$LAUNCH_COMPONENT"
adb logcat -c

if [ "$LOG" = "1" ]; then
  adb logcat -v time "*:S" AndroidRuntime:E Buge:D Shizuku:D
fi
