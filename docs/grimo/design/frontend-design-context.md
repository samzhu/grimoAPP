# 前端設計脈絡

這份文件保存 Grimo 前端 UI/UX 決策、頁面層級理由、browser comment 處理結果，以及 visual gate 證據。

## 1. 目的

Grimo 的前端設計工作需要留下可延續的脈絡。當需求、browser comment、UI 文案、響應式行為、版面選擇或 visual regression evidence 出現時，把穩定決策記在這裡，後續 UI 工作才不用從聊天紀錄重新推理。

## 2. 全域設計原則

- **決策：** 第一輪 redesign 保留目前的淺色系。
  **原因：** 使用者明確希望先吸收資訊架構、狀態語義、quality score、evidence 和文案；dark / cyan / glow 放到之後的 Arcane theme proposal。
  **證據：** `docs/grimo/design/ui-ux-redesign-brief.md`、`docs/grimo/design/DESIGN.md`、2026-05-28 使用者方向。
  **狀態：** 有效

- **決策：** 把 `docs/grimo/design/DESIGN.md` 視為外部設計師提案，分三輪吸收。
  **原因：** 該文件描述 dark Arcane workbench 方向，但使用者已明確分階段採用，以降低 churn 並保留可審查性。
  **證據：** 2026-05-28 使用者方向。
  **狀態：** 有效

  第一輪：只吸收資訊架構、狀態語義、quality score、evidence model 和文案；保留目前淺色 theme。

  第二輪：建立可切換的 `Arcane theme proposal` 或 screenshot mock 供討論；不取代預設 app theme。

  第三輪：品牌方向確認後，再更新 `docs/grimo/design/tokens.json` 和全站色彩 tokens。

- **決策：** 第一輪採用 premium utilitarian minimalism。
  **原因：** Grimo 是密集的本地工作台，不是 marketing page。UI 應該可讀、平面、淺色、evidence-first、克制，同時吸收設計師文件裡較完整的狀態語義。
  **證據：** `minimalist-ui` skill、`design-taste-frontend` audit discipline、2026-05-28 使用者方向。
  **狀態：** 有效

- **決策：** 前端設計文件使用 `docs/grimo/design/README.md` 作為索引，`AGENTS.md` / `CLAUDE.md` 只放短入口。
  **原因：** Agent 啟動檔應該短而穩定；完整設計理由、流程、token、prompt 和 evidence 應放在 design docs / active spec，避免重複後 drift。
  **證據：** Claude Code memory docs 建議 `CLAUDE.md` 放 project instructions 且保持 concise；`CLAUDE.md` 讀 `AGENTS.md` 時可用 import。
  **狀態：** 有效

- **決策：** 同一個 viewport 不放重複的主要 `新增 Task` CTA。
  **原因：** 重複的 `新增 Task` 按鈕會讓 command hierarchy 不清楚。
  **證據：** Browser comment 比較 Arcane mockup 中兩個 create buttons。
  **狀態：** 有效

- **決策：** 視覺變更完成前必須通過 visual gate。
  **原因：** Playwright snapshots 提供桌面版、平板、手機版的穩定證據；behavior assertions 補足 screenshots 看不到的互動語義。
  **證據：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`。
  **狀態：** 有效

- **決策：** Page-flow 變更必須先有 Screen Flow Contract，再做 wireframe 或 implementation。
  **原因：** 2026-06-06 使用者回饋指出，如果 first-run、empty、loading、error、success、navigation、CTA 行為只在單頁內設計，前端頁面流程容易不一致。Screen Flow Contract 讓 PRD critical path、page state 和 verification evidence 在畫面設計前先對齊。
  **證據：** `docs/grimo/design/screen-flow-contract.md`、`docs/grimo/design/ui-ux-workflow.md`。
  **狀態：** 有效

- **決策：** Agent 能力不能只靠 generic Chat 呈現；主要 UI 必須提供明確 signifier、state feedback、human control 和 recovery path。
  **原因：** 使用者研究指出，agent 若住在 LINE / Chat 輸入框裡，非工程背景使用者看不出背後能建立 Task、執行 workflow、收集 evidence 或交給人審查。Grimo 後續 UI 應用 Norman 的 affordance / signifier / discoverability / feedback，以及 Swiss cheese model 的多層防護，把 agent 能力變成可探索、可監督、可恢復的工作台體驗。
  **證據：** `docs/grimo/design/human-centered-agent-ui-principles.md`、2026-06-13 使用者研究整理要求。
  **狀態：** 有效

## 3. 頁面脈絡

### App Shell / Project 啟動流程

**目的：** App 第一次載入時，必須先建立真實 Project context，才顯示 Task、待處理、Chat 等工作區。

**S011 Design Read：** 這是本地開發工作台的 Project context setup surface，不是 SaaS landing page。設計語言採 premium utilitarian minimalism：平面淺色、克制邊框、清楚的工作入口；first-run 用 assistant-ui 啟發的 `Project Setup Copilot` 引導建立 Project，closed session 則直接進 `專案管理` 列表。

**討論用詞：**

- `App Header` 是 app chrome：放全域入口、品牌和目前 Project 狀態。它回答「我現在在哪個 Project context 裡」，不承擔 first-run 教學或主要建立流程。S011 target class 是 `.app-header`，legacy class 是 `.topbar`。
- `Side Navigation` 是主要頁面導覽，程式上是 `Navigation`。它只負責切換 page view，不是頁面內容本身；未固定時浮在主畫面上方，固定後才佔左側欄位。S011 target class 是 `.side-navigation`，legacy class 是 `.rail`。
- `Main Content Area` 是 App Header 下方的主要工作區。它承載目前頁面的主要內容，例如 Task Workbench、Project list、Chat，或第一次使用時的建立引導。S011 target class 是 `.main-content-area`，legacy class 是 `.main-surface`。
- `Project Selection Gate` 是 Main Content Area 裡的 no-active-Project 狀態頁。first-run 或 missing session 會用它引導建立 Project；closed session 不使用 gate，而是進 `專案管理`。
- `Project Setup Copilot` 是 `Project Selection Gate` 裡的 first-run / missing-session 建立引導。它不是完整 Chat，而是參考 assistant-ui thread/composer/suggestions 的 setup surface，用來說明 repo、workflow、Task context 如何進入 Project。
- `Project Setup Error` 是 `Project Selection Gate` 裡的 error 狀態，顯示 `無法載入 Project context` 和 `重試`。
- `Project Picker` 是已停用的 no-active-Project 方案。既有 Project 管理留在 `專案` 頁；有 active Project 時，App Header 的 `ProjectSwitcher` 才負責切換 Project。
- `Task Details Pane` 不是 menu item；它是 `Task 管理` 裡選到 Task 後打開的 nested surface。固定在右側時叫 pane，浮出覆蓋時叫 `Task Details Drawer`；完整頁面另稱 full Task detail page。
- `Chat` 是 page view，也是 Task 的完整討論入口。從 Task card、待處理卡片或 detail 進入 Chat 時，會帶著目前 selected Task；沒有 selected Task 時，Chat 是形成新 Task 的討論入口。

**命名來源：**

| Grimo 正式名 | 通用來源 | S011 target class / file | Legacy class |
| --- | --- | --- | --- |
| App Header | Carbon `Header`、Material `App bar` | `.app-header`, `App.tsx` | `.topbar` |
| Side Navigation | Carbon `Left panel` / `side-nav`、Microsoft `left navigation pane` | `.side-navigation`, `Navigation.tsx` | `.rail` |
| Main Content Area | Material `content area` / `content canvas`、Microsoft `content` | `.main-content-area`, `App.tsx` | `.main-surface` |
| Task Details Pane | Microsoft `details pane` / `list/details pattern` | `.task-details-pane`, `TaskDetail.tsx` | `.detail-pane` |

**整體畫面地圖：**

```mermaid
flowchart LR
  subgraph APP["App shell"]
    TOP["App Header：全域入口、品牌、目前 Project 狀態"]
    WORKSPACE["Workspace shell"]
    TOP --> WORKSPACE
  end

  WORKSPACE --> RAIL["Side Navigation：切換 page view"]
  WORKSPACE --> MAIN["Main Content Area：目前 page view 的內容"]

  RAIL --> TASKS["Task 管理"]
  RAIL --> ATTENTION["待處理"]
  RAIL --> PROJECTS["專案"]
  RAIL --> CHAT["Chat"]
  RAIL --> WORKFLOW["Workflow"]

  MAIN --> GATE["Project Selection Gate"]
  GATE --> SETUP_COPILOT["Project Setup Copilot：first-run 建立引導"]
  GATE --> SETUP_ERROR["Project Setup Error：載入失敗重試"]
  MAIN --> TASKS_PAGE["Task Workbench"]
  MAIN --> ATTENTION_PAGE["Attention Queue"]
  MAIN --> PROJECTS_PAGE["Projects List / Create"]
  MAIN --> CHAT_PAGE["Task Chat / Task forming Chat"]
  MAIN --> WORKFLOW_PAGE["Workflow Reference"]

  TASKS_PAGE --> DETAIL["Task Details Pane / Drawer / full page"]
  TASKS_PAGE --> CREATE_TASK["新增 Task dialog"]
  ATTENTION_PAGE --> CHAT_PAGE
  DETAIL --> CHAT_PAGE
