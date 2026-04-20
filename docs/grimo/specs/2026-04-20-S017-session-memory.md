# S017: Grimo Session Memory — 對話歷史記錄（v2 重設計）

> Spec: S017 | Size: S (9) | Status: ⏳ Design
> Date: 2026-04-20 (v2 — 基於 POC 發現重新設計)

---

## 1. Goal

建立 Grimo 自有的對話歷史記錄層。`RecordingAgentSession` decorator 攔截每輪 `AgentSession.prompt()`，將 user input、assistant response **及 rich metadata**（model、duration、token counts、finish reason）透過 Modulith event 非同步持久化至 H2 自建表。

**兩層分離設計（PRD P3「Grimo 擁有 session」）：**

| 層 | 管理者 | 儲存 | 用途 |
|---|--------|------|------|
| Grimo 層 | `RecordingAgentSession` decorator | `grimo_conversation_turn`（H2） | 跨 CLI 切換、歷史查詢、成本追蹤、壓縮重放 |
| CLI 層 | `ClaudeAgentSessionRegistry` | `~/.claude/projects/*.jsonl` | `--resume` / `--continue`（Claude 原生） |

**兩層記憶架構參考（Spring AI Agentic Patterns Part 6）：**

| 記憶類型 | 職責 | 本 spec 範圍 |
|----------|------|-------------|
| Session Memory | 當前對話的完整歷史（每輪 user/assistant） | ✅ 本 spec |
| Long-term Memory | 跨 session 的策展事實（`AutoMemoryTools` → `~/.grimo/memory/`） | ❌ Backlog |

**晉升自 Backlog。** 原 Backlog 項目「持久化 Session」設計為 `SessionMemoryAdvisor`（S011 POC 否定）。v1 設計改用 Spring AI `ChatMemory` + `JdbcChatMemoryRepository`（POC 發現嚴重限制，見 §2.1）。v2 改用 decorator + event + 自建 JDBC 表。

**依賴。** S007 ✅（主代理 REPL）、S011 ✅（AgentSession API 驗證）。

**不包含。** 跨 CLI 壓縮重放（Backlog）。AutoMemoryTools 長期記憶（Backlog）。Session 列表 / 匯出 CLI 子命令（後續 spec）。

### 1.1 設計變更紀錄

| 項目 | v1 設計（已作廢） | v2 設計 |
|------|------------------|---------|
| 儲存 API | Spring AI `ChatMemory` + `JdbcChatMemoryRepository` | 自建 `JdbcConversationTurnAdapter` + `JdbcTemplate` |
| Schema | `SPRING_AI_CHAT_MEMORY`（4 欄位，metadata 丟棄） | `grimo_conversation_turn`（11 欄位，含 model/tokens/duration） |
| 寫入語意 | 全量替換（DELETE + re-INSERT） | Append-only（每輪一筆 INSERT） |
| 記錄機制 | Decorator 直接寫 DB | Decorator publish event → `@ApplicationModuleListener` 寫 DB |
| 依賴 | `spring-ai-model-chat-memory-repository-jdbc` | 僅 `spring-boot-starter-jdbc` + H2 |

---

## 2. Approach

### 2.1 v1 設計否定原因

POC（2026-04-20）驗證 `JdbcChatMemoryRepository` 2.0.0-M4 有以下問題：

| 問題 | 影響 |
|------|------|
| `content` 存純文字（`message.getText()`），metadata 完全丟棄 | 無法存 model name、token counts、duration |
| `saveAll()` 全量替換（DELETE all + re-INSERT all） | 不適合 append-only 場景，效能差 |
| `timestamp` 是排序序號（`getEpochSecond()` 遞增），非真實時間戳 | 無法推算訊息實際發生時間 |
| Schema `timestamp` 在 H2 2.4.240 觸發保留字衝突 | CHECK constraint 異常（POC 確認） |
| TOOL 訊息 round-trip 內容遺失 | 框架已知限制 |

