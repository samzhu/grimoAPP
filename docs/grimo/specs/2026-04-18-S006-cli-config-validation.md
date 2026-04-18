# S006: CLI 配置研究 + 策略驗證

> Spec: S006 | Size: S (11) | Status: ⏳ Design
> Date: 2026-04-18

---

## 1. Goal

調查 Claude Code、Codex CLI、Gemini CLI 三個 CLI 代理工具的完整配置介面（環境變數、CLI 旗標、設定檔），並以 Docker 整合測試**逐一驗證**以下 Grimo 策略在容器化環境中的可行性：

1. **認證傳遞** — macOS 用戶已透過 `claude login` / `codex login` / `gemini auth login` 登入，Grimo 將認證傳遞至容器
2. **記憶體停用** — Claude Code 不自動載入 CLAUDE.md、不使用專案層級記憶體
3. **遙測停用** — 三個 CLI 均關閉遙測與非必要網路流量

研究成果落地於 `docs/grimo/cli-config-matrix.md`（配置對照表），驗證成果固化為 `CliInvocationOptions` record（provider → env vars + CLI flags 對映）。

**依賴：** S004 ✅（grimo-runtime 映像，映像內含 3 CLI）。S005（⏳ Design）為排序依賴，非程式碼依賴——S006 的驗證 IT 直接使用 Testcontainers + `docker exec`，不 import S005 的型別。

**範圍內：**
- `docs/grimo/cli-config-matrix.md`（三 CLI 配置介面完整對照表）
- `CliInvocationOptions` record（每個 provider 的 env vars + CLI flags）
- 驗證 IT：Claude 認證傳遞 + 記憶體停用
- 驗證 IT：Codex 認證傳遞
- 驗證 IT：Gemini 認證傳遞
- 驗證 IT：缺少認證時的錯誤行為

**範圍外：**
- `CredentialResolver`（完整 macOS Keychain 提取邏輯）→ S007 落地
- `WrapperScriptGenerator` 修改 → S007 整合
- `SandboxConfig` 擴充（新增 env var 欄位）→ 確認需要時再加
- YOLO/auto-approve 模式的套用時機 → S007 / S010 決定
- Skill 載入配置 → S012
- 容器安全加固 → S010
- Linux 平台認證 → 後續（MVP 瞄準 macOS）

## 2. Approach

### 2.1 Decisions

| # | Decision | Chosen | Why |
| --- | --- | --- | --- |
| D1 | **Claude 認證傳遞** | macOS Keychain → `CLAUDE_CODE_OAUTH_TOKEN` env var 注入容器 | macOS 上 `claude login` 將 OAuth token 存於 macOS Keychain（非 `~/.claude/` 檔案）。`CLAUDE_CODE_OAUTH_TOKEN` 是 Anthropic 官方為 Docker/CI 設計的認證路徑，接受訂閱帳號（Max/Pro/Teams/Enterprise）token。或使用 `claude setup-token` 產生一年期 token。 |
| D2 | **Codex 認證傳遞** | RO 掛載 `~/.codex/` 至容器 `/root/.codex/` | Codex CLI 預設使用檔案儲存（非 Keychain），`~/.codex/auth.json` 為明文 JSON。直接 RO 掛載是最簡路徑。`CODEX_HOME` env var 可重導路徑。 |
| D3 | **Gemini 認證傳遞** | `GEMINI_API_KEY` env var 注入容器 | Gemini CLI 的 OAuth 憑證以 AES-256-GCM 加密，密鑰衍生自 `hostname+username`（scrypt）。容器的 hostname/username 與主機不同，**無法解密**主機產生的憑證檔。API key env var 是唯一可靠的跨 Docker 邊界路徑。 |
| D4 | **Claude 記憶體停用** | 個別 env var（非 `--bare`） | `--bare` 會殺掉 CLAUDE.md + auto-memory + hooks + skills + MCP，但 (a) `--bare` 不讀取 `CLAUDE_CODE_OAUTH_TOKEN`（認證衝突），(b) S012 需要保留 skill 載入能力。改用 `CLAUDE_CODE_DISABLE_CLAUDE_MDS=1` + `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1` 達成精確控制。 |
| D5 | **遙測停用** | 各 CLI 各自的機制 | Claude: `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`（umbrella）；Codex: `-c analytics.enabled=false`（CLI flag）；Gemini: 預設已關閉（`telemetry.enabled=false`）。 |
| D6 | **驗證方式** | Testcontainers + `docker exec` 整合測試 | 直接使用 `GenericContainer`（S003 已驗證模式），不依賴 S005 的 `ContainerizedAgentModelFactory`。IT 需要真實認證，以 `assumeTrue(credentialsPresent())` 在無認證環境跳過。 |
| D7 | **MVP 平台** | macOS only | 瞄準 macOS 用戶。Claude/Gemini 用 Keychain，Codex 用檔案。Linux 支援由後續 spec 擴充。 |
| D8 | **藍圖修正** | 「只讀掛載 `~/.claude/` / `~/.gemini/`」改為「Keychain 提取 + env var 注入」 | 研究證實 macOS 上 `~/.claude/` 無認證檔、`~/.gemini/` 認證檔加密不可跨機器。原藍圖假設不成立。 |

