# Grimo 詞彙表

Grimo 文件與程式碼中所有重要領域術語的雙語參考。每個術語在 PRD、規格或 ADR 中首次出現時，請在此新增。

> 風格規則：**程式碼使用英文術語原文**（類別、套件、屬性名稱）。散文中可使用任何一種，但在繁體中文散文中優先使用中文術語，並在首次出現時以括號附上英文。

## 核心領域

| 英文（程式碼） | 中文（zh-TW） | 定義 |
| --- | --- | --- |
| Grimo | Grimo | 產品本身。發音為「grim-oh」— 源自 *grimoire*，一本程序之書。一個可自我演化的 CLI AI 代理**使用者外殼（user harness）**。 |
| GrimoHome | Grimo 家目錄 | 持久化狀態目錄，預設為 `~/.grimo`。包含 `memory/`、`skills/`、`sessions/`、`worktrees/`、`logs/`、`config/`。 |
| Harness | 外殼 / 挽具 | 包裝模型以形成代理的一套控制、契約與狀態。`Agent = Model + Harness`。Grimo 是**使用者外殼**，而非框架。 |
| User Harness | 使用者外殼 | 由使用該工具的團隊*圍繞*現成代理（Claude Code、Codex、Gemini CLI）構建的外殼 — 與烘焙進代理本身的**建置者外殼（builder harness）**相對。 |
| Main Agent | 主代理 | 對話進入點。唯讀 — 無法執行 Edit / Write / Bash 寫入操作。在主機上的 Docker 外部執行。可配置的 CLI（`claude` / `codex` / `gemini`）。 |
| Sub Agent | 子代理 | 為委派任務生成的隔離執行器。在 **Docker 內部**執行，並掛載 git worktree。在沙箱內擁有完整的 Read/Write/Bash/Edit（YOLO）權限。 |
| Session | 對話會話 | Grimo 的事件源對話狀態，由 Grimo 擁有（而非底層 CLI）。基於 Spring AI `SessionMemoryAdvisor` 構建。可在 CLI 切換後存活。 |
| CLI Switch | CLI 切換 | 在 session 中途更換主代理底層 CLI 的操作。Grimo 壓縮先前的 `Message` 事件，並將其作為引導訊息重放給新的 CLI。 |
| Skill | 技能 | 磁碟上可重用的程序，格式為帶有 YAML frontmatter 的 `SKILL.md`。可由所有 CLI 適配器透過統一登錄檔發現。 |
| Skill Distillation | 技能蒸餾 | 閉環程序：在某個重複模式成功執行 N 次後，Grimo 提案一份 `SKILL.md` 草稿；使用者審核並提交。 |
| Memory | 記憶 | 由代理本身透過 `AutoMemoryTools` 整理的持久化事實。存放於 `~/.grimo/memory/`，以 `MEMORY.md` 作為索引。 |
| Guide | 指導 | 前饋控制：在輪次執行**前**套用的 prompts、工具允許清單、角色設定覆寫。 |
| Sensor | 感測器 | 回饋控制：在輪次執行**後**套用的 linters、測試、AI 審查者。 |
| Jury | 陪審團 | 多視角審查模式：將單一任務並行派送給 N 個 CLI，聚合成共識 + 分歧。 |
| Router | 路由器 | 感知成本與複雜度、決定哪個 CLI/模型執行某輪次的元件。 |
| Worktree | 工作樹 | 在 `~/.grimo/worktrees/<task-id>/` 下以每任務目錄檢出的 git worktree。掛載至子代理容器中。 |
| Sandbox | 沙箱 | 子代理運行的 Docker 容器。採用 `agent-sandbox-core` 0.9.1 的 `Sandbox` SPI 作為抽象層；S003 自訂 `BindMountSandbox` 適配器實作 `Sandbox` 介面，支援 worktree bind-mount。`DockerSandbox` 不用於 worktree 場景（無 bind-mount 鉤點）。 |
| Contract | 契約 | 任務宣告的輸入/輸出/停止條件形狀 — 一個外殼原語。 |
| Control | 控制 | 工作如何分解、排程與守護 — 一個外殼原語。 |
| State | 狀態 | 跨步驟、分支與子代理持久化的內容 — 一個外殼原語（sessions、memory、skills、worktrees）。 |
| Approval Fatigue | 核准疲勞 | UX 失敗模式：持續的 Y/N 確認（每次檔案編輯、每次 shell 命令）迫使使用者無腦蓋章或停用安全檢查。Grimo 的解答是將核准移至沙箱 worktree 內單一的任務末尾 diff 確認點。 |
| Subscription-Native Auth | 訂閱帳號原生認證 | Grimo 透過呼叫使用者已登入的 CLI 二進位來認證，因此 Claude Max / ChatGPT Plus / Gemini Advanced 訂閱即已足夠 — 無需 API 金鑰。（見 PRD D19 / P10。） |
| Vendor Lock-in Resilience | 跨 agent 韌性 | 任何單一提供者中斷、配額耗盡或價格變動，都只會將 Grimo 降級為「改用其他 CLI 執行」，而非「Grimo 停止運作」的特性。（見 PRD P9。） |