```

```text
┌────────────────────────────────────────────────────────────────────────┐
│ App Header                                                             │
│ [Menu] [Logo] Grimo              目前專案 尚未開啟 Project / Project 名稱 │
├────────────────────────────────────────────────────────────────────────┤
│ Workspace shell                                                        │
│                                                                        │
│  Side Navigation（按 Menu 後出現；pin 後固定在左側）                     │
│  ┌──────────────┐   Main Content Area                                  │
│  │ Task 管理     │   ┌──────────────────────────────────────────────┐    │
│  │ 待處理        │   │ 目前選到的 page view                         │    │
│  │ 專案          │   │                                              │    │
│  │ Chat          │   │ - 沒有 active Project 且進 Task/待處理/Chat： │    │
│  │ Workflow      │   │   顯示 Project Selection Gate                │    │
│  └──────────────┘   │ - 進專案：顯示 Project list / create          │    │
│                     │ - 進 Workflow：顯示 workflow reference        │    │
│                     │ - 有 active Project：顯示對應工作頁            │    │
│                     └──────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────┘
```

`Project Selection Gate` 內部的 first-run / missing-session 版面：

```text
main.main-content-area
└─ section.project-selection-gate
   └─ Project Setup Copilot（assistant-ui style 建立入口）
      ├─ eyebrow: 新手引導 / 尚未開啟 Project
      ├─ heading: 建立 Project 工作台
      ├─ primary action: 建立新 Project
      ├─ setup thread preview: Project Setup Copilot / suggestion buttons / composer preview
      └─ setup steps: 選擇本機 repo / 套用 Project workflow / 開始形成 Task
```

`closed session` 的入口版面：

```text
main.main-content-area
└─ section.projects-view
   ├─ heading: 專案管理
   ├─ action: 新增專案
   └─ existing Project rows