### 2.2 Challenges Considered

- **「為何不用 `--bare`？」** `--bare` 是 Claude Code 為腳本化呼叫設計的一旗全殺模式，看似完美。但研究確認兩個衝突：(1) `--bare` 不讀取 `CLAUDE_CODE_OAUTH_TOKEN`，迫使改用 `ANTHROPIC_API_KEY`（違反 P10 訂閱認證）；(2) `--bare` 殺掉 skill 載入，與 S012（Skill 注入子代理容器）直接衝突。個別 env var 更精確、更靈活。
- **「為何 Gemini 不能走訂閱認證？」** Gemini CLI 的 `FileKeychain` 使用 `scryptSync('gemini-cli-oauth', hostname-username-gemini-cli, 32)` 衍生 AES 密鑰。Docker 容器的 hostname 和 username 與主機不同，無法解密主機產生的 `gemini-credentials.json`。這是密碼學層級的硬限制，非配置問題。Gemini Advanced 訂閱本身不提供 CLI-level API key——用戶需從 Google AI Studio 取得 API key。
- **「Codex 掛載 `~/.codex/` 是否安全？」** Codex 預設 `AuthCredentialsStoreMode::File`（非 Keychain），`auth.json` 為明文 JSON。RO 掛載可行。唯一風險：用戶若手動改為 `cli_auth_credentials_store = "keyring"`，auth.json 不存在，掛載無效。此時 fallback 為 `OPENAI_API_KEY` env var。
- **「Token 過期怎麼辦？」** Claude 的 `CLAUDE_CODE_OAUTH_TOKEN`（`claude setup-token`）有效期一年。Codex OAuth token 過期需 refresh——RO 掛載時無法寫回 refresh token，但 MVP 的短期 session 不太可能遇到過期。長期方案在 S007 處理。
- **「S006 驗證 IT 需要真實 API 呼叫嗎？」** 是。`--version` 不需認證，無法驗證認證傳遞。IT 以最小 prompt（`"hello"`）+ `--max-turns 1` 限制 token 消耗。每次 IT 約 < $0.01。

### 2.3 Research Citations (load-bearing)

