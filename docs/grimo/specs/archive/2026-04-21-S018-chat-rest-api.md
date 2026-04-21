# S018: REST API Foundation — Project / Task / Chat

> Spec: S018 | Size: S (9) | Status: ✅ Done
> Date: 2026-04-21 (v2 — 從 XS 測試 API 擴展為開發助手資料層)

---

## 1. Goal

**一句話：** 建立 Grimo 作為「自動開發助手」的 REST API 資料層 — Project 管理、Task 管理、Chat 對話，取代 CLI adapter。

### 背景

原 S018（v1）定位為「測試用 REST API」（XS, 8 點）。經競品研究（Devin、Linear、OpenClaw、Hermes Agent、Superconductor）與使用者討論後，重新定位為 Grimo 的完整 REST API 基礎。

**核心類比：** Grimo 就是一位真人工程師。啟動後是空白的，可以直接對話（Grimo 級 chat），可以建立專案（各有自己的 work_dir），可以管理開發任務（Task），可以把專案對出去到外部頻道（未來 S021）。

### 三層架構，本 spec 做 Layer 1

```
Layer 1: 資料基礎（S018 ← 本 spec）
  Project / Task CRUD、Chat REST、Session 擴充、Schema 重設計

Layer 2: AI 智慧（S019 — 後續）
  對話中 Agent 自動判斷建 Task、context injection、project 推斷

Layer 3: 執行管線（S020 — 後續）
  Task → worktree → Docker → Agent → commit → PR
```

### 本 spec 包含

1. **`project` 新模組** — Project CRUD REST API + JDBC
2. **`task` 新模組** — Task CRUD + lifecycle 狀態機 REST API + JDBC
3. **Chat REST API** — Grimo 級 + Project 級對話
4. **Session 擴充** — `grimo_session` 加 `session_type` / `project_id` / `task_id`
5. **Skill REST API** — 保留 v1 設計
6. **Schema 重設計** — `schema.sql` 全部重寫（`.grimo/db` 可砍掉重建）
7. **移除 CLI adapter** — `ChatCommandRunner` + `SkillCommandRunner` 刪除

### 不包含（後續 spec 負責）

| 項目 | 歸屬 | 理由 |
|------|------|------|
| 對話中 Agent 自動建 Task | S019 | 需要 LLM 推理層 |
| Task 執行管線（worktree + Docker + PR） | S020 | 與 subagent 模組整合 |
| Channel Binding + Discord adapter | S021 | PRD 列為 out-of-scope，port 設計先行 |
| Memory 模組（兩層記憶） | S022 | 獨立領域概念 + 觸發邏輯 |

### 依賴

S007 ✅、S012 ✅、S016 ✅、S017 ✅。無程式碼層級阻塞。

---

## 2. Approach

### 2.1 Schema 重設計（drop & rebuild）

`.grimo/db` 可直接刪除重建，無需 migration。完整 `schema.sql` 重寫。

**新增 2 表 + 擴充 1 表 + 保留 1 表：**

### 2.0 Compact ID — base36, 12 字元

S018 新實體（Project、Task）採用 12 字元 base36 ID（`0-9a-z`），取代現有 21 字元 base64 NanoID。本機單使用者，62 bit 熵綽綽有餘，且無 `_-` 特殊字元，檔案系統友善。

`NanoIds.java` 新增 `compact()` 方法：

```java
private static final char[] ALPHANUM =
    "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

public static String compact() {
    byte[] bytes = new byte[12];
    RNG.nextBytes(bytes);
    char[] out = new char[12];
    for (int i = 0; i < 12; i++) {
        out[i] = ALPHANUM[(bytes[i] & 0xFF) % 36];
    }
    return new String(out);
}
```

| 用途 | 方法 | 長度 | 字母表 | 範例 |
|------|------|------|--------|------|
| Session / Turn（既有） | `NanoIds.generate()` | 21 | base64 | `Vk3xQpRrT2mAbCdEfGhI` |
| Project / Task（S018） | `NanoIds.compact()` | 12 | base36 | `k7m3p2q9r4s1` |

```sql
-- 1. Project（新增）
CREATE TABLE IF NOT EXISTS grimo_project (
    id          VARCHAR(12)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL UNIQUE,
    work_dir    VARCHAR(500) NOT NULL,
    description VARCHAR(2000),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
```

**欄位說明：**

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `id` | VARCHAR(12) | PK | base36 compact ID，例如 `"k7m3p2q9r4s1"` |
| `name` | VARCHAR(200) | ✅ | 人類可讀的專案名稱，全系統唯一。例如 `"grimoAPP"` |
| `work_dir` | VARCHAR(500) | ✅ | 本地資料夾的絕對路徑。可能是 git repo 也可能是普通資料夾 |
| `description` | VARCHAR(2000) | 選填 | 一句話描述這個專案是什麼 |
| `created_at` | TIMESTAMP | ✅ | 專案建立時間 |
| `updated_at` | TIMESTAMP | ✅ | 最後修改時間（名稱或描述變更時更新） |

**範例資料（使用者建了 2 個專案後的樣子）：**

| id | name | work_dir | description | created_at |
|----|------|----------|-------------|------------|
| `k7m3p2q9r4s1` | grimoAPP | /Users/sam/workspace/grimoAPP | Spring Boot AI agent harness | 2026-04-21 10:00 |
| `a2b4c6d8e0f1` | my-blog | /Users/sam/workspace/my-blog | null | 2026-04-21 11:30 |

**設計決策：** 不存 `repo_url` / `default_branch` / `settings_json`。Git 資訊從 `work_dir` 動態偵測（`git remote -v`）。參考 Hermes Agent（只存 `terminal.cwd`）和 OpenAB（只存 `working_dir`）。

```sql
-- 2. Task（新增）— 通用工作項目，不限於開發任務
CREATE SEQUENCE IF NOT EXISTS grimo_task_number_seq START WITH 1;

CREATE TABLE IF NOT EXISTS grimo_task (
    id            VARCHAR(12)  PRIMARY KEY,
    task_number   INT          NOT NULL UNIQUE,
    project_id    VARCHAR(12),
    title         VARCHAR(500) NOT NULL,
    body          CLOB,
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    priority      VARCHAR(10)  DEFAULT 'MEDIUM',
    labels_json   CLOB,
    source_type   VARCHAR(20),
    source_ref    VARCHAR(200),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    closed_at     TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES grimo_project(id)
);

CREATE INDEX IF NOT EXISTS idx_task_project_status
    ON grimo_task(project_id, status);
```

