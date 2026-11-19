#!/usr/bin/env bash
set -euo pipefail

APP_ID=com.buge.appmanager
OUT="${1:-bugreport-$(date +%Y%m%d-%H%M%S).txt}"

if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb not found in PATH" >&2
  exit 1
fi

adb wait-for-device

{
  echo "== generated =="
  date
  echo
  echo "== devices =="
  adb devices
  echo
  echo "== device =="
  adb shell getprop ro.product.manufacturer
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell getprop ro.build.version.sdk
  echo
  echo "== related packages =="
  adb shell pm list packages | sed -n 's/^package://p' | grep -E 'shizuku|dhizuku|buge' || true
  echo
  echo "== package $APP_ID =="
  adb shell dumpsys package "$APP_ID" || true
  echo
  echo "== appops $APP_ID =="
  adb shell dumpsys appops --package "$APP_ID" || true
  echo
  echo "== logcat =="
  adb logcat -d -v threadtime
} > "$OUT" 2>&1

echo "wrote $OUT ($(wc -c < "$OUT") bytes)"
