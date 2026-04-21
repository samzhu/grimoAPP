# S017: Grimo Session Memory — Event-Sourced 對話歷史（v3.1 Provider-Agnostic）

> Spec: S017 | Size: S (11) | Status: ⏳ Design
> Date: 2026-04-20 (v3 — Event Sourcing + Projection)
> Updated: 2026-04-21 (v3.1 — Provider-Agnostic metadata extraction + runtime switch)

---

## 1. Goal

**一句話：** 使用者每說一句話、AI 每回一句話，Grimo 都自動存進資料庫，不管底層用哪個 CLI（Claude / Codex / Gemini）。

**具體做法：** 用一個 `RecordingAgentSession` 包在真正的 `AgentSession` 外面（Decorator pattern）。使用者呼叫 `prompt()` 時，先讓底層 CLI 回應，再把「使用者說了什麼」和「AI 回了什麼」存進 H2 資料庫。使用者完全無感，零額外延遲。

```
使用者輸入 "hello"
       │
       ▼
RecordingAgentSession.prompt("hello")
       │
       ├──① 轉交給底層 CLI ──→ ClaudeAgentSession.prompt("hello")
       │                              │
       │                              ▼
       │                        CLI 回應 "Hi there"
       │                              │
       ├──② 收到回應 ◄───────────────┘
       │
       ├──③ 發 event（非同步）──→ TurnRecorder 寫入 DB：
       │                           INSERT grimo_session_event (USER,  "hello")
       │                           INSERT grimo_session_event (ASSISTANT, "Hi there")
       │                           UPDATE grimo_session SET turn_count = 1
       │
       └──④ 回傳 "Hi there" 給使用者
```

### 兩層分離設計（PRD P3「Grimo 擁有 session」）

對話記錄同時存在兩個地方，各管各的：

| 層 | 誰管 | 存在哪 | 用途 |
|---|--------|------|------|
| **Grimo 層** | `RecordingAgentSession`（我們寫的 decorator） | H2 資料庫（`grimo_session_event` + `grimo_session`） | 跨 CLI 切換、歷史查詢、壓縮、fork 追蹤 |
| **CLI 層** | Claude / Codex / Gemini 各自管 | 各 CLI 原生格式（不相容） | 各 CLI 的 `--resume` / `--continue` 等原生功能 |

> **為什麼需要兩層？** 各家 CLI 的 session 檔案格式不相容。使用者從 Claude 切換到 Codex 時，Codex 讀不懂 Claude 的 `*.jsonl`。Grimo 的 H2 event store 是唯一的跨 Provider 共通記憶 — 不管用哪個 CLI，Grimo 都有完整紀錄。

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
- **跨 Provider context injection**（切換 provider 時，從 event store 讀取歷史、組成摘要 prompt 注入新 session — 為後續 spec，S017 提供 event store 讀取基礎。對應 PRD AC3「CLI 切換保留對話上下文」）
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

**v3 → v3.1 變更（2026-04-21）：**

| 項目 | v3 設計 | v3.1 設計 |
|------|---------|----------|
| Provider 假設 | 硬編碼 `"claude"`（`buildMetadataJson` + schema DEFAULT） | `ProviderMetadataExtractor` SPI 動態辨識；schema 無 DEFAULT |
| Token 萃取 | 直接讀 `meta.get("inputTokens")` | 委派 `ProviderMetadataExtractor.extractTokens()` |
| `parent_id` 語意 | 僅 Claude Code `/branch` 對齊 | 也用於跨 Provider 切換追蹤（PRD AC3） |
| 程式模型 | 未明述 | 明確記載 `AgentModel`（無狀態）vs `AgentClient`（facade）vs `AgentSession`（多輪）選擇理由 |
| 新增檔案 | — | `ProviderMetadataExtractor.java`（SPI）+ `ClaudeMetadataExtractor.java`（實作） |

---

## 2. Approach

### 2.1 v1 / v2 否定原因（摘要）

| 版本 | 否定原因 |
|------|---------|
| v1 `JdbcChatMemoryRepository` | `content` 存純文字，metadata 完全丟棄；`saveAll()` 全量替換（DELETE + re-INSERT）；H2 2.4.240 `timestamp` 保留字衝突 |
| v2 Flat table | 可行但 schema 不夠靈活 — 新增 metadata 欄位就要 ALTER TABLE；USER / ASSISTANT 混在同一 row 不利 turn-safe compaction；無 fork 追蹤 |

### 2.2 為什麼包 AgentSession（不是 AgentModel 或 AgentClient）

agent-client SDK 提供三種用法，差別在「有沒有記憶」：

| 用法 | 一句話解釋 | 有記憶？ | Grimo 怎麼用 |
|------|-----------|:-------:|-------------|
| **`AgentModel.call()`** | 丟一個任務進去，拿結果出來 | ❌ 每次獨立 | 未來的 sub-agent：「修這個 bug」 |
| **`AgentClient.run()`** | 同上，但可以加攔截器 | ❌ 每次獨立 | 未來的 sub-agent + advisor |
| **`AgentSession.prompt()`** | 多輪對話，AI 記得之前說過什麼 | ✅ 跨 prompt 保持 | **主代理 REPL** ← S017 記錄的對象 |

**結論：** S017 的 decorator 包在 `AgentSession` 外面，攔截 `prompt()`。`AgentModel.call()` 的記錄（sub-agent 單次任務）為未來 spec。

### 2.3 選型比較（v3 更新）

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| **A: Event Sourcing + Projection** | ⭐ yes | Append-only event store 天然適合對話記錄。`payload_json` + `metadata_json` 靈活不受欄位束縛。USER / ASSISTANT 分離事件支援 turn-safe compaction（S014）。Projection 表提供快速摘要查詢。`parent_id` + `fork_turn` 預留 Claude Code `/branch` 對齊。Schema 預留 `synthetic` flag 讓 S014 即插即用。 |
| B: spring-ai-session `SessionService` | no | `SessionMemoryAdvisor` 掛載 `ChatClient` advisor chain，**無法掛載** `AgentSession.prompt()`（S011 POC 已驗證）。`AgentResponse` → Spring AI `Message` 轉換有阻抗（agent-specific metadata 無法自然映射）。0.2.0 仍是 pre-1.0 incubating（Spring AI 2.1 2026-11 才 GA）。 |
| C: v2 Flat table | no | 使用者否決 — 希望一次設計好 session 基礎。Schema 不夠靈活，無 fork 追蹤，USER/ASSISTANT 混合不利 turn-safe compaction。 |

