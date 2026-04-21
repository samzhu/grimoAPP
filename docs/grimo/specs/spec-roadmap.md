# Grimo — 規格藍圖（v4 · 重新排序於 2026-04-19）

**狀態：** v0.4 · **日期：** 2026-04-19
**來源：** `docs/grimo/PRD.md` + `architecture.md` + `docs/local/competitive-analysis.md`

> **v4 重新規劃說明（2026-04-19）。** S011 設計期間發現關鍵依賴鬆綁：S011（Session 持久化）只依賴 S007（`AgentSession` SPI），不需等 S008-S010（容器化 CLI）完成。S012（Skill 登錄檔）只依賴 S001（`GrimoHomePaths`，已出貨），可與 S008 並行。據此重新排序：S011 提前至 S007 之後，S012 與 S008 並行，新增 S014（Session 壓縮策略研究）。
>
> 研究發現：`SessionMemoryAdvisor`（spring-ai-session）為 ChatClient advisor，無法掛載到 `AgentSession`（agent-client）— 兩者屬不同 API 層次。S011 改用 decorator 模式包裝 `AgentSession` / `AgentSessionRegistry`，以 `SessionService` 做 H2 儲存後端。Hermes Agent 競品分析整合至 `docs/local/competitive-analysis.md`。

> 估算量表（六維評分，每維 1–3）：`技術風險 · 不確定性 · 依賴關係 · 範疇 · 測試 · 可逆性`
> 6–8 → XS · 9–11 → S · 12–14 → M · 15–16 → L · 17–18 → XL（必須分解）

## 依賴關係圖（MVP）

```
                                  S000 ✅
                                    │
                                    ▼
                                  S001 ── S002
                                    │      │
                                    ▼      ▼
                                  S003（容器操作）
                                    │
                         ┌──────────┼──────────┐
                         ▼          ▼          ▼
                       S004（含 3 個 CLI 的執行映像）
                         │
                         ▼
                       S005（透過 docker exec 的 CLI 適配器）
                         │
                         ▼
                       S006（CLI 配置研究 + 策略驗證）
                         │
                         ▼
                       S007（主機 claude 對話）
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
        S011           S008           S012
    (Session 持久化)  (Claude Docker)  (Skill 登錄檔)
          │              │              │
          ▼         ┌────┴────┐         │
        S014        ▼         ▼         │
    (壓縮策略)    S009      S010        │
                 (Gemini)  (Codex)      │
                    │         │         │
                    └────┬────┘         │
                         ▼              │
                       S013 ◄───────────┘
                  (Skill 預裝至容器)
```

## 里程碑地圖（MVP）

| M# | 名稱 | 優先級（使用者） | 規格 | 目標 |
| --- | --- | --- | --- | --- |
| M0 | 基礎建設 | — | S000–S002 | 可建置的 Spring Boot 4.0 模組化骨架，運行於 JDK 25 |
| M1 | 容器操作 | 能操作容器 | S003 | Grimo 可從 Java 啟動 / exec / bind-mount / 停止 Docker 容器 |
| M2 | 容器化 CLI | 能在容器內用 3 個 CLI | S004–S005 | `grimo-runtime` 映像內建 `claude` + `codex` + `gemini`；Java 適配器透過 `docker exec` 呼叫 |
| M3 | CLI 配置 | 研究 CLI 配置 | S006 | Claude-Code 記憶體關閉、各提供者配置慣例套用至所有容器化呼叫 |
| M4 | 主代理對話 | main-agent 跟使用者對話 | S007 | `grimo chat` → 主機 claude CLI → 終端 REPL 多輪對話 |
| M5 | Session + Skill 基礎 | 對話持久化 + Skill 登錄 | S011, S012 | 對話歷史存入 H2 + `~/.grimo/skills/` 掃描（可並行） |
| M6 | 容器化 CLI 對話 | 在 Docker 中跑各 CLI | S008 → S009, S010 | 逐一驗證 Claude / Gemini / Codex 在 grimo-runtime 容器中對話 |
| M7 | Skill 注入 + 壓縮 | 預裝 skill + 壓縮策略 | S013, S014 | Skill 預裝至容器 + Session 壓縮策略研究 |

---

## 里程碑 0：基礎建設 ✅（2026-04-16）

3/3 規格完成（S000 + S001 + S002）。詳見 `specs/archive/2026-04-16-S00[0-2]-*.md`。

---

## 里程碑 1：容器操作 ✅（2026-04-17）

1/1 規格完成（S003）。詳見 `specs/archive/2026-04-17-S003-sandbox-bind-mount-adapter.md`。

---

## 里程碑 2：容器化 CLI ✅（2026-04-18）

2/2 規格完成（S004 + S005）。詳見 `specs/archive/2026-04-1[7-8]-S00[4-5]-*.md`。

---

