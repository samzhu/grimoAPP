# CLI 配置對照表

> 來源：S006 研究。每個條目附官方文件或原始碼 URL。
> 日期：2026-04-18 · 適用映像：`grimo-runtime:0.0.1-SNAPSHOT`

---

## 1. 認證機制

| 項目 | Claude Code | Codex CLI | Gemini CLI |
| --- | --- | --- | --- |
| **主機儲存位置** | macOS Keychain（`Claude Code-credentials`）；Linux: `~/.claude/.credentials.json` | `~/.codex/auth.json`（明文 JSON） | macOS Keychain（`@github/keytar`）→ fallback `~/.gemini/gemini-credentials.json`（AES-256-GCM 加密） |
| **容器可用性** | Keychain 不可跨容器 → 需提取 token | 明文檔案 → RO 掛載可行 | Keychain 不可跨容器；加密檔密鑰綁定 hostname+username（scrypt）→ 容器內**無法解密** |
| **Grimo 策略** | 注入 `CLAUDE_CODE_OAUTH_TOKEN` env var | RO 掛載 `~/.codex/auth.json` 至 `/root/.codex/auth.json` + `CODEX_HOME=/root/.codex`（Codex 需 writable CODEX_HOME 存 cache） | 注入 `GEMINI_API_KEY` env var |
| **認證 env var** | `CLAUDE_CODE_OAUTH_TOKEN`（優先於 Keychain；接受 Max/Pro/Teams/Enterprise token） | `OPENAI_API_KEY`（API key fallback） | `GEMINI_API_KEY`（完全繞過 OAuth） |
| **Token 取得** | `claude setup-token`（一年期）或 macOS: `security find-generic-password -s "Claude Code-credentials" -w` | `~/.codex/auth.json` 直接可用 | Google AI Studio 申請 API key |
| **來源** | [Authentication docs](https://code.claude.com/docs/en/authentication) | [config types](https://github.com/openai/codex/blob/main/codex-rs/config/src/types.rs) · [auth storage](https://github.com/openai/codex/blob/main/codex-rs/login/src/auth/storage.rs) | [keychainService.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/services/keychainService.ts) · [fileKeychain.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/services/fileKeychain.ts) · [contentGenerator.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/core/contentGenerator.ts) |

## 2. 記憶體 / 專案上下文控制

| 項目 | Claude Code | Codex CLI | Gemini CLI |
| --- | --- | --- | --- |
| **CLAUDE.md / 專案上下文** | `CLAUDE_CODE_DISABLE_CLAUDE_MDS=1` 停用所有 CLAUDE.md 載入 | 無等效機制（Codex 不讀取專案層級指令檔） | `GEMINI_CLI_HOME=/tmp/gemini-home` 避免讀取主機 `~/.gemini/settings.json` |
| **自動記憶體** | `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1` 停用 auto-memory | N/A | N/A |
| **`--bare` 模式** | 一旗全殺（CLAUDE.md + auto-memory + hooks + skills + MCP）。**不使用**——(1) 不讀取 `CLAUDE_CODE_OAUTH_TOKEN`（認證衝突）；(2) 殺掉 skill 載入（S012 衝突） | N/A | N/A |
| **來源** | [Env vars](https://code.claude.com/docs/en/env-vars) · [CLI reference](https://code.claude.com/docs/en/cli-reference) | [Codex README](https://github.com/openai/codex) | [config.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/config/config.ts) |

## 3. 遙測控制

| 項目 | Claude Code | Codex CLI | Gemini CLI |
| --- | --- | --- | --- |
| **停用方式** | `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`（umbrella: 遙測 + 自動更新 + 回饋） | `-c analytics.enabled=false`（CLI flag）或 `~/.codex/config.toml` 的 `analytics.enabled = false` | 預設已關閉（`telemetry.enabled = false`） |
| **來源** | [Env vars](https://code.claude.com/docs/en/env-vars) | [config types](https://github.com/openai/codex/blob/main/codex-rs/config/src/types.rs) | [config.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/config/config.ts) |

## 4. 設定檔位置與格式

| 項目 | Claude Code | Codex CLI | Gemini CLI |
| --- | --- | --- | --- |
| **設定檔路徑** | `~/.claude/settings.json`（全域）+ 專案根 `.claude/settings.json` | `~/.codex/config.toml`（TOML） | `~/.gemini/settings.json`（JSON） |
| **格式** | JSON | TOML | JSON |
| **來源** | [Settings](https://code.claude.com/docs/en/settings) | [config types](https://github.com/openai/codex/blob/main/codex-rs/config/src/types.rs) | [config.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/config/config.ts) · [paths.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/utils/paths.ts) |

## 5. 配置目錄覆寫 env var

| 項目 | Claude Code | Codex CLI | Gemini CLI |
| --- | --- | --- | --- |
| **覆寫 env var** | 無官方 env var（`~/.claude/` 硬編碼） | `CODEX_HOME`（重導整個 `~/.codex/` 路徑） | `GEMINI_CLI_HOME`（重導整個 `~/.gemini/` 路徑） |
| **來源** | [Authentication docs](https://code.claude.com/docs/en/authentication) | [home-dir](https://github.com/openai/codex/blob/main/codex-rs/utils/home-dir/src/lib.rs) | [paths.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/utils/paths.ts) |

---

## Grimo 容器化呼叫配置摘要

| Provider | 認證 env var | 額外 env vars | CLI flags | Bind mounts |
| --- | --- | --- | --- | --- |
| **Claude** | `CLAUDE_CODE_OAUTH_TOKEN` | `CLAUDE_CODE_DISABLE_CLAUDE_MDS=1`, `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1`, `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1` | — | — |
| **Codex** | —（RO 掛載 auth.json） | `CODEX_HOME=/root/.codex` | `-c analytics.enabled=false` | `~/.codex/auth.json` → `/root/.codex/auth.json`（RO） |
| **Gemini** | `GEMINI_API_KEY` | `GEMINI_CLI_HOME=/tmp/gemini-home` | — | — |

> 以上配置固化於 `CliInvocationOptions` record（`io.github.samzhu.grimo.cli.api`）。
