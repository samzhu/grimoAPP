# Grimo Screen Flow Contract

## Purpose

Screen Flow Contract 是 Grimo 前端頁面設計的流程合約。它放在 `docs/grimo/PRD.md` 和 low-fidelity wireframe 之間，用來先固定使用者入口、頁面狀態、跳轉、空狀態、失敗狀態和驗收證據，避免畫面做完才發現流程互相打架。

適用時機：

- 新增或改動 frontend page、modal、drawer、navigation、onboarding、empty state、error state。
- 一個頁面會因資料狀態顯示不同內容，例如沒有 Project、有 Project、loading、error、empty list。
- 一個 action 會跨頁或跨 surface，例如 `建立專案` 成功後進入 Task Workbench。
- 使用者回饋「一開始應該看到什麼」「這頁流程怪怪的」「按完應該去哪」。

不適用時機：

- 純 CSS token 調整，而且不改畫面資訊架構或互動。
- 純 backend/API change，沒有任何 user-visible flow。
- 已有 spec 的 task 實作階段，除非 task 發現原 flow contract 有缺口。

## Research Basis

| 來源 | 查到什麼 | Grimo 採用方式 |
| --- | --- | --- |
| Figma User Flow — https://www.figma.com/resource-library/user-flow/ | User flow 要定義 user goal、entry point、steps、decision points、endpoint；一個 flow 聚焦一個明確任務。 | 每份 Screen Flow Contract 必須先寫使用者目標、入口、成功終點和 decision points。 |
| Figma Wireframing — https://www.figma.com/resource-library/what-is-wireframing/ | Wireframe 用來對齊 screen layout、navigation、interactive elements，讓討論聚焦功能和流程，不被視覺細節帶歪。 | 先寫 flow contract，再畫 low-fidelity wireflow；wireflow 不處理 final pixels。 |
| Atlassian Customer Journey Mapping — https://www.atlassian.com/team-playbook/plays/customer-journey-mapping | Journey map 應限定 single persona、single scenario、single goal，否則太泛會漏掉真正問題。 | Grimo flow contract 一次只處理一個 persona/scenario/goal；跨角色或跨大流程要拆多份。 |
| GitLab Empty States — https://design.gitlab.com/patterns/empty-states/ | Empty state 應提供下一步，且同一 context 盡量只有一個 primary action。 | Grimo empty state 必須寫出使用者下一步；不能同一畫面放多個同級 primary CTA。 |
| Carbon Empty States — https://carbondesignsystem.com/patterns/empty-states-pattern/ | Empty state 要放在原本會顯示資料的位置；多個 empty state 同時出現時要避免多個 primary action。 | Grimo 不用假資料填空；沒有資料的位置直接顯示對應 empty state。 |
| Material Empty States — https://m1.material.io/patterns/empty-states.html | Empty state 需要避免使用者困惑；教育內容要簡短，starter content 必須可刪除替換。 | Grimo first-run 不用不可刪的假 Task；測試 fixture 不可變成使用者真狀態。 |

## Required Contract Shape

每個會改頁面流程的 spec，在 `## 2. 研究與設計` 或 UI subsection 中至少要包含以下內容。

### 1. Flow Header

| 欄位 | 說明 |
| --- | --- |
| Flow name | 使用者看得懂的流程名稱，例如 `First-run Project setup`。 |
| Persona | 這個流程服務誰；MVP 預設是本機開發者。 |
| User goal | 使用者想完成的結果，不是技術任務。 |
| Entry point | 使用者從哪裡進來，例如 app first load、sidebar `專案`、deep link。 |
| Success endpoint | 成功後停在哪個 screen/state，例如 `Task Workbench with current Project`。 |
| Out of scope | 明確不處理的流程，例如 production packaging、native folder picker。 |

### 2. State Matrix

每個 page/surface 都要列出資料狀態和畫面結果。

| State | Data condition | 使用者看到什麼 | Primary action | Forbidden behavior | Evidence |
| --- | --- | --- | --- | --- | --- |
| loading | request pending | 載入文字或 skeleton | none | 不顯示假資料 | Playwright assertion |
| empty | `content=[]` | 空狀態文案 + 下一步 | one primary CTA | 不顯示 fixture card | screenshot / test |
| ready | records exist | 真資料列表或工作台 | context-specific | 不預選不該預選的 item | E2E |
| error | request failed | 可理解錯誤與 retry/recovery | retry or navigate | 不清掉已存在資料 | E2E |
| success | submit succeeds | success feedback + endpoint | next natural action | 不停在 stale form | full-stack E2E |

