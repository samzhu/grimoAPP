# S017: Grimo Session Memory — Event-Sourced 對話歷史（v3 重設計）

> Spec: S017 | Size: S (11) | Status: ⏳ Design
> Date: 2026-04-20 (v3 — Event Sourcing + Projection)

---

## 1. Goal

建立 Grimo 自有的 **event-sourced 對話歷史記錄層**。`RecordingAgentSession` decorator 攔截每輪 `AgentSession.prompt()`，將 user input、assistant response 以 **JSON payload + metadata** 形式持久化至 append-only event store，同時維護 session projection 摘要表。

### 兩層分離設計（PRD P3「Grimo 擁有 session」）

| 層 | 管理者 | 儲存 | 用途 |
|---|--------|------|------|
| Grimo 層 | `RecordingAgentSession` decorator | `grimo_session_event`（H2 event store） + `grimo_session`（projection） | 跨 CLI 切換、歷史查詢、壓縮重放、fork 追蹤 |
| CLI 層 | `ClaudeAgentSessionRegistry` | `~/.claude/projects/*.jsonl` | `--resume` / `--continue` / `--fork-session`（Claude 原生） |

### 兩層記憶架構（Spring AI Agentic Patterns Part 6 + Part 7）

| 記憶類型 | 職責 | 本 spec 範圍 |
|----------|------|-------------|
| Session Memory（短期） | 當前對話的 event-sourced 完整歷史 | ✅ 本 spec |
| Long-term Memory（長期） | 跨 session 策展事實（`AutoMemoryTools` → `~/.grimo/memory/`） | Backlog |

### 晉升自 Backlog

- v1：Spring AI `ChatMemory` + `JdbcChatMemoryRepository`（POC 否決：metadata 丟棄、全量替換、H2 相容性）
- v2：Decorator + event + 自建 flat table（可行但 schema 不夠靈活）
- **v3（本版）：** Event Sourcing + Projection。受 T3 Code event store 啟發，借鑑 Spring AI Session API（`SessionMemoryAdvisor`）的 turn 概念。Compaction SPI 由 S014 規劃。

### 依賴

S007 ✅（主代理 REPL）、S011 ✅（AgentSession API 驗證）。

### 不包含

- Compaction SPI 介面定義 + 策略實作（S014 負責全部）
- 對話 fork **邏輯**（schema 預留 `parent_id` + `fork_turn`，Claude CLI 的 `/branch` / `--fork-session` 橋接為後續 spec）
- 跨 CLI 壓縮重放（Backlog）
- `AutoMemoryTools` 長期記憶（Backlog）

### 1.1 設計變更紀錄

| 項目 | v2 設計（已作廢） | v3 設計 |
|------|------------------|---------|
| 儲存模型 | 單表 `grimo_conversation_turn`（11 flat columns） | Event store `grimo_session_event` + projection `grimo_session` |
| 資料格式 | Flat columns（model, duration_ms, tokens_in, ...） | `payload_json` + `metadata_json`（靈活 JSON） |
| 壓縮支援 | 無（全部推給 S014） | Schema 預留 `synthetic` flag + `SUMMARY` event type；SPI 由 S014 定義 |
| Fork 支援 | 無 | `grimo_session.parent_id` + `fork_turn`（schema 預留，Claude Code `/branch` 對齊） |
| Turn 模型 | 每輪 1 筆 row（user+assistant 混合） | 每輪 2 筆 events（USER + ASSISTANT 分離），turn-safe 壓縮基礎 |
| 其餘 | 同 v2 | Decorator + Modulith event 機制不變 |

---

## 2. Approach

### 2.1 v1 / v2 否定原因（摘要）

| 版本 | 否定原因 |
|------|---------|
| v1 `JdbcChatMemoryRepository` | `content` 存純文字，metadata 完全丟棄；`saveAll()` 全量替換（DELETE + re-INSERT）；H2 2.4.240 `timestamp` 保留字衝突 |
| v2 Flat table | 可行但 schema 不夠靈活 — 新增 metadata 欄位就要 ALTER TABLE；USER / ASSISTANT 混在同一 row 不利 turn-safe compaction；無 fork 追蹤 |

