# Grimo — 產品需求文件（PRD）

**狀態：** v0.1 草稿 · **負責人：** samzhu · **日期：** 2026-04-16
**下一步交接：** `/planning-project`

---

## 1. 問題陳述

開發者日益頻繁地使用**多個 CLI AI 代理**（Claude Code、Codex CLI、Gemini CLI）。實際上，這帶來四個痛點：

1. **成本不可控。** 每個任務 — 無論是瑣碎的格式調整、一行式問題，還是策略性重構 — 都使用相同的頂級模型。簡單工作消耗昂貴的 tokens。
2. **每個答案都只是一個模型的觀點。** 程式碼審查、架構決策和除錯受益於對比觀點。單一 CLI 只帶來單一盲點。
3. **管理分散。** 每個 CLI 各自擁有 skill 目錄、MCP server 配置、session 佈局和允許清單語法。想讓「這個 skill 在三個 CLI 上都可用」的使用者，今天只能手動複製檔案。
4. **核准疲勞（Approval fatigue）。** 現代 CLI 代理在每次檔案編輯、shell 執行和網路呼叫時都要求人工核准。在實際重構中，這意味著一行一行的 Y/N 確認；人類變成蓋橡皮章的機器，安全機制淪為噪音。關掉核准是用安全換人體工學 — 這是壞選擇。正確答案是**把危險放進箱子裡**，而不是去掉安全檢查。

這些痛點相互疊加：使用者標準化在最先試用的 CLI 上，並為此永遠付出代價 — 無論是 token、品質，還是注意力。

## 2. 解決方案 — *Grimo*，一個使用者外殼

> *外殼（Harness）= 圍繞模型的控制、契約與狀態。**代理（Agent）** = `Model + Harness`。**使用者外殼** 是團隊圍繞現成代理組裝的那個部分 — 與烘焙進每個 CLI 的建置者外殼相對。*（見 `glossary.md`。）*

Grimo 是一個本地優先的 CLI AI 代理**使用者外殼**，基於 Spring 生態系統構建。它位於 Claude Code / Codex / Gemini CLI 前面，為使用者提供：

- **一個對話，任何 CLI。** `main-agent` 角色（對話進入點），其底層 CLI 可在 session 中途切換而不丟失上下文。
- **封箱執行（Delegated execution in a box）。** 寫入密集型工作委派給在 Docker 中、帶有每任務 git worktree 的 `sub-agent` 執行。主代理本身**唯讀**（Read / Glob / Grep / WebFetch / WebSearch）。
- **成本感知路由。** 路由器選擇適合任務類別的最便宜 CLI/模型 — 使用者隨時可覆寫。
- **多視角審查。** *jury* 命令將單一審查任務並行派送給 N 個 CLI，聚合共識 + 分歧。
- **統一的 skills、memory 與 sessions。** Skills 和 memory 存放於 `~/.grimo/`，並投影至每個 CLI 的原生介面。
- **自我演化。** 在某個重複模式成功執行 N 次後，Grimo 蒸餾出 `SKILL.md` 草稿供使用者審核。Memory 由代理本身透過 `AutoMemoryTools` 整理。

## 3. 定位

| Grimo **是** | Grimo **不是** |
| --- | --- |
| 使用者圍繞現有 CLI 代理組裝的外殼 | 新的 AI 模型、CLI 或聊天客戶端 |
| 帶有 Spring-Modulith 邊界的輕量程序監控編排器 | Python 代理框架或 LangChain 的替代品 |
| 本地優先、單使用者開發者工具 | 多租戶 SaaS 或消費者訊息機器人 |
| 對成本、隔離和視角有主見 | 「什麼都做」的無主見 shell |

## 4. 使用者與場景

### 目標使用者

- 已安裝 **≥ 1 個 CLI 代理**（Claude Code、Codex CLI 或 Gemini CLI），且熟悉終端和 Docker 的開發者。
- 想要**節省費用** — 簡單工作用便宜的模型，昂貴的模型保留給策略性工作。
- 想要**多視角** — 多個代理並行審查 / 研究單一產物。
- **不**想手動管理多個 CLI 視窗。
- 訂閱了**付費計劃**（Claude Max、ChatGPT Plus / Codex、Gemini Advanced），並期望 Grimo 使用每個 CLI 的原生 OAuth 流程 — **無需 API 金鑰**。（見 D19。）

**主要使用者畫像：** 一位能輕鬆閱讀英文 PRD、用繁體中文撰寫摘要的資深開發者；將本地優先工具視為隱私 / 低延遲的優勢。

代表性場景：

