# Grimo Changelog

## [Unreleased]

### Added

- S011: Project Selection Gate 升級為 Main Content Area 裡的 `Project Setup Hero`；first-run 顯示 `建立第一個 Project`，已有 Projects 時以 Project card selection 作為主要路徑，Project list error 顯示 `無法載入 Project context` + `重試`。同步把 app shell selector/token 文件化為 `App Header`、`Side Navigation`、`Main Content Area`、`Task Details Pane`，新增 `CLAUDE.md` 與 `docs/grimo/design/README.md` 作前端設計文件入口，並以 Playwright visual/full-stack、backend tests、release gate 與 @chrome smoke 驗證。
- S010: 新增正式產品啟動流程與 Project session restore；第一次打開會先建立第一個 Project，有 open session 時恢復上次 Project，`Close Project` 後回到 Project selection gate。Topbar 目前 Project 現在是 Project Switcher，可切換 Project、建立/管理 Project、Close Project；沒有 active Project 時 Task 管理、待處理、Chat 不再顯示 fixture data。
- S001: 完成 Project onboarding 第一條 full-stack vertical slice；使用者可以建立 Project、選擇 workflow recipe，前端透過 real `/api` 走 Vite proxy 呼叫 Spring Boot，backend 以 SQLite 保存 Project，release gate 納入 backend 與 full-stack Playwright 驗證。後續 S002/S003 已將 live API 演進為 `CollectionResponse<T>` 與 `projectPath` 合約。
- S004: 新增 Project-owned manual Task 建立與列表 API；使用者在已選 Project 內可建立 `BACKLOG` Task，backend 會保存 SQLite `tasks` row、複製 Task Workflow、不啟動 active run，frontend 只送 `title/body/labels`，未選 Project 時不可建立孤兒 Task。
- S009: 新增 Task Workflow copy、workflow run/step/Quality Loop evidence 的 SQLite 正規化儲存；Task board 的 `workflowSummary` 由 evidence rows 投影，Task detail 可讀 `GET /api/projects/{projectId}/tasks/{taskId}/workflow`，且 public API 不開 workflow evidence write endpoint。
- S003: 專案管理改成 list-first 入口；新增專案頁使用選填 `projectPath`，不填時由 backend 建立預設 `.grimo/projects/<projectId>`，手動輸入有效 repo path 時保存該路徑，無效或重複 path 會被拒絕；前端主流程不再展開 backend directory browser。
- S002: 建立 Project 前可預覽 Workflow Recipe 的 steps、roles 與 Quality Loop summary；建立後保存 `workspacePath`、selected workflow role settings、13 字元 TSID Project id，並讓 workflow/project collections 統一回 `CollectionResponse<T>`。
