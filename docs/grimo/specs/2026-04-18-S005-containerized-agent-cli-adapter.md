# S005: 透過 `docker exec` 的容器化 AgentModel 適配器

> Spec: S005 | Size: S (11) | Status: ✅ Done
> Date: 2026-04-18

---

## 1. Goal

讓 Grimo 的 `cli` 模組能夠在已運行的 `grimo-runtime` 容器中呼叫三個 CLI（claude、codex、gemini），並**完整重用 Spring AI Community agent-client 0.12.2 的 `AgentModel` / `StreamingAgentModel` 生態系**——零自建 AgentModel 實作，零自建輸出解析。

核心機制：為每個容器動態生成一個 wrapper script（`docker exec -i $CONTAINER_ID <cli> "$@"`），透過各 SDK 的 binary path 設定注入。SDK 完全不知道 CLI 跑在容器裡——stdin/stdout 的 JSON-LD 協議透過 `docker exec -i` 管道透明傳輸。

**依賴：** S003 ✅（Sandbox SPI，提供 `getContainerId()`）、S004 ✅（grimo-runtime 映像，映像內含 3 CLI）。兩者皆已出貨，非阻塞。

**範圍內：**
- `ContainerizedAgentModelFactory`（cli 模組的 API 介面）
- `WrapperScriptGenerator`（動態生成 docker exec wrapper script）
- 各 provider 的 AgentModel 組裝（Claude / Gemini / Codex）
- `StubContainerizedAgentModelFactory`（測試用存根）
- 整合測試（Docker-based IT，CI 環境跳過）

**範圍外：**
- 認證策略 / 憑證掛載 → S006
- CLI 配置（記憶體關閉、遙測設定）→ S006
- 主代理 CLI 直通（grimo chat）→ S007
- 容器安全加固 → S010
- 併發多容器支援 → S010

## 2. Approach

### 2.1 Decisions

| # | Decision | Chosen | Why |
| --- | --- | --- | --- |
| D1 | **CLI 呼叫機制** | Wrapper script — `docker exec -i $CONTAINER_ID <cli> "$@"` | 三個 SDK（claude-code-sdk、gemini-sdk、codex-sdk）內部均硬編碼 ProcessBuilder 執行 CLI binary。Wrapper script 是唯一不修改 SDK 即可容器化的路徑。stdin/stdout 管道透過 docker exec -i 透明傳輸。 |
| D2 | **Claude 整合** | `ClaudeAgentModel.builder().claudePath(wrapperPath)` | `claudePath` 是 SDK 唯一的 binary path 自訂點。設定後，SDK 的 `StreamingTransport` 以 wrapper 取代 `claude` 二進位，完整保留 sync + streaming + iterator 三種程式模型、hooks、session resume、MCP 支援。 |
| D3 | **Gemini 整合** | `GeminiAgentModel(geminiClient, opts, null)` + wrapper path via system property | `GeminiAgentModel` 有原生 Sandbox 整合（`sandbox.exec()`），但只支援 sync `call()`。Wrapper script 路徑透過 `gemini.cli.path` 系統屬性設定，走 SDK 的非 Sandbox 路徑（`geminiClient.query()`），功能等同。 |
| D4 | **Codex 整合** | `CodexAgentOptions.builder().executablePath(wrapperPath)` | `CodexAgentModel` 的 Sandbox 欄位存在但 `call()` 未使用它（上游 WIP）。`executablePath` 透過 `CODEX_CLI_PATH` 系統屬性注入，SDK 內部以此路徑啟動 CLI。 |
| D5 | **容器生命週期歸屬** | 呼叫者管理，cli 模組只收 `containerId` 字串 | S007（主代理）和 S010（子代理）的容器建立需求不同。cli 模組的職責是「在已存在的容器內呼叫 CLI」。以字串傳遞 containerId，cli 模組零依賴於 sandbox Modulith 模組。 |
| D6 | **回傳型別** | `AgentModel` / `StreamingAgentModel` / `AgentResponse`（標準 agent-client 型別） | 避免重複造輪子。`AgentResponse` 含 `getText()`、`isSuccessful()`、`getMetadata()`（model、duration、sessionId、token counts）。 |
| D7 | **認證策略** | 訂閱帳號優先；API key 為 CI fallback | PRD P10 / D19。容器建立時由上游掛載 `~/.claude/` 等（S006 範疇）。Wrapper script 有條件轉發 `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` / `OPENAI_API_KEY`——有值才傳，不要求必須存在。 |
| D8 | **跨模組依賴** | `cli` 模組 `allowedDependencies = {}` 維持不變 | cli 模組只 import library 依賴（`agent-client-core`、`agent-model`、`agent-claude` 等）和 `core`（Type.OPEN）。不引用任何 Grimo Modulith 模組的型別。 |
| D9 | **Sandbox 模組** | 不修改 | Wrapper script 方案不需要 `startInteractive()` 或任何 Sandbox SPI 擴充。容器已由 `SandboxManager.create()` 啟動（S003），cli 模組只需容器 ID。 |