- 「瑣碎雜務」— 重命名變數、格式化時間戳、搜索 codebase。應在最便宜的可用 CLI 上執行。
- 「範疇重構」— 重寫一個服務類別、更新其測試、執行測試套件。應在帶有完整工具存取權的隔離 worktree 中執行。
- 「架構審查」— 要求三個 CLI 批評一份設計文件，並展示它們同意和分歧的地方。
- 「跨 CLI 連續性」— 在 `claude` 上開始了一段對話，配額耗盡，想在 `codex` 上繼續而不需要重新解釋上下文。
- 「只安裝我擁有的」— 使用者 PATH 中只有 `claude`；啟動 Grimo 必須成功，`codex`/`gemini` 功能必須在**呼叫時**失敗，而非在啟動時。

## 5. 核心原則（含理由）

### P1 — 外殼，不是代理，不是框架（*包裝 CLI agent，不取代它們*）

Grimo 不發明新的代理。它不取代 LangChain。它將外殼工程準則（控制 + 契約 + 狀態、指導 + 感測器、計算型 + 推理型控制）應用於已存在的 CLI 代理。

*為何如此：* 代理的核心價值存在於 LLM + 工具循環，而這些循環在 Claude Code / Codex CLI / Gemini CLI 中已經有效運作。重建它們既昂貴又毫無意義。價值在於圍繞它們的組裝。

### P2 — 讀寫非對稱（*規劃與執行分離 · 隔離取代管控*）

主代理**唯讀**（規劃、閱讀、搜索、提問）。寫入只透過在 Docker 中帶有掛載 worktree 的**子代理**進行，子代理在箱子內的主機權限有意為 YOLO。

*為何如此 — 三個相互印證的理由：*
1. **核准疲勞**（痛點 #4）。當每次寫入都需要 Y/N 確認時，人類會蓋橡皮章或停用檢查。沙箱消除了每次操作確認的成本效益計算：爆炸半徑已被箱子限制，因此核准可以移至任務末尾單一的 diff 層級檢查點。**隔離取代細粒度權限控制** — 複雜的允許清單維護成本高且容易出錯；在 Docker 中的 bind-mounted worktree 成本低且明確無誤。
2. **規劃/執行分離。** 規劃受益於強推理（昂貴模型、唯讀、長上下文）。執行受益於精確和速度（較便宜的模型、可寫入、短暫）。分離它們讓每個使用它應該使用的模型。
3. **安全 + 成本結構控制。** 讀取操作便宜且非破壞性；適合長時間運行的對話上下文。寫入需要隔離，防止行為異常的代理損壞主機 repo。這符合外殼工程「計算控制」原則 — 讓危險路徑在結構上更難以走到。

### P3 — Grimo 擁有 session

Session 是由 **Grimo 擁有**的一等領域概念，以事件源的 `Message`（透過 Spring AI 的 `SessionMemoryAdvisor`）形式儲存。它不屬於任何單一 CLI。

*為何如此：* agent-client 0.12.2 的 `AgentSession` 目前僅支援 Claude；跨 CLI 可移植的對話無法委派給它。擁有 session 讓 Grimo 在 CLI 切換時可以壓縮並重放歷史。

### P4 — 帶事件驅動接縫的模組化單體

限界上下文是 Spring Modulith 的 `@ApplicationModule`。跨模組通訊透過 `ApplicationEventPublisher` + `@ApplicationModuleListener` 進行。領域核心保持無 Spring 注解（純 records 和介面）。

*為何如此：* 六邊形 + 模組化邊界提供可測試的接縫和廉價的未來解耦（例如，將子代理池改為程序外執行），同時保持 v1 為單一可部署單元。

### P5 — 缺少 CLI 時軟失敗

當 `claude` / `codex` / `gemini` 的任意子集從 `PATH` 缺失時，Grimo **必須**成功啟動。每個提供者的 `agent-*` starters 受 `@ConditionalOnProperty` + 明確選擇加入的控制。缺少的 CLI 在**呼叫時**檢測，而非刷新時。

*為何如此：* 多 CLI 工具上的 #1「新使用者」失敗模式是某個二進位未安裝時的啟動時爆炸。絕不讓這種情況發生。

### P6 — 自我演化是分層的，不是魔法

v1 自我演化 = `AutoMemoryTools`（代理整理的記憶）+ skill 蒸餾（在成功的模式重複後，Grimo 提案 `SKILL.md` 草稿供使用者審核）。MVP 中無自主規則調整。

*為何如此：* 閉環自我改進（類似 Hermes）是真實的，但提案 → 審核 → 提交閘門讓人類保持控制，防止漂移。更豐富的形式（自動調整指導、RL 風格感測器）延後處理。

### P7 — 本地優先

所有狀態存放於 `~/.grimo/`。MVP 中無雲端後端、無帳號系統、無遙測上傳。

*為何如此：* 開發者工具存在於筆記型電腦上。本地優先消除了大類隱私/合規問題，並將延遲保持最低。

### P8 — 統一管理，原生分發

使用者在 Grimo 的統一格式中**一次**配置 skill / MCP server / memory / guide。在呼叫時，Grimo 將該配置投影至每個 CLI 的原生介面（Claude Code 的 `.claude/skills/`、Gemini 的 skill 目錄、Codex 的配置等）。

