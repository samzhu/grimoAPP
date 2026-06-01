# Grimo Architecture

**Status:** Living architecture baseline through S002 release
**Last updated:** 2026-06-01

## State At Planning

Repo 目前已有兩個可獨立啟動的 POC surface：

| Surface | Current state | Evidence |
| --- | --- | --- |
| `frontend/` | React + Vite UI POC，已有 Task workbench、Projects、Workflow、Playwright visual snapshots | `frontend/package.json`、`frontend/src/features/*`、`frontend/e2e/task-workbench.visual.spec.ts` |
| `backend/` | Spring Boot skeleton，已有 Pollack AgentWorks / SQLite POC tests，但尚未有 production REST API | `backend/build.gradle.kts`、`backend/src/main/java/io/github/samzhu/grimo/GrimoApplication.java`、`backend/src/test/java/io/github/samzhu/grimo/poc/*` |
| `scripts/verify-release.sh` | 目前 release gate 主要跑 frontend build 與 visual regression | `scripts/verify-release.sh` |

缺口：

- backend 尚未提供 `Project` REST API。
- frontend `Projects` view 仍是 fixture / local UI，不會呼叫 backend。
- `docs/grimo/specs/spec-roadmap.md` 尚未存在；S001 會建立第一個可實作 spec。

## Packaging And Development Target

S001 採用前後端分別啟動，方便開發環境測試：

```mermaid
flowchart LR
  Browser["Browser"] --> Vite["Vite dev server :5173"]
  Vite --> Frontend["React UI"]
  Frontend --> Api["/api/projects"]
  Vite -- "dev proxy" --> Backend["Spring Boot :8080"]
  Backend --> Store["SQLite local database"]
```

開發期：

- Frontend: `npm --prefix frontend run dev`
- Backend: `cd backend && ./gradlew bootRun`
- API calls: frontend 使用相對路徑 `/api/...`，由 Vite `server.proxy` 轉到 backend。

未決：

- Production packaging 尚未決定，不在 S001 內解決。
- Spring Boot serve frontend build、desktop installer、container image 都保留給後續 spec。

## arc42 Runtime And Environment View — Project Creation

arc42 的 runtime view 用具體情境描述 building blocks 在執行時如何互動；deployment view 則補足需要知道的基礎設施與環境節點。S002 只需要描述 Project Creation 的開發期本機 runtime，不決定 production packaging。

### Environment Nodes

```mermaid
flowchart LR
  User["Human user"]
  Browser["Local browser\nReact UI"]
  Vite["Vite dev server\n127.0.0.1:5173"]
  Backend["Spring Boot MVC\n127.0.0.1:8080"]
  SQLite["SQLite local DB"]
  Workspace["Project workspace\n/Users/.../repo"]
  LocalDirs["Local filesystem directories"]

  User --> Browser
  Browser --> Vite
  Vite -- "/api proxy" --> Backend
  Backend --> SQLite
  Backend -- "GET /api/local-directories" --> LocalDirs
  Backend -. "future Task execution uses stored workspacePath" .-> Workspace
```

Runtime facts for S002:

- Grimo is executed locally; the browser is only the human operator UI.
- Frontend and backend are still started separately in development.
- S003 changes `POST /api/projects.workspacePath` from a required user-provided path into a workspace binding result. If the user does not provide an existing path, backend creates a Grimo-managed workspace under `~/.grimo/projects/<projectId>`.
- `GET /api/local-directories` lets the browser choose a local directory path through Spring Boot.
- Backend-owned features can operate `GRIMO_MANAGED` and `LOCAL_PATH` workspaces in later specs. Browser-only handles are not backend-operable until a future native bridge or browser-mediated file flow exists.
- S002 stores `workspacePath` as data only; directory browsing lists immediate child directories but does not read file contents, run shell commands, inspect the repo, or start agent execution.
- No Electron, Tauri, native desktop shell, or OS-level file chooser bridge is assumed in S002.

### Runtime Scenario: Enter Project Creation Page

