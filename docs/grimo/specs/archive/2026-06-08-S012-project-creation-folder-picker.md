# S012: Project Creation Folder Picker And Form Polish

> 規格：S012 | 大小：S(11) | 狀態：⛔ superseded by S013
> 日期：2026-06-08
> 對應：PRD §0.1 / §0.2 / §2、S003 follow-up、spec-roadmap S012

---

## 0. Closure

S012 is closed as superseded, not shipped.

使用者原本要在 Project Creation Page 選本機資料夾；S012 的 backend-rendered Local Directory Picker 證明「能拿到 backend absolute path」，但使用者後續回饋指出，在 web UI 裡瀏覽系統資料夾會讓操作變複雜，也不容易理解目前看到的是誰的 filesystem。S013 因此改採 Native Folder Dialog Bridge：使用者看見 OS folder chooser，Grimo 仍保存同一個 backend validated `projectPath`。

結論：

- S012-T01 的 POC/測試結果保留為歷史證據：Local Directory Picker 技術可行。
- S012-T02/T03 不繼續；Project Creation primary UX 不再走 backend directory browser。
- S012 的 `poc/S012-browser-folder-picker/` 與 `docs/grimo/tasks/*-S012-*` 在 release cleanup 中移除。
- `新增專案` 表單資訊層級仍值得改善，但不再綁在 S012 folder picker；後續若要做，應以新的 form polish spec 重新規劃。

## 1. 目標

使用者建立 Project 時，可以按「選擇資料夾」從本機資料夾清單挑出 repo / codebase，並在更清楚、更可掃描的新增專案頁完成基本資料、路徑與工作流確認。

### 1.1 範圍

| 類別 | 內容 |
| --- | --- |
| In scope | Project Creation Page 的 `projectPath` 欄位旁新增 compact folder picker；透過 `GET /api/local-directories` 瀏覽資料夾；選取後填回 `projectPath` input；`POST /api/projects` request 仍只送 `projectPath` 字串；同步優化 create form 資訊層級，讓填表、工作流摘要和角色預覽在桌面版可掃描、手機版可單欄完成。 |
| Out of scope | 不做 browser-native `showDirectoryPicker()`；不做 Electron / Tauri / native bridge；不讀檔案內容；不執行 shell；不讓 Project creation 變成必須選路徑。 |
| 既有決策修正 | S003 的「Project Creation Page 不使用 directory browser」改為：不使用會佔滿頁面的長清單，但允許 compact picker 由使用者主動開啟。 |

### 1.2 相依與重疊掃描

| 來源 | 分類 | 狀態 | 對 S012 的影響 |
| --- | --- | --- | --- |
| S002 Workflow recipe role preview | Code-level | shipped | `GET /api/local-directories` 已存在，S012 只恢復前端使用。 |
| S003 Project management list and simple projectPath contract | Code-level | shipped | `projectPath` 是唯一 public path field；S012 不新增 `workspacePath`、`folderPath` 或 browser handle 欄位。 |
| Active specs | overlap scan | none | `docs/grimo/specs/` 目前沒有其他 active spec，S012 可獨立設計。 |

### 1.3 初始估算

| 維度 | 分數 | 理由 |
| --- | ---: | --- |
| Technical risk | 1 | backend directory API、Project create validation、Vite proxy 都已有 POC 證據。 |
| Uncertainty | 2 | 使用者明確要求「專案路徑應該要可以選本機資料夾」，並追加介面優化；視覺細節需由 task 實作後用 screenshot gate 固定。 |
| Dependencies | 2 | 依賴 shipped S002/S003 contract。 |
| Scope | 2 | 主要改 `frontend/src/features/projects`、frontend API types、CSS、Playwright tests。 |
| Testing | 3 | 需要 UI、full-stack、visual gate，且要避免污染使用者真 DB。 |
| Reversibility | 1 | 不改 DB schema、不改 public `POST /api/projects` shape，可用一個 revert 移除 UI。 |
| Total | 11 | S |

## 2. 研究與設計

### 2.1 查到的事實

