# S014: Project Path Folder Browser

> 規格：S014 | 大小：M(13) | 狀態：⏳ Design
> 日期：2026-06-12
> 對應：PRD §0.1 / §0.2 / §2、architecture A004、S003 `projectPath` contract、S012/S013 path picker findings

---

## 1. 目標

使用者建立 Project 時，可以在 Grimo 內打開像檔案選擇器的資料夾瀏覽 modal，從 `~/.grimo/projects/` 開始選本機資料夾；Grimo 仍保存 backend 可驗證、agent 可操作的 absolute `projectPath`。

### 1.1 範圍

| 類別 | 內容 |
| --- | --- |
| In scope | 將 Project Creation Page 的 `選擇資料夾` primary UX 從 Swing native dialog 改成 Grimo in-app folder browser；backend 用 Java filesystem API 回傳本機資料夾候選清單；未帶 `path` 時從 `~/.grimo/projects/` 開始並確保 root 存在；此清單代表本機實體資料夾，不是 DB 內已建立的 Project records；使用者選定後把 absolute path 填回 `projectPath` input；`POST /api/projects` 仍只送 `projectPath` 字串。 |
| Out of scope | 不用 browser-only `showDirectoryPicker()` 當 `projectPath` source；不導入 Electron/Tauri；不讀檔案內容；不執行 shell；不掃 repo；不改 `projects` DB schema；不讓 Project 建立變成必須選路徑。 |
| 既有決策修正 | S013 的 Swing Native Folder Dialog Bridge 不再是 Project Creation primary UX；S014 以 backend-backed Grimo Folder Browser 取代它。S013 保留為 shipped history，不回改 archive。 |
| Open decision | Modal 是否提供「建立新資料夾」 action。推薦答案：先不做；使用者要 Grimo 自動建立 path 時保持 `projectPath` 空白，沿用 S003 default project path。 |
| Resolved decision | `~/.grimo/projects/` 是 Grimo-managed Project path 的預設容器與 folder browser 起點，不是使用者要選的 Project Path 本身。使用者沒有選資料夾時，Grimo 自動建立 `~/.grimo/projects/<projectId>`；使用者按 `選擇資料夾` 時，代表要指定自己習慣放 repo/codebase 的具體資料夾。 |
| Resolved decision | S014 不提供 Swing / OS folder chooser fallback。Folder browser 讀取失敗時，錯誤留在 modal 內，`projectPath` input 保持可編輯；前端不得改呼叫 `POST /api/native-folder-dialogs/project-path`。 |
| Resolved decision | Folder browser modal 不提供「輸入或貼上路徑後前往」欄位。使用者若要選自己習慣放專案的位置，從 modal 的家目錄 / 上層導覽進去；表單上的 `projectPath` input 仍保留手動輸入能力。 |
| Resolved decision | Folder browser 要像 Finder 一樣自然導覽：提供 `回家目錄` 與 `回 Grimo 預設位置` 兩個明確入口。`回家目錄` 用來找使用者習慣放 repo/codebase 的位置；`回 Grimo 預設位置` 回到 `~/.grimo/projects/`。 |
| Resolved decision | `GET /api/local-directories` 以 `location=home|default` 支援兩個 shortcut；`path` 與 `location` 同時出現時回 400，避免 request 意圖不清楚。 |

### 1.2 相依與重疊掃描

| 來源 | 分類 | 狀態 | 對 S014 的影響 |
| --- | --- | --- | --- |
| S003 Project management list and simple `projectPath` contract | Code-level | shipped | `projectPath` 是 public API 唯一路徑欄位；S014 必須保留 backend-operable absolute path，不新增 browser handle/source 欄位。 |
| S013 Native Project Path folder dialog | Code-level | shipped | 現有前端按鈕和 native dialog endpoint 可被取代；若 implementation 移除 Swing bridge，要同步更新 tests、architecture/glossary/design context。 |
| S012 Project Creation folder picker | Superseded historical evidence | archived | S012 已證明 backend directory listing 技術可行，但舊設計被拒絕的原因是 UI 直接顯示系統樹太複雜；S014 必須用 modal/file-picker interaction 解決 UX，而不是重現長清單。 |
| Active specs | overlap scan | none | `docs/grimo/specs/` 目前沒有其他 active spec；S014 可獨立設計。 |

### 1.3 初始估算

| 維度 | 分數 | 理由 |
| --- | ---: | --- |
| Technical risk | 2 | `GET /api/local-directories` 已存在，但 default root、root creation、modal interaction 和 Swing cleanup 會跨 backend/frontend/test。 |
| Uncertainty | 2 | 使用者已確定拒絕 Swing primary UX；仍需確認是否加入「建立新資料夾」。 |
| Dependencies | 2 | 依賴 S003 path contract、S013 current implementation、S012 directory listing evidence。 |
| Scope | 2 | 改 backend directory service default、frontend modal state、API client usage、Playwright full-stack/visual tests、docs。 |
| Testing | 3 | 需要 backend API、frontend interaction、full-stack Vite proxy、visual/responsive evidence；不能碰使用者真 DB。 |
| Reversibility | 2 | 不改 DB schema，但會移除或降級 shipped S013 native endpoint usage；可用 feature-level revert 回到 S013。 |
| Total | 13 | M |

## 2. 研究與設計

### 2.1 查到的事實

