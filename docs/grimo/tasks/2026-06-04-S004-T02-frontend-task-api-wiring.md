# S004-T02: Frontend Task API Wiring

> Spec: S004 | Task: T02 | Status: PASS | Owner: frontend | Depends on: S004-T01

## Purpose

使用者選定 Project 後，在 Task board 按「新增 Task」會呼叫真實 backend API，成功後看到新的 `BACKLOG` 卡片。沒有 current Project 時不能建立 Task，避免畫面送出孤兒資料。

## Contract

- `CreateTaskDialog` shows title, body and labels only.
- Remove the `建議 skill` submit surface.
- Do not use `建立草稿`; button text is `建立 Task`.
- The dialog closes only after the API succeeds.
- API errors remain visible in the dialog.
- Frontend request body must not include server-owned fields.

## BDD

Scenario: create Task in the selected Project

- Given `currentProject` exists
- And the Task board has loaded that Project's backend Tasks
- When the user fills title, body and labels
- And clicks `建立 Task`
- Then the frontend posts to `/api/projects/{projectId}/tasks`
- And the request body only contains `title`, `body`, and `labels`
- And the returned Task appears in the `BACKLOG` column

Scenario: no Project means no orphan Task create

- Given no `currentProject` exists
- When the user opens the Task board
- Then the create action is disabled or routes to Project selection
- And the frontend does not call `/api/projects/*/tasks`

## Files

- `frontend/src/domain/task/task-types.ts`
- `frontend/src/features/task-board/task-api.ts`
- `frontend/src/features/task-create/CreateTaskDialog.tsx`
- `frontend/src/features/task-board/TaskWorkbench.tsx`
- `frontend/src/App.tsx`
- `frontend/e2e/task-management.ui.spec.ts`
- `frontend/e2e/task-workbench.visual.spec.ts`

## Test Plan

- Red: add a frontend interaction test proving the create request shape and no-Project guard.
- Green: wire task API client and local loading/error state.
- Refactor: preserve existing visual layout while replacing prototype-only root fields with `workflowSummary` and `commentCount`.

## Verification

```bash
cd frontend
npm run build
npm run test:visual
```

## Notes

- Keep fixture/demo Tasks read-only when no Project is selected if needed for current prototype visual baseline.
- If `Task` type still supports fixtures, isolate compatibility mapping so backend API fields stay clean.

## Status

PASS

## Result

Date: 2026-06-04
Test: `AC-S004-4: selected Project can create a Task through the backend API shape`, `AC-S004-4: without a current Project the Task board cannot create an orphan Task` (`frontend/e2e/task-management.ui.spec.ts`)
Files changed:
- `frontend/src/App.tsx` (modified)
- `frontend/src/domain/task/task-types.ts` (modified)
- `frontend/src/domain/task/task-fixtures.ts` (modified)
- `frontend/src/features/task-board/TaskWorkbench.tsx` (modified)
- `frontend/src/features/task-board/task-api.ts` (new)
- `frontend/src/features/task-create/CreateTaskDialog.tsx` (modified)
- `frontend/e2e/task-management.ui.spec.ts` (new)
- `frontend/e2e/task-workbench.visual.spec.ts` (modified)
- `frontend/e2e/project-management.ui.spec.ts` (modified)
- `frontend/e2e/task-workbench.visual.spec.ts-snapshots/create-task-dialog-chromium-darwin.png` (modified)
Notes:
- RED: `cd frontend && npm run test:visual -- task-management.ui.spec.ts` failed because `建議 skill` still rendered and no-Project `新增 Task` was enabled.
- GREEN: `cd frontend && npm run test:visual -- task-management.ui.spec.ts` passed.
- Build: `cd frontend && npm run build` passed.
- Visual gate: `cd frontend && npm run test:visual` passed after intentionally updating the create Task dialog snapshot with `npm run test:visual -- task-workbench.visual.spec.ts --update-snapshots`.
- Scope: frontend now disables Task creation without `currentProject`; when a Project exists, it loads `/api/projects/{projectId}/tasks`, posts only `title/body/labels`, closes the dialog after success, and renders the returned `BACKLOG` card.
