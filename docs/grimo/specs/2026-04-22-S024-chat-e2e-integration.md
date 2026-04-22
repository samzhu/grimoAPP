# S024: Chat E2E Integration — 真實 Claude CLI 全棧驗收

> Spec: S024 | Size: S (9) | Status: ✅ Done
> Date: 2026-04-22

---

## 1. Goal

**一句話：** 以 `@SpringBootTest` + 真實 Claude CLI 進行端對端整合測試，驗證 Chat REST API → Session Recording → H2 持久化 → Message Tree 的完整垂直切片。

### 背景

S000–S023 共 14 個 spec 已出貨。現有測試覆蓋：

| 測試層 | 覆蓋狀態 | 驗證範圍 |
|--------|----------|----------|
| T0 Unit（domain records） | ✅ 充足 | record 建構、驗證、純邏輯 |
| T2 Slice（@WebMvcTest） | ✅ 5 controller | HTTP 序列化 — use case 全部 mock |
| T2 Slice（JDBC adapter + H2） | ✅ 3 adapter | 單模組 DB round-trip |
| Schema 驗證 | ✅ SchemaTest | 欄位存在、FK、約束 |
| Session recording（純 H2） | ✅ TurnRecorderTest | TurnRecorder → JDBC → H2（無 HTTP、無 CLI） |
| Message Tree（純 H2） | ✅ ConversationPathTest | parent-child chain + CTE（無 HTTP、無 CLI） |
| **E2E（@SpringBootTest + 真實 CLI）** | **❌ 缺失** | **HTTP → Spring → Claude CLI → Session → H2 全棧** |

**核心缺口：** 沒有任何測試驗證「從 HTTP 請求進入到 Claude CLI 真實回應再到 DB 持久化」這條完整路徑。現有 `MainAgentChatIT`（S007）直接呼叫 `ClaudeAgentSessionRegistry`，不經過 Spring 上下文，不走 HTTP，不驗 Session recording。

本 spec 建立一個 `@SpringBootTest` 整合測試，透過 MockMvc 發送真實 HTTP 請求，觸發完整的 Spring 上下文（含 `RecordingAgentSessionRegistry`、`TurnRecorder`、JDBC adapter），呼叫真實 Claude CLI，並驗證 H2 中的 session event + projection + message tree。

### 不包含

| 項目 | 理由 |
|------|------|
| Project / Task CRUD E2E | 單元測試 + Slice 測試已充分覆蓋 |
| Sandbox / Docker（S003-S006） | 無 REST 端點，S008 未出貨 |
| Skill 檔案系統操作 | 單元測試已覆蓋 |
| Gemini / Codex CLI | S009-S010 未出貨 |

### 依賴

S018 ✅（REST API Foundation）、S023 ✅（Message Tree）。無程式碼層級阻塞。

---

## 2. Approach

### 2.1 測試架構

```
@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
ChatEndToEndIT
    │
    ├─ @BeforeAll: assumeTrue(claudeAvailable())
    │
    ├─ POST /api/chat  ──→ ChatController
    │                        ├─ SkillProjectionUseCase.projectToWorkDir()
    │                        ├─ MainAgentChatService.createSession()
    │                        │    ├─ RecordingAgentSessionRegistry.createRecordedSession()
    │                        │    │    ├─ ClaudeAgentSessionRegistry.create(workDir)  ← 真實 CLI
    │                        │    │    └─ new RecordingAgentSession(raw, ...)
    │                        │    └─ return AgentSession
    │                        ├─ session.prompt(message)  ← 真實 Claude 回應
    │                        │    └─ RecordingAgentSession.prompt()
    │                        │         ├─ delegate.prompt()  ← 呼叫 claude CLI
    │                        │         └─ eventPublisher.publishEvent(TurnRecorded)
    │                        │              └─ TurnRecorder.on(TurnRecorded)
    │                        │                   ├─ INSERT grimo_session_event (USER)
    │                        │                   ├─ INSERT grimo_session_event (ASSISTANT)
    │                        │                   ├─ UPSERT grimo_session (projection)
    │                        │                   └─ UPDATE current_event_id
    │                        └─ return ChatResponse(sessionId, response)
    │
    ├─ GET /api/sessions/{id}         ← 驗 projection
    ├─ GET /api/sessions/{id}/events  ← 驗 event chain
    └─ POST /api/chat/{sessionId}     ← 多輪 + 驗 turn_count
```

