# Grimo — 架構說明

**狀態：** v0.1 · **負責人：** Tech Lead (samzhu) · **日期：** 2026-04-16
**來源：** `docs/grimo/PRD.md` (v0.1)

---

## 1. 架構風格

- **模組化單體（Modular monolith）**，採用 **Spring Modulith 2.0.5**。每個限界上下文都是頂層套件，並在 `package-info.java` 上標記 `@ApplicationModule`。跨模組呼叫只能透過 `@NamedInterface` 套件進行；其他所有套件依 Modulith 慣例視為 `internal`。
- 每個模組內部採用**六邊形架構（Hexagonal / ports & adapters）**。每個模組包含：
  - `domain/` — records、值物件、領域服務。**零 Spring 注解。** 不依賴 Spring classpath 即可編譯。
  - `application/port/in/` — 用例介面（commands / queries）。
  - `application/port/out/` — 儲存庫與閘道介面。
  - `application/service/` — `@Service` 用例實作。模組內唯一感知 Spring 的層。
  - `adapter/in/**` — 控制器、事件監聽器、CLI 命令處理器。
  - `adapter/out/**` — JDBC 儲存庫、CLI 包裝器、Docker 客戶端。
  - `internal/` — 僅供實作使用的輔助類（對兄弟模組隱藏）。
- **跨模組通訊政策（S002 釘定）。** 三種合法模式，其餘均為 Modulith 違規：
  - **A · `core` 型別** — `core` 為 `Type.OPEN`，所有模組可直接 import `io.github.samzhu.grimo.core.domain.*`。**注意：** Modulith 2.0.5 中，即使目標模組為 `OPEN`，消費者若設定了 `allowedDependencies`，仍須顯式列出 `"core"`（S005 §7 發現）。
  - **B · 同步埠呼叫** — 出版者把埠放在 `@NamedInterface("api")` 套件中；消費者必須宣告 `allowedDependencies = { "<publisher>::api" }`。
  - **C · 非同步事件** — 預設模式（PRD P4「廉價未來解耦」）。出版者把事件 record 放在 `<module>/events/` 並標 `@NamedInterface("events")`；消費者用 `@ApplicationModuleListener` 訂閱，宣告 `allowedDependencies = { "<publisher>::events" }`，只看得到事件、看不到內部 service。
  事件機制依賴 `ApplicationEventPublisher` + `@ApplicationModuleListener`。**禁止**跨模組直接 bean 注入或引用 `internal/` 套件。完整三模式表 + 禁止清單見 `development-standards.md` §13。
- **本地單一程序優先。** 一個 Spring Boot 應用、一個本地 JDBC 儲存（預設 H2 檔案模式）、主機上的一個 Docker Daemon。

## 2. 模組地圖（MVP）

> **2026-04-18 更新（v3 藍圖）：** 隨 v3 重新規劃，本節改列 MVP 真正落地的 6 個模組。`agent` 模組在 S007 先用主機 claude，S008 起整合容器化。`subagent` 移至 Backlog（委派 + 工作樹 + 子代理生命週期待容器化 + session + skill 驗證後晉升）。原 v1 模組（`session`、`router`、`memory`、`jury`、`cost`、`web`、`nativeimage`）已移至下方 §2.x「Backlog 模組（晉升時恢復）」附錄。

```
io.github.samzhu.grimo                                   # 根（GrimoApplication）
├── core                                    # @ApplicationModule(type = Type.OPEN)
│   └── domain/ { SessionId, TurnId, TaskId, CorrelationId,
│                 AgentRole, ProviderId, GrimoHomePaths, NanoIds }
│                                           # 共用領域原語（S001）
│                                           # Cost 由未來的成本遙測規格擁有，不在此處
├── sandbox                                 # Sandbox SPI (agent-sandbox-core) + bind-mount 適配器（S003）
├── cli                                     # ContainerizedAgentModelFactory（docker exec wrapper → AgentModel）（S005 ✅）
├── agent                                   # 主代理對話（S007 主機 → S008 容器化 Claude → S009/S010 Gemini/Codex）
├── subagent                                # 委派 + 工作樹 + 子代理生命週期（Backlog，待晉升）
└── skills                                  # SKILL.md 登錄檔 + 預裝至 Agent 容器（S012、S013）
```