## 里程碑 3：CLI 配置 ✅（2026-04-18）

1/1 規格完成（S006）。詳見 `specs/archive/2026-04-18-S006-cli-config-validation.md`。

---

## 里程碑 4：主代理對話 ✅（2026-04-19）

1/1 規格完成（S007）。詳見 `specs/archive/2026-04-18-S007-main-agent-chat.md`。

---

## 里程碑 5：Session + Skill 基礎 ✅（2026-04-20）

2/2 規格完成（S011 + S012）。詳見 `specs/archive/2026-04-19-S01[1-2]-*.md`。

---

## 人工驗證閘門（M5 → M6）

**目標。** 在進入容器化里程碑前，以 `java -jar` 端對端驗證 M5 出貨的三大能力：對話、Session 恢復、Skill 管理 + 投影。
**完成條件。** S016 ✅ + 人工驗證計畫全部通過。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S016 | MVP 人工驗證閘門（Skill CLI + 投影） | XS (7) | ✅ |

### S016 — MVP 人工驗證閘門 · XS (7)

**描述。** 為 `SkillRegistryUseCase`（S012）加上 CLI 子命令（`grimo skill list/enable/disable`）。`grimo chat` 啟動前將已啟用 Skill 投影至 `<workdir>/.claude/skills/`，使 Claude Code 原生載入。含結構化人工驗證計畫。

**依賴。** S007（主代理對話）、S012（Skill 登錄檔）。

**SBE（草稿）。**
- **AC-1** `grimo skill list` 顯示所有 skill 的名稱、狀態、描述。
- **AC-2** `grimo skill enable/disable <name>` 切換狀態並持久化至 `.state.json`。
- **AC-3** `grimo chat` 啟動前，已啟用 skill 投影至 `<workdir>/.claude/skills/<name>/SKILL.md`；已停用不投影。
- **AC-4** 無效子命令或不存在的 skill 名稱印出友善錯誤訊息。

**估算。** 技術 1 · 不確定性 1 · 依賴 1 · 範疇 2 · 測試 1 · 可逆性 1 = **7 / XS**

---

## Session 記錄層（M5 → M6）

**目標。** 建立 Grimo 自有的 event-sourced 對話歷史記錄層。Decorator 攔截 `AgentSession.prompt()`，以 JSON payload + metadata 持久化至 append-only event store + session projection。定義 Compaction SPI 供 S014 插入策略。Schema 預留 Claude Code 對話 fork 支援。
**完成條件。** S017 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S017 | Grimo Session Memory（event-sourced session 模組） | S (11) | ✅ |

### S017 — Grimo Session Memory · S (11)

**描述。** 新建 `session` Modulith 模組（Backlog 晉升）。`RecordingAgentSession` decorator 攔截每輪 `AgentSession.prompt()`，透過 Modulith event 非同步持久化至 H2 event store `grimo_session_event`（append-only，`payload_json` + `metadata_json`）。每輪產生 USER + ASSISTANT 兩筆 events。`grimo_session` projection 表物化 session 摘要，含 `parent_id` + `fork_turn` 預留對話 fork 及跨 Provider 切換追蹤。Schema 預留 `synthetic` flag + `SUMMARY` event type 供 S014 compaction 使用。**Provider-agnostic 設計：** `ProviderMetadataExtractor` SPI 動態辨識 provider 並萃取 metadata，新增 provider 只需一個 class。

**v3.1 更新（2026-04-21）。** v3 硬編碼 Claude provider。v3.1 引入 `ProviderMetadataExtractor` SPI、移除 schema DEFAULT 'claude'、擴展 `parent_id` 語意涵蓋跨 Provider 切換（PRD AC3）、明確記載三層程式模型（`AgentModel` vs `AgentClient` vs `AgentSession`）選擇理由。