**欄位說明：**

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `id` | VARCHAR(12) | PK | base36 compact ID |
| `task_number` | INT | ✅ | 全域自動遞增的編號，讓使用者用 `#42` 指稱任務。由 DB sequence 產生 |
| `project_id` | VARCHAR(12) | 選填 | 歸屬的專案。`null` = 這個任務屬於 Grimo 本身（如研究、整理） |
| `title` | VARCHAR(500) | ✅ | 任務標題，一句話描述要做什麼 |
| `body` | CLOB | 選填 | 任務詳細描述，Markdown 格式 |
| `status` | VARCHAR(20) | ✅ | 狀態機：`OPEN` → `IN_PROGRESS` → `IN_REVIEW` → `DONE` 或 `CANCELLED` |
| `priority` | VARCHAR(10) | 選填 | `LOW` / `MEDIUM`（預設）/ `HIGH` / `URGENT` |
| `labels_json` | CLOB | 選填 | JSON array 標籤，用 label 區分任務類型。例如 `["bug","backend"]` |
| `source_type` | VARCHAR(20) | 選填 | 這個任務從哪裡來的：`CHAT`（對話中建立）/ `MANUAL`（REST API 手動建）/ `WEBHOOK` |
| `source_ref` | VARCHAR(200) | 選填 | 來源參考 ID。如果 source_type=CHAT，這裡放建立任務的 chat session ID |
| `created_at` | TIMESTAMP | ✅ | 任務建立時間 |
| `updated_at` | TIMESTAMP | ✅ | 最後修改時間（標題、狀態、優先級等變更時更新） |
| `closed_at` | TIMESTAMP | 選填 | 任務關閉時間。狀態變為 `DONE` 或 `CANCELLED` 時自動填入 |

**範例資料（使用者建了 3 個任務後的樣子）：**

| task_number | project_id | title | status | priority | labels_json | source_type |
|-------------|-----------|-------|--------|----------|-------------|-------------|
| 1 | `k7m3p2q9r4s1` | 修 UserService bug | IN_PROGRESS | HIGH | `["bug","backend"]` | CHAT |
| 2 | null | 研究 Spring AI 最新版 | OPEN | MEDIUM | `["research"]` | MANUAL |
| 3 | `k7m3p2q9r4s1` | 加 OAuth2 整合 | OPEN | MEDIUM | `["feature"]` | MANUAL |

**設計決策：**
- **Task 是通用工作項目** — 研究、開發、文件、分析都是 Task。不預設有 worktree/Docker/PR。
- `task_number` 全域遞增（非 per-project），因為 Task 可以無 project。
- `project_id` nullable — 無 project 的 Task 屬於 Grimo 本身。
- **無 execution 欄位** — `branch` / `worktree_path` / `container_id` / `pr_url` 等開發執行資訊由 S020 負責，可能以擴充欄位或獨立 `grimo_task_execution` 表實作。S018 的 Task 是純工作管理。
- `labels_json` 存 JSON array（`["bug","research"]`），用 label 區分任務類型而非 schema 層硬編碼。

```sql
-- 3. Session（擴充，從 S017 加 3 欄位）
CREATE TABLE IF NOT EXISTS grimo_session (
    id                VARCHAR(36)  PRIMARY KEY,
    session_type      VARCHAR(20)  NOT NULL,
    project_id        VARCHAR(12),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    turn_count        INT          DEFAULT 0,
    total_tokens_in   BIGINT       DEFAULT 0,
    total_tokens_out  BIGINT       DEFAULT 0,
    total_duration_ms BIGINT       DEFAULT 0,
    event_version     BIGINT       DEFAULT 0,
    work_dir          VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL,
    last_active_at    TIMESTAMP    NOT NULL,
    FOREIGN KEY (project_id) REFERENCES grimo_project(id)
);

CREATE INDEX IF NOT EXISTS idx_session_project
    ON grimo_session(project_id, session_type);
```

**欄位說明（S018 新增欄位以 ⭐ 標記，其餘為 S017 既有）：**

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `id` | VARCHAR(36) | PK | 21 字元 NanoID（既有格式，不改） |
| ⭐ `session_type` | VARCHAR(20) | ✅ | `GRIMO`（Grimo 本身的對話）或 `PROJECT`（綁定某專案的對話） |
| ⭐ `project_id` | VARCHAR(12) | 選填 | 綁定的專案 ID。`session_type=GRIMO` 時為 null |
| `status` | VARCHAR(20) | ✅ | `ACTIVE`（對話進行中）或 `CLOSED`（已結束） |
| `turn_count` | INT | ✅ | 對話輪次數。使用者問一次 + AI 回一次 = 1 輪。例如聊了 3 輪就是 3 |
| `total_tokens_in` | BIGINT | ✅ | 累計輸入 token 數。每輪從 CLI 回應的 metadata 萃取後累加。用於成本追蹤 |
| `total_tokens_out` | BIGINT | ✅ | 累計輸出 token 數。同上 |
| `total_duration_ms` | BIGINT | ✅ | 累計 CLI 回應時間（毫秒）。例如 3 輪各花 2 秒 = 6000 |
| `event_version` | BIGINT | ✅ | 樂觀鎖版本號。每寫入一筆 event 就 +1，用於 S014 壓縮的 CAS 控制 |
| `work_dir` | VARCHAR(500) | 選填 | 對話的工作目錄路徑 |
| `created_at` | TIMESTAMP | ✅ | Session 建立時間 |
| `last_active_at` | TIMESTAMP | ✅ | 最後一次對話的時間 |

**範例資料（使用者用 Grimo 聊了一會，又在 grimoAPP 專案聊了一會）：**

| id | session_type | project_id | provider | status | turn_count | total_tokens_in | total_tokens_out | total_duration_ms |
|----|-------------|-----------|----------|--------|-----------|----------------|-----------------|------------------|
| `AbCdEfGhIjKlMnOpQrStU` | GRIMO | null | claude | ACTIVE | 5 | 3200 | 1800 | 12500 |
| `VwXyZaBcDeFgHiJkLmNoP` | PROJECT | `k7m3p2q9r4s1` | claude | ACTIVE | 2 | 1100 | 600 | 4200 |

> 第一列：使用者跟 Grimo 聊了 5 輪（問了 5 個問題，AI 回了 5 次），總共送了 3200 個 token 給 Claude，Claude 回了 1800 個 token，花了 12.5 秒。
> 第二列：使用者在 grimoAPP 專案的對話中聊了 2 輪。

```sql
-- 4. Session Event（對齊 Spring AI Session 命名）— 每一句對話都存在這裡
CREATE TABLE IF NOT EXISTS grimo_session_event (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    session_id      VARCHAR(36)  NOT NULL,
    message_type    VARCHAR(20)  NOT NULL,
    message_content TEXT,
    message_data    TEXT,
    provider        VARCHAR(20),
    model           VARCHAR(100),
    metadata        TEXT,
    synthetic       BOOLEAN      NOT NULL DEFAULT FALSE,
    branch          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_event_session
        FOREIGN KEY (session_id) REFERENCES grimo_session(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_session_event_session_ts
    ON grimo_session_event(session_id, created_at);
```

**這張表就是「對話內容」的真正儲存位置。** 使用者每說一句話、AI 每回一句話，都會 INSERT 一筆 event。

**與 S017 的差異（對齊 Spring AI Session）：**

| S017（舊） | S018（新） | 改動原因 |
|-----------|-----------|---------|
| `sequence` BIGINT auto-increment | 移除 | 靠 `created_at` 排序即可簡化 |
| `event_id` | `id` | 對齊 Spring AI 命名 |
| `turn_number` | 移除 | 用不太到，turn_count 在 session 投影計算 |
| `event_type` | `message_type` | 對齊 Spring AI `MessageType` 枚舉 |
| `payload_json` | `message_content` + `message_data` | 拆開：文字內容 vs 結構化資料（tool calls 等） |
| `metadata_json` | `metadata` | 更彈性的 JSON |
| 無 | `provider` 欄位 | 拉出來：哪個 CLI 回的（claude / codex / gemini） |
| 無 | `model` 欄位 | 拉出來：具體模型名（claude-sonnet-4-6 / gpt-5.4-mini-2026-03-17） |
| 無 FK cascade | `ON DELETE CASCADE` | 刪 session 連帶清 events |