### 2.4 核心設計：Event Sourcing + Projection

#### 資料流（一步步看）

當使用者輸入 `prompt("幫我重構 OrderService")` 時：

| 步驟 | 誰做的 | 做了什麼 |
|------|--------|---------|
| ① | `RecordingAgentSession` | 把訊息轉交給底層 CLI（`delegate.prompt()`），**等 CLI 回應** |
| ② | Claude / Codex / Gemini | 處理完畢，回傳 `AgentResponse`（含文字、model、token、耗時） |
| ③ | `RecordingAgentSession` | turn +1，發 `TurnRecorded` event（Spring Modulith 非同步事件） |
| ④ | `RecordingAgentSession` | **立刻**把 `AgentResponse` 回傳給使用者（零延遲） |
| ⑤ | `TurnRecorder`（非同步） | 收到 event → 寫 2 筆 INSERT（USER + ASSISTANT）→ UPDATE projection 摘要 |

```
RecordingAgentSession.prompt(userText):
  1. response = delegate.prompt(userText)            // 阻塞，等 CLI 回應
  2. turn = turnCounter.incrementAndGet()
  3. eventPublisher.publishEvent(                     // 非同步，不影響回應速度
       new TurnRecorded(sessionId, turn, userText, response))
  4. return response                                  // 使用者拿到回應

TurnRecorder.on(TurnRecorded event):                  // 非同步處理
  1. INSERT grimo_session_event (type=USER,  payload={text}, metadata=null)
  2. INSERT grimo_session_event (type=ASSISTANT, payload={text}, metadata={model, tokens, ...})
  3. UPDATE grimo_session SET turn_count += 1, total_tokens += ..., last_active_at = now()
```

#### 設計選擇的理由

**為什麼用 Event Sourcing（不是普通的表格）：**
- 對話本身就是一連串事件（你說一句、我說一句） — append-only 是最自然的寫法
- 永遠不刪除舊紀錄 — 壓縮後仍可搜尋完整歷史
- USER 和 ASSISTANT 分開存 — 未來做摘要壓縮時，可以整輪（user+assistant）為單位壓縮，不會切到一半
- 萬一 projection 表壞了，可從 event store 重建

**為什麼用 JSON 欄位（不是 flat columns）：**
- 不同 provider 的 metadata 格式不同 — JSON 靈活，不需為每個新欄位 ALTER TABLE
- 新增 tool call 記錄、新增 provider 欄位，都不影響 schema

**為什麼用 Decorator（不是 Advisor）：**
- SDK 的 `AgentCallAdvisor` 攔截的是 `AgentClient.run()`（單次任務），攔截不到 `AgentSession.prompt()`（多輪對話）
- `SessionMemoryAdvisor` 是給 `ChatClient` 用的，不是 `AgentSession`
- Decorator 包在 `AgentSession` interface 外面，是唯一不侵入 SDK 的方式

### 2.5 從 spring-ai-session 借鑑的設計模式

spring-ai-session（0.3.0-SNAPSHOT）是 Spring AI 生態系的 event-sourced session memory 實作。S017 借鑑其五大設計模式，但因 Grimo 的攔截點不同（`AgentSession.prompt()` vs `ChatClient` advisor），採用 decorator 而非 advisor。

| spring-ai-session 設計模式 | S017 對應設計 | 實作時機 |
|--------------------------|-------------|---------|
| **Event-sourced log** — append-only `SessionEvent` wrapping `Message` | `grimo_session_event` + `payload_json`（包 `AgentResponse` 而非 `Message`） | S017 ✅ |
| **Composable EventFilter** — from/to/messageTypes/keyword/lastN/page/branch | `EventFilter` record 對齊相同欄位 | S017 ✅ |
| **Turn-boundary safety** — 策略 snap 到 USER message，不切割 tool-call 序列 | `turn_number` 欄位 = compaction 邊界。USER / ASSISTANT 分離事件 | S014（策略實作） |
| **Optimistic concurrency (CAS)** — `event_version` + `replaceEvents(id, events, expectedVersion)` | `grimo_session.event_version` schema 預留 | S014（compaction 需要） |
| **Branch isolation** — dot-separated `branch` 欄位隔離 peer sub-agent 事件 | `grimo_session_event.branch` schema 預留（null = root） | 未來 sub-agent spec |

> **為什麼不直接用 spring-ai-session？** `SessionMemoryAdvisor` 掛載 `ChatClient` advisor chain，攔截的是 `ChatClient.prompt()`。Grimo 主代理用 `AgentSession.prompt()`（agent-client SDK），兩者是不同的 API 層，無法互通（S011 POC 已驗證）。但其 schema 設計和 compaction 框架是優秀的參考。

### 2.6 對話 Fork 設計（Schema 預留 + 跨 Provider 切換）

`grimo_session` 表的 `parent_id` + `fork_turn` 用來記錄「這個 session 是從哪裡分出來的」。涵蓋兩種場景：

**場景 A — 同一個 CLI 的對話分岔。** 例如 Claude Code 的 `/branch` 指令。對話到 turn 5 時分岔，新 session 從 turn 5 之後獨立發展。SDK 的 `AgentSession.fork()` 方法已定義，但 Claude 目前尚未實作（拋 `UnsupportedOperationException`）。

**場景 B — 切換到不同的 CLI。** 使用者跟 Claude 聊了 5 輪，覺得想試試 Codex。各家 CLI 的 session 檔案格式完全不同（Claude: `*.jsonl`、Codex: 自己的格式），無法互通。Grimo 的 event store 是唯一跨 Provider 的共通記憶。

```
grimo_session 表：

id=A  provider=claude  parent_id=null  fork_turn=null  ← 原始 session
id=B  provider=claude  parent_id=A     fork_turn=5     ← 場景 A：Claude 內部分岔
id=C  provider=codex   parent_id=A     fork_turn=5     ← 場景 B：切換到 Codex
id=D  provider=codex   parent_id=C     fork_turn=3     ← Codex session 再分岔
```