| 來源 | 查到什麼 | 對設計的影響 |
| --- | --- | --- |
| `docs/grimo/architecture.md` A004 | `projectPath` 是 single optional backend path；browser handle 不可假裝成 server-operable path。 | S014 保留 `projectPath` absolute path contract；不採 browser-only picker。 |
| `docs/grimo/glossary.md` | Project Path 是 backend 可驗證、可保存、可操作的 repo/codebase path；Local Directory Picker 是歷史方案，Native Folder Dialog Bridge 是 S013 primary UX。 | S014 要新增「Project Path Folder Browser」語彙，並標出它取代 Swing primary UX。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/LocalDirectoryService.java` | 目前未帶 path 時從 `System.getProperty("user.home")` 開始；只列 immediate child directories；invalid/unreadable 會回「請選擇有效的本機資料夾」。 | S014 要把 empty default 改為 `~/.grimo/projects/`，並用 `Files.createDirectories` 確保 root 存在。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/ProjectService.java` | 使用者不填 `projectPath` 時，backend 建立 `~/.grimo/projects/<projectId>`；使用者填 path 時驗證存在、資料夾、可讀。 | Folder browser 是輔助選取；如果使用者要 Grimo 自動建立唯一 Project path，仍可讓 `projectPath` 空白。 |
| `frontend/src/features/projects/Projects.tsx` | `選擇資料夾` 現在呼叫 `chooseNativeProjectPath`，用 `isNativeDialogLoading/nativeDialogError` 管狀態。 | S014 要改成 modal/list state：`folderBrowserOpen`、`folderListing`、`folderBrowserError`、`isFolderListingLoading`。 |
| `frontend/src/features/projects/project-api.ts` | `listLocalDirectories(path?: string)` 已存在，但前端目前不使用；`chooseNativeProjectPath` 仍存在。 | S014 可直接重用 `listLocalDirectories`，並移除 frontend native dialog call。 |
| Java `Files` API — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html | `Files.list(Path)` 可列目錄；`Files.createDirectories(Path)` 會建立不存在的 parent directories，目錄已存在時不因已存在而失敗。 | Backend 可用標準 Java API 列 `~/.grimo/projects/`，並在 first open 時建立 root。 |
| Java `Path` API — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Path.html | `Path` 表示 hierarchical filesystem path；可轉 absolute path、normalize 並取得 parent/root。 | API response 可以回 normalized absolute `path`、`parentPath`，讓前端做 breadcrumbs/up navigation。 |
| MDN `showDirectoryPicker()` — https://developer.mozilla.org/en-US/docs/Web/API/Window/showDirectoryPicker | Browser picker 回 `FileSystemDirectoryHandle`，不是 OS absolute path；需要 secure context 與 user activation。 | 不採 browser-only picker；它無法產生 backend 可用的 `/Users/...` String。 |
| S012/S013 archived findings | Browser-native picker 可開 chooser 但不能提供 backend path；Swing 可拿 path 但 UX 被使用者拒絕；backend directory listing 可回 absolute path。 | S014 選擇「frontend modal + backend listing」作為新 primary UX。 |

### 2.2 架構設計

S014 把「選資料夾」拆成兩層：Grimo modal 負責互動，Spring Boot 只提供本機資料夾清單與 absolute path。

```mermaid
sequenceDiagram
  actor User as 使用者
  participant UI as Projects.tsx
  participant API as GET /api/local-directories
  participant FS as LocalDirectoryService
  participant Create as POST /api/projects

  User->>UI: 點「選擇資料夾」
  UI->>API: GET /api/local-directories
  API->>FS: listDirectories(null)
  FS->>FS: ensure ~/.grimo/projects exists
  FS-->>API: path=/Users/.../.grimo/projects, parentPath, directories[]
  API-->>UI: LocalDirectoryResponse
  UI->>UI: 顯示 Grimo folder browser modal
  User->>UI: 點 child directory 或上層
  UI->>API: GET /api/local-directories?path=<absolute path>
  API-->>UI: next LocalDirectoryResponse
  User->>UI: 點「使用此資料夾」
  UI->>UI: projectPath input = response.path
  User->>UI: 點「建立專案」
  UI->>Create: POST { name, description, projectPath, workflowRecipeId }
```

Design rules:

- `GET /api/local-directories` 不帶 `path` 時，backend 使用 `Path.of(user.home, ".grimo", "projects")`，先 `Files.createDirectories(root)`，再回 listing。
- 帶 `path` 時，backend normalize 後驗證存在、是 directory、可讀；invalid/unreadable 仍回現有 user-readable error。
- Response 仍是 `LocalDirectoryResponse(path, parentPath, directories[])`；不回檔案、不讀內容、不回 hidden file metadata。
- 前端 modal 顯示目前 path、可回家目錄、可回 Grimo 預設位置、可上層、可進入 child directory、可使用目前資料夾、可關閉。
- 當目前 path 是 default root `~/.grimo/projects/` 時，modal 只把它當瀏覽起點；`使用此資料夾` 必須 disabled 或顯示不可選原因，避免把 Grimo-managed root 容器保存成單一 Project 的 `projectPath`。
- Modal 不新增任意 path jump input；使用者需要跳到其他常用位置時，透過家目錄 / 上層 / child directory 導覽，或直接使用表單上的 `projectPath` input。
- 選定資料夾只填 `projectPath` input，不直接 submit Project。
- `POST /api/projects` request 仍不包含 `workspacePath`、`folderPath`、`projectPathSource`、`browserProjectPathKey`、`FileSystemDirectoryHandle`。
- Swing native dialog endpoint 不再是 primary path，也不是 S014 fallback；implementation 可刪除 `NativeFolderDialog*` 與 `SwingNativeFolderDialogGateway`，或保留為非 UI 使用的 historical/internal code，但前端不得呼叫它。

### 2.3 Screen Flow Contract

Flow Header:

