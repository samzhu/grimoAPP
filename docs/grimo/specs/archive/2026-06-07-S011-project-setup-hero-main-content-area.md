# S011: Project Setup Hero in Main Content Area

> 規格：S011 | 大小：L(15) | 狀態：✅ Done
> 日期：2026-06-07
> 對應：PRD §0.1、§0.2；spec-roadmap row S011；S010 shipped startup flow

---

## 1. 目標

讓第一次開啟 Grimo 或沒有 active Project 的使用者，在 `Main Content Area` 看到清楚的大主視覺建立引導，而不是只看到一條 App Header 後面接大片空白。

S010 已完成正式啟動流程：沒有 Project 時停在 `Project Selection Gate`，有 open session 時恢復上次 Project，`Close Project` 後回到選擇 gate。本 spec 不重做 session restore、Project API、Project create form 或 backend；它設計 `Project Selection Gate` 裡的 `Project Setup Hero` 版面、文案層級、navigation/gate 行為、layout shell class rename 和 visual evidence。

相依分類：

| 相依 | 分類 | 狀態 | 對 S011 的影響 |
| --- | --- | --- | --- |
| S010 Formal product startup flow | code-level 已 shipped | shipped | S011 直接沿用 `ProjectSelectionGate`、`ProjectSwitcher`、session gate 行為，不改 API。 |
| S003 Project management list/create | code-level 已 shipped | shipped | `建立 Project` 仍導向既有 Projects create view。 |
| S004 Task creation API | ordering-only | shipped | S011 不改 Task API，但要維持沒有 Project 時不顯示 Task fixture。 |

Overlap scan：

- Active specs table 目前沒有其他 in-flight spec。
- Shipped S010 已覆蓋「是否顯示 gate」和「session restore」；S011 只覆蓋 gate 內部的 Main Content Area onboarding visual contract，不與 S010 重疊超過 50%。

範圍：

- In scope：`Project Setup Hero` 命名、版面、first-run / closed / stale session 變體、Main Content Area wireflow、layout shell class rename、Playwright visual assertions / snapshots。
- Out of scope：backend Project API、localStorage session semantics、Project create form fields、native folder picker、agent dispatch、Task board 功能、與 rename 無關的視覺重設計。

## 2. 研究與設計

### 2.1 查到的事實

| 來源 | 查到什麼 | 對設計的影響 |
| --- | --- | --- |
| `docs/grimo/PRD.md` | Grimo 的核心不是聊天視窗，而是 Project 底下的 Task 工作台；Project 決定工作流和品質基準。 | 沒有 Project 時，主內容不能顯示 Task board 或 generic Chat，必須先引導建立 Project context。 |
| `docs/grimo/specs/archive/2026-06-07-S010-formal-product-startup-flow.md` | S010 已定義 no active Project 時顯示 Project gate，且 Task 管理、待處理、Chat 不顯示 fixture data。 | S011 不改 gating 邏輯，只改 `ProjectSelectionGate` 內部資訊架構和 visual layout。 |
| `frontend/src/features/projects/ProjectSelectionGate.tsx` | 現有 gate 用 `.section-head project-selection-head` 呈現 heading/copy/action；first-run 時下方沒有內容，造成大片空白。 | 需要把 first-run 引導升級為 `Project Setup Hero`，佔用 Main Content Area 的主要可視區域。 |
| `frontend/src/App.tsx` | `shouldShowProjectGate` 只會擋 `tasks / blockers / chat`；`projects / workflow` 沒 active Project 時仍可進入。 | Screen Flow Contract 必須明確列出例外，避免把全部選單項目都誤認為被 gate 擋住。 |
| `frontend/src/App.tsx`, `frontend/src/app/Navigation.tsx`, `frontend/src/features/task-detail/TaskDetail.tsx`, `frontend/src/styles.css` | S011 前 layout shell class 是 `.topbar`、`.rail`、`.main-surface`、`.detail-pane`，和正式設計詞不一致。 | S011 local implementation 已同步 rename 為 `.app-header`、`.side-navigation`、`.main-content-area`、`.task-details-pane`，避免後續 test/spec 繼續使用舊詞。 |
| Carbon Empty States — https://carbondesignsystem.com/patterns/empty-states-pattern/ | Empty state 應出現在原本資料會出現的位置；first-use no-data state 要說明資料出現後會有什麼，並提供下一步；大型 empty state 可用置中的 left-aligned block。 | `Project Setup Hero` 應放在 Main Content Area 內；S011 target selector 是 `main.main-content-area`，legacy selector 是 `main.main-surface`。 |
| Carbon Global Header — https://carbondesignsystem.com/patterns/global-header/ | UI shell 可依資訊架構組合 header 與 left panel；global header 幫助使用者定位目前狀態。 | Grimo 的 `App Header`、`Side Navigation`、`Main Content Area` 應分開命名；Project setup onboarding 不放 App Header。 |
| Microsoft NavigationView — https://learn.microsoft.com/en-us/windows/apps/design/controls/navigationview | NavigationView 提供 top-level navigation，支援 expanded left pane、LeftCompact 和 LeftMinimal display modes。 | Grimo 左側可收合、有文字 label 的導覽應稱為 `Side Navigation`，不是 icon-only rail。 |
| Microsoft List/details — https://learn.microsoft.com/en-us/windows/apps/develop/ui/controls/list-details | List/details pattern 由 list pane 和 details pane 組成；選到項目時 details pane 更新。 | Task 選取後的右側資訊區應稱為 `Task Details Pane`；浮出覆蓋時才稱 drawer。 |
| Octopus Empty State — https://www.octopus.design/latest/patterns/ui-patterns/layouts/empty-state-YMQIoJ84 | Onboarding empty state 是 full-page experience，佔用 body container、排除 header navigation；包含 heading、description、add action。 | `Project Setup Hero` 應成為 body/Main Content Area 中的大主視覺，不是小 header。 |
| Yale Page Layout — https://usability.yale.edu/ux/plan/establish-structure-findability/page-layout-and-content-organization | 一致的 page templates 可降低 cognitive load；heading hierarchy 和可掃描的左對齊內容能幫助使用者快速定位。 | Gate、Hero、Project list、Task Workbench 的 heading 層級要固定；hero text 使用 left-aligned block，不用散落在 App Header 或空白區。 |
| Atlassian Designing Messages — https://atlassian.design/foundations/content/designing-messages/ | Empty state 可出現在 full-screen、panel、table 等容器；error、warning、feature discovery 等訊息要選對 component 和 tone。 | First-run no data 使用 setup hero；Project list failure 使用 error hero，兩者 heading/action 不混用。 |
| Material Layout — https://m2.material.io/design/layout/understanding-layout.html | App layout 應分出 app bar、navigation、body；文字內容應控制 line length，responsive layout 要讓內容區適應不同螢幕。 | S011 以 App Header / Side Navigation / Main Content Area 命名；hero body 限寬且短文案，mobile Project card 改單欄。 |
| Telerik Design Tokens — https://www.telerik.com/design-system/docs/foundation/guides/design-tokens/usage/ | Token 應依 intended purpose 使用；component-specific token 不應混用到其他 component。 | `appHeader.*`、`sideNavigation.*`、`taskDetailsDrawer.*` 是 current component/surface token；舊 `topbar.*` / `rail.*` / `detailDrawer.*` 只保留 deprecated reference。 |
| Claude Code memory — https://code.claude.com/docs/en/memory | `CLAUDE.md` 適合放 project instructions，需 concise；若 repo 已有 `AGENTS.md`，官方建議建立 `CLAUDE.md` import `AGENTS.md`，避免規則重複。 | 新增薄 `CLAUDE.md` 作 Claude Code 入口；完整前端設計索引放 `docs/grimo/design/README.md`，再由 `AGENTS.md` / `CLAUDE.md` 指過去。 |