### 2.2 跳過策略

遵循 `development-standards.md` §7.5 三層策略：

```java
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
    disabledReason = "Requires local claude CLI + valid login")
```

加上 `@BeforeAll`（`@TestInstance(PER_CLASS)` 下只需檢查一次）：

```java
Assumptions.assumeTrue(claudeAvailable(),
    "claude CLI not found on PATH — skipping IT");
```

### 2.3 workDir 處理

使用 `@TempDir` 提供測試專用工作目錄，避免污染專案目錄。Skill projection 在空的 `~/.grimo/skills/` 下為 no-op。

### 2.4 Project-scoped Chat

測試 Project 級 session 需要先透過 REST API 建立 Project，取得 `projectId`，再帶入 `POST /api/chat`。驗證 session 的 `session_type = PROJECT` 和 `project_id` 正確設定。

### 2.5 Research Citations

- **Spring Boot Test + MockMvc：** Spring Boot 4.0.5 `@AutoConfigureMockMvc` — 在 `MOCK` web environment 下注入 `MockMvc`，走完整 DispatcherServlet 但不啟動真實 Tomcat。已由 S018 的 5 個 `@WebMvcTest` 驗證。
- **`@SpringBootTest` 與 H2：** Boot 自動配置 `DataSource` 使用 `schema.sql`。S018 的 TurnRecorderTest 已驗證 H2 + `schema.sql` round-trip。
- **`RecordingAgentSessionRegistry` 為 `@Primary`：** 確保 `@SpringBootTest` 上下文中的 `AgentSessionRegistry` bean 自動包裝 recording decorator — 不需要額外配置。
- **Claude CLI session timeout：** `MainAgentChatService` 使用 `Duration.ofMinutes(30)` timeout，但 E2E 測試的 prompt 應在 60 秒內回應。測試可加 `assertTimeout` 防護。

---

## 3. SBE Acceptance Criteria

**驗證命令：** `./gradlew integrationTest`
Pass: 所有攜帶 S024 AC id 的測試為綠色（或因缺少 claude CLI 而 skip — 非 fail）。

---

**AC-1: 新建 GRIMO session → 真實 Claude 回應 → HTTP 200**

```
Given  主機已安裝 claude CLI 且已登入
When   POST /api/chat { "message": "Reply with exactly: GRIMO_E2E_OK" }
Then   HTTP 200
And    response.sessionId 非空（UUID 格式）
And    response.response 非空（真實 Claude 回應）
```

**AC-2: Session event 持久化至 H2**

```
Given  AC-1 完成
When   GET /api/sessions/{sessionId}/events
Then   HTTP 200
And    回傳恰好 2 筆 events（USER + ASSISTANT）
And    第 1 筆 event: messageType = USER, messageContent 含使用者 prompt
And    第 2 筆 event: messageType = ASSISTANT, messageContent 非空
And    ASSISTANT event 的 provider 非空（USER event 的 provider 為 null — by design）
```

**AC-3: Session projection 正確物化**

```
Given  AC-1 完成
When   GET /api/sessions/{sessionId}
Then   HTTP 200
And    turnCount = 1
And    totalTokensIn > 0（真實 Claude 回應有 token 數據）
And    totalDurationMs > 0
And    status = ACTIVE
And    sessionType = GRIMO
And    currentEventId 非空（指向最後一個 ASSISTANT event）
```

**AC-4: 多輪對話 → parent-child chain + turn_count 遞增**

```
Given  AC-1 完成（session 已有 1 輪）
When   POST /api/chat/{sessionId} { "message": "What was my previous message?" }
Then   HTTP 200 + 非空 response
When   GET /api/sessions/{sessionId}/events
Then   回傳 4 筆 events
And    每筆 event 的 parentEventId 指向前一筆（線性鏈）
And    第 1 筆 event 的 parentEventId = null（根節點）
When   GET /api/sessions/{sessionId}
Then   turnCount = 2
And    totalTokensIn > AC-3 的 totalTokensIn（累積）
And    currentEventId = 最後一個 ASSISTANT event 的 id
```

**AC-5: Project-scoped session**