| 欄位 | 內容 |
| --- | --- |
| Flow name | Project Path folder browser |
| Persona | 本機開發者 |
| User goal | 建立 Project 時不用手貼長路徑，也不用 Swing dialog；可以在 Grimo 內從預設專案資料夾挑本機資料夾。 |
| Entry point | Project Creation Page -> `專案路徑` -> `選擇資料夾` |
| Success endpoint | `專案路徑` input 填入 backend validated absolute path；使用者可繼續建立 Project。 |
| Out of scope | browser `FileSystemDirectoryHandle`、Swing/JFileChooser primary UX、資料夾內容預覽、repo 掃描、modal 內任意 path jump input、建立新資料夾（待確認）。 |

State Matrix:

| State | Data condition | 使用者看到什麼 | Primary action | Forbidden behavior | Evidence |
| --- | --- | --- | --- | --- | --- |
| loading | directory listing request pending | Modal 內顯示 `載入資料夾中...`；表單欄位保留 | none | 不顯示 stale child rows 當成新結果、不清空 form | Playwright UI |
| empty default root | current path is `~/.grimo/projects/` and `directories=[]` | 目前 path、`尚未有可選的專案資料夾`、`使用此資料夾` disabled、`回家目錄`、`回 Grimo 預設位置` | close / leave Project Path blank | 不把 default root 當 Project Path；不要求使用者一定要選 | Playwright UI |
| empty selectable folder | `directories=[]` and current path is not default root | 目前 path、`這個資料夾沒有可選的子資料夾`、`使用此資料夾`、`上層`、`回家目錄`、`回 Grimo 預設位置` | `使用此資料夾` | 不要求一定要有 child directory | Playwright UI |
| ready | `directories.length > 0` | 目前 path、可讀 child directory rows、`上層`、`回家目錄`、`回 Grimo 預設位置`、`使用此資料夾`；若目前 path 是 default root，`使用此資料夾` disabled | `使用此資料夾` for non-root folder | 不讀檔案、不遞迴展開整棵樹、不 submit Project、不選 default root | UI + full-stack |
| error | backend 回 400/500 | Modal 內顯示 `請選擇有效的本機資料夾` 或 `無法讀取資料夾`，提供 `回 Grimo 預設位置`/`回家目錄`/`關閉` | `回 Grimo 預設位置` or `回家目錄` | 不覆蓋已填的 `projectPath` | backend + UI |
| success | user clicks `使用此資料夾` | Modal 關閉；input 顯示 selected absolute path | `建立專案` | 不送 browser handle/source 欄位 | full-stack |

Flow Steps:

| Step | Outcome | Screen / surface | User action | System response | Next state | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 使用者知道可選也可留空 | Project Creation Page | 查看 `專案路徑` | input 旁有 `選擇資料夾`，note 顯示 `未填會使用 Grimo 預設路徑` | ready | UI test |
| 2 | 使用者看到 Grimo 預設管理區底下有哪些可選資料夾 | Folder browser modal | 點 `選擇資料夾` | `GET /api/local-directories` 不帶 path；backend 建立/列出 `~/.grimo/projects/` 底下的本機實體資料夾候選，不讀 DB Project records；default root 本身不可被選成 Project Path | ready/empty | backend + UI |
| 3 | 使用者瀏覽本機資料夾 | Folder browser modal | 點 child row、`上層`、`回家目錄` 或 `回 Grimo 預設位置` | modal 更新 current path 和 child directories；`回家目錄` request home listing，`回 Grimo 預設位置` request default root listing | ready/empty/error | UI + full-stack |
| 4 | 使用者選定路徑 | Folder browser modal | 點 `使用此資料夾` | `projectPath` input = current response `path`；modal 關閉 | success | UI |
| 5 | 使用者建立 Project | Project Creation Page | 點 `建立專案` | `POST /api/projects` 只送 selected `projectPath` String | Task Workbench | full-stack |

Low-fidelity wireflow:

```text
這不是 final pixels，也不是新 design system；只固定互動合約。

Project Creation Page
+------------------------------------------------+
| 新增專案                                      |
| 專案名稱 [ grimoAPP                         ] |
| 專案路徑 [ /Users/samzhu/.grimo/projects/app ] |
|          [選擇資料夾]                         |
| 未填會使用 Grimo 預設路徑                    |
| 專案工作流 [Web 服務開發 v]                  |
| [建立專案]                                   |
+------------------------------------------------+
          | click 選擇資料夾
          v
Folder Browser Modal
+------------------------------------------------+
| 選擇 Project 資料夾                 [關閉]     |
| 目前位置                                           |
| /Users/samzhu/.grimo/projects                     |
| [回家目錄] [回 Grimo 預設位置] [上層]             |
| ------------------------------------------------ |
| app-a/                                      [進入] |
| grimoAPP/                                  [進入] |
| skills-hub/                                [進入] |
| ------------------------------------------------ |
| [使用此資料夾]                                  |
+------------------------------------------------+
          | click 使用此資料夾
          v
Project Creation Page
+------------------------------------------------+
| 專案路徑 [ /Users/samzhu/.grimo/projects/grimoAPP ] |
| [建立專案]                                      |
+------------------------------------------------+
```

CTA/navigation rules:

- Primary action: page-level `建立專案`；modal-level `使用此資料夾`。
- Secondary actions: `選擇資料夾`、`關閉`、`上層`、`回家目錄`、`回 Grimo 預設位置`、child row `進入`。
- Cancel/back/retry: `關閉` 只關 modal，不清 input；error 可 `回家目錄` 或 `回 Grimo 預設位置`。
- Success destination: selected path 回填到 create form；真正建立成功後沿用 S003/S010 進 Task Workbench/current Project。
- No-context behavior: create page 不需要 active Project；folder browser default root 由 backend local runtime 決定。
- Duplicate primary CTA check: modal 的 `使用此資料夾` 不建立 Project；頁面只有一個 `建立專案`。
- Default root check: `~/.grimo/projects/` 是瀏覽起點；若使用者想交給 Grimo 管理，應關閉 modal 並保持 `projectPath` 空白，由 `POST /api/projects` 建立 `~/.grimo/projects/<projectId>`。