```mermaid
sequenceDiagram
  actor User as Human user
  participant Browser as Browser / React UI
  participant Vite as Vite dev server
  participant API as Spring Boot MVC
  participant Catalog as WorkflowRecipeCatalog

  User->>Browser: Click feature-list 建立專案
  Browser->>Vite: GET /api/workflow-recipes
  Vite->>API: proxy GET /api/workflow-recipes
  API->>Catalog: list()
  Catalog-->>API: CollectionResponse<WorkflowRecipeResponse>
  API-->>Vite: 200 OK JSON
  Vite-->>Browser: 200 OK JSON
  Browser->>Browser: Render Project Creation Page
  Browser->>Browser: Show 專案名稱, 專案描述, 專案工作區, 專案工作流, 參與角色
```

### Runtime Scenario: Submit Project Creation

```mermaid
sequenceDiagram
  actor User as Human user
  participant Browser as Browser / React UI
  participant Vite as Vite dev server
  participant API as Spring Boot MVC
  participant Service as ProjectService
  participant Catalog as WorkflowRecipeCatalog
  participant Store as ProjectStore
  participant DB as SQLite

  User->>Browser: Fill name, description, workspacePath, workflow
  User->>Browser: Click page-level 建立專案
  Browser->>Vite: POST /api/projects JSON
  Vite->>API: proxy POST /api/projects JSON
  API->>Service: createProject(request)
  Service->>Catalog: findById(workflowRecipeId)
  Catalog-->>Service: recipe with preconfigured roles
  Service->>Store: create Project + Project workflow role settings
  Store->>DB: INSERT projects
  Store->>DB: INSERT project_workflow_roles
  DB-->>Store: persisted rows
  Store-->>Service: Project record + role settings
  Service-->>API: ProjectResponse
  API-->>Vite: 201 Created JSON
  Vite-->>Browser: 201 Created JSON
  Browser->>Browser: Show created Project and selected workflow roles
```

### Runtime Scenario: Choose Project Workspace

```mermaid
sequenceDiagram
  actor User as Human user
  participant Browser as Browser / React UI
  participant Vite as Vite dev server
  participant API as Spring Boot MVC
  participant FS as Local filesystem

  User->>Browser: Click 選擇資料夾
  Browser->>Vite: GET /api/local-directories
  Vite->>API: proxy GET /api/local-directories
  API->>FS: list immediate child directories
  FS-->>API: directory entries
  API-->>Vite: 200 OK JSON
  Vite-->>Browser: 200 OK JSON
  Browser->>Browser: Render folder picker
  User->>Browser: Choose /Users/samzhu/workspace/github-samzhu/grimoAPP
  Browser->>Browser: Fill 專案工作區 with selected path
```

## Module Map

| Module | Responsibility | Rule |
| --- | --- | --- |
| `frontend/src/features/projects` | Project list、Project create form、current project selection UI | 不直接寫 fixture 作為真狀態；透過 frontend API client 呼叫 backend。 |
| `frontend/src/domain/project` | Project frontend type、request/response shape、workflow recipe labels | 對齊 backend API 欄位名稱。 |
| `frontend/src/shared/api` | Thin fetch client | 只處理 HTTP request/response 與 user-readable error，不放 domain rule。 |
| `backend/src/main/java/io/github/samzhu/grimo/project` | Project domain、repository、service、REST controller | 第一個 production backend package。 |
| `backend/src/main/resources` | SQLite datasource、schema migration 設定 | 本地 DB path 必須可覆寫，測試不得碰使用者真資料。 |

## Framework Dependency Table