Confidence classification：

| 決策 | Confidence | 理由 |
| --- | --- | --- |
| `Project Setup Hero` 放在 Main Content Area 裡，而不是 App Header | Validated | S010 已把 gate render 在 main content；S011 local implementation 已 rename 為 `main.main-content-area`；Carbon/Octopus empty-state guidance 支持 onboarding 佔用 body/content container。 |
| 抽出 `ProjectSetupHero` 作為 `ProjectSelectionGate` 內部區塊 | Validated | React/Vite stack 已在 `features/projects` 使用 feature-local component pattern；不需要新 framework。 |
| 同步 rename layout shell classes | Validated | `CONTEXT.md`、glossary、frontend design context 已決定正式詞；rename 會讓 code selectors 和文件語言一致。 |
| First-run 只保留一個 primary CTA | Validated | Carbon/Octopus empty-state guidance 和既有 S010 visual tests 都要求單一主要下一步。 |
| 最終 visual polish 只靠 Playwright snapshots 是否足夠 | Hypothesis | Deterministic snapshots 可驗 regression，但「是否更像 onboarding hero」仍需人工/browser review；本 spec 要保留 manual/Webwright optional evidence。 |
| 前端設計文件索引放 `CLAUDE.md` 是否足夠 | Validated with constraint | Claude Code 官方建議 `CLAUDE.md` concise 且可 import `AGENTS.md`；因此 `CLAUDE.md` 只放入口，真正索引放 `docs/grimo/design/README.md`，避免啟動檔過長和設計文件 drift。 |

Design Read（來自 `design-taste-frontend` / `minimalist-ui`）：

| 欄位 | S011 判讀 |
| --- | --- |
| Page kind | 本地開發工作台的 first-run / no-context setup surface，不是 landing page。 |
| Audience | 已熟悉 repo、CLI、agent workflow 的本機開發者。 |
| Vibe | Premium utilitarian minimalism：平面、淺色、左對齊、短文案、少裝飾、可掃描。 |
| DESIGN_VARIANCE / MOTION_INTENSITY / VISUAL_DENSITY | 5 / 2 / 4。這是產品工作台，不做 kinetic hero、行銷式 split media、AI-purple gradient 或 decorative card stack。 |
| S011 UI consequence | Hero 是狀態頁的資訊架構，不是品牌行銷；first-run 只有一個 primary CTA，existing Projects 以 Project cards 為主要行動，error state 用可恢復錯誤文案。 |

### 2.2 架構設計

採用「保留 gate、抽 hero」：

```text
App shell
├─ App Header
│  └─ 目前專案 / 尚未開啟 Project
└─ Main Content Area
   └─ ProjectSelectionGate
      ├─ ProjectSetupHero
      │  ├─ heading
      │  ├─ description
      │  └─ primary action
      └─ Project list（只有已有 Projects 且需要選擇時顯示）
```

命名規則：

| 名稱 | 概念 | 使用時機 | 不要拿來指稱 |
| --- | --- | --- | --- |
| `Project Selection Gate` | no-active-Project 狀態頁 | 整個 gate / section / route content | 不要拿來指稱內部 hero。 |
| `Project Setup Hero` | gate 裡的大主視覺建立引導 | first-run 或 no-context onboarding block | 不要拿來指稱 Project create form。 |
| `建立第一個 Project` | first-run heading 文案 | 沒有任何 Project 時的 hero heading | 不要當 component name。 |
| `選擇或建立 Project` | existing-project no-open-session heading 文案 | 有 Projects 但 session closed/stale/no-context 時 | 不要當 route name。 |

Implementation shape：

```tsx
type ProjectSetupHeroProps = {
  heading: string;
  copy: string;
  actionLabel: string;
  actionStyle: "primary" | "secondary";
  onAction: () => void;
};

function ProjectSetupHero(props: ProjectSetupHeroProps) {
  return <div className="project-setup-hero">...</div>;
}
```

`ProjectSetupHero` 可先放在 `ProjectSelectionGate.tsx` 同檔案內。這是單一 feature-local 區塊，還不到需要 shared component 的程度。

Layout shell class rename：

| 現有 class | S011 後 class | UI 名詞 | 說明 |
| --- | --- | --- | --- |
| `.topbar` | `.app-header` | App Header | 上方全域區域，含品牌、menu button、Project switcher。 |
| `.topbar-menu` | `.app-header-menu` | App Header menu button | 開關 Side Navigation 的 button。 |
| `.rail` | `.side-navigation` | Side Navigation | 左側可收合主要導覽。 |
| `.rail-control` / `.rail-pin` / `.rail-item` | `.side-navigation-control` / `.side-navigation-pin` / `.side-navigation-item` | Side Navigation controls/items | 導覽內部 controls 和 page view item。 |
| `.main-surface` | `.main-content-area` | Main Content Area | App Header 下方顯示目前 page view 的主要工作區。 |
| `.detail-pane` | `.task-details-pane` | Task Details Pane | selected Task 的右側詳情區。 |

S011 implementation 後，產品文件與新測試都不應再用 `.topbar`、`.rail`、`.main-surface`、`.detail-pane` 作為 current selectors。若需要過渡 alias，必須只留在同一 task 的 compatibility note，且不得成為新 acceptance selector。

Design token rename：

| 舊 token key | S011 後 token key | 用途 |
| --- | --- | --- |
| `topbar.height` | `appHeader.height` | App Header 固定高度。 |
| `rail.width` | `sideNavigation.width` | Side Navigation 展開寬度。 |
| `rail.control` | `sideNavigation.control` | Side Navigation control / icon button 尺寸。 |
| `detailDrawer.width` | `taskDetailsDrawer.width` | Task Details Drawer 寬度。 |

`docs/grimo/design/tokens.json` 必須使用正式詞作為 current token key；`frontend-design-context.md` 內既有 historical evidence 可以保留舊詞，因為它們是在記錄當時已驗證的 selector / snapshot 狀態，不是新實作 contract。

### 2.3 Screen Flow Contract

Flow Header:

| 欄位 | 內容 |
| --- | --- |
| Flow name | Project Setup Hero in Main Content Area |
| Persona | 本機開發者 |
| User goal | 第一次開啟或沒有 active Project 時，理解為什麼要建立/選擇 Project，並能從主工作區進入下一步。 |
| Entry point | App first load、`Close Project` 後、stale session、從 gated view 進入 `Task 管理 / 待處理 / Chat`。 |
| Success endpoint | 使用者建立或選擇 Project 後，看到目前 Project 的 Task Workbench。 |
| Out of scope | 不改 Project create form、不改 backend Project API、不加入 demo Task、不做 native folder picker。 |