Verification Mapping:

| Behavior | Required evidence |
| --- | --- |
| default root is `~/.grimo/projects/` and is created if missing | backend `LocalDirectoryApiTests` with temporary `user.home` |
| default root itself is not selected as Project Path | UI/full-stack test verifies `使用此資料夾` is disabled at root and blank `projectPath` create still generates `<projectId>` path |
| modal opens and renders loading/ready/empty/error states | `frontend/e2e/project-management.ui.spec.ts` |
| home and Grimo default navigation are separate | UI/full-stack test verifies `回家目錄` and `回 Grimo 預設位置` request different filesystem locations |
| browsing child directories uses real `/api/local-directories` through Vite proxy | `frontend/e2e/project-onboarding.fullstack.spec.ts` |
| selected path submits as `projectPath` only | full-stack request body + persisted read-back |
| native Swing endpoint is no longer called by UI | UI/full-stack route spy asserts `POST /api/native-folder-dialogs/project-path` count is zero |
| directory listing error does not fallback to Swing | UI/full-stack route spy asserts `POST /api/native-folder-dialogs/project-path` count remains zero after error |
| responsive/modal layout does not overlap | visual snapshots via `npm run test:visual` |

### 2.4 做法比較

| 做法 | 採用 | 理由 |
| --- | --- | --- |
| A. Backend-backed Grimo Folder Browser | yes | 符合使用者「Swing 不好用」與「Java 讀本地、前端做成類似檔案選擇器」；backend 仍回 absolute path，保留 `projectPath` contract。 |
| B. 保留 S013 Swing native bridge | no for primary UX | 技術可行，且剛修過 headless 問題，但使用者已明確拒絕 Swing 操作體驗；保留會讓產品路徑混亂。 |
| C. Browser `showDirectoryPicker()` | no | 官方 API 回 `FileSystemDirectoryHandle`，拿不到 backend 可操作的 absolute path；不符合本機 Project 建立目的。 |
| D. 手動輸入 path only | no | 技術最簡單，但使用者已明確拒絕把 fallback 當主方案。 |
| E. Electron/Tauri native shell | no for MVP | 可回 absolute path，但會引入 desktop packaging/IPC/security boundary；超出目前 Spring Boot + Vite dev surface。 |

### 2.5 Confidence / POC

| 決策 | Confidence | 證據 |
| --- | --- | --- |
| Java backend can list directories and return absolute child paths | Validated | Existing `LocalDirectoryService` uses `Files.list`, filters directories, returns normalized absolute path; existing backend tests cover valid/invalid listing. |
| Empty path can default to `~/.grimo/projects/` and create the root | Validated by API docs, implementation still required | Java `Files.createDirectories` supports creating missing parent directories; S003 `ProjectService` already creates default project paths under `~/.grimo/projects/<projectId>`。 |
| Browser-only picker cannot satisfy backend `projectPath` | Validated/rejected | MDN + S012 POC show browser handle/relative path only, no OS absolute path. |
| In-app modal can provide acceptable file-picker UX | Hypothesis | Needs frontend implementation and visual/interaction evidence. |
| Removing frontend use of Swing endpoint will not break Project create | Validated by contract | `POST /api/projects` already accepts plain `projectPath`; picker only fills existing input. |

POC：not required for backend listing; required only if task implementation chooses to add「建立新資料夾」because directory creation UX and duplicate/error states are not yet specified.

### 2.6 Task 邊界提示

| Task 候選 | Class / file | 來源 | 正向情境 | 反向情境 | POC |
| --- | --- | --- | --- | --- | --- |
| S014-T01 Backend default folder root BDD | `LocalDirectoryService`, `LocalDirectoryApiTests` | Java Files API + S003 default path | no path request creates/lists `~/.grimo/projects/` under temporary home | invalid path still returns user-readable error; files are not listed | not required |
| S014-T02 Frontend folder browser modal BDD | `Projects.tsx`, `project-api.ts`, `project-types.ts`, `project-management.ui.spec.ts` | user request + S012/S013 findings | click `選擇資料夾` opens modal, browses folders, fills input | API error keeps form values; native endpoint not called | not required |
| S014-T03 Full-stack selected path create BDD | `project-onboarding.fullstack.spec.ts` | S003 `projectPath` contract | real `/api/local-directories` selected path submits/persists as `projectPath` | request lacks browser/source/native fields | not required |
| S014-T04 Visual/design sync | `styles.css`, snapshots, `frontend-design-context.md`, `architecture.md`, `glossary.md`, maybe ADR | S014 flow contract | modal looks like compact file picker and does not overlap desktop/mobile | no long directory tree on create page; Swing not documented as primary UX | not required |

## 3. BDD Contract

驗證命令：

執行：`scripts/verify-release.sh`
通過條件：所有帶 `S014` AC id 的 scenario 都有對應 test evidence，且標記為 `@state:verified` 前必須通過 release gate；開發中可先用 `./gradlew test`、`npm run test:visual`、`npm run test:fullstack` 分層驗證。

AC 覆蓋矩陣：

