# Grimo Development Standards

## Frontend Scope

這份標準目前先約束 `frontend/` React + Vite POC。它的目標是讓 OpenDesign prototype 能穩定轉成產品 UI，而不是讓 `frontend/src/App.tsx` 繼續膨脹。

## Backend Scope

`backend/` 是 Spring Boot API surface。S001 起，production code 以 domain package 分區，例如 `io.github.samzhu.grimo.project`，同一 package 內保留 controller / service / repository / domain type 的近距離可讀性；不要在第一個功能先拆出泛用 framework layer。

Backend rule：

- REST API 放在 `/api/*`，request / response 使用明確 record，不直接暴露 persistence row / entity。
- Collection response 必須使用 project-owned envelope，不直接回傳 raw array；不分頁清單用 `CollectionResponse<T>`，分頁清單用 `PageResponse<T>`。
- Controller 只處理 HTTP mapping、validation 和 response status；Project 建立規則放在 service。
- 本地持久化以 architecture / ADR-001 的 SQLite path 為準；S001 優先使用 Spring JDBC stack，只有在 POC 驗證 SQLite `JdbcDialect` 後才使用 Spring Data JDBC repository；測試必須使用 temporary database，不碰使用者真資料。
- SQLite schema design 必須遵守 `docs/grimo/references/sqlite-data-modeling.md`：核心資料先正規化，JSON / `[]` 只用於 raw payload、adapter metadata、import/export envelope 或可重建 projection。
- Spring Security 在 MVP 功能開發階段維持 permit-all，不因第一個 API 加入認證阻擋開發流程。
- 新增 backend API 時，至少補 controller/service/repository 對應測試；frontend 呼叫該 API 時，還要補 full-stack browser path。

## API Response Shape

API response shape 要讓 BDD 好驗證，也讓前端不用猜這個 endpoint 回 raw array 還是 page object。

### Non-Paged Collection

使用者要一次看到完整小清單時，例如建立 Project 前讀取 workflow list，使用：

```java
record CollectionResponse<T>(List<T> content) {}
```

JSON:

```json
{
  "content": []
}
```

規則：

- `content` 必填，沒有資料時回空陣列。
- 不放 `page`、`links`、`_links`。
- 不使用 Spring HATEOAS `CollectionModel` / HAL。
- 測試要驗 `$.content`，不要只驗 `$` 是 array。

### Paged Collection

使用者會翻頁、調整每頁筆數、排序，或 UI 需要總筆數時，使用：

```java
record PageResponse<T>(List<T> content, PageMetadata page) {}

record PageMetadata(int size, long totalElements, int totalPages, int number) {}
```

JSON:

```json
{
  "content": [],
  "page": {
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "number": 0
  }
}
```

規則：

- 分頁 request query 使用 `page`, `size`, `sort`，語意對齊 Spring Data Web `Pageable`。
- 不直接回傳 Spring Data `Page`, `PageImpl`, `Slice` 或 persistence entity。
- 若 service 使用 Spring Data `Page<T>`，controller 回傳前要 mapping 成 `PageResponse<T>`。
- Spring Data `PagedModel` 只作為官方設計參考；Grimo MVP 預設不用 HATEOAS / HAL。
- BDD scenario 的 Contract 區塊必須呈現 `content[]` 和 `page` metadata，Verification Bindings 必須驗 `$.content` 與 `$.page.*`。

## Source Of Truth

- 產品語言與行為以 `docs/grimo/PRD.md` 為準。
- BDD 行為規格以 `docs/grimo/bdd-contract.md` 為準；spec 使用框架無關的 BDD Contract，測試實作用 JUnit、MockMvc、Playwright 等各自 idiom 對應。
- UI layout、spacing、component states 以 `docs/grimo/ui/prototype/index.html` 和 `docs/grimo/ui/prototype/DESIGN-HANDOFF.md` 為準。
- 前端 UI/UX 作業流程與設計語言保存規則以 `docs/grimo/design/ui-ux-workflow.md` 為準。
- 可命名的設計決策先放進 `docs/grimo/design/tokens.json`，再映射到 CSS custom properties。
- 可重複的 Webwright review prompt 放在 `docs/grimo/design/webwright-prompts.md`；prompt 必須對應已命名的產品或設計規則。
- 目前前端只可把 prototype 轉成 product UI；不要加入 prototype 沒有、PRD 也沒要求的新產品功能。

