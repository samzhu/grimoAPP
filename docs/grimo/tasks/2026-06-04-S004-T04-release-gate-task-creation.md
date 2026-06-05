# S004-T04: Release Gate Task Creation

> Spec: S004 | Task: T04 | Status: PASS | Owner: automation | Depends on: S004-T03

## Purpose

出貨前的單一 release gate 要明確跑到 S004 Task creation。`scripts/verify-release.sh` 綠燈時，log 必須能回查 backend Task API 和 full-stack Task creation evidence。

## BDD

Scenario: release gate includes S004 Task creation

- Given S004 implementation is complete
- When `scripts/verify-release.sh` runs
- Then backend tests include `TaskApiTests`
- And full-stack tests include `task-creation.fullstack.spec.ts`
- And `temp/verify-release.log` names S004 Task creation in the full-stack gate
- And the script exits non-zero if backend Task API or full-stack Task creation fails

## Files

- `scripts/verify-release.sh`
- `docs/grimo/qa-strategy.md` if command registry wording needs to name S004
- `frontend/package.json` only if a narrower script is needed

## Test Plan

- Red: assert current release log wording does not mention S004.
- Green: update release script section/verdict wording or command grouping so S004 is traceable.
- Refactor: keep one critical full-stack command unless a separate command is necessary.

## Verification

```bash
scripts/verify-release.sh
```

## Notes

- Do not remove existing S001/S002/S003 gate coverage.
- If full-stack command already discovers S004 spec, only update the section/verdict wording and preserve the command.

## Status
PASS

## Result
Date: 2026-06-04
Test: `scripts/verify-release.sh` (`scripts/verify-release.sh`)
Files changed:
- `scripts/verify-release.sh` (modified)
- `docs/grimo/tasks/2026-06-04-S004-T04-release-gate-task-creation.md` (modified)
Notes:
- RED: `rg -n "S004|Task creation|TaskApiTests|task-creation\\.fullstack" scripts/verify-release.sh docs/grimo/qa-strategy.md` exited `1`; the release gate wording did not name S004 Task creation evidence.
- GREEN marker: `rg -n "S004|Task creation|TaskApiTests|S001/S002/S003/S004" scripts/verify-release.sh` found the backend and full-stack release gate labels.
- GREEN release gate: `scripts/verify-release.sh` passed. `temp/verify-release.log` includes `backend tests (includes S004 TaskApiTests)`, `S001/S002/S003/S004 full-stack Project onboarding and Task creation`, the passing `e2e/task-creation.fullstack.spec.ts`, and the final verdict naming S004 Task creation.