| AC | 使用者結果 | Contract / 可觀察輸出 | Layer | State |
| --- | --- | --- | --- | --- |
| AC-S014-1 | 使用者按 `選擇資料夾` 後看到 Grimo 內建 folder browser，而不是 Swing dialog 或手動貼路徑 fallback。 | UI 顯示 modal title `選擇 Project 資料夾`，並呼叫 `GET /api/local-directories`。 | frontend, fullstack | proposed |
| AC-S014-2 | Folder browser 預設從 `~/.grimo/projects/` 開始，缺資料夾時 backend 會建立它，並回傳此實體位置底下的資料夾候選；此 root 本身不可被選成 Project Path。 | `GET /api/local-directories` without `path` returns `path=<temp-home>/.grimo/projects` and filesystem-backed `directories[]`；不查 DB Project records；UI at root disables `使用此資料夾`。 | backend, api, frontend | proposed |
| AC-S014-3 | 使用者可在 modal 裡進入子資料夾、回上層、回家目錄、回 Grimo 預設位置。 | `GET /api/local-directories?path=<absolute>` returns updated `path`, `parentPath`, `directories[]`；`location=home` 回家目錄；`location=default` 回 Grimo 預設位置；`path` + `location` 同時出現回 400。 | frontend, fullstack | proposed |
| AC-S014-4 | 使用者點 `使用此資料夾` 後，modal 關閉且 `專案路徑` input 填入 selected absolute path。 | input value equals response `path`; no `POST /api/projects` yet。 | frontend | proposed |
| AC-S014-5 | 建立 Project 時仍只送 backend 可操作的 `projectPath` String。 | `POST /api/projects` body contains `projectPath` and excludes native/browser/source fields；created/listed Project reads back same path。 | fullstack | proposed |
| AC-S014-6 | Directory listing error 不會破壞使用者已填的表單。 | modal 顯示 error；`name`, `description`, `projectPath`, `workflowRecipeId` 保留。 | frontend | proposed |
| AC-S014-7 | S014 不讀檔、不執行 shell、不用 browser handle。 | API response only includes directory names/paths; UI/native endpoint request count zero; no `FileSystemDirectoryHandle` field. | backend, frontend, security | proposed |
| AC-S014-8 | Modal 在 desktop/tablet/mobile 不遮住不可恢復操作，文字不溢出。 | deterministic visual snapshots pass for required viewport set。 | frontend, visual | proposed |

Feature: Project Path folder browser

### Rule: backend-backed folder listing provides real Project paths

使用者結果：
使用者不用手貼長路徑，也不用 Swing 視窗；按 `選擇資料夾` 後，Grimo 在 app 裡顯示本機資料夾清單，最後把 backend 可操作的 absolute path 填回 `專案路徑`。

Contract:

```http
GET /api/local-directories
200 OK
```

```json
{
  "path": "/Users/samzhu/.grimo/projects",
  "parentPath": "/Users/samzhu/.grimo",
  "directories": [
    {
      "name": "grimoAPP",
      "path": "/Users/samzhu/.grimo/projects/grimoAPP"
    },
    {
      "name": "skills-hub",
      "path": "/Users/samzhu/.grimo/projects/skills-hub"
    }
  ]
}
```

Empty directory:

```json
{
  "path": "/Users/samzhu/.grimo/projects/empty",
  "parentPath": "/Users/samzhu/.grimo/projects",
  "directories": []
}
```

Error:

```http
GET /api/local-directories?path=/Users/samzhu/.grimo/projects/README.md
400 Bad Request
```

```json
{
  "error": "請選擇有效的本機資料夾"
}
```

Shortcut requests:

```http
GET /api/local-directories?location=home
```

```http
GET /api/local-directories?location=default
```

Ambiguous request:

```http
GET /api/local-directories?path=/Users/samzhu&location=home
400 Bad Request
```

```json
{
  "error": "請選擇一種資料夾位置"
}
```

Field contract:

| 欄位 | 型別/格式 | 規則 | 來源 | 設計理由 | BDD 要驗什麼 |
| --- | --- | --- | --- | --- | --- |
| `path` | absolute path string | backend normalized；empty request uses `~/.grimo/projects/` and ensures it exists | `LocalDirectoryService` | modal current location and selected value | no-path request returns temp home `.grimo/projects`; selected input equals `path` |
| `parentPath` | absolute path string or null | filesystem root 時為 null | `Path.getParent()` | `上層` navigation | root disables/hides upper navigation; non-root can request parent |
| `directories[].name` | string | child basename only | `Path.getFileName()` | row label | files do not appear; two varied directories sorted by name |
| `directories[].path` | absolute path string | backend normalized child path | `Files.list` child path | click row requests next listing | full-stack test navigates at least one child |
| query `location` | `home` / `default` / omitted | cannot appear with `path`; `home` resolves to `user.home`; `default` resolves to `~/.grimo/projects/` and ensures it exists | request query | Finder-like shortcuts without adding modal path input | home/default return different expected roots; `path + location` returns 400 |

```gherkin
@spec:S014
@ac:AC-S014-2
@layer:backend,api
@api:GET /api/local-directories
@state:proposed
Scenario: 預設資料夾不存在時 backend 建立並回傳 ~/.grimo/projects
  Given（前提） 測試用 user.home 底下沒有 ".grimo/projects"
  When（動作） frontend 呼叫 GET /api/local-directories，沒有帶 path
  Then（結果） response.path 是 "<temp-home>/.grimo/projects"
  And（而且） filesystem 上存在 "<temp-home>/.grimo/projects"
  And（而且） response.directories 是本機實體資料夾候選陣列，不包含檔案或 DB Project records
  # 技術證據：LocalDirectoryApiTests 用 temporary user.home 和 child directory/file fixture 驗 status/body/filesystem
```