## Task Planning From BDD

開 task 時要先回答「這個 task 要證明哪個使用者結果成立」，再決定要改哪些檔案。`/planning-tasks` 或人工開 task 都必須從 spec 的 `## 3. BDD Contract` 和 Verification Bindings 往回切，不從 implementation file list 直接切。

### Verification Shift-Left

驗收條件要在 spec 和 task 階段先講清楚，不能等 `$verifying-quality` 才第一次發現。`$verifying-quality` 的工作是獨立查證與擋 release，不是替前段補寫規格。

`$planning-spec` 必須在交給 `$planning-tasks` 前定義驗收預期：

- 每個 AC 要寫出可觀察結果：API response、DB row、UI text、command output、log line、file content 或人工檢查畫面。
- 每個會改 API、DTO、DB row、event payload、command output、UI form data 或 file format 的 AC，都要在 spec `## 3. BDD Contract` 放資料合約：request/input 範例、response/output 範例，以及每個欄位的型別/格式、規則、來源、設計理由、BDD 要驗什麼。
- 每個新增或修改 DB table 的 spec，都要在 Storage 設計固定附上 table 說明註解與範例資料：table 用途、owner / parent、不可存放的資料、欄位 rationale、realistic sample rows、FK 如何串起來，以及 BDD 要用哪個 persisted read-back 驗證不是硬編碼。
- 系統欄位要明確標出不能由 client 設定，例如 `id`, `state`, `source`, `workflowRecipeId`, `createdAt`；BDD 要驗 request override 不會生效或不會被 DTO 接受。
- 每個 scenario 的 Verification Bindings 要先列出預期測試層：backend、frontend、full-stack、manual、deployment 或 testing infrastructure。
- 如果驗收有前置條件，例如需要特定 profile、temporary database、browser viewport、外部憑證、Cloud Run revision、人工操作步驟，spec 要先寫成 verification condition。
- 每個可被硬編碼通過的 AC，都要先定義 generality expectation：至少一個非範例輸入、邊界/反向案例、property、metamorphic relation、differential check 或 persisted read-back。不要只寫單一 `5 + 5 = 10` 型範例。
- 如果設計會改 API shape、DB schema、UI 流程、command contract、release gate 或測試策略，§2 design、§3 BDD Contract、§4 interface/API、§5 file plan 必須同步更新，不能只改其中一段。
- 如果某個 AC 應該被自動或整合測試驗證，但專案沒有測試基礎設施，先開 testing infrastructure spec 或把它列為前置 task；不要把 feature task 開成「實作完再說」。

`$planning-tasks` 必須把上述驗收預期轉成可執行 task：

- Task plan 要把每個 AC 對到測試檔、驗證命令與 release gate 位置。
- 有條件驗收要變成 task 裡的 `Verification` 前置條件，而不是藏在備註。
- 如果 task 的 BDD 只是一組範例，task 要補 `Generality Probe`：說明要用哪個額外輸入、狀態轉換、讀回檢查或關係式證明功能不是硬編碼測資。
- 測試基礎設施缺口要先開 task 或回到 `$planning-spec` 修正設計；不得產生一組最後必然被 `$verifying-quality` 判定 `BLOCKED-BY-TESTABILITY` 的 feature tasks。
- 如果 task planning 發現 spec 的設計或驗收條件不一致，要停下並回到 `$planning-spec` 修訂，不要替 spec 靜默改方向。

Spec 的 `## 6. Task Plan` 必須先列出 BDD layer split：

| Layer | Task | 主要 AC | 測試檔 | 驗證命令 |
| --- | --- | --- | --- | --- |
| Backend BDD | `SNNN-T01 ...` | `AC-SNNN-*` | `backend/src/test/java/**` | backend test command |
| Frontend BDD | `SNNN-T02 ...` | `AC-SNNN-*` | `frontend/e2e/*.spec.ts` 或 component test | frontend test command |
| Full-stack E2E | `SNNN-T03 ...` | cross-layer AC | `frontend/e2e/*.spec.ts` | full-stack command |