```

**有無 active Project 的頁面行為：**

| 選單項目 | 有 active Project 時 | 沒有 active Project 時 |
| --- | --- | --- |
| `Task 管理` | 顯示 Task Workbench、待處理焦點、Kanban/list、Task Detail、`新增 Task`。 | first-run / missing-session 顯示 Project Selection Gate；closed session 首頁先進 `專案管理`。 |
| `待處理` | 顯示 `REVIEW` approvals、`NEEDS_HUMAN` 修復項與 definition gaps，可回到 Chat。 | 顯示 Project Selection Gate，避免看見沒有 Project context 的假 attention list。 |
| `專案` | 顯示 Project list / create，可建立、管理、切換 Project。 | 仍可進入 Project list / create，因為這是建立 Project context 的地方。 |
| `Chat` | 顯示目前 selected Task 的 Task Chat；沒有 selected Task 時，可作為形成新 Task 的討論入口。 | 顯示 Project Selection Gate，避免開啟沒有 Project context 的 generic blank Chat。 |
| `Workflow` | 顯示 workflow reference，說明 Project recipe steps 和 Task List State 的對應。 | 仍可顯示 workflow reference，但 App Header 保持 `尚未開啟 Project`，不暗示已有 Project context。 |

**頁面層級責任：**

| 層級 | 程式對應 | 負責什麼 | 不負責什麼 |
| --- | --- | --- | --- |
| App shell | `App.tsx`, `.app-shell` | 組合 App Header、workspace shell、全域 Project session。 | 不顯示具體 Task cards 或 Chat messages。 |
| App Header | `.app-header`, `ProjectSwitcher` | 顯示品牌、menu button、目前 Project 狀態與 Project switcher。 | 不做 first-run onboarding，不放主要頁面內容。 |
| Side Navigation | `Navigation`, `.side-navigation` | 切換 `tasks / blockers / projects / chat / workflow` page view。 | 不決定 Project 是否有效，不承載頁面內容。 |
| Main Content Area | `main.main-content-area` | 顯示目前 page view 的主要內容，或顯示 Project Selection Gate。 | 不放全域品牌或 app chrome。 |
| Project Selection Gate | `ProjectSelectionGate`, `.project-selection-gate` | first-run / missing-session 時建立 Project context；載入失敗時重試。 | 不顯示 fixture tasks、不假裝已有 Project、不列既有 Project。 |
| Project Setup Copilot | `ProjectSelectionGate`, `.project-setup-copilot` | 用 assistant-ui 風格的 setup thread、suggestions、composer preview 協助使用者進入建立 Project。 | 不保存真實 Chat history，不取代 `Projects` create form，不列 existing Projects。 |
| Project Setup Error | `ProjectSelectionGate`, `.project-setup-error` | Project list API 失敗時顯示問題與 `重試`。 | 不顯示 first-run onboarding。 |
| Project Picker | historical `.project-picker` | 已停用的 no-active-Project 選擇方案，只保留在歷史紀錄和舊 snapshot 說明。 | 不得重新出現在 `ProjectSelectionGate` current DOM。 |
| Task Workbench | `TaskWorkbench` | 管理 Task list、focus tray、Task Detail、create dialog。 | 不保存完整 Chat history。 |
| Task Details Pane / Drawer | `TaskDetail`, `.task-details-pane` | 顯示 selected Task 的摘要、品質、evidence 和操作。 | 不取代 full Task detail page，也不取代 Chat。 |
| Chat | `AssistantChat` | 顯示 Task thread 或形成新 Task 的討論入口。 | 不取代 Task board，也不直接顯示所有 review evidence。 |

**目前版面決策：**

- 第一次啟動且沒有 Project 時，`Project Selection Gate` 顯示 `Project Setup Copilot`；heading 是 `建立 Project 工作台`，主要操作是 `建立新 Project`，suggestion / composer preview 也導向建立 Project。
- 已有 Project 且 session 是 closed 時，App 首頁直接顯示 `專案管理` 列表，讓使用者 reopen / manage Project；不顯示 Project Selection Gate。
- 已有 Project 但沒有開啟中的 session 時，主工作區仍顯示 Project Setup Copilot；既有 Project 的查看、管理、後續切換留在 `專案` 頁和 active Project 時的 App Header `ProjectSwitcher`。
- `lastActiveProjectId` 過期時，停在建立門檻並顯示 `上次開啟的 Project 已不存在或無法載入。你可以建立新 Project。`；app 不可以偷偷改選另一個 Project。
- 有 active Project 時，App Header 的 Project context 是 Project Switcher。它列出 Projects，並提供 `新增 Project`、`管理 Projects`、`Close Project`。
- `Close Project` 是前端 session 指令：它寫入 `isClosed=true`，從 UI 清掉 active Project，但不刪除後端 Project 資料；關閉後立刻回到 `專案管理`。
- Task 管理、待處理、Chat 都必須受 Project gate 控制。沒有 active Project 時，這些頁面顯示 Project Selection Gate，不顯示 fixture tasks、attention cards 或 generic blank Chat。
- 專案和 Workflow 是例外：沒有 active Project 時仍可開啟，因為專案頁負責建立 Project context，Workflow 頁負責說明可選 workflow recipe。

**響應式行為：**

- Selection Gate 和 Project Switcher 在 `1366x768`、`1440x900`、`390x844`、`820x1180` 都有固定 Playwright snapshots。
- App Header 保持既有 52px row height。Project path 太長時，在 switcher trigger 內截斷；在 Project card/menu row 裡必要時可換行。

**驗證：**

- `frontend/e2e/project-startup.ui.spec.ts` 覆蓋 bootstrap、restore、stale session、close、switch、no-context gating 和 startup visual snapshots。
- `frontend/e2e/project-startup.fullstack.spec.ts` 覆蓋透過真實 `/api` 串接的 create/select 流程。

**S011 current implementation contract：**

- Current DOM selectors 使用 `.app-header`、`.app-header-menu`、`.side-navigation`、`.main-content-area`、`.task-details-pane`；`.topbar`、`.rail`、`.main-surface`、`.detail-pane` 只允許出現在 deprecated mapping 或 historical evidence。
- Current design tokens 使用 `appHeader.height`、`sideNavigation.width`、`sideNavigation.control`、`taskDetailsDrawer.width`；`topbar.*`、`rail.*`、`detailDrawer.*` 只作為 deprecated references。
- `ProjectSetupCopilot` 是 `ProjectSelectionGate` 內的 first-run / missing-session component，selector 是 `.project-setup-copilot`。
- `ProjectSetupError` 是 `ProjectSelectionGate` 內的 error component，selector 是 `.project-setup-error`。
- `ProjectPicker` / `.project-picker` 是已停用 selector；current `ProjectSelectionGate` 不應輸出它。
- First-run 使用 `新手引導` + `建立 Project 工作台` + primary `建立新 Project`，且不顯示 Project picker/list 或既有 Project rows。
- Project list 載入失敗使用 error setup panel：heading `無法載入 Project context`、button `重試`，不得沿用 first-run heading。
- S011 必須有自己的 `AC-S011-*` Playwright assertions；S010 startup tests 保留為 shipped regression，不拿來替代 S011 evidence。
- No-active Project 文案要短、可掃描，讓 setup 像任務管理工具的工作入口，不像 onboarding landing page。
- 既有 Project rows 不出現在 `ProjectSelectionGate`；窄版不需要為 no-active gate 設計 Project card。

**2026-06-07 S011 implementation / evidence update：**

- **設計研究：** S011 對照 Carbon Empty States / Global Header、Microsoft NavigationView、Atlassian Designing Messages、Material Layout、Telerik Design Tokens 和 Claude Code memory docs 後，維持「Main Content Area 內的 Project Setup Hero」方案。
- **文件索引：** 新增 `docs/grimo/design/README.md` 作前端設計索引；`CLAUDE.md` 只 import `AGENTS.md` 並指向該索引；`AGENTS.md` 的 Workflow Artifacts 列出 frontend design docs。
- **Historical 程式/selector：** 當時 selectors 是 `.app-header`、`.app-header-menu`、`.side-navigation`、`.main-content-area`、`.task-details-pane`、`.project-setup-hero`；後續 layout redesign 已改成 `.project-setup-panel` / `.project-picker`。
- **視覺基準：** 更新 4 張 Project selection gate snapshots：`desktop-1366`、`desktop-1440`、`mobile-390`、`tablet-820`。變更原因是 first-run/existing Project gate 從小 section head 升級為 Main Content Area hero，且 mobile Project card 改單欄。
- **驗證：** `npm --prefix frontend run build` 通過；`npx playwright test project-startup.ui.spec.ts --update-snapshots=changed` 通過 21/21；`npm --prefix frontend run test:visual` 通過 40/40；`python3 scripts/visual-snapshot-summary.py --repo-root .` 顯示 changed snapshots 只有上述 4 張。

**2026-06-07 S011 browser comment follow-up（superseded by layout redesign）：**

- **User evidence：** in-app Browser comment on `Project list` at `http://localhost:5173/` asked「看不懂這下半部區塊在幹麻?」；selected element was `.project-selection-list` under `ProjectSelectionGate`.
- **Decision：** Existing Projects state now wraps the cards in `.project-selection-existing` with heading `選擇現有 Project` and copy `回到已建立 Project 的 Task 工作台。`；hero height is shorter in `.project-selection-gate.has-projects` so the hero and existing list read as one setup flow.
- **Rejected alternative：** Only changing card border/spacing was rejected because the problem was semantic: the list lacked a visible purpose, not visual weight.
- **Verification：** `npm --prefix frontend run build` passed; `npm --prefix frontend run test:visual:update` regenerated only the 4 Project selection gate snapshots before final visual verification.

**2026-06-07 S011 layout redesign after task-management research：**

- **User evidence：** after seeing the labeled list, user said「整個 layout 不對」and asked to research task management services before redesigning.
- **Research synthesis：** Jira / Linear / Asana / ClickUp / GitHub Projects treat project/workspace as context selection and keep the main surface focused on list / board / inbox / detail preview. Existing Projects state should therefore be a compact picker, not a large setup hero.
- **Decision：** Existing Projects state renders `.project-picker` with heading `選擇 Project`, status `目前沒有開啟 Project`, secondary `建立 Project`, and dense Project rows with Project / Repo / Workflow columns.
- **Decision：** First-run and error states render `.project-setup-panel`; first-run may guide creation, but it remains compact and does not consume the page like a marketing hero.
- **Rejected alternative：** Keeping the S011 hero and only improving labels/spacing was rejected because it preserves the wrong information hierarchy.
- **Verification：** `npm --prefix frontend run build` passed; `npm --prefix frontend run test:visual:update` regenerated the 4 Project selection gate snapshots; `npm --prefix frontend run test:visual` passed 40/40.

**2026-06-07 first-run onboarding clarification（superseded by create-only correction）：**