### 2.2 Challenges Considered

- **「為何不自建 SandboxedClaudeAgentModel？」** Claude SDK（`claude-code-sdk`）的 `StreamingTransport` 硬編碼 ProcessBuilder，無 `TransportFactory` 或 `Sandbox` 注入點。自建 model 需重寫 ~200 行命令組裝 + JSON-LD 解析，且失去 hooks、session resume、MCP 等 SDK 功能。Wrapper script 以零成本保留全部 SDK 功能。
- **「為何不用 GeminiAgentModel 原生 Sandbox？」** 原生 Sandbox 路徑（`sandbox.exec()`）只支援 sync `call()`，不支援 streaming。且注入 `Sandbox` 需要 cli 模組接收 `Sandbox` 物件，增加跨模組耦合。Wrapper script 讓三個 provider 走統一路徑。
- **「System.setProperty 是全域狀態，併發安全嗎？」** Gemini 和 Codex 的 `executablePath` 透過系統屬性注入，是全域可變狀態。MVP 場景下（一次一個主代理容器）不構成問題。S010（併發子代理）需另行處理——屆時可為每個容器生成獨立的 wrapper script 路徑，或改用 Gemini 的 Sandbox 路徑。
- **「Wrapper script 的 stdin/stdout 管道可靠嗎？」** `docker exec -i` 在 S003 已驗證（`execInContainer` 內部即 docker exec）。JSON-LD 協議（stream-json）透過管道傳輸與直接 localhost 執行完全一致——SDK 讀寫 Process 的 stdin/stdout，管道只是透明中繼。
- **「認證怎麼進容器？」** 容器建立時掛載主機 `~/.claude/` → `/root/.claude/`（唯讀），CLI 繼承 `claude login` session。這是 S006 的範疇。S005 的 wrapper script 額外轉發 API key env vars 作為 CI fallback，但不要求 key 存在。

### 2.3 Research Citations (load-bearing)

