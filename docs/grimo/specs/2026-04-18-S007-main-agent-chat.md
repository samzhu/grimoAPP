# S007: 主代理 CLI 對話（grimo chat）

> Spec: S007 | Size: XS (7) | Status: ⏳ Design
> Date: 2026-04-18

---

## 1. Goal

`grimo chat` 子命令啟動多輪互動對話 session。使用 Spring AI Community agent-client 0.12.2 的 `ClaudeAgentSessionRegistry` 維持持續運行的主機 claude CLI process，`AgentSession.prompt()` 實現多輪對話。

MVP 驗證目標：agent-client SDK → 主機 claude binary → 使用者終端 REPL 的端到端可行性。

**不在範圍內（延後至後續 spec）：**
- Docker 容器化（grimo-runtime 映像、wrapper script、env var 注入、CredentialResolver）
- Web UI / TUI
- Session 持久化
- 唯讀工具限制（`--allowedTools`）
- 多 provider 支援（Codex / Gemini）

**依賴：** 無程式碼層級依賴。S006 ✅ 已出貨但 S007 不消費其型別。agent-client 0.12.2 為 library dependency（已在 `build.gradle.kts` 中宣告），不觸發 Modulith 跨模組違規。

## 2. Approach

**直接推薦（XS 規格）。**

### 2.1 Decisions

| # | Decision | Chosen | Why |
|---|----------|--------|-----|
| D1 | **對話機制** | `ClaudeAgentSessionRegistry` + `AgentSession.prompt()` | 維持同一 claude CLI process，多輪對話自動保持上下文。比 `AgentClient.run()`（每次建立/銷毀 process，無狀態）更適合 chat 場景。 |
| D2 | **CLI binary** | 主機 claude（auto-discover on PATH） | `ClaudeAgentSessionRegistry.builder().build()` 未設定 `claudePath` 時自動以 `ClaudeCliDiscovery` 尋找 PATH 上的 `claude`。使用者已有 `claude login` session，零配置。容器化延後到下一個 spec。 |
| D3 | **子命令偵測** | `ApplicationRunner` + `ApplicationArguments.getNonOptionArgs()` | Spring Boot 內建機制，無需外部 CLI 框架（picocli / Spring Shell）。XS 範疇下最簡方案。 |
| D4 | **唯讀限制** | MVP 不限制 | 使用者直接操作，主機 claude 使用預設 permission mode。唯讀工具允許清單是 Backlog 項目（PRD §7「明確的主代理唯讀工具允許清單」）。 |
| D5 | **Session 持久化** | 無 | Roadmap 明確指定「MVP 不持久化」。`AgentSession` 的 claude process 結束即銷毀。 |
| D6 | **跨模組依賴** | `allowedDependencies = {}` 不變 | agent-client 是 library dependency，不觸發 Modulith 跨模組違規。S007 不引用 core、sandbox、cli 模組的型別。 |
| D7 | **Timeout** | 硬編碼 30 分鐘 | XS 不加配置屬性。未來可擴充為 `grimo.chat.timeout`。 |

### 2.2 Challenges Considered

- **「為何不用 `AgentClient.run()` 更簡單？」** `AgentClient.run()` 每次呼叫建立並銷毀一個 claude process，對話間無上下文保持。使用者要求「chat 對話」需要多輪連續性，`AgentSession` 是 agent-client 設計的多輪 API。
- **「為何不用容器化 claude？」** 容器化涉及 Docker 容器管理、macOS Keychain credential 提取（`CredentialResolver`）、wrapper script env var 注入（`CLAUDE_CODE_OAUTH_TOKEN` 等）、`SandboxConfig` 擴充。使用者要求 MVP 先驗證功能，容器化延後到下一個 spec。
- **「主機 claude 不在容器中，安全嗎？」** S007 是 MVP 功能驗證。主機 claude 使用預設 permission mode，使用者直接控制所有操作。容器隔離是後續 spec 的範疇。
- **「`System.out.println` 違反 dev-standards §3 嗎？」** §3 的「絕不使用 System.out」針對診斷日誌（應用 SLF4J）。CLI REPL 的使用者輸出是 UI 層級輸出，`System.out` 是 CLI adapter 的正當輸出通道。
- **「`Path.of("").toAbsolutePath()` 使用 `System.getProperty("user.dir")` 嗎？」** 間接使用。但 dev-standards §11 的禁令針對 Grimo 配置屬性的 ad-hoc 存取，CWD 是 JVM 執行期屬性，且呼叫發生在 adapter 層（系統邊界）。可接受。

### 2.3 Research Citations (load-bearing)

**agent-client 0.12.2 — ClaudeAgentSessionRegistry：**
- `ClaudeAgentSessionRegistry.builder()` 接受 `.timeout(Duration)`、`.claudePath(String)`、`.defaultOptions(ClaudeAgentOptions)`、`.hookRegistry(HookRegistry)`：[GitHub](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-claude/src/main/java/org/springaicommunity/agents/claude/ClaudeAgentSessionRegistry.java)
- `claudePath(String)` = null → auto-discover via `ClaudeCliDiscovery`。可設定自訂路徑（未來容器化 wrapper script 可用）。
- `registry.create(Path workDir)` 啟動 claude CLI process 並回傳 `AgentSession`。若 CLI 未找到或啟動失敗，拋 `IllegalStateException`。

