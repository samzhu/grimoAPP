# S017: Grimo Session Memory — 對話歷史記錄

> Spec: S017 | Size: S (9) | Status: ⏳ Design
> Date: 2026-04-20

---

## 1. Goal

建立 Grimo 自有的對話歷史記錄層。在 `AgentSession.prompt()` 每一輪攔截 user input 和 assistant response，存入 Spring AI `ChatMemory`，以 `JdbcChatMemoryRepository` + H2 持久化。

**兩層分離設計（PRD P3「Grimo 擁有 session」）：**

| 層 | 管理者 | 儲存 | 用途 |
|---|--------|------|------|
| Grimo 層 | `RecordingAgentSession` decorator | `ChatMemory` → H2 | 跨 CLI 切換、歷史查詢、壓縮重放 |
| CLI 層 | `ClaudeAgentSessionRegistry` | `~/.claude/projects/*.jsonl` | `--resume` / `--continue`（Claude 原生） |

Grimo 的記錄與 Claude CLI 的 transcript 完全獨立。未來 Gemini/Codex（無 session 概念）也能被記錄，因為記錄層在 AgentSession 介面之上。

**晉升自 Backlog。** 原 Backlog 項目「持久化 Session」設計為 `SessionMemoryAdvisor`（S011 POC 否定），本 spec 改用 decorator 模式 + Spring AI core `ChatMemory`。

**依賴。** S007 ✅（主代理 REPL）、S011 ✅（AgentSession API 驗證）。

**不包含。** 跨 CLI 壓縮重放（Backlog 另行規劃）。AutoMemoryTools 長期記憶（Backlog 另行規劃）。Session 列表 / 匯出 CLI 子命令（後續 spec）。

### 1.1 參考