`core` 標記為 `@ApplicationModule(type = Type.OPEN)`。**Modulith 2.0.5 行為：** 即使目標模組為 `OPEN`，消費者若設定了 `allowedDependencies`，仍須顯式列出 `"core"`（S005 §7 發現）。因此需要引用 `core` 型別的模組以 `allowedDependencies = { "core" }` 起步，由各自的 owning spec 在第一次跨模組引用時擴充（同步埠透過 `<publisher>::api`，事件透過 `<publisher>::events`，見 §1 與 `development-standards.md` §13）。

### 模組職責與計畫發佈的埠 / 事件（MVP）

> 表格列出**規劃中的**入站埠、出站埠與事件 — 模組於 S002 時除 `core` 外皆為空殼；下表所列項目由各自 owning spec 在其落地時建立，並依 §1 政策加上 `@NamedInterface` 限定。

| 模組 | 入站埠（規劃） | 出站埠（規劃） | 發布事件（規劃） | Owning spec |
| --- | --- | --- | --- | --- |
| `core` | — | — | — | S001 ✅ |
| `sandbox` | — | `Sandbox` SPI（`agent-sandbox-core`）+ bind-mount 適配器 | — | S003 |
| `cli` | `ContainerizedAgentModelFactory`（`@NamedInterface("api")`） | —（WrapperScriptGenerator 內部直接呼叫 docker exec） | `CliUnavailable`、`CliInvocationFailed`（S006 規劃） | S005 ✅ / S006 |
| `agent` | `MainAgentChatUseCase`（`grimo chat` 入口） | S007: 無（主機 claude）；S016: `skills :: api`（投影）；S008+: `cli :: api`（容器化）、`sandbox :: api` | — | S007 → S016 → S008–S010 |
| `subagent` | `DelegateTaskUseCase` | `Sandbox`（SPI）、`WorktreePort`、`AgentCliPort` | `SubagentStarted`、`SubagentCompleted`、`SubagentFailed` | Backlog（v2 S008–S010） |
| `skills` | `SkillRegistryUseCase`、`SkillProjectionUseCase`（`@NamedInterface("api")`） | `SkillStorePort`（檔案系統） | `SkillEnabled`、`SkillDisabled` | S012 / S013 / S016 |

### 2.x Backlog 模組（晉升時恢復）

下列模組原屬 v1 完整藍圖，在 2026-04-16 容器優先 MVP 重新規劃中移至 Backlog（spec-roadmap.md「Backlog」段落）。各項目晉升時，重新進入 `/planning-spec` 走完 grill 循環並建立 `package-info.java`。**保留此清單**讓未來讀者看到「目前停在哪 / 計畫往哪去」。

| Backlog 模組 | 用途 | 從何處晉升 |
| --- | --- | --- |
| `session` | `SessionMemoryAdvisor` 接線 + 持久化對話 session | Backlog「持久化 Session」 |
| `router` | 成本/複雜度感知 CLI 路由 | Backlog「成本路由器」 |
| `memory` | `AutoMemoryTools` 管理 `~/.grimo/memory` | Backlog「AutoMemoryTools 接線」 |
| `jury` | N 路並行審查 | Backlog「評審團」 |
| `cost` | 每輪 token/成本遙測 + `Cost` 領域型別 | Backlog「成本遙測面板」 |
| `web` | Spring MVC + Thymeleaf + HTMX UI | Backlog「Web UI」 |
| `nativeimage` | `GrimoRuntimeHints` 等原生映像加固類別（**`native` 是 Java 保留字，故 Backlog 套件名為 `nativeimage`**） | Backlog「原生映像加固」 |

## 3. 框架依賴表

所有版本固定於 2026-04-16。每個「已驗證」的 **yes** 均有原始碼層級的 spike 為依據（見 `docs/grimo/PRD.md` 風險登錄）。