| 來源 | 查到什麼 | 對設計的影響 |
| --- | --- | --- |
| `frontend/src/features/projects/Projects.tsx` | `projectPath` 目前是 optional text input，submit 時只在非空白時放入 `CreateProjectInput.projectPath`。 | picker 只要把選到的 path 寫回同一個 form field；submit contract 不變。 |
| `frontend/src/features/projects/project-api.ts` | API client 目前有 `listProjects`、`listWorkflowRecipes`、`createProject`，尚未包 `GET /api/local-directories`。 | 新增 `listLocalDirectories(path?: string)`，維持 thin fetch client。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/LocalDirectoryService.java` | `GET /api/local-directories` 會回 current `path`、`parentPath`、可讀 immediate child directories；無效 path 回「請選擇有效的本機資料夾」。 | S012 可直接重用，不新增 backend endpoint。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/ProjectService.java` | `POST /api/projects` 會 normalize、驗證存在/資料夾/可讀，並做 duplicate `projectPath` 保護。 | picker 只是 UX shortcut；backend 仍是最後驗證者。 |
| `docs/grimo/specs/archive/2026-06-01-S003-project-management-list-project-path.md` | S003 明確要求新增頁不要顯示 directory browser，避免長清單和 browser handle 假成功。 | S012 要保留 S003 的 field shape，但覆寫 UI interaction：主流程不自動展開長清單，只由使用者點「選擇資料夾」開 compact picker。 |
| 2026-06-08 Edge screenshot / user feedback | 目前 `新增專案` 頁是滿版單欄表單；workflow steps 展開成長清單，角色說明在下方擠成大段文字；使用者追加「並且做介面優化」。 | S012 不只加 picker，也要重排 create form：主表單、路徑 picker、workflow summary、roles preview 要變成可掃描的工作介面。 |
| POC 2026-06-08 | `GET :5173/api/local-directories?path=/Users/samzhu/workspace/github-samzhu` 透過 Vite proxy 回 `grimoAPP` absolute path；headless browser POC 送出 `projectPath` request body；targeted Gradle tests passed。 | 核心鏈路 validated；不需要 POC task。 |
| MDN `Window.showDirectoryPicker()` | API 回傳 `FileSystemDirectoryHandle`，limited availability，需要 secure context 與 user activation。https://developer.mozilla.org/en-US/docs/Web/API/Window/showDirectoryPicker | 不採用 browser-native picker 作為 backend `projectPath` 來源。 |
| POC 2026-06-08 browser-native folder picker | `poc/S012-browser-folder-picker/browser-folder-picker-poc.mjs` 驗證 Chromium/localhost `isSecureContext=true` 且 `showDirectoryPicker` 存在；`FileSystemHandle` / `FileSystemDirectoryHandle` prototype 只有 `kind`, `name`, `entries`, `values`, `resolve` 等 handle API，沒有 `path`；`webkitdirectory` 只回 `webkitRelativePath`。 | Browser-native chooser 可改善使用者選資料夾體驗，但不能產生 backend/agent 可操作的 absolute `projectPath`。若採用，必須另設「browser-mediated file access」或 native desktop bridge，不可直接替代 S003/S012 `projectPath` contract。 |
| MDN `HTMLInputElement.webkitdirectory` | 會選整個 directory hierarchy，透過 `File.webkitRelativePath` 表示相對路徑。https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/webkitdirectory | 不用 directory upload；Grimo 需要 absolute backend path，不需要上傳檔案清單。 |

### 2.2 架構設計

使用者看見的是一個小型 folder picker；系統保存的仍是 `projectPath` 字串。

```mermaid
sequenceDiagram
  actor User as 使用者
  participant UI as Projects.tsx
  participant API as /api/local-directories
  participant Create as POST /api/projects

  User->>UI: 點「選擇資料夾」
  UI->>API: GET /api/local-directories?path=<目前路徑或空>
  API-->>UI: path, parentPath, directories[]
  User->>UI: 點子資料夾或「使用此資料夾」
  UI->>UI: 將 selected path 填入 projectPath input
  User->>UI: 點「建立專案」
  UI->>Create: POST { name, description, projectPath, workflowRecipeId }
  Create-->>UI: ProjectResponse.projectPath
```

設計規則：

- `projectPath` input 保留可手動貼上 path；picker 是輔助，不是唯一入口。
- 開啟 picker 時，預設從目前 input 值開始；若 input 空白，backend 以 `user.home` 作為起點。
- picker 只列 immediate child directories，不顯示檔案，不遞迴，不讀內容。
- `使用此資料夾` 只把目前 `LocalDirectoryResponse.path` 寫回 input，不直接建立 Project。
- 無效或不可讀目錄錯誤顯示在 picker 區塊，不能清掉已填好的 Project form。
- `POST /api/projects` request 不新增 `projectPathSource`、`browserProjectPathKey`、`workspacePath`、`folderPath`。

Create form polish rules:

- 桌面版用 two-zone create workspace：左側是主要表單與 submit，右側是 workflow / roles preview；不要讓 workflow steps 變成佔滿整頁的長清單。
- `專案工作流` 下拉選單保留在主表單；右側只顯示所選 workflow 的摘要、steps 和 roles，避免使用者以為 roles 可手動編輯。
- Workflow steps 用 compact rows 或 chips 顯示 `name` + `taskState`，必要時限制 preview 高度並可捲動；不可把 `參與角色` 推到 fold 下方很遠。
- Roles preview 用 dense list/grid 顯示 role name + one-line description；不把所有 descriptions 串成單段文字。
- 手機版維持單欄順序：基本資料 -> 專案路徑/picker -> 工作流 -> workflow summary -> roles -> submit。
- 不新增 hero、marketing copy、裝飾圖、漸層或大型卡片；這是操作頁，不是 landing page。

### 2.3 Screen Flow Contract

Flow Header:

| 欄位 | 內容 |
| --- | --- |
| Flow name | Project Creation folder picker and form polish |
| Persona | 本機開發者 |
| User goal | 建立 Project 時選到正確 repo / codebase path，並快速確認這個 Project 會套用哪個 workflow 與角色。 |
| Entry point | `專案` page -> `新增專案` -> `專案路徑` 欄位的「選擇資料夾」 |
| Success endpoint | Project Creation Page 的 `專案路徑` input 填入 absolute path；使用者看清 workflow / roles preview；建立成功後進入目前 Project 的 Task Workbench。 |
| Out of scope | native OS picker、browser handle、資料夾內容預覽、repo 掃描、agent execution。 |

State Matrix:

| State | Data condition | 使用者看到什麼 | Primary action | Forbidden behavior | Evidence |
| --- | --- | --- | --- | --- | --- |
| loading | picker 正在 request | picker 內顯示 `載入資料夾中...`，form 其他欄位保持可見 | none | 不清掉表單、不顯示 stale child rows 當作新結果 | Playwright UI |
| empty | `directories=[]` | 目前 path、上層 action、`這個資料夾沒有可選的子資料夾`、`使用此資料夾` | `使用此資料夾` | 不要求一定要選 child directory | Playwright UI |
| ready | `directories.length > 0` | 目前 path、上層 action、子資料夾 rows、`使用此資料夾` | `使用此資料夾` | 不自動 submit Project、不讀檔案 | Playwright UI + full-stack |
| error | `GET /api/local-directories` 400/500 | picker 內顯示 backend error，例如 `請選擇有效的本機資料夾` | `重試` 或關閉 picker | 不覆蓋已填好的 `projectPath` | Playwright UI |
| success | user clicks `使用此資料夾` | picker 收合或保留 compact summary；input 顯示 selected absolute path | `建立專案` | 不送 `projectPathSource` 或 browser handle | full-stack E2E |
| polished layout | create view rendered | 桌面版呈現主表單 + compact preview；手機版單欄且文字不溢出 | `建立專案` | 不顯示長 workflow list 佔滿主畫面；不把 roles 串成一大段 | visual snapshots |

Flow Steps:

| Step | Outcome | Screen / surface | User action | System response | Next state | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 使用者知道路徑可選也可留空 | Project Creation Page | 查看 `專案路徑` | input 旁有 `選擇資料夾`，note 仍說 `未填會使用 Grimo 預設路徑` | ready | UI test |
| 2 | 使用者打開本機資料夾清單 | Folder picker | 點 `選擇資料夾` | `GET /api/local-directories` 回 current path、parent、children | ready / empty / error | UI test |
| 3 | 使用者瀏覽到 repo path | Folder picker | 點子資料夾 row，例如 `grimoAPP` | picker 更新 current path，列出該資料夾子資料夾 | ready | full-stack E2E |
| 4 | 使用者選定 repo path | Folder picker | 點 `使用此資料夾` | `專案路徑` input 填入 `/Users/.../grimoAPP` | success | full-stack E2E |
| 5 | 使用者確認 workflow 和 roles | Project Creation Page | 選 `Web 服務開發` | 右側 preview 顯示 compact steps、quality loop 和角色列表 | polished layout | visual + UI test |
| 6 | 使用者建立 Project | Project Creation Page | 點 `建立專案` | `POST /api/projects` 只送 `projectPath` 字串並由 backend 驗證 | Task Workbench | full-stack E2E + backend tests |

Low-fidelity wireflow:

```text
這不是 final pixels，也不是新 design system；只固定互動合約。

Desktop Project Creation Page
+--------------------------------------------------------------+
| 新增專案                                      [返回列表]       |
|                                                              |
| ┌─ Project basics ───────────────┐ ┌─ Workflow preview ─────┐ |
| │ 專案名稱 [ grimoAPP         ]  │ │ Web 服務開發             │ |
| │ 專案描述 [ 本機 AI 開發...  ]  │ │ Discuss  DEFINING        │ |
| │ 專案路徑 [ /Users/...       ]  │ │ Explore  DEFINING        │ |
| │          [選擇資料夾]          │ │ Dev      RUNNING         │ |
| │          未填會使用預設路徑     │ │ AI Review RUNNING         │ |
| │ 專案工作流 [Web 服務開發 v]    │ │ Quality loop summary      │ |
| │ [建立專案]                    │ └──────────────────────────┘ |
| │                                │ ┌─ 參與角色 ──────────────┐ |
| │  click [選擇資料夾]            │ │ Product Manager           │ |
| │  v                             │ │ Architect                 │ |
| │  ┌─ 選擇本機資料夾 ─────────┐  │ │ Frontend Engineer         │ |
| │  │ 目前：/Users/...         │  │ │ Backend Engineer          │ |
| │  │ [上層] [關閉]            │  │ │ QA Reviewer               │ |
| │  │ grimoAPP [進入]          │  │ │ Release Engineer          │ |
| │  │ skills-hub [進入]        │  │ └──────────────────────────┘ |
| │  │ [使用此資料夾]            │  │                              |
| │  └──────────────────────────┘  │                              |
| └────────────────────────────────┘                              |
+--------------------------------------------------------------+

Mobile order
+--------------------------------------+
| 新增專案                [返回列表]    |
| 專案名稱 [grimoAPP]                  |
| 專案描述 [本機 AI 開發工作台]         |
| 專案路徑 [/Users/.../grimoAPP]        |
| [選擇資料夾]                         |
| 專案工作流 [Web 服務開發 v]           |
| Workflow preview（compact）          |
| 參與角色（compact）                  |
| [建立專案]                           |
+--------------------------------------+
```

CTA/navigation rules:

- Primary action: page primary 仍是 `建立專案`；picker primary 是區塊內的 `使用此資料夾`。
- Secondary actions: `選擇資料夾`、`上層`、child row `進入`、`關閉`。
- Cancel/back/retry: `關閉` 只收合 picker，不清除 input；`返回列表` 行為沿用 S003。
- Success destination: 建立 Project 成功後仍進入目前 Project 的 Task Workbench。
- No-context behavior: Project Creation Page 本身就是建立 Project context 的例外頁，不需要 active Project。
- Duplicate primary CTA check: 同一時間只能有一個 page-level `建立專案`；picker 的 `使用此資料夾` 不 submit Project。

Verification Mapping:

| Behavior | Required evidence |
| --- | --- |
| picker open / browse / select | `frontend/e2e/project-management.ui.spec.ts` |
| real `/api/local-directories` through Vite proxy | `frontend/e2e/project-onboarding.fullstack.spec.ts` |
| selected path submits as `projectPath` only | `frontend/e2e/project-onboarding.fullstack.spec.ts` |
| invalid directory error | `backend/src/test/java/io/github/samzhu/grimo/project/LocalDirectoryApiTests.java` + frontend UI assertion |
| layout compactness | `npm run test:visual` snapshots after intentional update |
| create form polish | Playwright assertions for workflow preview + desktop/mobile visual snapshots |