### 2.2 選型比較（v3 更新）

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| **A: Event Sourcing + Projection** | ⭐ yes | Append-only event store 天然適合對話記錄。`payload_json` + `metadata_json` 靈活不受欄位束縛。USER / ASSISTANT 分離事件支援 turn-safe compaction（S014）。Projection 表提供快速摘要查詢。`parent_id` + `fork_turn` 預留 Claude Code `/branch` 對齊。Schema 預留 `synthetic` flag 讓 S014 即插即用。 |
| B: spring-ai-session `SessionService` | no | `SessionMemoryAdvisor` 掛載 `ChatClient` advisor chain，**無法掛載** `AgentSession.prompt()`（S011 POC 已驗證）。`AgentResponse` → Spring AI `Message` 轉換有阻抗（agent-specific metadata 無法自然映射）。0.2.0 仍是 pre-1.0 incubating（Spring AI 2.1 2026-11 才 GA）。 |
| C: v2 Flat table | no | 使用者否決 — 希望一次設計好 session 基礎。Schema 不夠靈活，無 fork 追蹤，USER/ASSISTANT 混合不利 turn-safe compaction。 |

### 2.3 核心設計：Event Sourcing + Projection

```
RecordingAgentSession.prompt(userText):
  1. response = delegate.prompt(userText)            // 阻塞，等 CLI 回應
  2. turn = turnCounter.incrementAndGet()
  3. eventPublisher.publishEvent(                     // 非同步記錄
       new TurnRecorded(sessionId, turn, userText, response))
  4. return response                                  // 零額外延遲

TurnRecorder.on(TurnRecorded event):
  1. INSERT grimo_session_event (type=USER,  payload={text}, metadata=null)
  2. INSERT grimo_session_event (type=ASSISTANT, payload={text, toolCalls}, metadata={model, duration, tokens, ...})
  3. UPDATE grimo_session SET turn_count += 1, total_tokens += ..., last_active_at = now()
```

**為什麼 Event Sourcing：**
- 對話 IS an event stream — append-only 是自然寫入模式
- Event store 保留全量歷史（Recall Storage — 壓縮後仍可查詢）
- USER / ASSISTANT 分離 → turn-safe compaction（所有策略以 turn 為原子單位，永不切割 turn 中間）
- Projection 可從 events 重建（未來升級容錯）
- `synthetic` flag 區分原始事件 vs 壓縮合成摘要

**為什麼 payload_json + metadata_json：**
- 不受 flat column 束縛 — 新增 metadata 欄位不需 ALTER TABLE
- Tool call 內容（未來）可直接存入 payload_json
- 與 T3 Code 的 `payload_json` + `metadata_json` 模式一致
- 查詢仍可用 H2 JSON 函數或 application-level 反序列化

**為什麼 Decorator 而非 Advisor：**
- `AgentCallAdvisor`（agent-client）→ 攔截 `AgentClient.run()`（無狀態單次呼叫），**不攔截** `AgentSession.prompt()`
- `SessionMemoryAdvisor`（spring-ai-session）→ 掛載 `ChatClient` pipeline，不是 `AgentSession`
- Decorator 在 `AgentSession` interface 層包裝，是唯一乾淨的擴展方式

### 2.4 從 SessionMemoryAdvisor 借鑑的概念

| SessionMemoryAdvisor 功能 | S017 對應設計 | 實作時機 |
|--------------------------|-------------|---------|
| **Turn 概念**（USER → 下一個 USER 之間為一個 turn） | `turn_number` 欄位，USER event 遞增 | S017 ✅ |
| **Synthetic 合成事件** | `synthetic BOOLEAN` + `SUMMARY` event type（schema 預留） | S017 schema ✅ |
| **Recall Storage**（壓縮後仍可關鍵字搜尋全量歷史） | Event store append-only 天然保留全量；`findBySessionId()` | S017 ✅ |
| **Turn-safe compaction** | USER / ASSISTANT 分離事件，`turn_number` 為 compaction 邊界 | S014（SPI + 策略） |
| **CompactionTrigger / CompactionStrategy** | — | S014（定義 + 實作） |
| **Branch isolation**（multi-agent event 隔離） | **不借鑑** — spring-ai-session 的 `branch` 是 multi-agent hierarchy，非對話 fork | — |