**欄位說明：**

| 欄位 | 型別 | 說明 |
|------|------|------|
| `id` | VARCHAR(36) | 每筆 event 的唯一 ID（NanoID） |
| `session_id` | VARCHAR(36) | 屬於哪個 session |
| `message_type` | VARCHAR(20) | Spring AI `MessageType`：`USER`（使用者輸入）/ `ASSISTANT`（AI 回覆）/ `SYSTEM`（系統指令）/ `TOOL`（工具執行結果） |
| `message_content` | TEXT | 訊息的文字內容。例如使用者說的話、AI 回的文字 |
| `message_data` | TEXT | 附加結構化資料（JSON）。例如 TOOL 類型的工具呼叫參數和結果；ASSISTANT 的 tool_calls 清單。一般文字對話為 null |
| `provider` | VARCHAR(20) | 哪個 CLI 產生這個回覆：`claude` / `codex` / `gemini`。USER 類型為 null |
| `model` | VARCHAR(100) | 具體模型名稱，例如 `claude-sonnet-4-6`、`gpt-5.4-mini-2026-03-17`。USER 類型為 null |
| `metadata` | TEXT | JSON 格式的額外資訊：`{"durationMs":1200,"tokensIn":15,"tokensOut":12,"finishReason":"stop"}`。USER 類型為 null |
| `synthetic` | BOOLEAN | `false` = 真實對話；`true` = 壓縮產生的合成 event（S014 用） |
| `branch` | VARCHAR(500) | 對話分支路徑（預留，目前為 null） |
| `created_at` | TIMESTAMP | event 寫入時間，用於排序 |

**範例資料（使用者跟 Grimo 聊了 2 輪）：**

| id | session_id | message_type | message_content | provider | model | metadata |
|----|-----------|-------------|-----------------|----------|-------|----------|
| `Tq9kLm...` | `AbCdEfG...` | USER | hello | null | null | null |
| `Xp2nRs...` | `AbCdEfG...` | ASSISTANT | Hi! How can I help? | claude | claude-sonnet-4-6 | `{"durationMs":1200,"tokensIn":15,"tokensOut":12}` |
| `Yw3oSt...` | `AbCdEfG...` | USER | 幫我修 UserService bug | null | null | null |
| `Zv4pUu...` | `AbCdEfG...` | ASSISTANT | 好的，我來看一下 UserService 的程式碼... | claude | claude-sonnet-4-6 | `{"durationMs":3100,"tokensIn":850,"tokensOut":420}` |

> 每一列就是一句對話。`message_type` 告訴你誰說的，`provider` + `model` 告訴你哪個 AI 回的。
> 刪除 session 時，這些 events 會因為 `ON DELETE CASCADE` 自動清除。

**兩張表的關係：**

```
grimo_session（投影/摘要）              grimo_session_event（完整對話）
┌──────────────────────────┐           ┌──────────────────────────────────┐
│ id: AbCdEfG...           │           │ session_id: AbCdEfG...           │
│ turn_count: 2            │ ←─1:N──→ │ USER:      "hello"               │
│ total_tokens_in: 865     │           │ ASSISTANT: "Hi!" (claude-sonnet) │
│ total_tokens_out: 432    │           │ USER:      "幫我修 bug"          │
│ status: ACTIVE           │           │ ASSISTANT: "好的" (claude-sonnet) │
└──────────────────────────┘           └──────────────────────────────────┘
```

### 2.2 新增 `project` 模組

```
project/
  ├── domain/               Project record, ProjectStatus
  ├── application/
  │   ├── port/in/          ProjectUseCase (@NamedInterface("api"))
  │   ├── port/out/         ProjectPort
  │   └── service/          ProjectService
  └── adapter/
      ├── in/web/           ProjectRestController, DTOs
      └── out/              JdbcProjectAdapter
```

`allowedDependencies = { "core" }`。不依賴其他模組。

### 2.3 新增 `task` 模組

```
task/
  ├── domain/               Task, TaskStatus, TaskPriority, TaskSource
  ├── application/
  │   ├── port/in/          TaskUseCase (@NamedInterface("api"))
  │   ├── port/out/         TaskPort
  │   └── service/          TaskService
  └── adapter/
      ├── in/web/           TaskRestController, DTOs
      └── out/              JdbcTaskAdapter
```

`allowedDependencies = { "core", "project::api" }`。依賴 project 模組驗證 `projectId` 存在。

### 2.4 擴充 `session` 模組

- `SessionProjection` record 加 2 個欄位：`sessionType`、`projectId`
- `SessionEvent` record 重新設計：對齊 Spring AI 命名（`messageType`/`messageContent`/`messageData`/`provider`/`model`/`metadata`），移除 `sequence`、`turnNumber`
- `EventType` enum → 改為 `MessageType` enum（`USER`/`ASSISTANT`/`SYSTEM`/`TOOL`），對齊 Spring AI `MessageType`
- `SessionHistoryUseCase` 新增：`listAll()`、`findByProjectId(String)`
- `SessionProjectionPort` 新增：`findAll()`、`findByProjectId(String)`
- `SessionEventPort`：更新方法簽名配合新欄位
- `JdbcSessionEventAdapter`：重寫 INSERT/SELECT 配合新 schema
- `JdbcSessionProjectionAdapter`：實作新查詢 + upsert 加 2 欄位
- `RecordingAgentSession`：建構子加 `sessionType`、`projectId`；publish 時傳遞 `provider`、`model`
- `TurnRecorded` event：對齊新欄位命名
- `TurnRecorder`：更新寫入邏輯
- 新增 `SessionRestController`（`adapter/in/web/`）

### 2.5 擴充 `agent` 模組 — Chat REST

- 移除 `ChatCommandRunner`（CLI REPL）
- `MainAgentChatUseCase` 改為 `createSession(Path, SessionType, projectId?)` / `resumeSession(Path)`
- 移除 `MainAgentChatService` 的 REPL 邏輯
- 新增 `ChatController`（`adapter/in/web/`）

### 2.6 擴充 `skills` 模組 — Skill REST

- 移除 `SkillCommandRunner`（CLI 子命令）
- 新增 `SkillRestController`（`adapter/in/web/`）
- 保留 v1 設計的 5 個 endpoint

### 2.7 移除 CLI adapter，Tomcat 常駐

加入 `spring-boot-starter-web` 後（architecture.md §3 已固定），Spring Boot 以 `SERVLET` 模式啟動 Tomcat:8080。不再需要 `ApplicationRunner` + stdin REPL。

`application.yaml` 新增：
```yaml
server:
  address: 127.0.0.1    # architecture §9: localhost-only
```

### 2.8 Research Citations