> **對比 T3 Code：** T3 Code 沒有 fork，只有 in-place revert（在同一個 thread 裡回退）。Grimo 的做法是建立新 session（「平行宇宙」），兩條路線共存 — 這對跨 Provider 切換和未來的 Jury 模式（多個 CLI 並行比較）都必要。

**S017 scope：** 只做 schema 預留（欄位存在但不寫入值）+ decorator 的 `fork()` delegate。實際的 fork / switch 寫入邏輯為後續 spec。

### 2.7 Schema

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
    branch        VARCHAR(500),                                    -- v3.1 預留：多 Agent 分支隔離（dot-separated path，對齊 spring-ai-session）
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
    provider          VARCHAR(20)  NOT NULL,    -- 由 ProviderMetadataExtractor 動態填入（v3.1：移除 DEFAULT 'claude'）
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    turn_count        INT          DEFAULT 0,
    total_tokens_in   BIGINT       DEFAULT 0,
    total_tokens_out  BIGINT       DEFAULT 0,
    total_duration_ms BIGINT       DEFAULT 0,
    event_version     BIGINT       DEFAULT 0,   -- v3.1 預留：CAS optimistic concurrency（S014 compaction 用）
    work_dir          VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL,
    last_active_at    TIMESTAMP    NOT NULL,
    FOREIGN KEY (parent_id) REFERENCES grimo_session(id)
);
```

#### 範例：使用者跟 Claude 對話 2 輪後，DB 長這樣

**`grimo_session_event`（Event Store — 每輪 2 筆，共 4 筆）：**

| seq | event_id | session_id | turn | type | payload_json | metadata_json | synthetic | branch |
|-----|----------|------------|------|------|-------------|---------------|-----------|--------|
| 1 | `e1a...` | `sess-abc` | 1 | USER | `{"text":"幫我重構 OrderService"}` | NULL | false | NULL |
| 2 | `e2b...` | `sess-abc` | 1 | ASSISTANT | `{"text":"好的，我來分析..."}` | `{"model":"claude-sonnet-4-20250514",...,"provider":"claude"}` | false | NULL |
| 3 | `e3c...` | `sess-abc` | 2 | USER | `{"text":"改成六邊形架構"}` | NULL | false | NULL |
| 4 | `e4d...` | `sess-abc` | 2 | ASSISTANT | `{"text":"六邊形架構的核心概念是..."}` | `{"model":"claude-sonnet-4-20250514",...,"provider":"claude"}` | false | NULL |

> `branch = NULL` 代表主代理（root）的對話。未來 sub-agent 的事件會帶 `branch = "agent.sub1"` 等 dot-separated path。

**`grimo_session`（Projection — 整個 session 一筆摘要）：**

| id | parent_id | fork_turn | provider | status | turn_count | tokens_in | tokens_out | duration_ms | event_ver | work_dir |
|----|-----------|-----------|----------|--------|------------|-----------|------------|-------------|-----------|----------|
| `sess-abc` | NULL | NULL | claude | ACTIVE | 2 | 470 | 227 | 5440 | 4 | `/Users/sam/myapp` |

> `event_ver = 4` 代表目前有 4 筆 event。S014 compaction 用 CAS：`UPDATE ... SET event_version = 5 WHERE event_version = 4`。

**接著使用者切換到 Codex 繼續（跨 Provider switch）：**

| id | parent_id | fork_turn | provider | status | turn_count | tokens_in | tokens_out | duration_ms | event_ver | work_dir |
|----|-----------|-----------|----------|--------|------------|-----------|------------|-------------|-----------|----------|
| `sess-abc` | NULL | NULL | claude | ACTIVE | 2 | 470 | 227 | 5440 | 4 | `/Users/sam/myapp` |
| `sess-xyz` | `sess-abc` | 2 | codex | ACTIVE | 0 | 0 | 0 | 0 | 0 | `/Users/sam/myapp` |

> `sess-xyz` 的 `parent_id = sess-abc`、`fork_turn = 2`，代表「從 Claude session 的第 2 輪之後切換過來的」。

#### 欄位說明

**event_type 值域：** `USER` | `ASSISTANT` | `SUMMARY`（未來可擴展 `TOOL_CALL` / `TOOL_RESULT`）

- `USER` — 使用者輸入的訊息。`metadata_json` 為 NULL（使用者端沒有 model / token 資訊）。
- `ASSISTANT` — AI 回應。`metadata_json` 記錄用了哪個模型、花多久、消耗多少 token。
- `SUMMARY` — 壓縮摘要（S014 產生），`synthetic = true`。

**metadata_json 的欄位來源（ASSISTANT event）：**

| 欄位 | 來源 | 說明 |
|------|------|------|
| `model` | `AgentResponseMetadata.getModel()` | 所有 provider 都有，標準 API |
| `durationMs` | `AgentResponseMetadata.getDuration()` | 所有 provider 都有，標準 API |
| `finishReason` | `AgentGenerationMetadata.getFinishReason()` | 所有 provider 都有，String 型別 |
| `provider` | `ProviderMetadataExtractor.providerName()` | **v3.1：** 由 extractor 動態辨識，非硬編碼 |
| `tokensIn` | `ProviderMetadataExtractor.extractTokens()` | **Provider-specific：** Claude 放在 `providerFields.get("inputTokens")`，其他 CLI 的 key 可能不同 |
| `tokensOut` | 同上 | 同上 |

**session_id 說明：** 使用 CLI 分配的 UUID（36 chars），來自 `AgentSession.getSessionId()`。不同 provider 的 session ID 格式可能不同，但都存為 VARCHAR(36)。

### 2.8 Projection 怎麼更新

`TurnRecorder` 收到 event 後，在同一個 method 裡做三件事：INSERT USER event → INSERT ASSISTANT event → UPDATE projection（加 turn_count、加 tokens）。不需要像 T3 Code 那樣搞獨立的 projector — 我們是單一 JVM，一個 listener 搞定。

`sequence` 欄位保留 AUTO_INCREMENT — 萬一未來需要 event replay，有排序基礎。

### 2.9 H2 Datasource 配置

維持現有 `application.yaml` 不變（H2 file mode, PostgreSQL mode）。`schema.sql` 將以新 schema 替換現有 `grimo_conversation_turn` 表。

### 2.10 模組設計

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
│   │   ├── SessionProjectionPort.java   # upsert, findById
│   │   └── ProviderMetadataExtractor.java # provider-agnostic token 萃取 SPI（v3.1）
│   └── service/
│       ├── SessionHistoryService.java     # 用例實作
│       └── TurnRecorder.java    # @ApplicationModuleListener — 處理 event
├── adapter/out/
│   ├── JdbcSessionEventAdapter.java     # JdbcTemplate → grimo_session_event
│   └── JdbcSessionProjectionAdapter.java # JdbcTemplate → grimo_session
└── internal/
    ├── RecordingAgentSession.java        # AgentSession decorator
    ├── RecordingAgentSessionRegistry.java # @Primary, wraps any AgentSessionRegistry
    ├── ClaudeMetadataExtractor.java      # ProviderMetadataExtractor for Claude（v3.1）
    └── SessionConfig.java              # Bean wiring + ObjectMapper
```

