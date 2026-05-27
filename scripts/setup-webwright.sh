#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-/Users/samzhu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3}"
WEBWRIGHT_COMMIT="0be73c18ce31a0920c979b8f2aab12d11ef26b9c"
VENV_DIR="$ROOT_DIR/.venv-webwright"

"$PYTHON_BIN" -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/python" -m pip install \
  "git+https://github.com/microsoft/Webwright.git@$WEBWRIGHT_COMMIT"
"$VENV_DIR/bin/python" -m playwright install chromium

printf "Webwright installed at %s\n" "$VENV_DIR/bin/webwright"