```
Given  POST /api/projects { "name": "e2e-test", "workDir": "<tempDir>" } → 201
When   POST /api/chat { "message": "hello", "projectId": "<projectId>" }
Then   HTTP 200
When   GET /api/sessions/{sessionId}
Then   sessionType = PROJECT
And    projectId = 建立的 project id
When   GET /api/sessions?sessionType=PROJECT
Then   回傳的 session 列表包含此 sessionId
When   GET /api/sessions?projectId={projectId}
Then   回傳的 session 列表包含此 sessionId
```

**AC-6: Session close**

```
Given  AC-1 完成
When   DELETE /api/chat/{sessionId}
Then   HTTP 204
When   POST /api/chat/{sessionId} { "message": "hello" }
Then   HTTP 404（session 已從記憶體移除）
```

---

## 4. Interface / API Design

### 4.1 無生產程式碼變更

本 spec 為純測試 spec — 不修改任何生產程式碼。所有變更限於 `src/test/`。

### 4.2 測試類別

```java
package io.github.samzhu.grimo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
    disabledReason = "Requires local claude CLI + valid login")
class ChatEndToEndIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @TempDir Path tempDir;

    @BeforeAll
    void skipIfCliUnavailable() {
        Assumptions.assumeTrue(claudeAvailable(),
            "claude CLI not found on PATH");
    }

    // AC-1
    @Test @DisplayName("[S024] AC-1: new GRIMO session with real claude")
    void newGrimoSessionWithRealClaude() { ... }

    // AC-2
    @Test @DisplayName("[S024] AC-2: session events persisted to H2")
    void sessionEventsPersisted() { ... }

    // AC-3
    @Test @DisplayName("[S024] AC-3: session projection materialized")
    void sessionProjectionMaterialized() { ... }

    // AC-4
    @Test @DisplayName("[S024] AC-4: multi-turn parent-child chain")
    void multiTurnParentChildChain() { ... }

    // AC-5
    @Test @DisplayName("[S024] AC-5: project-scoped session")
    void projectScopedSession() { ... }

    // AC-6
    @Test @DisplayName("[S024] AC-6: session close")
    void sessionClose() { ... }

    private static boolean claudeAvailable() {
        try {
            return new ProcessBuilder("which", "claude")
                .start().waitFor() == 0;
        } catch (Exception e) { return false; }
    }
}
```

### 4.3 測試內部流程

每個 AC 的測試方法使用 `mockMvc.perform()` 發送 HTTP 請求，解析 JSON 回應，再發送驗證請求。AC-1 到 AC-4 共用同一個 session（方法內循序執行），減少 Claude CLI 呼叫次數。

設計選項考量：

| 方案 | 優點 | 缺點 |
|------|------|------|
| 每 AC 獨立 @Test + @Order | 各測試獨立、失敗易定位 | 每次建立新 session = 更多 CLI 呼叫 |
| AC-1~4 合併為一個 @Test | 一次 session、快 | 失敗時不易定位 |
| **AC-1~4 獨立 @Test + @TestInstance(PER_CLASS) + @TestMethodOrder** | ⭐ 共用 session、獨立斷言 | 需依序執行 |

選擇第三方案 — `@TestInstance(Lifecycle.PER_CLASS)` + `@TestMethodOrder(OrderAnnotation.class)`，讓 AC-1 建立 session 後，後續 AC 共用。

---

## 5. File Plan

### 新增檔案

| File | Description |
|------|-------------|
| `src/test/java/io/github/samzhu/grimo/ChatEndToEndIT.java` | 全棧 E2E 整合測試（6 AC） |

### 修改檔案

無。

**合計：** 1 新增 test file、0 修改 = **1 個檔案接觸點**

---

### 估算

| 維度 | 分數 | 理由 |
|------|------|------|
| 技術風險 | 2 | 真實 CLI 程序管理 + 回應時序不確定 |
| 不確定性 | 2 | Claude CLI 回應格式、token 數據是否正確傳播 |
| 依賴關係 | 1 | S018 ✅、S023 ✅ |
| 範疇 | 2 | 6 AC、跨 5 個模組的全棧驗證 |
| 測試 | 1 | 本 spec 就是測試 |
| 可逆性 | 1 | 純測試檔案，刪除無影響 |
| **合計** | **9** | **S** |

---

## 6. Task Plan

### POC Decision