*為何如此：* 使用 N 個代理不應帶來 N 倍的管理成本。統一的撰寫格式是可擴展的東西；原生分發是讓 CLI 本身保持高興的東西。

### P9 — 跨 agent 即韌性

Grimo 的多 CLI 支援不是一項功能 — 它*就是*韌性故事。任何提供者中斷、配額耗盡、價格變動或棄用，都只會將 Grimo 降級為「在這個提供者恢復之前改用其他 CLI」，而不是「Grimo 停機」。

*為何如此：* 讓團隊鎖定在單一提供者上是供應商鎖定和可用性風險。我們為成本路由和多視角審查已經需要的多 CLI 架構，同時也是最好的備份計劃 — 不需要維護冷備基礎設施。

### P10 — 訂閱帳號原生認證

訂閱了 Claude Max / ChatGPT Plus / Gemini Advanced 的使用者，必須能夠不建立、管理或輸入任何 API 金鑰地端對端驅動 Grimo。Grimo 將所有認證委派給底層 CLI 自己的 OAuth/登入流程。

*為何如此：* 我們的目標使用者已經支付月費訂閱。要求他們同時取得並儲存 API token（除訂閱外另外按 token 計費）是雙重收費和 UX 懸崖。直接使用 CLI 繼承使用者以 `claude login` / `gemini auth` / `codex login` 設定的任何認證。

## 6. SBE 驗收標準

*具體、可執行的範例。如果以下任何一項未通過，MVP 即未出貨。*

### AC1 — 成本路由為簡單工作選擇便宜的模型

```
Given  使用者 prompt「將這個 ISO-8601 時間戳格式化為本地時間」
When   主代理接收到它
Then   路由器將任務分類為「trivial/format」
And    派送至 Gemini 2.5 Flash（非 Claude Opus）
And    session 成本面板顯示該輪次的 delta ≤ $0.0005
```

### AC2 — 成本路由為策略性工作升級

```
Given  使用者 prompt「將 OrderService 重構為六邊形架構
       並更新其 Spring Modulith 模組宣告」
When   主代理接收到它
Then   路由器將任務分類為「strategic/refactor」
And    派送至 Claude Opus 4.6 或 Claude Sonnet 4.6
And    session 成本面板記錄輪次成本
And    對該輪次附加一個使用者可見的理由字串
       （「升級：結構性重構，多檔案」）
```

### AC3 — CLI 切換保留對話上下文

```
Given  使用者已與 main-agent = claude 進行了 5 輪對話
And    在那些輪次中討論了一個名為「OrderService」的實體
When   使用者執行「/grimo switch codex」
And    然後發送「你能也為它加一個取消端點嗎？」
Then   Grimo 將 5 個先前輪次壓縮為引導 prompt
And    一次呼叫將 <bootstrap>+<新使用者訊息> 傳遞給 codex
And    codex 的回應在未被告知的情況下自然引用 OrderService
```

### AC4 — 主代理無法寫入

```
Given  主代理正在運行（任何 CLI）
When   主代理嘗試呼叫寫入工具（Edit / Write / Bash with mutation）
Then   外殼攔截該呼叫
And    回傳結構化錯誤「main-agent is read-only; delegate to sub-agent」
And    主機檔案系統未被修改
And    錯誤在對話日誌中對使用者可見
```

### AC5 — 子代理只在其 worktree 內寫入

```
Given  主代理委派一個編輯 src/.../Foo.java 的任務 T1
When   Grimo 在 Docker 沙箱中建立子代理 T1
And    在容器內將每任務 worktree 掛載至 /work
Then   子代理 T1 可在 /work 下自由 Read/Write/Bash
And    無法讀取或寫入 /work 以外的任何主機路徑
And    兄弟子代理 T2 看不到 T1 的 worktree
And    T1 完成時，Grimo 在合併前向使用者呈現 diff 供審查
```

### AC6 — 缺少 CLI 時軟失敗啟動

```
Given  主機安裝了 `claude` 但未安裝 `codex` 或 `gemini`
When   `./gradlew bootRun` 啟動 Grimo
Then   應用上下文在 N 秒內成功刷新
And    Web UI 可在 http://localhost:8080/grimo 存取
And    `main-agent = claude` 可用
And    嘗試 `/grimo switch codex` 回傳
       「codex CLI not detected; install or set grimo.cli.codex.enabled=false」
       — 無堆疊追蹤，無啟動失敗
```

### AC7 — 陪審團審查並行聚合 N 個 CLI

```
Given  使用者執行「/grimo jury review PRD.md --n=3」
And    claude、codex 和 gemini 均已配置
When   Grimo 派送三個並行審查子代理
Then   每個審查在各自的 Docker 沙箱中執行，以唯讀方式掛載 repo
And    在同一輪次內，Grimo 回傳包含
       「## Consensus」和「## Divergence」章節
       以及每個審查者的「## <provider>」章節的 markdown 報告
And    顯示每個提供者的 token 成本
```

