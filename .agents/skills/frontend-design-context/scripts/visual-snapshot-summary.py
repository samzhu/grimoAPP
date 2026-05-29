#!/usr/bin/env python3
"""Summarize visual snapshot and Playwright artifact changes for Grimo."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


SNAPSHOT_MARKER = "frontend/e2e/"
TEST_RESULT_MARKER = "frontend/test-results/"


def run_git_status(repo_root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "status", "--short"],
        cwd=repo_root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        sys.stderr.write(result.stderr)
        raise SystemExit(result.returncode)
    return [line.rstrip() for line in result.stdout.splitlines() if line.strip()]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=".", help="Repository root path")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    if not (repo_root / ".git").exists():
        sys.stderr.write(f"Not a git repository root: {repo_root}\n")
        return 2

    status_lines = run_git_status(repo_root)
    snapshots = [
        line
        for line in status_lines
        if SNAPSHOT_MARKER in line and "snapshots/" in line and line.endswith(".png")
    ]
    specs = [
        line
        for line in status_lines
        if line.endswith(".spec.ts") and SNAPSHOT_MARKER in line
    ]
    test_results = [line for line in status_lines if TEST_RESULT_MARKER in line]

    print("Visual snapshot summary")
    print("=======================")
    print(f"Changed snapshot files: {len(snapshots)}")
    for line in snapshots:
        print(f"- {line}")

    print(f"\nChanged visual specs: {len(specs)}")
    for line in specs:
        print(f"- {line}")

    print(f"\nTracked test-result artifacts: {len(test_results)}")
    for line in test_results:
        print(f"- {line}")

    if not snapshots and not specs:
        print("\nNo tracked visual snapshot/spec changes detected.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