State Matrix:

| State | Data condition | 使用者看到什麼 | Primary action | Forbidden behavior | Evidence |
| --- | --- | --- | --- | --- | --- |
| loading | `GET /api/projects` pending | App Header 顯示 `載入 Project context`；Main Content Area 不顯示假 Project 或假 Task。 | none | 不顯示 `grimo/web` 或 fixture task | Playwright UI assertion |
| first-run empty | `GET /api/projects` 回 `content=[]` | `Project Selection Gate` 內的 `Project Setup Hero`，heading `建立第一個 Project`，說明 Project 會讓 Task 工作台有真實 repo / codebase context。 | `建立 Project` | 不只顯示一條 header；不顯示 Task board / generic Chat / fake Project | Playwright visual + UI assertion |
| closed existing | 有 Projects，session `isClosed=true` | `Project Setup Hero` 說明需要選擇 Project；Project list card 是主要工作內容，`建立 Project` 降為次要 action。 | 選擇 Project card | 不自動選第一個 Project；不把 `建立 Project` 做成唯一主要行動 | Playwright UI assertion |
| stale session | 有 Projects，但 `lastActiveProjectId` 不存在 | Hero 或 gate copy 顯示 `上次開啟的 Project 已不存在或無法載入，請選擇 Project。`；Project list 可選。 | 選擇 Project card | 不偷偷切到另一個 Project | Playwright UI assertion |
| no-context gated view | 沒 active Project，view 是 `tasks / blockers / chat` | Main Content Area 顯示 Project Selection Gate / Project Setup Hero，不顯示該 view 的假內容。 | `建立 Project` 或選擇 Project | 不顯示 fixture tasks、attention cards、generic blank Chat | Playwright UI assertion |
| projects view | 沒 active Project，view 是 `projects` | Project list / create view 可用。 | `新增專案` 或 submit create form | 不用 Project Setup Hero 擋住建立 Project 的地方 | existing Project management tests |
| workflow view | 沒 active Project，view 是 `workflow` | Workflow reference 可用；App Header 仍顯示 `尚未開啟 Project`。 | none | 不暗示已有 Project context | Playwright UI assertion |
| error | Project list request failed | `Project Setup Hero` 顯示 heading `無法載入 Project context`，body 顯示錯誤或修復提示。 | `重試` | 不顯示 first-run heading；不把 fixture data 當成功 | S011 Playwright assertion + existing S010 failure regression |
| success | 使用者建立或選擇 Project 成功 | App Header 顯示 Project；Task Workbench 讀該 Project tasks。 | `新增 Task` | 不停在 stale hero；不保留上一 Project tasks | existing full-stack + S011 smoke assertion |

Flow Steps:

| Step | Outcome | Screen / surface | User action | System response | Next state | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 使用者知道需要先建立 Project | App first load / Main Content Area | open app | 顯示 `Project Setup Hero` | first-run empty | `project-startup.ui.spec.ts` |
| 2 | 使用者進入 Project create | `Project Setup Hero` | click `建立 Project` | view 切到 `projects` create mode | Project create | existing project management/full-stack tests |
| 3 | 使用者建立 Project 後進工作台 | Project create form | submit valid Project | 保存 session，App Header 顯示 Project，Main Content Area 顯示 Task Workbench | success | `project-startup.fullstack.spec.ts` |
| 4 | 使用者 close Project 後不看到假內容 | App Header Project Switcher | click `Close Project` | Main Content Area 回到 gate；Task/Chat/待處理被擋住 | closed existing | `project-startup.ui.spec.ts` |
| 5 | 使用者從 gated Chat 回到 Project setup | Side Navigation | select `Chat` without active Project | Main Content Area 仍顯示 gate/hero | no-context gated view | `project-startup.ui.spec.ts` |

Low-fidelity wireflow:

```text
First-run, no Projects
+---------------------------------------------------------------------+
| App Header                                                          |
| [Menu] [Logo] Grimo             目前專案 尚未開啟 Project             |
+---------------------------------------------------------------------+
| Main Content Area                                                   |
|                                                                     |
|   Project Selection Gate                                            |
|   ┌─────────────────────────────────────────────────────────────┐    |
|   │ Project Setup Hero                                          │    |
|   │                                                             │    |
|   │ 建立第一個 Project                                          │    |
|   │ Project 會讓 Task 工作台有真實 repo / codebase context。    │    |
|   │                                                             │    |
|   │ [建立 Project]                                             │    |
|   └─────────────────────────────────────────────────────────────┘    |
+---------------------------------------------------------------------+
        | click 建立 Project
        v
Projects view / create mode
+---------------------------------------------------------------------+
| 返回列表                                                           |
| 專案名稱 [________________]                                        |
| Project Path [選填 /Users/.../repo]                                |
| Workflow [Web 服務開發 v]                                          |
| [建立 Project]                                                     |
+---------------------------------------------------------------------+
        | success
        v
Task Workbench with current Project
```

Closed session with existing Projects:

```text
Project Selection Gate
├─ Project Setup Hero
│  ├─ heading: 選擇或建立 Project
│  ├─ body: Project 會決定 Task 的工作流、角色和品質基準。
│  └─ secondary action: 建立 Project
└─ Project list
   ├─ [grimoAPP]   .../github-samzhu/grimoAPP   Web 服務開發
   └─ [skills-hub] .../github-samzhu/skills-hub Web 服務開發
```

不是 final pixels，不是新 design system，不授權加入無關裝飾。真正實作時要沿用現有 light theme、App Header 52px row height、Project gate snapshots viewport set。

CTA/navigation rules：

- Primary action：first-run empty 只有 `建立 Project`；closed/stale existing Projects 以 Project card selection 為主要行動。
- Secondary actions：existing Projects 狀態下 `建立 Project` 可作 secondary；first-run 不放多個同級 primary CTA。
- Retry：error state 使用 `重試`。
- Success destination：建立或選擇 Project 成功後進 `Task 管理` / Task Workbench。
- No-context behavior：`tasks / blockers / chat` 顯示 gate；`projects / workflow` 不被 gate 擋住。
- Duplicate primary CTA check：同一 viewport 只允許一個 primary project-setup CTA；Project list cards 不和 `建立 Project` 同時做成同級 primary buttons。

Verification Mapping:

| Behavior | Required evidence |
| --- | --- |
| Project Setup Hero 出現在 Main Content Area | Playwright locator assertion：`.main-content-area .project-selection-gate .project-setup-hero` 可見。 |
| First-run hero 使用 heading/body/action | Playwright role/text assertion + visual snapshot at `1366x768`, `1440x900`, `390x844`, `820x1180`。 |
| No-context `tasks / blockers / chat` 被 gate 擋住 | Playwright UI assertions：不顯示 fixture tasks、attention cards、generic blank Chat。 |
| `projects / workflow` 例外可用 | Playwright UI assertion 或 existing tests 保持通過。 |
| Layout shell class rename 完成 | Playwright / DOM assertions 改用 `.app-header`、`.side-navigation`、`.main-content-area`、`.task-details-pane`；新 test 不使用舊 selector。 |
| Responsive layout | `npm --prefix frontend run test:visual` deterministic snapshots。 |
| Frontend design docs 可被 agent 發現 | `CLAUDE.md` import `AGENTS.md`；`AGENTS.md` 和 `CLAUDE.md` 都指向 `docs/grimo/design/README.md`；README 指向 S011 和核心 design docs。 |