- **User evidence：** user clarified「進入主畫面 當沒有任何專案 應該要有像是新手引導 建立新專案，不用呈現專案列表」。
- **Decision：** When `GET /api/projects` returns empty, `ProjectSelectionGate` renders only `.project-setup-panel.empty` with `新手引導`, `建立第一個 Project`, and `建立新 Project`; `.project-picker` and `.project-selection-card` must be absent.
- **Decision：** Existing Projects state remains `.project-picker`; this clarification only changes the no-projects first-run state.

**2026-06-07 no-active Project create-only correction（superseded by Project Setup Copilot）：**

- **User evidence：** user reviewed Edge at `127.0.0.1:5173/` and said「長這樣不好看 應該只要顯示建立新專案」 after seeing `S011 Chrome Smoke` listed under the no-active Project main surface.
- **Research sources：** Linear projects can be created from a workspace/team project view with a `+` and then open a prompt; Linear keeps project browsing in Projects pages with list/board/timeline views（https://linear.app/docs/projects）. Jira exposes `Create project` from Projects navigation and frames project structure around team / business unit / product choices（https://www.atlassian.com/software/jira/guides/projects/tutorials）. ClickUp creates Spaces from sidebars / All Spaces and then moves through name, details, workflow, and view choices（https://help.clickup.com/hc/en-us/articles/6309390855319-Create-and-edit-Spaces）. Asana creation starts from Quick add and then asks whether to create a blank project, use a template, or import（https://help.asana.com/s/article/understanding-projects）. Atlassian Empty State guidance says an empty state should describe the next action（https://atlassian.design/components/empty-state/）.
- **Research synthesis：** Competitors separate creation, browsing, and active context switching. A no-active main surface should make the next action obvious; it should not render a project table unless the screen is explicitly a Projects management page.
- **Decision：** `ProjectSelectionGate` now always renders `.project-setup-panel` for no-active Project states, even when `GET /api/projects` returns existing Projects. The heading and primary action are both `建立新 Project`; `.project-picker` and `.project-selection-card` must be absent.
- **Visual direction：** The setup panel is an unframed, left-aligned command surface centered in the Main Content Area: thin top divider, short body copy, black primary CTA, no cards, no gradients, no Project rows.
- **Decision：** Closed sessions and missing sessions keep `尚未開啟 Project` in App Header and do not auto-select another Project. Existing Project access stays in the `專案` page and, after a Project is active, the App Header `ProjectSwitcher`.
- **Supersedes：** This replaces the earlier 2026-06-07 layout redesign decision that made existing Projects state a compact `.project-picker`.
- **Verification：** `npm --prefix frontend run build` passed; `npx playwright test project-startup.ui.spec.ts --update-snapshots=changed` passed 21/21 and updated 4 Project selection gate snapshots; `npm --prefix frontend run test:visual` passed 40/40. `npm --prefix frontend run test:fullstack -- project-startup.fullstack.spec.ts` did not start because `http://127.0.0.1:8080/api/workflow-recipes` was already in use and fullstack config has `reuseExistingServer:false`.

**2026-06-07 Project session state and assistant-ui setup correction：**

- **User evidence：** user set the goal that first launch with no Project enters a lead-in page, open Project sessions restore directly, closed Project sessions enter the Project list, and the lead-in page must stop looking ugly. User also asked to research assistant-ui examples.
- **Research sources：** assistant-ui Thread UI documents welcome / empty state, suggestions, composer, scroll behavior, and message actions（https://www.assistant-ui.com/docs/ui/thread）. The assistant-ui architecture separates UI components, runtime, and state（https://www.assistant-ui.com/docs/architecture）. The Form-Filling AI Copilot example pairs a form with an AI sidebar for guided data entry（https://www.assistant-ui.com/examples/form-demo）. The Generative UI example shows assistant-rendered interactive UI（https://www.assistant-ui.com/examples/generative-ui）.
- **Decision：** `ProjectSelectionGate` first-run / missing-session state is now `.project-setup-copilot`, not a plain setup panel. It displays a left setup brief, a right assistant-style thread preview with suggestions and composer affordance, and a three-step capability strip.
- **Decision：** `closed session` is no longer a Project Selection Gate state. App bootstrap and `Close Project` both route to `專案管理` with existing Project rows visible and no task API request.
- **Decision：** `open session` still restores the matching Project and loads only that Project's task API.
- **Verification：** `npm --prefix frontend run build` passed; `npx playwright test project-startup.ui.spec.ts --update-snapshots=changed` passed 21/21 and updated the 4 Project selection gate snapshots.

### Task 工作台（Task Workbench）

**目的：** 主要 Task 管理畫面，用來追蹤 Task state、找出需要人處理的工作，並開啟 Task detail / review 流程。

**目前版面決策：**

- 桌面版使用 `Focus + Board`：上方 attention tray 顯示 `REVIEW` tasks 和帶 gaps / `NEEDS_HUMAN` repair reason 的 tasks；Kanban board 留在下方用來掃描六個 Task State。
- Focus tray 可收合。使用者不一定想立刻處理待處理項目，所以 tray 可以縮成 summary row。
- Focus header 是單行：`待處理焦點` 後面接小型 `需要你處理` 狀態標籤。這個標籤不應該獨占一行。
- 桌面版上 search 和 `新增 Task` 保持在同一列 toolbar。手機版可以堆疊。
- Main navigation 預設以 overlay 開啟。只有使用者按 pin button 後，才改變 workbench layout。

**響應式行為：**

- 桌面版：focus tray 加水平 Kanban board。
- 平板/手機版 breakpoint：隱藏 board，改成 grouped task list。
- 手機版 list row 顯示 state、task id、title、labels、updated time、score 和 comments。Task source 不在 detail 之外顯示。
- Card labels 只能顯示使用者看得懂的分類，且必須來自 prototype 定義的 task label taxonomy。不要把 `source` 值如 `chat`，或 skill/workflow capability name 如 `task-forming`，放進 list-level label chips。
- Cards 和手機版 rows 可以顯示 Task Conversation Preview 線索，例如最近討論摘要、未解問題數、comment count 或 attachment count，但不能顯示完整 raw transcript。

**元件備註：**

- `.focus-strip`：給人類行動用的 attention tray，必須支援 expanded / collapsed states。
- `.focus-toggle`：控制 focus tray 收合/展開，visible labels 必須精確使用 `收合` 和 `展開`。
- `.board-grid`：只給桌面版 board 使用。不要把 Kanban columns 硬塞到手機版。
- `.mobile-task-list`：手機版和平板的 list surface。它依六個 board-facing Task State 分組；needs-human repair 以 row 內提示呈現，不新增 `BLOCKED` 分組。
- Side Navigation 的 S011 target class 是 `.side-navigation`：unpinned main nav 是 overlay。只有 `.nav-pinned` 可以配置左側 column。

**未決問題：**

- 有 user preferences 後，focus collapse state 是否要跨 reload 保存。
- Attention tasks 是否要除了 `REVIEW` 和 needs-human repair 外也包含 `READY`，特別是在 user-started dispatch window 開啟時。

### Task 詳情（Task Detail）

**目的：** Drawer / full-page review surface，用來查看 task state、workflow step、quality、evidence、acceptance materials、gaps 和下一步操作。

**目前版面決策：**

