# S001 T03 — Full-stack verification and release gate

Status: PASS

## Purpose

證明 browser 裡的 `Projects` 表單真的透過 Vite `/api` proxy 打到 Spring Boot backend，不是 mock 或 fixture。同時讓 `scripts/verify-release.sh` 覆蓋 backend tests 和 S001 full-stack test。

## Requires

- T01 PASS
- T02 PASS
- Java 25
- Node/npm
- Ports `5173` and `8080` available during Playwright run

## BDD

**Scenario: Vite proxy connects browser to Spring Boot**

- Given（前提）Playwright starts Spring Boot backend with temporary SQLite database and Vite frontend dev server
- When（動作）browser submits the Projects form
- Then（結果）browser request URL is `/api/projects`
- And（而且）UI renders the created Project from backend response
- And（而且）a follow-up `GET /api/projects` returns the same created Project
- And（而且）test fails if backend is unavailable

**Scenario: Release gate records S001 verification**

- Given（前提）S001 implementation is complete
- When（動作）`scripts/verify-release.sh` runs
- Then（結果）`temp/verify-release.log` contains frontend build, frontend visual regression, backend tests, and S001 full-stack Project onboarding sections
- And（而且）script exits non-zero if any CRITICAL command fails

## Implementation Notes

- Add a dedicated full-stack Playwright config or project so existing visual snapshots stay stable.
- Backend must use temporary SQLite state for full-stack tests.
- Avoid writing into the user's real local Grimo database.

## Target Files

- `frontend/e2e/project-onboarding.fullstack.spec.ts`
- `frontend/playwright.fullstack.config.ts` or `frontend/playwright.config.ts`
- `frontend/package.json`
- `scripts/verify-release.sh`
- `docs/grimo/specs/2026-05-31-S001-project-onboarding-workflow-selection.md`

## Verification

Run: `scripts/verify-release.sh`

Pass: full release gate exits 0 and records S001 evidence.

## Result

- Added `frontend/playwright.fullstack.config.ts` to start Spring Boot with temporary SQLite state and Vite on port 5173.
- Added `frontend/e2e/project-onboarding.fullstack.spec.ts` to create a Project through the browser, then confirm `/api/projects` returns the same row through the Vite proxy.
- Updated `scripts/verify-release.sh` so release verification includes frontend build, visual regression, backend tests, and S001 full-stack Project onboarding.
- Evidence: `scripts/verify-release.sh` exited 0 and wrote PASS evidence to `temp/verify-release.log`.
