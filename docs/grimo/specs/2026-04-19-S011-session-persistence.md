# S011: Session 持久化

> Spec: S011 | Size: S (10) | Status: ⏳ Design
> Date: 2026-04-19

---

## 1. Goal

為 `grimo chat` 加入 Session 持久化。透過 decorator 模式包裝 `AgentSession` / `AgentSessionRegistry`（agent-client 0.12.2 SPI），在每輪 `prompt()` 前後將 user/assistant 訊息寫入 H2（via `SessionService`，spring-ai-session-jdbc 0.2.0）。使用者可用 `grimo chat --resume <sessionId>` 恢復先前對話。

**依賴。** S007（主代理 CLI 對話）— **程式碼層級依賴**，S011 需修改 S007 建立的 REPL 以加入 `--resume` 旗標解析。S007 目前狀態：⏳ Design。S011 可先完成設計（§1-5），實作需等 S007 出貨。

**不包含。** 壓縮策略選型（另行研究 spec）。S011 的 `--resume` fallback 以最近 20 條事件為安全上限注入 bootstrap prompt，非正式壓縮。

## 2. Approach

### 2.1 核心矛盾

`SessionMemoryAdvisor`（spring-ai-session）是 **ChatClient advisor**，無法掛載到 `AgentSession`（agent-client）。兩者屬於不同 API 層次：

```
SessionMemoryAdvisor → hooks into ChatClient advisor chain
AgentSession.prompt() → directly talks to CLI subprocess, no advisor chain
```

agent-client repo 全文搜尋確認**零整合**：無任何 `SessionMemoryAdvisor`、`SessionService`、`spring-ai-session` 引用。

### 2.2 方案比較

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| **A: Decorator on AgentSession + AgentSessionRegistry** | ✅ yes | 用框架自己的 SPI（javadoc: "Analogous to SessionRepository in Spring Session"），不發明新抽象。`SessionService` 做儲存後端。全 CLI 通用（未來 Gemini/Codex 同模式）。 |
| B: AgentCallAdvisor on AgentClient.goal().run() | no | 走框架 advisor 鏈但每輪重啟 CLI process（慢），喪失 AgentSession 的常駐 process 優勢。且 AgentCallAdvisor 是 client-layer 攔截，非 session-layer。 |
| C: File-system SessionRepository（NDJSON） | no | 需自行實作 SessionRepository SPI（~200 LOC）、壓縮策略也需自行處理。spring-ai-session-jdbc + H2 開箱即用，無收益。 |
| D: SQLite | no | spring-ai-session-jdbc 0.2.0 無 SQLite dialect（需自行實作）；GraalVM native image JNI 風險（musl/Alpine 不支援）。 |

### 2.3 Research Citations

