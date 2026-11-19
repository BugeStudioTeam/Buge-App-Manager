#!/usr/bin/env bash
set -euo pipefail

GRADLE_FILE="${1:-app/build.gradle.kts}"

if [ ! -f "$GRADLE_FILE" ]; then
  echo "ERROR: $GRADLE_FILE not found" >&2
  exit 1
fi

VERSION_NAME=$(sed -n 's/.*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$GRADLE_FILE" | sed -n '1p')
VERSION_CODE=$(sed -n 's/.*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$GRADLE_FILE" | sed -n '1p')

if [ -z "$VERSION_NAME" ]; then
  echo "ERROR: versionName not found in $GRADLE_FILE" >&2
  exit 1
fi

if [ -z "$VERSION_CODE" ]; then
  echo "ERROR: versionCode not found in $GRADLE_FILE" >&2
  exit 1
fi

TAG="AppManager$VERSION_NAME"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "version_name=$VERSION_NAME"
    echo "version_code=$VERSION_CODE"
    echo "tag=$TAG"
  } >> "$GITHUB_OUTPUT"
fi

echo "VERSION_NAME=$VERSION_NAME"
echo "VERSION_CODE=$VERSION_CODE"
echo "TAG=$TAG"