```gherkin
@spec:S014
@ac:AC-S014-2
@layer:frontend
@api:GET /api/local-directories
@state:proposed
Scenario: default root 是瀏覽起點，不是可選 Project Path
  Given（前提） folder browser 目前位置是 "<temp-home>/.grimo/projects"
  When（動作） 使用者查看 modal action
  Then（結果） "使用此資料夾" disabled 或顯示不可選原因
  And（而且） "專案路徑" input 不會被填入 "<temp-home>/.grimo/projects"
  And（而且） 使用者可以關閉 modal 並保持 "專案路徑" 空白，讓 Grimo 建立 "<temp-home>/.grimo/projects/<projectId>"
```

```gherkin
@spec:S014
@ac:AC-S014-3
@layer:backend,api
@api:GET /api/local-directories
@state:proposed
Scenario: home 和 Grimo default shortcut 回到不同資料夾
  Given（前提） 測試用 user.home 是 "<temp-home>"
  When（動作） frontend 呼叫 GET /api/local-directories?location=home
  Then（結果） response.path 是 "<temp-home>"
  When（動作） frontend 呼叫 GET /api/local-directories?location=default
  Then（結果） response.path 是 "<temp-home>/.grimo/projects"
  And（而且） filesystem 上存在 "<temp-home>/.grimo/projects"
```

```gherkin
@spec:S014
@ac:AC-S014-3
@layer:backend,api
@api:GET /api/local-directories
@state:proposed
Scenario: path 和 location 同時出現時 backend 拒絕模糊 request
  Given（前提） 測試用 user.home 是 "<temp-home>"
  When（動作） frontend 呼叫 GET /api/local-directories?path=<temp-home>&location=home
  Then（結果） response status 是 400
  And（而且） response.error 是 "請選擇一種資料夾位置"
```

驗證綁定：

- backend: `backend/src/test/java/io/github/samzhu/grimo/project/LocalDirectoryApiTests.java`
- command: `cd backend && ./gradlew test --tests '*LocalDirectoryApiTests'`

### Rule: folder browser modal fills the existing Project Path field

使用者結果：
使用者在 modal 中瀏覽資料夾並選定目前位置後，回到同一個新增 Project 表單；只有 `專案路徑` input 改成 selected absolute path，Project 不會被提前建立。

```gherkin
@spec:S014
@ac:AC-S014-1
@layer:frontend
@api:GET /api/local-directories
@state:proposed
Scenario: 使用者按選擇資料夾後看到 Grimo folder browser modal
  Given（前提） 使用者位於 "新增專案" 頁
  When（動作） 使用者按 "選擇資料夾"
  Then（結果） 使用者看到 modal heading "選擇 Project 資料夾"
  And（而且） modal 顯示目前位置 "/Users/samzhu/.grimo/projects"
  And（而且） frontend 呼叫 GET /api/local-directories
  And（而且） frontend 沒有呼叫 POST /api/native-folder-dialogs/project-path
```

```gherkin
@spec:S014
@ac:AC-S014-3
@layer:frontend,fullstack
@api:GET /api/local-directories
@state:proposed
Scenario: 使用者在 modal 裡進入子資料夾再回上層
  Given（前提） folder browser 目前列出 "grimoAPP" 子資料夾
  When（動作） 使用者點 "grimoAPP" row 的 "進入"
  Then（結果） modal 目前位置變成 "/Users/samzhu/.grimo/projects/grimoAPP"
  When（動作） 使用者點 "上層"
  Then（結果） modal 目前位置回到 "/Users/samzhu/.grimo/projects"
  # 技術證據：full-stack test 用 temporary home 和 real /api/local-directories 驗 URL/request/visible path
```

```gherkin
@spec:S014
@ac:AC-S014-4
@layer:frontend
@state:proposed
Scenario: 使用者使用目前資料夾後只回填專案路徑
  Given（前提） folder browser 目前位置是 "/Users/samzhu/.grimo/projects/grimoAPP"
  When（動作） 使用者點 "使用此資料夾"
  Then（結果） modal 關閉
  And（而且） "專案路徑" input 顯示 "/Users/samzhu/.grimo/projects/grimoAPP"
  And（而且） 尚未送出 POST /api/projects
```

```gherkin
@spec:S014
@ac:AC-S014-6
@layer:frontend
@api:GET /api/local-directories
@state:proposed
Scenario: 資料夾讀取失敗時表單內容不遺失
  Given（前提） 使用者已填 "專案名稱" 為 "grimoAPP"
  And（而且） 使用者已填 "專案描述" 為 "本機 AI 開發工作台"
  And（而且） 使用者已填 "專案路徑" 為 "/Users/samzhu/.grimo/projects/grimoAPP"
  When（動作） folder browser request 回 400 和 "請選擇有效的本機資料夾"
  Then（結果） modal 顯示 "請選擇有效的本機資料夾"
  And（而且） Project Creation form 的三個欄位值都保留
  And（而且） frontend 沒有呼叫 POST /api/native-folder-dialogs/project-path
```

驗證綁定：

- frontend: `frontend/e2e/project-management.ui.spec.ts`
- fullstack: `frontend/e2e/project-onboarding.fullstack.spec.ts`
- command: `cd frontend && npm run test:visual -- project-management.ui.spec.ts --grep "AC-S014"`；`cd frontend && npm run test:fullstack -- project-onboarding.fullstack.spec.ts --grep "AC-S014"`

### Rule: Project creation keeps the existing projectPath API shape

使用者結果：
選資料夾只是幫使用者填 path；建立 Project 時，backend 仍收到可操作的 `projectPath` 字串，沒有 browser handle，也沒有 Swing/native endpoint 的資料。

Contract:

```http
POST /api/projects
Content-Type: application/json
```

