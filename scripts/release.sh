#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

eval "$(bash scripts/version.sh app/build.gradle.kts)"

CHANGELOG="fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt"

if [ ! -s "$CHANGELOG" ]; then
  echo "ERROR: changelog missing or empty: $CHANGELOG" >&2
  exit 1
fi

if [ ! -f keystore.properties ]; then
  echo "ERROR: keystore.properties missing in $ROOT" >&2
  exit 1
fi

STORE_FILE=$(sed -n 's/^[[:space:]]*storeFile[[:space:]]*=[[:space:]]*//p' keystore.properties | sed -n '1p')

if [ -n "$STORE_FILE" ]; then
  case "$STORE_FILE" in
    /*|[A-Za-z]:[\\/]*) STORE_PATH="$STORE_FILE" ;;
    *) STORE_PATH="app/$STORE_FILE" ;;
  esac
  if [ ! -f "$STORE_PATH" ]; then
    echo "ERROR: keystore not found: $STORE_PATH" >&2
    exit 1
  fi
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

if [ -z "$SDK" ] && [ -f local.properties ]; then
  SDK=$(sed -n 's/^sdk\.dir=//p' local.properties | sed -n '1p')
  SDK=${SDK//\\//}
fi

if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "ERROR: Android SDK not found, set ANDROID_HOME" >&2
  exit 1
fi

./gradlew assembleRelease --stacktrace

APK=$(find app/build/outputs/apk/release -name "*.apk" -type f | sed -n '1p')

if [ -z "$APK" ]; then
  echo "ERROR: release APK not found" >&2
  exit 1
fi

APKSIGNER=""

if [ -n "${WINDIR:-}" ]; then
  APKSIGNER=$(find "$SDK/build-tools" -name "apksigner.bat" -type f | sort | tail -1)
fi

if [ -z "$APKSIGNER" ]; then
  APKSIGNER=$(find "$SDK/build-tools" -name apksigner -type f | sort | tail -1)
fi

if [ -z "$APKSIGNER" ]; then
  echo "ERROR: apksigner not found under $SDK/build-tools" >&2
  exit 1
fi

"$APKSIGNER" verify "$APK"

mkdir -p dist
DEST="dist/BugeAppManager-v${VERSION_NAME}-release.apk"
cp "$APK" "$DEST"

echo "VERSION_NAME=$VERSION_NAME"
echo "VERSION_CODE=$VERSION_CODE"
echo "APK=$DEST"
echo "SIZE=$(du -h "$DEST" | cut -f1)"
