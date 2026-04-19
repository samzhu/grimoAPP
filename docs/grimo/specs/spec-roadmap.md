# Grimo — 規格藍圖（v3 · 重新排序於 2026-04-18）

**狀態：** v0.3 · **日期：** 2026-04-18
**來源：** `docs/grimo/PRD.md` + `architecture.md` + `docs/local/competitive-analysis.md`

> **v3 重新規劃說明（2026-04-18）。** S007 設計期間，使用者重新排序 MVP 驗證路徑：先逐個 CLI provider 驗證容器化對話 → session 持久化 → skill 管理 + 注入。原 v2 的任務派送（委派協議、工作樹管理、子代理生命週期）移至 Backlog，待容器化 + session + skill 驗證完畢後晉升。
>
> 競品分析確認：Grimo 的 `docker exec → CLI` 架構屬於「包裝官方 harness」類別，合規（Anthropic 2026-04-03 確認）。Session 持久化參考 T3 Code Event Sourcing 模式。Skill 管理參考 Hermes Agent Closed Learning Loop 觸發條件。

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
                         ▼
                       S008（Claude Code in Docker）
                         │
                    ┌────┴────┐
                    ▼         ▼
                  S009      S010
                (Gemini)  (Codex)
                    │         │
                    └────┬────┘
                         ▼
                       S011（Session 持久化）
                         │
                         ▼
                       S012（Skill 登錄檔）
                         │
                         ▼
                       S013（Skill 預裝至 Agent 容器）