### 2.4 做法比較

| 做法 | 採用 | 理由 |
| --- | --- | --- |
| A. Backend directory picker | yes | 既有 Spring Boot API 會回 backend absolute path，POC 已證明 Vite proxy、directory listing、submit body 都可行。 |
| B. Browser native `showDirectoryPicker()` | no for `projectPath`; maybe future UX mode | POC 驗證 Chromium/localhost 可開啟 browser folder chooser，但 handle 不 expose absolute path；可作為未來 browser-mediated file access 或 desktop/native bridge 研究方向，不能直接送 `POST /api/projects.projectPath`。 |
| C. `input webkitdirectory` / folder upload | no | 會展開 file hierarchy 和 relative paths；Grimo 不需要上傳檔案，也不能用 relative path 當 backend repo path。 |
| D. 保留手打 only | no | 技術最簡單，但不符合使用者這次指出的實際操作需求。 |
| E. 只加 picker、不整理 create view | no | 會讓目前已經過長的單欄表單更長；使用者追加要求介面優化，應把 workflow/roles preview 一起整理。 |

### 2.5 Confidence / POC 結果

| 決策 | Confidence | 證據 |
| --- | --- | --- |
| Vite proxy 可呼叫 directory API | Validated | POC：`:5173/api/local-directories?path=/Users/samzhu/workspace/github-samzhu` 回 `grimoAPP` absolute path。 |
| 選到的 path 可直接送 `POST /api/projects.projectPath` | Validated | Headless browser POC request body 含 `projectPath: "/Users/samzhu/workspace/github-samzhu/grimoAPP"`。 |
| backend validation / invalid path behavior | Validated | `./gradlew test --tests LocalDirectoryApiTests --tests ProjectApiTests.createsAndListsProject --tests ProjectApiTests.rejectsInvalidProjectPathWithoutPersistedRows` 通過。 |
| Browser-native folder chooser availability | Validated | `cd frontend && node ../poc/S012-browser-folder-picker/browser-folder-picker-poc.mjs`：`isSecureContext=true`、`showDirectoryPickerType="function"`、`inputWebkitdirectory=true`。 |
| Browser-native folder chooser as backend `projectPath` source | Rejected by POC | Same POC：`FileSystemHandle` / `FileSystemDirectoryHandle` prototype 沒有 `path`；`webkitdirectory` fixture 只回 `repo-a/README.md`、`repo-a/src/main.ts` 這類 relative paths。 |
| compact picker + polished create layout | Hypothesis | 需在 task implementation 後跑 Playwright screenshot / visual gate。 |

### 2.6 Task 邊界提示

| Task 候選 | Class / file | 來源 | 正向情境 | 反向情境 | POC |
| --- | --- | --- | --- | --- | --- |
| S012-T01 Frontend picker UI BDD | `frontend/src/features/projects/Projects.tsx`, `frontend/src/features/projects/project-api.ts`, `frontend/src/domain/project/project-types.ts` | S012 AC-S012-1..4 | 使用者打開 picker、進入 `grimoAPP`、按 `使用此資料夾` 後 input 變成 absolute path | API 回錯時顯示 error，不清掉 input | not required |
| S012-T02 Full-stack selected path BDD | `frontend/e2e/project-onboarding.fullstack.spec.ts` | S012 AC-S012-5 | browser 透過真 `/api/local-directories` 選 path，建立 Project request 只含 `projectPath` | request 不含 browser handle/source fields | not required |
| S012-T03 Visual / design context | `frontend/src/styles.css`, visual snapshots, `docs/grimo/design/frontend-design-context.md` | S012 AC-S012-6, AC-S012-7 | create view 形成主表單 + compact preview 的可掃描 layout | 不重現長 directory browser；workflow steps 不佔滿整頁；roles 不串成一大段 | not required |

## 3. BDD Contract

驗證命令：

執行：`scripts/verify-release.sh`
通過條件：所有帶 `S012` AC id 的 scenario 都有對應 test evidence，且標記為 `@state:verified` 前必須通過 release gate；開發中可用 layer-specific commands 先驗紅綠燈。

AC 覆蓋矩陣：

| AC | 使用者結果 | Contract / 可觀察輸出 | Layer | State |
| --- | --- | --- | --- | --- |
| AC-S012-1 | 使用者在新增專案頁知道可以選資料夾，也可以留空用 Grimo 預設路徑。 | UI 顯示 `專案路徑` input、`選擇資料夾` button、`未填會使用 Grimo 預設路徑`。 | frontend | planned |
| AC-S012-2 | 使用者打開 picker 後看得到目前資料夾、上層與可讀子資料夾。 | `GET /api/local-directories` response: `path`, `parentPath`, `directories[]`。 | frontend, api | planned |
| AC-S012-3 | 使用者可以把目前資料夾選為 Project Path。 | 點 `使用此資料夾` 後 input value 變成 response `path`。 | frontend | planned |
| AC-S012-4 | directory API 失敗時，使用者看懂錯誤且表單內容不遺失。 | picker 顯示 error；`name`, `description`, `projectPath`, `workflowRecipeId` 保留。 | frontend | planned |
| AC-S012-5 | 建立 Project 時，選到的資料夾以 `projectPath` 字串送到 backend。 | `POST /api/projects` request 只含 `projectPath`，不含 `projectPathSource` / `browserProjectPathKey` / `workspacePath` / `folderPath`。 | fullstack | planned |
| AC-S012-6 | picker 不會把新增專案頁變成很長的資料夾清單。 | `.directory-browser` 或 successor container 只在使用者點開後顯示 compact region；visual snapshots 通過。 | frontend, visual | planned |
| AC-S012-7 | 新增專案頁本身變得可掃描，使用者不用在長清單裡找 workflow 和角色資訊。 | 桌面版主表單 + compact preview；手機版單欄；workflow steps/roles 不溢出、不互相重疊。 | frontend, visual | planned |

