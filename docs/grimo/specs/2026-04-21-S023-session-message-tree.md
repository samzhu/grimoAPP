# S023: Session Message Tree — Adjacency List Branching

> Spec: S023 | Size: XS (7) | Status: 🔵 in-design
> Date: 2026-04-21

---

## 1. Goal

**一句話：** 將對話從線性列表改為樹狀結構，支援無限分支（regenerate、fork），對齊 ChatGPT / LibreChat 的 message-level tree 模式。

### 背景

S018 建立了 session event store，但對話是線性排列（按 `created_at` 排序）。使用者無法：
- Regenerate 一個 AI 回覆（同一個 USER 下掛多個 ASSISTANT children）
- Fork 對話（從任意 message 分支出新路徑）
- 切換到不同分支檢視

本 spec 在 `grimo_session_event` 加 `parent_event_id` 建立 Adjacency List tree，在 `grimo_session` 加 `current_event_id` 作為 branch 書籤。載入對話 = 一條 recursive CTE 從葉走到根。

### 依賴

S018 ✅（REST API Foundation — session event store + session projection）。

---

## 2. Approach

### 2.1 Schema 變更

S018 的 `schema.sql` 使用 DROP+CREATE 策略。直接修改 CREATE TABLE 語句即可。

**grimo_session_event 變更：**

```sql
-- 移除 branch VARCHAR(500)（被 parent_event_id 取代）
-- 新增 parent_event_id VARCHAR(36)（自我參照 FK）
CREATE TABLE IF NOT EXISTS grimo_session_event (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    session_id      VARCHAR(36)  NOT NULL,
    parent_event_id VARCHAR(36),              -- ← 新增：指向父 event
    message_type    VARCHAR(20)  NOT NULL,
    message_content TEXT,
    message_data    TEXT,
    provider        VARCHAR(20),
    model           VARCHAR(100),
    metadata        TEXT,
    synthetic       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_event_session
        FOREIGN KEY (session_id) REFERENCES grimo_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_event_parent
        FOREIGN KEY (parent_event_id) REFERENCES grimo_session_event(id)
);
```

**grimo_session 變更：**

```sql
CREATE TABLE IF NOT EXISTS grimo_session (
    ...existing columns...
    current_event_id  VARCHAR(36),            -- ← 新增：目前分支的葉節點
    FOREIGN KEY (project_id) REFERENCES grimo_project(id),
    CONSTRAINT fk_session_current_event
        FOREIGN KEY (current_event_id) REFERENCES grimo_session_event(id)
);
```

**範例資料（使用者聊了 2 輪後 regenerate 第 2 輪的 ASSISTANT 回覆）：**

```
grimo_session_event:
id    | session_id | parent_event_id | message_type | message_content
evt-1 | sess-1     | null            | USER         | hello
evt-2 | sess-1     | evt-1           | ASSISTANT    | Hi! How can I help?
evt-3 | sess-1     | evt-2           | USER         | 修 bug
evt-4 | sess-1     | evt-3           | ASSISTANT    | 好的，我來看看...  ← 原始回覆
evt-5 | sess-1     | evt-3           | ASSISTANT    | 讓我檢查一下...    ← regenerate

grimo_session:
id     | current_event_id | ...
sess-1 | evt-5            |     ← 書籤指向 regenerate 的新回覆
```

樹狀結構：
```
evt-1 (USER: hello)
└── evt-2 (ASSISTANT: Hi!)
    └── evt-3 (USER: 修 bug)
        ├── evt-4 (ASSISTANT: 好的...)    ← 分支 A
        └── evt-5 (ASSISTANT: 讓我檢查...)  ← 分支 B (current)
```

### 2.2 Recursive CTE — 載入對話路徑

從 `current_event_id` 走到根：