**跨模組接線：**
- `agent` 模組注入 `AgentSessionRegistry`（Spring bean）— `@Primary` 自動取得 `RecordingAgentSessionRegistry`（透明包裝任何 provider 的 registry）
- `agent` 的 `allowedDependencies` **不需要改動** — `@Primary` 透明注入不引用 `session` 模組型別
- `ClaudeMetadataExtractor` 在 `session/internal/` — module-internal，不對外暴露。未來新增 provider（Codex/Gemini）的 extractor 也放在此處
- `TurnRecorded` event 放在 `session/events/`（`@NamedInterface("events")`）— 未來 `cost` 模組可宣告 `allowedDependencies = { "session::events" }` 監聽
- S014 在 `session` 模組內新增 `CompactionTrigger` / `CompactionStrategy` SPI + 實作

### 2.11 S014 處置

S017 提供 event store schema（含 `synthetic` flag + `SUMMARY` event type）。S014 負責定義 Compaction SPI 介面 + 實作具體策略 + 基準測試：

| S017 提供給 S014 的基礎 | S014 全權負責 |
|-------------------------|-------------|
| `grimo_session_event` 表（`synthetic` + `SUMMARY` + `branch`） | `CompactionTrigger` interface + 實作（TurnCount / TokenCount） |
| `grimo_session.event_version`（CAS 版本號） | `replaceEvents(sessionId, events, expectedVersion)` CAS 操作（對齊 spring-ai-session） |
| `SessionEventPort.append()` / `findBySessionId()` | `CompactionStrategy` interface + 實作（SlidingWindow / RecursiveSummarization） |
| `turn_number` 欄位（turn 邊界） | Turn-boundary safety：策略 snap 到 USER message，不切割 tool-call 序列 |

### 2.12 Provider-Agnostic 設計（v3.1 新增）

#### 問題

Grimo 是桌面應用，使用者可以隨時切換 provider（Claude → Codex → Gemini）。Session 記錄層不能寫死只支援 Claude。

#### 哪些是共通的、哪些是 provider-specific

| 資訊 | 來源 | 跨 provider？ |
|------|------|:------------:|
| model 名稱 | `AgentResponseMetadata.getModel()` | ✅ 所有 provider 都有 |
| 耗時 | `AgentResponseMetadata.getDuration()` | ✅ |
| session ID | `AgentResponseMetadata.getSessionId()` | ✅ |
| 完成原因 | `AgentGenerationMetadata.getFinishReason()` | ✅ |
| token 用量 | `providerFields` HashMap | ❌ **key 名不同** — Claude 叫 `inputTokens`，Codex 可能叫 `usage.prompt_tokens` |

**結論：** 只有 token 萃取需要 provider-specific 邏輯。用 Strategy pattern 解決。

#### 解法：`ProviderMetadataExtractor` SPI

```java
public interface ProviderMetadataExtractor {
    String providerName();                     // "claude" | "codex" | "gemini"
    boolean supports(AgentResponse response);  // 看 model 名稱前綴判斷
    Map<String, Object> extractTokens(AgentResponse response);
}
```

**範例 — Claude 的實作：**

```java
@Component
class ClaudeMetadataExtractor implements ProviderMetadataExtractor {
    public String providerName() { return "claude"; }

    public boolean supports(AgentResponse r) {
        // model = "claude-sonnet-4-20250514" → startsWith("claude-") → true
        return r.getMetadata().getModel().startsWith("claude-");
    }

    public Map<String, Object> extractTokens(AgentResponse r) {
        // Claude 把 token 放在 providerFields 的 "inputTokens" / "outputTokens"
        var map = new HashMap<String, Object>();
        if (r.getMetadata().get("inputTokens") instanceof Number n)
            map.put("tokensIn", n.longValue());
        if (r.getMetadata().get("outputTokens") instanceof Number n)
            map.put("tokensOut", n.longValue());
        return map;
    }
}
```

**未來加 Codex？** 只需加一個 class：

```java
@Component
class CodexMetadataExtractor implements ProviderMetadataExtractor {
    public String providerName() { return "codex"; }
    public boolean supports(AgentResponse r) {
        return r.getMetadata().getModel().startsWith("codex-");
    }
    // extractTokens() — 按 Codex 的 providerFields key 萃取
}
```

Spring 自動注入 `List<ProviderMetadataExtractor>`，`TurnRecorder` 用 `supports()` 匹配。**零配置、零 if-else。**

#### Bean 接線

```
使用者選 Claude：
  agent module → AgentSessionRegistry → RecordingAgentSessionRegistry (@Primary)
                                              └── wraps ClaudeAgentSessionRegistry

使用者改選 Codex（未來）：
  agent module → AgentSessionRegistry → RecordingAgentSessionRegistry (@Primary)
                                              └── wraps CodexAgentSessionRegistry
```

`RecordingAgentSessionRegistry` 包的是 `AgentSessionRegistry` **interface**，不管底層是哪家 CLI。目前只有 `ClaudeAgentSessionRegistry` 一個 bean，constructor injection 自動注入。未來多 provider 時，由上層 factory 控制只有一個 active。