- Detail 在使用者選取 task 前仍是 workbench 的次要畫面。
- Review actions 要等 state mutation 存在後，才變成真正的 approve/reject controls。
- 現階段 focus card actions 先導向 task detail，不假裝會改變 review state。
- Full-page detail 是 `REVIEW` 的工作畫面。第一個桌面版 viewport 必須看到 human gate summary、REVIEW tasks 的 approve/reject controls、review materials、evidence package、timeline、risk notes 和 linked work。
- Task source 只顯示在 task detail。不要升級到 board cards、手機版 rows、attention cards、search placeholder text 或 create-task copy。
- Task detail 是少數使用者可輸入的 surfaces 之一，另一個是 Chat。使用者看得到的文案要描述回到 `Chat` 繼續探索或規劃，不要說另一個 context-repair job。
- Detail headers 只顯示 task identity 和 Task List State。`Discuss` 等 Workflow recipe steps 留在 `Stage & Quality`，不要放到 header chips。
- Task detail 可以顯示 Task Conversation Preview 和 attachment summaries。完整 message history 和 attached files 透過 task `Chat` 或完整 detail evidence surfaces 開啟。

**響應式行為：**

- 桌面版可使用 drawer 或 full page。
- 手機版使用目前 CSS 的 bottom sheet style drawer behavior。

**未決問題：**

- 大量 evidence sets 要怎麼呈現：diff、logs、screenshots、risk notes、review findings、fix history。

### 待處理頁（Attention Page）

**目的：** 專門處理人類行動的 queue，包含 `REVIEW` approvals、`NEEDS_HUMAN` recovery，以及需要更多討論或明確使用者決策的 definition gaps。

**目前版面決策：**

- 這個頁面不是第二個 Kanban board。它先摘要 action counts，再把 `REVIEW` tasks 和 needs-human repair tasks 列成 priority queue。
- `REVIEW` 和 needs-human repair 共用主 queue，因為兩者都會卡住進度：`REVIEW` 卡住 `DONE`，needs-human repair 卡住 dispatcher 或 workflow recovery。Release evidence 若存在，會在 `DONE` task 內 review。
- Definition gaps 是右欄的次要 decision material，讓使用者可以掃描未完成 tasks，但不把它們混進 urgent queue。
- Attention cards 顯示真實 task labels，不顯示 recipe steps。`Prototype`、`Spec`、`Review` 等 recipe steps 屬於 Task detail / Workflow evidence，不是 list-level label chips。

**響應式行為：**

- 桌面版 使用 main queue 加右側 sidebar。
- 平板/手機版 將 summary cards、queue cards 和 sidebar panels 堆疊成單欄。

**元件備註：**

- `.attention-summary`：review、blocked、definition gaps 三個 compact counters。
- `.attention-task`：urgent queue 的 repeated task cards。
- `.attention-sidebar`：gap distribution、blocker summary、recent handling notes 的 secondary diagnostics。

### Projects 頁

**目的：** 使用者進入 Task work 前，用來建立 Project context 的入口畫面。

**目前版面決策：**

- First-run Project setup 必須先用 `docs/grimo/design/screen-flow-contract.md` 規劃，再改 app bootstrap 或 Project onboarding screens。
- Project management 從 list-first view 開始。使用者應先看到 existing Projects 或 empty state，再看到 create fields。
- `新增專案` 開啟獨立 create view，並提供 `返回列表`；create form 不永久顯示在 Project list 旁邊。
- Project Path 是 optional field。使用者可以貼 `/Users/.../repo`；留空時，Grimo 在 `~/.grimo/projects/<projectId>` 下建立 default project path。
- S012 T01 已驗證 compact Local Directory Picker 可行，但使用者回饋 backend directory tree 會讓操作變複雜、看不懂系統資料夾；S012 已由 S013 supersede，不再作為 active Project Path UX。
- Browser-native `showDirectoryPicker()` POC 顯示 Chromium/localhost 可開 OS-like chooser，但回傳 browser handle，不 expose backend absolute path；不能直接作為 `projectPath` source。
- S013 的 OS folder chooser first 已被 S014 設計取代為 Project Path Folder Browser：前端按 `選擇資料夾` 後打開 Grimo modal，local backend 透過 `GET /api/local-directories` 回目前 path、上層與可讀子資料夾；預設起點是 `~/.grimo/projects/`，並提供 `回家目錄` 與 `回 Grimo 預設位置` 兩個 Finder-like shortcut。
- Project Path Folder Browser 要支援 `建立新資料夾`：使用者輸入資料夾名稱後，Grimo 在目前位置建立 child directory，成功後直接把新資料夾 absolute path 回填到 `projectPath` input 並關閉 modal；Project 仍只由頁面上的 `建立專案` submit。
- Manual path entry、歷史 Local Directory Picker、Native Folder Dialog Bridge、Project Path Folder Browser 都必須共用同一個 `projectPath` contract；不新增 `projectPathSource`、`browserProjectPathKey` 或其他 source/readiness 欄位。
- Project Path Folder Browser 的 cancel/關閉是 no-op，不清空 Project Creation form；directory API error 只顯示在 modal 內，manual `projectPath` input 維持可編輯。
- Full-stack automation 應使用 real `GET /api/local-directories` + real `POST /api/projects` 驗證 selected path；不再 mock native dialog endpoint 作為 primary UX evidence。
- Drag-and-drop folder import 不屬於 S003 main flow。它太隱晦，而且和 browser handles 一樣無法取得 absolute path。
- Project Creation 不應該在 form 下方顯示很長的 backend-generated directory browser；S014 folder browser 必須是可關閉的 modal / bounded overlay，不把 create form 拉成長清單。
- S014 folder browser modal 的 disabled action 必須在視覺上明確：default root 不能被選成 Project Path，因此 `使用此資料夾` 要呈現灰階 disabled，不可保留 primary green 外觀。
- `新增專案` 表單資訊層級仍應改善：桌面版整理成主表單 + compact workflow/roles preview；手機版維持單欄。S014 只規劃 folder browser primary UX；更大的 form polish 若超出 picker 可另開 spec。

**響應式行為：**

- 桌面版 和 手機版 使用同一套 list/create mode split。
- Create view 在桌面版可使用兩個 zone：左側填表，右側顯示 compact workflow/roles preview；project path input state 必須保持 compact，不要把 workflow roles 推到 fold 下方很遠。
- Create view 在手機版堆疊 form fields、path picker、workflow preview、roles preview；文字不可溢出或互相重疊。
- S014 folder browser modal 在 `1366x768`、`1440x900`、`820x1180`、`390x844` 都維持 bounded overlay；長路徑使用 wrapping，不讓 directory rows、建立資料夾 input、footer actions 互相重疊。

**元件備註：**

- Manual project paths 顯示 validated local path。
- 沒有 user-selected path 的 Projects 顯示 generated `projectPath`，仍然是 valid Projects。
- UI 在 native bridge 能提供 backend-operable paths 前，不顯示 browser-selected folders 作為 Project paths。

### Workflow 頁

**目的：** 給產品使用者看的 reference view，用來說明 Project Workflow Recipe steps 如何對應 Task List State。

**目前版面決策：**

- Web development workflow 把 verification 保留在 `RUNNING` 裡，不把它做成一個 first-class Task List State。
- `RUNNING` 顯示 implementation 和 evidence work：`Dev / Unit-test / Integration-test / E2E-test`。
- `REVIEW` 保持在 verification evidence 完成後的人類 approve/reject state。
- `DONE` 顯示 `Release` 作為 web development workflow 的 completion subflow；release evidence 留在 DONE task 裡，不成為另一個 board column。

**不要做：** 不要新增 `VERIFYING` board/list state，也不要把 Quality Loop 裡的 `Review` 當成 human approval。

**驗證：** `npm run build`；backend `ProjectApiTests.exposesWebServiceDevelopmentRecipeDefinition`。