### 2.4 做法比較

| 做法 | 採用 | 理由 |
| --- | --- | --- |
| A: 保留 `ProjectSelectionGate`，在內部新增 `ProjectSetupHero` | yes | 最小改動；對齊現有 S010 gate 行為；把狀態頁和主視覺區塊分層命名，未來可測 `.project-setup-hero`。 |
| B: 把 `Project Setup Hero` 做成獨立 route/page | no | 會和 `projects` view / create mode 混淆；使用者不是進新頁，而是在 no-context 狀態下被 Main Content Area 引導。 |
| C: 只改 CSS，沿用 `.section-head project-selection-head` | no | 不能留下明確 UI contract；未來 spec / tests 無法穩定指認「大主視覺區塊」。 |

### 2.5 Task 邊界提示

| Task 候選 | Class / file | 來源 | 正向情境 | 反向情境 | POC |
| --- | --- | --- | --- | --- | --- |
| T01 Layout shell class rename | `frontend/src/App.tsx`, `frontend/src/app/Navigation.tsx`, `frontend/src/features/task-detail/TaskDetail.tsx`, `frontend/src/styles.css`, `frontend/e2e/*` | S011 naming contract | DOM 使用 `.app-header` / `.side-navigation` / `.main-content-area` / `.task-details-pane` | 新測試不依賴 `.topbar` / `.rail` / `.main-surface` / `.detail-pane` | not required |
| T02 Project Setup Hero UI | `frontend/src/features/projects/ProjectSelectionGate.tsx`, `frontend/src/styles.css` | S011 Screen Flow Contract | first-run 顯示 `.project-setup-hero` + `建立第一個 Project` + `建立 Project` | 不顯示 Task board / fake Project | not required |
| T03 Project gate visual evidence | `frontend/e2e/project-startup.ui.spec.ts`, snapshots | QA strategy V4 | desktop/mobile/tablet snapshots 更新後穩定 | layout regression fail | not required |
| T04 Design docs sync | `docs/grimo/design/frontend-design-context.md`, `docs/grimo/glossary.md` | frontend-design-context | 名詞和實作 class 對齊 | 不再把 hero 稱為 generic empty state | not required |
| T05 Frontend design docs index | `docs/grimo/design/README.md`, `AGENTS.md`, `CLAUDE.md` | Claude Code memory docs + Grimo frontend workflow | Agent 能從啟動檔找到前端設計索引和 S011 | `CLAUDE.md` 不複製完整設計文件造成 drift | not required |

## 3. BDD Contract

驗證命令：

執行：`npm --prefix frontend run build && npm --prefix frontend run test:visual`
通過條件：所有帶 `S011` AC id 的 scenario 都有對應 Playwright evidence；visual snapshot 變更必須是 intentional baseline update 後再次通過。

AC 覆蓋矩陣：

| AC | 使用者結果 | Contract / 可觀察輸出 | Layer | State |
| --- | --- | --- | --- | --- |
| AC-S011-1 | 第一次打開 Grimo 時，使用者在主工作區看到建立 Project 的大主視覺引導。 | UI 有 `.main-content-area .project-selection-gate .project-setup-hero`，heading `建立第一個 Project`，button `建立 Project`。 | frontend | verified local |
| AC-S011-2 | 沒有 active Project 時，Task 管理、待處理、Chat 不會露出假內容。 | 選到 `tasks / blockers / chat` 時仍顯示 Project Selection Gate；沒有 `.task-card` fixture、attention cards 或 generic blank Chat。 | frontend | verified by S010 regression |
| AC-S011-3 | 已有 Projects 但沒有 open session 時，使用者可先選 Project，不被建立 CTA 搶走主要行動。 | `選擇或建立 Project` hero 可見；`.project-selection-card` 可點選；`建立 Project` 是 secondary action，且 project setup 畫面不得出現多個 `.primary-button` CTA。 | frontend | verified local |
| AC-S011-4 | 沒有 active Project 時，專案頁和 Workflow 頁仍可用。 | `projects` view 顯示 Project list/create；`workflow` view 顯示 workflow reference，App Header 仍是 `尚未開啟 Project`。 | frontend | verified local |
| AC-S011-5 | Project Setup Hero 在 desktop、tablet、mobile 都是穩定 Main Content Area layout。 | Visual snapshots 覆蓋 `1366x768`, `1440x900`, `390x844`, `820x1180`。 | frontend | verified local |
| AC-S011-6 | 文件和程式碼使用同一套 layout shell 名詞，後續測試不再靠舊 class selector。 | DOM 有 `.app-header`、`.side-navigation`、`.main-content-area`、`.task-details-pane`；design tokens 使用 `appHeader.*` / `sideNavigation.*` / `taskDetailsDrawer.*`；S011 tests 不使用 `.topbar`、`.rail`、`.main-surface`、`.detail-pane`。 | frontend | verified local |
| AC-S011-7 | Project list 載入失敗時，使用者知道是 context 載入錯誤，不會誤以為只是第一次使用。 | Error hero 顯示 heading `無法載入 Project context`、body 包含錯誤文字、button `重試`；不顯示 first-run heading `建立第一個 Project`。 | frontend | verified local |
| AC-S011-8 | 下一個 agent 做前端設計時，可以從 repo 入口找到正確設計文件，不靠聊天紀錄。 | `CLAUDE.md` 匯入 `AGENTS.md` 並指向 `docs/grimo/design/README.md`；`AGENTS.md` 的 workflow artifacts 列出 frontend design docs；README 指向 S011、screen flow、tokens、Webwright prompts。 | docs | verified local |

Feature: Project Setup Hero in Main Content Area

### Rule: First-run Project setup uses the Main Content Area

使用者結果：
第一次打開 Grimo 且沒有任何 Project 時，使用者在主工作區看到完整的建立引導，不會以為畫面壞掉或只是一條 header 後面空白。

Contract：

```text
main.main-content-area
└─ section.project-selection-gate
   └─ div.project-setup-hero
      ├─ h1: 建立第一個 Project
      ├─ p: Project 會讓 Task 工作台有真實 repo / codebase context。
      └─ button: 建立 Project
```

```gherkin
@spec:S011
@ac:AC-S011-1
@layer:frontend
@state:verified-local
Scenario: 第一次打開 Grimo 時在主工作區看到 Project Setup Hero
  Given（前提）GET /api/projects 回傳 content=[]
  When（動作）使用者打開 app
  Then（結果）Main Content Area 顯示 Project Selection Gate
  And（而且）Gate 內有 Project Setup Hero
  And（而且）Hero 顯示 heading "建立第一個 Project"
  And（而且）Hero 顯示 button "建立 Project"
  And（而且）畫面不顯示 fixture Task 或 fake Project path
  # 技術證據：Playwright locator `.main-content-area .project-selection-gate .project-setup-hero`，role heading/button assertions
```