```

## 里程碑地圖（MVP）

| M# | 名稱 | 優先級（使用者） | 規格 | 目標 |
| --- | --- | --- | --- | --- |
| M0 | 基礎建設 | — | S000–S002 | 可建置的 Spring Boot 4.0 模組化骨架，運行於 JDK 25 |
| M1 | 容器操作 | 能操作容器 | S003 | Grimo 可從 Java 啟動 / exec / bind-mount / 停止 Docker 容器 |
| M2 | 容器化 CLI | 能在容器內用 3 個 CLI | S004–S005 | `grimo-runtime` 映像內建 `claude` + `codex` + `gemini`；Java 適配器透過 `docker exec` 呼叫 |
| M3 | CLI 配置 | 研究 CLI 配置 | S006 | Claude-Code 記憶體關閉、各提供者配置慣例套用至所有容器化呼叫 |
| M4 | 主代理對話 | main-agent 跟使用者對話 | S007 | `grimo chat` → 主機 claude CLI → 終端 REPL 多輪對話 |
| M5 | 容器化 CLI 對話 | 在 Docker 中跑各 CLI | S008–S010 | 逐一驗證 Claude / Gemini / Codex 在 grimo-runtime 容器中對話 |
| M6 | Session 持久化 | 儲存對話 session | S011 | `grimo chat --resume <id>` 恢復先前 session |
| M7 | Skill 管理 + 注入 | 管理並預裝 skill | S012–S013 | `~/.grimo/skills/` 管理 + 啟動 agent 時預裝至容器 |

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

## 里程碑 4：主代理對話（優先級「main-agent 跟使用者對話」）

**目標。** `grimo chat` 啟動主代理多輪對話 session，使用 agent-client SDK 的 `ClaudeAgentSessionRegistry` 與主機 claude CLI 互動。
**完成條件。** S007 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S007 | 主代理 CLI 對話（`grimo chat`） | XS (7) | ⏳ Design |

### S007 — 主代理 CLI 對話 · XS (7)

**描述。** Spring Boot `ApplicationRunner` 偵測到 `chat` 子命令。使用 `ClaudeAgentSessionRegistry`（agent-client 0.12.2）建立 `AgentSession`，維持持續運行的主機 claude CLI process。`AgentSession.prompt()` 實現多輪對話。終端 REPL 讀取使用者輸入、印出回應。`/exit` 或 `Ctrl+D` 結束 session。**MVP 不持久化** — 每次 `grimo chat` 均為全新 Session。容器化（Docker、wrapper script、CredentialResolver）延後至後續 spec。

**依賴。** 無程式碼層級依賴。agent-client 0.12.2 為 library dependency。

**SBE（草稿）。**
- **AC-1** `./gradlew bootRun --args='chat'` 啟動互動式 Session；輸入「hello」收到 claude 的回應；可繼續多輪對話。
- **AC-2** `/exit` 或 `Ctrl+D` 乾淨退出，回到主機 shell。
- **AC-3** 若 claude CLI 未安裝，印出清楚的安裝指引並以非零狀態退出 — 不顯示堆疊追蹤。

**估算。** 技術 2 · 不確定性 1 · 依賴 1 · 範疇 1 · 測試 1 · 可逆性 1 = **7 / XS**

---

## 里程碑 5：容器化 CLI 對話（優先級「在 Docker 中跑各 CLI」）

**目標。** 逐一驗證 Claude Code / Gemini / Codex 在 `grimo-runtime` 容器中透過 agent-client SDK 對話。建立 CredentialResolver（macOS Keychain 提取）、WrapperScriptGenerator env var 注入、容器化 `AgentSession` 整合。
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

## 里程碑 6：Session 持久化（優先級「儲存對話 session」）

**目標。** 對話歷史存入 H2，使用者可用 `grimo chat --resume <id>` 恢復先前 session。
**完成條件。** S011 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S011 | Session 持久化 | S (10) | ⏳ Design |

### S011 — Session 持久化 · S (10)

**描述。** 透過 decorator 模式包裝 `AgentSession` / `AgentSessionRegistry`（agent-client 0.12.2 SPI），在每輪 `prompt()` 前後將 user/assistant 訊息寫入 H2（via `SessionService`，spring-ai-session-jdbc 0.2.0）。`grimo chat --resume <id>` 恢復先前 session。不含壓縮策略選型（另行研究 spec）。

**設計要點。** 研究確認 `SessionMemoryAdvisor` 為 ChatClient advisor，無法掛載到 `AgentSession`（不同 API 層次）。改用 decorator：`PersistentAgentSession` 包裝任意 `AgentSession`，`PersistentAgentSessionRegistry` 包裝任意 `AgentSessionRegistry`，以 `@Primary` bean 透明替換。

**競品參考。** T3 Code Event Sourcing（sequence-based partial replay，§3.1）+ Hermes Agent 四層記憶架構（§6.3.3）。

**依賴。** S007（程式碼層級 — 需修改 REPL 加入 `--resume` 旗標）。

**SBE（草稿）。**
- **AC-1** `grimo chat` 對話後，`AI_SESSION_EVENT` 表中存在該 session 的事件記錄。
- **AC-2** `grimo chat --resume <sessionId>` 恢復先前對話；agent 回應參考先前上下文。
- **AC-3** 無效或不存在的 `sessionId` 印出清楚錯誤，不崩潰。
- **AC-4** `~/.grimo/db/` 目錄在首次使用時自動建立（via `GrimoHomePaths.db()`）。

**估算。** 技術 2 · 不確定性 1 · 依賴 2 · 範疇 2 · 測試 2 · 可逆性 1 = **10 / S**

---

## 里程碑 7：Skill 管理 + 注入（優先級「管理並預裝 skill」）

**目標。** Grimo 管理 `~/.grimo/skills/` 下的 Skill；啟動容器化 agent 時，將已啟用 Skill 掛載至容器的 CLI 原生 skill 路徑。
**完成條件。** S012、S013 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S012 | Skill 登錄檔 | XS (8) | 🔲 |
| S013 | Skill 預裝至 Agent 容器 | S (11) | 🔲 |

### S012 — Skill 登錄檔 · XS (8)

**描述。** 使用 JDK NIO 掃描 `~/.grimo/skills/*/SKILL.md`。建立 `SkillRegistryUseCase`，公開 `list()`、`enable(name)`、`disable(name)`、`get(name) → Optional<Skill>`。啟用狀態持久化至 `~/.grimo/skills/.state.json`。

**競品參考。** Hermes Agent Closed Learning Loop 觸發條件（§6.3.2）+ Claude Code `.claude/skills/` 結構。

**依賴。** S001（`GrimoHomePaths.skills()`）。

**SBE（草稿）。**
- **AC-1** `~/.grimo/skills/hello/SKILL.md` 中的 Skill 出現在 `list()` 中。
- **AC-2** 無效格式的 SKILL.md 記錄警告並跳過（不崩潰）。
- **AC-3** `disable("hello")` 持久化；重啟後 `list()` 仍標記為已停用。

**估算。** 技術 1 · 不確定性 1 · 依賴 2 · 範疇 2 · 測試 1 · 可逆性 1 = **8 / XS**

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

---

## 摘要（MVP）

| 里程碑 | 規格 | 點數 |
| --- | --- | --- |
| M0 基礎建設 | S000、S001、S002 | 23 |
| M1 容器操作 | S003 | 13 |
| M2 容器化 CLI | S004、S005 | 24 |
| M3 CLI 配置 | S006 | 11 |
| M4 主代理對話 | S007 | 7 |
| M5 容器化 CLI 對話 | S008、S009、S010 | 29 |
| M6 Session 持久化 | S011 | 11 |
| M7 Skill 管理 + 注入 | S012、S013 | 19 |
| **合計** | **14 個規格** | **137 點** |

v3 藍圖相較 v2（13 規格 / 138 點）差異極小（+1 規格 / -1 點）。核心變化在**驗證順序**：先逐一 CLI 容器化驗證 → session 持久化 → skill 管理+注入，再處理複雜的任務委派。

下一步行動：`/planning-tasks S007`

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
| `AutoMemoryTools` 接線 | v1 S017 | S011 持久化 session 上線後再考慮。 | M (12) |
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