## 4. 元件決策

### Logo 標誌

- **決策：** 在 topbar brand mark 使用 `frontend/src/assets/grimo-logo.png`，外側角落透明，34px visual box 對齊 `.topbar-menu`。
- **原因：** 使用者提供 logo，要求移除背景，並要求大小和 menu button 一樣。
- **調整：** Logo image 可以稍微大於 34px brand box，但 桌面版 topbar row 必須維持 52px 高。
- **不要做：** 不要退回舊的 text-only `G` mark，不要加額外 `.brand-mark` border/background，也不要讓 logo 撐高 topbar row。
- **驗證：** `npm run build`、`npm run test:visual`。

### 建立 Task 按鈕

- **決策：** 每個 viewport 只保留一個 primary `新增 Task` button。
- **原因：** 重複的 primary actions 會造成 command hierarchy 不清楚。
- **不要做：** 不要在 focus tray 或 board mode controls 內新增同 scope 的第二個 create button。
- **驗證：** Browser comment 和 visual snapshots。

### 主導覽（Main Navigation）

- **決策：** Nav 在 pinned 前以 overlay 開啟。
- **原因：** 開啟 nav 不應該在一般瀏覽中一直改變 board layout。
- **不要做：** 不要只因為 `.nav-open` 就改變 grid columns。
- **驗證：** Playwright test `main navigation overlays until pinned`。

### 待處理焦點列（Focus Tray）

- **決策：** 支援 expanded 和 collapsed states。
- **原因：** 使用者有時只想知道有哪些待處理項目，不一定要立刻處理，所以需要可收合。
- **不要做：** 不要強迫 attention cards 永久佔用垂直空間。
- **驗證：** Playwright test `attention focus can collapse and expand`。

### Task 詳情全頁

- **決策：** Full-page `REVIEW` detail 以 human approval gate 作為主要內容，不只是 Acceptance 和 Evidence lists 的稀疏複製。
- **原因：** 使用者在這個頁面決定 approve/reject，所以第一屏要看到 compact review summary、decision actions、execution timeline、risk notes 和 linked work。
- **不要做：** 不要把 full-page detail 做成 drawer 的稀疏唯讀 duplicate。
- **驗證：** Playwright test `full page detail baseline` 斷言 `審查結論`、`Approve`、`Reject`；snapshot `task-detail-full-page-chromium-darwin.png`。

### Task 來源

- **決策：** Task `source` 只顯示在 task detail。
- **原因：** 2026-05-28 使用者回饋：task 從哪裡來，對 list-level triage 沒有幫助。Source 保留為 provenance metadata，但不應該和 state、gap、quality score、evidence 或 next action 競爭。
- **不要做：** 不要在 board cards、手機版 list rows、`待處理` 頁、search placeholder text 或 create-task explanatory copy 顯示 `source`。
- **驗證：** `npm run build`、`npm run test:visual`。

### Task 標籤

- **決策：** Board、手機版、focus、attention card chips 只顯示使用者看得懂的 task labels。
- **原因：** 2026-05-29 使用者回饋：`GRM-144` 上的 `chat task-forming` 不清楚，因為 `chat` 是 source，`task-forming` 是 skill/workflow capability，不是 label。
- **來源：** Label options 來自 `docs/grimo/ui/prototype/index.html` 的 `taskLabelPicker`：`bug`、`documentation`、`duplicate`、`enhancement`、`good first issue`、`help wanted`、`invalid`、`question`、`wontfix`、`frontend`、`backend`、`ci/cd`、`design`、`research`。
- **不要做：** 不要把 `source`、workflow recipe steps、skill names 或臨時 ad hoc labels 當成 card label chips。
- **驗證：** Playwright board baseline 斷言 `GRM-144` card 上沒有 `task-forming`；`task fixtures use prototype-defined labels` 斷言 fixture labels 全部在 prototype taxonomy 裡。

### Badge 語義

- **決策：** Task id、Task List State、task label 和 metric badges 必須有不同視覺處理。
- **原因：** 2026-05-29 使用者回饋：`GRM-144`、`BACKLOG`、`frontend` 看起來太像，不清楚哪個是 identity、state 或 label。
- **不要做：** 不要用同一種 neutral pill style 呈現 task id、state 和 labels。
- **實作：** `Badge` 支援 `kind="task-id"`、`kind="state"`、`kind="label"`、`kind="metric"`；board card labels 使用 `.badge.label`，detail headers 使用 `.badge.task-id` 和 `.badge.state`。
- **驗證：** Playwright 斷言 `GRM-144` card labels 使用 `.badge.label`，task detail headers 透過 `.badge.task-id` 和 `.badge.state` 顯示 task id/state。

### 回到 Chat 的路徑

- **決策：** 使用者看得到的 actions 應寫 `Chat` 或 `回到 Chat 繼續探索或規劃`，不要寫工程術語式的 context。
- **原因：** 2026-05-28 使用者回饋：context-filling language 是工程術語，不是產品動作。真正互動是回到 chat interface，繼續探索、規劃、釐清或 refine task。
- **不要做：** 不要呈現 `review-material` 或 `gap-view` 這類獨立 list-level actions。Attention cards 和 focus cards 應導向 `Chat` 進行討論和釐清。
- **輸入 surfaces：** 使用者可以輸入的地方是 Task detail 和 Chat。其他 surfaces 應導回其中一個，而不是暗示第三種 input workflow。
- **驗證：** `npm run build`；因為 task detail、attention page 和 workbench focus action copy 變更，visual snapshots 需要更新。

### Task 對話串與預覽

- **決策：** 每個 Task 都擁有 durable Task Conversation Thread。開啟 task `Chat` 時，顯示完整 task thread：歷史 user messages、agent replies、attached files、referenced files、external links、clarifications 和重要 system event summaries。
- **原因：** 使用者釐清每個 task 都應能開啟完整 conversation history 和 attachments；collapsed Chat 只顯示最近訊息和 key summary，類似 issue comments 附著在工作上。
- **Preview 規則：** Board cards、手機版 rows、attention cards、detail summaries 和 collapsed Chat 可顯示 Task Conversation Preview：recent messages、key summary、unresolved questions、comment count 和 attachment count。
- **不要做：** 不要從 task 開啟 blank generic chat，不要把 task attachments 藏在 provider transcripts 裡，不要在 list surfaces 顯示 raw transcripts，也不要把 attachments 當 labels/source chips。
- **輸入 surfaces：** Chat 是主要 conversation surface；Task detail 可以接受 structured task edits 或 review decisions。其他 list-level surfaces 應導回其中一個。
- **驗證：** 此次為 documentation-only decision；未來 UI implementation 應加入 Playwright assertions，確認 task `Chat` 連到 selected task，且 cards 顯示 preview metadata 而非完整 transcripts。

### Task 附件

- **決策：** Attachments 優先屬於 Task Conversation Thread 和 Task detail。當 attachments 成為 approval evidence 時，可以 promoted 或 linked 到 Review Materials。
- **原因：** Attachments 協助保存 context 和 discussion history，但 list-level triage 應專注在 state、labels、quality 和 next action。
- **不要做：** 不要把 attachments 顯示成 task labels、source metadata 或獨立 board chips。Detail/chat 之外只使用 attachment count 或 compact hints。
- **驗證：** 此次為 documentation-only decision。

### 待處理佇列