驗證綁定：

- frontend: `frontend/e2e/project-startup.ui.spec.ts`
- command: `npm --prefix frontend run test:visual`

### Rule: Project context gate protects Task, attention, and Chat surfaces

使用者結果：
沒有 active Project 時，使用者不會看到看似可用但其實沒有 repo/codebase context 的 Task board、待處理 queue 或 Chat。

Contract：

| View | Expected no-context output | Forbidden output |
| --- | --- | --- |
| `tasks` | Project Selection Gate + Project Setup Hero | `.task-card`, `任務工作台` board content |
| `blockers` | Project Selection Gate + Project Setup Hero | `.attention-task`, `待處理` queue content |
| `chat` | Project Selection Gate + Project Setup Hero | `.assistant-thread`, generic blank Chat |

```gherkin
@spec:S011
@ac:AC-S011-2
@layer:frontend
@state:verified-by-S010-regression
Scenario: 沒有 active Project 時需要 Project context 的頁面都回到 gate
  Given（前提）使用者已 Close Project
  When（動作）使用者依序切到 Task 管理、待處理、Chat
  Then（結果）每個 view 的 Main Content Area 都顯示 Project Selection Gate
  And（而且）Task 管理不顯示 fixture task cards
  And（而且）待處理不顯示 attention cards
  And（而且）Chat 不顯示 generic blank Chat composer
  # 技術證據：Playwright menu navigation + negative locators
```

驗證綁定：

- frontend: `frontend/e2e/project-startup.ui.spec.ts`
- command: `npm --prefix frontend run test:visual`

### Rule: Existing Projects make selection the primary path

使用者結果：
如果使用者已經有 Projects，只是上次按過 Close Project 或 session 找不到，畫面應鼓勵「選一個 Project 繼續」，而不是把「建立 Project」做成唯一主路徑。

Contract：

```text
Project Selection Gate
├─ Project Setup Hero
│  ├─ heading: 選擇或建立 Project
│  ├─ body: Project 會決定 Task 的工作流、角色和品質基準。
│  └─ action: 建立 Project（secondary / lower emphasis）
└─ Project list
   ├─ button: grimoAPP
   └─ button: skills-hub
```

```gherkin
@spec:S011
@ac:AC-S011-3
@layer:frontend
@state:verified-local
Scenario: 已有 Projects 但沒有 open session 時優先讓使用者選 Project
  Given（前提）GET /api/projects 回傳 grimoAPP 和 skills-hub
  And（而且）localStorage session isClosed=true
  When（動作）使用者打開 app
  Then（結果）Main Content Area 顯示 heading "選擇或建立 Project"
  And（而且）Project list 顯示 grimoAPP 和 skills-hub
  And（而且）使用者可以點 grimoAPP 進入該 Project 的 Task Workbench
  And（而且）Project card selection 是主要行動，"建立 Project" 是 secondary action
  And（而且）project setup 畫面不出現多個 ".primary-button" CTA
  And（而且）系統不自動選第一個 Project
  # 技術證據：Playwright assertions for `.project-selection-card`, `.project-setup-hero .icon-text-button`, primary-button count, and task API request URL contains selected project id
```

驗證綁定：

- frontend: `frontend/e2e/project-startup.ui.spec.ts`
- command: `npm --prefix frontend run test:visual`

### Rule: Projects and Workflow remain available without active Project

使用者結果：
沒有 active Project 時，使用者仍能進專案頁建立 Project，也能看 Workflow reference；但產品不能暗示已經有 Project context。

Contract：

| View | No active Project output |
| --- | --- |
| `projects` | Project list/create view 可用 |
| `workflow` | Workflow reference 可用，App Header 顯示 `尚未開啟 Project` |

```gherkin
@spec:S011
@ac:AC-S011-4
@layer:frontend
@state:verified-local
Scenario: 沒有 active Project 時專案與 Workflow 是可用例外
  Given（前提）目前沒有 active Project
  When（動作）使用者從 menu 進入專案
  Then（結果）使用者看到 Project list 或 create view
  When（動作）使用者從 menu 進入 Workflow
  Then（結果）使用者看到 Workflow reference
  And（而且）App Header 仍顯示 "尚未開啟 Project"
  # 技術證據：Playwright view navigation assertions
```

驗證綁定：

- frontend: `frontend/e2e/project-startup.ui.spec.ts`
- command: `npm --prefix frontend run test:visual`

### Rule: Project list failure uses an error hero

使用者結果：
如果 Project list 載入失敗，使用者看到的是可重試的錯誤狀態，不會看到 first-run 建立引導而誤判成沒有 Project。

Contract：

```text
main.main-content-area
└─ section.project-selection-gate
   └─ div.project-setup-hero
      ├─ h1: 無法載入 Project context
      ├─ p: Project 載入失敗（或實際錯誤訊息）
      └─ button: 重試
```

```gherkin
@spec:S011
@ac:AC-S011-7
@layer:frontend
@state:verified-local
Scenario: Project list 載入失敗時顯示 error hero
  Given（前提）GET /api/projects 回傳錯誤
  When（動作）使用者打開 app
  Then（結果）Main Content Area 顯示 Project Setup Hero
  And（而且）Hero heading 是 "無法載入 Project context"
  And（而且）Hero 顯示錯誤文字和 button "重試"
  And（而且）Hero 不顯示 first-run heading "建立第一個 Project"
  # 技術證據：Playwright role/text assertions
```

驗證綁定：

- frontend: `frontend/e2e/project-startup.ui.spec.ts`
- command: `npm --prefix frontend run test:visual`

### Rule: Project Setup Hero has deterministic responsive evidence

使用者結果：
Project setup onboarding 在桌面、平板、手機都不會變回小 header 或文字擠壓。

Contract：

Required viewport evidence:

| Viewport | Snapshot expectation |
| --- | --- |
| `1366x768` | Hero 在 Main Content Area 內清楚可見，CTA 不被 App Header 或 Side Navigation 遮住。 |
| `1440x900` | Hero 不貼頂，不像薄 header；Main Content Area 空間有明確引導焦點。 |
| `390x844` | Hero 內容垂直堆疊，heading/body/button 不溢出。 |
| `820x1180` | Hero 和 Project list 不重疊，Project card 可掃描。 |

```gherkin
@spec:S011
@ac:AC-S011-5
@layer:frontend
@state:verified-local
Scenario: Project Setup Hero 在四個 viewport 都有穩定截圖證據
  Given（前提）使用者處於 first-run empty 或 closed existing Project 狀態
  When（動作）Playwright 以 1366x768, 1440x900, 390x844, 820x1180 開啟 app
  Then（結果）每個 viewport 的 Project setup 畫面都符合 snapshot baseline
  And（而且）文字不溢出、不互相覆蓋、不被 Side Navigation / App Header 遮住
  # 技術證據：`toHaveScreenshot(project-selection-gate-*.png)`
```

驗證綁定：

- frontend: `frontend/e2e/project-startup.ui.spec.ts`
- command: `npm --prefix frontend run test:visual`

### Rule: Layout shell class names match the canonical design terms