| 群組 / 套件 | 版本 | 主要 import | 已驗證 |
| --- | --- | --- | --- |
| `org.springframework.boot`（starter-parent） | **4.0.5** | — | yes（使用者提供） |
| `io.spring.dependency-management`（Gradle 外掛） | **1.1.7** | — | yes |
| `org.graalvm.buildtools.native`（Gradle 外掛） | **0.11.5** | — | yes |
| Java toolchain | **25**（LTS，GA 2025-09-16） | — | yes |
| `org.springframework.modulith:spring-modulith-bom` | **2.0.5** | `org.springframework.modulith.core.ApplicationModules` | yes |
| `org.springframework.modulith:spring-modulith-starter-core` | 2.0.5 | （自動） | yes |
| `org.springframework.modulith:spring-modulith-starter-test` | 2.0.5 | `org.springframework.modulith.test.ApplicationModuleTest` | yes |
| `org.springframework.modulith:spring-modulith-events-jdbc` | 2.0.5 | （自動） | yes |
| `org.springframework.modulith:spring-modulith-docs` | 2.0.5 | `org.springframework.modulith.docs.Documenter` | yes |
| `org.springframework.boot:spring-boot-starter-web` | 4.0.5 | `org.springframework.web.bind.annotation.*` | yes |
| `org.springframework.boot:spring-boot-starter-thymeleaf` | 4.0.5 | `org.thymeleaf.TemplateEngine` | yes |
| `org.springframework.boot:spring-boot-starter-jdbc` | 4.0.5 | `org.springframework.jdbc.core.JdbcTemplate` | yes |
| `org.springframework.boot:spring-boot-starter-actuator` | 4.0.5 | — | yes |
| `org.springframework.boot:spring-boot-starter-validation` | 4.0.5 | `jakarta.validation.*` | yes |
| `org.springframework.boot:spring-boot-devtools`（僅開發） | 4.0.5 | — | yes |
| `com.h2database:h2` | 2.3.232 | `org.h2.Driver` | yes |
| `io.github.wimdeblauwe:htmx-spring-boot` | **5.1.0** | `io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxResponse` | yes |
| `io.github.wimdeblauwe:htmx-spring-boot-thymeleaf` | 5.1.0 | Thymeleaf dialect `htmx:*` | yes |
| `org.springaicommunity:spring-ai-agent-utils` | **0.7.0** | `org.springaicommunity.agent.tools.SkillsTool.Skill`（S012 Skill 登錄檔產出型別）；`org.springaicommunity.agent.tools.SkillsFunction`（S013 ToolCallback 整合）。Grimo 重寫 `Skills` + `MarkdownParser`（同介面，SnakeYAML 內部） | yes — S012 POC 8/8 通過 |
| `org.springaicommunity.agents:agent-client-core` | **0.12.2** | `org.springaicommunity.agents.client.AgentClient` | yes |
| `org.springaicommunity.agents:agent-model` | 0.12.2 | `org.springaicommunity.agents.model.AgentModel` | yes |
| `org.springaicommunity.agents:agent-claude` | 0.12.2 | `org.springaicommunity.agents.claude.ClaudeAgentModel` | yes |
| `org.springaicommunity.agents:agent-codex` | 0.12.2 | `org.springaicommunity.agents.codex.CodexAgentModel` | yes |
| `org.springaicommunity.agents:agent-gemini` | 0.12.2 | `org.springaicommunity.agents.gemini.GeminiAgentModel` | yes |
| `org.springaicommunity:spring-ai-starter-session-jdbc` | **0.2.0** | `org.springframework.ai.session.advisor.SessionMemoryAdvisor`；`org.springframework.ai.session.SessionService` | yes（與 agent-client 獨立發版；需要 Spring AI ≥ 2.0.0-M4） |
| `org.springaicommunity:spring-ai-session-management` | 0.2.0 | `org.springframework.ai.session.compaction.*` | yes |
| `org.springaicommunity:agent-sandbox-core` | **0.9.1** | `org.springaicommunity.sandbox.Sandbox`（SPI）、`ExecSpec`、`ExecResult`、`SandboxFiles` — **S003 的沙箱埠介面**（D9 修訂） | yes — Maven Central 上的 groupId 為 `org.springaicommunity`（非 `.agents`） |
| `org.springaicommunity:agent-sandbox-docker` | **0.9.1** | `org.springaicommunity.sandbox.docker.DockerSandbox` — **S003 不使用**（建構子啟動容器，無 bind-mount 鉤點）；僅供不需 bind-mount 的短暫檔案複製情境 | yes — 同上 group |
| `org.testcontainers:testcontainers` | 1.20.4 | `org.testcontainers.containers.GenericContainer` | yes（僅 JVM；`@DisabledInNativeImage`） |
| `org.eclipse.jgit:org.eclipse.jgit` | 7.1.1.202506271520-r | `org.eclipse.jgit.api.Git`（worktree add/remove） | yes |
| `org.springframework.boot:spring-boot-starter-test` | 4.0.5 | （自動） | yes |
| `org.testcontainers:junit-jupiter` | 1.20.4 | `@Testcontainers` | yes（僅測試 classpath） |
| `com.tngtech.archunit:archunit-junit5` | 1.3.0 | 透過 Modulith verify API | yes（傳遞引入） |