### 2.5 對話 Fork 設計（Schema 預留）

Claude Code 有三種 fork 機制：`/branch [name]`（session 內分岔）、`--fork-session`（CLI flag）、`/rewind`（rollback 自動 fork）。核心語意：**建立新 session ID，複製歷史至 fork 點，此後獨立**。

Grimo 以 `grimo_session` projection 表的 `parent_id` + `fork_turn` 追蹤 fork 關係：

```
Root session (id=A, parent_id=null, fork_turn=null)
  ├── Branch 1 (id=B, parent_id=A, fork_turn=5)
  └── Branch 2 (id=C, parent_id=A, fork_turn=8)
       └── Sub-branch (id=D, parent_id=C, fork_turn=3)
```

**SDK 已支援 fork：** `AgentSession.fork()` 方法存在於 agent-model 0.12.2。呼叫後回傳新 `AgentSession`（新 session ID，歷史已複製）。若 provider 不支援則拋 `UnsupportedOperationException`。`RecordingAgentSession.fork()` delegate 呼叫並包裝回傳值，確保 forked session 也有 recording。

**S017 scope：** schema 預留（`parent_id` + `fork_turn` 欄位）+ decorator 的 `fork()` delegate。Parent-child 關係寫入（偵測 fork → UPDATE `grimo_session` projection）為後續 spec。

### 2.6 Schema

```sql
-- Event Store（append-only, immutable）
CREATE TABLE IF NOT EXISTS grimo_session_event (
    sequence      BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id      VARCHAR(36)  NOT NULL UNIQUE,
    session_id    VARCHAR(36)  NOT NULL,
    turn_number   INT          NOT NULL,
    event_type    VARCHAR(30)  NOT NULL,
    payload_json  CLOB         NOT NULL,
    metadata_json CLOB,
    synthetic     BOOLEAN      DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_grimo_evt_session
    ON grimo_session_event(session_id, sequence);
CREATE INDEX IF NOT EXISTS idx_grimo_evt_turn
    ON grimo_session_event(session_id, turn_number);

-- Session Projection（物化摘要 + fork 關係）
CREATE TABLE IF NOT EXISTS grimo_session (
    id                VARCHAR(36)  PRIMARY KEY,
    parent_id         VARCHAR(36),
    fork_turn         INT,
    provider          VARCHAR(20)  NOT NULL DEFAULT 'claude',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    turn_count        INT          DEFAULT 0,
    total_tokens_in   BIGINT       DEFAULT 0,
    total_tokens_out  BIGINT       DEFAULT 0,
    total_duration_ms BIGINT       DEFAULT 0,
    work_dir          VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL,
    last_active_at    TIMESTAMP    NOT NULL,
    FOREIGN KEY (parent_id) REFERENCES grimo_session(id)
);
```

**event_type 值域：** `USER` | `ASSISTANT` | `SUMMARY`（未來可擴展 `TOOL_CALL` / `TOOL_RESULT`）

**payload_json 格式：**

```jsonc
// USER event
{ "text": "幫我重構 OrderService" }

// ASSISTANT event
{ "text": "好的，我來分析目前的結構..." }

// SUMMARY event（壓縮產生，synthetic=true）
{ "text": "前 5 輪討論了 OrderService 的六邊形重構方案..." }
```

**metadata_json 格式（ASSISTANT event）：**

```jsonc
{
  "model": "claude-sonnet-4-20250514",
  "durationMs": 2340,
  "tokensIn": 150,
  "tokensOut": 42,
  "finishReason": "SUCCESS",
  "provider": "claude",
  "cliSessionId": "abc-123"
}
```

**session_id 說明：** 使用 Claude CLI 分配的 UUID（36 chars），非 Grimo NanoID。`AgentSession.getSessionId()` 回傳值。core domain 的 `SessionId`（21-char NanoID）為 Grimo 內部概念，未來 fork 管理或跨 CLI 切換時可建立映射。