Feature: Project Creation local folder picker

### Rule: 使用者可以從 backend 可驗證的本機資料夾清單選 Project Path

使用者結果：
建立 Project 前，使用者不用手打 `/Users/...` 長路徑；可以打開 picker，從本機資料夾清單進入 repo，再把該 absolute path 填回 Project Path 欄位。若使用者不選也不填，仍可用 Grimo 預設路徑建立 Project。

Contract:

```http
GET /api/local-directories?path=/Users/samzhu/workspace/github-samzhu
200 OK
```

```json
{
  "path": "/Users/samzhu/workspace/github-samzhu",
  "parentPath": "/Users/samzhu/workspace",
  "directories": [
    {
      "name": "grimoAPP",
      "path": "/Users/samzhu/workspace/github-samzhu/grimoAPP"
    },
    {
      "name": "skills-hub",
      "path": "/Users/samzhu/workspace/github-samzhu/skills-hub"
    }
  ]
}
```

Field contract:

| 欄位 | 型別/格式 | 規則 | 來源 | 設計理由 | BDD 要驗什麼 |
| --- | --- | --- | --- | --- | --- |
| `path` | absolute path string | backend normalized；目前正在瀏覽的資料夾 | `LocalDirectoryService` | `使用此資料夾` 要填回 input 的值 | UI 會顯示，點選後 input value 等於此欄位 |
| `parentPath` | absolute path string or null | filesystem root 時為 null | `Path.getParent()` | 讓使用者可以往上層瀏覽 | 有 parent 時顯示 `上層`；null 時不可進上層 |
| `directories[].name` | string | child directory basename | backend filesystem listing | 給使用者掃描資料夾名稱 | 檔案不出現在 list；至少顯示 target child name |
| `directories[].path` | absolute path string | backend normalized child path | backend filesystem listing | 點 child row 時查下一層；也可作為選取結果 | 點 child 後下一次 request 使用此 path |

```gherkin
@spec:S012
@ac:AC-S012-1
@layer:frontend
@state:planned
Scenario: 新增專案頁提供資料夾選取入口但仍允許留空
  Given（前提） 使用者位於 "新增專案" 頁
  When（動作） 使用者查看 "專案路徑" 區塊
  Then（結果） 使用者看到 "專案路徑" input
  And（而且） 使用者看到 "選擇資料夾" action
  And（而且） 使用者看到 "未填會使用 Grimo 預設路徑"
  # 技術證據：Playwright role/label assertion；submit button enablement 不要求 projectPath
```

```gherkin
@spec:S012
@ac:AC-S012-2
@layer:frontend,api
@api:GET /api/local-directories
@state:planned
Scenario: 使用者打開 picker 後看到 backend 回傳的本機資料夾
  Given（前提） 使用者位於 "新增專案" 頁
  And（而且） backend 對 "/Users/samzhu/workspace/github-samzhu" 回傳 "grimoAPP" 子資料夾
  When（動作） 使用者按 "選擇資料夾"
  Then（結果） picker 顯示目前資料夾 path
  And（而且） picker 顯示 "上層"
  And（而且） picker 顯示 "grimoAPP" 與它的 absolute path
  # 技術證據：UI test mock API；full-stack test 用真 Vite proxy/API response
```

```gherkin
@spec:S012
@ac:AC-S012-3
@layer:frontend
@api:GET /api/local-directories
@state:planned
Scenario: 使用者選定目前資料夾後 input 填入 absolute path
  Given（前提） picker 目前停在 "/Users/samzhu/workspace/github-samzhu/grimoAPP"
  When（動作） 使用者按 "使用此資料夾"
  Then（結果） "專案路徑" input 顯示 "/Users/samzhu/workspace/github-samzhu/grimoAPP"
  And（而且） 使用者仍留在 "新增專案" 頁，可以繼續選工作流或建立 Project
  # 技術證據：Playwright expect input value；沒有 POST /api/projects 直到使用者按 "建立專案"
```

### Rule: picker 錯誤和 Project 建立 contract 不能污染既有表單或 API shape

使用者結果：
如果資料夾讀不到，錯誤只出現在 picker 區塊；使用者剛填的專案名稱、描述、工作流不會消失。建立 Project 時，前端送出的仍是 S003 的簡化 contract：`projectPath` 字串。

Contract:

```http
GET /api/local-directories?path=/Users/samzhu/workspace/github-samzhu/grimoAPP/README.md
400 Bad Request
```

```json
{
  "error": "請選擇有效的本機資料夾"
}
```

```http
POST /api/projects
201 Created
```

```json
{
  "name": "POC folder picker",
  "description": "POC",
  "projectPath": "/Users/samzhu/workspace/github-samzhu/grimoAPP",
  "workflowRecipeId": "web-service-development"
}
```

禁止欄位：

| 欄位 | 規則 | BDD 要驗什麼 |
| --- | --- | --- |
| `workspacePath` | S002 舊 field，不可再出現在 request/response contract | request body 不包含 |
| `folderPath` | S001 舊 field，不可再出現在 request/response contract | request body 不包含 |
| `projectPathSource` | S003 已移除，不要用來區分 picker/manual | request body 不包含 |
| `browserProjectPathKey` | browser handle key，不是 backend path | request body 不包含 |