> **原則提醒：** 這裡的每個版本都是截至 2026-04-16 針對 Boot 4.0.5 驗證過的最新穩定版。`spring-ai-session` 設計上為 pre-1.0 — 固定於 `0.2.0` 發版；不追蹤 `0.3.0-SNAPSHOT`。

### 禁用 / 拒絕的函式庫

| 函式庫 | 原因 |
| --- | --- |
| `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect` | Groovy 執行期 → GraalVM native 阻礙（D16）。請改用帶參數的 Thymeleaf fragments。 |
| `spring-boot-starter-webflux`（用於 web adapter 路徑） | 函式式路由上的 AOT 回歸問題（R1）；保持使用 MVC + virtual threads。 |
| 基於 `cglib` 的手動代理 | Boot 4 native 不支援執行期 CGLIB（#49350）。 |
| 原生映像內的 Testcontainers | 執行期非原生安全；使用時請以 `@DisabledInNativeImage` 包裝。 |

## 4. 執行期與並發

- **Virtual threads 預設啟用**，透過 `spring.threads.virtual.enabled=true`（Spring Boot 4 一等支援）。阻塞 I/O（ProcessBuilder、JDBC、HTTP）在 virtual carrier 上執行。JEP 491 在 JDK 24+ 上消除了 `synchronized` 固定問題，GraalVM for JDK 25 繼承此特性，無 carrier 饑餓問題。
- **串流路徑：** Spring AI `ChatClient.stream().content()` 回傳 `Flux<String>`；由 `adapter/in/web/ChatSseController` 橋接至 `SseEmitter`。HTMX 頁面透過 `<div hx-ext="sse" sse-connect="..." sse-swap="token">` 消費 — 無需自訂 JS。
- **子代理派送：** 每任務一個 virtual thread 執行器；同時最多 `grimo.subagent.max-concurrent`（預設 2）個沙箱容器，以限制主機資源壓力。
- **超時：** 每個出站呼叫都有明確的 `Duration` 超時，透過 `grimo.cli.<provider>.timeout`（預設 5m）設定並傳遞給 `AgentClient` 建構器。

## 5. 儲存與檔案系統配置

### 5.1 GrimoHome（`~/.grimo/`）

```
~/.grimo/
├── config/
│   └── application.yml          # 使用者覆寫（由 Boot 合併）
├── db/
│   └── grimo.mv.db              # H2 檔案模式 DB（session、events、cost）
├── memory/
│   ├── MEMORY.md                # 索引（AutoMemoryTools 慣例）
│   └── <topic>.md               # 使用者/回饋/專案/參考條目
├── skills/
│   └── <skill-name>/
│       └── SKILL.md             # skill 定義
├── sessions/                    # 使用者端 session 匯出（可選）
├── worktrees/
│   └── <task-id>/               # 一個子代理任務的 git worktree
└── logs/
    └── grimo.log
```

`GrimoHomePaths`（在 `core` 中）是路徑解析的唯一權威；其他模組不得直接從 `System.getProperty("user.home")` 建構路徑。

### 5.2 關聯式儲存

