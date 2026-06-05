# S004-T03: Full-stack Task Creation Evidence

> Spec: S004 | Task: T03 | Status: PASS | Owner: full-stack | Depends on: S004-T01, S004-T02

## Purpose

QA 要能從瀏覽器證明 Task creation 不是 fixture 假成功：使用者建立 Project，再建立 Task，最後用 follow-up API read-back 看到同一筆資料。

## BDD

Scenario: browser creates a dynamic Task through real API wiring

- Given the full-stack test starts Spring Boot with temporary SQLite state
- And the browser creates a dynamic Project
- When the user creates a Task inside that Project
- Then the browser shows the new Task in `BACKLOG`
- And follow-up `GET /api/projects/{projectId}/tasks` contains the same title and labels
- And the response has `state=BACKLOG`
- And `workflowSummary.currentStep` and `workflowSummary.qualityScore` are `null`

Scenario: Task create does not use a mocked response

- Given the test observes network traffic
- When the Task is submitted
- Then there is a real `POST /api/projects/{projectId}/tasks`
- And a real successful response from the backend
- And no Playwright route fulfills the Task create response

## Files

- `frontend/e2e/task-creation.fullstack.spec.ts`
- `frontend/playwright.fullstack.config.ts` if needed
- `frontend/src/features/task-board/task-api.ts`
- `backend/src/test/java/io/github/samzhu/grimo/task/TaskApiTests.java`

## Test Plan

- Red: add the full-stack Playwright spec against current UI; it should fail before S004 frontend/backend wiring exists.
- Green: make the browser flow pass with real `/api` calls.
- Refactor: keep the spec deterministic with dynamic unique names and temporary backend state.

## Verification

```bash
cd frontend
npm run test:fullstack -- task-creation.fullstack.spec.ts
```

## Notes

- Do not use mocked Task API responses in this full-stack spec.
- The test may reuse Project onboarding helpers, but the Task title must be unique per run.

## Status
PASS

## Result
Date: 2026-06-04
Test: `AC-S004-4/5 creates a Project-owned BACKLOG Task through real full-stack API wiring` (`frontend/e2e/task-creation.fullstack.spec.ts`)
Files changed:
- `frontend/e2e/task-creation.fullstack.spec.ts` (new)
- `docs/grimo/tasks/2026-06-04-S004-T03-fullstack-task-creation-evidence.md` (modified)
Notes:
- RED: `test -f frontend/e2e/task-creation.fullstack.spec.ts` exited `1`; S004 full-stack Task creation evidence file was absent.
- GREEN: `cd frontend && npm run test:fullstack -- task-creation.fullstack.spec.ts` passed `1` Chromium test.
- The Playwright spec does not call `page.route()`; it observes the real `POST /api/projects/{projectId}/tasks`, asserts the request body omits system/projection fields, then verifies follow-up `GET /api/projects/{projectId}/tasks` read-back with `state=BACKLOG` and `workflowSummary.currentStep/qualityScore = null`.
