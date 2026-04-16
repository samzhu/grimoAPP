#!/usr/bin/env bash
# verify-spec-coverage.sh
# Given a spec file, assert that every acceptance criterion (AC1, AC2, ...)
# is referenced by at least one test (by @DisplayName or an `// ACN` marker).
#
# Usage:  ./docs/grimo/scripts/verify-spec-coverage.sh docs/grimo/specs/S004-claude-adapter.md
# Exit:   0 if every AC is covered, 1 otherwise (prints the uncovered IDs).

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: $0 <path-to-spec.md>" >&2
  exit 2
fi

SPEC="$1"
if [ ! -f "$SPEC" ]; then
  echo "spec file not found: $SPEC" >&2
  exit 2
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# 1. Extract AC ids from the spec. Matches `### AC1`, `### AC-1`, etc.
#    Skips ACs whose heading declares itself deferred (e.g., "AC-4: ...
#    (verification deferred)" or "AC-3: _deferred_").
AC_IDS=$(grep -E '^### *AC-?[0-9]+' "$SPEC" \
  | grep -viE '(deferred|_deferred_)' \
  | grep -oE 'AC-?[0-9]+' \
  | sort -u || true)

if [ -z "$AC_IDS" ]; then
  echo "[verify-spec-coverage] no active '### ACn' or '### AC-n' headings in $SPEC — nothing to check"
  exit 0
fi

# 2. For each AC, search for a marker in:
#    - src/test/java/**/*.java (test code: @DisplayName, @Tag, or // comment)
#    - docs/grimo/tasks/**/*.md (task files: BDD references like "(AC-6)")
#    An AC is considered covered if it appears in at least one of those.
MISSING=""
TEST_DIRS="src/test/java"
TASK_DIR="docs/grimo/tasks"

for AC in $AC_IDS; do
  FOUND=""
  if [ -d "$TEST_DIRS" ]; then
    if grep -rq --include='*.java' -E "(\"$AC\b|// *$AC\b|@Tag\\(\"$AC\\b)" "$TEST_DIRS"; then
      FOUND=1
    fi
  fi
  if [ -z "$FOUND" ] && [ -d "$TASK_DIR" ]; then
    if grep -rq --include='*.md' -E "(\(|[[:space:]])$AC\b" "$TASK_DIR"; then
      FOUND=1
    fi
  fi
  if [ -z "$FOUND" ]; then
    MISSING="$MISSING $AC"
  fi
done

REPORT_DIR="build/reports/grimo"
mkdir -p "$REPORT_DIR"
REPORT="$REPORT_DIR/spec-coverage.md"

{
  echo "# Spec coverage — $(basename "$SPEC")"
  echo
  echo "Generated: $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  echo
  if [ -z "$MISSING" ]; then
    echo "**All ACs covered** (${AC_IDS})."
  else
    echo "**Uncovered ACs:** $MISSING"
    echo
    echo "Add a test referencing each uncovered AC via @DisplayName (\"AC1 ...\")"
    echo "or a \`// AC1 ...\` marker inside the test method."
  fi
} > "$REPORT"

if [ -n "$MISSING" ]; then
  echo "[verify-spec-coverage] uncovered:$MISSING"
  echo "[verify-spec-coverage] report: $REPORT"
  exit 1
fi

echo "[verify-spec-coverage] all covered (${AC_IDS})"
echo "[verify-spec-coverage] report: $REPORT"