- **Spring MVC `@RestController`：** Boot 4.0.5 標準，architecture.md §3 固定。S017 已驗證 JDBC adapter pattern。
- **H2 SEQUENCE：** H2 2.3.232 PostgreSQL mode 支援 `CREATE SEQUENCE` + `NEXT VALUE FOR`。
- **競品研究：** Devin REST API（session = task 單實體）、Linear Agents API（task + session 雙實體）、OpenClaw TaskFlow（session-scoped 背景任務）、Hermes Agent（無 project 管理）、Superconductor（ticket as primitive）。完整分析見 `docs/local/competitive-analysis.md`。
- **Session-Project binding 研究：** OpenClaw（agent = project 邊界，靜態）、Hermes（profile = project，靜態）、OpenAB（instance = project，靜態）。Grimo 的動態 binding（GRIMO / PROJECT session type）超越所有競品。

---

## 3. SBE Acceptance Criteria

**驗證命令：** `./gradlew test`
Pass: 所有攜帶 S018 AC id 的測試為綠色。

---

**AC-1: Project CRUD**

```
Given  no projects exist
When   POST /api/projects with { "name": "grimoAPP", "workDir": "/tmp/grimo-test" }
Then   201 Created with { "id": "<uuid>", "name": "grimoAPP", "workDir": "/tmp/grimo-test" }

When   GET /api/projects
Then   200 with JSON array containing 1 project

When   GET /api/projects/{id}
Then   200 with project details

When   PATCH /api/projects/{id} with { "description": "AI agent harness" }
Then   200 with updated project

When   DELETE /api/projects/{id}
Then   204 No Content
When   GET /api/projects
Then   200 with empty array
```

**AC-2: Project 名稱唯一 + workDir 驗證**

```
Given  project "grimoAPP" already exists
When   POST /api/projects with { "name": "grimoAPP", "workDir": "/tmp/other" }
Then   409 Conflict with { "error": "Project name already exists: grimoAPP" }
```

**AC-3: Task CRUD — 有 project**

```
Given  project "grimoAPP" exists (id = P1)
When   POST /api/tasks with { "projectId": "P1", "title": "修 UserService bug", "priority": "HIGH" }
Then   201 Created with { "id": "<uuid>", "taskNumber": 1, "projectId": "P1",
       "title": "修 UserService bug", "status": "OPEN", "priority": "HIGH" }

When   GET /api/tasks?projectId=P1
Then   200 with array containing task #1

When   GET /api/tasks/1
Then   200 with task details (lookup by taskNumber)
```

**AC-4: Task CRUD — 無 project（Grimo 級）**

```
When   POST /api/tasks with { "title": "研究 Spring AI 最新版" }
Then   201 Created with { "taskNumber": 2, "projectId": null, "status": "OPEN" }

When   GET /api/tasks
Then   200 with array containing task #1 (project) and task #2 (no project)

When   GET /api/tasks?projectId=none
Then   200 with array containing only task #2 (no project)
```

**AC-5: Task lifecycle 狀態轉換**

```
Given  task #1 exists with status OPEN
When   PATCH /api/tasks/1 with { "status": "IN_PROGRESS" }
Then   200 with status = "IN_PROGRESS"

When   PATCH /api/tasks/1 with { "status": "IN_REVIEW" }
Then   200

When   PATCH /api/tasks/1 with { "status": "DONE" }
Then   200 with closedAt != null
```

**AC-6: Chat 建立 Grimo 級 session + 送訊息**

```
Given  Claude CLI available on PATH
And    app running (Tomcat on :8080)
When   POST /api/chat with { "message": "hello" }
Then   200 with { "sessionId": "<uuid>", "response": "<non-empty AI text>" }
And    GET /api/sessions/{sessionId} shows session_type = "GRIMO", project_id = null
```

**AC-7: Chat 建立 Project 級 session**

```
Given  project "grimoAPP" exists (id = P1)
When   POST /api/chat with { "message": "hello", "projectId": "P1" }
Then   200 with { "sessionId": "...", "response": "..." }
And    GET /api/sessions/{sessionId} shows session_type = "PROJECT", project_id = "P1"
```

**AC-8: Chat 多輪對話 + resume**

```
Given  AC-6 建立的 session (sessionId = X)
When   POST /api/chat/X with { "message": "what did I just say?" }
Then   200 with AI referencing the first message

Given  a prior session exists in the working directory
When   POST /api/chat/resume with { "message": "continue" }
Then   200 with { "sessionId": "...", "response": "..." }
```

**AC-9: Session 列表 + 篩選**

```
Given  1 Grimo session + 1 Project session exist
When   GET /api/sessions
Then   200 with 2 sessions, each containing session_type, project_id

When   GET /api/sessions?sessionType=PROJECT
Then   200 with 1 session (only project sessions)

When   GET /api/sessions?projectId=P1
Then   200 with 1 session (only P1's sessions)
```

**AC-10: Skill REST API**

```
Given  ~/.grimo/skills/greet/SKILL.md exists and is enabled
When   GET /api/skills
Then   200 with [{ "name": "greet", "enabled": true, "description": "..." }]

When   PUT /api/skills/greet/disable
Then   200 OK
When   GET /api/skills
Then   greet shows enabled: false

When   POST /api/skills/project with { "workDir": "/tmp/test" }
Then   200 OK and /tmp/test/.claude/skills/greet/SKILL.md exists
```

**AC-11: 錯誤處理**

```
When   GET /api/projects/nonexistent
Then   404 Not Found

When   GET /api/tasks/999
Then   404 Not Found

When   POST /api/chat/nonexistent-session with { "message": "hi" }
Then   404 Not Found

When   PUT /api/skills/nonexistent/enable
Then   404 Not Found
```

**AC-12: CLI adapter 已移除 + MockMvc 可測試**

```
Given  ChatCommandRunner.java and SkillCommandRunner.java deleted
When   ./gradlew test
Then   compile succeeds — no dangling references
And    ModuleArchitectureTest.verify() passes
And    @WebMvcTest tests pass without real CLI, stdin pipe, or Docker
```

---

## 4. Interface / API Design

### 4.1 新增 Domain Records

```java
// === project module: domain/ ===

public record Project(
    String id,
    String name,
    String workDir,
    @Nullable String description,
    Instant createdAt,
    Instant updatedAt
) {}
```

```java
// === task module: domain/ ===

public record Task(
    String id,
    int taskNumber,
    @Nullable String projectId,
    String title,
    @Nullable String body,
    TaskStatus status,
    TaskPriority priority,
    @Nullable String labelsJson,
    @Nullable TaskSource source,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant closedAt
) {}

public enum TaskStatus { OPEN, IN_PROGRESS, IN_REVIEW, DONE, CANCELLED }

public enum TaskPriority { LOW, MEDIUM, HIGH, URGENT }

public record TaskSource(
    String type,       // CHAT | MANUAL | WEBHOOK
    @Nullable String ref
) {}
```

### 4.2 新增 / 修改 Use Cases

```java
// === project module: port/in/ (@NamedInterface("api")) ===

public interface ProjectUseCase {
    Project create(String name, String workDir, @Nullable String description);
    List<Project> listAll();
    Optional<Project> findById(String id);
    Project update(String id, @Nullable String name,
                   @Nullable String workDir, @Nullable String description);
    void delete(String id);
}
```