```sql
WITH RECURSIVE conversation(
    id, session_id, parent_event_id, message_type,
    message_content, message_data, provider, model,
    metadata, synthetic, created_at
) AS (
    SELECT CAST(id AS VARCHAR(36)),
           CAST(session_id AS VARCHAR(36)),
           CAST(parent_event_id AS VARCHAR(36)),
           CAST(message_type AS VARCHAR(20)),
           CAST(message_content AS CLOB),
           CAST(message_data AS CLOB),
           CAST(provider AS VARCHAR(20)),
           CAST(model AS VARCHAR(100)),
           CAST(metadata AS CLOB),
           CAST(synthetic AS BOOLEAN),
           CAST(created_at AS TIMESTAMP)
    FROM grimo_session_event
    WHERE id = ?
    UNION ALL
    SELECT e.id, e.session_id, e.parent_event_id, e.message_type,
           e.message_content, e.message_data, e.provider, e.model,
           e.metadata, e.synthetic, e.created_at
    FROM grimo_session_event e
    JOIN conversation c ON e.id = c.parent_event_id
)
SELECT * FROM conversation ORDER BY created_at ASC;
```

> **H2 限制：** Recursive CTE 所有欄位預設為 VARCHAR，需在 anchor query 用 CAST 指定型別。

### 2.3 TurnRecorder 變更

每輪寫入 USER + ASSISTANT 事件時：

1. 讀取 session 的 `current_event_id`（第一輪為 null）
2. USER event 的 `parent_event_id` = session 的 `current_event_id`
3. ASSISTANT event 的 `parent_event_id` = 剛寫入的 USER event id
4. 更新 session 的 `current_event_id` = ASSISTANT event id

```
Before turn:  session.current_event_id = evt-2
After turn:   USER(evt-3, parent=evt-2) → ASSISTANT(evt-4, parent=evt-3)
              session.current_event_id = evt-4
```

### 2.4 不包含（未來 spec）

| 項目 | 理由 |
|------|------|
| Fork REST API (`POST /api/chat/{id}/fork`) | 需要 UI 支援指定 fromEventId，S023 先建 tree 結構 |
| Branch 切換 REST API | 同上 |
| Session Compaction 與 tree 的交互 | S014 負責 |

### 2.5 Research Citations

- **H2 WITH RECURSIVE：** h2database.com/html/advanced.html — 支援，限制 UNION ALL，欄位預設 VARCHAR 需 CAST。
- **ChatGPT message tree：** 業界標準 — 每個 message 有一個 parent，session 有 current leaf pointer。LibreChat、Ably 同款。
- **S017 自我參照 FK：** S017 schema 的 `grimo_session.parent_id → grimo_session(id)` 驗證 H2 支援自我參照 FK。

---

## 3. SBE Acceptance Criteria

**驗證命令：** `./gradlew test`
Pass: 所有攜帶 S023 AC id 的測試為綠色。

---

**AC-1: parent_event_id 欄位存在且為自我參照 FK**

```
Given  schema.sql 已執行
When   查詢 grimo_session_event 的 metadata
Then   parent_event_id VARCHAR(36) nullable 欄位存在
And    FK 指向 grimo_session_event(id)
```

**AC-2: current_event_id 欄位存在且指向 event**

```
Given  schema.sql 已執行
When   查詢 grimo_session 的 metadata
Then   current_event_id VARCHAR(36) nullable 欄位存在
And    FK 指向 grimo_session_event(id)
```

**AC-3: TurnRecorder 建立 parent-child 鏈**

```
Given  一個新 session（current_event_id = null）
When   記錄 3 輪對話
Then   6 個 events 形成線性鏈：
       evt-1(parent=null) → evt-2(parent=evt-1) → evt-3(parent=evt-2)
       → evt-4(parent=evt-3) → evt-5(parent=evt-4) → evt-6(parent=evt-5)
And    session.current_event_id = evt-6（最後一個 ASSISTANT event）
```

**AC-4: Recursive CTE 從葉走到根**

```
Given  AC-3 的 6 個 events
When   以 current_event_id (evt-6) 執行 recursive CTE
Then   回傳 6 個 events，按 created_at ASC 排序
And    第一個是 evt-1（根），最後一個是 evt-6（葉）
```

**AC-5: 分支場景 — 同一 parent 有多個 children**

```
Given  evt-1(USER) → evt-2(ASSISTANT)
When   以 evt-1 為 parent 插入第二個 ASSISTANT event (evt-3)
And    以 evt-3 為 current_event_id 執行 recursive CTE
Then   回傳 [evt-1, evt-3]（不含 evt-2）
```