- **預設：** H2 檔案模式 — `jdbc:h2:file:~/.grimo/db/grimo;MODE=PostgreSQL;AUTO_SERVER=TRUE`
- **可選：** 透過設定 `grimo.datasource.url`（以及 `username` / `password`）切換至 PostgreSQL — session-jdbc 有對應的 `PostgresJdbcSessionRepositoryDialect`。
- **資料表**（由 `spring-ai-session-jdbc` 的 schema.sql 管理）：`AI_SESSION`、`AI_SESSION_EVENT`（含主鍵/索引定義）。
- **Modulith 事件發佈資料表**，由 `spring-modulith-events-jdbc` 管理：`event_publication` + `event_publication_archive`。
- **Grimo 自有資料表**（由 Flyway 管理，位於 `src/main/resources/db/migration/`）：
  - `grimo_cost` — 每輪 token + 美元遙測（欄位：`turn_id`、`session_id`、`provider`、`tokens_in`、`tokens_out`、`usd_cents`、`created_at`）。
  - `grimo_subagent_task` — 任務生命週期（`id`、`session_id`、`worktree_path`、`status`、`started_at`、`finished_at`、`exit_code`）。
  - `grimo_skill_proposal` — 待審蒸餾提案（`id`、`name`、`source_session_ids`、`draft_md`、`status`、`created_at`）。

## 6. 關鍵資料流

### 6.1 單一使用者 prompt → 串流回應（正常路徑）

```
Browser       web            session         router          cli           provider CLI
  │   POST     │                │               │              │                │
  │──────────▶ │                │               │              │                │
  │            │─ classify ───▶│◀── getEvents ─│              │                │
  │            │                │  (advisor)    │              │                │
  │            │◀── RouteDecided (evt)          │              │                │
  │            │─ converse ───────────────────▶ │              │                │
  │            │                │               │─ stream ────▶│   ProcessBuilder
  │            │                │               │              │── claude CLI ▶│
  │            │◀── tokens (SSE) ───────────────│◀─── tokens ──│                │
  │◀── swap ── │                │               │              │                │
  │            │─ persistTurn ──▶ SessionMemoryAdvisor.after()                  │
```

### 6.2 主代理 CLI 切換（AC3）

```
 user → /grimo switch codex  →  web.SwitchController
                               ↓
                           session.switch(sessionId, codex)
                               ↓
                      SessionMemoryAdvisor pre-load events
                               ↓
                    CompactionStrategy.compact(events)
                               ↓
            router.bind(sessionId, codex)   ── RouteDecided evt ──▶ cli
                               ↓
     next user message → cli(codex).prompt(compactedSystem + userMessage)
```

壓縮策略：預設使用 `SlidingWindowCompactionStrategy`（`maxEvents=20`）；當 session 超過 `grimo.session.summarize-after-tokens` 時可替換為 `RecursiveSummarizationCompactionStrategy`。

### 6.3 主代理委派寫作任務給子代理

```
 main-agent decides: "delegate: refactor OrderService"
            ↓ (structured task output)
 subagent.DelegateTaskUseCase.execute(taskSpec)
   ├─ WorktreePort.create(taskId)          → ~/.grimo/worktrees/<id>/
   ├─ Sandbox sandbox = sandboxFactory.create(image, mount=/work:<worktree> RW)
   │     (BindMountSandbox: GenericContainer.withFileSystemBind)
   ├─ inner agent runs with full Read/Write/Bash inside /work
   ├─ sandbox.exec(ExecSpec.of("git","diff")) → ExecResult returned to main-agent
   └─ user reviews diff → accept → worktree merge back
```

子代理無法讀取 `/work` 以外的主機路徑（AC5 由測試驗證；見 `qa-strategy.md`）。

## 7. 錯誤處理策略

- **`CliUnavailableException`** — 當底層二進位檔在呼叫時找不到時，由 `cli` 適配器拋出。這不是 500 錯誤；透過 HTMX toast 向使用者顯示 `"codex CLI not detected — run 'brew install codex' or /grimo switch claude"`。Boot 永遠成功啟動（P5）。
- **`SandboxSpawnException`** — Docker Daemon 未運行或映像拉取失敗。使用者看到可行動的訊息；`sub-agent` 透過拒絕啟動任務來降級（不會靜默降回主代理）。
- **`CompactionBudgetException`** — 壓縮後的歷史加上新訊息超過目標 CLI 的上下文預算。路由器升級至更大上下文的提供者，或要求使用者縮短訊息。
- **所有領域事件都攜帶 `correlationId`**（每個使用者輪次一個），讓 Web UI SSE 頻道可以劃定 token 串流的範圍。

## 8. 原生映像策略