```json
{
  "name": "grimoAPP",
  "description": "本機 AI 開發工作台",
  "projectPath": "/Users/samzhu/.grimo/projects/grimoAPP",
  "workflowRecipeId": "web-service-development"
}
```

Forbidden fields:

```json
{
  "workspacePath": "forbidden",
  "folderPath": "forbidden",
  "projectPathSource": "forbidden",
  "browserProjectPathKey": "forbidden",
  "FileSystemDirectoryHandle": "forbidden",
  "nativeFolderDialog": "forbidden"
}
```

```gherkin
@spec:S014
@ac:AC-S014-5
@layer:fullstack
@api:POST /api/projects
@state:proposed
Scenario: folder browser 選到的 path 以 projectPath 建立 Project
  Given（前提） "專案路徑" input 已由 folder browser 填入 "/Users/samzhu/.grimo/projects/grimoAPP"
  When（動作） 使用者按 "建立專案"
  Then（結果） POST /api/projects request body 包含 "projectPath"
  And（而且） request body 不包含 workspacePath、folderPath、projectPathSource、browserProjectPathKey、FileSystemDirectoryHandle 或 nativeFolderDialog
  And（而且） GET /api/projects 讀回同一個 projectPath
```

```gherkin
@spec:S014
@ac:AC-S014-7
@layer:backend,frontend,security
@state:proposed
Scenario: folder browser 不讀檔、不執行 shell、不使用 browser handle
  Given（前提） test fixture 有一個 child directory 和一個 README.md file
  When（動作） 使用者打開 folder browser
  Then（結果） API response 只列 child directory
  And（而且） response 不包含 file content、file count、shell output 或 FileSystemDirectoryHandle
  And（而且） frontend 沒有呼叫 POST /api/native-folder-dialogs/project-path
```

驗證綁定：

- backend: `backend/src/test/java/io/github/samzhu/grimo/project/LocalDirectoryApiTests.java`
- fullstack: `frontend/e2e/project-onboarding.fullstack.spec.ts`
- command: `scripts/verify-release.sh`

### Rule: folder browser layout remains usable across viewports

```gherkin
@spec:S014
@ac:AC-S014-8
@layer:frontend,visual
@state:proposed
Scenario: modal 在桌面和平板手機版都可用
  Given（前提） Project Creation Page 顯示 folder browser modal
  When（動作） visual tests capture 1366x768, 1440x900, 820x1180, 390x844
  Then（結果） modal 內 path、directory rows、關閉、上層、使用此資料夾按鈕不重疊
  And（而且） page-level "建立專案" 不被誤認為 modal primary action
```

驗證綁定：

- frontend visual: `frontend/e2e/project-management.ui.spec.ts`
- command: `cd frontend && npm run test:visual -- project-management.ui.spec.ts --grep "AC-S014"`

### 非功能需求檢查

| 分類 | 對應驗收 | 說明 |
| --- | --- | --- |
| Performance | AC-S014-2, AC-S014-3 | 只列 immediate child directories，不遞迴掃描，避免大型 repo/root 卡住 UI。 |
| Security | AC-S014-7 | 不讀檔案內容、不執行 shell、不傳 browser handle；MVP 仍 permit-all local dev surface。 |
| Reliability | AC-S014-2, AC-S014-6 | default root 不存在時自動建立；invalid/unreadable path 顯示可恢復錯誤且不清 form。 |
| Usability | AC-S014-1, AC-S014-4, AC-S014-8 | 不使用 Swing；modal 提供 file-picker-like flow；選定後只填 path。 |
| Maintainability | AC-S014-5, AC-S014-7 | 保留 `projectPath` API shape，移除 UI 對 native dialog endpoint 的依賴。 |

## 4. 介面與 API 設計

### Backend

```java
@GetMapping("/local-directories")
LocalDirectoryResponse listLocalDirectories(
		@RequestParam(required = false) String path,
		@RequestParam(required = false) String location
)
```

```java
public record LocalDirectoryResponse(
		String path,
		String parentPath,
		List<LocalDirectoryEntryResponse> directories
) {}

public record LocalDirectoryEntryResponse(String name, String path) {}
```

`LocalDirectoryService` rules:

```java
private Path resolvePath(String requestedPath, String requestedLocation) {
	if (requestedPath != null && !requestedPath.isBlank()
			&& requestedLocation != null && !requestedLocation.isBlank()) {
		throw new IllegalArgumentException("請選擇一種資料夾位置");
	}
	if ("home".equals(requestedLocation)) {
		return homeRoot();
	}
	if ("default".equals(requestedLocation)
			|| requestedPath == null || requestedPath.isBlank()) {
		return defaultProjectRoot();
	}
	if (requestedLocation != null && !requestedLocation.isBlank()) {
		throw new IllegalArgumentException("請選擇有效的本機資料夾");
	}
	return Path.of(requestedPath.trim()).toAbsolutePath().normalize();
}

private Path homeRoot() {
	return Path.of(System.getProperty("user.home"))
			.toAbsolutePath()
			.normalize();
}

private Path defaultProjectRoot() {
	Path path = Path.of(System.getProperty("user.home"), ".grimo", "projects")
			.toAbsolutePath()
			.normalize();
	Files.createDirectories(path);
	return path;
}
```

Validation/default rules:

| Rule | Result | BDD |
| --- | --- | --- |
| `path` missing/blank | create/list `~/.grimo/projects/` | AC-S014-2 |
| `location=default` | create/list `~/.grimo/projects/` | AC-S014-2, AC-S014-3 |
| `location=home` | list `user.home` | AC-S014-3 |
| `path` and `location` both present | `400 {"error":"請選擇一種資料夾位置"}` | AC-S014-3 |
| unsupported `location` | `400 {"error":"請選擇有效的本機資料夾"}` | AC-S014-6 |
| `path` exists, is readable directory | list immediate readable child directories | AC-S014-3 |
| `path` missing/file/unreadable | `400 {"error":"請選擇有效的本機資料夾"}` | AC-S014-6 |
| child is file | omitted from `directories[]` | AC-S014-7 |
| child is unreadable | omitted from `directories[]` | AC-S014-7 |

