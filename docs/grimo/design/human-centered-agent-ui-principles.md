# Grimo Human-Centered Agent UI Principles

**狀態：** v0.1 research synthesis
**日期：** 2026-06-13
**用途：** 把 `The Design of Everyday Things`、Swiss cheese model、AI/agent UX 研究轉成 Grimo 後續 UI/UX 設計判準。
**適用範圍：** Project onboarding、Task Workbench、Task Detail、Chat、Attention Queue、Workflow / Dispatcher / Agent execution surfaces。

---

## 1. 為什麼需要這份文件

Grimo 的問題不是 agent 能力不夠，而是能力如果只住在一個聊天輸入框裡，使用者看不出它能幫自己完成什麼。

對熟悉軟體工程的人來說，`Chat -> Task -> Workflow -> Agent -> Evidence -> Review` 很自然；對沒有這套心智模型的人來說，聊天框只 signifier 出「可以打字聊天」，沒有 signifier 出「可以建立可追蹤工作、排程 agent、檢查證據、檢視結果、保存學習」。這會造成兩個使用者體驗問題：

1. **執行的鴻溝 (Gulf of Execution)：** 使用者知道自己想完成一件事，但不知道畫面上哪裡可以開始、可以交給誰、需要先補什麼。
2. **評估的鴻溝 (Gulf of Evaluation)：** 系統做了很多事，但使用者看不懂目前在哪個狀態、是否可靠、下一步是不是要自己決定。

因此 Grimo 的 UI 不能把核心能力藏在 generic chat 裡。Chat 可以形成工作，但產品第一層必須讓使用者看見 Project、Task、state、next action、agent status、evidence 和 recovery path。

---

## 2. 外部研究怎麼轉成 Grimo 原則

| 研究概念 | 白話意思 | 對 Grimo 的設計要求 |
| --- | --- | --- |
| 預設用途 (affordance) | 一個東西實際允許哪些動作。 | `Task Card` 不能只像靜態摘要；如果能開 detail、回 Chat、approve/reject，就要有對應可操作區。 |
| 指意 (signifier) | 使用者看得到的線索，告訴他哪裡能做什麼。 | `READY` 要 signifier 出「可以開始 dispatch」，`REVIEW` 要 signifier 出「等待檢視」，needs-human repair cue 要 signifier 出「要修哪個前置條件」。 |
| 可探索性 (discoverability) | 使用者不讀說明也能找到可能動作。 | 首次使用、空狀態、error state 都要給清楚下一步；不能只顯示空白 Chat 或空表格。 |
| 對應性 (mapping) | 控制項和結果之間的關係要自然。 | `Start Dispatch Window` 應靠近 `READY` queue；`Approve / Reject` 應靠近 review materials；folder browser 的 `使用此資料夾` 只回填 path，不建立 Project。 |
| 回饋 (feedback) | 動作後要立刻知道發生什麼。 | 建立 Task、啟動 agent、測試完成、檢視後 reject，都要顯示 state change、時間、證據或下一步。 |
| 概念模型 (conceptual model) | 使用者心中對產品怎麼運作的解釋。 | Grimo 要穩定傳達：Chat 是入口，Task 是工作單位，Workflow 是執行方法，Evidence 是檢視依據。 |
| 限制 (constraints) | 用設計縮小錯誤可能。 | 高風險動作要 gate；沒有 Project context 時不能顯示假 Task；default root `~/.grimo/projects/` 不能被誤選成單一 Project Path。 |
| Swiss cheese model | 不期待單層防護完美，而是用多層防線避免錯誤穿透。 | 對 agent 執行不能只靠「模型應該懂」；要有狀態、preflight、confirmation、permissions、logs、review、undo/recovery 多層保護。 |

---

## 3. 核心設計判準

### P1. 不要把 agent 能力偽裝成普通聊天

使用者看到聊天框時，只會自然理解成「我可以問問題」。如果 Grimo 期待使用者知道它能建立 Task、排程 agent、保存 evidence，那些能力必須在 UI 上有自己的 signifier。

Grimo 應該做到：

- Task Workbench 是主要入口，不是 Chat。
- Chat message 可以產生 Task draft，但要讓使用者看到 draft、狀態、缺口和確認動作。
- Chat 內的 suggestion chips 要用 outcome 寫法，例如 `整理成 Task`、`補 acceptance criteria`、`查看 Review evidence`，不要只寫 provider 或 skill 名稱。
- 從 Task 開啟 Chat 時，要進入該 Task 的 conversation thread，不是空白 generic chat。