- **JVM 優先（v1）。** `./gradlew bootRun` 和 `./gradlew bootJar` 是主要交付物。JDK 25 + virtual threads。
- **`native` profile 第一天就存在。** `./gradlew nativeCompile` 必須產生二進位檔；`./gradlew nativeRun` 必須啟動並提供 `/actuator/health`。這是**夜間 CI 閘門**，不是 PR 閘門 — 在不阻礙功能交付的前提下持續發現 hint 缺口。
- **Testcontainers 僅限 JVM。** `sub-agent` 有兩個實作：
  1. `TestcontainersSandboxAdapter` — 預設，JVM；用於測試和 JVM 執行期。
  2. `ProcessBuilderSandboxAdapter` — native 安全；直接呼叫 `docker run --mount type=bind,src=<worktree>,dst=/work ...`。人體工學稍差（無自動清理生命週期），但避免 Testcontainers 執行期依賴。透過 `grimo.subagent.backend=process|testcontainers` 選擇（預設 `testcontainers`，當 `NativeDetector.inNativeImage() == true` 時自動切換為 `process`）。
- **靜態 hints 登錄器**（`io.github.samzhu.grimo.native.GrimoRuntimeHints`，實作 `RuntimeHintsRegistrar`）登錄：
  - `AgentClient`、各提供者 `*AgentModel` 介面的代理。
  - CLI I/O DTOs 的 Jackson 型別。
  - Thymeleaf 模板路徑 `classpath:/templates/**/*.html`。
  - H2 JDBC 驅動 + session-jdbc schema 資源。
- **`ApplicationModules.verify()`** 僅保留在 JVM 測試中。生產環境設定 `spring.modulith.runtime.verification-enabled=false`。

## 9. 安全立場（MVP）

- **Bind-host：** `server.address=127.0.0.1` 在 `application.yml` 中鎖定，確保 Web UI 不會意外暴露在 LAN 上。
- **CSRF：** v1 未加入 Spring Security（D15），但 Thymeleaf 表單攜帶由 `HandlerInterceptor` 驗證的 `X-CSRF` 同步器 token。
- **沙箱：** 以 `--read-only` 根檔案系統（`/tmp` 使用 tmpfs）、`--security-opt=no-new-privileges`、`--cap-drop=ALL`（除非 skill 明確需要某 capability），以及 `--network=bridge`（附帶允許清單代理，預設：全部拒絕，除使用者在 `grimo.sandbox.allowlist` 中聲明的域名外）執行。
- **Git 操作**以主機使用者身份執行；子代理無法 push（掛載為每任務 worktree；不掛載憑證檔案）。

## 10. 可觀測性

- **Actuator** 端點位於 `127.0.0.1:8080/actuator`，MVP 限制為 `health`、`info`、`modulith`、`metrics`。
- **每輪成本**透過 SSE 事件名稱 `cost` 與 token 一起串流。聚合檢視位於 `/grimo/cost`。
- **Session 事件日誌**（來自 `SessionMemoryAdvisor`）可重放並匯出至 `~/.grimo/sessions/<session-id>.jsonl`。

## 11. 部署

- **開發：** `./gradlew bootRun --args='--spring.profiles.active=dev'`。DevTools 熱重載，H2 檔案 DB 自動初始化。
- **使用者安裝（JVM）：** `./gradlew bootJar` → `java -jar grimo-<v>.jar`。需要 JDK 25 執行期。
- **使用者安裝（native，M7）：** `./gradlew nativeCompile` → 單一檔案 `build/native/nativeCompile/grimo`。需要 Docker Daemon 可達，以供子代理隔離使用。
- **設定優先級**（高→低）：環境變數 → `~/.grimo/config/application.yml` → 打包預設值。

## 12. 尚未解決的技術問題（留待後續 ADR）

以下問題仍開放，若規格工作中的發現迫使我們偏離此處的假設，將成為 ADR：

- 確切的成本路由器啟發式表（任務類別 × 模型）。
- `RecursiveSummarizationCompactionStrategy` 是否需要專用的「摘要」模型（例如 Gemini 2.5 Flash），還是重用活躍的 CLI。
- 長時間運行子代理的可觀測性（結構化日誌抓取 vs 沙箱內的 OpenTelemetry）。
- Skill 蒸餾觸發策略（定時器 vs 成功後掛鉤 vs 夜間執行）。

---

*代碼級慣例請參考 `development-standards.md`，驗證管道請參考 `qa-strategy.md`。*