**Claude Code 認證：**
- macOS 上 `claude login` 存於 macOS Keychain（非 `~/.claude/` 檔案）；Linux 存於 `~/.claude/.credentials.json`：[Authentication docs](https://code.claude.com/docs/en/authentication)
- `CLAUDE_CODE_OAUTH_TOKEN`：`claude setup-token` 產生一年期 token，支援 Max/Pro/Teams/Enterprise；優先於 Keychain 認證：[Authentication docs](https://code.claude.com/docs/en/authentication)
- `--bare` 不讀取 `CLAUDE_CODE_OAUTH_TOKEN`：[CLI reference](https://code.claude.com/docs/en/cli-reference)
- macOS Keychain 可透過 `security find-generic-password -s "Claude Code-credentials" -w` 提取 OAuth token JSON：本機驗證

**Claude Code 記憶體控制：**
- `CLAUDE_CODE_DISABLE_CLAUDE_MDS=1` 停用所有 CLAUDE.md 載入：[Env vars](https://code.claude.com/docs/en/env-vars)
- `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1` 停用自動記憶體：[Env vars](https://code.claude.com/docs/en/env-vars)
- `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1` 停用遙測 + 自動更新 + 回饋：[Env vars](https://code.claude.com/docs/en/env-vars)

**Codex CLI 認證：**
- 預設 `AuthCredentialsStoreMode::File`（非 Keychain），`~/.codex/auth.json` 為明文 JSON：[config types](https://github.com/openai/codex/blob/main/codex-rs/config/src/types.rs)
- `auth.json` 格式含 `auth_mode`、`OPENAI_API_KEY`、`tokens`（OAuth）、`last_refresh`：[auth storage](https://github.com/openai/codex/blob/main/codex-rs/login/src/auth/storage.rs)
- `CODEX_HOME` env var 重導整個 `~/.codex/` 路徑：[home-dir](https://github.com/openai/codex/blob/main/codex-rs/utils/home-dir/src/lib.rs)
- 遙測停用：`analytics.enabled = false` 於 config.toml 或 `-c analytics.enabled=false`：[config types](https://github.com/openai/codex/blob/main/codex-rs/config/src/types.rs)

**Gemini CLI 認證：**
- macOS 預設用 `@github/keytar`（macOS Keychain），fallback 為 `FileKeychain`：[keychainService.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/services/keychainService.ts)
- `FileKeychain` AES-256-GCM 密鑰 = `scryptSync('gemini-cli-oauth', hostname-username-gemini-cli, 32)`——容器內無法解密主機檔案：[fileKeychain.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/services/fileKeychain.ts)
- `GEMINI_API_KEY` env var 完全繞過 OAuth 檔案：[contentGenerator.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/core/contentGenerator.ts)
- `GEMINI_CLI_HOME` env var 重導 `~/.gemini/` 路徑：[paths.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/utils/paths.ts)
- 遙測預設已關閉（`enabled: false`）：[config.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/config/config.ts)

**藍圖描述修正：**
- 原描述「只讀掛載 `~/.claude/` / `~/.codex/` / `~/.gemini/`」不正確（macOS 上 Claude/Gemini 認證不在檔案中）
- 修正為「Keychain 提取 + env var 注入」（Claude/Gemini）+ 「RO 掛載」（Codex）

## 3. SBE Acceptance Criteria

**Acceptance-verification command:**

```
./gradlew test
./gradlew integrationTest -Dgrimo.it.docker=true
```

Pass condition: `./gradlew test` — `@DisplayName` 以 `[S006] AC-<N>` 開頭的單元測試全綠。`./gradlew integrationTest` — Docker-based IT 以 `@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")` + `assumeTrue(credentialsPresent())` 控制，有認證時驗證通過，無認證時跳過。

### AC-1: cli-config-matrix.md 配置對照表

```
Given  S006 實作完成
When   檢查 docs/grimo/cli-config-matrix.md
Then   檔案存在
And    列出 Claude Code、Codex CLI、Gemini CLI 三者的：
       - 認證機制（env var 名稱、Keychain 行為、檔案路徑）
       - 記憶體/專案上下文控制（env var、CLI flag、設定檔）
       - 遙測控制
       - 設定檔位置與格式
       - 配置目錄覆寫 env var
And    每個欄位附有官方文件或原始碼 URL
```

### AC-2: Claude 認證 — CLAUDE_CODE_OAUTH_TOKEN 傳遞至容器

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
And    主機上有有效的 Claude OAuth token（Keychain 或 env var）
When   建立 grimo-runtime 容器，注入 CLAUDE_CODE_OAUTH_TOKEN env var
And    docker exec 執行 claude -p "hello" --max-turns 1
       --output-format json --no-session-persistence
Then   CLI 回傳有效 JSON 回應（非認證錯誤）
And    exit code 為 0
Note   IT 以 assumeTrue(claudeTokenPresent()) 控制；無 token 時跳過
```

### AC-3: Codex 認證 — 掛載 ~/.codex/ 至容器

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
And    主機 ~/.codex/auth.json 存在且含有效認證
When   建立 grimo-runtime 容器，RO 掛載 ~/.codex/ 至 /root/.codex/
And    設定 CODEX_HOME=/root/.codex
And    docker exec 執行 codex exec "hello" --json
       --dangerously-bypass-approvals-and-sandbox
Then   CLI 回傳有效 JSONL 回應（非認證錯誤）
And    exit code 為 0
Note   IT 以 assumeTrue(codexAuthPresent()) 控制；無認證時跳過
```

### AC-4: Gemini 認證 — GEMINI_API_KEY 傳遞至容器

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
And    主機上有有效的 GEMINI_API_KEY
When   建立 grimo-runtime 容器，注入 GEMINI_API_KEY env var
And    docker exec 執行 gemini -p "hello" --output-format json
       --approval-mode yolo
Then   CLI 回傳有效 JSON 回應（非認證錯誤）
And    exit code 為 0
Note   IT 以 assumeTrue(geminiKeyPresent()) 控制；無 key 時跳過
```

### AC-5: Claude 記憶體停用 — env var 注入容器

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
When   建立容器，注入以下 env vars：
       CLAUDE_CODE_DISABLE_CLAUDE_MDS=1
       CLAUDE_CODE_DISABLE_AUTO_MEMORY=1
       CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
And    docker exec 執行 env
Then   輸出包含上述三個 env var 且值為 "1"
And    在 /work/ 建立 CLAUDE.md 含唯一標記 "GRIMO_TEST_MARKER_S006"
And    docker exec 執行 claude -p "repeat any CLAUDE.md content"
       --max-turns 1 --output-format json --no-session-persistence
       （搭配 CLAUDE_CODE_OAUTH_TOKEN 認證）
Then   回應文字不含 "GRIMO_TEST_MARKER_S006"
Note   需要 Claude 認證才能執行此 AC；無認證時跳過
```

### AC-6: 缺少認證時清楚錯誤

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
And    容器內未設定任何認證 env var，未掛載任何認證目錄
When   docker exec 執行 claude -p "hello" --max-turns 1
       --output-format json --no-session-persistence
Then   exit code 非 0
And    stderr 或 stdout 含認證相關錯誤訊息（如 "auth"、"login"、"token"、"key"）
And    exit code 非 signal-killed（非 segfault / SIGABRT）
```

## 4. Interface / API Design

### 4.1 Package Layout

```
io.github.samzhu.grimo.cli
├── package-info.java                  # @ApplicationModule (已存在，不修改)
└── CliInvocationOptions.java          # record — provider → env vars + CLI flags
```

> 設計說明：`CliInvocationOptions` 放在 `cli` 模組根套件（非 `api/` 子套件），因為 S005 尚未建立 `api/` 套件。模組根的公開型別對宣告 `allowedDependencies = { "cli" }` 的消費者可見。S005 出貨後可遷移至 `cli/api/`。

### 4.2 `CliInvocationOptions` — 容器化 CLI 呼叫配置

```java
package io.github.samzhu.grimo.cli;

import io.github.samzhu.grimo.core.domain.ProviderId;
import java.util.List;
import java.util.Map;

/**
 * 每個 CLI provider 的容器化呼叫配置。包含注入容器的環境變數
 * 和附加至 CLI 命令的旗標。
 *
 * <p>設計說明：此 record 封裝 S006 驗證確認的配置組合。
 * 呼叫者（S007 主代理 / S010 子代理）在建立容器（env vars）
 * 和組裝 wrapper script（CLI flags）時消費這些選項。
 *
 * <p>每個靜態工廠方法代表一個經 S006 IT 驗證可行的配置。
 */
public record CliInvocationOptions(
    ProviderId provider,
    Map<String, String> containerEnvVars,
    List<String> cliFlags
) {
    /**
     * Claude Code — 訂閱認證 + 記憶體停用 + 遙測停用。
     *
     * @param oauthToken 從 macOS Keychain 或 claude setup-token 取得的 OAuth token
     */
    public static CliInvocationOptions claude(String oauthToken) {
        return new CliInvocationOptions(
            ProviderId.CLAUDE,
            Map.of(
                "CLAUDE_CODE_OAUTH_TOKEN", oauthToken,
                "CLAUDE_CODE_DISABLE_CLAUDE_MDS", "1",
                "CLAUDE_CODE_DISABLE_AUTO_MEMORY", "1",
                "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1"
            ),
            List.of()
        );
    }

    /**
     * Codex CLI — 認證由 RO 掛載 ~/.codex/ 提供；遙測停用。
     * 呼叫者需在 SandboxConfig 額外設定 ~/.codex/ bind-mount。
     */
    public static CliInvocationOptions codex() {
        return new CliInvocationOptions(
            ProviderId.CODEX,
            Map.of("CODEX_HOME", "/root/.codex"),
            List.of("-c", "analytics.enabled=false")
        );
    }

    /**
     * Gemini CLI — API key 認證；遙測預設已關閉。
     *
     * @param apiKey Google AI Studio 的 Gemini API key
     */
    public static CliInvocationOptions gemini(String apiKey) {
        return new CliInvocationOptions(
            ProviderId.GEMINI,
            Map.of(
                "GEMINI_API_KEY", apiKey,
                "GEMINI_CLI_HOME", "/tmp/gemini-home"
            ),
            List.of()
        );
    }
}
```

### 4.3 IT 驗證模式（概要）

```java
// 範例：AC-2 Claude 認證驗證
@Test
@DisplayName("[S006] AC-2: Claude auth via CLAUDE_CODE_OAUTH_TOKEN in container")
void claudeAuthViaOAuthToken() {
    Assumptions.assumeTrue(claudeTokenPresent(), "No Claude token available");

    try (var container = new GenericContainer<>(GRIMO_RUNTIME_IMAGE)
            .withCommand("sleep", "infinity")
            .withEnv("CLAUDE_CODE_OAUTH_TOKEN", getClaudeToken())
            .withEnv("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1")) {
        container.start();

        // When
        ExecResult result = container.execInContainer(
            "claude", "-p", "hello", "--max-turns", "1",
            "--output-format", "json", "--no-session-persistence");

        // Then
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).isNotBlank();
    }
}
```

### 4.4 跨模組通訊

| 消費者模組 | 需要的型別 | `allowedDependencies` 變更 |
| --- | --- | --- |
| `agent` (S007) | `CliInvocationOptions` | `"cli"` |
| `subagent` (S010) | `CliInvocationOptions` | `"cli"` |

`CliInvocationOptions` 在 `cli` 模組根套件，消費者以 `allowedDependencies = { "cli" }` 存取。S005 出貨建立 `@NamedInterface("api")` 後，可遷移至 `cli/api/` 並改為 `"cli :: api"`。

## 5. File Plan

### New documentation

| File | Description |
| --- | --- |
| `docs/grimo/cli-config-matrix.md` | 三 CLI 配置介面完整對照表（認證、記憶體、遙測、設定檔、env var） |

### New production files

| File | Description |
| --- | --- |
| `src/main/java/io/github/samzhu/grimo/cli/CliInvocationOptions.java` | record — provider → env vars + CLI flags 對映，含 `claude()`、`codex()`、`gemini()` 靜態工廠 |

### New test files

| File | Description |
| --- | --- |
| `src/test/java/io/github/samzhu/grimo/cli/CliInvocationOptionsTest.java` | T0 unit：record 建構驗證、靜態工廠回傳正確 env vars/flags（AC-1 部分） |
| `src/test/java/io/github/samzhu/grimo/cli/ClaudeConfigIT.java` | T3 contract：Claude 認證傳遞（AC-2）+ 記憶體停用（AC-5）+ 缺少認證（AC-6） |
| `src/test/java/io/github/samzhu/grimo/cli/CodexConfigIT.java` | T3 contract：Codex RO 掛載認證（AC-3） |
| `src/test/java/io/github/samzhu/grimo/cli/GeminiConfigIT.java` | T3 contract：Gemini API key 認證（AC-4） |

### Modified files

| File | Action | Description |
| --- | --- | --- |
| `docs/grimo/specs/spec-roadmap.md` | modify | S006 status → `⏳ Design`；修正描述（移除「只讀掛載」，改為「Keychain 提取 + env var 注入」） |

### Not touched

- `cli/package-info.java` — `allowedDependencies = {}` 維持不變（`CliInvocationOptions` 只用 `core`（OPEN）和 library 依賴）
- `sandbox/` 模組 — 不修改 `SandboxConfig`（IT 直接用 `GenericContainer`）
- `build.gradle.kts` — 不新增依賴（Testcontainers 已由 S003 引入）
- `application.yml` — 無新屬性
- S005 的檔案 — S005 尚未出貨，不碰

---

<!-- Sections 6-7 added by /planning-tasks after implementation -->
