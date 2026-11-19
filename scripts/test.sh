#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

MODE=unit

for arg in "$@"; do
  case "$arg" in
    --unit) MODE=unit ;;
    --instrumented) MODE=instrumented ;;
    --all) MODE=all ;;
    -h|--help)
      echo "usage: scripts/test.sh [--unit|--instrumented|--all]"
      exit 0
      ;;
    *)
      echo "ERROR: unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

if [ "$MODE" = "unit" ] || [ "$MODE" = "all" ]; then
  ./gradlew testDebugUnitTest --stacktrace
fi

if [ "$MODE" = "instrumented" ] || [ "$MODE" = "all" ]; then
  ./gradlew connectedDebugAndroidTest --stacktrace
fi

REPORT=app/build/reports/tests/testDebugUnitTest/index.html

if [ -f "$REPORT" ]; then
  echo "report: $REPORT"
fi