**POC: not required.** 所有技術元件已由先前 spec 驗證：
- `@SpringBootTest` + MockMvc：Spring Boot 4.0.5 標準測試模式
- H2 + `schema.sql`：S018 TurnRecorderTest / S023 ConversationPathTest 驗證
- 真實 Claude CLI：S007 MainAgentChatIT 驗證
- `RecordingAgentSessionRegistry`（`@Primary`）：RecordingRegistryTest 驗證

### Task Summary

| Task | AC | Description | Depends On |
|------|----|-------------|------------|
| T01 | AC-1, AC-2, AC-3, AC-4, AC-6 | ChatEndToEndIT 建立 + GRIMO session 全棧 E2E | — |
| T02 | AC-5 | Project-scoped session E2E | T01 |

### Execution Order

```
T01 (scaffolding + GRIMO session E2E: 5 AC)
 └──→ T02 (Project-scoped session: 1 AC)
```

Task files: `docs/grimo/tasks/2026-04-22-S024-T0{1,2}.md`

---

## 7. Implementation Results

### Verification Results

```
./gradlew compileTestJava  → BUILD SUCCESSFUL
./gradlew test             → BUILD SUCCESSFUL (unit tests pass, IT excluded)
./gradlew integrationTest --tests ChatEndToEndIT → BUILD SUCCESSFUL (6 tests, 0 failed, ~32s)
```

### Key Findings

1. **Boot 4.0.5 import path change.** `AutoConfigureMockMvc` moved from `org.springframework.boot.test.autoconfigure.web.servlet` to `org.springframework.boot.webmvc.test.autoconfigure`。architecture.md 的框架依賴表未記載此遷移。

2. **USER event 的 provider/model/metadata 為 null.** `TurnRecorder` 只在 ASSISTANT event 設定 `provider`、`model`、`metadata`（TurnRecorder.java line 66-71 vs 74-79）。USER event 只攜帶 `messageContent`。§3 AC-2 已更新反映此設計。

3. **真實 Claude CLI E2E 計時.** 完整 6 個測試（3 次 Claude CLI 呼叫）約 32 秒完成。`@TestInstance(PER_CLASS)` + `@TestMethodOrder` 讓 AC-1~4 共用 session，大幅減少 CLI 呼叫次數。

4. **`@TempDir` + `@TestInstance(PER_CLASS)` 相容.** Class-level `@TempDir` 在 PER_CLASS lifecycle 下正常共用，所有測試方法共享同一個暫存目錄。

5. **Skill projection 安全.** 當 `~/.grimo/skills/` 不存在時，`Skills.loadDirectory()` 回傳空列表，`SkillProjectionService.projectToWorkDir()` 為 no-op — 不影響 E2E 測試。

### [Implementation note] Divergence from §2/§3