### 2.2 選型比較

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| **A: Decorator + Event + 自建表** | ✅ yes | Decorator 是唯一能攔截 `AgentSession.prompt()` 的機制（advisor chain 不涵蓋）。Modulith event 解耦記錄邏輯。自建表完全控制 schema，可存 model/tokens/duration。Append-only INSERT，效能好。 |
| B: `AgentCallAdvisor` + Event | no | `AgentCallAdvisor` 只攔截 `AgentClient.run()`（無狀態單次呼叫），**不攔截** `AgentSession.prompt()`（有狀態多輪）。S007 的 REPL 使用 `AgentSession`，advisor chain 完全繞過。 |
| C: Spring AI `ChatMemory` + `JdbcChatMemoryRepository` | no | v1 設計。metadata 丟棄、全量替換、H2 相容性問題。見 §2.1。 |
| D: `ChatMemory` + 額外 metadata 表 | no | 兩表同步維護複雜度高。`ChatMemory` 的全量替換語意仍是問題。不如一表到底。 |

**為什麼 Decorator 而非 Advisor：**
- `AgentCallAdvisor`（agent-client）→ 攔截 `AgentClient.run()`（每次建立/銷毀 process，無狀態）
- `AgentSession.prompt()`（S007 用的 API）→ 直接呼叫 CLI subprocess，**無 advisor chain**
- `AutoMemoryToolsAdvisor`（spring-ai-agent-utils）→ 是 Spring AI `BaseChatMemoryAdvisor`，攔截 `ChatClient`，不是 `AgentSession`
- Decorator 在 `AgentSession` interface 層包裝，是唯一乾淨的擴展方式

### 2.3 Event-Driven Recording

```
RecordingAgentSession.prompt(userText):
  1. response = delegate.prompt(userText)          // 阻塞，等 CLI 回應
  2. eventPublisher.publishEvent(                   // 非同步記錄
       new ConversationTurnRecorded(sessionId, userText, response))
  3. return response                                // 零額外延遲
```

**為什麼用 event 而非直接寫 DB：**
- **Modulith 解耦**：decorator（在 `session/internal/`）不直接依賴 JDBC adapter
- **可擴展性**：未來 `cost` 模組可監聽同一 `ConversationTurnRecorded` event 做成本統計
- **跨模組通訊**：符合 `development-standards.md` §13 模式 C（非同步事件為預設）

### 2.4 自建 Schema

```sql
CREATE TABLE IF NOT EXISTS grimo_conversation_turn (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    turn_sequence INT NOT NULL,
    user_message CLOB NOT NULL,
    assistant_message CLOB,
    model VARCHAR(100),
    duration_ms BIGINT,
    tokens_in BIGINT,
    tokens_out BIGINT,
    finish_reason VARCHAR(20),
    provider VARCHAR(20) DEFAULT 'claude',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_grimo_turn_session
    ON grimo_conversation_turn(session_id, turn_sequence);
```

**Schema 設計理由：**
- `session_id` — 來自 `AgentSession.getSessionId()`（Claude CLI 分配的 UUID）
- `turn_sequence` — 從 1 遞增，由 `RecordingAgentSession` 內部 `AtomicInteger` 維護
- `model` / `duration_ms` — 來自 `AgentResponseMetadata.getModel()` / `.getDuration()`
- `tokens_in` / `tokens_out` — 來自 `AgentResponseMetadata.getProviderFields()`（非標準 API，可能為 null）
- `finish_reason` — 來自 `AgentGenerationMetadata.getFinishReason()`
- `provider` — 目前固定 `'claude'`，未來多 CLI 時由 decorator 設定
- `created_at` — 真實時間戳（非 Spring AI ChatMemory 的排序序號）
- `CLOB` 而非 `LONGVARCHAR` — 避免 H2 2.x 相容性問題

### 2.5 H2 Datasource 配置

```yaml
spring:
  datasource:
    url: jdbc:h2:file:${user.home}/.grimo/db/grimo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: embedded  # H2 embedded → auto-init schema.sql
```

- H2 file mode（PRD D17）— `~/.grimo/db/grimo.mv.db`
- `MODE=PostgreSQL` — 未來遷移 PostgreSQL 時保持對等（PRD D17）
- `schema.sql` 由 Spring Boot `spring.sql.init.mode=embedded` 自動執行（`CREATE TABLE IF NOT EXISTS` 冪等）
- 研究筆記 `docs/local/session-design-research.md` §3.1 已詳述 URL 參數選擇

### 2.6 模組設計

新建 `session` Modulith 模組（從 Backlog 晉升）：