不要做：

- 首頁只給一個空白輸入框，期待使用者猜 agent 能力。
- 用 `AI-powered`、`agentic`、`smart workflow` 當主要價值文案，卻不顯示使用者能完成什麼。
- 讓 provider / model 名稱變成第一層導航。

### P2. 每個 state 都要回答「我現在能做什麼」

Grimo 的 state 不是工程 metadata，而是使用者決策提示。

| State | 畫面必須回答的問題 | 主要 signifier |
| --- | --- | --- |
| `BACKLOG` | 這件事還只是想法，下一步怎麼開始釐清？ | `開始討論` / `補需求` |
| `DEFINING` | 還缺什麼才算 ready？ | open questions、gaps、definition progress |
| `READY` | 能不能開始？開始後會發生什麼？ | dispatch readiness、preflight result、start control |
| `RUNNING` | Agent 正在做什麼？卡在哪裡？ | current step、worker log、elapsed time、stop/recover path |
| `REVIEW` | 我要根據哪些證據 approve/reject？ | review summary、tests、risk notes、Approve / Reject |
| `DONE` | 完成了什麼？後續學到什麼？ | release evidence、summary、follow-up |

`NEEDS_HUMAN` / blocked reason 不是第七個主狀態；它是顯示在相關 Task State 上的修復提示，要回答「誰要處理、要修什麼、修完回哪裡」。

### P3. 把知識放在畫面上，減少使用者記憶負擔

Grimo 不該要求使用者記住 workflow recipe、quality gate、agent 權限、Project path contract 或前一次對話結論。這些都要變成可掃描的 external knowledge。

具體要求：

- Task card 只顯示 list-level 判斷需要的資訊：state、title、labels、quality / evidence hints、conversation preview。
- Task detail 顯示完整 decision material：definition package、workflow evidence、review materials、risk notes、linked work。
- Chat 顯示完整 task-owned thread，並保留 attachments / references。
- Project creation 顯示 `projectPath` 的實際結果：不填會建立 Grimo-managed path；選資料夾會回填 backend 可操作 absolute path。

### P4. 讓使用者監督 automation，而不是被 automation 推著走

Agent 可以主動做事，但 Grimo 要讓使用者保有控制感。低風險可以更自動，高風險要更明確地顯示確認、範圍和復原方式。

Automation level 應分層：

| Level | 使用者看到什麼 | Grimo 例子 |
| --- | --- | --- |
| 建議 | Agent 只整理選項，使用者決定。 | Task draft、workflow recommendation、missing-info suggestions |
| 準備 | Agent 產生可檢視材料，但不改外部狀態。 | Definition package、review checklist、test plan |
| 執行 | 使用者確認後，agent 在限定範圍內執行。 | Dispatch Window、single Task start |
| 交付 | 高影響動作需 evidence 和人工 gate。 | REVIEW approve、release / merge / tag |

設計要求：

- `READY` 不等於自動開始；畫面要保留 `Start` 或 `Dispatch Window`。
- `RUNNING` 要有可理解狀態，不只 spinner。
- `REVIEW` 要把 approve/reject 放在 evidence 旁邊。
- automation failed 時要能回到 manual path，例如手動補資訊、重試 preflight、回 Chat 釐清。

### P5. 用多層防護設計錯誤，而不是責怪使用者

Swiss cheese model 對 Grimo 的重點是：錯誤一定會發生，UI 不能只靠單一提示或單一 confirmation。每個高風險流程至少要有多層防護。

Agent 執行的防護層：

| 防護層 | 目的 | UI / UX 形式 |
| --- | --- | --- |
| Scope signifier | 使用者知道 agent 會碰哪個 Project / Task。 | App Header project context、Task identity、selected repo path |
| Readiness gate | 開始前發現缺資訊或缺工具。 | READY gate、preflight result、NEEDS_HUMAN repair reason |
| Explicit confirmation | 高影響動作不能默默發生。 | Start Dispatch、Approve / Reject、release confirmation |
| Bounded execution | agent 只能在可理解範圍內工作。 | Dispatch Window、task claim、worktree / sandbox indicator |
| Continuous feedback | 使用者看得到目前做了什麼。 | current step、logs、quality score、test status |
| Evidence gate | 完成前交出可檢視證據。 | Review Materials、risk notes、test evidence |
| Recovery path | 失敗後能接手或重試。 | Retry、回 Chat、manual input、reject with reason |

