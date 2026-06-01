# S003-T01: Backend Project Path API BDD

## 對應規格
S003：Project management list and simple projectPath contract

## 這個 task 要做什麼
這個 task 把 S003 的 backend BDD 先寫成 `ProjectApiTests`，再讓 backend API contract 從舊的 `workspacePath` 收斂成單一 `projectPath`。完成後，`POST /api/projects` 可以不帶 `projectPath` 建立預設路徑，也可以保存有效 repo path；無效或重複 path 不會留下 Project 或 role rows。

## 使用者情境（BDD）
Given（前提）使用者建立 Project 時沒有填「專案路徑」  
When（動作）frontend 送出 `POST /api/projects`，request body 只有 `name`, `description`, `workflowRecipeId`  
Then（結果）API 回 `201 Created`，body 有 `projectPath` 位於 temporary `user.home/.grimo/projects/<projectId>`  
And（而且）response 不包含 `workspacePath`, `projectPathSource`, `backendPathReady`, `projectDataPath`

Given（前提）本機存在可讀資料夾 `/tmp/grimo-s003-repo`  
When（動作）API 收到 `projectPath="/tmp/grimo-s003-repo"`  
Then（結果）API 回 `201 Created`，body.projectPath 是 normalized absolute path  
And（而且）DB `projects.workspace_path` 保存同一個 normalized absolute path

Given（前提）使用者輸入不存在或不可讀的 `projectPath`  
When（動作）API 收到 create request  
Then（結果）API 回 `400 Bad Request`，body.error 是 `請輸入有效的本機資料夾路徑`  
And（而且）`projects` / `project_workflow_roles` row count 不變

Given（前提）已存在 Project 使用同一個 normalized `projectPath`  
When（動作）使用者再次用同一路徑建立 Project  
Then（結果）API 回 `409 Conflict`，body.error 是 `這個專案路徑已經建立過 Project`  
And（而且）DB 仍只有一筆使用該 `projectPath` 的 Project

## 研究來源
- `docs/grimo/specs/2026-06-01-S003-project-management-list-project-path.md` §3 AC-S003-3/4/5/6
- `docs/grimo/development-standards.md` Backend rule：REST API 用明確 DTO，測試不得碰使用者真資料。
- `backend/src/test/java/io/github/samzhu/grimo/project/ProjectApiTests.java`
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectService.java`
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectStore.java`

## 先做 POC
- POC：not required — S003 不引入新 dependency；使用既有 Spring MVC + SQLite + MockMvc 測試架構。
- Fixture：
  - `no-path-project`: request 不含 `projectPath` → 預期建立 generated path。
  - `valid-project-path`: temporary directory path → 預期 201。
  - `invalid-project-path`: nonexistent path → 預期 400，DB 不新增。
  - `duplicate-project-path`: same normalized path → 預期 409，DB 不新增第二筆。

## 正式程式怎麼做
- Class / file 名稱：
  - `backend/src/main/java/io/github/samzhu/grimo/project/CreateProjectRequest.java`
  - `backend/src/main/java/io/github/samzhu/grimo/project/ProjectResponse.java`
  - `backend/src/main/java/io/github/samzhu/grimo/project/ProjectRecord.java`
  - `backend/src/main/java/io/github/samzhu/grimo/project/ProjectService.java`
  - `backend/src/main/java/io/github/samzhu/grimo/project/ProjectStore.java`
  - `backend/src/main/resources/schema.sql`
- 入口：`POST /api/projects`
- 必要行為：
  - `CreateProjectRequest` 改為 `String projectPath`，不再要求 `@NotBlank`。
  - `ProjectResponse` 回 `projectPath`，不回 `workspacePath`。
  - `ProjectService` 在 `projectPath` blank / omitted 時，用 generated Project id 建立 `user.home/.grimo/projects/<projectId>`。
  - `ProjectService` 在 `projectPath` nonblank 時 normalize 成 absolute path，驗證 exists + directory + readable。
  - duplicate detection 用 normalized `projectPath`。
  - request 中帶 `workspacePath` 不應成為行為來源；S003 API source of truth 是 `projectPath`。
- Response / DB 欄位：
  - `projectPath`: generated path 或 normalized absolute user path。
  - `workflowRoles`: 沿用 S002 role snapshot。
  - `workspace_path` DB column 可暫時保留作為 storage column，但 API 欄位語意是 `projectPath`。

## 單元測試 / 整合測試
- `ProjectApiTests`
  - `@DisplayName("AC-S003-3: creates Project with generated projectPath when request omits projectPath")`
  - `@DisplayName("AC-S003-4: creates Project with validated manual projectPath")`
  - `@DisplayName("AC-S003-5: rejects invalid projectPath without persisted rows")`
  - `@DisplayName("AC-S003-6: rejects duplicate normalized projectPath without persisted rows")`
  - `@DisplayName("AC-S003-3/7: Project response exposes projectPath only")`

## 會改哪些檔案
- `backend/src/main/java/io/github/samzhu/grimo/project/CreateProjectRequest.java`
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectResponse.java`
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectRecord.java`
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectService.java`
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectStore.java`
- `backend/src/main/resources/schema.sql`
- `backend/src/test/java/io/github/samzhu/grimo/project/ProjectApiTests.java`

## 驗證方式
執行：`./backend/gradlew -p backend test --tests '*ProjectApiTests'`

## 前置條件
- 無

## Status
PASS

## Result
Date: 2026-06-01
Test: `ProjectApiTests` (`backend/src/test/java/io/github/samzhu/grimo/project/ProjectApiTests.java`)
Files changed:
- `backend/src/main/java/io/github/samzhu/grimo/project/CreateProjectRequest.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectResponse.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectRecord.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectService.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectStore.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/project/DuplicateProjectException.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectErrorHandler.java` (modified)
- `backend/src/test/java/io/github/samzhu/grimo/project/ProjectApiTests.java` (modified)
Red: `./gradlew test --tests '*ProjectApiTests'` in `backend/` failed with 8 failures after S003 `projectPath` BDD assertions were added.
Green: `./gradlew test --tests '*ProjectApiTests'` in `backend/` passed; 10 tests completed, 0 failures.
Notes: Backend API now accepts optional `projectPath`, creates generated `user.home/.grimo/projects/<projectId>` when omitted, validates manual paths, rejects duplicates with `409`, and no longer exposes `workspacePath` in Project responses.