```
io.github.samzhu.grimo.session
├── package-info.java                    # @ApplicationModule, allowedDependencies = { "core" }
├── domain/
│   └── ConversationTurn.java            # record(id, sessionId, turnSeq, userMsg, assistantMsg, model, ...)
├── events/
│   ├── package-info.java                # @NamedInterface("events")
│   └── ConversationTurnRecorded.java    # event record — published by decorator
├── application/
│   ├── port/in/
│   │   ├── package-info.java            # @NamedInterface("api")
│   │   └── ConversationHistoryUseCase.java  # getHistory(sessionId)
│   └── service/
│       ├── ConversationHistoryService.java
│       └── ConversationTurnRecorder.java    # @ApplicationModuleListener — handles event
├── adapter/out/
│   └── JdbcConversationTurnAdapter.java     # JdbcTemplate → grimo_conversation_turn
└── internal/
    ├── RecordingAgentSession.java            # decorator (thin, publishes event)
    ├── RecordingAgentSessionRegistry.java    # @Primary, wraps ClaudeAgentSessionRegistry
    └── SessionModuleConfig.java             # ApplicationEventPublisher wiring
```

**跨模組接線：**
- `agent` 模組注入 `AgentSessionRegistry`（Spring bean）— `@Primary` 自動取得 `RecordingAgentSessionRegistry`（透明包裝）
- `agent` 的 `allowedDependencies` **不需要改動** — `@Primary` 透明注入不引用 `session` 模組的型別
- `ConversationTurnRecorded` event 放在 `session/events/`（`@NamedInterface("events")`）— 未來 `cost` 模組可宣告 `allowedDependencies = { "session::events" }` 監聽

### 2.7 Research Citations

