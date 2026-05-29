#!/usr/bin/env python3
"""Summarize Playwright visual snapshot changes and evidence artifacts.

This script is intentionally read-only. It inspects git status for snapshot
baseline changes and lists local visual-test evidence folders so design context
updates can cite the actual artifacts after `npm run test:visual:update`.
"""

from __future__ import annotations

import argparse
import subprocess
from dataclasses import dataclass
from pathlib import Path


SNAPSHOT_MARKER = "-snapshots/"
SNAPSHOT_SUFFIX = ".png"
ARTIFACT_CANDIDATES = [
    "frontend/test-results",
    "frontend/playwright-report",
    "temp/webwright/outputs",
]


@dataclass(frozen=True)
class StatusEntry:
    code: str
    path: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Summarize changed Playwright visual snapshots.",
    )
    parser.add_argument(
        "--repo-root",
        default=".",
        help="Repository root to inspect. Defaults to current directory.",
    )
    return parser.parse_args()


def run_git_status(repo_root: Path) -> list[StatusEntry]:
    result = subprocess.run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=repo_root,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    entries: list[StatusEntry] = []
    for line in result.stdout.splitlines():
        if not line:
            continue

        code = line[:2]
        path = line[3:]
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        entries.append(StatusEntry(code=code, path=path))

    return entries


def is_snapshot(path: str) -> bool:
    return SNAPSHOT_MARKER in path and path.endswith(SNAPSHOT_SUFFIX)


def status_label(code: str) -> str:
    if code == "??":
        return "added"
    if "D" in code:
        return "deleted"
    if "R" in code:
        return "renamed"
    if "M" in code:
        return "updated"
    if "A" in code:
        return "added"
    return "changed"


def summarize_snapshots(entries: list[StatusEntry]) -> dict[str, list[str]]:
    grouped: dict[str, list[str]] = {
        "added": [],
        "updated": [],
        "renamed": [],
        "deleted": [],
        "changed": [],
    }

    for entry in entries:
        if not is_snapshot(entry.path):
            continue
        grouped[status_label(entry.code)].append(entry.path)

    return {key: sorted(value) for key, value in grouped.items() if value}


def list_artifacts(repo_root: Path) -> list[str]:
    artifacts: list[str] = []

    for relative in ARTIFACT_CANDIDATES:
        path = repo_root / relative
        if not path.exists():
            continue

        if path.is_file():
            artifacts.append(relative)
            continue

        files = sorted(
            item.relative_to(repo_root).as_posix()
            for item in path.rglob("*")
            if item.is_file()
        )
        if files:
            artifacts.extend(files[:20])
            if len(files) > 20:
                artifacts.append(f"{relative}/... ({len(files) - 20} more files)")
        else:
            artifacts.append(f"{relative}/ (empty)")

    return artifacts


def print_summary(repo_root: Path, snapshots: dict[str, list[str]], artifacts: list[str]) -> None:
    print("# Visual Snapshot Summary")
    print(f"Repo root: {repo_root}")
    print()

    snapshot_count = sum(len(paths) for paths in snapshots.values())
    print(f"Changed snapshots: {snapshot_count}")
    if snapshots:
        for label in ["added", "updated", "renamed", "deleted", "changed"]:
            paths = snapshots.get(label, [])
            if not paths:
                continue
            print(f"- {label}: {len(paths)}")
            for path in paths:
                print(f"  - {path}")
    else:
        print("- none")

    print()
    print("Visual evidence artifacts:")
    if artifacts:
        for artifact in artifacts:
            print(f"- {artifact}")
    else:
        print("- none found")

    print()
    if snapshot_count:
        print("Snapshot update status: changed baselines are present in git status.")
    else:
        print("Snapshot update status: no baseline changes detected.")


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).expanduser().resolve()

    if not repo_root.exists():
        raise SystemExit(f"Repo root does not exist: {repo_root}")

    entries = run_git_status(repo_root)
    snapshots = summarize_snapshots(entries)
    artifacts = list_artifacts(repo_root)
    print_summary(repo_root, snapshots, artifacts)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