**agent-model 0.12.2 — AgentSession：**
- `AgentSession` extends `AutoCloseable`。`prompt(String)` blocking multi-turn call，在同一 process 中續接：[GitHub](https://github.com/spring-ai-community/agent-client/blob/main/agent-model/src/main/java/org/springaicommunity/agents/model/AgentSession.java)
- `close()` 終止底層 claude process，狀態轉為 `DEAD`
- `getSessionId()` 建立後立即可用
- `getStatus()` 回傳 `ACTIVE | DEAD | RESUMED`
- 若 process 中途死亡，`prompt()` 轉為 `DEAD` 並拋例外；可用 `resume()` 恢復（以 `--resume <sessionId>` 重啟）

**agent-model 0.12.2 — AgentResponse：**
- `getText()` 主要回應文字（REPL 輸出）
- `getMetadata()` 含 `sessionId`、`duration`、token counts（`inputTokens`、`outputTokens`）
- `isSuccessful()` 當 finishReason 為 `"SUCCESS"` 或 `"COMPLETE"` 時 true

**AgentClient vs AgentSession — 多輪對話：**
- `AgentClient.run(goal)` 每次建立/銷毀 process，無狀態。`AgentTaskRequest` 無 `sessionId` 欄位，無法鏈接 session。
- 多輪對話必須使用 `AgentSession`，不能透過 `AgentClient.run()` 實現。

**agent-client 0.12.2 — 無互動終端支援：**
- 整個 agent-client 生態系零互動終端支援。`AgentModel`、`AgentClient`、`StreamingTransport` 皆為程式化 prompt-in / JSON-out。
- `Sandbox.startInteractive(ExecSpec)` 存在但為 stub（拋 `UnsupportedOperationException`），無具體實作。
- 確認：S007 使用 `AgentSession`（SDK 設計的多輪 API），不做 terminal passthrough。

**claude-code interactive mode（供未來參考）：**
- `claude` 無 `-p` flag 進入 full TUI REPL；退出：`/exit`、`/quit`、`Ctrl+D`
- `--dangerously-skip-permissions` 在互動模式可用，官方建議用於容器環境
- `--no-session-persistence` 僅限 print mode（`-p`），不適用互動模式

## 3. SBE Acceptance Criteria

**Acceptance-verification command:**

```
./gradlew test
```

Pass condition: all JUnit tests with `@DisplayName` beginning `[S007] AC-<N>` are green. Real CLI IT 以 `assumeTrue(claudeAvailable())` 控制，無 claude 時跳過。

**AC naming contract:** `S007-AC-<N>`

### AC-1: grimo chat 啟動互動 session 並回應

```
Given  主機已安裝 claude CLI 且已認證（claude login）
When   ./gradlew bootRun --args='chat' 啟動
And    使用者輸入 "hello"
Then   收到 claude 的非空回應文字
And    可繼續輸入第二個 prompt 並收到參考先前對話的回應（多輪驗證）
```

### AC-2: /exit 或 Ctrl+D 乾淨退出

```
Given  grimo chat session 正在運行
When   使用者輸入 "/exit"（或按 Ctrl+D 送出 EOF）
Then   AgentSession 乾淨關閉（claude process 終止）
And    程式以 exit code 0 回到主機 shell
And    無堆疊追蹤輸出
```

### AC-3: claude CLI 未安裝時清楚錯誤

```
Given  主機未安裝 claude CLI（PATH 中找不到 claude binary）
When   ./gradlew bootRun --args='chat' 啟動
Then   印出使用者友善訊息（含安裝指引）
And    以非零 exit code 退出
And    不顯示原始 Java 堆疊追蹤
```

## 4. Interface / API Design

### 4.1 Package Layout

```
io.github.samzhu.grimo.agent
├── package-info.java                          # @ApplicationModule(allowedDependencies={}) — 不變
├── domain/
│   └── ChatSessionException.java              # 領域例外 — CLI 未安裝或 session 啟動失敗
├── application/
│   ├── port/in/
│   │   └── MainAgentChatUseCase.java          # interface — 啟動 chat session
│   └── service/
│       └── MainAgentChatService.java          # @Service — REPL loop 實作
├── adapter/
│   └── in/
│       └── cli/
│           └── ChatCommandRunner.java         # @Component ApplicationRunner — 偵測 "chat" 子命令
└── internal/
    └── AgentModuleConfig.java                 # @Configuration — ClaudeAgentSessionRegistry bean
```

### 4.2 `MainAgentChatUseCase` — 用例介面

```java
package io.github.samzhu.grimo.agent.application.port.in;

import java.nio.file.Path;

/**
 * 啟動主代理互動對話 session。阻塞至使用者退出（/exit 或 Ctrl+D）。
 */
public interface MainAgentChatUseCase {

    /**
     * @param workingDirectory 使用者的工作目錄（claude 的 project root）
     * @throws io.github.samzhu.grimo.agent.domain.ChatSessionException
     *         若 claude CLI 未安裝或 session 啟動失敗
     */
    void startChat(Path workingDirectory);
}
```

### 4.3 `MainAgentChatService` — 用例實作（核心 REPL）

```java
package io.github.samzhu.grimo.agent.application.service;

@Service
class MainAgentChatService implements MainAgentChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(MainAgentChatService.class);
    private final ClaudeAgentSessionRegistry sessionRegistry;

    MainAgentChatService(ClaudeAgentSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void startChat(Path workingDirectory) {
        AgentSession session;
        try {
            session = sessionRegistry.create(workingDirectory);
        } catch (IllegalStateException e) {
            throw new ChatSessionException(
                "Claude CLI not found. Install: npm install -g @anthropic-ai/claude-code", e);
        }

        log.info("Chat session started (sessionId={})", session.getSessionId());

        try (session) {
            var reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while (true) {
                System.out.print("you> ");
                System.out.flush();
                line = reader.readLine();
                if (line == null) break;                          // Ctrl+D (EOF)
                if ("/exit".equals(line.trim())
                        || "/quit".equals(line.trim())) break;
                if (line.isBlank()) continue;

                try {
                    AgentResponse response = session.prompt(line);
                    System.out.println(response.getText());
                } catch (Exception e) {
                    log.error("Session error", e);
                    System.err.println("Session terminated: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
```

### 4.4 `ChatCommandRunner` — CLI 入站適配器

```java
package io.github.samzhu.grimo.agent.adapter.in.cli;

@Component
class ChatCommandRunner implements ApplicationRunner {

    private final MainAgentChatUseCase chatUseCase;

    ChatCommandRunner(MainAgentChatUseCase chatUseCase) {
        this.chatUseCase = chatUseCase;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("chat")) {
            return;  // 非 chat 模式，不做任何事
        }
        try {
            chatUseCase.startChat(Path.of("").toAbsolutePath());
        } catch (ChatSessionException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
```

### 4.5 `AgentModuleConfig` — bean 配置

```java
package io.github.samzhu.grimo.agent.internal;

@Configuration
class AgentModuleConfig {

    @Bean
    ClaudeAgentSessionRegistry claudeSessionRegistry() {
        return ClaudeAgentSessionRegistry.builder()
                .timeout(Duration.ofMinutes(30))
                .build();
    }
}
```

### 4.6 `ChatSessionException` — 領域例外

```java
package io.github.samzhu.grimo.agent.domain;

public class ChatSessionException extends RuntimeException {
    public ChatSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 4.7 跨模組通訊

無。agent 模組 `allowedDependencies = {}` 不變。所有 import 來自 library dependency（agent-client、Spring Boot），不觸發 Modulith 跨模組檢查。

## 5. File Plan

### New production files

All under `src/main/java/io/github/samzhu/grimo/agent/`:

| File | Description |
|------|-------------|
| `domain/ChatSessionException.java` | 領域例外 — CLI 未安裝或 session 啟動失敗 |
| `application/port/in/MainAgentChatUseCase.java` | interface — `startChat(Path)` |
| `application/service/MainAgentChatService.java` | `@Service` — REPL loop，使用 `ClaudeAgentSessionRegistry` + `AgentSession.prompt()` |
| `adapter/in/cli/ChatCommandRunner.java` | `@Component ApplicationRunner` — 偵測 "chat" 子命令 |
| `internal/AgentModuleConfig.java` | `@Configuration` — `ClaudeAgentSessionRegistry` bean（timeout 30m） |

### New test files

| File | Description |
|------|-------------|
| `src/test/java/.../agent/adapter/in/cli/ChatCommandRunnerTest.java` | T0 unit：驗證 "chat" 子命令偵測 + CLI 未安裝錯誤處理 |
| `src/test/java/.../agent/application/service/MainAgentChatServiceTest.java` | T0 unit：mock `ClaudeAgentSessionRegistry`，驗證 session 建立/關閉 + CLI-not-found exception |
| `src/test/java/.../agent/MainAgentChatIT.java` | T3 IT：真實 claude binary，`assumeTrue(claudeAvailable())`；驗證 "hello" → 非空回應 |

### Modified files

| File | Action | Description |
|------|--------|-------------|
| `docs/grimo/specs/spec-roadmap.md` | modify | S007 status → `⏳ Design`，描述修訂為主機 claude + AgentSession，估算 XS (7) |

### Not touched

- `agent/package-info.java` — `allowedDependencies = {}` 不變
- `build.gradle.kts` — `agent-claude:0.12.2` 已宣告，無需新增
- `GrimoApplication.java` — 不修改
- `cli/` 模組 — 不修改（S007 不使用 ContainerizedAgentModelFactory）
- `sandbox/` 模組 — 不修改（S007 不使用 Docker）
- `core/` 模組 — 不修改（S007 不引用 core 型別）

---

<!-- Sections 6-7 added by /planning-tasks after implementation -->