**參考。** [Spring AI Session API (Part 7)](https://spring.io/blog/2026/04/15/spring-ai-session-management) — turn-safe compaction、four strategies。[Spring AI Agentic Patterns Part 6](https://spring.io/blog/2026/04/07/spring-ai-agentic-patterns-6-memory-tools) — Session Memory vs Long-term Memory 兩層互補。[competitive-analysis.md §3.1](docs/local/competitive-analysis.md) — T3 Code event sourcing schema。

**依賴。** S007 ✅（主代理 REPL）、S011 ✅（AgentSession API 驗證）。

**SBE。**
- **AC-1** 每輪產生 USER + ASSISTANT 兩筆 events，payload_json + metadata_json。
- **AC-2** 多輪 append-only：3 輪 → 6 筆 INSERT，turn_number 遞增。
- **AC-3** Session projection 自動物化：turn_count、total_tokens、total_duration。
- **AC-4** Registry decorator 透明包裝：sessionId/workDir 與底層一致。
- **AC-5** Fork schema 預留：parent_id + fork_turn 欄位存在。
- **AC-6** Modulith verify 通過，session 模組邊界合規。

**POC: not required** — Event sourcing 為標準 pattern。JdbcTemplate + H2 + Jackson 已在專案中驗證。

**估算。** 技術 2 · 不確定性 1 · 依賴 1 · 範疇 3 · 測試 2 · 可逆性 2 = **11 / S**

---

## REST API Foundation（M5 → M6）

**目標。** 建立 Grimo 作為「自動開發助手」的 REST API 資料層。Project 管理、Task 管理、Chat 對話，取代 CLI adapter。Schema 重設計。
**完成條件。** S018 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S018 | REST API Foundation — Project / Task / Chat | S (9) | ✅ |

### S018 — REST API Foundation · S (9)

**描述。** 建立 Grimo 的完整 REST API 基礎（Layer 1 資料層）。新增 `project` 模組（Project CRUD）和 `task` 模組（Task CRUD + lifecycle 狀態機）。Chat REST API 支援 Grimo 級（無 project）和 Project 級（綁定 project）兩種 session。Schema 全部重寫（`.grimo/db` 可砍掉重建）。移除 CLI adapter（`ChatCommandRunner` + `SkillCommandRunner`），Tomcat 常駐。

**v2 重新定位說明（2026-04-21）。** 原 v1 定位為 XS 測試 API。經競品研究（Devin、Linear、OpenClaw、Hermes Agent、Superconductor）後，擴展為開發助手資料層。AI 智慧層（S019）、執行管線（S020）、頻道適配器（S021）、記憶模組（S022）為後續 spec。

**依賴。** S007 ✅、S012 ✅、S016 ✅、S017 ✅。

**SBE（12 AC）。**
- **AC-1** Project CRUD：create / list / get / update / delete。
- **AC-2** Project 名稱唯一，重複回傳 409。
- **AC-3** Task CRUD 有 project：create（auto task_number）/ list（filter by projectId）/ get（by taskNumber）。
- **AC-4** Task CRUD 無 project：`projectId` 為 null 的 Grimo 級 Task。
- **AC-5** Task lifecycle：OPEN → IN_PROGRESS → IN_REVIEW → DONE | CANCELLED。
- **AC-6** Chat Grimo 級 session：`POST /api/chat` 無 projectId → session_type = GRIMO。
- **AC-7** Chat Project 級 session：`POST /api/chat` 有 projectId → session_type = PROJECT。
- **AC-8** Chat 多輪 + resume。
- **AC-9** Session 列表 + 篩選（sessionType、projectId）。
- **AC-10** Skill REST API（list / enable / disable / project）。
- **AC-11** 錯誤處理（404 for missing resources）。
- **AC-12** CLI adapter 移除 + MockMvc 可測試 + ModuleArchitectureTest 通過。

**估算。** 技術 1 · 不確定性 1 · 依賴 1 · 範疇 3 · 測試 2 · 可逆性 1 = **9 / S**

---

## Session Message Tree（S018 後續）

**目標。** 將對話從線性列表改為 Adjacency List tree，支援無限分支（regenerate、fork）。
**完成條件。** S023 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S023 | Session Message Tree — Adjacency List Branching | XS (7) | ✅ |

### S023 — Session Message Tree · XS (7)

**描述。** `grimo_session_event` 加 `parent_event_id`（自我參照 FK）建立 Adjacency List tree。`grimo_session` 加 `current_event_id` 作為 branch 書籤。移除預留的 `branch` 欄位。TurnRecorder 建立 parent-child 鏈。Recursive CTE 從葉走到根載入對話路徑。

**依賴。** S018 ✅。

**SBE（6 AC）。** parent_event_id FK, current_event_id FK, parent-child chain, recursive CTE path, branching scenario, branch column removed。

**估算。** 技術 1 · 不確定性 1 · 依賴 1 · 範疇 2 · 測試 1 · 可逆性 1 = **7 / XS**

---

## 開發助手管線（S018 後續）

**目標。** S018 建立資料基礎後，逐步加上 AI 智慧、執行管線、多平台入口、記憶系統。
**完成條件。** S019–S022 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S019 | Chat Intelligence — 對話驅動任務建立 | 待估 | 🔲 |
| S020 | Task Execution Pipeline — worktree + Docker 執行 | 待估 | 🔲 |
| S021 | Channel Binding — 多平台入站適配器 | 待估 | 🔲 |
| S022 | Memory Module — 兩層記憶 | 待估 | 🔲 |

### S019 — Chat Intelligence（對話驅動任務建立）

**描述。** Main Agent 在對話中辨識使用者意圖，自動建立 Task 並回報進度。Grimo 級對話需推斷目標 project（從對話內容 + 已註冊 project 列表比對）；Project 級對話自動 scope 到綁定的 project。Task 建立後回覆使用者「已建立任務 #42，開始執行...」。

**為什麼獨立：** 需要 LLM 推理層（tool calling / function calling），與 S018 的純 CRUD 不同性質。

**依賴。** S018（REST API + Task CRUD）。

**關鍵設計方向：**
- Main Agent 透過 tool calling 呼叫 `TaskUseCase.create()` — Grimo 自己呼叫自己的 API
- Project 推斷邏輯：比對使用者訊息中的 repo 名稱 / 路徑 / 技術棧關鍵字
- 回報機制：Task 狀態變更時，透過 chat session 通知使用者

### S020 — Task Execution Pipeline（開發任務的 worktree + Docker 執行）

**描述。** 針對**開發類 Task**（labels 含 `dev` / `bug` / `refactor` 等）的自動執行管線。新增 `grimo_task_execution` 表（或擴充 `grimo_task` 加 execution 欄位）記錄執行資訊。流程：`git worktree add` → Docker sandbox 啟動 → Agent 在容器內執行 → commit + push → `gh pr create`。非開發類 Task（研究、文件、分析）不走此管線。非 git 資料夾的 Task 跳過 worktree/PR 流程。

**為什麼獨立：** Task 本身是通用工作項目（S018），開發執行管線是特定類型 Task 的行為層。與 Backlog subagent 模組高度重疊。

**依賴。** S018（Task CRUD）、S003（sandbox）、S005（CLI adapter）。

**關鍵設計方向：**
- 執行資訊（branch, worktreePath, containerId, prUrl, prNumber）由本 spec 定義 schema
- Agent 忘記 commit 的防護：容器退出前 orchestrator 強制 `git commit`
- Worktree cleanup：PR merge 後或 Task CANCELLED 時自動移除
- 非 git 資料夾：Task 仍可執行，但 worktree/PR 不適用
- MVP 用 main agent 執行（不是 sub-agent），未來擴展為 sub-agent 模式

### S021 — Channel Binding（多平台入站適配器）

**描述。** `grimo_channel_binding` 表 + `ChannelBindingPort` 六邊形 port。Discord adapter POC：一個 Discord channel 對應一個 project 的 chat session。六邊形架構讓 Slack / LINE / Webhook 未來可接入而不改核心。

**為什麼獨立：** PRD 列為 out-of-scope adapter 實作，但 port 設計影響 S018 的 ChatPort 介面。先把資料模型和 port 介面定好，adapter 可逐個實作。

**依賴。** S018（Project + Chat REST API）。

**關鍵設計方向：**
- Session binding 策略：Discord channel config 決定 projectId（BOUND 模式）
- OpenClaw 參考：binding rule 的 most-specific-match-wins 路由
- Hermes 參考：profile = project 隔離（Grimo 做到單實例多 project）
- OpenAB 參考：ThreadKey = `"discord:{channel_id}"`（1 channel = 1 session）

### S022 — Memory Module（兩層記憶）

**描述。** `grimo_memory` 表（GLOBAL + PROJECT scope）。Global 記憶 = 工程師跨專案經驗；Project 記憶 = 專案架構、決策、脈絡。Session 蒸餾觸發條件參考 Hermes Closed Learning Loop（工具呼叫 ≥5 / 錯誤恢復 / 用戶糾正）。記憶繼承：Project Memory 繼承 Global Memory；蒸餾回：Project 中學到的通用教訓回流到 Global。

**為什麼獨立：** 獨立的領域概念 + 蒸餾觸發邏輯。與 PRD 的 `AutoMemoryTools` 接線（Backlog）有交集，需要重新 grill。

**依賴。** S018（Project 模組）、S017（Session event store — 蒸餾來源）。

**關鍵設計方向：**
- Hermes 四層記憶參考：Prompt / Session / Skill / User
- Grimo 對應：Global Memory / Session Events / Skills / Project Memory
- 蒸餾觸發器：sub-agent 執行完畢時，檢查 TurnRecorded events 是否符合觸發條件
- 記憶格式：Markdown（與 `~/.grimo/memory/MEMORY.md` 一致）

---

## 里程碑 6：容器化 CLI 對話（優先級「在 Docker 中跑各 CLI」）

**目標。** 逐一驗證 Claude Code / Gemini / Codex 在 `grimo-runtime` 容器中透過 agent-client SDK 對話。建立 CredentialResolver（macOS Keychain 提取）、WrapperScriptGenerator env var 注入、容器化 `AgentSession` 整合。S011 的 decorator 自動套用於容器化 session — 無需額外接線。
**完成條件。** S008、S009、S010 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S008 | Claude Code 容器化對話 | S (11) | 🔲 |
| S009 | Gemini CLI 容器化驗證 | S (9) | 🔲 |
| S010 | Codex CLI 容器化驗證 | S (9) | 🔲 |

### S008 — Claude Code 容器化對話 · S (11)

**描述。** S007 的 REPL 改接容器化 claude-code。使用 `SandboxManager`（S003）建立 `grimo-runtime` 容器，`WrapperScriptGenerator`（S005）產生 wrapper script，`ClaudeAgentSessionRegistry.builder().claudePath(wrapperPath)` 建立容器化 `AgentSession`。

核心新增：
1. **CredentialResolver** — 從 macOS Keychain 提取 Claude OAuth token（`security find-generic-password -s "Claude Code-credentials" -w` → 解析 JSON → 提取 `accessToken`）。S006 §7 Finding 1 確認 env var 預期 bearer token，非完整 JSON。
2. **WrapperScriptGenerator 擴充** — 注入 `CliInvocationOptions.claude(token)` 的 env vars（`CLAUDE_CODE_OAUTH_TOKEN`、`CLAUDE_CODE_DISABLE_CLAUDE_MDS=1` 等）至 `docker exec -e` flags。
3. **ContainerizedAgentModelFactory 擴充** — 新增 `create(provider, containerId, CliInvocationOptions)` overload。

**依賴。** S007（REPL 基礎）、S003–S006（容器化基礎設施）。

**SBE（草稿）。**
- **AC-1** 建立 `grimo-runtime` 容器，透過容器化 `AgentSession` 輸入「hello」收到 claude 回應；多輪對話保持上下文。
- **AC-2** CredentialResolver 從 macOS Keychain 自動提取 OAuth token 並注入容器。
- **AC-3** 容器內 claude-code 的記憶體（CLAUDE.md）和遙測已停用（S006 env vars 驗證）。
- **AC-4** Docker 未運行時，印出「Start Docker Desktop」訊息並以非零狀態退出。

**估算。** 技術 2 · 不確定性 2 · 依賴 2 · 範疇 2 · 測試 2 · 可逆性 1 = **11 / S**

### S009 — Gemini CLI 容器化驗證 · S (9)

**描述。** `GeminiAgentModel.call()` 透過 S005 wrapper script 對 `grimo-runtime` 容器內的 gemini CLI 呼叫。`GEMINI_API_KEY` env var 注入（S006 驗證）。Gemini SDK 無 session registry — 單次問答 REPL（每個 prompt 獨立）。

**依賴。** S008（共用 CredentialResolver 框架 + WrapperScriptGenerator 擴充）。

**SBE（草稿）。**
- **AC-1** 建立容器，`GeminiAgentModel.call()` 輸入「hello」收到 gemini 回應。
- **AC-2** `GEMINI_API_KEY` 正確注入容器（非 Keychain — Google AI Studio API key）。
- **AC-3** 缺少 `GEMINI_API_KEY` 時，印出清楚的取得指引。

**估算。** 技術 2 · 不確定性 1 · 依賴 2 · 範疇 1 · 測試 2 · 可逆性 1 = **9 / S**

### S010 — Codex CLI 容器化驗證 · S (9)

**描述。** `CodexAgentModel.call()` 透過 S005 wrapper script 對 `grimo-runtime` 容器內的 codex CLI 呼叫。`~/.codex/auth.json` 單檔 RO 掛載至 `/root/.codex/auth.json`（S006 驗證）。Codex SDK 無 session registry — 單次問答 REPL。

**依賴。** S008（共用基礎設施）。

**SBE（草稿）。**
- **AC-1** 建立容器，RO 掛載 `auth.json`，`CodexAgentModel.call()` 輸入「hello」收到 codex 回應。
- **AC-2** `CODEX_HOME=/root/.codex` 正確設定；codex 可讀取認證且自由寫入 cache。
- **AC-3** 缺少 `~/.codex/auth.json` 時，印出清楚的登入指引。

**估算。** 技術 2 · 不確定性 1 · 依賴 2 · 範疇 1 · 測試 2 · 可逆性 1 = **9 / S**

---

## 里程碑 7：Skill 注入 + 壓縮策略 + 複雜度評估（優先級「預裝 skill + 壓縮研究 + 智能分級」）

**目標。** 啟動容器化 agent 時預裝已啟用 Skill。研究並選定 Session 壓縮策略。Skill 安裝時自動評估複雜度，寫入 metadata 供未來成本路由器使用。
**完成條件。** S013、S014、S015 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S013 | Skill 預裝至 Agent 容器 | S (11) | 🔲 |
| S014 | Session 壓縮策略研究 | XS (8) | 🔲 |
| S015 | Skill 複雜度評估 | S (9) | 🔲 |

### S013 — Skill 預裝至 Agent 容器 · S (11)

**描述。** 啟動容器化 agent（S008–S010）前，查詢 `SkillRegistryUseCase.listEnabled()`，將已啟用 Skill 目錄掛載（bind-mount 或 docker cp）至容器的 CLI 原生 Skill 路徑：
- Claude Code → `/root/.claude/skills/<name>/`
- Codex / Gemini → S006 cli-config-matrix.md 記載的路徑（待 grill 確認）

**競品參考。** Aizen `AgentConfigRegistry` 多 agent 設定投影。

**依賴。** S008–S010（容器化 agent）、S012（Skill 登錄檔）。

**SBE（草稿）。**
- **AC-1** 啟用 Skill `hello` 後，容器化 claude agent 啟動，容器內存在 `/root/.claude/skills/hello/SKILL.md`。
- **AC-2** agent 回應參考該 Skill 指令（使用夾具 Skill 斷言）。
- **AC-3** 已停用的 Skill 不被注入。

**估算。** 技術 2 · 不確定性 2 · 依賴 2 · 範疇 2 · 測試 2 · 可逆性 1 = **11 / S**

### S014 — Session Compaction SPI + 策略實作 + 基準測試 · S (10)

**描述。** 定義 `CompactionTrigger` / `CompactionStrategy` SPI 介面（在 `session/domain/`），並提供實作：`TurnCountTrigger`、`TokenCountTrigger`（OR-composite）、`SlidingWindowCompactionStrategy`、`RecursiveSummarizationCompactionStrategy`。整合至 `RecordingAgentSession` decorator — 每輪記錄後檢查 trigger，超過閾值時自動產生 `SUMMARY` synthetic event。含基準測試：不同策略下 bootstrap prompt 品質 vs token 消耗。利用 S017 已建立的 event store schema（`synthetic` flag + `SUMMARY` event type + `turn_number`）。

**參考。** Spring AI Session API（Part 7）四種策略 + 兩種觸發器。Hermes Agent Periodic Nudge 自省機制（§6.3.3）。

**依賴。** S017（Event-sourced session — event store + projection + turn_number）。

**SBE（草稿）。**
- **AC-1** `CompactionTrigger` + `CompactionStrategy` SPI 介面定義在 `session/domain/`，純 Java。
- **AC-2** 超過觸發閾值時自動壓縮：產生 SUMMARY synthetic event，turn-safe（不切割 turn）。
- **AC-3** 壓縮後 event store 保留全量歷史（Recall Storage），compacted events 不刪除。
- **AC-4** 選定壓縮策略 + 觸發器配置，記錄於 spec §7。

**估算。** 技術 2 · 不確定性 2 · 依賴 1 · 範疇 2 · 測試 2 · 可逆性 1 = **10 / S**

### S015 — Skill 複雜度評估 · S (9)

**描述。** Skill 安裝（首次出現在 `~/.grimo/skills/`）時，用 agent 分析 SKILL.md 內容判斷複雜度等級，將結果寫入 `.state.json` 的擴充欄位（如 `complexity`、`recommendedModel`）。評估維度：指令步驟數、工具需求、領域知識要求。用於未來成本路由器選擇模型。

**依賴。** S012（Skill 登錄檔）、S007（主代理 — 需要 agent 做評估）。

**SBE（草稿）。**
- **AC-1** 新 Skill 出現時，自動觸發複雜度評估，結果寫入 `.state.json`。
- **AC-2** 評估結果包含 `complexity`（low/medium/high）和 `recommendedModel`。
- **AC-3** 已評估過的 Skill 不重複評估（除非 SKILL.md 內容變更）。

**估算。** 技術 2 · 不確定性 2 · 依賴 1 · 範疇 2 · 測試 1 · 可逆性 1 = **9 / S**

---

## 摘要（MVP）

| 里程碑 | 規格 | 點數 |
| --- | --- | --- |
| M0 基礎建設 | S000、S001、S002 | 23 |
| M1 容器操作 | S003 | 13 |
| M2 容器化 CLI | S004、S005 | 24 |
| M3 CLI 配置 | S006 | 11 |
| M4 主代理對話 | S007 | 7 |
| M5 Session + Skill 基礎 | S011、S012 | 18 |
| M6 容器化 CLI 對話 | S008、S009、S010 | 29 |
| M7 Skill 注入 + 壓縮 + 評估 | S013、S014、S015 | 30 |
| 驗證閘門 | S016 | 7 |
| Session 記錄層 | S017 | 11 |
| **合計** | **18 個規格** | **173 點** |

v6 藍圖相較 v5（16 規格 / 153 點）新增 1 個規格（S016 MVP 人工驗證閘門，+7 點）。S016 在 M5 → M6 之間插入人工驗證環節：Skill CLI 子命令 + Skill 投影至工作目錄。

下一步行動：`/planning-tasks S016`

---

## Backlog

以下項目現已**延後，直至明確晉升**。每個項目晉升時將使用新的規格 ID 重新進行 grill-me 循環；以下估算為遺留值，晉升時將重新評估。

| 能力 | 先前規格參考 | 延後原因 | 粗略工作量 |
| --- | --- | --- | --- |
| 委派協議（主代理 → Grimo） | v2 S008 | v3 重新排序：先驗證容器化 + session + skill，再做任務委派 | M (13) |
| 工作樹管理員（JGit） | v2 S009 | 同上 | S (9) |
| 子代理生命週期 + diff 審查 | v2 S010 | 同上 | M (14) |
| Web UI（Thymeleaf + HTMX + SSE） | v1 S003 + S006 | CLI 直通為 MVP 介面。需要展示 UI 時再晉升。 | M×2 (23) |
| 帶壓縮重放的 CLI 切換 | v1 S009 | MVP 主代理僅使用 Claude-Code；多 CLI 主代理為後期考量。 | L (15) — 需手動 QA |
| 明確的主代理唯讀工具允許清單 | v1 S010 | 容器隔離 + S006 CLI 配置已在 MVP 中抵消主代理的寫入路徑。 | S (9) |
| 成本路由器（啟發式 v1） | v1 S014 | MVP 不進行路由。 | S (9) |
| 評審團（N 路並行審查） | v1 S015 | 次要功能；需要多 CLI + 比較 UI。 | M (13) |
| `AutoMemoryTools` 接線 | v1 S017 | S011 持久化 session 上線後再考慮。spring-ai-agent-utils 0.7.0 提供 `AutoMemoryToolsAdvisor`。 | M (12) |
| Skill 蒸餾提案者 | v1 S018 | 參考 Hermes Closed Learning Loop；待 S012/S013 累積使用時間後晉升。 | M (14) |
| 成本遙測面板 + `Cost` 領域型別 | v1 S019 | 等待成本路由器。 | XS (8) |
| 模組邊界 CI 任務 | v1 S020 | `ModuleArchitectureTest` 已在測試中覆蓋。 | XS (7) |
| 原生映像加固 | v1 S021a–c | PRD D3 擴展目標。MVP 優先 JVM。 | M×2 + XS (33) |
| E2E 整合測試套件 | v1 S022 | 垂直切片有足夠使用者可見行為時晉升。 | S (10) |

**Backlog 策略。** 項目不得插隊。使用者晉升項目時，以全新 grill 循環重新進入 `/planning-spec`（請勿盲目重用舊草稿驗收標準 — 環境將已改變）。

---

## 技術債（Tech Debt）

實作過程中發現但不阻塞出貨的問題。類型定義見 `development-standards.md` §10.1。每次 `/planning-spec` 開始前檢視此表，可順手處理的直接清掉。

| 來源規格 | 項目描述 | 類型 | 優先級 | 狀態 |
| --- | --- | --- | --- | --- |
| S005 | `WrapperScriptGenerator` 使用 `System.getProperty("java.io.tmpdir")` — 違反 §11 僅 `GrimoHomePaths` 可存取系統屬性規則 | drift | 低 | 🔲 |
| S005 | `cli/package-info.java` Javadoc 仍寫 "strictest white-list" 但 `allowedDependencies` 已改為 `{ "core" }` | drift | 低 | 🔲 |
| S005 | architecture.md §1 聲稱 `Type.OPEN` 模組消費者無需宣告 `allowedDependencies`，實際 Modulith 2.0.5 仍需顯式宣告 | drift | 中 | ✅ S005 出貨時修正 |
| S005 | ContainerizedAgentModelIT (AC-2, AC-3) 編譯通過但未在 Docker 環境執行 | skip | 中 | 🔲 |
| S006 | GeminiConfigIT (AC-4) 編譯通過但因無 GEMINI_API_KEY 以 assumeTrue 跳過 | skip | 中 | 🔲 |
| S006 | cli-config-matrix.md 的 Codex 認證描述需更新為「RO 掛載 auth.json 單檔」（非整個目錄） | drift | 低 | ✅ QA 時修正 |
| S011 | architecture.md §5.2 H2 URL 需修正：移除 `AUTO_SERVER=TRUE`、加入 `DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;CACHE_SIZE=32768`（待實際使用 H2 時修正） | drift | 低 | 🔲 |
| S011 | architecture.md §2.x Backlog `session` 模組描述寫 "SessionMemoryAdvisor 接線"，實際 POC 證實不需要 — Claude CLI 原生支援 `--continue` / `--resume` | drift | 中 | 🔲 |
| S011 | `--resume <sessionId>` 指定 ID 恢復功能延後。需持久化 sessionId 映射 + session 列表 UI。POC 已驗證 SDK `CLIOptions.resume(id)` 可行。 | skip | 中 | 🔲 |
| S011 | PRD D7/D18/P3 仍寫 "透過 SessionMemoryAdvisor" 和 "spring-ai-session 0.2.0"，與 POC 發現不符 — Claude CLI 自管歷史，不需要 Grimo 層持久化（至少對 Claude 而言）。跨 CLI 切換（AC3）可能仍需要，但那是未來 spec 的事。 | drift | 中 | 🔲 |
| S007 | `ChatCommandRunner` 未呼叫 `System.exit(1)` — AC-3「非零 exit code」未強制執行。待引入 CLI 框架（picocli）時以 `ExitCodeGenerator` 處理 | bug | 低 | 🔲 |
| S007 | `MainAgentChatIT` 編譯通過但需主機 claude CLI — 以 `assumeTrue(claudeAvailable())` 跳過 | skip | 中 | 🔲 |
| S007 | `MainAgentChatService` REPL 迴圈使用 `catch (Exception e)` 全捕獲 — 有設計理由（session 死亡時乾淨退出）但違反 §11；應縮窄例外型別 | bug | 低 | 🔲 |
| S011 | `SessionResumeIT` 未建立 — 需主機 claude CLI，與 S007 `MainAgentChatIT` 同模式 | skip | 中 | 🔲 |
| S011 | `ClaudeSessionConnectorTest` 需主機 claude CLI — 以 `assumeTrue(claudeAvailable)` 跳過 | skip | 中 | 🔲 |
| S012 | AC-2 測試（`ac2_invalidSkillSkipped`）僅斷言回傳清單大小，未正式斷言 WARN log 輸出。運行時確認 log 確實輸出但未以程式碼斷言。 | bug | 低 | 🔲 |
| S012 | 測試方法缺少 `// Given / // When / // Then` 注解區塊（development-standards §7.9 要求） | drift | 低 | 🔲 |
| S016 | `SkillCommandRunner` 使用 `System.out`（9 處）+ `System.err`（1 處），違反 development-standards §3「絕不使用 System.out」。`ChatCommandRunner`（S007/S011）也有同模式。需釐清 §3 是否豁免 CLI adapter 層 | drift | 低 | ✅ S018 移除 CLI adapter，改用 REST — 問題消滅 |
| S017 | `JdbcSessionEventAdapter.findBySessionId(sessionId, EventFilter)` 忽略 filter — 回傳全部事件。完整 filter 支援延後至 S014 或查詢功能需要時 | skip | 低 | 🔲 |
| S017 | `EventFilter.lastTurns(n)` 使用 `n * 2` 作為 lastN — 假設每 turn 只有 USER+ASSISTANT 兩筆 events。若未來加入 TOOL_CALL events，此公式需修正 | drift | 低 | ✅ S018 移除 `lastTurns()`，改為 timestamp-based filter |
| S018 | Schema 使用 `DROP TABLE` — 每次啟動重建所有表。生產環境需 migration 策略（Flyway/Liquibase）或 `init.mode=embedded` | drift | 中 | 🔲 |
| S018 | `architecture.md` §5.2 session event store 描述仍為 S017 格式（sequence, event_id, turn_number），需更新為 S018 格式（id, message_type, message_content） | drift | 中 | 🔲 |
| S018 | `architecture.md` §2 模組地圖缺少 `project` 和 `task` 模組 | drift | 中 | 🔲 |
| S018 | Chat REST API 需 real Claude CLI IT：`POST /api/chat` → 實際 prompt → 驗證 session 持久化到 H2 | skip | 中 | 🔲 |
| S018 | Spec §2.1 範例資料表格含已移除的 `provider` 欄位 — 文件與實作不符 | drift | 低 | 🔲 |
| S007 | `ChatCommandRunner` 已被 S018 刪除 — S007 的 AC-3 exit code bug 不再適用 | bug | 低 | ✅ S018 移除 CLI adapter |
| S007 | `MainAgentChatService` REPL catch (Exception e) — S018 已移除 REPL 邏輯 | bug | 低 | ✅ S018 移除 REPL |
| S023 | `SessionEventPort#findConversationPath` Javadoc 寫 "ordered by created_at ASC"，實際實作為 `ORDER BY depth DESC`（deterministic）；AC-4 @DisplayName 同樣描述有誤 | drift | 低 | 🔲 |
| S023 | `architecture.md` 第 168–169 行：`grimo_session` 缺 `current_event_id`；`grimo_session_event` 仍寫 `branch (reserved)` 而非 `parent_event_id` | drift | 中 | ✅ S023 出貨時修正 |
| S023 | `glossary.md` 缺少 S023 新增術語：Adjacency List tree（Adjacency List 樹）、branch bookmark（分支書籤 / current_event_id）、Message Tree（訊息樹） | drift | 低 | 🔲 |
| S023 | `SchemaTest#currentEventIdColumn`（AC-2）只驗證欄位存在 + nullable，未驗證 FK 指向 grimo_session_event(id)（AC-2 spec 要求） | bug | 低 | 🔲 |
