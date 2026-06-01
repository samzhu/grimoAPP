# S003-T03: Full-Stack E2E Project Path Verification

## 對應規格
S003：Project management list and simple projectPath contract

## 這個 task 要做什麼
這個 task 把 S003 的前後端整合 BDD 落到 full-stack Playwright。它不 mock API，而是用 `frontend/playwright.fullstack.config.ts` 啟動 Spring Boot + Vite，證明 UI、API、SQLite schema、request/response shape 真的串起來。

## 使用者情境（BDD）
Given（前提）Spring Boot 使用 isolated in-memory SQLite，Vite 開在 `127.0.0.1:5173`  
When（動作）使用者進入「專案管理」並按 `新增專案`  
Then（結果）使用者看到 create view，且沒有 backend directory browser

Given（前提）使用者填寫 Project name、description、workflow，但不填「專案路徑」  
When（動作）使用者按 `建立專案`  
Then（結果）Project 建立成功，list 顯示 generated `projectPath`  
And（而且）POST response 只有 `projectPath`，不包含 `workspacePath`, `projectPathSource`, `backendPathReady`, `projectDataPath`

Given（前提）測試建立 temporary repo directory  
When（動作）使用者在 `專案路徑` 輸入該 directory 並建立 Project  
Then（結果）Project list 顯示該 normalized path  
And（而且）`GET /api/projects` 的 `content[]` 有同一筆 Project + workflow roles

Given（前提）使用者輸入不存在的 projectPath  
When（動作）使用者按 `建立專案`  
Then（結果）畫面顯示 `請輸入有效的本機資料夾路徑`  
And（而且）Project list 不新增該 Project

## 研究來源
- `docs/grimo/specs/2026-06-01-S003-project-management-list-project-path.md` AC-S003-1/2/3/4/5/7
- `frontend/playwright.fullstack.config.ts`
- `frontend/e2e/project-onboarding.fullstack.spec.ts`
- `scripts/verify-release.sh`

## 先做 POC
- POC：not required — full-stack Playwright 已在 S001/S002 使用，S003 只更新既有 spec file。
- Fixture：
  - `generated-path-project`: no projectPath → generated path。
  - `manual-path-project`: temporary directory → saved path。
  - `invalid-path-project`: nonexistent path → user-readable error。

## 正式程式怎麼做
- Class / file 名稱：
  - `frontend/e2e/project-onboarding.fullstack.spec.ts`
  - `scripts/verify-release.sh`
  - 必要時更新 `frontend/e2e/task-workbench.visual.spec.ts` 或新增 Projects visual spec。
- 入口：browser at `/` with real backend.
- 必要行為：
  - 更新既有 full-stack test 從 `workspacePath` 改成 `projectPath`。
  - 驗證不填 `projectPath` 時 submit button 仍可按。
  - 驗證 valid manual path 成功、invalid path 失敗。
  - 驗證 network 沒有 `GET /api/local-directories` request。
  - 驗證 POST body 不含 `workspacePath`, `projectPathSource`, `browserProjectPathKey`。
  - 驗證 response/list 不含 `workspacePath`, `projectPathSource`, `backendPathReady`, `projectDataPath`。
  - 若 `./scripts/verify-release.sh` 的 log label 還寫 `S001/S002 full-stack Project onboarding`，改成包含 S003。
- Evidence：
  - `npm --prefix frontend run test:fullstack` PASS。
  - `./scripts/verify-release.sh` PASS。

## 單元測試 / 整合測試
- `frontend/e2e/project-onboarding.fullstack.spec.ts`
  - `test("AC-S003-1/2/7 shows list-first Project management and projectPath-only create form")`
  - `test("AC-S003-3 creates Project with generated projectPath when projectPath is blank")`
  - `test("AC-S003-4 creates Project with validated manual projectPath")`
  - `test("AC-S003-5 rejects invalid manual projectPath without adding Project")`

## 會改哪些檔案
- `frontend/e2e/project-onboarding.fullstack.spec.ts`
- `scripts/verify-release.sh`
- `frontend/e2e/*visual*` snapshots if layout changes require intentional updates

## 驗證方式
執行：`npm --prefix frontend run test:fullstack`

最終收斂執行：`./scripts/verify-release.sh`

## 前置條件
- S003-T01 PASS
- S003-T02 PASS

## Status
PASS

## Result
Date: 2026-06-01
Test: `project-onboarding.fullstack.spec.ts` (`frontend/e2e/project-onboarding.fullstack.spec.ts`)
Files changed:
- `frontend/e2e/project-onboarding.fullstack.spec.ts` (modified)
- `frontend/playwright.fullstack.config.ts` (modified)
- `scripts/verify-release.sh` (modified)
Red: `npm --prefix frontend run test:fullstack` failed because the stale full-stack spec still clicked the old `建立專案` entry action and expected `workspacePath`.
Green: `npm --prefix frontend run test:fullstack` passed; 4 tests completed, 0 failures.
Notes: Full-stack Playwright now verifies list-first Project management, projectPath-only create form, generated projectPath when blank, validated manual projectPath, invalid path rejection, no `/api/local-directories` main-flow request, and absence of removed response/request fields. The full-stack config sets `user.home` to repo-local `temp/grimo-fullstack-home` so generated paths do not write to the user's real home.