### 2.7 Projection 更新策略

同一個 `@ApplicationModuleListener` 中，INSERT events + UPDATE projection（同步）。單 JVM 不需要 T3 Code 的獨立 projector（`projection_state` + `last_applied_sequence`）。

保留 event store 的 `sequence` AUTO_INCREMENT 欄位 — 未來如需獨立 projector 或 event replay，已有排序基礎。

### 2.8 H2 Datasource 配置

維持現有 `application.yaml` 不變（H2 file mode, PostgreSQL mode）。`schema.sql` 將以新 schema 替換現有 `grimo_conversation_turn` 表。

### 2.9 模組設計

新建 `session` Modulith 模組（從 Backlog 晉升）：

```
io.github.samzhu.grimo.session
├── package-info.java                    # @ApplicationModule, allowedDependencies = { "core" }
├── domain/
│   ├── SessionEvent.java                # record — event store 值物件
│   ├── SessionProjection.java           # record — session 摘要
│   ├── EventType.java                   # enum: USER, ASSISTANT, SUMMARY
│   ├── SessionStatus.java              # enum: ACTIVE, CLOSED
│   ├── EventFilter.java                 # record — 查詢條件
│   └── (S014 新增 CompactionTrigger / CompactionStrategy)
├── events/
│   ├── package-info.java                # @NamedInterface("events")
│   └── TurnRecorded.java               # Modulith event record
├── application/
│   ├── port/in/
│   │   ├── package-info.java            # @NamedInterface("api")
│   │   └── SessionHistoryUseCase.java     # getEvents(), findById()
│   ├── port/out/
│   │   ├── SessionEventPort.java   # append, findBySessionId
│   │   └── SessionProjectionPort.java   # upsert, findById
│   └── service/
│       ├── SessionHistoryService.java     # 用例實作
│       └── TurnRecorder.java    # @ApplicationModuleListener — 處理 event
├── adapter/out/
│   ├── JdbcSessionEventAdapter.java     # JdbcTemplate → grimo_session_event
│   └── JdbcSessionProjectionAdapter.java # JdbcTemplate → grimo_session
└── internal/
    ├── RecordingAgentSession.java        # AgentSession decorator
    ├── RecordingAgentSessionRegistry.java # @Primary, wraps ClaudeAgentSessionRegistry
    └── SessionConfig.java              # Bean wiring + ObjectMapper
```

**跨模組接線：**
- `agent` 模組注入 `AgentSessionRegistry`（Spring bean）— `@Primary` 自動取得 `RecordingAgentSessionRegistry`（透明包裝）
- `agent` 的 `allowedDependencies` **不需要改動** — `@Primary` 透明注入不引用 `session` 模組型別
- `TurnRecorded` event 放在 `session/events/`（`@NamedInterface("events")`）— 未來 `cost` 模組可宣告 `allowedDependencies = { "session::events" }` 監聽
- S014 在 `session` 模組內新增 `CompactionTrigger` / `CompactionStrategy` SPI + 實作

### 2.10 S014 處置

S017 提供 event store schema（含 `synthetic` flag + `SUMMARY` event type）。S014 負責定義 Compaction SPI 介面 + 實作具體策略 + 基準測試：

| S017 提供給 S014 的基礎 | S014 全權負責 |
|-------------------------|-------------|
| `grimo_session_event` 表（`synthetic` 欄位 + `SUMMARY` event type） | `CompactionTrigger` interface + 實作（TurnCount / TokenCount） |
| `SessionEventPort.append()` / `findBySessionId()` | `CompactionStrategy` interface + 實作（SlidingWindow / RecursiveSummarization） |
| `turn_number` 欄位（turn 邊界） | 整合至 decorator + 基準測試 |

### 2.11 Research Citations