- **決策：** `待處理` 是 human-action queue，包含 count summary、priority task cards 和 diagnostic sidebar。
- **原因：** Plain needs-human-only list 會隱藏 `REVIEW` approvals，也無法說明是哪個 action 卡住進度。
- **不要做：** 不要複製完整 board，不要讓每個 task 權重相同，也不要新增獨立 list-level `審查材料` / `查看缺口` buttons。
- **驗證：** Playwright test `attention page baseline` 斷言 `優先處理`、不存在 `審查材料` / `查看缺口`，並顯示 `Chat`；snapshot `attention-page-chromium-darwin.png`。

## 5. Visual Gate 紀錄

### 2026-06-07 — Project Setup Copilot And Closed Session Routing

- **指令：** `npm --prefix frontend run build`、`npx playwright test project-startup.ui.spec.ts --update-snapshots=changed`、`npm --prefix frontend run test:visual`、`npm --prefix frontend run test:fullstack`、`scripts/verify-release.sh`
- **結果：** build 通過；startup targeted suite 通過 21/21；完整 visual suite 通過 40/40；full-stack suite 通過 7/7；release gate PASS。
- **截圖基準變更：** 更新 4 張 `project-selection-gate-*` snapshots：`desktop-1366`、`desktop-1440`、`mobile-390`、`tablet-820`。
- **原因：** first-run 前導頁改成 assistant-ui 啟發的 `Project Setup Copilot`；closed session 改成直接進 `專案管理`，不再顯示前導頁。
- **測試修正：** full-stack tests 使用 exact `專案` navigation selector，並在 startup create path 明確設定 missing-session localStorage，避免受同一 suite 前面建立的 Project 資料影響。

### 2026-06-07 — No-Active Project Create-Only Gate（superseded）

- **指令：** `npm --prefix frontend run build`、`npx playwright test project-startup.ui.spec.ts --update-snapshots=changed`、`npm --prefix frontend run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** build 通過；startup targeted suite 通過 21/21；完整 visual suite 通過 40/40。
- **截圖基準變更：** 更新 4 張 `project-selection-gate-*` snapshots：`desktop-1366`、`desktop-1440`、`mobile-390`、`tablet-820`。
- **原因：** no-active Project 主畫面改成只顯示 `建立新 Project` 的 compact setup panel；既有 Project rows / picker 從 gate 移除。
- **未執行項目：** `npm --prefix frontend run test:fullstack -- project-startup.fullstack.spec.ts` 被現有服務擋住，錯誤為 `http://127.0.0.1:8080/api/workflow-recipes is already used`；fullstack config 設定 `reuseExistingServer:false`，因此本次沒有殺掉既有服務。

### 2026-06-07 — S010 Project Startup Gate And Switcher

- **指令：** `npm --prefix frontend run test:visual:update`、`npm --prefix frontend run test:visual`、`npm --prefix frontend run test:fullstack`
- **結果：** 通過；visual gate 現在有 34 個 Playwright checks，包含 S010 Project startup、API failure retry 和 switcher screenshots。
- **截圖基準變更：** 新增 桌面版、手機版、平板 viewports 的 `project-selection-gate-*` 和 `project-switcher-*` snapshots；更新 Task Workbench snapshots，因為 topbar 現在顯示真實 Project context，不再使用舊 fixture fallback。
- **原因：** App first load 現在必須有真實 Project context。First-run / closed / stale sessions 顯示 Project gate，active sessions 恢復 matching Project，topbar current Project 成為 Project Switcher。

### 2026-05-28 — Task Workbench Round 1

- **指令：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`
- **結果：** 通過
- **截圖基準變更：** 第一輪 redesign 期間，Task workbench 桌面版、平板、手機版、drawer、dialog snapshots 改變。
- **原因：** 新增 light-theme `Focus + Board`、手機版 grouped list、single-row toolbar、logo、overlay-until-pinned navigation 和 collapsible focus tray。

### 2026-05-28 — Task Detail Full Page Completion

- **指令：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`、`npm run build`
- **結果：** 通過
- **截圖基準變更：** `frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-detail-full-page-chromium-darwin.png`
- **原因：** Full-page `REVIEW` detail 現在包含 human gate decision area、approve/reject actions、review summary、execution timeline、risk notes 和 linked work。

### 2026-05-28 — Attention Page Completion

- **指令：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`
- **結果：** 通過
- **截圖基準變更：** `frontend/e2e/task-workbench.visual.spec.ts-snapshots/attention-page-chromium-darwin.png`
- **原因：** `待處理` 現在顯示 human-action counts、`REVIEW`/needs-human priority queue、definition gaps、repair summary 和 handling notes。

### 2026-05-28 — Source Metadata Demotion

- **指令：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`、`npm run build`
- **結果：** 通過
- **截圖基準變更：** task workbench 桌面版/平板/手機版、task detail drawer、create task dialog 和 attention page snapshots。
- **原因：** 使用者指出 task source 在 list level 沒有幫助。Source 現在只顯示在 task detail；board、手機版 list、attention page、search placeholder 和 create-task copy 不再凸顯 source。
- **截圖摘要：** `python3 scripts/visual-snapshot-summary.py --repo-root .` 回報 8 個 changed baselines：1 個新增 attention page snapshot、7 個更新 workbench/detail/dialog snapshots，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-28 — Chat Return Path Copy

- **指令：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 通過；visual gate 現在有 12 個 Playwright checks，包含 `chat action returns to task-forming chat`。
- **截圖基準變更：** task workbench 桌面版/平板/手機版、task detail drawer、task detail full page 和 attention page snapshots。
- **原因：** 使用者釐清 list-level actions 應顯示 `Chat`，並回到 chat interface 繼續探索或規劃。產品上沒有獨立的 user-facing context-filling job；使用者輸入屬於 Task detail 或 Chat。
- **截圖摘要：** 目前 summary 回報 git status 裡有 8 個 changed baselines，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-28 — Attention Actions Collapse To Chat

- **指令：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 通過；`attention page baseline` 現在斷言沒有 `審查材料` 或 `查看缺口` buttons，並有可見的 `Chat` action。
- **截圖基準變更：** task workbench 桌面版/平板/手機版、task detail drawer、create task dialog 和 attention page snapshots。
- **原因：** 使用者釐清 `審查材料` 和 `查看缺口` 不應作為 list-level action buttons 出現。Focus 和 attention cards 現在導向 `Chat` 進行討論和釐清。
- **截圖摘要：** 目前 summary 回報 git status 裡有 8 個 changed baselines，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-29 — Attention Cards Use Labels, Not Recipe Steps

- **指令：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 通過；`attention page baseline` 現在斷言 attention task cards 上沒有 `Prototype`。
- **截圖基準變更：** `frontend/e2e/task-workbench.visual.spec.ts-snapshots/attention-page-chromium-darwin.png`
- **原因：** 使用者釐清 labels 已經定義，`Prototype` 作為 chip 看起來像 task label。Attention cards 現在 render `task.labels`；workflow recipe steps 保留在 Task detail / Workflow evidence。
- **截圖摘要：** 目前 summary 回報 1 個 changed baseline，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-29 — Detail Header Separates State From Step

- **指令：** `npm run build`、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 通過；detail visual tests 現在斷言 recipe steps 不在 detail header chip rows 裡。
- **截圖基準變更：** `frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-detail-drawer-chromium-darwin.png`、`frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-detail-full-page-chromium-darwin.png`
- **原因：** 使用者選到 `BACKLOG Discuss` 並指出設計語言混在一起。Detail headers 現在顯示 task id 和 Task List State；workflow recipe steps 保留在 `Stage & Quality`。
- **截圖摘要：** 目前 summary 回報 3 個 changed baselines，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-29 — Card Chips Use User Labels Only