Project onboarding 的防護層：

| 防護層 | 目的 | UI / UX 形式 |
| --- | --- | --- |
| Context gate | 沒有 Project 時不顯示假工作台。 | Project Selection Gate / Project Setup Copilot |
| Path contract | 使用者知道 path 會被 backend 和 agent 使用。 | `projectPath` input note、absolute path display |
| Picker boundary | 不把 Grimo root 誤當 Project path。 | default root disabled、modal state copy |
| No destructive fallback | Picker 失敗不破壞已填表單。 | modal error、input 保留、manual edit remains |

### P6. Empty、error、loading 都是產品畫面，不是暫時空白

空狀態是最好的 signifier。它應該告訴使用者現在缺什麼、下一步是什麼、這一步成功後會去哪裡。

要求：

- Empty state 要有一個主動作，不要多個同級 primary CTA。
- Error state 要說清楚發生什麼、使用者能做什麼、是否保留資料。
- Loading state 不顯示 stale data 當成新結果。
- Success state 要帶使用者到自然下一步，不留在已完成的 stale form。

對 Grimo：

- 沒有 active Project 時，Task / Attention / Chat 不顯示 fixture data。
- Project list error 顯示 retry，不假裝 first-run。
- Folder browser error 留在 modal，不清掉 Project Creation form。
- Review evidence 缺失時，不顯示可用 approve 的假狀態。

### P7. 解釋要幫使用者決策，不是展示系統內部

AI/agent 介面常犯的錯，是把內部機制當成說明。Grimo 的說明要先講使用者得到什麼結果，再補技術線索。

寫法：

- 好：`這件 Task 已準備好執行。開始後，agent 會在目前 Project 裡 claim 這件 Task，完成後回到 REVIEW 等你檢視。`
- 不好：`Dispatcher will enqueue a workflow execution using recipe steps.`

放在畫面上的解釋要回答：

1. 這個狀態對我代表什麼？
2. 我按下去會發生什麼？
3. 系統為什麼需要我決定？
4. 如果 AI 不確定或失敗，我可以怎麼接手？

### P8. 熟悉的 UI pattern 優先，魔法感要讓位給可理解

AI 產品很容易為了表現「很聰明」而使用陌生互動，但陌生互動會降低信任。Grimo 應該用開發者熟悉的工作台語言：list、board、detail pane、review gate、logs、evidence、status badge、queue、modal。

使用 familiar patterns：

- Task list / board：管理工作。
- Detail pane：查看選取工作。
- Attention queue：處理人工阻塞。
- Review materials：做 approve/reject。
- Command buttons：開始、重試、取消、回 Chat。
- Modal / bounded overlay：短流程選擇，例如 Project Path Folder Browser。

不要用：

- 只有大型聊天框的「萬能入口」。
- 看起來像魔法但沒有狀態、範圍、復原路徑的自動化。
- 把所有 workflow steps 做成同級 navigation，讓使用者以為自己要管理內部 pipeline。

---

## 4. Grimo UI 檢查清單

每次新增或修改 UI spec 前，先檢查這些問題。

### 4.1 Capability signifier

- 使用者不用讀文件，就看得出這個畫面能做什麼嗎？
- 主要 action 是否對應使用者結果，而不是技術動詞？
- Chat suggestion 是否揭露 Grimo 的工作台能力，而不是只有 generic prompt？
- Provider、skill、workflow step 是否被放錯成 user-facing label？

### 4.2 State and feedback

- 目前 state 是否回答「現在在哪裡」和「下一步是什麼」？
- 使用者按下主要 action 後，有沒有立即 feedback？
- 長任務是否顯示 current step、等待原因、最近 evidence 或 log？
- Success 後是否進入自然下一個 screen/state？

### 4.3 Error prevention and recovery

- 這個流程有哪些可能的 slip / mistake？
- 哪些錯誤可以靠 constraints 直接避免？
- 哪些錯誤需要 confirmation、undo、retry 或 manual takeover？
- 失敗時是否保留使用者已填資料？
- Error message 是否說明下一步，而不是只說失敗？

### 4.4 Agent trust and control

- 使用者是否知道 agent 可以碰哪些資料、工具、Project 和 Task？
- 任何 background work 是否能在 dashboard、log 或 task detail 被看見？
- 高風險 automation 是否有人工 gate？
- 使用者是否能暫停、拒絕、回 Chat 釐清或手動接手？

### 4.5 Documentation and evidence