使用者結果：
畫面看起來不變，但設計文件、程式碼和測試用同一套名字溝通區塊；之後討論 App Header、Side Navigation、Main Content Area 或 Task Details Pane 時，不需要再翻譯舊 class 名。

Contract：

| UI 名詞 | Required selector | Deprecated selector |
| --- | --- | --- |
| App Header | `.app-header` | `.topbar` |
| App Header menu button | `.app-header-menu` | `.topbar-menu` |
| Side Navigation | `.side-navigation` | `.rail` |
| Main Content Area | `.main-content-area` | `.main-surface` |
| Task Details Pane | `.task-details-pane` | `.detail-pane` |

| Current token key | Deprecated token key |
| --- | --- |
| `appHeader.height` | `topbar.height` |
| `sideNavigation.width` | `rail.width` |
| `sideNavigation.control` | `rail.control` |
| `taskDetailsDrawer.width` | `detailDrawer.width` |

```gherkin
@spec:S011
@ac:AC-S011-6
@layer:frontend
@state:verified-local
Scenario: Layout shell DOM classes use canonical design terms
  Given（前提）使用者打開 app
  When（動作）Playwright 檢查 app shell DOM
  Then（結果）App Header 使用 selector ".app-header"
  And（而且）Side Navigation 使用 selector ".side-navigation"
  And（而且）Main Content Area 使用 selector ".main-content-area"
  When（動作）使用者選取一個 Task 並打開詳情
  Then（結果）Task Details Pane 使用 selector ".task-details-pane"
  And（而且）design tokens 使用 "appHeader.height", "sideNavigation.width", "sideNavigation.control", "taskDetailsDrawer.width"
  And（而且）S011 新增或修改的測試不使用 ".topbar", ".rail", ".main-surface", ".detail-pane" 作為 acceptance selector
  # 技術證據：Playwright locator assertions + rg selector audit + tokens.json key audit
```

驗證綁定：

- frontend: `frontend/e2e/project-startup.ui.spec.ts`, `frontend/e2e/task-workbench.visual.spec.ts`
- command: `npm --prefix frontend run build && npm --prefix frontend run test:visual`
- selector/token audit: `rg -n "topbar|rail|main-surface|detail-pane|detailDrawer" frontend/src frontend/e2e docs/grimo/design/tokens.json`

### Rule: Frontend design docs are discoverable from agent entry files

使用者結果：
下一個 agent 或開發者要改前端設計時，不需要翻聊天紀錄，也不會只讀到 `AGENTS.md` / `CLAUDE.md` 就漏掉 design docs。

Contract：

| File | Required content |
| --- | --- |
| `CLAUDE.md` | 匯入 `AGENTS.md`，並指向 `docs/grimo/design/README.md`。 |
| `AGENTS.md` | `Workflow Artifacts` 列出 frontend design index、context、screen flow、tokens、Webwright prompts。 |
| `docs/grimo/design/README.md` | 提供 PRD -> frontend-design-context -> screen-flow-contract -> active spec -> tokens -> prompts 的讀取順序。 |

```gherkin
@spec:S011
@ac:AC-S011-8
@layer:docs
@state:verified-local
Scenario: Agent 可以從 repo 入口找到前端設計文件
  Given（前提）下一個 agent 從 repo root 開始工作
  When（動作）agent 讀取 CLAUDE.md 或 AGENTS.md
  Then（結果）它能找到 docs/grimo/design/README.md
  And（而且）design README 指向 S011、frontend design context、screen flow contract、tokens 和 Webwright prompts
  And（而且）CLAUDE.md 不複製完整設計文件內容
  # 技術證據：rg 檢查 CLAUDE.md、AGENTS.md、docs/grimo/design/README.md 的 path references
```

驗證綁定：

- docs: `CLAUDE.md`, `AGENTS.md`, `docs/grimo/design/README.md`
- command: `rg -n "docs/grimo/design/README.md|S011|frontend-design-context|screen-flow-contract|tokens.json|webwright-prompts" CLAUDE.md AGENTS.md docs/grimo/design/README.md`

### 非功能需求檢查

| 分類 | 對應驗收 | 說明 |
| --- | --- | --- |
| Performance | AC-S011-5 | UI-only CSS/React change，不新增 network request；visual test 證明 layout 穩定。 |
| Security | N/A | 不改 auth、API、storage 或使用者輸入驗證。 |
| Reliability | AC-S011-2, AC-S011-3 | 沒有 active Project 時不顯示假資料；closed/stale session 不自動選 Project。 |
| Usability | AC-S011-1, AC-S011-3, AC-S011-5 | 使用者看到主工作區大主視覺引導，且既有 Project 時優先選 Project。 |
| Maintainability | AC-S011-1, AC-S011-6, AC-S011-8 | `Project Setup Hero`、layout shell 和 frontend design docs index 都有明確入口，未來 spec/test 不需用 copy 或聊天紀錄推測區塊。 |

## 4. 介面與 API 設計

Backend/API：N/A — S011 不新增或修改 backend API、DTO、DB row 或 storage schema。

Frontend UI contract：

| Contract | Type | Rule | Source | 設計理由 | BDD 要驗什麼 |
| --- | --- | --- | --- | --- | --- |
| `.project-selection-gate` | CSS/DOM class | 包住 no-active-Project 狀態頁 | existing S010 component | 保留 gate 層級，避免把狀態頁和 hero 混名 | gate 在 `main.main-content-area` 裡 |
| `.project-setup-hero` | CSS/DOM class | 包住 hero heading/body/action | S011 new UI contract | 讓大主視覺區塊可被測試和設計文件指認 | first-run 時 selector 可見 |
| `.app-header` | CSS/DOM class | 包住 App Header | S011 rename | 讓 code 對齊正式 UI shell 名詞 | App Header selector 可見 |
| `.app-header-menu` | CSS/DOM class | App Header 內的 Side Navigation toggle | S011 rename | 取代 `.topbar-menu` 舊名 | menu button label 保持 `展開主選單` / `收合主選單` |
| `.side-navigation` | CSS/DOM class | 包住主要 page view 導覽 | S011 rename | 取代 `.rail` 舊名，避免和 icon-only rail 混淆 | Side Navigation selector 可見 |
| `.main-content-area` | CSS/DOM class | 包住目前 page view 的主要內容 | S011 rename | 取代 `.main-surface` 舊名 | Project gate 在 `.main-content-area` 內 |
| `.task-details-pane` | CSS/DOM class | 包住 selected Task 詳情 pane | S011 rename | 取代 `.detail-pane` 舊名 | 打開 Task detail 後 selector 可見 |
| `ProjectSetupHeroProps.heading` | string | 使用者可見 heading | `ProjectSelectionGate` state | 文案 variant 不等於 component name | first-run heading 是 `建立第一個 Project` |
| `ProjectSetupHeroProps.copy` | string | 使用者可見 body copy | existing S010 copy | 說明 Project context 的目的 | body copy 可見且不溢出 |
| `ProjectSetupHeroProps.actionLabel` | string | primary/secondary action label | current `onCreateProject` | 行動入口不靠外部猜測 | button label 可見 |
| `ProjectSetupHeroProps.actionVariant` | `"primary" | "secondary"` | first-run 使用 primary；existing Projects 使用 secondary | S011 CTA rules | 防止既有 Project 狀態下建立 CTA 搶走 Project card selection | Playwright 驗 `建立 Project` 不使用 `.primary-button` |
| `appHeader.height` | design token key | App Header 固定高度 | S011 token rename | token key 對齊正式 UI 名詞 | `tokens.json` 不再使用 `topbar.height` |
| `sideNavigation.width` / `sideNavigation.control` | design token keys | Side Navigation 展開寬度與 control 尺寸 | S011 token rename | token key 對齊正式 UI 名詞 | `tokens.json` 不再使用 `rail.width` / `rail.control` |
| `taskDetailsDrawer.width` | design token key | Task Details Drawer 寬度 | S011 token rename | token key 對齊正式 UI 名詞 | `tokens.json` 不再使用 `detailDrawer.width` |