- **`AgentSession` SPI** — 無 `getMessages()` API；`prompt()` 是唯一攔截點。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentSession.java)
- **`AgentResponseMetadata`** — 強型別 getter：`getModel()` → String、`getDuration()` → Duration、`getSessionId()` → String。Token counts 在 `providerFields`（非標準 API）。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentResponseMetadata.java)
- **`AgentCallAdvisor`** — 只攔截 `AgentClient.run()`，不攔截 `AgentSession.prompt()`。Around-advice 模式。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-client-core/src/main/java/org/springaicommunity/agents/client/advisor/api/AgentCallAdvisor.java)
- **`JdbcChatMemoryRepository`** — 只存 `content`（text）+ `type`（enum name），metadata 完全丟棄。`saveAll()` 全量替換。[source](https://github.com/spring-projects/spring-ai/blob/main/memory/repository/spring-ai-model-chat-memory-repository-jdbc/src/main/java/org/springframework/ai/chat/memory/repository/jdbc/JdbcChatMemoryRepository.java)
- **Spring AI Agentic Patterns Part 6** — ChatMemory（短期滑動視窗）vs AutoMemoryTools（長期策展）兩層互補架構。[blog](https://spring.io/blog/2026/04/07/spring-ai-agentic-patterns-6-memory-tools)
- **`AutoMemoryToolsAdvisor`** — `BaseChatMemoryAdvisor`（Spring AI ChatClient 層），非 `AgentCallAdvisor`。六個 `@Tool` 注入 `ToolCallingChatOptions`。[source](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/advisors/AutoMemoryToolsAdvisor.java)
- **H2 2.4.240 相容性** — `schema-h2.sql` 的 `timestamp` 保留字 + CHECK constraint 在 H2 2.x 觸發異常。POC 確認。
- **session-design-research.md** — H2 配置、SQLite 否決、記憶分層研究。[local](docs/local/session-design-research.md)

---

## 3. SBE Acceptance Criteria

**驗證命令：** `./gradlew test`
Pass: 所有攜帶 S017 AC id 的測試為綠色。

---

**AC-1: 對話記錄 — 每輪 prompt/response 及 metadata 存入 DB**

```
Given  RecordingAgentSession 包裝了一個 stub AgentSession
       （回傳預設 model="claude-sonnet-4-20250514", duration=2s, finishReason="SUCCESS"）
When   呼叫 prompt("hello") 且 delegate 回傳 "Hi there"
Then   grimo_conversation_turn 表中存在 1 筆記錄
And    session_id 與 delegate 的 sessionId 一致
And    turn_sequence = 1
And    user_message = "hello"
And    assistant_message = "Hi there"
And    model = "claude-sonnet-4-20250514"
And    duration_ms > 0
And    finish_reason = "SUCCESS"
And    created_at 為真實時間（非 epoch 秒數序號）
```

**AC-2: 多輪累積（append-only）**

```
Given  RecordingAgentSession 已記錄 2 輪對話
When   呼叫第 3 輪 prompt("bye")
Then   grimo_conversation_turn 表中存在 3 筆記錄
And    turn_sequence 分別為 1, 2, 3（遞增）
And    每筆為獨立 INSERT（非全量替換）
```

**AC-3: Registry decorator 透明包裝**

```
Given  RecordingAgentSessionRegistry 包裝了一個 stub AgentSessionRegistry
When   呼叫 create(workDir)
Then   回傳的 AgentSession 是 RecordingAgentSession
And    getSessionId() 與底層 session 一致
And    getWorkingDirectory() 與底層一致
```

**AC-4: H2 持久化 — grimo_conversation_turn 表**

```
Given  應用以 H2 embedded mode 啟動
When   schema.sql 自動執行
Then   grimo_conversation_turn 表存在
And    包含 11 個欄位（id, session_id, turn_sequence, user_message,
       assistant_message, model, duration_ms, tokens_in, tokens_out,
       finish_reason, provider, created_at）
```

**AC-5: Modulith 邊界合規**

```
Given  session 模組以 @ApplicationModule 宣告
And    events/ 標記 @NamedInterface("events")
And    application/port/in/ 標記 @NamedInterface("api")
When   ./gradlew test 執行 ModuleArchitectureTest
Then   Modulith verify 通過
And    agent 模組不直接引用 session/internal/ 套件
```

---

## 4. Interface / API Design

```java
// === session module: domain ===

// session/domain/ConversationTurn.java
public record ConversationTurn(
    Long id,
    String sessionId,
    int turnSequence,
    String userMessage,
    String assistantMessage,
    String model,
    Long durationMs,
    Long tokensIn,          // nullable — provider-dependent
    Long tokensOut,         // nullable — provider-dependent
    String finishReason,
    String provider,
    Instant createdAt
) {}

// === session module: events (exposed via @NamedInterface("events")) ===

// session/events/ConversationTurnRecorded.java
public record ConversationTurnRecorded(
    String sessionId,
    int turnSequence,
    String userMessage,
    AgentResponse agentResponse  // carries text + metadata
) {}

// === session module: port/in (exposed via @NamedInterface("api")) ===

// session/application/port/in/ConversationHistoryUseCase.java
public interface ConversationHistoryUseCase {
    List<ConversationTurn> getHistory(String sessionId);
}

// === session module: internal (NOT exposed) ===

// session/internal/RecordingAgentSession.java
class RecordingAgentSession implements AgentSession {
    private final AgentSession delegate;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicInteger turnCounter = new AtomicInteger(0);

    @Override
    public AgentResponse prompt(String message) {
        AgentResponse response = delegate.prompt(message);
        int turn = turnCounter.incrementAndGet();
        eventPublisher.publishEvent(
            new ConversationTurnRecorded(
                getSessionId(), turn, message, response));
        return response;
    }

    // getSessionId(), getWorkingDirectory(), getStatus(), close(), resume()
    // → 全部 delegate
}

// session/internal/RecordingAgentSessionRegistry.java
@Primary
class RecordingAgentSessionRegistry implements AgentSessionRegistry {
    private final AgentSessionRegistry delegate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AgentSession create(Path workDir) {
        return new RecordingAgentSession(
            delegate.create(workDir), eventPublisher);
    }
    // find, evict, evictStale → delegate
}

// session/application/service/ConversationTurnRecorder.java
@ApplicationModuleListener
class ConversationTurnRecorder {
    private final JdbcConversationTurnAdapter adapter;

    void on(ConversationTurnRecorded event) {
        var response = event.agentResponse();
        var meta = response.getMetadata();
        var genMeta = response.getResult().getMetadata();

        var turn = new ConversationTurn(
            null,                                      // id — auto-increment
            event.sessionId(),
            event.turnSequence(),
            event.userMessage(),
            response.getText(),                        // assistant message
            meta.getModel(),                           // model name
            meta.getDuration().toMillis(),              // duration in ms
            extractLong(meta, "inputTokens"),           // nullable
            extractLong(meta, "outputTokens"),          // nullable
            genMeta.getFinishReason(),                  // SUCCESS / COMPLETE / ERROR
            "claude",                                   // provider — future: dynamic
            Instant.now()
        );
        adapter.save(turn);
    }

    private Long extractLong(AgentResponseMetadata meta, String key) {
        var val = meta.get(key);
        return val instanceof Number n ? n.longValue() : null;
    }
}

// session/adapter/out/JdbcConversationTurnAdapter.java
class JdbcConversationTurnAdapter {
    private final JdbcTemplate jdbcTemplate;

    void save(ConversationTurn turn) {
        jdbcTemplate.update("""
            INSERT INTO grimo_conversation_turn
            (session_id, turn_sequence, user_message, assistant_message,
             model, duration_ms, tokens_in, tokens_out,
             finish_reason, provider, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            turn.sessionId(), turn.turnSequence(),
            turn.userMessage(), turn.assistantMessage(),
            turn.model(), turn.durationMs(),
            turn.tokensIn(), turn.tokensOut(),
            turn.finishReason(), turn.provider(),
            Timestamp.from(turn.createdAt()));
    }

    List<ConversationTurn> findBySessionId(String sessionId) {
        return jdbcTemplate.query("""
            SELECT * FROM grimo_conversation_turn
            WHERE session_id = ? ORDER BY turn_sequence ASC
            """,
            (rs, _) -> new ConversationTurn(
                rs.getLong("id"), rs.getString("session_id"),
                rs.getInt("turn_sequence"),
                rs.getString("user_message"),
                rs.getString("assistant_message"),
                rs.getString("model"),
                rs.getObject("duration_ms", Long.class),
                rs.getObject("tokens_in", Long.class),
                rs.getObject("tokens_out", Long.class),
                rs.getString("finish_reason"),
                rs.getString("provider"),
                rs.getTimestamp("created_at").toInstant()),
            sessionId);
    }
}
```

---

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `build.gradle.kts` | modify | 加 `spring-boot-starter-jdbc` + `h2`（runtimeOnly）；移除 `spring-ai-model-chat-memory-repository-jdbc`（v1 殘留） |
| `src/main/resources/application.yaml` | modify | H2 datasource URL + `spring.sql.init.mode=embedded` |
| `src/main/resources/schema.sql` | new | `grimo_conversation_turn` 表 + 索引（替換 v1 POC 的 ChatMemory schema） |
| `session/package-info.java` | new | `@ApplicationModule(allowedDependencies = { "core" })` |
| `session/domain/ConversationTurn.java` | new | 純 record — 零 Spring |
| `session/events/package-info.java` | new | `@NamedInterface("events")` |
| `session/events/ConversationTurnRecorded.java` | new | event record |
| `session/application/port/in/package-info.java` | new | `@NamedInterface("api")` |
| `session/application/port/in/ConversationHistoryUseCase.java` | new | 入站埠 |
| `session/application/service/ConversationHistoryService.java` | new | 用例實作 |
| `session/application/service/ConversationTurnRecorder.java` | new | `@ApplicationModuleListener` — 處理 event |
| `session/adapter/out/JdbcConversationTurnAdapter.java` | new | JdbcTemplate → grimo_conversation_turn |
| `session/internal/RecordingAgentSession.java` | new | AgentSession decorator（thin, publishes event） |
| `session/internal/RecordingAgentSessionRegistry.java` | new | Registry decorator, `@Primary` |
| `session/internal/SessionModuleConfig.java` | new | Bean wiring |
| `src/test/java/.../session/RecordingAgentSessionTest.java` | new | AC-1, AC-2（stub delegate + embedded H2） |
| `src/test/java/.../session/RecordingAgentSessionRegistryTest.java` | new | AC-3 |
| `src/test/java/.../session/ConversationTurnPersistenceTest.java` | new | AC-4（H2 schema + JdbcTemplate） |
| `src/test/java/.../poc/ChatMemoryPocTest.java` | delete | v1 POC 殘留，不再需要 |
| `src/test/resources/schema-test-chat-memory.sql` | delete | v1 POC 殘留 |

> **注意：** 所有 `session/` 路徑的完整前綴為 `src/main/java/io/github/samzhu/grimo/session/`；測試的完整前綴為 `src/test/java/io/github/samzhu/grimo/session/`。

---

### 估算

| 維度 | 分數 | 理由 |
|------|------|------|
| 技術風險 | 1 | 標準 JdbcTemplate + H2 + Modulith events，無 exotic API |
| 不確定性 | 1 | 設計經完整 research + grill 確認 |
| 依賴關係 | 1 | S007 + S011 已出貨 |
| 範疇 | 2 | 新模組 + decorator + JDBC + schema + events |
| 測試 | 2 | 單元 + H2 embedded 整合 |
| 可逆性 | 2 | 新模組，但 @Primary 覆蓋現有 bean 有風險 |
| **合計** | **9** | **S** |