## 技術棧

| 英文（程式碼） | 中文（zh-TW） | 定義 |
| --- | --- | --- |
| AgentClient | 代理客戶端 | Spring AI Community 抽象層（`org.springaicommunity.agents`），將 CLI 代理包裝在統一的 builder API 後面。固定版本：0.12.2。 |
| AgentSession | 代理會話 | 來自 agent-client 的每個 CLI 低層級 session 物件。**目前僅支援 Claude** — Grimo *不*依賴此物件實現跨 CLI 可移植的 session。 |
| SessionMemoryAdvisor | 會話記憶顧問 | 來自 Spring AI Session API（2026-04-15）的事件源 session advisor。Grimo 的標準 session 儲存。 |
| AutoMemoryTools | 自動記憶工具 | 來自 `spring-ai-agent-utils` 的檔案支援長期記憶工具。 |
| DockerSandbox | Docker 沙箱 | 來自 Spring AI `agent-sandbox` 的 Testcontainers 支援沙箱後端。 |
| Spring Modulith | Spring 模組 | 模組邊界 + 事件發佈函式庫。固定版本：2.0.5。每個限界上下文 = 一個 `@ApplicationModule`。 |
| Application Module | 應用模組 | Boot 主類下的頂層套件，由 `package-info.java` 上的 `@ApplicationModule(displayName, allowedDependencies, type)` 守護。`type = Type.OPEN` 表示其他模組可直接存取（`core` 採此模式）；預設 `Type.CLOSED` 則只有對外公開的「命名介面」可被引用。 |
| Named Interface | 命名介面 | 套件層級的對外發佈點，標 `@NamedInterface("<name>")`。Grimo 約定：同步埠用 `"api"`、事件型別用 `"events"`。消費者透過 `<publisher>::<name>` 在 `allowedDependencies` 中限定範圍，看不到出版者的內部型別。 |
| Allowed Dependencies | 允許的依賴 | 閉合 `@ApplicationModule(allowedDependencies = { ... })` 的明確白名單。空陣列 `{}` 表示禁止任何跨模組存取（Grimo 各 MVP 模組於 S002 起步狀態）；`"<module>::<named-interface>"` 條目允許進入該命名介面。屬性留空（不寫）= 「不限制」（不採用）。 |

## 命令介面（規劃中）

| 命令 | 中文 | 效果 |
| --- | --- | --- |
| `/grimo switch <cli>` | 切換 CLI | 替換主代理 CLI，將壓縮後的歷史重放至新 CLI。 |
| `/grimo delegate <task>` | 委派任務 | 在 Docker 中啟動帶有全新 worktree 的子代理。 |
| `/grimo jury <task> --n=3` | 陪審團審查 | 並行派送給 N 個 CLI，聚合審查結果。 |
| `/grimo skills` | 列出技能 | 列出所有已發現的 skills（內建 + `~/.grimo/skills/`）。 |
| `/grimo memory` | 記憶管理 | 檢視 / 清理 `~/.grimo/memory/`。 |
| `/grimo cost` | 成本報表 | 顯示本次 session 每個 CLI 的 token/成本遙測資料。 |