- **Claude Code `/branch`** — v2.1.77 將 `/fork` 改名為 `/branch`。建立新 session ID，複製歷史，此後獨立。`--fork-session` CLI flag 為程式化版本。`/rewind` 自動 fork。[CLI reference](https://code.claude.com/docs/en/cli-reference)、[GitHub #35143](https://github.com/anthropics/claude-code/issues/35143)
- **Spring AI Session API（Part 7 blog）** — event-sourced short-term memory，turn-safe compaction，four strategies，branch isolation（multi-agent 用途，非對話 fork）。Incubating → Spring AI 2.1（2026-11）時 ChatMemory 將 deprecated。[blog](https://spring.io/blog/2026/04/15/spring-ai-session-management)
- **T3 Code Event Sourcing** — `orchestration_events`（append-only）+ 8 張 projection 表 + `payload_json` + `metadata_json`。Sequence-based partial replay。[competitive-analysis.md §3.1](docs/local/competitive-analysis.md)
- **`AgentSession` SPI** — `prompt()` 是攔截點；`fork()` 回傳新 session（歷史已複製）；`resume()` 恢復 DEAD session。無 `getMessages()` API。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentSession.java)
- **`AgentCallAdvisor` chain** — `AgentCallAdvisor.adviseCall(request, chain)` 攔截 `AgentClient.run()`（非 `AgentSession.prompt()`）。命名模式：`[Purpose]Advisor`。[source](https://github.com/spring-ai-community/agent-client/tree/main/agent-client-core/src/main/java/org/springaicommunity/agents/client/advisor)
- **`AgentResponseMetadata`** — `getModel()` → String、`getDuration()` → Duration、`getSessionId()` → String。extends `HashMap<String,Object>`，`getProviderFields()` 回傳 `this`。**無標準 token count getter** — `inputTokens`/`outputTokens` 僅在 providerFields 中（provider-dependent，可能不存在）。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentResponseMetadata.java)
- **`AgentGenerationMetadata`** — `getFinishReason()` → String（非 enum）。常見值：`"SUCCESS"` / `"COMPLETE"` / `"ERROR"` / `"TIMEOUT"` / `"CANCELLED"`。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentGenerationMetadata.java)
- **`JdbcChatMemoryRepository`** — 只存 `content` + `type`，metadata 丟棄。`saveAll()` 全量替換。POC 否決。[source](https://github.com/spring-projects/spring-ai/blob/main/memory/repository/spring-ai-model-chat-memory-repository-jdbc/src/main/java/org/springframework/ai/chat/memory/repository/jdbc/JdbcChatMemoryRepository.java)
- **`CorrelationId` vs `traceId`** — `CorrelationId` 是 domain-layer ephemeral SSE scoping key（不持久化）；`traceId` 是 infra-layer OTel 概念。兩者語意不同，皆不存入 event store。[core/domain/CorrelationId.java](src/main/java/io/github/samzhu/grimo/core/domain/CorrelationId.java)
- **session-design-research.md** — H2 配置、SQLite 否決、記憶分層研究。[local](docs/local/session-design-research.md)

---

## 3. SBE Acceptance Criteria

**驗證命令：** `./gradlew test`
Pass: 所有攜帶 S017 AC id 的測試為綠色。

---

**AC-1: Event Store — 每輪產生 USER + ASSISTANT 兩筆 events**

```
Given  RecordingAgentSession 包裝了一個 stub AgentSession
       （回傳 model="claude-sonnet-4-20250514", duration=2s, finishReason="SUCCESS"）
When   呼叫 prompt("hello") 且 delegate 回傳 "Hi there"
Then   grimo_session_event 表中存在 2 筆記錄
And    第 1 筆: event_type="USER", turn_number=1, payload_json 含 {"text":"hello"}, metadata_json IS NULL, synthetic=false
And    第 2 筆: event_type="ASSISTANT", turn_number=1, payload_json 含 {"text":"Hi there"}, synthetic=false
And    第 2 筆 metadata_json 含 model="claude-sonnet-4-20250514", durationMs>0, finishReason="SUCCESS"
And    兩筆 session_id 與 delegate 的 sessionId 一致
And    兩筆 event_id 為合法 UUID 且不重複
And    created_at 為真實時間戳
```

**AC-2: 多輪累積（append-only + turn 遞增）**

```
Given  RecordingAgentSession 已記錄 2 輪對話
When   呼叫第 3 輪 prompt("bye")
Then   grimo_session_event 表中存在 6 筆記錄（每輪 2 筆）
And    turn_number 分別為 1, 1, 2, 2, 3, 3
And    每筆為獨立 INSERT（非 DELETE + re-INSERT）
And    sequence 嚴格遞增
```

**AC-3: Session Projection 自動物化**

```
Given  RecordingAgentSession 完成 3 輪對話
       （每輪 metadata 含 tokensIn=100, tokensOut=50, durationMs=2000）
When   查詢 grimo_session 表
Then   存在 1 筆記錄，id = delegate 的 sessionId
And    turn_count = 3
And    total_tokens_in = 300, total_tokens_out = 150
And    total_duration_ms = 6000
And    status = 'ACTIVE'
And    last_active_at >= created_at
```

**AC-4: Registry decorator 透明包裝**

```
Given  RecordingAgentSessionRegistry 包裝了一個 stub AgentSessionRegistry
When   呼叫 create(workDir)
Then   回傳的 AgentSession 是 RecordingAgentSession
And    getSessionId() 與底層 session 一致
And    getWorkingDirectory() 與底層一致
When   呼叫 find(sessionId)
Then   回傳的 AgentSession 也是 RecordingAgentSession 包裝
```

**AC-5: Fork schema 預留**

```
Given  應用以 H2 embedded mode 啟動
When   schema.sql 自動執行
Then   grimo_session 表存在 parent_id VARCHAR(36) 欄位（nullable）
And    grimo_session 表存在 fork_turn INT 欄位（nullable）
And    parent_id 有 FOREIGN KEY 指向 grimo_session(id)
```

**AC-6: Modulith 邊界合規**

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

// session/domain/EventType.java
public enum EventType {
    USER, ASSISTANT, SUMMARY
}

// session/domain/SessionStatus.java
public enum SessionStatus {
    ACTIVE, CLOSED
}

// session/domain/SessionEvent.java
public record SessionEvent(
    Long sequence,             // auto-increment PK
    String eventId,            // UUID
    String sessionId,          // CLI-assigned session ID
    int turnNumber,            // USER event 遞增
    EventType eventType,
    String payloadJson,        // message content (JSON)
    String metadataJson,       // model/duration/tokens/... (JSON, nullable)
    boolean synthetic,         // true = compaction 產生的合成事件
    Instant createdAt
) {}

// session/domain/SessionProjection.java
public record SessionProjection(
    String id,                 // = session_id
    String parentId,           // null = root session
    Integer forkTurn,          // fork 時的 turn number
    String provider,
    SessionStatus status,
    int turnCount,
    long totalTokensIn,
    long totalTokensOut,
    long totalDurationMs,
    String workDir,
    Instant createdAt,
    Instant lastActiveAt
) {}

// session/domain/EventFilter.java
// Aligns with spring-ai-session EventFilter — query criteria for events
public record EventFilter(
    Integer lastN,           // null = all
    Boolean excludeSynthetic // null = include all
) {
    public static EventFilter all() { return new EventFilter(null, null); }
    public static EventFilter lastTurns(int n) { return new EventFilter(n * 2, null); }
    public static EventFilter realOnly() { return new EventFilter(null, true); }
}

// (CompactionTrigger / CompactionStrategy — S014 在此新增)

// === session module: events (exposed via @NamedInterface("events")) ===

// session/events/TurnRecorded.java
public record TurnRecorded(
    String sessionId,
    int turnNumber,
    String userMessage,
    AgentResponse agentResponse
) {}

// === session module: port/in (exposed via @NamedInterface("api")) ===
// Method names align with spring-ai-session SessionService

// session/application/port/in/SessionHistoryUseCase.java
public interface SessionHistoryUseCase {
    List<SessionEvent> getEvents(String sessionId);
    List<SessionEvent> getEvents(String sessionId, EventFilter filter);
    Optional<SessionProjection> findById(String sessionId);
}

// === session module: port/out ===

// session/application/port/out/SessionEventPort.java
// Aligns with SessionRepository.appendEvent / findEvents
public interface SessionEventPort {
    void append(SessionEvent event);
    List<SessionEvent> findBySessionId(String sessionId);
    List<SessionEvent> findBySessionId(String sessionId, EventFilter filter);
}

// session/application/port/out/SessionProjectionPort.java
public interface SessionProjectionPort {
    void upsert(SessionProjection projection);
    Optional<SessionProjection> findById(String sessionId);
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
            new TurnRecorded(getSessionId(), turn, message, response));
        return response;
    }

    @Override
    public AgentSession fork() {
        // fork() may throw UnsupportedOperationException if provider doesn't support it
        AgentSession forked = delegate.fork();
        return new RecordingAgentSession(forked, eventPublisher);
        // TODO(future-spec): persist parent-child in grimo_session projection
    }

    // getSessionId(), getWorkingDirectory(), getStatus(), close(), resume() → delegate
}

