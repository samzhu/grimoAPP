# S001 T02 — Frontend Projects view uses backend API

Status: PASS

## Purpose

讓 `Projects` 畫面不再只顯示 fixture。頁面載入時從 `/api/projects` 和 `/api/workflow-recipes` 讀資料；按「新增專案」時送 `POST /api/projects`，成功後新 Project 出現在列表並更新 topbar 目前專案。

## Requires

- T01 PASS
- Node/npm

## BDD

**Scenario: User creates Project from Projects view**

- Given（前提）Vite app opens the Projects view and backend has no Project named `grimoAPP`
- When（動作）user fills `名稱`, `描述`, `專案資料夾`, selects `開發工作流`, and clicks `新增專案`
- Then（結果）button shows pending text while the request is in flight
- And（而且）after success, `grimoAPP` appears in the Project list
- And（而且）topbar current project context shows `grimoAPP` and the submitted folder
- And（而且）form clears only after successful backend response

**Scenario: Failed create keeps existing list**

- Given（前提）Projects view already shows existing Projects
- When（動作）`POST /api/projects` returns validation or duplicate error
- Then（結果）error text appears in the form area
- And（而且）existing Project list remains visible

## Implementation Notes

- Use relative URLs: `fetch("/api/projects")`, not hardcoded backend origin.
- Keep existing visual language; do not add a new UI library or form library.
- Topbar current Project state should live above `Projects` so the list can update it after create.

## Target Files

- `frontend/src/domain/project/project-types.ts`
- `frontend/src/features/projects/project-api.ts`
- `frontend/src/features/projects/Projects.tsx`
- `frontend/src/App.tsx`
- `frontend/vite.config.ts`

## Verification

Run: `npm --prefix frontend run build`

Pass: TypeScript build is green; T03 adds browser-level verification.

## Result

- Implemented `frontend/src/features/projects/project-api.ts` with relative `/api` calls for workflow recipes, project list, and project creation.
- Updated `Projects` to load backend data, submit the create form, show success/error states, and update topbar current Project state through `App`.
- Evidence: `npm --prefix frontend run build` passed.