**agent-client SPI（raw source）：**
- AgentSession 介面：`prompt(String) → AgentResponse`，無 advisor chain，無持久化。[source](https://raw.githubusercontent.com/spring-ai-community/agent-client/refs/heads/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentSession.java)
- AgentSessionRegistry：javadoc "Analogous to SessionRepository in Spring Session"，`create(Path) → AgentSession`、`find(sessionId) → Optional`。[source](https://raw.githubusercontent.com/spring-ai-community/agent-client/refs/heads/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentSessionRegistry.java)
- ClaudeAgentSessionRegistry：`ConcurrentHashMap<String, ClaudeAgentSession>`，JVM 重啟即失。`create()` 啟動 CLI subprocess → parse sessionId → 存入 map。[source](https://raw.githubusercontent.com/spring-ai-community/agent-client/refs/heads/main/agent-models/agent-claude/src/main/java/org/springaicommunity/agents/claude/ClaudeAgentSessionRegistry.java)
- AgentResponse：`getText()` 取回應文字，`getMetadata()` 含 `model`、`duration`、`sessionId`、provider fields（HashMap）。[source](https://raw.githubusercontent.com/spring-ai-community/agent-client/refs/heads/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentResponse.java)
- AgentCallAdvisor：`adviseCall(AgentClientRequest, AgentCallAdvisorChain) → AgentClientResponse`。存在於 AgentClient 層（單次呼叫），非 AgentSession 層（多輪）。[source](https://raw.githubusercontent.com/spring-ai-community/agent-client/refs/heads/main/agent-client-core/src/main/java/org/springaicommunity/agents/client/advisor/api/AgentCallAdvisor.java)

**spring-ai-session 0.2.0（raw source）：**
- SessionService：`create(CreateSessionRequest)`、`appendEvent(SessionEvent)`、`getEvents(sessionId, EventFilter)`、`compact(sessionId, trigger, strategy)`。[mintlify docs](https://spring-ai-community.github.io/spring-ai-session/0.3.0-SNAPSHOT/)
- SessionEvent：builder pattern — `.sessionId(s).message(m).metadata(map).build()`。欄位：`id`、`sessionId`、`timestamp`、`message`（Spring AI `Message`）、`metadata`、`branch`。
- H2JdbcSessionRepositoryDialect：存在，使用 `MERGE INTO ... KEY (id) VALUES (...)`。自動偵測（`JdbcSessionRepositoryDialect.from(DataSource)`）。
- H2 Schema：`AI_SESSION`（id, user_id, created_at, expires_at, metadata, event_version）+ `AI_SESSION_EVENT`（id, session_id, timestamp, message_type, message_content, message_data, synthetic, branch, metadata）。FK cascade delete。
- SessionMemoryAdvisor：實作 `BaseAdvisor`（ChatClient SPI），`before()` 載入歷史 + append user，`after()` append assistant + optional compact。**無法用於 AgentSession**。
- 壓縮策略：4 種內建（SlidingWindow / TurnWindow / TokenCount / RecursiveSummarization）+ 2 種 trigger（TurnCount / TokenCount）。S011 不選型，留後續 spec。

**H2 配置（web research）：**
- `~` 展開由 H2 原生支援（`System.getProperty("user.home")`）。
- `AUTO_SERVER=TRUE` 單程序 CLI 不需要，有負面開銷 — 建議移除。[H2 docs](http://h2database.com/html/features.html)
- `MODE=PostgreSQL` 完整設定需加 `DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`。[H2 docs](http://h2database.com/html/features.html)
- `CACHE_SIZE=32768`（32MB）明確限制 CLI 工具快取上限。預設為「可用記憶體 50%」過於寬鬆。
- File mode 使用 page cache + LRU 驅逐，不會將全部資料載入記憶體。
- Java 25 + JEP 491 消除 `synchronized` 固定 virtual threads 問題。[JEP 491](https://openjdk.org/jeps/491)

### 2.4 H2 URL 修正

架構文件原 URL：`jdbc:h2:file:~/.grimo/db/grimo;MODE=PostgreSQL;AUTO_SERVER=TRUE`

修正為：
```
jdbc:h2:file:~/.grimo/db/grimo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CACHE_SIZE=32768
```

| 變更 | 理由 |
|------|------|
| 移除 `AUTO_SERVER=TRUE` | 單程序 CLI 不需要，增加 TCP 啟動開銷 |
| 加入 `DATABASE_TO_LOWER=TRUE` | H2 官方建議的 PostgreSQL mode 完整設定 |
| 加入 `DEFAULT_NULL_ORDERING=HIGH` | NULL 排序行為與 PostgreSQL 一致 |
| 加入 `DB_CLOSE_DELAY=-1` | JVM 存活時資料庫保持開啟 |
| 加入 `DB_CLOSE_ON_EXIT=FALSE` | 避免 shutdown hook 競爭 |
| 加入 `CACHE_SIZE=32768` | 明確限制快取上限 32MB |

### 2.5 模組歸屬

Decorator 類別放在 `agent/internal/session/`（遵循既有慣例：`cli/internal/`、`sandbox/internal/`）。透過 `@Configuration` + `@Primary` bean 提供 `PersistentAgentSessionRegistry`，S007 的 REPL 注入 `AgentSessionRegistry`（SPI）自動拿到持久化版本。不建立新的 `session` 模組。

architecture.md backlog 中的 `session` 模組描述（"SessionMemoryAdvisor 接線"）與實際設計不符 — 我們用 decorator 而非 SessionMemoryAdvisor。S011 出貨時更新 backlog 描述。

---

## 3. SBE Acceptance Criteria

**驗證命令：**
```
Run: ./gradlew test
Pass: all tests carrying S011 AC ids are green.
```

---

### AC-1: 對話事件持久化至 H2

```
Given  grimo chat 啟動互動式 Session
When   使用者輸入「hello」並收到 agent 回應
Then   AI_SESSION 表中存在該 session 記錄
And    AI_SESSION_EVENT 表中存在 ≥ 2 筆事件（USER + ASSISTANT）
And    USER 事件的 message_content 包含「hello」
```

### AC-2: --resume 恢復先前對話

```
Given  先前有一個已結束的 session（grimoSessionId = "abc123"）
       且 H2 中存有該 session 的對話歷史
When   grimo chat --resume abc123
Then   agent 回應參考先前上下文
And    新的對話事件追加至同一個 session
```

### AC-3: 無效 sessionId 印出錯誤

```
Given  H2 中不存在 sessionId = "nonexistent"
When   grimo chat --resume nonexistent
Then   印出 "Session 'nonexistent' not found"
And    以非零狀態退出
And    不顯示堆疊追蹤
```

### AC-4: ~/.grimo/db/ 自動建立

```
Given  ~/.grimo/db/ 目錄不存在
When   grimo chat 首次啟動
Then   目錄自動建立（via GrimoHomePaths.db()）
And    grimo.mv.db 檔案出現在該目錄下
```

---

## 4. Interface / API Design

### 4.1 PersistentAgentSession（decorator）

```java
package io.github.samzhu.grimo.agent.internal.session;

// implements AgentSession — 框架自己的 SPI
public class PersistentAgentSession implements AgentSession {

    private final AgentSession delegate;
    private final SessionService sessionService;
    private final String grimoSessionId;

    @Override
    public AgentResponse prompt(String message) {
        sessionService.appendEvent(SessionEvent.builder()
            .sessionId(grimoSessionId)
            .message(new UserMessage(message))
            .build());

        AgentResponse response = delegate.prompt(message);

        sessionService.appendEvent(SessionEvent.builder()
            .sessionId(grimoSessionId)
            .message(new AssistantMessage(response.getText()))
            .metadata("cliSessionId", delegate.getSessionId())
            .metadata("model", response.getMetadata().getModel())
            .build());

        return response;
    }

    // getSessionId() → grimoSessionId（Grimo 擁有的 ID）
    // getStatus(), getWorkingDirectory(), resume(), fork(), close() → 委派 delegate
}
```

### 4.2 PersistentAgentSessionRegistry（decorator）

```java
package io.github.samzhu.grimo.agent.internal.session;

// implements AgentSessionRegistry — 框架自己的 SPI
@Primary
public class PersistentAgentSessionRegistry implements AgentSessionRegistry {

    private final AgentSessionRegistry delegate;  // e.g. ClaudeAgentSessionRegistry
    private final SessionService sessionService;

    @Override
    public AgentSession create(Path workingDirectory) {
        AgentSession raw = delegate.create(workingDirectory);
        String grimoSessionId = SessionId.random().value();

        Session stored = sessionService.create(CreateSessionRequest.builder()
            .id(grimoSessionId)
            .metadata("cliSessionId", raw.getSessionId())
            .metadata("provider", "claude")
            .build());

        return new PersistentAgentSession(raw, sessionService, grimoSessionId);
    }

    @Override
    public Optional<AgentSession> find(String grimoSessionId) {
        Session stored = sessionService.findById(grimoSessionId);
        if (stored == null) return Optional.empty();

        String cliSessionId = (String) stored.getMetadata().get("cliSessionId");

        // 嘗試從底層 registry 恢復 CLI session
        Optional<AgentSession> cliSession = delegate.find(cliSessionId);

        if (cliSession.isPresent()) {
            return Optional.of(
                new PersistentAgentSession(cliSession.get(), sessionService, grimoSessionId));
        }

        // Fallback: CLI session 已過期 → 建新 session + 注入歷史
        AgentSession newRaw = delegate.create(stored.getWorkingDirectory());
        List<SessionEvent> history = sessionService.getEvents(grimoSessionId);
        String bootstrap = buildBootstrapPrompt(history);  // 最近 20 條事件
        newRaw.prompt(bootstrap);  // 注入歷史

        // 更新 cliSessionId 映射
        // ...

        return Optional.of(
            new PersistentAgentSession(newRaw, sessionService, grimoSessionId));
    }

    // evict(), evictStale() → 委派 + 清理 SessionService
}
```

### 4.3 --resume 恢復流程

```
grimo chat --resume <grimoSessionId>
    │
    ▼
PersistentAgentSessionRegistry.find(grimoSessionId)
    │
    ├─ H2 查到 → 取得 cliSessionId
    │   ├─ delegate.find(cliSessionId) → 找到 → 包裝回傳（快速路徑）
    │   └─ delegate.find(cliSessionId) → 找不到（CLI session 過期）
    │       └─ delegate.create(dir) → 新 CLI session
    │           └─ 載入 H2 歷史（最近 20 條）→ 構建 bootstrap prompt → 注入
    │
    └─ H2 查不到 → Optional.empty() → REPL 印出 "Session not found"
```

### 4.4 H2 配置

```yaml
# application.yml（S011 新增）
spring:
  datasource:
    url: "jdbc:h2:file:~/.grimo/db/grimo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CACHE_SIZE=32768"
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  sql:
    init:
      mode: always
```

### 4.5 SessionPersistenceConfig

```java
@Configuration
class SessionPersistenceConfig {

    @Bean
    @Primary
    AgentSessionRegistry persistentAgentSessionRegistry(
            AgentSessionRegistry delegate,
            SessionService sessionService) {
        return new PersistentAgentSessionRegistry(delegate, sessionService);
    }
}
```

---

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `build.gradle.kts` | modify | 新增 `spring-boot-starter-jdbc`、`com.h2database:h2:2.3.232`、`org.springaicommunity:spring-ai-starter-session-jdbc:0.2.0` |
| `src/main/resources/application.yml` | modify | 新增 H2 datasource + session schema init 配置 |
| `src/main/java/.../agent/internal/session/PersistentAgentSession.java` | new | AgentSession decorator — prompt() 前後寫入 SessionService |
| `src/main/java/.../agent/internal/session/PersistentAgentSessionRegistry.java` | new | AgentSessionRegistry decorator — create/find 加持久化 + resume fallback |
| `src/main/java/.../agent/internal/session/SessionPersistenceConfig.java` | new | @Configuration — @Primary bean 提供持久化 registry |
| `src/main/java/.../agent/internal/session/BootstrapPromptBuilder.java` | new | 從 SessionEvent list 構建 bootstrap prompt 的工具類（最近 N 條） |
| `src/main/java/.../agent/package-info.java` | modify | allowedDependencies 加入 `"core"`（若尚未有） |
| `src/test/java/.../agent/internal/session/PersistentAgentSessionTest.java` | new | 單元測試：mock delegate + SessionService，驗證 appendEvent 呼叫 |
| `src/test/java/.../agent/internal/session/PersistentAgentSessionRegistryTest.java` | new | 單元測試：create/find/resume fallback 邏輯 |
| `src/test/java/.../agent/internal/session/BootstrapPromptBuilderTest.java` | new | 單元測試：歷史 → prompt 轉換 |
| `src/test/java/.../agent/SessionPersistenceIT.java` | new | 整合測試：真實 H2 + stub AgentSession，驗證 AC-1 至 AC-4 |
| `docs/grimo/architecture.md` | modify | §5.2 更新 H2 URL（移除 AUTO_SERVER、加入 CACHE_SIZE 等）；§2.x Backlog 更新 session 模組描述 |

---

## 6. Task Plan

**POC: required** — `spring-ai-starter-session-jdbc:0.2.0` 從未在此專案中使用過。即使 spec research 已引用 raw source，`SessionService` / `SessionEvent` / `CreateSessionRequest` 的 builder API 語義（回傳值、例外行為、必填欄位）需透過 POC 驗證。

### Task Index

| Task | Topic | AC Coverage | Depends On | Status |
|------|-------|-------------|------------|--------|
| T1 | Infrastructure + Session persistence decorator (create path) | AC-1, AC-4 | — | pending |
| T2 | --resume flow + error handling | AC-2, AC-3 | T1 | pending |
| T3 | Integration tests covering all ACs | AC-1-4 | T2 | pending |

### AC → Task Mapping

| AC | Task(s) |
|----|---------|
| AC-1: 對話事件持久化至 H2 | T1 (decorator), T3 (IT) |
| AC-2: --resume 恢復先前對話 | T2 (resume flow), T3 (IT) |
| AC-3: 無效 sessionId 印出錯誤 | T2 (error handling), T3 (IT) |
| AC-4: ~/.grimo/db/ 自動建立 | T1 (GrimoHomePaths.db()), T3 (IT) |

### Execution Order

```
T1 (deps + decorator + unit tests)
  └─▶ T2 (resume + REPL mods + unit tests)
        └─▶ T3 (integration test + arch doc update)
```

### POC Findings

*(to be filled after Phase 1.5)*

<!-- Section 7 added after implementation -->