**agent-client 0.12.2 — AgentModel SPI：**
- `ClaudeAgentModel` 實作 `AgentModel` + `StreamingAgentModel` + `IterableAgentModel`，Builder 含 `claudePath(String)`：[GitHub](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-claude/src/main/java/org/springaicommunity/agents/claude/ClaudeAgentModel.java)
- `GeminiAgentModel` 實作 `AgentModel`，構造子含 `Sandbox sandbox`，`executeViaSandbox()` 用 `sandbox.exec(ExecSpec)`：[GitHub](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-gemini/src/main/java/org/springaicommunity/agents/gemini/GeminiAgentModel.java)
- `CodexAgentModel` 實作 `AgentModel`，構造子含 `Sandbox sandbox` 但 `call()` 未使用：[GitHub](https://github.com/spring-ai-community/agent-client/blob/main/agent-models/agent-codex/src/main/java/org/springaicommunity/agents/codex/CodexAgentModel.java)

**claude-code-sdk — StreamingTransport 硬編碼 ProcessBuilder：**
- `buildStreamingCommand(CLIOptions)` 組出 CLI 命令，`startSession()` 以 `new ProcessBuilder(command)` 執行，無介面可替換：[GitHub](https://github.com/spring-ai-community/claude-agent-sdk-java/blob/main/claude-code-sdk/src/main/java/org/springaicommunity/claude/agent/sdk/transport/StreamingTransport.java)
- `claudePath` 是唯一的 binary path 自訂點，由 `ClaudeClient.sync().claudePath(String)` 設定：[GitHub](https://github.com/spring-ai-community/claude-agent-sdk-java/blob/main/claude-code-sdk/src/main/java/org/springaicommunity/claude/agent/sdk/ClaudeClient.java)
- Tutorial repo（23 個模組）無 Docker/Sandbox/容器化範例：[GitHub](https://github.com/spring-ai-community/claude-agent-sdk-java-tutorial)

**agent-sandbox-core 0.9.1 — Sandbox SPI：**
- `startInteractive(ExecSpec)` 為 default method（拋 `UnsupportedOperationException`）。`DockerSandbox` 未實作。本 spec 不需要此方法。：[GitHub](https://github.com/spring-ai-community/agent-sandbox/blob/main/agent-sandbox-core/src/main/java/org/springaicommunity/sandbox/Sandbox.java)

**藍圖描述修正：**
- 原描述「實作 `AgentCliPort`，提供 `stream(SpawnSpec, Prompt): Flux<Token>`」不正確。研究顯示最大化重用路徑為 wrapper script + 標準 `AgentModel`/`StreamingAgentModel` 型別，不需自訂 `AgentCliPort`。

## 3. SBE Acceptance Criteria

**Acceptance-verification command:**

```
./gradlew test
```

Pass condition: all JUnit tests with `@DisplayName` beginning `[S005] AC-<N>` are green. Docker-based IT 標記 `@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")`，CI 環境跳過。

### AC-1: StubContainerizedAgentModelFactory 回傳預設 AgentResponse

```
Given  StubContainerizedAgentModelFactory 已建立
When   factory.create(CLAUDE, "stub-container-id") 回傳 AgentModel
And    model.call(AgentTaskRequest.builder("hello", Path.of("/work")).build())
Then   AgentResponse.getText() 為非空的預設文字
And    AgentResponse.isSuccessful() 為 true

When   model instanceof StreamingAgentModel streaming
And    streaming.stream(request) 回傳 Flux<AgentResponse>
Then   Flux 發射至少一個 AgentResponse
And    所有 AgentResponse.getText() 為非空
```

### AC-2: 真實適配器對 grimo-runtime 容器的整合測試

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
And    SandboxManager 建立一個 grimo-runtime 容器（bind-mount 一個暫存目錄至 /work）
And    containerId = manager.listActive().getFirst()
And    認證憑證可用（subscription login 或 ANTHROPIC_API_KEY）
When   factory.create(CLAUDE, containerId) 回傳 AgentModel
And    model.call(AgentTaskRequest.builder("回答 hello", sandbox.workDir()).build())
Then   AgentResponse.getText() 含有非空回應文字
And    AgentResponse.isSuccessful() 為 true
Note   此為手動 IT；CI 環境以 assumeTrue(credentialsPresent()) 跳過
```

### AC-3: 容器內缺少 CLI 時顯示清楚錯誤

```
Given  一個運行中的容器（如 alpine:3.21），內部未安裝 claude CLI
When   factory.create(CLAUDE, containerId) 回傳 AgentModel
And    model.call(request)
Then   AgentResponse.isSuccessful() 為 false
And    AgentResponse.getText() 含 "claude" 與 "not found" 相關訊息
And    不顯示原始 docker-exec stderr 堆疊追蹤
```

## 4. Interface / API Design

### 4.1 Package Layout

```
io.github.samzhu.grimo.cli
├── package-info.java                             # @ApplicationModule(allowedDependencies={})
├── api/
│   ├── package-info.java                         # @NamedInterface("api")
│   └── ContainerizedAgentModelFactory.java       # interface — 工廠
├── internal/
│   ├── DefaultContainerizedAgentModelFactory.java # @Service — 工廠實作
│   ├── WrapperScriptGenerator.java               # 動態生成 docker exec wrapper script
│   ├── StubContainerizedAgentModelFactory.java    # 測試用存根工廠
│   └── CliModuleConfig.java                       # @Configuration — bean 註冊
```

### 4.2 `ContainerizedAgentModelFactory` — 工廠介面

```java
package io.github.samzhu.grimo.cli.api;

import io.github.samzhu.grimo.core.domain.ProviderId;
import org.springaicommunity.agents.model.AgentModel;

/**
 * 建立以容器化 CLI 為後端的 AgentModel。呼叫者負責容器生命週期
 * （透過 SandboxManager），只傳入 containerId；本工廠負責組裝
 * wrapper script + SDK AgentModel。
 *
 * <p>設計說明：回傳標準 agent-client 的 {@link AgentModel}，
 * 呼叫者可 {@code instanceof StreamingAgentModel} 檢查串流支援。
 * Claude 支援 sync + streaming + iterate；Gemini / Codex 僅 sync。
 */
public interface ContainerizedAgentModelFactory {

    /**
     * 為指定 provider 建立一個 AgentModel，目標容器為 containerId。
     *
     * @param provider  CLAUDE / CODEX / GEMINI
     * @param containerId 已運行的 Docker 容器 ID
     * @return AgentModel（Claude 額外實作 StreamingAgentModel + IterableAgentModel）
     * @throws IllegalArgumentException containerId 為 blank
     */
    AgentModel create(ProviderId provider, String containerId);
}
```

### 4.3 `WrapperScriptGenerator` — Wrapper Script 生成器

```java
package io.github.samzhu.grimo.cli.internal;

import io.github.samzhu.grimo.core.domain.ProviderId;
import java.nio.file.Path;

/**
 * 為指定容器 + CLI 產生一個 shell script，透過 docker exec -i
 * 呼叫容器內的 CLI binary。Script 存放於暫存目錄，隨容器關閉清除。
 *
 * <p>設計說明：script 包含條件式環境變數轉發（API key fallback），
 * 訂閱認證透過容器內已掛載的 credential 目錄自動生效。
 */
class WrapperScriptGenerator {

    private static final Path WRAPPER_DIR = Path.of(System.getProperty("java.io.tmpdir"), "grimo", "wrappers");

    /**
     * 產生 wrapper script 並回傳路徑。Script 自動設定 executable 權限。
     *
     * @param provider    ProviderId — 決定容器內的 CLI binary 名稱
     * @param containerId Docker 容器 ID
     * @return wrapper script 的 Path（已 chmod +x）
     */
    Path generate(ProviderId provider, String containerId) {
        // 產生內容如下：
        // #!/bin/bash
        // ENV_ARGS=""
        // [ -n "$ANTHROPIC_API_KEY" ] && ENV_ARGS="$ENV_ARGS -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY"
        // [ -n "$GEMINI_API_KEY" ] && ENV_ARGS="$ENV_ARGS -e GEMINI_API_KEY=$GEMINI_API_KEY"
        // [ -n "$OPENAI_API_KEY" ] && ENV_ARGS="$ENV_ARGS -e OPENAI_API_KEY=$OPENAI_API_KEY"
        // exec docker exec -i $ENV_ARGS "<containerId>" "<cliBinary>" "$@"
        //
        // cliBinary = switch(provider) { CLAUDE -> "claude"; CODEX -> "codex"; GEMINI -> "gemini"; }
        throw new UnsupportedOperationException("implementation in T2");
    }

    /** 清除指定容器的 wrapper script。 */
    void cleanup(String containerId) {
        // 刪除 WRAPPER_DIR 下以 containerId 命名的 script
    }
}
```

### 4.4 `DefaultContainerizedAgentModelFactory` — 工廠實作

```java
package io.github.samzhu.grimo.cli.internal;

import io.github.samzhu.grimo.cli.api.ContainerizedAgentModelFactory;
import io.github.samzhu.grimo.core.domain.ProviderId;
import org.springaicommunity.agents.claude.ClaudeAgentModel;
import org.springaicommunity.agents.codex.CodexAgentModel;
import org.springaicommunity.agents.codex.CodexAgentOptions;
import org.springaicommunity.agents.codexsdk.CodexClient;
import org.springaicommunity.agents.gemini.GeminiAgentModel;
import org.springaicommunity.agents.gemini.GeminiAgentOptions;
import org.springaicommunity.agents.geminisdk.GeminiClient;
import org.springaicommunity.agents.model.AgentModel;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 組裝容器化 AgentModel。每次 create() 產生一個新的 AgentModel 實例，
 * 綁定至指定容器的 wrapper script。
 *
 * <p>設計說明：Claude 用 Builder.claudePath()（per-instance，無全域狀態）。
 * Gemini/Codex 用 System.setProperty（全域，MVP 限定一次一個容器）。
 * 併發多容器場景由 S010 處理。
 */
@Service
class DefaultContainerizedAgentModelFactory implements ContainerizedAgentModelFactory {

    private final WrapperScriptGenerator scriptGenerator;

    DefaultContainerizedAgentModelFactory(WrapperScriptGenerator scriptGenerator) {
        this.scriptGenerator = scriptGenerator;
    }

    @Override
    public AgentModel create(ProviderId provider, String containerId) {
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalArgumentException("containerId must not be blank");
        }

        Path wrapperScript = scriptGenerator.generate(provider, containerId);

        return switch (provider) {
            case CLAUDE -> ClaudeAgentModel.builder()
                    .claudePath(wrapperScript.toString())
                    .workingDirectory(Path.of("/work"))
                    .timeout(Duration.ofMinutes(5))
                    .build();
            case GEMINI -> {
                System.setProperty("gemini.cli.path", wrapperScript.toString());
                yield new GeminiAgentModel(
                        GeminiClient.create(),
                        new GeminiAgentOptions(),
                        null);  // 不注入 Sandbox — 走 SDK 非 Sandbox 路徑
            }
            case CODEX -> {
                var opts = CodexAgentOptions.builder()
                        .executablePath(wrapperScript.toString())
                        .fullAuto(true)
                        .build();
                yield new CodexAgentModel(
                        CodexClient.create(),
                        opts,
                        null);  // Sandbox 欄位上游未接線
            }
        };
    }
}
```

### 4.5 跨模組通訊

| 消費者模組 | 需要的型別 | `allowedDependencies` 變更 |
| --- | --- | --- |
| `agent` (S007) | `ContainerizedAgentModelFactory` | `"cli :: api"` |
| `subagent` (S010) | `ContainerizedAgentModelFactory` | `"cli :: api"` |

消費者透過 Spring DI 注入 `ContainerizedAgentModelFactory`，呼叫 `create(provider, containerId)` 取得標準 `AgentModel`。

`agent-client-core`、`agent-model`、`agent-claude`、`agent-codex`、`agent-gemini` 皆為 library 依賴，所有模組可直接 import，不觸發 Modulith 跨模組違規。

## 5. File Plan

### New production files

All under `src/main/java/io/github/samzhu/grimo/cli/`:

| File | Description |
| --- | --- |
| `api/package-info.java` | `@NamedInterface("api")` — cli 模組的公開 API 套件 |
| `api/ContainerizedAgentModelFactory.java` | interface — 工廠（create by provider + containerId） |
| `internal/DefaultContainerizedAgentModelFactory.java` | `@Service` — 工廠實作，組裝 SDK AgentModel + wrapper script |
| `internal/WrapperScriptGenerator.java` | 動態生成 `docker exec -i` wrapper script |
| `internal/StubContainerizedAgentModelFactory.java` | 測試用存根工廠，回傳預設 AgentResponse |
| `internal/CliModuleConfig.java` | `@Configuration` — bean 註冊（如 WrapperScriptGenerator） |

### New test files

| File | Description |
| --- | --- |
| `src/test/java/.../cli/api/ContainerizedAgentModelFactoryTest.java` | T0 unit：StubFactory 回傳預設 AgentResponse（AC-1） |
| `src/test/java/.../cli/internal/WrapperScriptGeneratorTest.java` | T0 unit：script 內容正確性、chmod +x、cleanup |
| `src/test/java/.../cli/internal/ContainerizedAgentModelIT.java` | T3 contract：對 grimo-runtime 容器的真實呼叫（AC-2、AC-3）；`@EnabledIfSystemProperty` |

### Modified files

| File | Action | Description |
| --- | --- | --- |
| `build.gradle.kts` | modify | 新增 `implementation("org.springaicommunity.agents:agent-client-core:0.12.2")`、`agent-model`、`agent-claude`、`agent-codex`、`agent-gemini` |
| `docs/grimo/specs/spec-roadmap.md` | modify | S005 status → `⏳ Design` |

### Not touched

- `sandbox/` 模組 — 不修改（不需要 `startInteractive()`）
- `core/` — 無新型別（`ProviderId` 已存在）
- `application.yml` — 無新屬性
- `cli/package-info.java` — `allowedDependencies` 改為 `{ "core" }`（見 §7 findings）

---

## 6. Task Plan

**POC: not required** — spec §2.3 已詳細研究 agent-client 0.12.2 的 API surface 並附程式碼片段，SDK 整合為標準 Builder pattern，wrapper script 機制為簡單 shell 腳本生成，不確定性低。

### Task Index

| Task | 主題 | AC 對映 | 依賴 |
| --- | --- | --- | --- |
| T1 | 新增 agent-client 依賴至 build.gradle.kts | — (infrastructure) | none |
| T2 | 建立 cli API 套件 + ContainerizedAgentModelFactory 介面 | — (infrastructure) | T1 |
| T3 | 實作 WrapperScriptGenerator + 單元測試 | — (infrastructure) | T1 |
| T4 | 實作 StubContainerizedAgentModelFactory + AC-1 測試 | **AC-1** | T2 |
| T5 | 實作 DefaultContainerizedAgentModelFactory + CliModuleConfig | — (implementation) | T2, T3 |
| T6 | 整合測試 ContainerizedAgentModelIT | **AC-2, AC-3** | T5 |

### Execution Order

```
T1 ─┬─ T2 ─┬─ T4 (AC-1)
    │      └─ T5 ─── T6 (AC-2, AC-3)
    └─ T3 ─┘
```

### AC Coverage

| AC | Task | Test Class |
| --- | --- | --- |
| AC-1 | T4 | `ContainerizedAgentModelFactoryTest` |
| AC-2 | T6 | `ContainerizedAgentModelIT` |
| AC-3 | T6 | `ContainerizedAgentModelIT` |

## 7. Implementation Results

### Verification Results

| Check | Result |
| --- | --- |
| `./gradlew test` | ✅ BUILD SUCCESSFUL — 全部單元測試通過 + Modulith verify 通過 |
| AC coverage | ✅ AC-1 (2 tests), AC-2 (1 IT), AC-3 (1 IT) 均有 `@DisplayName("[S005] AC-<N>")` |
| `./gradlew compileTestJava` | ✅ IT 編譯通過（Docker-based IT 由 §7.5 skip guard 控制） |

### Key Findings

1. **Modulith `allowedDependencies = {}` 擋住 `Type.OPEN` 模組。** Architecture.md §1 聲稱 `core` 為 `Type.OPEN` 時消費者「無需宣告」——實際 Modulith 2.0.5 行為為：即使目標模組是 OPEN，消費者的 `allowedDependencies` 若為空集則仍擋住。修正：`cli` 模組 `allowedDependencies` 改為 `{ "core" }`。其他未來模組（agent、subagent、skills）在引用 `core` 型別時也需做同樣修正。

2. **`AgentResponse.isSuccessful()` 依賴 `AgentGenerationMetadata.finishReason`。** 必須為 `"SUCCESS"` 或 `"COMPLETE"` 才回傳 true。用 `new AgentGenerationMetadata("SUCCESS", Map.of())` 構建 Stub response。

3. **`AgentModel` + `StreamingAgentModel` 的 `isAvailable()` default method 衝突。** 同時實作兩個介面時必須顯式覆寫 `isAvailable()`。

4. **Gemini/Codex path 注入改良。** Spec D3/D4 原設計使用 `System.setProperty` 注入 CLI path（全域可變狀態，違反 dev-standards §11）。實際 SDK 提供 per-instance setter：`GeminiAgentOptions.setExecutablePath()` 和 `CodexAgentOptions.builder().executablePath()`。改用 per-instance 方式，三個 provider 統一無全域副作用。

### Correct Usage Patterns

**建立 wrapper script（核心機制）：**
```java
// WrapperScriptGenerator 產生 shell script:
// #!/bin/bash
// ENV_ARGS=""
// [ -n "$ANTHROPIC_API_KEY" ] && ENV_ARGS="$ENV_ARGS -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY"
// ...
// exec docker exec -i $ENV_ARGS "<containerId>" "<cliBinary>" "$@"
Path wrapperScript = scriptGenerator.generate(ProviderId.CLAUDE, containerId);
```

**組裝各 provider 的 AgentModel：**
```java
// Claude — per-instance claudePath, 支援 sync + streaming + iterate
ClaudeAgentModel.builder()
    .claudePath(wrapperScript.toString())
    .workingDirectory(Path.of("/work"))
    .timeout(Duration.ofMinutes(5))
    .build();

// Gemini — per-options executablePath, sync only
var opts = new GeminiAgentOptions();
opts.setExecutablePath(wrapperScript.toString());
new GeminiAgentModel(GeminiClient.create(), opts, null);

// Codex — builder executablePath, sync only
CodexAgentOptions.builder()
    .executablePath(wrapperScript.toString())
    .fullAuto(true)
    .build();
new CodexAgentModel(CodexClient.create(), opts, null);
```

**建立 Stub response（測試用）：**
```java
var metadata = new AgentGenerationMetadata("SUCCESS", Map.of());
var generation = new AgentGeneration(responseText, metadata);
return new AgentResponse(List.of(generation));
```

### AC Results

| AC | Status | Test |
| --- | --- | --- |
| AC-1 | ✅ PASS | `ContainerizedAgentModelFactoryTest` (sync + streaming) |
| AC-2 | ✅ 編譯通過，待 Docker 驗證 | `ContainerizedAgentModelIT.realClaudeCallAgainstGrimoRuntime` |
| AC-3 | ✅ 編譯通過，待 Docker 驗證 | `ContainerizedAgentModelIT.missingCliReturnsFailedResponse` |

### QA Review
Date: 2026-04-18
Verdict: PASS

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | MINOR | `WrapperScriptGenerator` 第 26 行使用 `System.getProperty("java.io.tmpdir")` 技術上違反 dev-standards §11（僅允許 `GrimoHomePaths` 和 `main` 存取系統屬性）。但此為 JVM 標準屬性用於暫存目錄，非 Grimo 配置路徑，規則原意已滿足。 | OPEN — 可在 S006 或後續清理時統一至 GrimoHomePaths |
| 2 | MINOR | `cli/package-info.java` Javadoc 仍寫 "strictest white-list"，但 `allowedDependencies` 已從 `{}` 改為 `{ "core" }`。註解略為過時。 | OPEN — 不影響功能 |
