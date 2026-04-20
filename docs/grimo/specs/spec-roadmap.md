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

## 里程碑 5：Session + Skill 基礎（優先級「對話持久化 + Skill 登錄」）

**目標。** `grimo chat --resume` 接回最近的 Claude session。同時建立 Skill 登錄檔，為後續 Skill 注入做準備。S011 和 S012 無互相依賴，可並行開發。
**完成條件。** S011、S012 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S011 | Session Resume | XS (6) | ✅ |
| S012 | Skill 登錄檔 | XS (8) | ✅ |

### S011 — Session Resume · XS (6)

**描述。** 為 `grimo chat` 加入 `--resume` 旗標，透過 Claude CLI 原生的 `--continue` flag 接回同工作目錄下最近一次的 session。零新依賴 — Claude CLI 自己管歷史（`~/.claude/projects/` 下的 JSONL transcript）。

**設計要點（v2 · POC 驅動重設計）。** POC（8/8 通過）驗證 `AgentSession.resume()` + `CLIOptions.continueConversation(true)` 完美運作。v1 設計（decorator + spring-ai-session + H2）基於「AgentSession 沒有持久化」的錯誤假設 — 實際上 Claude CLI 原生支援 `--continue` / `--resume`。改用 same-package helper（`ClaudeSessionConnector`）存取 package-private 建構子，回傳標準 `AgentSession`。

**依賴。** S007 ✅（程式碼層級 — 修改 REPL 加入 `--resume` 旗標）。

**SBE。**
- **AC-1** `grimo chat --resume` 接回先前對話；agent 回應參考先前上下文。
- **AC-2** 無先前 session 時，自動降級為新 session，印出提示訊息。
- **AC-3** `grimo chat`（無 `--resume`）行為不變（不破壞 S007）。

**估算。** 技術 1 · 不確定性 1 · 依賴 1 · 範疇 1 · 測試 1 · 可逆性 1 = **6 / XS**

### S012 — Skill 登錄檔 · XS (8)

**描述。** 重寫 `spring-ai-agent-utils` 0.7.0 的 `Skills` + `MarkdownParser`（保持同介面，內部改用 SnakeYAML），掃描 `~/.grimo/skills/*/SKILL.md`，遵循 [agentskills.io](https://agentskills.io/specification) 開放標準驗證。建立 `SkillRegistryUseCase`，公開 `list()`、`listEnabled()`、`enable(name)`、`disable(name)`、`get(name) → Optional<SkillEntry>`。產出型別為框架原生 `SkillsTool.Skill` record，可直接被 `SkillsFunction` → `ToolCallback` → `ChatClient` 消費。啟用狀態持久化至 `~/.grimo/skills/.state.json`。

**設計要點。** agent-utils 的 `MarkdownParser` 為自製 flat parser，不支援巢狀 YAML（`metadata:` map、`allowed-tools:` list、多行 scalar 均解析失敗）。`Skills` 類全 static 無 SPI，`MarkdownParser` 硬編碼無法替換。Grimo 重寫這兩個類別（同介面），改用 SnakeYAML（Boot classpath 已有）+ graceful 錯誤處理（單檔失敗跳過而非全部炸掉）。

**競品參考。** agentskills.io 開放標準（26+ 平台相容）+ Hermes Agent Closed Learning Loop 觸發條件（§6.3.2）+ Claude Code `.claude/skills/` 結構。

**依賴。** S001（`GrimoHomePaths.skills()`，已出貨）。無程式碼層級阻塞。

**SBE。**
- **AC-1** `~/.grimo/skills/hello/SKILL.md`（含巢狀 `metadata` map）出現在 `list()` 中，frontMatter 正確解析。
- **AC-2** 無效格式的 SKILL.md 記錄警告並跳過（不崩潰）。
- **AC-3** `disable("hello")` 持久化；重啟後 `list()` 仍標記為已停用；`listEnabled()` 不包含。

**估算。** 技術 1 · 不確定性 1 · 依賴 2 · 範疇 2 · 測試 1 · 可逆性 1 = **8 / XS**

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

### S014 — Session 壓縮策略研究 · XS (8)

**描述。** 研究並選定 `spring-ai-session` 0.2.0 內建的壓縮策略（`SlidingWindowCompactionStrategy` / `TurnWindowCompactionStrategy` / `TokenCountCompactionStrategy` / `RecursiveSummarizationCompactionStrategy`）與觸發器（`TurnCountTrigger` / `TokenCountTrigger`），整合至 S011 的 `PersistentAgentSession` decorator。含基準測試：不同策略下 `--resume` 的 bootstrap prompt 品質 vs token 消耗。

**競品參考。** Hermes Agent Periodic Nudge 自省機制（§6.3.3）+ Claude Code Auto Dream 200 行閾值。

**依賴。** S011（Session 持久化 — 需有 H2 事件資料才能做策略比較）。

**SBE（草稿）。**
- **AC-1** 選定壓縮策略 + 觸發器配置，記錄於 spec §7。
- **AC-2** `PersistentAgentSession` 整合壓縮：超過觸發閾值時自動壓縮舊事件。
- **AC-3** 壓縮後 `--resume` 的 bootstrap prompt 仍能讓 agent 參考先前上下文。

**估算。** 技術 2 · 不確定性 2 · 依賴 1 · 範疇 1 · 測試 1 · 可逆性 1 = **8 / XS**

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
| M7 Skill 注入 + 壓縮 + 評估 | S013、S014、S015 | 28 |
| **合計** | **16 個規格** | **153 點** |

v5 藍圖相較 v4（15 規格 / 144 點）新增 1 個規格（S015 Skill 複雜度評估，+9 點）。S012 設計更新：重寫 `Skills` + `MarkdownParser`（同介面，SnakeYAML 內部），遵循 agentskills.io 標準，產出框架原生 `SkillsTool.Skill`。

下一步行動：`/planning-tasks S012`

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