不是每個 spec 都一定要三個 task；只有真的跨 backend、frontend、full-stack 時才拆三層。純 backend spec 可以只有 Backend BDD，純 UI layout spec 可以只有 Frontend BDD + visual evidence。拆 task 的原則是「一個 task 對一組可獨立驗證的 AC」，不是「一個檔案一個 task」。

每個 `docs/grimo/tasks/SNNN-TNN-*.md` 至少要包含：

- `Purpose`：使用者或產品會得到什麼結果。
- `BDD`：從 spec 複製或濃縮對應 scenario，保留 Given/When/Then。
- `Contract Source`：指回 spec 的 AC 或 scenario，例如 `AC-S003-3, AC-S003-4`。
- `Data Contract`：指回 spec 裡的 request/response/DB row 範例和欄位設計表；如果 task 實作會新增或改欄位，先回 spec 修正，不在 task 裡偷偷發明。
- `Target Tests`：明確列出要新增或修改的測試檔。
- `Verification Conditions`：驗收前置條件，例如 profile、temporary database、viewport、外部憑證、人工步驟或 release gate 是否已接入。
- `Generality Probe`：至少一個不在 BDD 範例裡的輸入、資料狀態、變形關係或 read-back 檢查，用來防止 production code 只回固定答案。
- `Verification`：列出可重跑 command；backend/API task 必須包含 backend test command，frontend-to-backend task 必須包含 full-stack command。
- `Result`：實作後記錄 RED/GREEN evidence、實際 command、是否接進 `scripts/verify-release.sh`。

後端 task 的預設寫法：

- Task 名稱帶 `Backend ... BDD`，讓人一眼知道它是 backend contract task。
- BDD scenario 對應 JUnit 5 test，`@DisplayName` 保留 AC id。
- `Given` 使用 temporary database、fixture 或現有 row。
- `When` 使用 MockMvc/WebTestClient/API request。
- `Then` 驗 HTTP status、JSON shape、database state；不要只驗 service method 回傳值。

前端和 full-stack task 的預設寫法：

- UI-only 行為用 Playwright 或 component test 驗使用者看得到的狀態。
- 只要前端呼叫 `/api`，就要有 full-stack E2E task 或在同一 task 中明確加入 full-stack command。
- 長流程用 `test.step()` 保留 Given/When/Then 可讀性，並讓 report 能對回 scenario。

開 task 時不要把「接 release gate」藏在最後。如果某層測試是 shipping 必要條件，task plan 要明確指出該 command 是否已經在 `scripts/verify-release.sh` 內；若尚未接入，必須開 release gate / verification task 補上。

## Frontend Architecture

- `App.tsx` 只保留 app shell、providers、route/view composition。
- Domain type、fixture、selector 放到 `src/domain/*`。
- 產品 surface 放到 `src/features/*`，以 PRD/prototype 語言命名，例如 `task-board`、`task-detail`、`task-create`、`task-forming-chat`、`projects`、`blockers`、`workflow`。
- API client 放到 `src/shared/api` 或 feature-local client；第一個使用點可保持 feature-local，重複三次以上再抽 shared helper。
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

### BDD Contract

- Spec 的 BDD section 必須用 `Feature / Rule / Scenario / Given / When / Then` 描述可觀察行為，不寫框架細節。
- 每個 scenario 必須帶 `@spec`, `@ac`, `@layer`, `@state` metadata；REST 行為另外帶 `@api`。
- 每個 scenario 必須有 Verification Bindings，指出 backend/frontend/full-stack/manual 由哪些測試或指引驗證。
- 後端測試用 JUnit/MockMvc/WebTestClient idiom 實作 contract；前端與 full-stack 測試用 Playwright idiom 實作 contract。
- 不因為使用 BDD 就預設引入 Cucumber；只有當 `.feature` 檔成為跨角色協作 artifact 時才評估導入。

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