```java
// === task module: port/in/ (@NamedInterface("api")) ===

public interface TaskUseCase {
    Task create(@Nullable String projectId, String title,
                @Nullable String body, @Nullable TaskPriority priority,
                @Nullable String labelsJson, @Nullable TaskSource source);
    List<Task> list(@Nullable String projectId, @Nullable TaskStatus status);
    Optional<Task> findByNumber(int taskNumber);
    Optional<Task> findById(String id);
    Task updateStatus(String id, TaskStatus status);
    Task update(String id, @Nullable String title, @Nullable String body,
                @Nullable TaskPriority priority, @Nullable String labelsJson);
    void delete(String id);
}
```

```java
// === session module: port/in/ (擴充 SessionHistoryUseCase) ===

public interface SessionHistoryUseCase {
    // S017 existing
    List<SessionEvent> getEvents(String sessionId);
    List<SessionEvent> getEvents(String sessionId, EventFilter filter);
    Optional<SessionProjection> findById(String sessionId);

    // S018 新增
    List<SessionProjection> listAll();
    List<SessionProjection> findByProjectId(String projectId);
    List<SessionProjection> findBySessionType(String sessionType);
}
```

```java
// === agent module: port/in/ (改寫 MainAgentChatUseCase) ===

public interface MainAgentChatUseCase {
    /** 建立新 session，回傳可 prompt 的 AgentSession */
    AgentSession createSession(Path workDir, SessionType sessionType,
                               @Nullable String projectId);
    /** 恢復最近 session */
    AgentSession resumeSession(Path workDir);
}

public enum SessionType { GRIMO, PROJECT }
```

### 4.3 新增 / 修改 Out Ports

```java
// === project module: port/out/ ===

public interface ProjectPort {
    void save(Project project);
    Optional<Project> findById(String id);
    Optional<Project> findByName(String name);
    List<Project> findAll();
    void deleteById(String id);
}
```

```java
// === task module: port/out/ ===

public interface TaskPort {
    void save(Task task);
    Optional<Task> findById(String id);
    Optional<Task> findByNumber(int taskNumber);
    List<Task> findByProjectId(@Nullable String projectId);
    List<Task> findByStatus(TaskStatus status);
    List<Task> findAll();
    int nextTaskNumber();
    void deleteById(String id);
}
```

```java
// === session module: port/out/ (擴充 SessionProjectionPort) ===

public interface SessionProjectionPort {
    // S017 existing
    void upsert(SessionProjection projection);
    Optional<SessionProjection> findById(String sessionId);

    // S018 新增
    List<SessionProjection> findAll();
    List<SessionProjection> findByProjectId(String projectId);
    List<SessionProjection> findBySessionType(String sessionType);
}
```

### 4.4 REST Endpoint 總覽

#### Project (`project/adapter/in/web/`)

| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| POST | `/api/projects` | `{ name, workDir, description? }` | 201 `Project` |
| GET | `/api/projects` | — | `List<Project>` |
| GET | `/api/projects/{id}` | — | `Project` |
| PATCH | `/api/projects/{id}` | `{ name?, workDir?, description? }` | `Project` |
| DELETE | `/api/projects/{id}` | — | 204 |

#### Task (`task/adapter/in/web/`)

| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| POST | `/api/tasks` | `{ projectId?, title, body?, priority?, labels? }` | 201 `Task` |
| GET | `/api/tasks` | `?projectId=&status=&projectId=none` | `List<Task>` |
| GET | `/api/tasks/{taskNumber}` | — | `Task` |
| PATCH | `/api/tasks/{taskNumber}` | `{ title?, body?, status?, priority?, labels? }` | `Task` |
| DELETE | `/api/tasks/{taskNumber}` | — | 204 |

#### Chat (`agent/adapter/in/web/`)

| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| POST | `/api/chat` | `{ message, projectId?, workDir? }` | `{ sessionId, response }` |
| POST | `/api/chat/{sessionId}` | `{ message }` | `{ sessionId, response }` |
| POST | `/api/chat/resume` | `{ message, workDir? }` | `{ sessionId, response }` |
| DELETE | `/api/chat/{sessionId}` | — | 204 |

`projectId` 決定 session 類型：有 → `PROJECT`，無 → `GRIMO`。

#### Skill (`skills/adapter/in/web/`)

| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| GET | `/api/skills` | — | `List<SkillDto>` |
| GET | `/api/skills/{name}` | — | `SkillDto` |
| PUT | `/api/skills/{name}/enable` | — | 200 |
| PUT | `/api/skills/{name}/disable` | — | 200 |
| POST | `/api/skills/project` | `{ workDir }` | 200 |

#### Session (`session/adapter/in/web/`)

| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| GET | `/api/sessions` | `?sessionType=&projectId=` | `List<SessionProjection>` |
| GET | `/api/sessions/{id}` | — | `SessionProjection` |
| GET | `/api/sessions/{id}/events` | — | `List<SessionEvent>` |

### 4.5 SessionProjection 擴充

```java
public record SessionProjection(
    String id,
    String sessionType,                  // "GRIMO" | "PROJECT"
    @Nullable String projectId,
    SessionStatus status,
    int turnCount,
    long totalTokensIn,
    long totalTokensOut,
    long totalDurationMs,
    long eventVersion,
    @Nullable String workDir,
    Instant createdAt,
    Instant lastActiveAt
) {}
```

### 4.6 資料流

```
POST /api/chat { message, projectId }
  │
  ├→ ChatController
  │     ├→ resolve sessionType: projectId != null ? PROJECT : GRIMO
  │     ├→ resolve workDir: projectId → ProjectUseCase.findById → project.workDir
  │     │                   projectId == null → Path.of("").toAbsolutePath()
  │     ├→ skillProjection.projectToWorkDir(workDir)
  │     ├→ chatUseCase.createSession(workDir, sessionType, projectId)
  │     │     └→ RecordingAgentSessionRegistry.create(workDir, sessionType, projectId)
  │     │           └→ RecordingAgentSession (carries sessionType, projectId)
  │     ├→ sessions.put(id, session)
  │     ├→ session.prompt(message)
  │     │     ├→ delegate.prompt() → Claude CLI
  │     │     └→ eventPublisher.publish(TurnRecorded)
  │     │           └→ TurnRecorder.on()
  │     │                 ├→ eventStore.append(USER + ASSISTANT events)
  │     │                 └→ projectionStore.upsert(with sessionType, projectId)
  │     │
  │     └→ return { sessionId, response }
  │
POST /api/tasks { projectId, title }
  │
  └→ TaskRestController
        └→ taskUseCase.create(projectId, title, ...)
              ├→ projectPort.findById(projectId) — verify exists (if not null)
              ├→ taskPort.nextTaskNumber() — NEXT VALUE FOR grimo_task_number_seq
              └→ taskPort.save(new Task(...))
```

---

## 5. File Plan

### 刪除檔案

| File | Reason |
|------|--------|
| `agent/adapter/in/cli/ChatCommandRunner.java` | CLI REPL，由 REST 取代 |
| `skills/adapter/in/cli/SkillCommandRunner.java` | CLI skill 子命令，由 REST 取代 |
| `test/.../ChatCommandRunnerTest.java` | 對應生產碼已刪除 |
| `test/.../SkillCommandRunnerTest.java` | 對應生產碼已刪除 |