### AC8 — Skill 蒸餾在重複後提案草稿

```
Given  在過去 N 個 session 中，使用者三次要求主代理
       「建立 Spring Boot health endpoint 骨架」，且結果相似
When   skill 蒸餾器任務執行（夜間或按需）
Then   `~/.grimo/skills/scaffold-health-endpoint/SKILL.md` 草稿
       以「pending」狀態提案
And    Web UI 顯示「new skill draft」徽章
And    使用者可接受/編輯/拒絕；已接受的 skills 在下一輪次出現於
       每個 CLI 適配器的工具列表中
```

### AC9 — Memory 由代理整理並建立索引

```
Given  在對話中使用者說「我總是部署到 europe-west3」
When   主代理決定這是一個持久化事實
Then   `AutoMemoryTools.write("user_deployment.md", ...)` 將其持久化
       至 `~/.grimo/memory/`
And    `~/.grimo/memory/MEMORY.md` 包含一個新的單行索引條目
And    後續 session 的第一輪次在 pre-prompt 中包含該記憶
```

### AC10 — Spring Modulith 邊界在測試中驗證

```
Given  codebase 結構化為 @ApplicationModules
       （core、sandbox、cli、agent、subagent、skills；
        其餘 v1 模組 — session、router、memory、jury、cost、web、nativeimage —
        於容器優先 MVP 重新規劃中移至 Backlog，
        詳見 spec-roadmap.md Backlog 與 architecture.md §2.x）
When   ./gradlew test 執行
Then   ApplicationModules.of(GrimoApplication.class).verify() 通過
And    沒有模組對另一個模組有非命名介面依賴
And   生成的模組圖表位於 build/spring-modulith-docs/
```

## 7. MVP 範疇

### 關鍵路徑（使用者於 2026-04-16 排序 — 驅動里程碑順序）

以下七個能力構成 Grimo v1 的垂直切片展示。按優先順序列出；每個成為 `spec-roadmap.md` 中的一個里程碑（M1 ↔ 項目 1，… M7 ↔ 項目 7）。不在此列表但在範疇內的項目為支援性關注點；完全缺席的項目預設進入 Backlog。

1. **容器操作** — Grimo 可從 Java 啟動、exec、bind-mount 並清理 Docker 容器。
2. **容器化 CLI** — `grimo-runtime` 映像預裝 `claude-code`、`codex` 和 `gemini`；Java 適配器透過 `docker exec` 呼叫每個 CLI。
3. **CLI 配置** — 調查每個 CLI 的配置，並將 Grimo 策略套用至每個容器化呼叫（Claude-Code 記憶體關閉、透過唯讀掛載從主機憑證儲存傳遞、可切換時停用遙測）。
4. **主代理對話** — `grimo chat` 在使用者終端和容器化的 `claude-code` 之間管道 stdin/stdout。MVP 中僅 CLI 直通；無 Web UI，無 TUI。
5. **任務派送給子代理** — 主代理結構化委派；Grimo 建立帶有每任務 git worktree bind-mounted 的子代理容器；在合併前擷取 diff 供使用者審查。
6. **Skill 管理** — Grimo 列出 / 啟用 / 停用 `~/.grimo/skills/` 下的 skills。
7. **Skill 注入** — 在子代理派送前，相關的已啟用 skills 複製至子代理容器的每個 CLI 原生 skill 路徑，使內部 CLI 能拾取它們。

### 支援性關注點（在 MVP 中，不在關鍵路徑上）

交付關鍵路徑所需的基礎設施，但不是本身的展示級能力：

- 本地優先單使用者部署，以 `./gradlew bootRun` 或原生映像二進位啟動。
- 測試中的 Spring Modulith 2.0.5 模組驗證。
- 將應用編譯為原生二進位的 `native` Gradle profile（夜間冒煙測試閘門證明它仍可建置）。生產級原生 UX — 快速啟動、低 RSS、出貨的產物 — 是 post-MVP 加固關注點，非 v1 發布閘門。
- I/O 密集型工作的 virtual thread 執行器（`spring.threads.virtual.enabled=true`）。
- 主機上任意 CLI 子集安裝時的軟失敗啟動。
- **訂閱帳號原生認證（P10）。** Grimo 透過它們自己的 OAuth/登入流程驅動底層 `claude` / `codex` / `gemini` CLI — 無 API 金鑰表單，無 `ANTHROPIC_API_KEY` 環境變數要求。使用者正常配置其 CLI（`claude login` 等），Grimo 在容器化 CLI 內以唯讀方式讀取主機憑證儲存（依關鍵路徑項目 3）。

### Backlog（原為 v0.1 MVP；2026-04-16 排定關鍵路徑後延後）