- §2.5 引用 `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` — 實際 Boot 4.0.5 路徑為 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`。
- §3 AC-2 原寫「兩筆 event 的 provider 非空」— 實際只有 ASSISTANT event 有 provider。已更新 §3。

### AC Results

| AC | Status | Test Method |
|----|--------|-------------|
| AC-1 new GRIMO session + real Claude | ✅ | `ac1_newGrimoSessionWithRealClaude` |
| AC-2 session events persisted to H2 | ✅ | `ac2_sessionEventsPersisted` |
| AC-3 session projection materialized | ✅ | `ac3_sessionProjectionMaterialized` |
| AC-4 multi-turn parent-child chain | ✅ | `ac4_multiTurnParentChildChain` |
| AC-5 project-scoped session | ✅ | `ac5_projectScopedSession` |
| AC-6 session close | ✅ | `ac6_sessionClose` |

### Pending Verification

| Item | Command | Status |
|------|---------|--------|
| CI 環境（無 claude CLI） | `CI=true ./gradlew integrationTest` | ⏳ 預期 6 tests skipped（`@DisabledIfEnvironmentVariable`） |

---

## 8. QA Review

**Reviewer:** Independent QA subagent (`/verifying-quality`)
**Date:** 2026-04-22
**Verdict:** ✅ PASS

---

### Automated Verification

```
./gradlew compileTestJava    → BUILD SUCCESSFUL
./gradlew test --rerun-tasks → BUILD SUCCESSFUL（IT 已正確排除）
./gradlew integrationTest --tests ChatEndToEndIT --rerun-tasks → BUILD SUCCESSFUL（6 tests, 0 failed, ~35s）
```

---

### AC Coverage Audit

| AC | §3 Criteria | @DisplayName Test | Verdict |
|----|-------------|-------------------|---------|
| AC-1 | 新建 GRIMO session + HTTP 200 | `ac1_newGrimoSessionWithRealClaude` | ✅ Full |
| AC-2 | 2 events (USER+ASSISTANT) + provider | `ac2_sessionEventsPersisted` | ✅ Full |
| AC-3 | turnCount=1, tokensIn>0, duration>0, ACTIVE, GRIMO | `ac3_sessionProjectionMaterialized` | ✅ Full |
| AC-4 | 4 events, parent-child chain, turnCount=2, tokens累積 | `ac4_multiTurnParentChildChain` | ✅ Full |
| AC-5 | sessionType=PROJECT, projectId 設定, 列表篩選 | `ac5_projectScopedSession` | ✅ Full |
| AC-6 | DELETE 204, POST 404 | `ac6_sessionClose` | ✅ Full |

---

### Testability Gate

| AC | Classification | Evidence |
|----|---------------|----------|
| AC-1 | VERIFIED | `./gradlew integrationTest` → PASS |
| AC-2 | VERIFIED | `./gradlew integrationTest` → PASS |
| AC-3 | VERIFIED | `./gradlew integrationTest` → PASS |
| AC-4 | VERIFIED | `./gradlew integrationTest` → PASS |
| AC-5 | VERIFIED | `./gradlew integrationTest` → PASS |
| AC-6 | VERIFIED | `./gradlew integrationTest` → PASS |

---

### Code Quality Review

| 檢查項目 | 狀態 | 備註 |
|----------|------|------|
| §7.2 IT 後綴命名 | ✅ | `ChatEndToEndIT` |
| §7.3 不模擬 CLI 子程序 | ✅ | 無 Mockito.mock(Process) |
| §7.4 主機二進位 | ✅ | 真實 `claude` CLI |
| §7.5 三層跳過策略 | ✅ | 層 1: `@DisabledIfEnvironmentVariable`; 層 3: `@BeforeAll` + `Assumptions.assumeTrue` |
| §7.9 Given/When/Then | ✅ | 全部 6 方法均有 |
| §3 命名 | ✅ | `ac1_*` 前綴風格一致 |
| §11 禁止模式 | ✅ | 無靜態可變狀態、無 System.out |
| 未使用 import | ✅ | 已移除 `java.time.Duration` |

**`@Autowired` 欄位注入：** 測試類別使用 `@Autowired MockMvc` / `@Autowired ObjectMapper` — 欄位注入。§4 規定「僅使用建構子注入」，但此規則適用於生產程式碼。JUnit 5 + `@SpringBootTest` 不支援建構子注入 Spring beans 到測試類別。業界標準做法，可接受。

**`@BeforeAll` vs §7.5 的 `@BeforeEach`：** §7.5 原文指定 `@BeforeEach`。實作使用 `@BeforeAll`，在 `@TestInstance(PER_CLASS)` 下功能等價且更高效（CLI 可用性不會在測試中途改變）。§2.2 和 §4.2 已更新為 `@BeforeAll`。

---

### Design Sync

| §2/§4 原文 | 實作 | 同步狀態 |
|------------|------|---------|
| §2.1 `@BeforeEach` | `@BeforeAll` | ✅ 已修正為 `@BeforeAll` |
| §2.2 `@BeforeAll` | `@BeforeAll` | ✅ 一致 |
| §2.5 import 路徑 | `boot.webmvc.test.autoconfigure` | ✅ §7 已記載 |
| §4.2 `@BeforeEach` | `@BeforeAll` | ✅ 已修正為 `@BeforeAll` |

---

### Four-Layer Summary

| Layer | Result | Detail |
|-------|--------|--------|
| Automated tests | ✅ PASS | `./gradlew test` + `compileTestJava` 通過 |
| Coverage / Integration | ✅ PASS | `./gradlew integrationTest` — 6/6 pass |
| Manual verification | N/A | 純自動化 E2E 測試 spec |
| Testability gate | ✅ CLEAR | 全部 6 AC 均 VERIFIED |