```gherkin
@spec:S012
@ac:AC-S012-4
@layer:frontend,api
@api:GET /api/local-directories
@state:planned
Scenario: 無效資料夾錯誤不清空新增專案表單
  Given（前提） 使用者已填 "專案名稱" 為 "grimoAPP"
  And（而且） 使用者已填 "專案描述" 為 "本機 AI 開發工作台"
  When（動作） picker 請求一個不是資料夾的 path
  Then（結果） picker 顯示 "請選擇有效的本機資料夾"
  And（而且） "專案名稱" 和 "專案描述" 欄位仍保留原值
  # 技術證據：Playwright mock 400；input value assertions
```

```gherkin
@spec:S012
@ac:AC-S012-5
@layer:fullstack
@api:GET /api/local-directories, POST /api/projects
@state:planned
Scenario: 選到的資料夾用 projectPath 字串建立 Project
  Given（前提） 使用者透過 picker 選到 "/Users/samzhu/workspace/github-samzhu/grimoAPP"
  When（動作） 使用者填好名稱、描述、工作流並按 "建立專案"
  Then（結果） browser 發出 POST /api/projects
  And（而且） request body 包含 "projectPath": "/Users/samzhu/workspace/github-samzhu/grimoAPP"
  And（而且） request body 不包含 workspacePath、folderPath、projectPathSource 或 browserProjectPathKey
  And（而且） 建立成功後使用者進入目前 Project 的 Task Workbench
  # 技術證據：full-stack Playwright waitForRequest + API response / UI state assertion
```

```gherkin
@spec:S012
@ac:AC-S012-6
@layer:frontend,visual
@state:planned
Scenario: picker 是 compact 輔助區塊，不是長列表主畫面
  Given（前提） 使用者位於 "新增專案" 頁
  When（動作） 使用者尚未按 "選擇資料夾"
  Then（結果） 頁面不顯示 child directory rows
  When（動作） 使用者按 "選擇資料夾"
  Then（結果） picker 顯示在 "專案路徑" 欄位附近
  And（而且） "專案工作流" 和 "參與角色" 仍在同一建立流程中，不被不受控的長清單取代
  # 技術證據：Playwright UI assertion + visual snapshots
```

### Rule: 新增專案頁資訊層級要像工作介面，而不是長表單

使用者結果：
使用者填完 Project 基本資料時，同一眼可以掃到目前 workflow、主要 steps、quality loop 和參與角色；不需要在全寬長清單裡一路往下找資訊。這是 create flow 的操作優化，不是新 landing page 或新 design system。

Contract:

Desktop layout:

- 主表單區顯示 `專案名稱`、`專案描述`、`專案路徑`、`專案工作流`、`建立專案`。
- Preview 區顯示 selected workflow name、short description、compact steps、quality loop summary。
- Roles 區顯示 role name + short description，文字不可串成同一段。

Mobile layout:

- 單欄順序為 basic fields -> path picker -> workflow select -> workflow preview -> roles -> submit。
- 文字不可溢出按鈕或遮住下一個 section。

```gherkin
@spec:S012
@ac:AC-S012-7
@layer:frontend,visual
@state:planned
Scenario: 新增專案頁把表單、工作流和角色整理成可掃描 layout
  Given（前提） 使用者位於 "新增專案" 頁
  And（而且） 工作流選擇為 "Web 服務開發"
  When（動作） 使用者查看桌面版畫面
  Then（結果） 使用者在主表單區看到 Project 基本欄位與 "建立專案"
  And（而且） 使用者在 preview 區看到 "Web 服務開發"、compact workflow steps 與 quality loop summary
  And（而且） 使用者在角色區看到 Product Manager、Architect、Frontend Engineer、Backend Engineer、QA Reviewer、Release Engineer
  And（而且） workflow steps 不以全寬長清單佔滿新增專案頁
  # 技術證據：Playwright UI assertions + desktop/mobile visual snapshots
```

Verification Bindings:

- backend: `backend/src/test/java/io/github/samzhu/grimo/project/LocalDirectoryApiTests.java`, `backend/src/test/java/io/github/samzhu/grimo/project/ProjectApiTests.java`
- frontend: `frontend/e2e/project-management.ui.spec.ts`
- fullstack: `frontend/e2e/project-onboarding.fullstack.spec.ts`
- visual: `frontend/e2e/project-startup.ui.spec.ts` 或新增/調整 Project create snapshot coverage
- command: `scripts/verify-release.sh`

Generality expectations:

| AC | 防硬編碼方式 |
| --- | --- |
| AC-S012-1 | 測試 blank `projectPath` submit button 仍可啟用，避免 picker 變成必填。 |
| AC-S012-2 | mock 至少兩個 child directories；full-stack 用 temporary path 或實際 fixture path。 |
| AC-S012-3 | 使用非預設 path，例如 temp dir 或 `/Users/.../grimoAPP`，驗 input value 來自 response。 |
| AC-S012-4 | 先填 form，再觸發 400，驗每個欄位值保留。 |
| AC-S012-5 | waitForRequest 驗 request body，不只看 UI success message。 |
| AC-S012-6 | 視覺測試覆蓋 picker closed/open 兩種狀態，避免只截 closed 狀態。 |
| AC-S012-7 | 視覺測試覆蓋 desktop 與 mobile；assert roles 分別存在，避免只看一段 concatenated text。 |

### 非功能需求檢查