以下每個項目只有在使用者明確要求時才重新進入 MVP，屆時進行全新的 `/planning-spec` grill 循環。工作量估算見 `spec-roadmap.md` §Backlog。

- `main-agent` CLI 切換（claude ↔ codex ↔ gemini），透過 Grimo 擁有的 session + 壓縮重放。
- 對主代理強制執行的明確唯讀工具允許清單 — 在 MVP 中，這由容器隔離加上關鍵路徑項目 3 的 CLI 配置結構性地處理。
- 持久化 session 儲存（`spring-ai-session-jdbc` + H2 檔案）。MVP 接受每次 `grimo chat` 呼叫為全新 session。
- 成本路由器（啟發式 v1）、`Cost` 領域型別、成本遙測面板。
- 陪審團命令，用於 N 路並行審查。
- 由 `AutoMemoryTools` 支援的 `~/.grimo/memory/` 記憶體。
- Skill 蒸餾提案者（框架層級自動演化）。
- 本地 Web UI（Spring MVC + Thymeleaf + HTMX + SSE）。MVP 介面為 CLI 直通。
- 模組邊界 CI 任務（測試中的 `ModuleArchitectureTest` 在 MVP 中已涵蓋；專用 CI 任務為奢侈品）。
- E2E 整合測試套件。

### 超出範疇（永遠）

- TUI 適配器（自有終端 UI）。
- Discord / Telegram / LINE 適配器。六邊形入站埠的設計將讓它們在不改變核心的情況下加入。
- A2A **客戶端側**消費（Spring AI A2A 整合目前僅限伺服器端；作為伺服器公開是 nice-to-have）。
- 多使用者 / 多租戶。無帳號，無超出 localhost bind 的認證。
- 自主指導調整 / 規則學習（自我演化第 2+ 層）。
- 行動 / Electron / 原生平台 UI。
- 遠端代理池 / 分散式子代理。
- 雲端託管 session 同步。
- 無使用者審查的自動 skill 執行。
- 超出 worktree create/merge 的內建 VCS 操作（v1 無 PR 建立 UI）。

## 8. 架構概覽

```
         ┌─────────────────────── Adapter (in) ──────────────────────┐
         │  Web UI (Spring MVC + HTMX)   │   [future] TUI / Discord  │
         └───────────────┬───────────────────────────┬───────────────┘
                         │ HTTP / SSE                │
┌────────────────────────▼───────────────────────────▼────────────────┐
│                      Application Core (hex)                         │
│  ┌──────────┐  ┌─────────┐  ┌──────┐  ┌────────┐  ┌──────────────┐  │
│  │ core-    │  │ router  │  │ jury │  │ skills │  │ memory        │  │
│  │ harness  │◀─┤         │  │      │  │        │  │ (AutoMemory)  │  │
│  │ (session)│  └─────────┘  └──────┘  └────────┘  └──────────────┘  │
│  └────┬─────┘                                                       │
│       │ events (ApplicationEventPublisher)                          │
│  ┌────▼──────────┐                                                  │
│  │ sub-agent     │  ── spawns ──►  DockerSandbox + worktree         │
│  └───────────────┘                                                  │
└────────────────────────┬───────────────────────────┬────────────────┘
                         │ AgentClient (0.12.2)      │
         ┌───────────────▼─────────────┐   ┌─────────▼──────────────┐
         │  cli-adapter (ports out)    │   │  FS / Docker / Git      │
         │  claude · codex · gemini    │   │  (host resources)       │
         └─────────────────────────────┘   └─────────────────────────┘
```

核心列中的每個名稱都是 Spring Modulith 的 `@ApplicationModule`。入站適配器透過薄控制器與埠通訊；出站適配器包裝 `AgentClient`、Testcontainers 和 JGit/shell。

## 9. 決策日誌