- Page-flow 變更是否先更新 Screen Flow Contract？
- UI decision 是否寫回 `frontend-design-context.md` 或 active spec？
- Visual / responsive 變更是否有 Playwright snapshot？
- 若是 AI/agent 行為，是否有 state、log、evidence 或 BDD 可驗證？

---

## 5. 對目前 Grimo 的直接提醒

### Project onboarding

- `Project Selection Gate` 是重要 signifier：它告訴使用者「先建立 Project context，Task 工作台才有真實資料」。
- `Project Setup Copilot` 可以用 assistant-style 形式，但不能變成 generic chat；它要清楚導向 `建立新 Project`、repo/path、workflow recipe 和 Task context。
- `Project Path Folder Browser` 應像檔案選擇器，而不是把 backend directory tree 直接攤在 form 下方。

### Task Workbench

- Task card 要幫使用者 triage，不要展示系統內部。
- `REVIEW` 和 needs-human repair 必須比一般 backlog 更有行動 signifier。
- Search、filter、create 和 focus tray 都要維持一個清楚的 command hierarchy。

### Task Detail

- Task detail 是評估的橋樑。它要讓使用者看懂：需求是什麼、agent 做了什麼、證據在哪裡、風險是什麼、我該 approve 還是 reject。
- Review action 必須和 Review Materials 形成自然 mapping，不要藏在頁面邊角。

### Chat

- Chat 是 Task 的討論入口與釐清工具，不是產品唯一入口。
- 從 Task 開 Chat，一定要帶著 task-owned thread；不應開新空白對話。
- Chat 裡的 AI 回應要能 promote 成 Task definition、gaps、acceptance、evidence 或 follow-up，而不是停留在 transcript。

### Dispatcher / Agent execution

- `READY` 是可排程，不是自動執行。
- `RUNNING` 必須顯示 agent status、current step 和 recovery path。
- `REVIEW` 是 human gate；沒有 evidence 的完成不算完成。

---

## 6. Research sources

- Don Norman, [Preface: Design of Everyday Things, Revised Edition](https://jnd.org/preface-design-of-everyday-things-revised-edition/)：affordance、signifier、HCD、discoverability / feedback 的脈絡。
- Don Norman, [Signifiers, not affordances](https://jnd.org/signifiers-not-affordances/)：設計師真正需要提供的是可感知的線索，讓人知道產品能做什麼、正在發生什麼、有哪些替代動作。
- `doet-study-companion`, [設計的心理學・互動導讀](https://j0214ack.github.io/doet-study-companion/)：本次 user 提供的 DOET 互動導讀案例，包含章節、術語、頁碼對照和 LINE / agent 介面反思。
- Nielsen Norman Group, [10 Usability Heuristics for User Interface Design](https://www.nngroup.com/articles/ten-usability-heuristics/)：system status、user control、error prevention、recognition rather than recall、error recovery 等 UI 檢查基礎。
- Nielsen Norman Group, [Designing AI Products and Features: Study Guide](https://www.nngroup.com/articles/designing-ai-study-guide/)：AI 產品要解真實使用者問題，不能預設 chat interface 永遠是答案；hybrid UI 和 prompt suggestions 可降低 articulation barrier。
- Microsoft Design, [UX design for agents](https://microsoft.design/articles/ux-design-for-agents/)：agent 需要 discoverability、history/context、transparency、control、visible status 和 familiar UI。
- Microsoft Learn, [Design for Agents](https://learn.microsoft.com/en-us/agents/design-guidelines/overview)：agent design 應以安全、尊重、使用者控制、透明與 human-centered design 為基礎。
- Google People + AI Research, [Patterns](https://pair.withgoogle.com/guidebook-v2/patterns)：AI UX 要說明使用者利益、讓使用者安全探索、用熟悉 pattern、逐步自動化、讓使用者監督 automation、失敗時交還控制。
- Google People + AI Research, [Mental Models](https://pair.withgoogle.com/guidebook-v2/chapter/mental-models/)：AI 產品 onboarding 要建立能力、限制、回饋和 co-learning 的心智模型，並避免 failure dead-end。
- AHRQ PSNet, [Human error: models and management](https://psnet.ahrq.gov/issue/human-error-models-and-management)：Reason 的 person approach vs system approach、Swiss cheese model 與高可靠系統思維。
- SKYbrary, [James Reason HF Model](https://skybrary.aero/articles/james-reason-hf-model)：Swiss cheese model 的多層防護、holes alignment、active / latent failures 的安全設計語彙。