// session/internal/RecordingAgentSessionRegistry.java
@Primary
class RecordingAgentSessionRegistry implements AgentSessionRegistry {
    private final AgentSessionRegistry delegate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AgentSession create(Path workDir) {
        AgentSession raw = delegate.create(workDir);
        return new RecordingAgentSession(raw, eventPublisher);
    }

    @Override
    public Optional<AgentSession> find(String sessionId) {
        return delegate.find(sessionId)
            .map(s -> new RecordingAgentSession(s, eventPublisher));
    }
    // evict, evictStale → delegate
}

// session/application/service/TurnRecorder.java
@ApplicationModuleListener
class TurnRecorder {
    private final SessionEventPort eventStore;
    private final SessionProjectionPort projectionStore;
    private final ObjectMapper objectMapper;

    void on(TurnRecorded event) {
        var response = event.agentResponse();
        var now = Instant.now();

        // 1. USER event
        eventStore.append(new SessionEvent(
            null, UUID.randomUUID().toString(), event.sessionId(),
            event.turnNumber(), EventType.USER,
            toJson(Map.of("text", event.userMessage())),
            null, false, now));

        // 2. ASSISTANT event
        eventStore.append(new SessionEvent(
            null, UUID.randomUUID().toString(), event.sessionId(),
            event.turnNumber(), EventType.ASSISTANT,
            toJson(Map.of("text", response.getText())),
            buildMetadataJson(response),
            false, now));

        // 3. Update projection
        updateProjection(event.sessionId(), response.getMetadata());
    }