**AC-6: branch 欄位已移除**

```
Given  schema.sql 已執行
When   查詢 grimo_session_event 的 column metadata
Then   branch 欄位不存在
```

---

## 4. Interface / API Design

### 4.1 Domain 變更

```java
// SessionEvent — 加 parentEventId，移除 branch
public record SessionEvent(
    String id,
    String sessionId,
    @Nullable String parentEventId,     // ← 新增
    MessageType messageType,
    @Nullable String messageContent,
    @Nullable String messageData,
    @Nullable String provider,
    @Nullable String model,
    @Nullable String metadata,
    boolean synthetic,
    Instant createdAt
) {}

// SessionProjection — 加 currentEventId
public record SessionProjection(
    String id,
    String sessionType,
    @Nullable String projectId,
    SessionStatus status,
    int turnCount,
    long totalTokensIn,
    long totalTokensOut,
    long totalDurationMs,
    long eventVersion,
    @Nullable String currentEventId,    // ← 新增
    @Nullable String workDir,
    Instant createdAt,
    Instant lastActiveAt
) {}
```

### 4.2 Port 變更

```java
// SessionEventPort — 新增 recursive CTE 查詢
public interface SessionEventPort {
    void append(SessionEvent event);
    List<SessionEvent> findBySessionId(String sessionId);
    List<SessionEvent> findBySessionId(String sessionId, EventFilter filter);
    List<SessionEvent> findConversationPath(String leafEventId);  // ← 新增
}
```

### 4.3 SessionHistoryUseCase — 新增

```java
List<SessionEvent> getConversationPath(String sessionId);
// 讀取 session.current_event_id，然後走 recursive CTE
```

---

## 5. File Plan

### 修改檔案

| File | Change |
|------|--------|
| `schema.sql` | grimo_session_event: 加 parent_event_id, 移除 branch; grimo_session: 加 current_event_id |
| `session/domain/SessionEvent.java` | 加 parentEventId，移除 branch |
| `session/domain/SessionProjection.java` | 加 currentEventId |
| `session/application/port/out/SessionEventPort.java` | 加 findConversationPath(String) |
| `session/application/port/in/SessionHistoryUseCase.java` | 加 getConversationPath(String) |
| `session/application/service/SessionHistoryService.java` | 實作 getConversationPath |
| `session/application/service/TurnRecorder.java` | parent_event_id 鏈接 + 更新 current_event_id |
| `session/adapter/out/JdbcSessionEventAdapter.java` | INSERT 加 parent_event_id; 新增 findConversationPath (recursive CTE) |
| `session/adapter/out/JdbcSessionProjectionAdapter.java` | MERGE INTO 加 current_event_id; mapRow 加欄位 |

### 修改測試

| File | Change |
|------|--------|
| `test/.../session/SchemaTest.java` | AC-1, AC-2, AC-6 schema 驗證 |
| `test/.../session/TurnRecorderTest.java` | AC-3 parent-child 鏈驗證 |
| `test/.../session/RealCliMetadataTest.java` | 更新 SessionEvent 建構子 |
| `test/.../session/RecordingRegistryTest.java` | 無變更（不觸及 event 層） |

### 新增測試

| File | Description |
|------|-------------|
| `test/.../session/ConversationPathTest.java` | AC-4, AC-5 recursive CTE + branching |

**合計：** 0 新增 production + 9 修改 + 1 新增 test = **10 個檔案接觸點**

---

### 估算

| 維度 | 分數 | 理由 |
|------|------|------|
| 技術風險 | 1 | H2 WITH RECURSIVE 已驗證，自我參照 FK S017 驗證 |
| 不確定性 | 1 | 設計完全確認，CTE 語法已查證 |
| 依賴關係 | 1 | S018 ✅ |
| 範疇 | 2 | 2 欄位 + CTE query + TurnRecorder 邏輯 + 9 檔案修改 |
| 測試 | 1 | pure JUnit + H2 |
| 可逆性 | 1 | schema DROP+CREATE |
| **合計** | **7** | **XS** |