- **指令：** `npm run build`、`npm run test:visual`、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 通過；board visual tests 現在斷言 `GRM-144` card 上沒有 `task-forming`。
- **截圖基準變更：** task workbench 桌面版/平板/手機版、task detail drawer、create task dialog、task detail full page 和 attention page snapshots。
- **原因：** 使用者詢問 `GRM-144` card 上的 `chat task-forming` 是什麼意思。`chat` 是 task source，`task-forming` 是 skill/workflow capability，兩者都不屬於 card label chips。Card fixtures 現在只使用 prototype-defined label taxonomy 裡的 labels；source 留在 task detail。
- **截圖摘要：** 目前 summary 回報 8 個 changed baselines，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-29 — Mock Labels Follow Prototype Taxonomy

- **指令：** `npm run build`、`npm run test:visual`、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 通過；visual gate 現在有 13 個 Playwright checks，包含 `task fixtures use prototype-defined labels`。
- **截圖基準變更：** task workbench 桌面版/平板/手機版、task detail drawer、create task dialog、task detail full page 和 attention page snapshots。
- **原因：** 使用者釐清 labels 已經定義，mock data 應使用那些定義，而不是臨時 invented examples。React task fixtures 現在使用 `frontend/src/domain/task/task-labels.ts`，來源是 prototype label picker；create-task label input 提供相同 options。
- **截圖摘要：** 目前 summary 回報 8 個 changed baselines，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-29 — Badge Semantics Are Visually Distinct

- **指令：** `npm run build`、`npm run test:visual`、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 通過；visual gate 維持 13/13，並包含 semantic badge assertions。
- **截圖基準變更：** task workbench 桌面版/平板/手機版、task detail drawer、create task dialog、task detail full page 和 attention page snapshots。
- **原因：** 使用者指出 labels、`GRM-144` 這類 task id 和 `BACKLOG` 這類 states 看起來太像。Task id badges 現在是 rectangular mono tokens，Task List State badges 使用 semantic state styling，task labels 使用較柔和的 category chips 和小 marker，metrics 使用 neutral compact style。
- **截圖摘要：** 目前 summary 回報 8 個 changed baselines，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-29 — Logo Slightly Larger Without Topbar Growth

- **指令：** `npm run build`、`npm run test:visual`、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 通過；visual gate 維持 13/13，且 桌面版 board tests 斷言 `.topbar` 高度維持 52px，同時 `.brand-mark img` 以 38px render。
- **截圖基準變更：** task workbench 桌面版/平板/手機版、task detail drawer、create task dialog、task detail full page 和 attention page snapshots。
- **原因：** Browser comment 要求 topbar logo 稍微大一點，但不要增加 row height。Brand box 維持 34px；image 以 38px render 並允許 visible overflow，所以 row 維持固定高度。
- **截圖摘要：** 目前 summary 回報 8 個 changed baselines，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

### 2026-05-28 — Topbar Logo Background And Size

- **指令：** `npm run build`、`npm run test:visual`（sandbox 因 `EPERM` 擋住 dev server）、`npm run test:visual:update`、`npm run test:visual`、`python3 scripts/visual-snapshot-summary.py --repo-root .`
- **結果：** 更新 intentional topbar logo snapshots 後通過；final visual gate 是 12/12 通過。
- **截圖基準變更：** task workbench 桌面版/平板/手機版、task detail drawer、create task dialog、task detail full page 和 attention page snapshots。
- **原因：** Browser comment 要求移除 logo 背景並對齊 menu-button size。Topbar logo 現在使用 transparent PNG，且 34px visual box 對齊 `.topbar-menu`。
- **截圖摘要：** 目前 summary 回報 git status 裡有 8 個 changed baselines，以及 `frontend/test-results/.last-run.json` 和 `frontend/playwright-report/index.html` 的 local evidence artifacts。

## 6. Browser Comment 收斂紀錄

### 2026-05-28 — Search Field Toolbar

- **回饋：** Search input 應和 `新增 Task` 在同一列。
- **決策：** 接受
- **結果：** 桌面版 `.toolbar` 不再換行；手機版 保持 stacked controls。
- **驗證：** `npm run build`、`npm run test:visual`。

### 2026-06-13 — S014 Project Path Folder Browser Visual Gate

- **決策：** Project Path Folder Browser 使用 bounded modal overlay；長 path、directory rows、建立資料夾 panel 和 footer actions 在 desktop/tablet/mobile 都要留在 modal 內，不把 Project Creation Page 拉成長清單。
- **結果：** 新增 `AC-S014-8` visual gate，覆蓋 `desktop-1366`、`desktop-1440`、`tablet-820`、`mobile-390`。default root 的 `使用此資料夾` 與 page-level disabled `建立專案` 使用灰階 disabled 外觀，不再保留 primary green。
- **驗證：** `npm run build`；`npm run test:visual -- project-management.ui.spec.ts --grep "AC-S014-8"` 先因新 snapshot 不存在紅燈；檢查 actual screenshots 後用 `npx playwright test project-management.ui.spec.ts --grep "AC-S014-8" --update-snapshots=all` 新增 baseline；最後 `npm run test:visual -- project-management.ui.spec.ts --grep "AC-S014-8"` 通過。
- **截圖基準變更：** 新增 `frontend/e2e/project-management.ui.spec.ts-snapshots/project-folder-browser-desktop-1366-chromium-darwin.png`、`project-folder-browser-desktop-1440-chromium-darwin.png`、`project-folder-browser-tablet-820-chromium-darwin.png`、`project-folder-browser-mobile-390-chromium-darwin.png`。

### 2026-05-28 — 手機版 Task 版面

- **回饋：** 手機版 應該像 list，因為 board layout 很難呈現。
- **決策：** 接受
- **結果：** 手機版/平板使用 `.mobile-task-list`；桌面版保留 `.board-grid`。
- **驗證：** `npm run build`、`npm run test:visual`。

### 2026-05-28 — Main Navigation Pinning

- **回饋：** Main nav 除非 pinned，否則應該浮在 board 上方。
- **決策：** 接受
- **結果：** Unpinned nav 是 overlay；pinned nav 改變 layout。
- **驗證：** Playwright test `main navigation overlays until pinned`。

### 2026-05-28 — Focus Tray Collapse

- **回饋：** `待處理焦點` 需要 collapse/expand，因為使用者有時不想處理它。
- **決策：** 接受
- **結果：** Focus tray 有 `收合` 和 `展開` states。
- **驗證：** Playwright test `attention focus can collapse and expand`。

### 2026-05-28 — Focus Header Density

- **回饋：** `需要你處理` 應放在 `待處理焦點` 後面，不要佔兩行。
- **決策：** 接受
- **結果：** `需要你處理` 是小型 inline status tag。
- **驗證：** `npm run build`、`npm run test:visual`。

### 2026-05-28 — Topbar Logo Background And Size

- **回饋：** Logo 要移除背景，並對齊 menu button 大小。
- **決策：** 接受
- **結果：** `frontend/src/assets/grimo-logo.png` 現在外側角落透明；`.brand-mark` 和 `.brand-mark img` 是 34px，對齊 `.topbar-menu`。
- **驗證：** `npm run build`、`npm run test:visual`。