建議 component shape：

```tsx
type ProjectSetupHeroProps = {
  heading: string;
  copy: string;
  actionLabel: string;
  actionVariant: "primary" | "secondary";
  onAction: () => void;
};
```

Storage：N/A — 不新增或修改 DB table。

## 5. 檔案規劃

| 檔案 | 動作 | 說明 |
| --- | --- | --- |
| `docs/grimo/specs/spec-roadmap.md` | modify | 更新 S011 Active row，狀態 `🧪 local verified`。 |
| `docs/grimo/specs/2026-06-07-S011-project-setup-hero-main-content-area.md` | new | 本 spec sections 1-5。 |
| `CLAUDE.md` | new | Claude Code 入口：import `AGENTS.md`，並指向 frontend design docs index；不複製完整設計文件。 |
| `AGENTS.md` | modify | Workflow Artifacts 加入 frontend design docs index/context/flow/tokens/prompts。 |
| `docs/grimo/design/README.md` | new | 前端設計文件索引，提供 PRD -> design context -> screen flow -> active spec -> tokens -> prompts 的讀取順序。 |
| `docs/grimo/glossary.md` | modify | 保留/確認 `Project Selection Gate`、`Project Setup Hero` 詞條。 |
| `docs/grimo/design/frontend-design-context.md` | modify | 保留/確認 App Header、Side Navigation、Main Content Area、gate、hero 分層與示意圖。 |
| `docs/grimo/design/screen-flow-contract.md` | modify | 保留/確認 first-run example 使用 `Project Setup Hero`。 |
| `docs/grimo/design/tokens.json` | modify | 把 current token key 從 `topbar.*` / `rail.*` / `detailDrawer.*` rename 成 `appHeader.*` / `sideNavigation.*` / `taskDetailsDrawer.*`；歷史 evidence 文字不硬改。 |
| `frontend/src/App.tsx` | modify | 已把 `.topbar` / `.topbar-menu` / `.main-surface` rename 為 `.app-header` / `.app-header-menu` / `.main-content-area`。 |
| `frontend/src/app/Navigation.tsx` | modify | 已把 `.rail*` rename 為 `.side-navigation*`。 |
| `frontend/src/features/task-detail/TaskDetail.tsx` | modify | 已把 `.detail-pane` rename 為 `.task-details-pane`。 |
| `frontend/src/features/projects/ProjectSelectionGate.tsx` | modify | 已加入 `ProjectSetupHero` 內部 component/markup。 |
| `frontend/src/styles.css` | modify | 已同步 layout shell selector rename，新增 `.project-setup-hero` layout，使 first-run 使用主工作區大主視覺；mobile Project card 改單欄。 |
| `frontend/e2e/project-startup.ui.spec.ts` | modify | 已新增 `AC-S011-*` selectors/assertions，改用 canonical class selectors，更新 intentional snapshots；保留 S010 tests 作為 shipped startup regression。 |
| `frontend/e2e/task-workbench.visual.spec.ts` | modify | 已把 Task Details Pane 相關 assertions 改用 `.task-details-pane`。 |

---

## 6. Task Plan

POC：not required。S011 只修改既有 React/CSS/Playwright/docs surfaces，不新增 package、SDK、backend API、DB schema 或陌生 framework SPI；做法已由 S010 startup flow、現有 Playwright suite 和官方設計文件交叉驗證。

BDD layer split：

| Layer | Task | 主要 AC | 測試檔 / evidence | 驗證命令 | Status |
| --- | --- | --- | --- | --- | --- |
| Frontend BDD | `S011-T01 Shell selector and token rename` | AC-S011-6 | `frontend/e2e/project-startup.ui.spec.ts`, `frontend/e2e/task-workbench.visual.spec.ts`, `docs/grimo/design/tokens.json` | `npm --prefix frontend run test:visual` + selector/token audit | PASS |
| Frontend BDD | `S011-T02 Project Setup Hero UI` | AC-S011-1, AC-S011-3, AC-S011-7 | `frontend/e2e/project-startup.ui.spec.ts` | `npm --prefix frontend run test:visual` | PASS |
| Browser E2E / Visual | `S011-T03 Navigation and visual evidence` | AC-S011-2, AC-S011-4, AC-S011-5 | Project startup Playwright tests + 4 snapshots | `npx playwright test project-startup.ui.spec.ts --update-snapshots=changed` then `npm --prefix frontend run test:visual` | PASS |
| Docs BDD | `S011-T04 Frontend design docs index` | AC-S011-8 | `CLAUDE.md`, `AGENTS.md`, `docs/grimo/design/README.md` | docs discoverability `rg` | PASS |
| QA / Release | `S011-T05 QA and release evidence` | All ACs | release gate, full-stack E2E, Chrome smoke, spec §7 | `scripts/verify-release.sh`, `npm --prefix frontend run test:fullstack`, Chrome manual smoke | PASS |

Temporary task files created by `/planning-tasks`:

- `docs/grimo/tasks/2026-06-07-S011-T01-shell-selector-token-rename.md`
- `docs/grimo/tasks/2026-06-07-S011-T02-project-setup-hero.md`
- `docs/grimo/tasks/2026-06-07-S011-T03-navigation-and-visual-evidence.md`
- `docs/grimo/tasks/2026-06-07-S011-T04-design-docs-index.md`
- `docs/grimo/tasks/2026-06-07-S011-T05-qa-release-evidence.md`

Implementation note：這些 task files 是在 S011 consistency implementation 已存在於 worktree 後 formalized。沒有偽造 Red phase；每個 task 的 PASS 以可重跑 GREEN evidence、selector/token audit、visual snapshots 和 release gate 作為驗證。

### POC Findings

- Existing stack covers S011：React feature-local component、native CSS、Playwright UI/visual tests、repo docs index 足以完成需求。
- No dependency added：不新增 frontend UI library、animation library、routing library 或 backend dependency。
- Integration seam：browser/UI seam exists，使用 Playwright visual/full-stack 和 Chrome manual smoke 驗證；backend/API seam unchanged。

### Task Results