| Package | Version | Primary import / module | Verified |
| --- | --- | --- | --- |
| Java | 25 | Gradle toolchain `JavaLanguageVersion.of(25)` | build file |
| Spring Boot Gradle plugin | 4.0.6 | `org.springframework.boot` | build file + official docs |
| Spring dependency management plugin | 1.1.7 | `io.spring.dependency-management` | build file |
| Spring Web MVC | managed by Spring Boot 4.0.6 | `org.springframework.boot:spring-boot-starter-webmvc`, `org.springframework.web.bind.annotation.RestController` | official Spring Boot 4 migration / web docs |
| Spring Web MVC Test | managed by Spring Boot 4.0.6 | `org.springframework.boot:spring-boot-starter-webmvc-test`, `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` | official Spring Boot 4 testing docs |
| Spring Validation | managed by Spring Boot 4.0.6 | `jakarta.validation.Valid`, `jakarta.validation.constraints.*` | official Spring MVC validation docs |
| Spring Data JDBC | managed by Spring Boot 4.0.6 | `org.springframework.boot:spring-boot-starter-data-jdbc`, `org.springframework.data.repository.CrudRepository` | official Spring Boot SQL docs + Spring Data Relational docs; SQLite requires custom dialect validation |
| Spring JDBC | managed by Spring Boot 4.0.6 | `org.springframework.jdbc.core.simple.JdbcClient`, `NamedParameterJdbcTemplate` | official Spring Boot SQL docs |
| SQLite JDBC | managed by Spring Boot; POC resolved `3.50.3.0` | `org.xerial:sqlite-jdbc` | ADR-001 POC |
| Hypersistence TSID | 2.1.4 | `io.hypersistence:hypersistence-tsid`, `io.hypersistence.tsid.TSID` | ADR-004 + Maven Central |
| AgentWorks BOM | 1.0.12 | `io.github.markpollack:agentworks-bom` | ADR-001 POC |
| React | 19.2.6 | `react` | `frontend/package.json` |
| React DOM | 19.2.6 | `react-dom` | `frontend/package.json` |
| TypeScript | 6.0.3 | `typescript` | `frontend/package.json` |
| Vite | 8.0.14 | `defineConfig` from `vite`; Node engine `^20.19.0 || >=22.12.0` | `frontend/package.json` + npm registry + official Vite docs |
| Playwright | 1.60.0 | `@playwright/test` | `frontend/package.json` |

## Key Architecture Decisions

### A001 — Frontend And Backend Run Separately During Development

Decision: S001 uses separate Vite and Spring Boot dev servers. Vite proxies `/api` to Spring Boot.

Reason:

- Matches the user-confirmed development need: independently start frontend and backend for fast testing.
- Keeps Vite HMR and existing Playwright visual gate intact.
- Avoids premature production packaging decisions.

### A002 — Project Is Backend-Owned

Decision: `Project` is created through backend API and stored in the local backend store. Frontend state reflects backend responses.

Reason:

- PRD says Project owns Task, workflow recipe and evidence.
- Local-first references say Grimo local store is the source of truth.
- A frontend-only project list would not prove the first full-stack capability.

### A002a — SQLite Persistence Should Avoid Premature ORM Coupling

Decision: S001 should target SQLite through Spring's JDBC stack. Spring Data JDBC repositories are desirable only if a POC confirms the required SQLite `JdbcDialect`; otherwise use Spring JDBC / `JdbcClient` for the first Project table.

Reason:

- ADR-001 accepted SQLite and Xerial JDBC for local-first persistence.
- Spring Data Relational 4.0 docs list direct Spring Data JDBC dialect support for DB2, H2, HSQLDB, MariaDB, Microsoft SQL Server, MySQL, Oracle and PostgreSQL; SQLite is not listed.
- The same docs state that unsupported databases need a `JdbcDialect` implementation, otherwise the application will not start.

### A003 — Workflow Recipe Selection Is Project-Level

Decision: S001 exposes workflow selection while creating Project; Task creation does not select workflow.

Reason:

- PRD and glossary define `Workflow Recipe` as Project-level.
- `Task` inherits Project workflow; this prevents recipe chips from leaking into Task labels.

### A004 — Project Workspace Selection Is Browser-First With Grimo-Managed Default

Decision: S003 changes Project Creation's primary folder selection from backend directory browsing to browser-native `showDirectoryPicker()` where supported. If the user does not choose an existing path, backend creates a Grimo-managed workspace under `~/.grimo/projects/<projectId>`. Manual path entry remains the fallback for binding the Project to an existing local repo/path.

Reason:

- The user wants `選擇資料夾` to open the OS-native browser picker instead of rendering a long backend `ls` directory list inside the form.
- The user confirmed that selecting a work path is unrelated to whether a Project can be created; if no path is selected, Grimo creates a managed Project workspace under `~/.grimo`.
- Browser File System Access returns a `FileSystemDirectoryHandle`, not a backend absolute path. Grimo must not pretend a browser-selected handle is a server-operable workspace.
- Manual path validation preserves the option to bind an existing repo/path for future Task / agent execution without requiring a framework change or desktop shell in S003.

## API Shape Baseline

S001 introduces:

```http
GET /api/workflow-recipes
GET /api/projects
POST /api/projects
```

`POST /api/projects` creates a Project and returns the created row, including `id`, `name`, `description`, `workspacePath`, `workflowRecipeId`, `workflowRecipeName`, `status`, `createdAt`, and `updatedAt`.

### REST Response Envelope Standard

Grimo API 使用明確 DTO 作為 response contract，不直接回傳 persistence row / entity，也不直接序列化 Spring Data `PageImpl`。

使用者結果優先說法：

- 使用者只是要看到完整小清單時，例如 Project Creation Page 的 workflow 下拉選單或 Project list，API 回不分頁清單。
- 使用者需要翻頁、排序或看總數時，例如未來 Task list，API 回分頁清單。

#### Non-Paged Collection

用於小型、完整、無 page/size/sort 控制的 collection，例如 `GET /api/workflow-recipes` 和 `GET /api/projects`。

```java
record CollectionResponse<T>(
    List<T> content
) {}
```

JSON shape:

```json
{
  "content": [
    {
      "id": "web-service-development",
      "name": "Web 服務開發"
    },
    {
      "id": "research",
      "name": "研究工作流"
    }
  ]
}
```

規則：

- `content` 永遠存在；沒有資料時回 `content: []`。
- 不放 `page` metadata，避免讓使用者以為清單可分頁。
- 不用 Spring HATEOAS `CollectionModel`、HAL、`_links`。
- BDD 驗證使用 `response.content[*]`，例如 `response.content[*].id`。

#### Paged Collection

用於使用者會翻頁、改 page size、排序，或 UI 需要 total count 的 collection，例如未來 Task list。

```java
record PageResponse<T>(
    List<T> content,
    PageMetadata page
) {}

record PageMetadata(
    int size,
    long totalElements,
    int totalPages,
    int number
) {}
```

JSON shape:

```json
{
  "content": [
    {
      "id": "task_001",
      "title": "建立 Project"
    }
  ],
  "page": {
    "size": 20,
    "totalElements": 43,
    "totalPages": 3,
    "number": 0
  }
}
```

規則：

- 分頁 endpoint 使用 query parameters `page`, `size`, `sort`，語意對齊 Spring Data Web `Pageable`：`page` 從 0 開始，`size` 是每頁筆數，`sort` 表示排序欄位與方向。
- Controller 可以接 Spring Data `Pageable`，service / repository 可以使用 Spring Data `Page<T>`，但 response 必須轉成 Grimo-owned `PageResponse<T>` 或等價 DTO；不要直接回傳 `PageImpl`。
- `page` 永遠存在；沒有資料時仍回 `content: []`，且 `page.totalElements = 0`。
- 暫不啟用 Spring HATEOAS / HAL；需要 `_links` 或 hypermedia navigation 時另開 ADR。

Decision source:

- ADR-002 records the non-paged collection envelope, paged response shape and no-HATEOAS decision.
- Spring Data Commons documents `PagedModel` as a stable wrapper for `Page` and warns against directly serializing `PageImpl`.
- Spring Data REST collection resources add pagination links and metadata only when the repository has paging capabilities.

## QA Baseline

S001 must extend verification beyond frontend-only checks:

- backend unit/API tests run through Gradle.
- frontend build and Playwright tests continue through npm.
- full-stack Project creation needs a browser test that exercises real Vite -> backend API wiring.

The canonical strategy lives in `docs/grimo/qa-strategy.md`.