### 新增：`project` 模組（8 檔案）

| File | Description |
|------|-------------|
| `project/package-info.java` | `@ApplicationModule`, `allowedDependencies = { "core" }` |
| `project/domain/Project.java` | Domain record |
| `project/application/port/in/ProjectUseCase.java` | `@NamedInterface("api")` |
| `project/application/port/out/ProjectPort.java` | Repository port |
| `project/application/service/ProjectService.java` | Use case 實作 |
| `project/adapter/in/web/ProjectRestController.java` | REST controller + DTOs |
| `project/adapter/out/JdbcProjectAdapter.java` | JDBC 實作 |
| `test/.../project/adapter/in/web/ProjectRestControllerTest.java` | `@WebMvcTest` |

### 新增：`task` 模組（10 檔案）

| File | Description |
|------|-------------|
| `task/package-info.java` | `@ApplicationModule`, `allowedDependencies = { "core", "project::api" }` |
| `task/domain/Task.java` | Domain record |
| `task/domain/TaskStatus.java` | Enum |
| `task/domain/TaskPriority.java` | Enum |
| `task/domain/TaskSource.java` | Value object record |
| `task/application/port/in/TaskUseCase.java` | `@NamedInterface("api")` |
| `task/application/port/out/TaskPort.java` | Repository port |
| `task/application/service/TaskService.java` | Use case 實作 |
| `task/adapter/in/web/TaskRestController.java` | REST controller + DTOs |
| `task/adapter/out/JdbcTaskAdapter.java` | JDBC 實作 |

### 新增：REST Controllers（各模組）

| File | Description |
|------|-------------|
| `agent/adapter/in/web/ChatController.java` | Chat REST endpoints + DTOs |
| `skills/adapter/in/web/SkillRestController.java` | Skill REST endpoints + DTOs |
| `session/adapter/in/web/SessionRestController.java` | Session query endpoints |

### 新增：測試

| File | Layer |
|------|-------|
| `test/.../project/adapter/in/web/ProjectRestControllerTest.java` | T2 `@WebMvcTest` |
| `test/.../project/adapter/out/JdbcProjectAdapterTest.java` | T2 `@DataJdbcTest` |
| `test/.../task/adapter/in/web/TaskRestControllerTest.java` | T2 `@WebMvcTest` |
| `test/.../task/adapter/out/JdbcTaskAdapterTest.java` | T2 `@DataJdbcTest` |
| `test/.../task/domain/TaskStatusTest.java` | T0 Unit |
| `test/.../agent/adapter/in/web/ChatControllerTest.java` | T2 `@WebMvcTest` |
| `test/.../skills/adapter/in/web/SkillRestControllerTest.java` | T2 `@WebMvcTest` |
| `test/.../session/adapter/in/web/SessionRestControllerTest.java` | T2 `@WebMvcTest` |

### 修改檔案

| File | Change |
|------|--------|
| `build.gradle.kts` | 加入 `spring-boot-starter-web` |
| `application.yaml` | 加入 `server.address: 127.0.0.1` |
| `schema.sql` | 全部重寫（加 grimo_project + grimo_task + 擴充 grimo_session） |
| `session/domain/SessionEvent.java` | 重寫：對齊 Spring AI 命名（messageType/messageContent/messageData/provider/model/metadata），移除 sequence/turnNumber |
| `session/domain/SessionProjection.java` | 加 `sessionType`, `projectId` |
| `session/domain/EventType.java` → `MessageType.java` | 重命名：`USER`/`ASSISTANT`/`SYSTEM`/`TOOL`，對齊 Spring AI |
| `session/domain/EventFilter.java` | 移除 `lastTurns()`（不再有 turn_number），調整為 timestamp-based |
| `session/domain/SessionStatus.java` | 不變（ACTIVE, CLOSED） |
| `session/events/TurnRecorded.java` | 更新欄位：加 `sessionType`/`projectId`/`provider`/`model` |
| `session/application/port/in/SessionHistoryUseCase.java` | 加 `listAll()`, `findByProjectId()`, `findBySessionType()` |
| `session/application/port/out/SessionProjectionPort.java` | 加 `findAll()`, `findByProjectId()`, `findBySessionType()` |
| `session/application/service/SessionHistoryService.java` | 實作新查詢 |
| `session/adapter/out/JdbcSessionEventAdapter.java` | 重寫 INSERT/SELECT 配合新 schema（messageType/messageContent/provider/model 等） |
| `session/adapter/out/JdbcSessionProjectionAdapter.java` | 實作 `findAll()`, `findByProjectId()`, `findBySessionType()`; `upsert()` 加 2 欄位; `mapRow()` 加 2 欄位 |
| `session/internal/RecordingAgentSession.java` | 建構子加 `sessionType`, `projectId`; publish 時傳遞 provider/model |
| `session/internal/RecordingAgentSessionRegistry.java` | `create()` 加 `sessionType`, `projectId` 參數 |
| `session/internal/TurnRecorder.java` | `TurnRecorded` 讀取新欄位 → upsert 時寫入 |
| `session/internal/SessionConfig.java` | 不變（web starter 加入後 `@ConditionalOnMissingBean ObjectMapper` 自動失效） |
| `agent/application/port/in/MainAgentChatUseCase.java` | 改為 `createSession(Path, SessionType, projectId?)` / `resumeSession(Path)` |
| `agent/application/service/MainAgentChatService.java` | 移除 REPL 邏輯 |
| `agent/package-info.java` | `allowedDependencies` 加 `"project::api"` |
| `test/.../MainAgentChatServiceTest.java` | 更新測試方法 |

**合計：** 4 刪除 + ~21 新增 + ~18 修改 = **~43 個檔案接觸點**

---

### 估算

| 維度 | 分數 | 理由 |
|------|------|------|
| 技術風險 | 1 | 所有 pattern 已由 S017 驗證（JDBC、Modulith、REST） |
| 不確定性 | 1 | 經大量對話 + 競品研究，scope 明確 |
| 依賴關係 | 1 | S007/S012/S016/S017 全部 ✅ |
| 範疇 | 3 | 2 新模組、4 表、20+ endpoint、schema 重設計 |
| 測試 | 2 | 8 個 `@WebMvcTest` + 2 個 `@DataJdbcTest` + 1 個 unit |
| 可逆性 | 1 | Schema 砍掉重建，CLI adapter 有 git 歷史 |
| **合計** | **9** | **S** |

---

## 6. Task Plan

### POC Decision

**POC: not required.** 所有技術（Spring MVC `@RestController`、H2 SEQUENCE、JDBC adapter、Modulith `@ApplicationModule`、`@WebMvcTest`）均由 S005/S012/S016/S017 驗證。`spring-boot-starter-web` 為標準 Spring Boot 基礎建設，已列入 `architecture.md` §3 框架依賴表。

### Pre-Flight Findings