    // AgentResponseMetadata: getModel(), getDuration(), getSessionId()
    // AgentGenerationMetadata: getFinishReason() → String ("SUCCESS"/"ERROR"/...)
    // Token counts: NOT in standard API — only in providerFields (HashMap)
    private String buildMetadataJson(AgentResponse response) {
        var meta = response.getMetadata();              // AgentResponseMetadata
        var genMeta = response.getResult().getMetadata(); // AgentGenerationMetadata

        var map = new LinkedHashMap<String, Object>();
        map.put("model", meta.getModel());
        map.put("durationMs", meta.getDuration().toMillis());
        map.put("finishReason", genMeta.getFinishReason());
        map.put("provider", "claude");
        // tokens — provider-dependent, stored in providerFields HashMap
        var tokensIn = meta.get("inputTokens");
        if (tokensIn instanceof Number n) map.put("tokensIn", n.longValue());
        var tokensOut = meta.get("outputTokens");
        if (tokensOut instanceof Number n) map.put("tokensOut", n.longValue());
        return toJson(map);
    }
}

// session/adapter/out/JdbcSessionEventAdapter.java
class JdbcSessionEventAdapter implements SessionEventPort {
    private final JdbcTemplate jdbc;

    @Override
    public void append(SessionEvent e) {
        jdbc.update("""
            INSERT INTO grimo_session_event
            (event_id, session_id, turn_number, event_type,
             payload_json, metadata_json, synthetic, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            e.eventId(), e.sessionId(), e.turnNumber(),
            e.eventType().name(), e.payloadJson(), e.metadataJson(),
            e.synthetic(), Timestamp.from(e.createdAt()));
    }

    @Override
    public List<SessionEvent> findBySessionId(String sessionId) {
        return jdbc.query("""
            SELECT * FROM grimo_session_event
            WHERE session_id = ? ORDER BY sequence ASC""",
            this::mapRow, sessionId);
    }
}
```

---

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `src/main/resources/schema.sql` | **replace** | 移除 `grimo_conversation_turn`，新增 `grimo_session_event` + `grimo_session` + indexes |
| `session/package-info.java` | new | `@ApplicationModule(allowedDependencies = { "core" })` |
| `session/domain/SessionEvent.java` | new | Event store 值物件 record |
| `session/domain/SessionProjection.java` | new | Session 摘要 record |
| `session/domain/EventType.java` | new | enum: USER, ASSISTANT, SUMMARY |
| `session/domain/SessionStatus.java` | new | enum: ACTIVE, CLOSED |
| `session/domain/EventFilter.java` | new | record — 查詢條件（對齊 spring-ai-session `EventFilter`） |
| `session/events/package-info.java` | new | `@NamedInterface("events")` |
| `session/events/TurnRecorded.java` | new | Modulith event record |
| `session/application/port/in/package-info.java` | new | `@NamedInterface("api")` |
| `session/application/port/in/SessionHistoryUseCase.java` | new | 入站埠 |
| `session/application/port/out/SessionEventPort.java` | new | Event store 出站埠 |
| `session/application/port/out/SessionProjectionPort.java` | new | Projection 出站埠 |
| `session/application/service/SessionHistoryService.java` | new | 用例實作 |
| `session/application/service/TurnRecorder.java` | new | `@ApplicationModuleListener` — 寫 event store + 更新 projection |
| `session/adapter/out/JdbcSessionEventAdapter.java` | new | JdbcTemplate → grimo_session_event |
| `session/adapter/out/JdbcSessionProjectionAdapter.java` | new | JdbcTemplate → grimo_session |
| `session/internal/RecordingAgentSession.java` | new | AgentSession decorator |
| `session/internal/RecordingAgentSessionRegistry.java` | new | `@Primary` Registry decorator |
| `session/internal/SessionConfig.java` | new | Bean wiring + ObjectMapper |
| `src/test/java/.../session/TurnRecorderTest.java` | new | AC-1, AC-2, AC-3（stub delegate + embedded H2） |
| `src/test/java/.../session/RecordingRegistryTest.java` | new | AC-4 |
| `src/test/java/.../session/SchemaTest.java` | new | AC-5（H2 schema 驗證） |

> **注意：** 所有 `session/` 路徑的完整前綴為 `src/main/java/io/github/samzhu/grimo/session/`；測試的完整前綴為 `src/test/java/io/github/samzhu/grimo/session/`。

---

### 估算

| 維度 | 分數 | 理由 |
|------|------|------|
| 技術風險 | 2 | Event sourcing pattern + JSON 序列化（ObjectMapper） |
| 不確定性 | 1 | 純記錄 + projection，無 SPI 設計不確定性 |
| 依賴關係 | 1 | S007 + S011 已出貨 |
| 範疇 | 3 | Event store + projection + 2 JDBC adapters + decorator + schema |
| 測試 | 2 | 單元 + H2 embedded 整合 |
| 可逆性 | 2 | 新模組，但 @Primary 覆蓋現有 bean + schema.sql 替換 |
| **合計** | **11** | **S** |

### Doc Sync

- [ ] `spec-roadmap.md` — S017 估算更新為 S(11)，描述更新 ✅
- [ ] `architecture.md` §2 模組地圖 — 新增 `session` 模組（從 §2.x Backlog 晉升）
- [ ] `architecture.md` §5.2 — 更新 schema 描述（`grimo_session_event` + `grimo_session` 取代 `grimo_conversation_turn`）
- [ ] `glossary.md` — 新增 Session Event、Session Projection、Turn、Compaction 詞條
- [ ] `spec-roadmap.md` S014 描述 — 更新為「Compaction SPI + 策略實作 + 基準測試」 ✅