| # | 決策 | 理由 | 被拒絕的替代方案 |
| --- | --- | --- | --- |
| D1 | 定位為**使用者外殼**，而非代理或框架 | 對 Böckeler / Parallel / OpenAI「外殼工程」的研究證實這是新興的標準抽象。它讓 Grimo 專注於控制/契約/狀態，而非發明新的 LLM 介面。 | 「另一個代理框架」（太擁擠）；「薄 CLI 啟動器」（無成本路由或陪審團價值）。 |
| D2 | Spring Boot **4.0.5** + Spring Modulith **2.0.5** + Java 25 | 使用者提供的 build.gradle.kts 鎖定了這些。Modulith 2.0.x *需要* Boot 4.0.x。Java 25 自 2025-09-16 起為 LTS。 | Boot 3.5 + Modulith 1.4.10（保守）；依使用者選擇拒絕。 |
| D3 | **`native` Gradle profile 第一天就存在**，生產原生映像作為**第二衝刺加固**（2026-04-16 可行性 spike 後修訂） | 使用者在初始建置中啟用了 `org.graalvm.buildtools.native`，因此 profile 必須從第一天起有效。但可行性 spike 回傳強烈警告：Testcontainers 非執行期原生依賴；`agent-client` 0.12.2 無原生 CI 或已發佈的 `RuntimeHints`；Modulith 2.0 有開放的原生回歸（#1493 CGLIB）。v1 以 JDK 25 的 JVM 出貨，從第一個 sprint 起執行**夜間 `nativeCompile` 冒煙測試**，以便在不阻礙交付的前提下早期發現 hint 缺口。生產原生映像目標在 sprint-2 加固里程碑（M7）。 | 「原生 AOT 硬閘在 v1 發布」— 依 spike 證據拒絕；「完全刪除 GraalVM 外掛」— 依使用者要求拒絕，且冒煙測試紀律成本低廉。 |
| D4 | 模組化單體（Spring Modulith）而非微服務 | 單使用者本地工具。微服務會帶來編排開銷而無任何收益。Modulith 驗證邊界，讓未來解耦成本低廉。 | 純微服務；MVP 範疇下被拒絕。 |
| D5 | 單體內部的六邊形架構 | 明確的埠讓我們可以在不改動核心邏輯的情況下替換 CLI 適配器、沙箱後端和入站通道。符合 Modulith 的「命名介面」模型。 | 帶有貧血服務的分層架構 — 可測試性更差，可移植性更低。 |
| D6 | 主代理**唯讀**；透過 Docker 中的子代理寫入（隔離取代細粒度權限控制） | 三個理由：(a) **核准疲勞** — 每次編輯的 Y/N 提示迫使使用者蓋橡皮章或停用安全性；bind-mounted Docker 將核准閘移至單一的任務末尾 diff。(b) **規劃 vs 執行** — 規劃需要強推理（昂貴，唯讀）；執行需要精確行動（便宜，沙箱）。(c) **安全 + 成本** — 讓危險路徑在結構上更難以走到。符合外殼工程的「計算控制」準則。見 P2。 | 帶使用者確認提示的統一讀寫主代理 — 保證更弱，UX 更差（核准疲勞），爆炸半徑更大。細粒度每操作權限矩陣 — 維護成本高，容易配置錯誤。 |
| D7 | Grimo 擁有 session（透過 Spring AI `SessionMemoryAdvisor`），而非 agent-client 的每 CLI `AgentSession` | `AgentSession` 在 0.12.2 中僅支援 Claude；無法交付跨 CLI 連續性。Session API（2026-04-15）提供 CLI 無關的事件源 `Message`。 | 直接使用 `AgentSession` — 將迫使 main-agent = claude 永遠不變。 |
| D8 | CLI 切換使用**壓縮歷史重放** | 使用者明確確認：「將過往對話跟新訊息一起派發給新 CLI」。務實、有界，無需自訂 CLI 協議。 | 完整的 CLI 無關 session 協議（重新發明 A2A）；僅在任務邊界切換（對 UX 限制過多）。 |
| D9 | 採用 `agent-sandbox-core` 0.9.1 的 `Sandbox` SPI 作為沙箱抽象層；**bind-mount 適配器**實作 `Sandbox` 介面，內部包裝 Testcontainers `GenericContainer`，在 `start()` 前呼叫 `withFileSystemBind(worktree, "/work", RW)`（非 `DockerSandbox`）。**2026-04-17 修訂** | 原始碼 spike（2026-04-16）確認：`DockerSandbox` 0.9.1 在建構子中啟動容器，無法在啟動前注入 bind-mount。但 `agent-sandbox-core` 的 `Sandbox` SPI（含 `ExecSpec`、`ExecResult`、`SandboxFiles`）設計良好，直接作為沙箱埠介面使用；S003 自訂適配器實作 `Sandbox` 加上 bind-mount 支援。`DockerSandbox` 仍可用於不需要 bind-mount 的短暫檔案複製情境。 | 自定義 `SandboxPort`（重新發明已有的 SPI）；原始 Docker Java API（更多程式碼）；直接使用 `DockerSandbox`（無 bind-mount 鉤點）；Podman（使用者更少）。 |
| D10 | 包含三個 `agent-claude`/`-codex`/`-gemini` starters；依賴堆疊的**呼叫時（非啟動時）CLI 探針**；需要時透過 `spring.autoconfigure.exclude=...` 停用每個提供者（2026-04-16 自動配置原始碼 spike 後修訂） | Spike 確認自動配置僅由 `@ConditionalOnClass` 控制，無 `enabled` 屬性；但它們**不會**急切地探測 CLI 二進位（`GeminiClient.create` / `ClaudeAgentModel` 等在首次呼叫時才發現 CLI）。因此 P5（軟失敗啟動）已由函式庫的自然行為滿足 — 缺少的 CLI 在呼叫時以乾淨的 `CliNotFoundException` 呈現。Grimo 包裝每個適配器，將其翻譯為「安裝 `<cli>` 或執行 /grimo switch <other>」的使用者可見訊息。注意屬性前綴為 `spring.ai.agents.claude-code.*`（連字號），非 `claude.*`。 | 發明 `grimo.cli.<provider>.enabled=true` 閘 — 鑑於函式庫已自然延遲，此做法多餘；增加配置雜訊。預設不啟用任何提供者 — 損害新使用者 UX。 |
| D11 | **2026-04-16 修訂：v1 外部通道 = CLI 直通**（`grimo chat` 在使用者終端和容器化 `claude-code` 之間管道 stdin/stdout）。原決策「本地 Web UI」按 §7 重新規劃移至 Backlog。 | 使用者將「main-agent CLI 對話」排為關鍵路徑項目 4，其下無 UI 層；在容器化 CLI 已擁有終端 I/O 的情況下，僅為託管聊天表單就建構 Spring MVC + HTMX，對 v1 來說超出範疇。六邊形埠仍設計為在不改動核心的情況下接受 Web UI。 | 原始 D11「本地 Web UI」；TUI（仍超出範疇）；所有通道第一天就上（仍超出範疇）。 |
| D12 | 自我演化範疇 = **Memory + Skills**（僅提案） | 使用者選擇。最小可信的自我演化循環。提案-審核-提交保持人類控制。 | 僅 Memory（幾乎不算「自我演化」）；Memory+Skills+Guides（自動調整指導為 R&D，非 MVP）。 |
| D13 | 成本路由器為**啟發式 v1**，使用者可覆寫 | 避免在第一天就建構分類器模型。從任務模式 + 大小 + 明確覆寫開始；從遙測中學習。 | 基於 LLM 的路由分類器（每輪次額外呼叫，延遲增加）；僅手動（無成本節省）。 |
| D14 | `~/.grimo/` 是單一持久化狀態根 | 一個備份、檢視、git-ignore 的地方。符合 `~/.claude`、`~/.codex`、`~/.gemini` 的使用者心智模型。 | 在 XDG 目錄下分散狀態 — 讓使用者更難推理。 |
| D15 | MVP 中無認證，僅 localhost-only bind | 本地優先，單使用者。認證為 v2 關注點。 | 第一天就加入 Spring Security — 超出範疇。 |
| D16 | Web UI = **Spring MVC + Thymeleaf + HTMX 2**，透過 `io.github.wimdeblauwe:htmx-spring-boot:5.1.0`；**禁用 `thymeleaf-layout-dialect`**（Groovy 執行期對原生映像有敵意 — 使用帶參數的 fragments） | 決策矩陣（原生 AOT × 串流 UX × 僅 Java × 單一建置）使 MVC+Thymeleaf+HTMX 遠遠領先。Boot 4.0.3+ 上的 `htmx-spring-boot` 5.1.0 基準線符合我們的 Boot 4.0.5；Thymeleaf 官方列為原生就緒；`ChatClient.stream().content()` → `SseEmitter` → `hx-ext="sse"` 是 15 行膠水程式碼。 | Vaadin Hilla（Boot 4 支援在 24.x 上仍為 pre-GA）；React SPA（違反僅 Java）；WebFlux+SSE（函式路由上有開放的 AOT 回歸）。 |
| D17 | 持久化儲存 = 預設**H2 檔案模式**（`~/.grimo/db/grimo;MODE=PostgreSQL`）；透過 `grimo.datasource.url` 選擇加入 PostgreSQL | 本地優先；H2 有優秀的原生映像支援；`spring-ai-session-jdbc` 0.2.0 附帶 H2 dialect（`H2JdbcSessionRepositoryDialect`）。`MODE=PostgreSQL` 讓使用者日後指向 Postgres 時保持對等。 | 透過 xerial 的 SQLite（無 Spring 一等 starter，session-jdbc 無 dialect）；嵌入式 Postgres（重量級）；純記憶體（重啟後丟失歷史）。 |
| D18 | 持久化 Session API = **`spring-ai-community/spring-ai-session` 0.2.0**（artifact group `org.springaicommunity`，starter `spring-ai-starter-session-jdbc`）— **與 `agent-client` 0.12.2 為獨立函式庫** | Spike 糾正了 session API 隨 0.12.2 一起出貨的初始錯誤假設。此函式庫有獨立的發版節奏（0.1.0 → 0.2.0 → 0.3.0-SNAPSHOT），需要 Spring AI ≥ 2.0.0-M4 和 Spring Boot ≥ 4.0.2（我們使用 4.0.5 ✓）。API 變化在所難免 — 固定於 `0.2.0` 發版，明確鎖定依賴版本。 | 建構自己的持久化層（重新發明輪子；他們已提供 `SessionEvent`、`SessionMemoryAdvisor`、壓縮策略、JDBC starter、schema）；目標 `0.3.0-SNAPSHOT`（不穩定）；延後 session 直至穩定的 1.x（延遲 CLI 切換 AC3）。 |
| D19 | MVP 中**僅訂閱帳號原生認證** — Grimo 從不提示輸入 API 金鑰；使用者透過每個 CLI 自己的流程一次登入（`claude login`、`codex login`、`gemini auth`），Grimo 透過呼叫 CLI 子程序繼承該認證 | 目標使用者畫像明確支付訂閱（Claude Max / ChatGPT Plus / Gemini Advanced）。在此之上要求 API token 是雙重收費和 UX 懸崖。`agent-client` 0.12.2 的每個提供者適配器已將 CLI 二進位作為子程序啟動 — CLI 的原生憑證儲存（keychain / `~/.claude` / `~/.gemini` / `~/.codex`）不作更動地重用。 | Web UI 中的 API 金鑰認證介面（拒絕：計費故事錯誤，重複了 CLI 已儲存的配置）；BYO-key 後備（MVP 拒絕 — 增加了我們還不需要的配置路徑，可在 post-MVP 以 `grimo.auth.api-key.enabled=true` 新增）。 |