### Frontend

Existing type remains:

```ts
export type LocalDirectoryQuery =
  | { path?: string; location?: never }
  | { location?: "home" | "default"; path?: never };

export type LocalDirectoryListing = {
  path: string;
  parentPath: string | null;
  directories: LocalDirectoryEntry[];
};
```

New UI state shape inside `Projects.tsx` can stay component-local:

```ts
type FolderBrowserState = {
  isOpen: boolean;
  isLoading: boolean;
  error: string;
  listing: LocalDirectoryListing | null;
};
```

Frontend behavior:

- `選擇資料夾` calls `listLocalDirectories()` with no query.
- `進入` calls `listLocalDirectories({ path: entry.path })`.
- `上層` calls `listLocalDirectories({ path: listing.parentPath })`.
- `回 Grimo 預設位置` calls `listLocalDirectories({ location: "default" })`.
- `回家目錄` calls `listLocalDirectories({ location: "home" })`.
- `使用此資料夾` sets `form.projectPath = listing.path` and closes modal.
- Native dialog API client/types can be removed if no other caller remains.

### Storage

N/A — S014 不新增或修改 DB table。Project 建立後仍由 existing `projects.workspace_path` internal column 保存 public `projectPath`。

### ADR / Architecture

S014 overwrites ADR-005 as the single current Project Path picker decision. ADR-005 now records that the Swing native dialog direction was replaced by the backend-backed Grimo Folder Browser, and that Swing / OS folder chooser is not an S014 fallback.

## 5. 檔案規劃

| 檔案 | 動作 | 說明 |
| --- | --- | --- |
| `docs/grimo/specs/spec-roadmap.md` | modify | 新增 S014 active row。 |
| `docs/grimo/specs/2026-06-12-S014-project-path-folder-browser.md` | new | 本 spec sections 1-5。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/LocalDirectoryService.java` | modify | default root 改為 `~/.grimo/projects/` 並建立 root；保留 path validation/listing。 |
| `backend/src/test/java/io/github/samzhu/grimo/project/LocalDirectoryApiTests.java` | modify | 增加 default root creation、files omitted、invalid path cases。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/NativeFolderDialogController.java` | delete or deprecate | S014 UI 不再使用 native dialog endpoint；若保留，需明確標為 non-primary/internal。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/NativeFolderDialogService.java` | delete or deprecate | 同上。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/NativeFolderDialogGateway.java` | delete or deprecate | 同上。 |
| `backend/src/main/java/io/github/samzhu/grimo/project/SwingNativeFolderDialogGateway.java` | delete or deprecate | 移除 Swing primary UX。 |
| `backend/src/test/java/io/github/samzhu/grimo/project/NativeFolderDialogApiTests.java` | delete or revise | 若 endpoint 刪除，測試移除；若保留 historical/internal code，改成非 primary、非 fallback contract。 |
| `backend/src/main/java/io/github/samzhu/grimo/GrimoApplication.java` | modify if native bridge removed | 移除為 Swing 加的 `setHeadless(false)`，除非另有 AWT use case。 |
| `backend/src/test/java/io/github/samzhu/grimo/GrimoApplicationTests.java` | modify if native bridge removed | 移除 native-dialog headless regression test，保留 context smoke test。 |
| `frontend/src/features/projects/Projects.tsx` | modify | 用 modal folder browser 取代 `openNativeFolderDialog`。 |
| `frontend/src/features/projects/project-api.ts` | modify | 移除 `chooseNativeProjectPath` caller/API if unused；保留並擴充 `listLocalDirectories`，支援 `{ path }` 或 `{ location: "home" | "default" }`。 |
| `frontend/src/domain/project/project-types.ts` | modify | 移除 native dialog request/response types if unused；保留 directory listing types，新增 `LocalDirectoryQuery`。 |
| `frontend/src/styles.css` | modify | 新增/調整 modal、directory rows、responsive styles。 |
| `frontend/e2e/project-management.ui.spec.ts` | modify | AC-S014 modal states、no native endpoint、error/form preservation。 |
| `frontend/e2e/project-onboarding.fullstack.spec.ts` | modify | AC-S014 full-stack listing/select/create path。 |
| `frontend/e2e/*snapshots*` | modify | Intentional visual updates after modal UI changes。 |
| `docs/grimo/architecture.md` | modify | Runtime scenario 改為 Grimo Folder Browser primary UX；S013 native bridge 改 historical/superseded by S014。 |
| `docs/grimo/glossary.md` | modify | 新增/更新 Project Path Folder Browser、Native Folder Dialog Bridge、Local Directory Picker 條目。 |
| `docs/grimo/design/frontend-design-context.md` | modify | 記錄 S014 folder browser UX 決策和 visual evidence。 |
| `docs/grimo/adr/ADR-005-project-path-folder-browser.md` | modify | 覆寫為 S014 Project Path Folder Browser 決策；記錄取代 Swing primary UX，且不提供 native dialog fallback。 |
| `scripts/verify-release.sh` | verify/modify | 若 test labels still mention S013 native dialog，更新 release gate description to S014 folder browser evidence。 |

---

<!-- Sections 6-7 added by /planning-tasks after implementation -->

## 6. Task 規劃

待 `/planning-tasks S014` 產生。

## 7. 實作結果

待 `/planning-tasks S014` 完成後補。
