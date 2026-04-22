#!/usr/bin/env bash
# verify-all.sh
# Deterministic verification gate — executes EVERY command from the
# Verification Command Registry (qa-strategy.md §6.1).
#
# This script is the executable version of the registry table.
# When a new spec introduces new test infrastructure, update BOTH
# this script and qa-strategy.md §6.1 in the same PR.
#
# Usage:  ./docs/grimo/scripts/verify-all.sh
# Exit:   0 if all CRITICAL commands pass (SKIP commands may skip)
#         1 if any CRITICAL command fails
#
# Output: build/reports/grimo/verify-all-log.txt (append-only)

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

LOG_DIR="build/reports/grimo"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/verify-all-log.txt"

TIMESTAMP="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
OVERALL_EXIT=0
RESULTS=()

# ── Helper ────────────────────────────────────────────────────────

run_critical() {
    local label="$1"
    shift
    echo "──── [$label] $* ────"
    if "$@"; then
        RESULTS+=("$TIMESTAMP  PASS  $label")
        echo "  → PASS"
    else
        RESULTS+=("$TIMESTAMP  FAIL  $label")
        echo "  → FAIL (CRITICAL)"
        OVERALL_EXIT=1
    fi
}

run_skippable() {
    local label="$1"
    local check_cmd="$2"
    shift 2
    echo "──── [$label] $* ────"

    # Check environment prerequisite
    if ! eval "$check_cmd" > /dev/null 2>&1; then
        RESULTS+=("$TIMESTAMP  SKIP  $label  (environment unavailable)")
        echo "  → SKIP (environment unavailable)"
        return
    fi

    if "$@"; then
        RESULTS+=("$TIMESTAMP  PASS  $label")
        echo "  → PASS"
    else
        RESULTS+=("$TIMESTAMP  FAIL  $label")
        echo "  → FAIL"
        OVERALL_EXIT=1
    fi
}

# ── Verification Command Registry (qa-strategy.md §6.1) ──────────
#
# CRITICAL commands: must pass, block shipping on failure.
# SKIPPABLE commands: skip when environment unavailable, pass when available.
#
# When adding a new command:
#   1. Add an entry to qa-strategy.md §6.1 table
#   2. Add a run_critical or run_skippable call below
#   3. Keep the order matching the table

# V1: Compile check
run_critical "V1-compile" \
    ./gradlew compileTestJava --console=plain

# V2: Unit + slice tests (T0-T2)
run_critical "V2-test" \
    ./gradlew test --console=plain

# V3: E2E integration tests (T4) — requires claude CLI
run_skippable "V3-integrationTest" \
    "which claude" \
    ./gradlew integrationTest --console=plain

# ── Results ───────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════"
echo "  verify-all.sh summary"
echo "═══════════════════════════════════════════"
if [ "${#RESULTS[@]}" -gt 0 ]; then
    for r in "${RESULTS[@]}"; do
        echo "  $r"
        echo "$r" >> "$LOG_FILE"
    done
else
    echo "  (no commands executed)"
fi
echo "═══════════════════════════════════════════"

if [ "$OVERALL_EXIT" -eq 0 ]; then
    echo "  OVERALL: PASS"
    echo "$TIMESTAMP  OVERALL  PASS" >> "$LOG_FILE"
else
    echo "  OVERALL: FAIL"
    echo "$TIMESTAMP  OVERALL  FAIL" >> "$LOG_FILE"
fi

echo ""
exit "$OVERALL_EXIT"