## 10. 風險登錄

| # | 風險 | 可能性 | 衝擊 | 緩解措施 |
| --- | --- | --- | --- | --- |
| R1 | Spring AI / Testcontainers / ProcessBuilder 對 GraalVM 是重組合 → Native AOT 可能耗費時間 | 高 | 中 | **已在規劃中解決：** D3 修訂 — JVM 優先 v1，`native` profile + 夜間 `nativeCompile` 冒煙測試從第一個 sprint 開始（追蹤 hint 缺口）。Testcontainers 由策略規定僅限 JVM（`@DisabledInNativeImage`）。生產原生映像目標在 sprint-2 加固里程碑（M7）。 |
| R2 | `DockerSandbox` 主機 bind-mount 在 0.9.1 中不支援 → worktree 直通被阻礙 | **已確認** | 高 | **已在規劃中解決：** D9 修訂 — Grimo 擁有 `Sandbox` 埠；預設實作直接包裝 `GenericContainer` + `withFileSystemBind`。`DockerSandbox` 不用於 worktree 路徑。可選的上游 PR 作為 post-MVP 貢獻追蹤。 |
| R3 | CLI 切換重放丟失保真度（微妙的工具使用歷史、部分 diffs） | 中 | 中 | 壓縮策略透過 AC3 測試；在使用者指南中記錄「保留」vs「丟失」的範疇。 |
| R4 | 成本路由器啟發式將策略性任務錯誤路由為簡單任務 | 中 | 中 | 使用者覆寫隨時可用；覆寫遙測驅動啟發式改進。 |
| R5 | Skill 蒸餾提案垃圾 skills → 使用者信任侵蝕 | 中 | 中 | 需要 N ≥ 3 次成功重複和使用者核准才能讓任何 skill 生效；每使用者可選擇加入的設定。 |
| R6 | Spring AI 0.12.2 API 在我們腳下移動（BOM 移位） | 中 | 低 | 固定傳遞依賴；追蹤 changelog；透過規格控制升級。 |

