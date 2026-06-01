# Grimo Changelog

## [Unreleased]

### Added

- S003: 專案管理改成 list-first 入口；新增專案頁使用選填 `projectPath`，不填時由 backend 建立預設 `.grimo/projects/<projectId>`，手動輸入有效 repo path 時保存該路徑，無效或重複 path 會被拒絕；前端主流程不再展開 backend directory browser。
- S002: 建立 Project 前可預覽 Workflow Recipe 的 steps、roles 與 Quality Loop summary；建立後保存 `workspacePath`、selected workflow role settings、13 字元 TSID Project id，並讓 workflow/project collections 統一回 `CollectionResponse<T>`。