| 分類 | 對應驗收 | 說明 |
| --- | --- | --- |
| Performance | AC-S012-2, AC-S012-6, AC-S012-7 | picker 只在使用者點開後 request，且只列 immediate child directories；workflow/roles preview 使用既有 recipe response，不新增 API。 |
| Security | AC-S012-2, AC-S012-5 | 不讀檔案內容、不執行 shell、不上傳檔案；backend 仍驗證 path。MVP Spring Security 維持 permit-all。 |
| Reliability | AC-S012-4, AC-S012-5 | directory API 失敗不清空表單；Project create 仍由 backend 最後驗證。 |
| Usability | AC-S012-1, AC-S012-3, AC-S012-7 | 使用者可以點選資料夾，也可以留空用 Grimo 預設路徑；新增專案頁的 workflow/roles 資訊更容易掃描。 |
| Maintainability | AC-S012-5 | API shape 不新增 source/readiness/browser handle fields，延續 S003 簡化 contract。 |

## 4. 介面與 API 設計

### Frontend types

```ts
export type LocalDirectoryEntry = {
  name: string;
  path: string;
};

export type LocalDirectoryListing = {
  path: string;
  parentPath: string | null;
  directories: LocalDirectoryEntry[];
};
```

### Frontend API client

```ts
export async function listLocalDirectories(path?: string): Promise<LocalDirectoryListing> {
  const search = path ? `?path=${encodeURIComponent(path)}` : "";
  return requestJson<LocalDirectoryListing>(`/api/local-directories${search}`);
}
```

Rules:

- `path` undefined / blank 時由 backend 使用 `user.home`；frontend 不猜 home directory。
- error 使用既有 `requestJson` error path，顯示 `body.error`。
- 不新增 shared fetch abstraction，除非重複使用超過現有 standards 的門檻。

### Project create request

不變，延續 S003：

```ts
export type CreateProjectInput = {
  name: string;
  description: string;
  projectPath?: string;
  workflowRecipeId: string;
};
```

```json
{
  "name": "grimoAPP",
  "description": "本機 AI 開發工作台",
  "projectPath": "/Users/samzhu/workspace/github-samzhu/grimoAPP",
  "workflowRecipeId": "web-service-development"
}
```

### Component state

`Projects.tsx` 可保持 local `useState`，不需要 reducer；狀態只屬於 create form 的小互動。

```ts
type DirectoryPickerState = {
  isOpen: boolean;
  isLoading: boolean;
  listing: LocalDirectoryListing | null;
  error: string;
};
```

Rules:

- open picker: `path = form.projectPath.trim() || undefined`
- enter child: `path = child.path`
- parent: `path = listing.parentPath`
- use current: `setForm({ ...form, projectPath: listing.path })`
- close: `isOpen=false`，不改 `projectPath`

### Layout contract

```ts
type ProjectCreateLayout = {
  mainForm: "project fields + path picker + workflow select + submit";
  workflowPreview: "selected workflow summary + compact steps + quality loop";
  rolePreview: "workflow roles as compact repeated rows";
};
```

Rules:

- desktop: `mainForm` 與 preview zones 可用 CSS grid；preview zones 不可包在 page-level nested cards 裡造成 card-in-card。
- mobile/tablet: zones stack as one column with stable gaps。
- Workflow steps 和 roles 使用穩定尺寸/scroll constraints，動態內容不得推擠 submit action 到不可預期位置。
- `建立專案` 保持唯一 page-level primary CTA。

### Storage

N/A — S012 不新增或修改 DB table。`projects.workspace_path` 仍是既有 internal storage column；public API field 仍是 `projectPath`。

## 5. 檔案規劃

| Path | Action | Why |
| --- | --- | --- |
| `docs/grimo/specs/spec-roadmap.md` | modify | 登錄 S012 active spec。 |
| `docs/grimo/specs/2026-06-08-S012-project-creation-folder-picker.md` | new | 保存本 spec sections 1-5。 |
| `docs/grimo/glossary.md` | modify | 更新 Project Path / Manual Project Path / Local Directory Picker 語言，標示 S012 恢復 compact picker。 |
| `docs/grimo/design/frontend-design-context.md` | modify | 記錄 S012 取代 S003 手打-only path 的 UI 決策。 |
| `frontend/src/domain/project/project-types.ts` | modify later | 新增 `LocalDirectoryListing` / `LocalDirectoryEntry` frontend type。 |
| `frontend/src/features/projects/project-api.ts` | modify later | 新增 `listLocalDirectories` API client。 |
| `frontend/src/features/projects/Projects.tsx` | modify later | 在 `專案路徑` 欄位旁加 compact picker UI 與狀態；整理 create view 的表單、workflow preview 和 roles preview。 |
| `frontend/src/styles.css` | modify later | 補 picker compact layout 與 create form polish；避免長清單破壞 create flow。 |
| `frontend/e2e/project-management.ui.spec.ts` | modify later | 驗 AC-S012-1..4、AC-S012-6、AC-S012-7。 |
| `frontend/e2e/project-onboarding.fullstack.spec.ts` | modify later | 驗 AC-S012-5 透過真 `/api/local-directories` 選 path 並建立 Project。 |
| `backend/src/test/java/io/github/samzhu/grimo/project/LocalDirectoryApiTests.java` | verify / maybe modify later | 既有 AC-S002 tests 已覆蓋 directory listing / invalid path；若 S012 需要 AC id trace，可補 display name 或新增 tests。 |
| `scripts/verify-release.sh` | verify later | 確認 full-stack / visual tests 被 release gate 覆蓋；若既有命令已包含，不改。 |

### Handoff readiness