## 11. 成功指標（post-MVP）

- **成本差異：** 每 session 中位數成本比單獨使用 Claude Opus 執行相同 prompts 的基準線下降 ≥ 30%。
- **切換留存：** ≥ 80% 的 CLI 切換事件之後，使用者發送的訊息在未重新解釋的情況下引用了先前的上下文（感測器：基於 LLM 評審的記錄分析）。
- **蒸餾 skill 採用率：** 每活躍使用者每 30 天 ≥ 1 個接受的蒸餾 skill。
- **啟動可靠性：** 在任何一個 CLI 安裝的全新筆記型電腦上，歸因於缺少 CLI 二進位的啟動失敗為 0 次。
- **模組完整性：** `ApplicationModules.verify()` 在 `main` 上始終為綠色。

## 12. 開放問題（供 `/planning-project` 確定）

1. 原生映像 **RuntimeHints** 清單 — 哪些 Spring AI / Testcontainers / agent-client 類別需要明確的可達性 hints？
2. 具體的**成本路由器啟發式表**（任務類別 × CLI × 閾值）。
3. **壓縮策略**選擇：`SlidingWindowCompactionStrategy` 的參數，還是在切換時對塊進行 LLM 摘要？
4. 如何將**主機 worktree** 掛載至 `DockerSandbox` — 擴展 vs 上游 PR vs 直接 Testcontainers。
5. 超出 localhost-only 的 Web UI **安全模型**（CSRF、origin-pinning）— MVP 最小集合。
6. **Skill 蒸餾節奏** — 夜間 cron vs 按需 vs 任務後掛鉤。

---

*詞彙表請見 `docs/grimo/glossary.md`。此處以粗體或 `code style` 出現的每個術語均在其中定義。*
