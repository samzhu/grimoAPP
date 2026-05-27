#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEBWRIGHT_BIN="$ROOT_DIR/.venv-webwright/bin/webwright"

if [ ! -x "$WEBWRIGHT_BIN" ]; then
  printf "Webwright is not installed. Run scripts/setup-webwright.sh first.\n" >&2
  exit 127
fi

if [ "$#" -eq 0 ]; then
  cat >&2 <<'USAGE'
Usage:
  scripts/run-webwright-visual-qa.sh \
    -t "Compare the local Grimo task workbench against docs/grimo/ui/prototype/index.html" \
    --start-url http://127.0.0.1:5173 \
    --task-id task-workbench-prototype-parity

The caller must start the target app first. Outputs are written under temp/webwright/outputs.
USAGE
  exit 2
fi

mkdir -p "$ROOT_DIR/temp/webwright/config" "$ROOT_DIR/temp/webwright/outputs"

MSWEBA_GLOBAL_CONFIG_DIR="$ROOT_DIR/temp/webwright/config" \
  "$WEBWRIGHT_BIN" "$@" -o "$ROOT_DIR/temp/webwright/outputs"