- POC：completed，核心 API / proxy / submit body 可行。
- Task planning：completed；S012-T01 PASS。
- Pause：使用者於 2026-06-08 選擇改走 S013 Native Project Path folder dialog，避免 backend directory tree 造成操作複雜。
- 建議下一步：先完成 S013 設計確認；不要繼續 S012-T02，避免把即將被取代的 backend directory picker 做進 full-stack flow。

## 6. Task Plan

> 狀態：⏳ Dev
> 日期：2026-06-08
> POC：not required — S012 §2.5 已用 live Vite proxy、headless browser request body、backend targeted tests 驗證核心做法；task loop 不再另建 `poc/S012/`。

### 6.1 Pre-flight validation

| Check | Result |
| --- | --- |
| PRD alignment | PASS — S012 支援 Project-first critical path；使用者先建立 Project，Project 才決定後續 Task 工作流與品質基準。 |
| Existing research / shipped findings | PASS — S002/S003 已出貨 `GET /api/local-directories`、`projectPath` contract、backend validation；S012 重用既有 stack，不新增 dependency。 |
| Simpler approach check | PASS — 保留手打 only 最簡單但不符合使用者需求；browser native picker 不能提供 backend absolute path；backend directory picker 是既有 stack 的最小可行方案。 |
| POC decision | PASS — POC completed in §2.5；沒有 unvalidated framework/library assumption。 |
| Task readiness | PASS — AC-S012-1..7 都有 UI/API/full-stack/visual evidence mapping。 |

### 6.2 BDD layer split

| Layer | Task | 主要 AC | 測試檔 | 驗證命令 |
| --- | --- | --- | --- | --- |
| Frontend BDD | `S012-T01 Frontend folder picker UI BDD` | AC-S012-1, AC-S012-2, AC-S012-3, AC-S012-4 | `frontend/e2e/project-management.ui.spec.ts` | `npm --prefix frontend run test:visual -- project-management.ui.spec.ts --grep "AC-S012-1\|AC-S012-2\|AC-S012-3\|AC-S012-4"` |
| Full-stack E2E | `S012-T02 Full-stack folder picker Project create BDD` | AC-S012-5 | `frontend/e2e/project-onboarding.fullstack.spec.ts` | `npm --prefix frontend run test:fullstack -- project-onboarding.fullstack.spec.ts --grep "AC-S012-5"` |
| Frontend visual | `S012-T03 Project create form polish and visual gate` | AC-S012-6, AC-S012-7 | `frontend/e2e/project-management.ui.spec.ts`, visual snapshots | `npm --prefix frontend run build`; `npm --prefix frontend run test:visual` |

### 6.3 Task order

| Order | Task file | Depends on | Why this order |
| ---: | --- | --- | --- |
| 1 | `docs/grimo/tasks/2026-06-08-S012-T01-frontend-folder-picker-ui.md` | none | 先建立可操作的 picker UI 與 mocked BDD，讓 user-visible behavior 有 RED/GREEN。 |
| 2 | `docs/grimo/tasks/2026-06-08-S012-T02-fullstack-folder-picker-project-create.md` | S012-T01 | 再用真 Vite proxy + Spring Boot + temporary DB 驗 selected path 可以建立 Project。 |
| 3 | `docs/grimo/tasks/2026-06-08-S012-T03-create-form-polish-visual.md` | S012-T01, S012-T02 | 最後整理 layout 與 snapshots，避免 visual churn 影響還沒穩定的 picker behavior。 |

### 6.4 AC coverage

| AC | Covered by | Evidence type |
| --- | --- | --- |
| AC-S012-1 | S012-T01 | Playwright UI assertion |
| AC-S012-2 | S012-T01 | Playwright UI assertion with mocked API; S012-T02 adds real API path |
| AC-S012-3 | S012-T01 | Playwright UI assertion |
| AC-S012-4 | S012-T01 | Playwright UI assertion with mocked 400 |
| AC-S012-5 | S012-T02 | Full-stack Playwright request body + persisted read-back |
| AC-S012-6 | S012-T03 | Playwright UI assertion + deterministic visual snapshots |
| AC-S012-7 | S012-T03 | Playwright UI assertion + desktop/mobile visual snapshots |

### 6.5 Release gate note

`scripts/verify-release.sh` already runs `npm run build`, `npm run test:visual`, backend tests, and `npm run test:fullstack`. If implementation adds S012 tests to the existing Playwright suites, the release gate should pick them up without a new per-spec command. If the log label becomes misleading, update only the label text, not the command shape.

### 6.5 Browser-native POC update

Date: 2026-06-08

User concern:

- 後端資料夾瀏覽器會讓使用者看見不熟悉的系統資料夾，操作比 OS folder chooser 複雜。
- Need to re-check whether browser-native folder selection can replace the backend directory picker before continuing S012-T02.

POC command:

```bash
cd frontend && node ../poc/S012-browser-folder-picker/browser-folder-picker-poc.mjs
```

Result:

```json
{
  "availability": {
    "isSecureContext": true,
    "showDirectoryPickerType": "function",
    "inputWebkitdirectory": true
  },
  "conclusion": {
    "browserChooserAvailableOnChromiumLocalhost": true,
    "exposesAbsolutePath": false,
    "webkitdirectoryReturnsRelativePaths": true
  }
}
```

Decision impact:

- Browser-native folder chooser is feasible for human-friendly folder selection on Chromium/localhost.
- Browser-native folder chooser is not feasible as a direct replacement for `POST /api/projects.projectPath`, because the browser does not expose a backend-usable absolute path.
- Continuing S012 as-is means improving the backend picker UX; switching to browser-native means opening a new design for browser-mediated file access or native desktop bridge, not a small replacement inside S012.
