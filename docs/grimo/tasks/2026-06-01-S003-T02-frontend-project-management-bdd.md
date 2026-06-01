# S003-T02: Frontend Project Management BDD

## 對應規格
S003：Project management list and simple projectPath contract

## 這個 task 要做什麼
這個 task 把 S003 的 frontend BDD 轉成 Playwright UI tests，先用 mocked `/api/*` 驗證純前端行為：專案管理一開始只顯示列表；按 `新增專案` 才進建立頁；建立頁只有 `專案路徑` text input，不顯示後端 directory browser，也不送 `projectPathSource` 或 browser handle 欄位。

## 使用者情境（BDD）
Given（前提）`GET /api/projects` 回 `{"content":[]}`  
When（動作）使用者打開「專案管理」  
Then（結果）畫面顯示 `Project list`、`尚未建立專案`、`新增專案`  
And（而且）畫面尚未顯示 `專案名稱`、`專案描述`、`專案路徑` create fields

Given（前提）使用者位於 Project list view  
When（動作）使用者按 `新增專案`  
Then（結果）畫面顯示 `新增專案`、`返回列表`、`專案名稱`、`專案描述`、`專案路徑`、`專案工作流`  
And（而且）使用者按 `返回列表` 後回到 list view，已載入的 Project list 沒有被清空

Given（前提）使用者位於新增專案頁  
When（動作）使用者查看 `專案路徑` 區塊  
Then（結果）只看到 text input 與 `未填會使用 Grimo 預設路徑`  
And（而且）畫面不顯示 `上層`、`選取此資料夾`、`.directory-browser` 或 `選擇資料夾`  
And（而且）submit request body 不包含 `workspacePath`, `projectPathSource`, `browserProjectPathKey`

## 研究來源
- `docs/grimo/specs/2026-06-01-S003-project-management-list-project-path.md` AC-S003-1/2/7
- `docs/grimo/development-standards.md` Frontend Architecture / Quality Gate
- `frontend/src/features/projects/Projects.tsx`
- `frontend/src/features/projects/project-api.ts`
- `frontend/src/domain/project/project-types.ts`

## 先做 POC
- POC：not required — 使用既有 Playwright frontend test setup，透過 `page.route()` mock API response。
- Fixture：
  - `empty-project-list`: `GET /api/projects` 回空 `content[]`。
  - `workflow-recipes`: `GET /api/workflow-recipes` 回至少 `web-service-development`，含 steps / roles。
  - `created-project`: `POST /api/projects` 回 `projectPath`，不回 removed fields。

## 正式程式怎麼做
- Class / file 名稱：
  - `frontend/e2e/project-management.ui.spec.ts`
  - `frontend/src/features/projects/Projects.tsx`
  - `frontend/src/features/projects/project-api.ts`
  - `frontend/src/domain/project/project-types.ts`
  - `frontend/src/styles.css`
- 入口：Projects feature 的 `專案管理` view。
- 必要行為：
  - 新增 `viewMode = "list" | "create"` 或等價狀態；初始為 list。
  - `建立專案` 改名/保留為 list view CTA；create view header 顯示 `返回列表`。
  - `workspacePath` UI label 改為 `專案路徑`，state/type/request 欄位改為 `projectPath`。
  - `canSubmit` 不再要求 `projectPath`；只要求 `name` + `workflowRecipeId`。
  - 移除 create page 主流程的 `listLocalDirectories` 呼叫、`選擇資料夾` button、`.directory-browser` render。
  - API client 不再 export/use `listLocalDirectories` in Projects create flow；endpoint 可留給舊相容，但 S003 UI 不使用。
- Request / UI 欄位：
  - `CreateProjectInput.projectPath?: string`
  - `Project.projectPath: string`
  - list card label 顯示 `專案路徑` 或 `Project path`，不得顯示 `工作區`。

## 單元測試 / 整合測試
- `frontend/e2e/project-management.ui.spec.ts`
  - `test("AC-S003-1: list view hides Project create fields before user starts creation")`
  - `test("AC-S003-2: create view can return to Project list without clearing loaded projects")`
  - `test("AC-S003-7: create view submits projectPath only and does not render directory browser")`

## 會改哪些檔案
- `frontend/e2e/project-management.ui.spec.ts`
- `frontend/src/features/projects/Projects.tsx`
- `frontend/src/features/projects/project-api.ts`
- `frontend/src/domain/project/project-types.ts`
- `frontend/src/styles.css`

## 驗證方式
執行：`npm --prefix frontend run test:visual -- project-management.ui.spec.ts --grep "AC-S003"`

## 前置條件
- S003-T01 PASS

## Status
PASS

## Result
Date: 2026-06-01
Test: `project-management.ui.spec.ts` (`frontend/e2e/project-management.ui.spec.ts`)
Files changed:
- `frontend/e2e/project-management.ui.spec.ts` (new)
- `frontend/src/features/projects/Projects.tsx` (modified)
- `frontend/src/features/projects/project-api.ts` (modified)
- `frontend/src/domain/project/project-types.ts` (modified)
- `frontend/src/App.tsx` (modified)
- `frontend/src/styles.css` (modified)
Red: `npm --prefix frontend run test:visual -- project-management.ui.spec.ts --grep "AC-S003"` failed because the existing UI did not expose the `新增專案` action and still used the old creation flow.
Green: `npm --prefix frontend run test:visual -- project-management.ui.spec.ts --grep "AC-S003"` passed; 3 tests completed, 0 failures.
Build: `npm --prefix frontend run build` passed.
Notes: Project management now opens in list view, the create view is entered only through `新增專案`, `projectPath` is optional text input, and the create flow no longer calls or renders backend directory browsing.