| Task | Result | Evidence |
| --- | --- | --- |
| S011-T01 | PASS | `AC-S011-6` tests and selector/token audit pass. |
| S011-T02 | PASS | `AC-S011-1`, `AC-S011-3`, `AC-S011-7` tests pass. |
| S011-T03 | PASS | Project startup targeted test 21/21 pass; 4 Project selection gate snapshots intentionally updated; full visual gate 40/40 pass. |
| S011-T04 | PASS | `CLAUDE.md` / `AGENTS.md` / `docs/grimo/design/README.md` discoverability grep pass. |
| S011-T05 | PASS | Automated release / full-stack evidence recorded in §7; Chrome manual smoke completed on Chrome Profile 1. |

## 7. Implementation Results

### Local Release Verification

| Command | Result | Evidence |
| --- | --- | --- |
| `npm --prefix frontend run build` | PASS | TypeScript + Vite production build completed. |
| `npx playwright test project-startup.ui.spec.ts --update-snapshots=changed` | PASS | 21/21 Project startup tests passed; 4 Project selection gate baselines regenerated intentionally. |
| `npm --prefix frontend run test:visual` | PASS | 40/40 Playwright visual/UI tests passed, including `AC-S011-1`, `AC-S011-3`, `AC-S011-4`, `AC-S011-6`, `AC-S011-7`. |
| `python3 scripts/visual-snapshot-summary.py --repo-root .` | PASS | Changed snapshots = 4, all Project selection gate baselines. |
| `scripts/verify-release.sh` | PASS | Verdict: `PASS: frontend build, deterministic visual regression including S010 Project startup/switcher evidence, backend tests including S004 TaskApiTests and S009 workflow evidence tests, and S001/S002/S003/S004/S010 full-stack Project onboarding, startup, and Task creation completed.` |
| `./gradlew test --rerun-tasks` in `backend/` | PASS | 4 Gradle tasks executed; XML summary `tests=42 failures=0 errors=0 skipped=0`. |
| `npm --prefix frontend run test:fullstack` | PASS | 7/7 full-stack Playwright tests passed. |
| Chrome manual smoke via `@chrome` | PASS | Chrome Profile 1 with Codex Chrome Extension opened `http://127.0.0.1:5174/` against isolated backend home. Verified first-run hero, Project creation to Task Workbench, Close Project to existing Project gate, Workflow available without active Project, and Project card selection back to Task Workbench. |

### AC Verification Matrix

| AC | Status | Evidence |
| --- | --- | --- |
| AC-S011-1 | VERIFIED | `frontend/e2e/project-startup.ui.spec.ts` checks `.main-content-area .project-selection-gate .project-setup-hero`, `建立第一個 Project`, and `建立 Project`; visual gate PASS. |
| AC-S011-2 | VERIFIED | S010 regression `AC-S010-6` still gates Task/attention/Chat without fixture content; visual gate PASS. |
| AC-S011-3 | VERIFIED | `AC-S011-3` checks Project cards as primary path, secondary `建立 Project`, and selected Project task API request. |
| AC-S011-4 | VERIFIED | `AC-S011-4` checks `專案` and `Workflow` remain available without active Project. |
| AC-S011-5 | VERIFIED | Project selection gate snapshots updated and passed at `1366x768`, `1440x900`, `390x844`, `820x1180`. |
| AC-S011-6 | VERIFIED | App shell and Task Details Pane tests use `.app-header`, `.side-navigation`, `.main-content-area`, `.task-details-pane`; selector/token audit has no old current names. |
| AC-S011-7 | VERIFIED | `AC-S011-7` checks error hero `無法載入 Project context`, error text, `重試`, and no first-run heading. |
| AC-S011-8 | VERIFIED | Docs discoverability grep proves `CLAUDE.md` / `AGENTS.md` / `docs/grimo/design/README.md` link to frontend design docs and S011. |

### QA Review

| Layer | Result | Detail |
| --- | --- | --- |
| Automated tests | PASS | `scripts/verify-release.sh`, backend `./gradlew test --rerun-tasks`, frontend build, visual, and full-stack commands passed. |
| Coverage / Integration | PASS | Backend tests executed 42 tests; full-stack browser path executed 7 tests against real backend/Vite wiring. |
| Manual verification | PASS | Chrome smoke completed through the user-visible app in Chrome Profile 1. |
| Testability gate | CLEAR | All ACs have automated or docs grep evidence; Chrome is manual additional evidence, not a missing test infrastructure. |
| Generality gate | CLEAR | S011 behavior derives from Project API state (`content=[]`, existing Projects, request failure, session state), not hardcoded production branches for fixture names. |
| Design sync | PASS | `frontend-design-context.md`, `screen-flow-contract.md`, `ui-ux-workflow.md`, `tokens.json`, `AGENTS.md`, `CLAUDE.md`, and design README match current selector/token terms. |

### Chrome Manual Smoke Evidence

Environment:

- Chrome Profile: `Profile 1` with Codex Chrome Extension enabled.
- Backend: `JAVA_TOOL_OPTIONS=-Duser.home=/Users/samzhu/workspace/github-samzhu/grimoAPP/temp/grimo-chrome-s011-home ./gradlew bootRun`
- Frontend: `npm --prefix frontend run dev -- --port 5174 --strictPort`
- URL: `http://127.0.0.1:5174/`

Checked flow:

1. First load showed App Header plus `Project Setup Hero` with heading `建立第一個 Project`, body `Project 會讓 Task 工作台有真實 repo / codebase context。`, and button `建立 Project`.
2. Clicking `建立 Project`, filling project name `S011 Chrome Smoke`, and submitting `建立專案` created a real backend Project and opened `任務工作台`.
3. Project Switcher showed current Project `S011 Chrome Smoke`; clicking `Close Project` returned to existing Project gate.
4. Existing Project gate showed heading `選擇或建立 Project`, one Project card, App Header `尚未開啟 Project`, and no primary button inside `.project-setup-hero`.
5. Side Navigation `Workflow` opened `Workflow 設計` while App Header still showed no active Project and `.project-selection-gate` was absent.
6. Returning to `Task 管理` showed the existing Project gate; clicking the Project card reopened `任務工作台` with current Project `S011 Chrome Smoke`.

### Final Size Re-score

| Dimension | Initial | Actual | Rationale |
| --- | --- | --- | --- |
| Tech risk | 2 | 2 | Existing React/CSS/Playwright stack covered the change; no new dependency. |
| Uncertainty | 2 | 2 | External design research clarified placement and docs-index choice; no design pivot after implementation. |
| Dependencies | 2 | 2 | S010/S003 dependencies remained stable; backend/API unchanged. |
| Scope | 3 | 4 | Scope expanded from hero layout to selector/token rename, docs index, snapshots, and Chrome smoke evidence. |
| Testing | 3 | 4 | Required visual snapshots, full-stack regression, backend rerun, release gate, and manual Chrome smoke. |
| Reversibility | 1 | 1 | Selector/token rename and docs/index changes are reversible without schema/data migration. |
| **Total** | **13 / M** | **15 / L** | Bucket shifted M -> L due to broader documentation/index and verification scope. |

### Release Verdict

`PASS` — local automated QA and user-requested Chrome manual smoke are complete. S011 is ready for shipping-release.

### Shipping Gate

`scripts/verify-release.sh` was re-run after archive / changelog / roadmap updates and passed with the same release verdict before commit.
