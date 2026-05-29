# Grimo Development Standards

## Frontend Scope

這份標準目前先約束 `frontend/` React + Vite POC。它的目標是讓 OpenDesign prototype 能穩定轉成產品 UI，而不是讓 `frontend/src/App.tsx` 繼續膨脹。

## Source Of Truth

- 產品語言與行為以 `docs/grimo/PRD.md` 為準。
- UI layout、spacing、component states 以 `docs/grimo/ui/prototype/index.html` 和 `docs/grimo/ui/prototype/DESIGN-HANDOFF.md` 為準。
- 前端 UI/UX 作業流程與設計語言保存規則以 `docs/grimo/design/ui-ux-workflow.md` 為準。
- 可命名的設計決策先放進 `docs/grimo/design/tokens.json`，再映射到 CSS custom properties。
- 可重複的 Webwright review prompt 放在 `docs/grimo/design/webwright-prompts.md`；prompt 必須對應已命名的產品或設計規則。
- 目前前端只可把 prototype 轉成 product UI；不要加入 prototype 沒有、PRD 也沒要求的新產品功能。

## Frontend Architecture

- `App.tsx` 只保留 app shell、providers、route/view composition。
- Domain type、fixture、selector 放到 `src/domain/*`。
- 產品 surface 放到 `src/features/*`，以 PRD/prototype 語言命名，例如 `task-board`、`task-detail`、`task-create`、`task-forming-chat`、`projects`、`blockers`、`workflow`。
- Shared UI 只在同一元件重複使用三次以上時抽出；不要先做泛用 component library。
- CSS 先保留 native CSS + CSS custom properties；拆成 token、layout、feature CSS 時要保留 prototype token 名稱或清楚映射。

## State Management

- 單一小互動可用 local `useState`。
- Task workbench 這類會同時控制 selected task、detail drawer、pin、full page、create modal、search、nav mode 的畫面，必須用 `useReducer` 管理狀態轉移。
- 保存 id，不保存可由 id 推導的完整物件，例如保存 `selectedTaskId`，由 task list 推導 `selectedTask`。
- 避免 contradictory state，例如 detail 已關閉但 full page action 仍指向 drawer-only 狀態。
- Effects 只用於同步外部系統，例如 REST/SSE、local storage、browser APIs；click/submit 這類使用者動作放在 event handler。

## Assistant UI

- `@assistant-ui/react` 只作為 headless chat primitives/runtime，不作為 Grimo 的視覺設計來源。
- Chat 是 Task-forming Discuss 入口，不是產品主頁。主工作介面仍是 Task Management Interface。
- Frontend 不直接持有 provider credential，不直接呼叫 provider SDK。Production runtime 應透過 backend adapter。
- Chat 可以建立或推進 DEFINING/BACKLOG task context，但不能直接把 Task 設成 READY 或啟動 execution。

## Prototype Fidelity

- 每次改 layout 前先對照 prototype 的實際 CSS 尺寸，不用目測重設 spacing。
- Topbar、rail、section head、board column、task card、detail drawer 的尺寸要能追溯到 prototype token 或明確 override 原因。
- selected task 提示應優先使用 prototype 的 subtle border / inset accent；不要加入會破壞 metadata 對齊的大型 badge，除非設計稿更新。
- Task board 初始進入時不得預選任何 task；selection 只能來自使用者點選或明確 deep link。
- `新增 Task` 必須對應 prototype 的 create task modal，不只是空按鈕。
- `在完整頁開啟` 必須有 task detail full page 行為，不是無作用按鈕。

## Quality Gate

每個 frontend UI task 至少要附：

- `scripts/verify-release.sh`
- `npm run build`
- 桌面瀏覽器人工檢查主要畫面
- 若改 layout：deterministic Playwright visual evidence，至少覆蓋 `1366x768`、`1440x900`
- 若改 responsive：再覆蓋 `390x844`、`820x1180`
- 若改表單或互動：補 component 或 E2E 行為測試

### Webwright Visual QA

- Webwright 用於 agent-assisted visual QA：把瀏覽器檢查流程產生為可重跑的 Playwright-backed script，並保存 `plan.md`、action log、screenshots、final run output。
- Webwright 不取代 deterministic Playwright assertions；它是 layout review 與長流程 UI 檢查的第二層 evidence。
- Webwright 安裝在 repo-local `.venv-webwright`，透過 `scripts/setup-webwright.sh` 建置，透過 `scripts/run-webwright-visual-qa.sh` 執行。
- Webwright prompt 不是臨場自由發揮；layout/prototype parity task 應先使用或更新 `docs/grimo/design/webwright-prompts.md`。
- Layout 或 prototype parity task 的 evidence 必須包含：
  - deterministic screenshot comparison 或固定 viewport screenshot set
  - Webwright run artifact：`final_script.py`、`final_script_log.txt`、`screenshots/`、self-reflection 或人工 review 結果
  - 對照來源：`docs/grimo/ui/prototype/index.html` 的 relevant selectors / CSS token
- 如果 layout task 沒有 deterministic screenshot gate，也沒有 Webwright/human visual evidence，`$verifying-quality` 必須判定為 `BLOCKED-BY-TESTABILITY`，不能 ship。
- Webwright 本身需要 Python 3.10+、Playwright Chromium，以及可用的 agent/model backend；若環境缺失，該檢查可標為 `EXECUTABLE with prereqs`，但不可把 layout parity 標成已驗證。

## Dependency Policy

- `frontend/package.json` 不使用 `latest` 作為長期版本策略；新增或升級 dependency 時要 pin exact version。
- 升級 dependency 必須同時驗證 registry version、主要 import path、API 是否 deprecated、`npm run build` 是否通過。
- 不為單一使用場景新增大型 state、router、form 或 UI library。先用 React/Vite 原生能力，直到需求明確超出。

## Current Recommended Next Steps

1. 先把 `frontend/src/App.tsx` 拆成 domain + features，保持畫面不變。
2. 加 `taskWorkbenchReducer`，整理 nav/detail/modal/full-page 狀態。
3. 把 prototype tokens 固定下來，再修 layout 對齊。
4. 補 Playwright + Webwright visual gate 後，再進行大幅 UI parity work。