### 2.13 Research Citations

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
- **`AgentModel` SPI** — `@FunctionalInterface`，無狀態單次任務：`call(AgentTaskRequest) → AgentResponse`。不用於多輪對話。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentModel.java)
- **`AgentClient` facade** — 包裝 `AgentModel`，加上 advisor chain + MCP + options。`run(goal)` 仍是單次呼叫，不維護對話狀態。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-client-core/src/main/java/org/springaicommunity/agents/client/AgentClient.java)
- **`ClaudeAgentSession.fork()` 現況** — 0.12.2 版直接拋 `UnsupportedOperationException("fork() is not yet implemented")`。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-claude/src/main/java/org/springaicommunity/agents/claude/ClaudeAgentSession.java)
- **T3 Code fork 機制** — 無 session-level fork，只有 in-place revert（同一 threadId 內追加 `thread.reverted` 事件）。Checkpoint 用 orphan commit git refs（`refs/t3/checkpoints/{threadId}/turn/{N}`）。Provider 對話回退為 in-memory turns 截斷 + resume cursor。[competitive-analysis.md §3.1](docs/local/competitive-analysis.md)
- **Agent Sessions 官方文件** — `AgentSession` 生命週期（ACTIVE → DEAD → RESUMED）、`AgentSessionRegistry` 工廠模式、Spring `@Scheduled` stale cleanup、startup health probe。**限制：** fork() 尚未實作；僅 Claude 有實作；in-memory registry 不跨重啟持久化；session 綁定單一 workDir；resumed session 含完整歷史（無 context pruning）。[doc](https://springaicommunity.mintlify.app/agent-client/sessions)
- **claude-agent-sdk-java** — Claude CLI 的 Java SDK（底層）。三種 API：`Query`（一次性）、`ClaudeSyncClient`（阻塞多輪）、`ClaudeAsyncClient`（Reactor）。`ClaudeSyncClient` 有 `setModel()` / `setPermissionMode()` 可中途切換。`CLIOptions` record 含 30+ 參數（model、mcpServers、maxBudgetUsd、forkSession 等）。`HookRegistry` 支援 preToolUse / postToolUse / userPromptSubmit。`ResultMessage` 含 `totalCostUsd`、`usage`（inputTokens + outputTokens + cache tokens）。[repo](https://github.com/spring-ai-community/claude-agent-sdk-java)
- **`AgentClientAdapter`** — `BiFunction<String, Path, AgentClientAdapterResponse>` bridge，僅用於接 `spring-ai-judge`。與 session 無關，adapter 套件只有此 class + response record。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-client-core/src/main/java/org/springaicommunity/agents/client/adapter/AgentClientAdapter.java)
- **`ClaudeAgentSessionRegistry.create()` 橋接範圍** — 從 `ClaudeAgentOptions` 只取 `model` + `mcpServers` 傳給 `CLIOptions`；其餘 28+ 個參數（systemPrompt、allowedTools、maxTurns、maxBudgetUsd 等）未橋接。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-claude/src/main/java/org/springaicommunity/agents/claude/ClaudeAgentSessionRegistry.java)
- **spring-ai-session（0.3.0-SNAPSHOT）** — Event-sourced session memory for Spring AI。五大設計模式：(1) append-only `SessionEvent` wrapping `Message`；(2) composable `EventFilter`（from/to/messageTypes/keyword/lastN/page/branch）；(3) turn-boundary safety（策略 snap 到 USER message）；(4) CAS optimistic concurrency（`event_version` + `replaceEvents`）；(5) branch isolation（dot-separated path 實作多 Agent 可見性隔離）。S017 的 `EventFilter`、schema `branch` 欄位、`event_version` 欄位均對齊此設計。[repo](https://github.com/spring-ai-community/spring-ai-session) · [doc](https://springaicommunity.mintlify.app/agent-client/sessions)

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

**AC-7: Provider-agnostic metadata 萃取（v3.1 新增）**

```
Given  ClaudeMetadataExtractor 已註冊
And    stub AgentResponse 的 model = "claude-sonnet-4-20250514"
When   TurnRecorder 處理 TurnRecorded 事件
Then   grimo_session_event 的 ASSISTANT event metadata_json 含 "provider":"claude"
And    metadata_json 含 tokensIn、tokensOut（從 providerFields 萃取）
And    grimo_session 的 provider = "claude"（非硬編碼，由 extractor 動態填入）
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
    String branch,             // v3.1 預留：dot-separated agent path（null = root）
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
    long eventVersion,         // v3.1 預留：CAS optimistic concurrency（S014 compaction 用）
    String workDir,
    Instant createdAt,
    Instant lastActiveAt
) {}

// session/domain/EventFilter.java
// Aligns with spring-ai-session EventFilter — composable query criteria
// v3.1: 擴充至與 spring-ai-session 對齊，預留未來查詢需求
public record EventFilter(
    Instant from,              // null = 不限起始時間
    Instant to,                // null = 不限結束時間
    Set<EventType> eventTypes, // null = 全部；{USER} = 只看使用者訊息
    Boolean excludeSynthetic,  // null = 包含全部；true = 排除壓縮摘要
    Integer lastN,             // null = 全部；N = 最後 N 筆
    String keyword,            // null = 不搜尋；非 null = payload_json LIKE 搜尋
    Integer page,              // null = 不分頁
    Integer pageSize,          // null = 不分頁
    String branch              // null = root；"agent.sub1" = 只看該 branch 可見事件
) {
    // 常用工廠方法
    public static EventFilter all() { return new EventFilter(null, null, null, null, null, null, null, null, null); }
    public static EventFilter lastTurns(int n) { return new EventFilter(null, null, null, null, n * 2, null, null, null, null); }
    public static EventFilter realOnly() { return new EventFilter(null, null, null, true, null, null, null, null, null); }
    public static EventFilter forBranch(String branch) { return new EventFilter(null, null, null, null, null, null, null, null, branch); }
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

// session/application/port/out/ProviderMetadataExtractor.java (v3.1 新增)
// Strategy for provider-specific metadata extraction from AgentResponse.
// TurnRecorder injects List<ProviderMetadataExtractor> and matches via supports().
public interface ProviderMetadataExtractor {
    /** Provider identifier: "claude", "codex", "gemini" */
    String providerName();
    /** Match by model name prefix in AgentResponseMetadata.getModel() */
    boolean supports(AgentResponse response);
    /** Extract provider-specific token counts from providerFields HashMap */
    Map<String, Object> extractTokens(AgentResponse response);
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
    private final List<ProviderMetadataExtractor> extractors; // v3.1: provider-agnostic
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

        // 3. Update projection (provider name from extractor)
        String provider = resolveProvider(response);
        updateProjection(event.sessionId(), response.getMetadata(), provider);
    }

    // v3.1: provider-agnostic metadata extraction
    // Standard fields (model, duration, finishReason) from AgentResponseMetadata — all providers.
    // Provider-specific fields (tokens, provider name) from ProviderMetadataExtractor.
    private String buildMetadataJson(AgentResponse response) {
        var meta = response.getMetadata();              // AgentResponseMetadata
        var genMeta = response.getResult().getMetadata(); // AgentGenerationMetadata

        var map = new LinkedHashMap<String, Object>();
        // Standard fields — all providers
        map.put("model", meta.getModel());
        map.put("durationMs", meta.getDuration().toMillis());
        map.put("finishReason", genMeta.getFinishReason());

        // Provider-specific fields via ProviderMetadataExtractor
        extractors.stream()
            .filter(e -> e.supports(response))
            .findFirst()
            .ifPresent(e -> {
                map.put("provider", e.providerName());
                map.putAll(e.extractTokens(response));
            });

        return toJson(map);
    }

    private String resolveProvider(AgentResponse response) {
        return extractors.stream()
            .filter(e -> e.supports(response))
            .findFirst()
            .map(ProviderMetadataExtractor::providerName)
            .orElse("unknown");
    }
}

// session/internal/ClaudeMetadataExtractor.java (v3.1 新增)
@Component
class ClaudeMetadataExtractor implements ProviderMetadataExtractor {

    @Override
    public String providerName() { return "claude"; }

    @Override
    public boolean supports(AgentResponse response) {
        String model = response.getMetadata().getModel();
        return model != null && model.startsWith("claude-");
    }

    @Override
    public Map<String, Object> extractTokens(AgentResponse response) {
        var meta = response.getMetadata();
        var map = new HashMap<String, Object>();
        // Claude provider stores tokens in providerFields HashMap
        if (meta.get("inputTokens") instanceof Number n)
            map.put("tokensIn", n.longValue());
        if (meta.get("outputTokens") instanceof Number n)
            map.put("tokensOut", n.longValue());
        return map;
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
| `session/application/port/out/ProviderMetadataExtractor.java` | new | Provider-agnostic metadata 萃取 SPI（v3.1） |
| `session/application/service/SessionHistoryService.java` | new | 用例實作 |
| `session/application/service/TurnRecorder.java` | new | `@ApplicationModuleListener` — 寫 event store + 更新 projection |
| `session/adapter/out/JdbcSessionEventAdapter.java` | new | JdbcTemplate → grimo_session_event |
| `session/adapter/out/JdbcSessionProjectionAdapter.java` | new | JdbcTemplate → grimo_session |
| `session/internal/RecordingAgentSession.java` | new | AgentSession decorator |
| `session/internal/RecordingAgentSessionRegistry.java` | new | `@Primary` Registry decorator |
| `session/internal/ClaudeMetadataExtractor.java` | new | Claude provider 的 `ProviderMetadataExtractor` 實作（v3.1） |
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
- [ ] `spec-roadmap.md` — S017 描述更新：加入 provider-agnostic + cross-provider switch 語意
- [ ] `architecture.md` §2 模組地圖 — 新增 `session` 模組（從 §2.x Backlog 晉升）
- [ ] `architecture.md` §5.2 — 更新 schema 描述（`grimo_session_event` + `grimo_session` 取代 `grimo_conversation_turn`）
- [ ] `glossary.md` — 新增 Session Event、Session Projection、Turn、Compaction、ProviderMetadataExtractor 詞條
- [ ] `spec-roadmap.md` S014 描述 — 更新為「Compaction SPI + 策略實作 + 基準測試」 ✅
- [ ] `architecture.md` §2.x Backlog — `session` 模組描述更新：移除 "SessionMemoryAdvisor 接線"，改為 "Event-sourced session memory + provider-agnostic metadata"

---

## Appendix A: 為什麼用 Decorator 而非 Advisor — 完整研究紀錄

> 本附錄記錄 2026-04-21 的研究過程，證明 Decorator 是唯一可行方案，並記錄所有被排除的替代方案及其排除理由。

### A.1 agent-client SDK 的兩條獨立路線

agent-client 專案提供兩種完全獨立的 API，**不互相包裝**：

```
路線 A：單次任務（無記憶）
  AgentClient.goal("修 bug").run()
    → advisor chain（AgentCallAdvisor 攔截點）
      → AgentModelCallAdvisor（terminal）
        → AgentModel.call(AgentTaskRequest)
          → AgentResponse

路線 B：多輪對話（有記憶）
  AgentSession.prompt("hello")
    → 直接呼叫 provider 實作（無攔截點）
      → ClaudeAgentSession 跟 CLI 對話
        → AgentResponse
```

Grimo 主代理是多輪對話 REPL（`MainAgentChatService.runRepl()`），走的是路線 B。

### A.2 被排除的方案

#### 方案 X1：AgentCallAdvisor

**排除理由：攔截不到 `AgentSession.prompt()`。**

`AgentCallAdvisor.adviseCall()` 的 signature：
```java
AgentClientResponse adviseCall(AgentClientRequest request, AgentCallAdvisorChain chain);
```

操作的型別是 `AgentClientRequest` / `AgentClientResponse`（路線 A 的型別）。`AgentSession.prompt(String)` 回傳 `AgentResponse`，完全不經過 `AgentClient` 的 advisor chain。

**原始碼驗證：** `DefaultAgentClient.run()` 內部呼叫的是 `AgentModel.call()`（透過 `AgentModelCallAdvisor`），不是 `AgentSession.prompt()`。

#### 方案 X2：AgentSessionAdvisor（假設 SDK 提供）

**排除理由：不存在。**

搜尋 `agent-client-core` 完整目錄，`advisor/api/` 套件只有 3 個檔案：
- `AgentAdvisor.java`（父介面，定義 `getName()` + `Ordered`）
- `AgentCallAdvisor.java`（攔截 `AgentClient.run()`）
- `AgentCallAdvisorChain.java`（chain 合約）

**沒有 `AgentSessionAdvisor`。** SDK 目前不提供 session-level 的攔截機制。

#### 方案 X3：AgentClientAdapter

**排除理由：只是 Judge 橋接器，跟 session 無關。**

`AgentClientAdapter` 實作 `BiFunction<String, Path, AgentClientAdapterResponse>`，用途是把 `AgentClient` 接到 `spring-ai-judge` 的 `JudgeAgentClient` 介面：
```java
public AgentClientAdapterResponse execute(String goal, Path workspace) {
    AgentClientResponse response = agentClient.goal(goal).workingDirectory(workspace).run();
    return new AgentClientAdapterResponse(response.getResult(), response.isSuccessful());
}
```

`adapter` 套件只有 2 個檔案（`AgentClientAdapter` + `AgentClientAdapterResponse`），沒有 `AgentSessionAdapter`。

#### 方案 X4：SessionMemoryAdvisor（spring-ai-session）

**排除理由：掛載 `ChatClient` pipeline，不是 `AgentSession`。**

S011 POC 已驗證：`SessionMemoryAdvisor` 是 Spring AI 的 `ChatClient` advisor，攔截的是 `ChatClient.prompt()`。`AgentSession.prompt()` 是完全不同的 API 層，兩者無法互通。

### A.3 Decorator 可行性驗證

`AgentSession` 是 Java interface：
```java
public interface AgentSession extends AutoCloseable {
    String getSessionId();
    Path getWorkingDirectory();
    AgentSessionStatus getStatus();
    AgentResponse prompt(String message);  // ← 攔截點
    AgentSession resume();
    AgentSession fork();
    void close();
}
```

Decorator pattern 直接實作此 interface，所有方法 delegate 給底層 session，只在 `prompt()` 前後加記錄邏輯。**不侵入 SDK、不修改任何上游程式碼、不依賴 SDK 未提供的擴展點。**

`AgentSessionRegistry` 也是 interface：
```java
public interface AgentSessionRegistry {
    AgentSession create(Path workingDirectory);
    Optional<AgentSession> find(String sessionId);
    void evict(String sessionId);
    void evictStale(Duration inactiveSince);
}
```

`RecordingAgentSessionRegistry` 以 `@Primary` 包裝任何 `AgentSessionRegistry` 實作，`agent` 模組透過 Spring DI 自動取得包裝後的版本，**零侵入、零配置**。

### A.4 未來 sub-agent 的記錄方式

Sub-agent 用 `AgentClient.run()` 做單次任務（路線 A），此時 `AgentCallAdvisor` **正好是框架原生的攔截方式**。未來 spec 可實作 `RecordingCallAdvisor implements AgentCallAdvisor`，在 advisor chain 中攔截 `AgentClient.run()` 的 request / response。

| 場景 | API | 記錄方式 | 理由 |
|------|-----|---------|------|
| 主代理（多輪對話） | `AgentSession.prompt()` | **Decorator**（S017） | SDK 無 session-level advisor |
| Sub-agent（單次任務） | `AgentClient.run()` | **AgentCallAdvisor**（未來 spec） | SDK 原生 advisor chain |

### A.5 研究來源

| 檔案 | 版本 | 路徑 | 確認內容 |
|------|------|------|---------|
| `AgentSession.java` | 0.12.2 | `agent-model/src/.../AgentSession.java` | interface 定義，`prompt()` 無攔截機制 |
| `AgentSessionRegistry.java` | 0.12.2 | `agent-model/src/.../AgentSessionRegistry.java` | interface 定義，可被 `@Primary` decorator 包裝 |
| `ClaudeAgentSession.java` | 0.12.2 | `agent-claude/src/.../ClaudeAgentSession.java` | `fork()` 拋 `UnsupportedOperationException` |
| `AgentCallAdvisor.java` | 0.12.2 | `agent-client-core/src/.../advisor/api/AgentCallAdvisor.java` | 操作 `AgentClientRequest`，非 `AgentSession` |
| `DefaultAgentClient.java` | 0.12.2 | `agent-client-core/src/.../DefaultAgentClient.java` | `run()` 透過 advisor chain 呼叫 `AgentModel.call()`，不經過 `AgentSession` |
| `AgentClientAdapter.java` | 0.12.2 | `agent-client-core/src/.../adapter/AgentClientAdapter.java` | Judge 橋接器，`BiFunction<String, Path, Response>`，與 session 無關 |
| `AgentModel.java` | 0.12.2 | `agent-model/src/.../AgentModel.java` | `@FunctionalInterface`，`call(AgentTaskRequest)` 無狀態 |

---

## Appendix B: T3 Code Fork 機制對照

> 研究 T3 Code 的「分支」設計，確認 Grimo 的 session-level fork 是更適合多 CLI 場景的選擇。

### B.1 T3 Code 做法：in-place revert（非 fork）

T3 Code **沒有 session-level fork**。它的「回到某個時間點再繼續」是同一個 thread 內的原地回退：

```
Thread A: [turn1] → [turn2] → [turn3] → [turn4] → [turn5]
                                  ↑ revert to turn 3
Thread A: [turn1] → [turn2] → [turn3] → [thread.reverted] → [turn4'] → ...
                                            ↑ 新事件追加在同一個 stream
```

三步 revert：
1. **Git checkpoint restore** — `git restore --source {commitOid} --worktree --staged -- .`
2. **Provider 對話截斷** — in-memory `turns.splice(nextLength)` + resume cursor 更新
3. **Projection 清理** — 每張表 delete + re-insert 保留到 revert 點的資料

### B.2 Grimo 做法：session-level fork（平行宇宙）

建立新 session ID，兩條路線共存：

```
Session A (claude): [turn1] → [turn2] → [turn3] → [turn4] → [turn5]
                                                         ↑ fork_turn=5
Session B (codex):  獨立的 event stream，parent_id=A ────┘
```

### B.3 為什麼 Grimo 不用 T3 Code 的做法

| 維度 | T3 Code (in-place revert) | Grimo (session-level fork) |
|------|---------------------------|---------------------------|
| 跨 Provider 切換 | ❌ thread 綁定單一 provider | ✅ 新 session 可用不同 provider |
| 並行比較 | ❌ 同一 thread 只有一條路線 | ✅ 兩個 session 可並行（Jury 模式基礎） |
| Event store 查詢 | 需過濾被 revert 的 events | 每個 session 的 stream 是乾淨線性序列 |
| 歷史保留 | 舊 events 保留但被邏輯覆蓋 | 每個 fork 的 events 獨立完整 |

### B.4 研究來源

- T3 Code repo: `packages/orchestration/` — event types、`CheckpointReactor`、`ProjectionPipeline`
- T3 Code checkpoint: `refs/t3/checkpoints/{base64url(threadId)}/turn/{N}` orphan commit git refs
- T3 Code provider rollback: Claude `context.turns.splice(nextLength)` + `updateResumeCursor()`
- 詳見 `docs/local/competitive-analysis.md` §3.1

---

## Appendix C: AgentSession 上游限制與 Grimo 的補位

> 記錄 `AgentSession`（agent-client 0.12.2）和 `ClaudeSyncClient`（claude-agent-sdk-java 1.0.0）的已知限制，以及 Grimo event store 如何補位。

### C.1 AgentSession 官方限制（來源：sessions 文件）

| 限制 | 說明 | Grimo 如何補位 |
|------|------|---------------|
| **fork() 未實作** | `ClaudeAgentSession.fork()` 拋 `UnsupportedOperationException` | S017 schema 預留 `parent_id` + `fork_turn`，decorator 的 `fork()` delegate — 上游實作後自動生效 |
| **僅 Claude 有實作** | 其他 provider 無 `AgentSessionRegistry` | S017 decorator 包 `AgentSession` interface — 未來任何 provider 實作都自動被包裝 |
| **In-memory registry** | `ConcurrentHashMap`，app 重啟後 session 全丟 | Grimo 的 H2 event store 持久化每輪對話，重啟後歷史仍在。未來可用 persisted session ID + `resume()` 恢復 CLI session |
| **No context pruning** | resumed session 含完整歷史，無法刪除早期 turn | S014（Compaction SPI）在 Grimo 層壓縮歷史，產生 SUMMARY synthetic event，減少 bootstrap prompt 的 token 消耗 |
| **綁定單一 workDir** | session 建立後不能換目錄 | Grimo 的 `grimo_session.work_dir` 記錄目錄，切換目錄 = 建新 session（parent_id 追蹤） |
| **prompt() 同步阻塞** | 呼叫後等 CLI 回應才 return | RecordingAgentSession 在 return 前發 event（非同步寫 DB），不增加延遲 |

### C.2 SDK 三層架構與各層能力

```
┌─ agent-client 0.12.2 ──────────────────────────────────────────┐
│  AgentClient.goal().run() → AgentModel.call()   單次任務       │
│  AgentSession.prompt()    → 多輪對話             ← S017 記錄點  │
│  AgentCallAdvisor         → 攔截 AgentClient     ← 攔截不到 Session │
│  AgentClientAdapter       → Judge 橋接           ← 與 session 無關 │
├─ agent-claude 0.12.2 ─────────────────────────────────────────┤
│  ClaudeAgentSession       → 持有 ClaudeSyncClient             │
│  ClaudeAgentSessionRegistry.create():                          │
│    只橋接 CLIOptions 的 model + mcpServers + timeout           │
│    未橋接: systemPrompt, allowedTools, maxTurns,               │
│           maxBudgetUsd, forkSession, addDirs... (28+ 參數)    │
├─ claude-agent-sdk-java 1.0.0 ─────────────────────────────────┤
│  ClaudeSyncClient:                                             │
│    connectText() / queryText()  ← 多輪對話                     │
│    setModel() / setPermissionMode()  ← 中途改配置              │
│  CLIOptions (30+ 參數)                                         │
│  HookRegistry (preToolUse / postToolUse / stop)                │
│  McpServerConfig (stdio / sse / http / sdk 四種)               │
│  ResultMessage: totalCostUsd, usage, sessionId, numTurns       │
└────────────────────────────────────────────────────────────────┘
```

### C.3 S017 不受上游限制影響的原因

S017 的 decorator 在 `AgentSession` **interface 層**運作：

1. **不依賴 `fork()` 實作** — decorator 的 `fork()` 只是 delegate + 包裝回傳值。上游拋 exception 就 pass-through
2. **不依賴特定 provider** — `RecordingAgentSession` 包的是 interface，不是 `ClaudeAgentSession`
3. **不依賴 registry 持久化** — Grimo 有自己的 H2 event store，不靠 `ConcurrentHashMap`
4. **不依賴 context pruning** — 壓縮由 S014 在 Grimo 層處理，不需 SDK 支援
5. **不依賴 `CLIOptions` 橋接** — S017 記錄的是 `prompt()` 的輸入輸出，不管 CLI 怎麼配置

### C.4 未來 spec 可利用的 SDK 能力

| SDK 能力 | 未來 spec | 說明 |
|----------|----------|------|
| `ClaudeSyncClient.setModel()` | 成本路由器 | 同一 session 內根據任務複雜度中途換模型 |
| `HookRegistry.preToolUse()` | 主代理唯讀 | Hook 攔截寫入工具，實現 PRD P2 讀寫非對稱 |
| `McpServerConfig.McpSdkServerConfig` | MCP 管理 | Grimo 自己的 MCP server 用同進程模式，零網路開銷 |
| `ResultMessage.totalCostUsd` | 成本遙測面板 | SDK 直接算好費用，不需 Grimo 自己算 |
| `CLIOptions.maxBudgetUsd` | 預算控制 | CLI 層級硬上限，超過自動停止 |
| `CLIOptions.appendSystemPrompt` | Skill 投影 | 除了檔案系統投影，也可用 system prompt 注入 skill 指引 |
