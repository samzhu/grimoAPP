#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_FILE="$ROOT_DIR/temp/verify-release.log"

mkdir -p "$(dirname "$LOG_FILE")"
: > "$LOG_FILE"

log_section() {
  printf "\n== %s ==\n" "$1" | tee -a "$LOG_FILE"
}

run_critical() {
  local name="$1"
  shift
  log_section "$name"
  "$@" 2>&1 | tee -a "$LOG_FILE"
}

run_critical "frontend build" npm --prefix "$ROOT_DIR/frontend" run build
run_critical "frontend visual regression (includes S010 Project startup and switcher evidence)" npm --prefix "$ROOT_DIR/frontend" run test:visual
run_critical "backend tests (includes S004 TaskApiTests and S009 workflow evidence tests)" "$ROOT_DIR/backend/gradlew" -p "$ROOT_DIR/backend" test
run_critical "S001/S002/S003/S004/S010 full-stack Project onboarding, startup, and Task creation" npm --prefix "$ROOT_DIR/frontend" run test:fullstack

log_section "visual qa infrastructure"
if [ -d "$ROOT_DIR/frontend/node_modules/@playwright" ] || [ -d "$ROOT_DIR/frontend/node_modules/playwright" ]; then
  printf "Playwright dependency detected. Run project visual specs when they are added.\n" | tee -a "$LOG_FILE"
else
  printf "SKIP: Playwright visual specs are not installed yet.\n" | tee -a "$LOG_FILE"
fi

if [ -x "$ROOT_DIR/.venv-webwright/bin/webwright" ]; then
  if MSWEBA_GLOBAL_CONFIG_DIR="$ROOT_DIR/temp/webwright/config" "$ROOT_DIR/.venv-webwright/bin/webwright" --help >/dev/null 2>&1; then
    printf "Webwright CLI detected. Run task-specific Webwright visual QA when prototype parity is claimed.\n" | tee -a "$LOG_FILE"
  else
    printf "FAIL: Webwright CLI exists but cannot start.\n" | tee -a "$LOG_FILE"
    exit 1
  fi
else
  printf "SKIP: Webwright CLI is not installed yet. Run scripts/setup-webwright.sh for prototype parity QA.\n" | tee -a "$LOG_FILE"
fi

log_section "verdict"
printf "PASS: frontend build, deterministic visual regression including S010 Project startup/switcher evidence, backend tests including S004 TaskApiTests and S009 workflow evidence tests, and S001/S002/S003/S004/S010 full-stack Project onboarding, startup, and Task creation completed. Webwright remains task-specific for prototype parity reviews.\n" | tee -a "$LOG_FILE"
