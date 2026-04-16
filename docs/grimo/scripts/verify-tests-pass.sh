#!/usr/bin/env bash
# verify-tests-pass.sh
# Runs the Grimo test suite and records a pass/fail line to a persistent
# log so humans and CI agents can both see recent verification status.
#
# Usage:  ./docs/grimo/scripts/verify-tests-pass.sh [gradle-args...]
# Exit:   0 on PASS, 1 on FAIL (also appends to build/reports/grimo/verify-log.txt)

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

LOG_DIR="build/reports/grimo"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/verify-log.txt"

TIMESTAMP="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
GRADLE_ARGS="${*:-test}"

set +e
./gradlew --console=plain $GRADLE_ARGS
EXIT=$?
set -e

STATUS="PASS"
if [ "$EXIT" -ne 0 ]; then
  STATUS="FAIL"
fi

printf '%s\t%s\tgradle %s\n' "$TIMESTAMP" "$STATUS" "$GRADLE_ARGS" \
  >> "$LOG_FILE"

echo "[verify-tests-pass] recorded $STATUS for '$GRADLE_ARGS' at $TIMESTAMP"
exit "$EXIT"