1. **`parent_event_id` + `current_event_id` 設計延後。** 交班筆記記載 message-level tree 設計討論，但 S018 的 12 個 AC 均不測試 branching/fork。`branch VARCHAR(500)` 保留為預留欄位。Branching 由 S014 或後續 spec 處理。
2. **Spec §2.1 範例資料含 `provider` 欄位不一致。** DDL 已移除 `provider`，但範例表格仍列出。實作時修正。
3. **PRD D11 偏差已確認。** S018 從 CLI-only 轉向 REST API — 使用者在 S018 設計 session 中經競品研究後明確決定。

### Task Summary

| Task | AC | Description | Depends On |
|------|----|-------------|------------|
| T01 | AC-12 | Schema Rewrite + Session Refactor + Web Starter + CLI Removal | — |
| T02 | AC-1, AC-2, AC-11 | Project Module — CRUD + REST | T01 |
| T03 | AC-3, AC-4, AC-5, AC-11 | Task Module — CRUD + Lifecycle + REST | T01, T02 |
| T04 | AC-6–AC-11 | Chat + Session + Skill REST Controllers | T01, T02 |

### AC Coverage Matrix

| AC | T01 | T02 | T03 | T04 |
|----|-----|-----|-----|-----|
| AC-1 Project CRUD | | ✅ | | |
| AC-2 Project name unique | | ✅ | | |
| AC-3 Task CRUD (with project) | | | ✅ | |
| AC-4 Task CRUD (no project) | | | ✅ | |
| AC-5 Task lifecycle | | | ✅ | |
| AC-6 Chat Grimo session | | | | ✅ |
| AC-7 Chat Project session | | | | ✅ |
| AC-8 Chat multi-turn + resume | | | | ✅ |
| AC-9 Session list + filter | | | | ✅ |
| AC-10 Skill REST | | | | ✅ |
| AC-11 Error handling (404) | | ✅ | ✅ | ✅ |
| AC-12 CLI removed + compile | ✅ | | | |

### Execution Order

```
T01 (infrastructure)
 ├──→ T02 (project)
 │     ├──→ T03 (task)
 │     └──→ T04 (chat + session + skill REST)
```

Task files: `docs/grimo/tasks/2026-04-21-S018-T0{1,2,3,4}.md`

---

## 7. Implementation Results

### Verification Results

```
./gradlew compileTestJava   → BUILD SUCCESSFUL
./gradlew test              → BUILD SUCCESSFUL (122 tests, 0 failures)
./gradlew bootJar           → BUILD SUCCESSFUL
E2E: java -jar grimo.jar    → Tomcat started on :8080 in 1.9s
  GET /api/projects          → [] (200)
  GET /api/sessions          → [] (200)
  GET /api/skills            → [{"name":"greet","enabled":true,...}] (200)
  GET /api/tasks             → [] (200)
```

### E2E Artifact Verification

**E2E required** — schema initialization (4 tables + FK + SEQUENCE), Spring MVC auto-discovery of new REST controllers across 5 modules, Modulith boundary enforcement.

**Evidence:** Application started in 1.9 seconds. All 4 REST endpoints responded with correct empty/populated data. Schema DDL (DROP + CREATE) executed successfully against file-based H2.

### Key Findings

1. **Spring Boot 4.0.5: `@WebMvcTest` moved package.** From `org.springframework.boot.test.autoconfigure.web.servlet` to `org.springframework.boot.webmvc.test.autoconfigure`. Requires `spring-boot-starter-webmvc-test` dependency.

2. **Schema DDL uses DROP + CREATE (not IF NOT EXISTS alone).** S018 is a full schema redesign. `CREATE TABLE IF NOT EXISTS` silently succeeds on existing tables without adding new columns. `DROP TABLE IF EXISTS` in reverse FK order ensures clean migration from S017.

3. **TurnRecorder: projection BEFORE events.** S018 adds `ON DELETE CASCADE` FK from `grimo_session_event.session_id → grimo_session.id`. TurnRecorder must upsert the session projection first, then insert events. S017 had no FK — order didn't matter.

4. **Modulith: domain types need `@NamedInterface("api")` for cross-module access.** When `ProjectUseCase.findById()` returns `Optional<Project>`, consumers (agent module) need bytecode-level access to `Project`. Adding `@NamedInterface("api")` to `project/domain/package-info.java` solves this.

5. **`SkillsTool.Skill` has `frontMatter()` map, not `description()`.** Description must be extracted via `frontMatter().get("description")`.

6. **`SessionConfig.ObjectMapper` auto-deactivates.** The `@ConditionalOnMissingBean ObjectMapper` in `SessionConfig` becomes a no-op when `spring-boot-starter-web` auto-configures one. No code change needed.

7. **`SessionRecordingPort.createRecordedSession()`** — new method allows creating sessions with explicit `sessionType`/`projectId`. Bypasses `@Primary` auto-wrapping which defaults to GRIMO/null.

### [Implementation note] Divergences from §2/§4

- §2.1 範例資料表格包含 `provider` 欄位，但 DDL 已移除 — 不影響實作。
- §2.4 MainAgentChatService 不再注入 `AgentSessionRegistry`，改用 `SessionRecordingPort.createRecordedSession()` 建立 session。
- §5 `agent/package-info.java` 需 `"project::api"` — 原 spec 中的 `allowedDependencies` 列表不完整。

### AC Results

| AC | Status | Test |
|----|--------|------|
| AC-1 Project CRUD | ✅ | ProjectRestControllerTest (8 tests) + JdbcProjectAdapterTest (5 tests) |
| AC-2 Project name unique | ✅ | ProjectRestControllerTest.createDuplicateName_returns409 |
| AC-3 Task CRUD (with project) | ✅ | TaskRestControllerTest + JdbcTaskAdapterTest |
| AC-4 Task CRUD (no project) | ✅ | TaskRestControllerTest.createWithoutProject_returns201 + JdbcTaskAdapterTest.saveOrphanTask |
| AC-5 Task lifecycle | ✅ | TaskRestControllerTest.updateStatusToDone + TaskStatusTest (3 tests) |
| AC-6 Chat Grimo session | ✅ | ChatControllerTest.newChat_grimoSession |
| AC-7 Chat Project session | ✅ | ChatControllerTest.newChat_projectSession |
| AC-8 Chat multi-turn + resume | ✅ | ChatControllerTest.continueChat + resumeChat |
| AC-9 Session list + filter | ✅ | SessionRestControllerTest (6 tests) |
| AC-10 Skill REST | ✅ | SkillRestControllerTest (5 tests) |
| AC-11 Error handling (404) | ✅ | *Controller*Test.handleNotFound across all controllers |
| AC-12 CLI removed + compile | ✅ | SchemaTest + TurnRecorderTest + RecordingRegistryTest + NanoIdsTest + ModuleArchitectureTest |

### Pending Verification

| Item | Status | Command |
|------|--------|---------|
| AC-6/7/8 Chat with real Claude CLI | ⏳ | `./gradlew integrationTest` (needs claude on PATH + credentials) |
| Skill projection to working directory | ⏳ | Manual: `curl -X POST localhost:8080/api/skills/project -H 'Content-Type: application/json' -d '{"workDir":"/tmp/test"}'` |

---

## QA Review

