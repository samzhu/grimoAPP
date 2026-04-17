# Grimo — 規格藍圖（v2 · 重新排序於 2026-04-16）

**狀態：** v0.2 · **日期：** 2026-04-16
**來源：** `docs/grimo/PRD.md` + `architecture.md`

> **重新規劃說明。** 本藍圖於 2026-04-16 重寫，將 MVP 聚焦於單一垂直切片驗證：**容器操作 → 容器化 CLI → CLI 配置 → 主代理對話 → 任務派送 → Skill 管理 → Skill 注入至子代理**。其他所有功能（Web UI、持久化 Session、CLI 切換、成本路由、評審團、記憶體、原生加固、E2E 測試套件……）均移至下方 Backlog。當 Backlog 項目被晉升時，會取得新的規格 ID 並重新進行 grill-me 循環。

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
                       S006（CLI 配置研究 + 策略）
                         │
                         ▼
                       S007（主代理 CLI 直通）
                         │
                         ▼
                       S008（派送協議）─┐
                         │              │
                         ▼              │
                       S009（工作樹管理）│
                         │              │
                         ▼              │
                       S010（子代理生命週期）
                                        │
                       S011（Skill 登錄檔）◄──┘
                         │
                         ▼
                       S012（Skill → 容器安裝）
```

## 里程碑地圖（MVP）

| M# | 名稱 | 優先級（使用者） | 規格 | 目標 |
| --- | --- | --- | --- | --- |
| M0 | 基礎建設 | — | S000–S002 | 可建置的 Spring Boot 4.0 模組化骨架，運行於 JDK 25 |
| M1 | 容器操作 | 能操作容器 | S003 | Grimo 可從 Java 啟動 / exec / bind-mount / 停止 Docker 容器 |
| M2 | 容器化 CLI | 能在容器內用 3 個 CLI | S004–S005 | `grimo-runtime` 映像內建 `claude` + `codex` + `gemini`；Java 適配器透過 `docker exec` 呼叫 |
| M3 | CLI 配置 | 研究 CLI 配置 | S006 | Claude-Code 記憶體關閉、各提供者配置慣例套用至所有容器化呼叫 |
| M4 | 主代理對話 | main-agent 跟使用者對話 | S007 | `grimo chat` → 容器化 claude-code ↔ 使用者終端（CLI 直通） |
| M5 | 任務派送 | 派送任務給 sub-agent | S008–S010 | 主代理結構化委派；Grimo 建立帶工作樹的子代理容器；將 diff 返回給使用者 |
| M6 | Skill 管理 | 管理 skill | S011 | Grimo 列出/啟用/停用 `~/.grimo/skills/` 下的 Skill |
| M7 | Skill 注入 | 派送前安裝 skill 到 sub-agent | S012 | 任務執行前將相關 Skill 複製至子代理容器 |

---

## 里程碑 0：基礎建設 ✅（2026-04-16）

3/3 規格完成（S000 + S001 + S002）。詳見 `specs/archive/2026-04-16-S00[0-2]-*.md`。

---

## 里程碑 1：容器操作 ✅（2026-04-17）

1/1 規格完成（S003）。詳見 `specs/archive/2026-04-17-S003-sandbox-bind-mount-adapter.md`。

---

## 里程碑 2：容器化 CLI（優先級「能在容器內用 3 個 CLI」）

**目標。** 由 Grimo 管理的 Docker 映像內建 `claude-code`、`codex` 與 `gemini` CLI；Java 適配器透過 `docker exec` 呼叫每個 CLI。
**完成條件。** S004、S005 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S004 | 預安裝 3 個 CLI 的 `grimo-runtime` Docker 映像 | S (10) | ✅ |
| S005 | 透過 `docker exec` 的 `AgentCliAdapter`（容器化適配器） | M (12) | 🔲 |

### S004 — `grimo-runtime` Docker 映像 · S (10)

**描述。** `docker/runtime/` 下的 Dockerfile 產生一個映像，基於 `node:20-slim`（Debian/glibc），安裝三個 CLI 代理工具：claude-code（curl 原生安裝器）、codex（`@openai/codex` npm）、gemini（`@google/gemini-cli` npm）——三者均為 Node.js / 原生二進位工具，不需要 Python 或 Google Cloud SDK。透過 `docker build --tag grimo-runtime:<version> docker/runtime/` 在本地建置。在 Dockerfile 旁的 README 中記錄精確的安裝命令與版本。

**依賴。** S003。

**SBE（草稿）。**
- **AC-1** `./gradlew buildRuntimeImage` 成功並在本地標記 `grimo-runtime:<version>`。
- **AC-2** `docker run --rm grimo-runtime:<version> claude-code --version` 印出版本號。
- **AC-3** `codex --version` 與 `gemini --version` 同上。
- **AC-4** 映像大小 < 1 GB（軟性目標；已記錄）。

**估算。** 技術 2 · 不確定性 2 · 依賴 2 · 範疇 2 · 測試 1 · 可逆性 1 = **10 / S**

### S005 — 透過 `docker exec` 的 `AgentCliAdapter` · M (12)

**描述。** 實作 `AgentCliPort`，提供 `stream(SpawnSpec, Prompt): Flux<Token>`。預設實作使用 S003 的 `Sandbox`（`agent-sandbox-core` SPI）啟動 `grimo-runtime` 容器，再以 `docker exec -i <container> <cli> ...` 導入 prompt，串流擷取 stdout。透過 `ProviderId` 支援三個提供者（CLAUDE / CODEX / GEMINI）。測試用存根實作 `StubAgentCliAdapter`。

**依賴。** S004。

**SBE（草稿）。**
- **AC-1** `StubAgentCliAdapter.stream("hello", CLAUDE)` 回傳含預設 token 的 `Flux<Token>`，供測試使用。
- **AC-2** 針對本地運行的 `grimo-runtime` 容器的真實適配器，通過手動整合測試（無 Docker 的 CI 環境中跳過）。
- **AC-3** 缺少 CLI（例如映像中未安裝 CLI 二進位檔）時，顯示清楚的 `CliNotFoundException`，而非原始的 docker-exec stderr 輸出。

**估算。** 技術 2 · 不確定性 3 · 依賴 3 · 範疇 2 · 測試 2 · 可逆性 2 = **14 / M**

---

## 里程碑 3：CLI 配置（優先級「研究 CLI 配置」）

**目標。** 記錄各 CLI 的配置介面；將 Grimo 的框架策略（例如 Claude-Code 記憶體關閉）套用至每個容器化呼叫。
**完成條件。** S006 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S006 | CLI 配置研究 + 框架策略套用 | S (11) | 🔲 |

### S006 — CLI 配置研究 + 策略 · S (11)

**描述。** 第一階段：以 WebFetch 查詢各 CLI 配置的官方文件（環境變數、設定檔、CLI 旗標），將研究結果記錄於 `docs/grimo/cli-config-matrix.md`。第二階段：將 Grimo 的策略表達為可重用的 `CliInvocationOptions` record，由各適配器在 `docker exec` 時套用。MVP 中的具體策略：**Claude-Code 記憶體停用**（不自動讀取 CLAUDE.md，不使用專案層級記憶體）、**API 金鑰 / 憑證儲存從主機傳遞**（只讀掛載 `~/.claude` / `~/.codex` / `~/.gemini`）、**停用遙測**（若各 CLI 有此開關）。

**依賴。** S005。

**SBE（草稿）。**
- **AC-1** `docs/grimo/cli-config-matrix.md` 存在，列出三個 CLI 的所有配置介面，並附有官方文件 URL 連結。
- **AC-2** 使用 S005 以 `harness=true` 呼叫 claude-code 時，產生的容器已停用記憶體（透過執行已知的記憶體觸發 prompt 並確認回應中無記憶體擷取來斷言）。
- **AC-3** 若主機上缺少憑證目錄，適配器不會崩潰；顯示清楚的 `CredentialsNotFoundException`。

**估算。** 技術 2 · 不確定性 3 · 依賴 2 · 範疇 2 · 測試 1 · 可逆性 1 = **11 / S**

---

## 里程碑 4：主代理對話（優先級「main-agent 跟使用者對話」）

**目標。** `grimo chat` 在使用者終端與充當主代理的容器化 `claude-code` 之間開啟 CLI 直通。
**完成條件。** S007 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S007 | 主代理 CLI 直通（`grimo chat`） | S (10) | 🔲 |

### S007 — 主代理 CLI 直通 · S (10)

**描述。** Spring Boot 主類進入點偵測到 `chat` 子命令。使用 S006 的框架配置啟動運行 `claude-code` 的 `grimo-runtime` 容器。將使用者的 stdin 導入容器 stdin；將容器 stdout 串流逐行輸出至使用者 stdout。按 `Ctrl+D` 或 `/exit` 結束 Session。**MVP 不持久化** — 每次 `grimo chat` 均為全新 Session。

**依賴。** S006。

**SBE（草稿）。**
- **AC-1** 執行 `./build/libs/grimo-<v>.jar chat`（或 `./gradlew bootRun --args='chat'`）啟動互動式 Session；輸入「hello」收到 claude-code 的回應。
- **AC-2** `/exit` 或 `Ctrl+D` 乾淨地停止容器並返回主機 shell。
- **AC-3** 若 Docker 未運行，Grimo 印出清楚的「Start Docker Desktop」訊息並以非零狀態退出 — 不顯示堆疊追蹤。

**估算。** 技術 2 · 不確定性 2 · 依賴 2 · 範疇 2 · 測試 1 · 可逆性 1 = **10 / S**

---

## 里程碑 5：任務派送（優先級「派送任務給 sub-agent」）

**目標。** 主代理宣告結構化委派；Grimo 建立帶工作樹的子代理容器；子代理在沙盒中以 YOLO 模式執行 CLI；將 diff 返回給使用者。
**完成條件。** S008、S009、S010 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S008 | 委派協議（主代理 → Grimo） | M (12) | 🔲 |
| S009 | 工作樹管理員（JGit） | S (9) | 🔲 |
| S010 | 子代理生命週期 + diff 審查 | M (13) | 🔲 |

### S008 — 委派協議 · M (12)

**描述。** 定義主代理如何發送「委派此任務」的信號。MVP 候選方案（規格規劃時需進行 grill-me）：(a) 主代理呼叫的 MCP 工具，由 Grimo 攔截；(b) Grimo 解析的主代理輸出中的結構化 JSON 片段；(c) 明確的使用者命令 `grimo delegate "…"`。在 grill-me 中選擇其一。產生下游規格所使用的 `TaskSpec` 領域 record。

**依賴。** S007。

**SBE — 草稿，規格規劃時細化。**
- **AC-1** 主代理回應中已知的委派觸發器產生 `TaskSpec` 事件 `TaskDelegated`。
- **AC-2** 無效/格式錯誤的觸發器顯示使用者可見的錯誤，而非靜默丟棄。

**估算。** 技術 2 · 不確定性 3 · 依賴 2 · 範疇 2 · 測試 2 · 可逆性 2 = **13 / M**

### S009 — 工作樹管理員 · S (9)

**描述。** `WorktreePort.create(TaskId, sourceBranch) → Worktree` 與 `.drop(TaskId)`。使用 JGit `Git.open(repo).worktreeAdd()` 建立至 `~/.grimo/worktrees/<taskId>/`。啟動時清掃移除孤立的工作樹。

**依賴。** S001（使用 `TaskId`）。

**SBE（草稿）。**
- **AC-1** `WorktreePort.create(taskId, "main")` 建立帶有正確 HEAD 的分離工作樹目錄。
- **AC-2** `WorktreePort.drop(taskId)` 移除目錄並取消登錄工作樹。
- **AC-3** 啟動時清掃移除未知任務 ID 的過時工作樹。

**估算。** 技術 1 · 不確定性 1 · 依賴 2 · 範疇 2 · 測試 2 · 可逆性 1 = **9 / S**

### S010 — 子代理生命週期 + diff 審查 · M (13)

**描述。** `DelegateTaskUseCase.execute(TaskSpec)`：
1. 保留子代理槽位（受 `grimo.subagent.max-concurrent` 限制，預設 2）。
2. 建立工作樹（S009），建立帶工作樹 bind-mount 的沙盒（S003）。
3. 透過 S005 以 **YOLO 模式** 執行內部 CLI（沙盒內完整寫入允許清單）。
4. 擷取 `git diff /work` 輸出。
5. 將 diff 印給使用者；按 `y` → 將工作樹分支合併回去；按 `n` → 捨棄工作樹。

**依賴。** S003、S005、S008、S009。

**SBE（草稿）。**
- **AC-1** 委派一個編輯檔案的任務，主機上工作樹內的檔案被修改。
- **AC-2** 相鄰的子代理無法存取第一個工作樹。
- **AC-3** 接受後，變更合併至主機 repo；拒絕後，主機 repo 不變。

**估算。** 技術 2 · 不確定性 2 · 依賴 3 · 範疇 2 · 測試 3 · 可逆性 2 = **14 / M**

---

## 里程碑 6：Skill 管理（優先級「main-agent 管理 skill」）

**目標。** Grimo 列出、啟用並從 `~/.grimo/skills/` 載入 Skill。
**完成條件。** S011 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S011 | Skill 登錄檔 + 啟用/停用命令 | S (10) | 🔲 |

### S011 — Skill 登錄檔 · S (10)

**描述。** 使用 JDK NIO + `YAMLFactory` 掃描 `~/.grimo/skills/*/SKILL.md` 的 frontmatter。建立 `SkillRegistryUseCase`，公開 `list()`、`enable(name)`、`disable(name)`、`get(name) → Optional<Skill>`。Skill 在啟動時發現，也可按需發現（`list()` 重新掃描）。啟用狀態持久化至 `~/.grimo/skills/.state.json`。

**依賴。** S001。

**SBE（草稿）。**
- **AC-1** `~/.grimo/skills/hello/SKILL.md` 中的 Skill 出現在 `list()` 中。
- **AC-2** 無效的 frontmatter 記錄警告並跳過該 Skill（不崩潰）。
- **AC-3** `disable("hello")` 持久化；重啟後，`list()` 仍標記為已停用。

**估算。** 技術 2 · 不確定性 2 · 依賴 2 · 範疇 2 · 測試 1 · 可逆性 1 = **10 / S**

---

## 里程碑 7：Skill 注入至子代理（優先級「派送前安裝 skill」）

**目標。** 在子代理容器運行前，Grimo 將主機 `~/.grimo/skills/` 中相關的已啟用 Skill 複製至容器，使內部 CLI 透過其原生 Skill 路徑取得。
**完成條件。** S012 ✅。

| # | 規格 | 點數 | 狀態 |
| --- | --- | --- | --- |
| S012 | Skill 注入至子代理容器 | M (12) | 🔲 |

### S012 — Skill 注入 · M (12)

**描述。** 當 `DelegateTaskUseCase`（S010）建立子代理容器時：
1. 查詢 `SkillRegistryUseCase.listEnabled()`。
2. 過濾與任務規格相關的 Skill（MVP：全部已啟用；更精細的過濾為 Backlog）。
3. 對每個選定的 Skill，將 Skill 目錄複製至子代理容器的 **CLI 原生 Skill 路徑** — claude-code → `/root/.claude/skills/<name>/`，codex 與 gemini 依 S006 調查的文件。
4. 在執行任務前，確認 Skill 對 CLI 可見。

**依賴。** S006（各 CLI 的 Skill 路徑）、S010、S011。

**SBE（草稿）。**
- **AC-1** 啟用一個 Skill `hello` 後，子代理容器啟動（claude-code 提供者），容器內存在 `/root/.claude/skills/hello/SKILL.md`。
- **AC-2** 任務執行在 prompt 中引用該 Skill，並產生符合 Skill 指令的輸出（使用夾具 Skill 斷言）。
- **AC-3** 已停用的 Skill 不被注入。

**估算。** 技術 2 · 不確定性 3 · 依賴 3 · 範疇 2 · 測試 2 · 可逆性 2 = **14 / M**

---

## 摘要（MVP）

| 里程碑 | 規格 | 點數 |
| --- | --- | --- |
| M0 基礎建設 | S000、S001、S002 | 23 |
| M1 容器操作 | S003 | 13 |
| M2 容器化 CLI | S004、S005 | 24 |
| M3 CLI 配置 | S006 | 11 |
| M4 主代理對話 | S007 | 10 |
| M5 任務派送 | S008、S009、S010 | 36 |
| M6 Skill 管理 | S011 | 10 |
| M7 Skill 注入 | S012 | 14 |
| **合計** | **13 個規格** | **141 點** |

與重新規劃前的 v1 藍圖（23 個規格 / 269 點）相比，MVP 範疇縮減約 48%，聚焦於「容器化代理編排與使用者管理 Skill」的單一垂直驗證。所有其他功能均可用，但暫時擱置。

下一步行動：`/planning-spec S002`（S001 已在設計中）。

---

## Backlog

以下項目原屬 v1 MVP 藍圖，現已**延後，直至明確晉升**。每個項目晉升時將使用新的規格 ID 重新進行 grill-me 循環；以下估算為 v1 遺留值，晉升時將重新評估。

| 能力 | 先前規格參考（v1） | 延後原因 | 粗略工作量 |
| --- | --- | --- | --- |
| 持久化 Session（spring-ai-session-jdbc + H2） | 舊 S005 | 驗證 MVP 垂直切片不需持久化。MVP 接受每次 `grimo chat` 均為全新 Session。 | M (12) |
| Web UI（Thymeleaf + HTMX + SSE） | 舊 S003 + S006 | CLI 直通為 MVP 介面。需要展示 UI 時再晉升。 | M×2 (23) |
| 帶壓縮重放的 CLI 切換 | 舊 S009 | MVP 主代理僅使用 Claude-Code；多 CLI 主代理為後期考量。 | L (15) — 需手動 QA |
| Codex / Gemini 主代理角色 | 舊 S007、S008 | MVP 主代理僅使用 Claude。Codex/Gemini CLI 已透過 S004 包含在 `grimo-runtime` 映像中供子代理使用。 | S×2 (20) |
| 明確的主代理唯讀工具允許清單 | 舊 S010 | 容器隔離 + S006 CLI 配置已在 MVP 中抵消主代理的寫入路徑。當主代理在容器外運行或需要更細緻的工具管控時再重新審視。 | S (9) |
| 成本路由器（啟發式 v1） | 舊 S014 | MVP 不進行路由；主代理 = claude-code，子代理 = 主代理在映像中的選擇。 | S (9) |
| 評審團（N 路並行審查） | 舊 S015 | 次要功能；需要多 CLI + 比較 UI。 | M (13) |
| `AutoMemoryTools` 接線 | 舊 S017 | MVP 每次 Chat Session 無狀態運行。 | M (12) |
| Skill 蒸餾提案者 | 舊 S018 | 框架層級的自動演化；僅在手動 Skill 管理循環（S011/S012）累積足夠使用時間後晉升。 | M (14) |
| 成本遙測面板 + `Cost` 領域型別 | 舊 S019 | 等待成本路由器。`Cost` 由擁有它的規格引入（`core.domain` 中無 `Cost`）。 | XS (8) |
| 模組邊界 CI 任務 | 舊 S020 | S002 的 JUnit ArchUnit 測試已在測試中覆蓋；專用 CI 任務為奢侈品。 | XS (7) |
| 原生映像加固（`RuntimeHints`、`ProcessBuilderSandboxAdapter`、夜間冒煙測試） | 舊 S021a–c | PRD D3 的擴展目標。MVP 優先使用 JVM。 | M×2 + XS (33) |
| E2E 整合測試套件 | 舊 S022 | 垂直切片有使用者可見行為值得回歸測試時晉升。 | S (10) |

**Backlog 策略。** 項目不得插隊。使用者晉升項目時，以全新 grill 循環重新進入 `/planning-spec`（請勿盲目重用 v1 草稿驗收標準 — 環境將已改變）。
