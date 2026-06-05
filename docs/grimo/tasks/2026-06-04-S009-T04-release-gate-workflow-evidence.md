# S009-T04: Release Gate Workflow Evidence

> Spec: S009 | Task: T04 | Status: PASS | Owner: automation | Depends on: S009-T02, S009-T03

## Purpose

出貨前的 release gate 要能追到 S009 workflow evidence tests。因為 S009 沒有新 UI flow，重點是 backend tests 經由 `scripts/verify-release.sh` 的 critical backend gate 執行，且 log 可回查 AC-S009。

## BDD

Scenario: release gate includes S009 workflow evidence

- Given S009 implementation is complete
- When `scripts/verify-release.sh` runs
- Then backend tests include workflow evidence, projection and API tests
- And `temp/verify-release.log` includes AC-S009 test names or a S009 backend evidence section
- And the script exits non-zero if S009 backend tests fail

## Files

- `scripts/verify-release.sh`
- `docs/grimo/qa-strategy.md` if command registry wording needs to name S009

## Test Plan

- Red: confirm current release log does not name S009.
- Green: update release script wording while keeping backend tests in the critical path.
- Refactor: avoid duplicating `./gradlew test` if the existing backend gate already runs all backend tests.

## Verification

```bash
scripts/verify-release.sh
```

## Notes

- Full-stack E2E is not required for S009 unless a later UI spec adds visible workflow detail behavior.
- Do not weaken existing frontend or S004 full-stack release gates.

## Status
PASS

## Result
Date: 2026-06-05
Test: `scripts/verify-release.sh` (`scripts/verify-release.sh`)
Files changed:
- `scripts/verify-release.sh` (modified)
- `docs/grimo/tasks/2026-06-04-S009-T04-release-gate-workflow-evidence.md` (modified)
Notes:
- RED: `rg -n "S009|workflow evidence|WorkflowEvidence|WorkflowSummaryProjection|AC-S009" scripts/verify-release.sh docs/grimo/qa-strategy.md temp/verify-release.log` exited `1`; release gate wording/log did not name S009 workflow evidence.
- GREEN marker: `rg -n "S009|workflow evidence|WorkflowEvidence|WorkflowSummaryProjection|AC-S009" scripts/verify-release.sh` found the S009 backend gate marker.
- GREEN release gate: `scripts/verify-release.sh` passed. `temp/verify-release.log` includes `backend tests (includes S004 TaskApiTests and S009 workflow evidence tests)` and the final verdict naming S009 workflow evidence tests.
- Existing frontend build, frontend visual regression, backend Gradle tests, and S001/S002/S003/S004 full-stack gates remain in the release script.