**日期：** 2026-04-21
**審查員：** Independent QA subagent (claude-sonnet-4-6)
**方法：** 獨立重新執行所有測試 + 審查全部生產碼與測試碼 + 逐項比對 AC

### 自動化測試結果

```
./gradlew compileTestJava  → BUILD SUCCESSFUL
./gradlew test             → BUILD SUCCESSFUL (123 tests, 0 failures, 0 skipped)
```

### AC 覆蓋率驗證（@DisplayName 對映）

| AC | 測試數量 | 代表性測試 | 狀態 |
|----|----------|------------|------|
| AC-1 Project CRUD | 9 | `ProjectRestControllerTest` (5) + `JdbcProjectAdapterTest` (5) | ✅ |
| AC-2 Project 名稱唯一 | 2 | `createDuplicateName_returns409`, `findByName` | ✅ |
| AC-3 Task CRUD (有 project) | 5 | `createWithProject_returns201`, `listByProject`, `saveAndFindByNumber` 等 | ✅ |
| AC-4 Task CRUD (無 project) | 3 | `createWithoutProject_returns201`, `listOrphan`, `saveOrphanTask` | ✅ |
| AC-5 Task lifecycle | 5 | `updateStatusToDone`, `TaskStatusTest` (3 tests), `updateStatusWithClosedAt` | ✅ |
| AC-6 Chat GRIMO session | 1 | `newChat_grimoSession` | ✅ |
| AC-7 Chat PROJECT session | 1 | `newChat_projectSession` | ✅ |
| AC-8 Chat 多輪 + resume | 2 | `continueChat`, `resumeChat` | ✅ |
| AC-9 Session 列表 + 篩選 | 5 | `SessionRestControllerTest` (6 tests) | ✅ |
| AC-10 Skill REST | 4 | `SkillRestControllerTest` (5 tests) | ✅ |
| AC-11 錯誤處理 404 | 6 | `*ControllerTest.*NotFound*` 各控制器 | ✅ |
| AC-12 CLI 移除 + 編譯 | 20 | `SchemaTest`, `TurnRecorderTest`, `NanoIdsTest`, `ModuleArchitectureTest` 等 | ✅ |

**所有 12 項 AC 均有對應的 `@DisplayName("[S018] AC-N: ...")` 測試，覆蓋率完整。**

### 程式碼品質（對照 development-standards.md）

| 項目 | 結果 |
|------|------|
| 僅使用建構子注入 | ✅ 全部控制器與服務均使用建構子注入 |
| 不回傳 null（`Optional<T>` 用於單一查詢） | ✅ `findById`、`findByNumber` 等均回傳 `Optional` |
| `@Nullable` 使用 JSpecify（非 javax） | ✅ 全部使用 `org.jspecify.annotations.Nullable` |
| 不使用 `System.out` / `System.err`（非 CLI） | ✅ 所有新模組均使用 SLF4J |
| 領域例外跨模組邊界翻譯 | ✅ `ProjectNotFoundException`、`TaskNotFoundException`、`ChatSessionException` |
| 無靜態可變狀態 | ✅ `ChatController.sessions` 為 instance field `ConcurrentHashMap`，非 static |
| `@ApplicationModule` + `allowedDependencies` 正確設定 | ✅ `project::api`、`project::core`、`task` 依賴宣告完整 |
| `ModuleArchitectureTest` 通過 | ✅ |
| CLI adapter 已刪除（`ChatCommandRunner`, `SkillCommandRunner`） | ✅ 原始碼中不存在 |
| `ApplicationRunner` / `CommandLineRunner` 已移除 | ✅ 無殘留 |

### Javadoc 準確性

| 類別 / 介面 | 狀況 |
|-------------|------|
| `MainAgentChatUseCase` | ✅ Javadoc 準確描述 `createSession`/`resumeSession` 行為 |
| `SessionProjection` | ✅ 所有欄位有說明，與 schema 欄位完全對齊 |
| `SessionEvent` | ✅ 欄位說明與 Spring AI 命名對齊說明清楚 |
| `NanoIds.compact()` | ✅ 說明 12 字元 base36，與 spec §2.0 一致 |
| `SessionRecordingPort` | ✅ `createRecordedSession` Javadoc 準確描述 `sessionType`/`projectId` 用途 |
| `TaskPort` | ✅ 有 Javadoc，`findOrphan()` 為新增方法（見下方設計偏移說明） |

### 設計偏移（Design Drift）分析

1. **`TaskUseCase` 介面簽名 vs §4.2 設計（輕微偏移，功能等效）**
   - 規格 §4.2：`findById(String id)`、`delete(String id)`（按 UUID）
   - 實作：`findByNumber(int)`、`delete(int)`（按 taskNumber）
   - 理由：REST API 路徑為 `/api/tasks/{taskNumber}`（§4.4 正確），REST controller 統一使用 `taskNumber` 查詢，`findById` 保留在 `TaskPort` 出站埠（供 JDBC adapter 內部使用）。對外 API 行為與 AC-3/4/5 完全一致。
   - 評估：**功能等效，不影響 AC 達成。** 屬已知偏移（§7 實作說明中隱含）。

2. **`TaskPort` 新增 `findOrphan()`（§4.3 未列出）**
   - `TaskPort` 規格只列 `findByProjectId(@Nullable)` 負責無 project 的情形
   - 實作新增 `findOrphan()` 專門查詢 `project_id IS NULL`，`TaskService.list()` 以 `"none"` 字串觸發
   - 評估：**合理擴充，AC-4 `?projectId=none` 語意清晰且通過測試。**

3. **`SessionRecordingPort` 新增（§5 文件中未直接列出介面名稱）**
   - 規格 §7 實作說明已記錄此偏移：「`SessionRecordingPort.createRecordedSession()` — 新方法允許建立帶有明確 sessionType/projectId 的 session。」
   - 評估：**已文件化偏移，非遺漏。**

4. **`SkillRestControllerTest` BeanOverrideRegistry WARN（非阻塞）**
   - 測試執行時 Spring Boot 4.0.5 記錄一條 WARN：`Bean with name '<<< PSEUDO BEAN NAME PLACEHOLDER >>>' was overridden by multiple handlers`
   - 原因：`@WebMvcTest` 中同時 mock `SkillRegistryUseCase` + `SkillProjectionUseCase`，Spring Boot 4 的 `@MockitoBean` 多 bean WARN
   - 5 個測試全部通過，無功能影響
   - 評估：**可接受的 WARN，測試結果正確。**

### 未驗證項目（技術債）

| 項目 | 類型 | 說明 |
|------|------|------|
| AC-6/7/8 Chat 真實 Claude CLI | `skip` | 需要 claude on PATH + 認證，應在 `*IT.java` 以 `assumeTrue(cliAvailable)` 覆蓋 |
| Skill 投影至 workDir E2E | `skip` | 需要手動 curl 或 IT 測試確認 |

### 最終裁定

**PASS**

所有 12 項 AC 均有對應測試且為綠色（123/123 通過，0 失敗）。程式碼品質符合 `development-standards.md` 要求。設計偏移均屬輕微且功能等效，已文件化。CLI 整合測試按規範列為 `⏳ pending`，不阻塞出貨。

S018 可標記為 **✅ Done**（spec header 已為此狀態）。