- [Spring AI Agentic Patterns Part 6: AutoMemoryTools](https://spring.io/blog/2026/04/07/spring-ai-agentic-patterns-6-memory-tools) — 兩層記憶架構：ChatMemory（對話歷史）+ AutoMemoryTools（長期策展）。本 spec 實作 ChatMemory 層。
- `AgentSession` 原始碼分析（2026-04-20）— 確認無 `getMessages()` API，Grimo 必須自建攔截層。
- `ClaudeAgentSessionRegistry` 原始碼分析 — `sessions` map 為 `private final`，decorator 在 interface 層是最乾淨的擴展方式。

---

## 2. Approach

### 2.1 Decorator 模式

```
MainAgentChatService
  → injects AgentSessionRegistry (Spring bean)
    → RecordingAgentSessionRegistry (decorator, session module)
      → delegates to ClaudeAgentSessionRegistry
      → wraps returned AgentSession with RecordingAgentSession

RecordingAgentSession.prompt(userText):
  1. chatMemory.add(sessionId, new UserMessage(userText))
  2. AgentResponse response = delegate.prompt(userText)
  3. chatMemory.add(sessionId, new AssistantMessage(response.getText()))
  4. return response
```

**為什麼 decorator 而非 advisor：** `AgentCallAdvisor`（agent-client）攔截 `AgentClient.run()`（無狀態單次呼叫），不攔截 `AgentSession.prompt()`（有狀態多輪）。Spring AI 的 `MessageChatMemoryAdvisor` 給 `ChatClient` 用，不給 `AgentSession` 用。Decorator 是唯一能攔截 `AgentSession.prompt()` 的模式。

### 2.2 持久化：JdbcChatMemoryRepository + H2

使用 Spring AI core module（非 starter）手動接線：

```java
// SessionModuleConfig.java
@Bean
ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
    return JdbcChatMemoryRepository.builder()
        .jdbcTemplate(jdbcTemplate)
        .dialect(JdbcChatMemoryRepositoryDialect.from(dataSource))
        .build();
}

@Bean
ChatMemory chatMemory(ChatMemoryRepository repo) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(repo)
        .maxMessages(100)
        .build();
}
```

**依賴（build.gradle.kts 變更）：**
```kotlin
// 新增 Spring AI BOM — 統一管理所有 spring-ai-* 版本
extra["springAiVersion"] = "2.0.0-M4"

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

dependencies {
    // 版本由 BOM 管理
    implementation("org.springframework.ai:spring-ai-model-chat-memory-repository-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("com.h2database:h2")
}
```

- `spring-ai-agent-utils:0.7.0` 要求 Spring AI ≥ 2.0.0-M4 — BOM 對齊
- H2 + JDBC starter 為本 spec 首次引入（S011 發現 Claude CLI 不需要，但 Grimo 層需要）
- Schema 自動初始化（H2 embedded 模式預設 `initialize-schema=embedded`），建立 `SPRING_AI_CHAT_MEMORY` 表

**POC: required（低風險）** — BOM 已保證版本一致，POC 改為驗證「H2 datasource + schema init + ChatMemory.add/get 端對端」能跑通。退回方案 B（自建 `ChatMemoryRepository`）保留但預期不需要。

### 2.3 模組設計：`session` 模組

新建 `session` Modulith 模組（從 Backlog 晉升）：

```
io.github.samzhu.grimo.session
├── package-info.java                    # @ApplicationModule, allowedDependencies = { "core" }
├── domain/
│   └── ConversationTurn.java            # record(sessionId, role, content, timestamp)
├── application/
│   ├── port/in/
│   │   ├── package-info.java            # @NamedInterface("api")
│   │   └── ConversationMemoryUseCase.java  # getHistory(sessionId), clear(sessionId)
│   └── service/
│       └── ConversationMemoryService.java
├── adapter/out/
│   └── SpringAiChatMemoryAdapter.java   # wraps ChatMemory → ConversationMemoryUseCase
└── internal/
    ├── RecordingAgentSession.java        # decorator
    ├── RecordingAgentSessionRegistry.java # decorator
    └── SessionModuleConfig.java         # @Bean wiring
```

**跨模組接線：**
- `agent` 模組注入 `AgentSessionRegistry`（Spring bean）
- `session` 模組的 `RecordingAgentSessionRegistry` 實作 `AgentSessionRegistry`，用 `@Primary` 覆蓋 Claude 原生 bean
- `agent` 不需要知道 recording 存在 — 透明包裝

### 2.4 Research Citations

- **`ChatMemory` interface**（spring-ai-model:2.0.0-M2，已在 classpath）— `add(conversationId, messages)`, `get(conversationId)`, `clear(conversationId)`。[source](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-model/src/main/java/org/springframework/ai/chat/memory/ChatMemory.java)
- **`ChatMemoryRepository` interface** — `findConversationIds()`, `findByConversationId()`, `saveAll()`, `deleteByConversationId()`。[source](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-model/src/main/java/org/springframework/ai/chat/memory/ChatMemoryRepository.java)
- **`JdbcChatMemoryRepository`** — `builder().jdbcTemplate(jt).dialect(auto).build()`。手動使用不需 starter。[docs](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- **`AgentSession`** — 無 `getMessages()` API；`prompt()` 是唯一攔截點。[source](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-model/src/main/java/org/springaicommunity/agents/model/AgentSession.java)
- **Spring AI Agentic Patterns Part 6** — ChatMemory（短期滑動視窗）vs AutoMemoryTools（長期策展）兩層架構。[blog](https://spring.io/blog/2026/04/07/spring-ai-agentic-patterns-6-memory-tools)

---

## 3. SBE Acceptance Criteria

**驗證命令：** `./gradlew test`
Pass: 所有攜帶 S017 AC id 的測試為綠色。

---

**AC-1: 對話記錄 — 每輪 prompt/response 存入 ChatMemory**

```
Given  RecordingAgentSession 包裝了一個 mock AgentSession
When   呼叫 prompt("hello") 且 delegate 回傳 "Hi there"
Then   chatMemory.get(sessionId) 包含 2 條 Messages
And    第 1 條為 UserMessage("hello")
And    第 2 條為 AssistantMessage("Hi there")
```

**AC-2: 多輪累積**

```
Given  RecordingAgentSession 已記錄 2 輪對話（4 條 Messages）
When   呼叫第 3 輪 prompt("bye")
Then   chatMemory.get(sessionId) 包含 6 條 Messages
And    順序為 user-assistant-user-assistant-user-assistant
```

**AC-3: Registry decorator 透明包裝**

```
Given  RecordingAgentSessionRegistry 包裝了 ClaudeAgentSessionRegistry
When   呼叫 create(workDir)
Then   回傳的 AgentSession 是 RecordingAgentSession
And    sessionId 與底層 Claude session 一致
And    workingDirectory 與底層一致
```

**AC-4: H2 持久化 — SPRING_AI_CHAT_MEMORY 表**

```
Given  應用以 H2 file mode 啟動
When   完成 3 輪對話後關閉 session
Then   SPRING_AI_CHAT_MEMORY 表中存在該 conversationId 的記錄
And    記錄數 ≥ 6（3 user + 3 assistant）
```

**AC-5: Modulith 邊界合規**

```
Given  session 模組以 @ApplicationModule 宣告
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
    String sessionId,
    String role,       // "user" | "assistant"
    String content,
    Instant timestamp
) {}

// === session module: port/in (exposed via @NamedInterface("api")) ===

// session/application/port/in/ConversationMemoryUseCase.java
public interface ConversationMemoryUseCase {
    List<ConversationTurn> getHistory(String sessionId);
    void clear(String sessionId);
}

// === session module: internal (NOT exposed) ===

// session/internal/RecordingAgentSession.java
class RecordingAgentSession implements AgentSession {
    RecordingAgentSession(AgentSession delegate, ChatMemory chatMemory) { ... }

    @Override
    public AgentResponse prompt(String message) {
        chatMemory.add(getSessionId(), new UserMessage(message));
        AgentResponse response = delegate.prompt(message);
        chatMemory.add(getSessionId(), new AssistantMessage(response.getText()));
        return response;
    }
    // other methods delegate directly
}

// session/internal/RecordingAgentSessionRegistry.java
@Primary
class RecordingAgentSessionRegistry implements AgentSessionRegistry {
    RecordingAgentSessionRegistry(AgentSessionRegistry delegate, ChatMemory chatMemory) { ... }

    @Override
    public AgentSession create(Path workDir) {
        return new RecordingAgentSession(delegate.create(workDir), chatMemory);
    }
    // find, evict, evictStale delegate directly
}
```

---

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `build.gradle.kts` | modify | 加 Spring AI BOM 2.0.0-M4 + `spring-ai-model-chat-memory-repository-jdbc` + `spring-boot-starter-jdbc` + `h2`（runtime） |
| `src/main/resources/application.yml` | modify | H2 datasource + schema init 設定 |
| `session/package-info.java` | new | `@ApplicationModule(allowedDependencies = { "core" })` |
| `session/domain/ConversationTurn.java` | new | 純 record |
| `session/application/port/in/package-info.java` | new | `@NamedInterface("api")` |
| `session/application/port/in/ConversationMemoryUseCase.java` | new | 入站埠 |
| `session/application/service/ConversationMemoryService.java` | new | 用例實作 |
| `session/adapter/out/SpringAiChatMemoryAdapter.java` | new | 包裝 ChatMemory |
| `session/internal/RecordingAgentSession.java` | new | AgentSession decorator |
| `session/internal/RecordingAgentSessionRegistry.java` | new | Registry decorator, `@Primary` |
| `session/internal/SessionModuleConfig.java` | new | JdbcChatMemoryRepository + ChatMemory bean 接線 |
| `agent/package-info.java` | modify | 可能需加 `"session::api"` 或保持不變（若 `@Primary` 透明注入） |
| `src/test/java/.../session/RecordingAgentSessionTest.java` | new | AC-1, AC-2 |
| `src/test/java/.../session/RecordingAgentSessionRegistryTest.java` | new | AC-3 |
| `src/test/java/.../session/SessionPersistenceIT.java` | new | AC-4（H2 整合測試） |

---

### 估算

| 維度 | 分數 | 理由 |
|------|------|------|
| 技術風險 | 1 | BOM 2.0.0-M4 統一管理，版本風險低；POC 驗證 H2 端對端 |
| 不確定性 | 1 | 設計經 grill 確認 |
| 依賴關係 | 1 | S007 + S011 已出貨 |
| 範疇 | 2 | 新模組 + decorator + JDBC + schema |
| 測試 | 2 | 單元 + H2 整合 |
| 可逆性 | 2 | 新模組，但 @Primary 覆蓋現有 bean 有風險 |
| **合計** | **9** | **S** |