可增減 state，但不能靜默略過 loading、empty、error、success。若某 state 不適用，寫 `N/A - <原因>`。

### 3. Flow Steps

使用 Outcome -> Screen -> Contract -> Evidence 寫法。

| Step | Outcome | Screen / surface | User action | System response | Next state | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 使用者知道還沒有 Project | App first load | open app | 顯示 Project setup empty state | first-run empty | `frontend/e2e/...` |
| 2 | 使用者建立 Project | Project create | submit form | `POST /api/projects` 成功 | current Project ready | full-stack E2E |

### 4. Wireflow

用 ASCII 或 Mermaid 先畫低保真流程。重點是畫面和動作，不是 final pixels。

```text
App first load, no Project
+------------------------------------------+
| Grimo                                    |
+------------------------------------------+
| 建立第一個 Project                       |
| Project 會決定 Task 工作流和品質基準。   |
| [建立專案]                               |
+------------------------------------------+
          | click
          v
Project Creation Page
+------------------------------------------+
| 專案名稱 [________________]              |
| 專案路徑 [選填 /Users/.../repo]          |
| 專案工作流 [Web 服務開發 v]              |
| [建立專案]                               |
+------------------------------------------+
          | success
          v
Task Workbench with current Project
```

Wireflow 必須明確說它不是 final pixels、不是新 design system、也不是授權加入無關裝飾。

### 5. CTA And Navigation Rules

每份 flow contract 必須回答：

- 這個 screen 的唯一 primary action 是什麼？
- secondary action 是什麼？是否真的必要？
- 使用者取消、返回、retry 時回哪裡？
- 成功後停在哪個 screen？為什麼？
- 沒有必要的前置 context 時，哪些頁面 disabled、redirect 或顯示 empty state？
- 是否會出現兩個同級 primary CTA？如果會，必須刪掉或降級其中一個。

### 6. Verification Mapping

每個重要 screen/state 都要綁到證據。

| Behavior | Required evidence |
| --- | --- |
| flow / navigation | Playwright UI or full-stack E2E |
| backend API interaction | backend API test + full-stack E2E when frontend calls `/api` |
| empty / loading / error state | Playwright assertion |
| layout / responsive | visual snapshot at required viewport set |
| prototype parity claim | deterministic screenshot + Webwright/manual review |

## Apply To Planning Specs

規劃 UI spec 時，順序固定為：

1. 讀 `docs/grimo/PRD.md`，確認 product critical path。
2. 查 `docs/grimo/design/frontend-design-context.md`，確認既有 page rules。
3. 寫或更新 Screen Flow Contract。
4. 畫 low-fidelity wireflow。
5. 產出 BDD Contract 和 Verification Bindings。
6. 才能進入 task planning 或 frontend implementation。

如果 spec 只有畫面草圖、沒有 state matrix 和 verification mapping，不算 ready for `/planning-tasks`。

## Example: First-run Project Setup

| 欄位 | 內容 |
| --- | --- |
| Flow name | First-run Project setup |
| Persona | 本機開發者 |
| User goal | 第一次打開 Grimo 時建立或選擇 Project，讓 Task 工作台有真實 Project context。 |
| Entry point | App first load |
| Success endpoint | 使用者看到目前 Project 的 Task Workbench。 |
| Out of scope | 不做 native folder picker、不做 demo Task、不做 agent dispatch。 |

State Matrix：

| State | Data condition | 使用者看到什麼 | Primary action | Forbidden behavior | Evidence |
| --- | --- | --- | --- | --- | --- |
| loading | `GET /api/projects` pending | 載入 Project context | none | 不顯示 `grimo/web` 假專案 | Playwright |
| empty | `GET /api/projects` 回 `content=[]` | 建立第一個 Project 的 empty state | `建立專案` | 不顯示 fixture tasks | Playwright |
| ready | Project exists | topbar 顯示真 Project，Task Workbench 讀該 Project tasks | `新增 Task` | 不用 fallback project path | full-stack E2E |
| error | Project list failed | 專案載入失敗與 retry/recovery | `重試` | 不把 fixture 當成功資料 | Playwright |
