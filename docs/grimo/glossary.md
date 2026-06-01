# Grimo Product Language

Grimo 的產品語言用來固定使用者應該理解的核心概念，避免把產品定位、工程架構和內部 workflow 名詞混在一起。

## Language

**Grimo**:
本地優先的 AI 開發工作台，讓開發者把想法變成可指派、可執行、可審查、可學習的 AI coding task。
_Avoid_: CLI user harness, thin CLI launcher, workflow console

**Project**:
Grimo MVP 中的一個本機 repo 或 codebase，是 Task、執行證據、connector 和 runtime capability 的歸屬單位。
_Avoid_: Multi-repo workspace as MVP meaning

**Project Home**:
Grimo 為每個 Project 建立的本機管理目錄，預設位於 `~/.grimo/projects/<projectId>`；用來保存 Grimo-managed workspace、evidence、未來 task worktree / sandbox metadata，並作為使用者沒選既有 repo 時的預設工作區。
_Avoid_: Treating Project Home as the same thing as an external user repo, storing all app-wide data directly under one flat directory

**Task**:
使用者想完成的一件工作，是 Grimo 追蹤進度、執行、審查和收尾的主要單位。
_Avoid_: Workflow step, internal phase, prompt message

**AI 開發工作台 (AI Development Workbench)**:
Grimo MVP 對使用者的主定位，聚焦在管理 AI 開發任務、執行證據、審查與流程學習。
_Avoid_: Local Agent Control Plane as user-facing tagline, generic AI workspace as MVP positioning

**Local Agent Control Plane**:
Grimo 的工程定位，表示它在本機協調 agent runtime、skills、workflow recipes、任務狀態和執行證據。
_Avoid_: User-facing product tagline

**Agent-Facing Task System**:
Grimo 的延伸能力，表示外部 coding agent 可以領取 Ready Task 並回報執行結果。
_Avoid_: Main product positioning

**Agent Profile**:
給使用者指派工作的薄角色入口，包含名稱、用途、runtime、skills 和 assignment rules；MVP 先服務 coding work，但角色模型可延伸到行銷、財務、營運等非開發工作。
_Avoid_: Thick AI coworker, independent persona, separate inbox, coding-only role model

**Project Creation Page**:
使用者從功能列表按「建立專案」後進入的頁面，負責填寫專案名稱、專案描述、Project Workspace、工作流下拉選單，並預覽隨工作流切換的角色列表；只有頁面上的 submit action 才真正建立 Project。
_Avoid_: Treating the feature-list entry button as the submit action, always-visible inline Project form

**Project Workspace**:
Project 的檔案系統工作區。S003 起，使用者不選工作路徑時，Grimo 會在 `~/.grimo/projects/<projectId>` 建立 `GRIMO_MANAGED` workspace；使用者也可以用 browser-native directory handle 或手動輸入既有 repo path 來改綁 workspace。
_Avoid_: Generic Project Path, `folderPath` as product/API field, blocking Project creation when no external path is selected, assuming every browser-selected folder is backend-ready, uploaded file list

**Browser Workspace Picker**:
Project Creation Page 中的主要「選擇資料夾」互動；支援時使用 browser `showDirectoryPicker()` 開 OS 原生資料夾視窗，讓使用者選本機 repo / codebase。它產生 browser-owned directory handle，不產生 backend absolute path。
_Avoid_: Backend `ls` UI as the primary picker, pretending `FileSystemDirectoryHandle` is a server path, browser file upload

**Manual Workspace Path**:
Project Creation Page 的 fallback；使用者手動輸入 `/Users/...` 這類本機 path，backend 驗證存在、是資料夾、可讀後，Project 使用該 existing local path 作為 workspace。它不是建立 Project 的必要條件；不輸入時使用 `GRIMO_MANAGED` workspace。
_Avoid_: Running shell commands during validation, accepting invalid path as a Project workspace, making manual path the primary happy path, making manual path required for Project creation

**Local Directory Picker**:
S001/S002 使用過的本機資料夾瀏覽方式；前端透過 Spring Boot read-only API 顯示資料夾清單。S003 起不再是 Project Creation Page 的主要互動，除非未來 spec 明確恢復。
_Avoid_: Treating backend directory browsing as the default S003 picker, shell command execution

**Workflow Role Preview**:
Project Creation Page 中由 Workflow Recipe 顯示的薄 Agent Profile 角色清單，幫使用者理解該工作流會用到哪些角色；它是 read-only preview，不讓使用者勾選或移除角色。
_Avoid_: User-selected role checklist, thick persona selection, workflow execution state

**Project Workflow Role Settings**:
建立 Project 時從所選 Workflow Recipe 複製出來的角色基本設定，保存 role id、name、description、primary steps 和 enabled default，供後續 Ready Gate / Dispatcher 做 Task-level assignment 基礎。
_Avoid_: Storing role settings as a comma-separated Project field, treating Project Creation Page as a role editor

**Future General Workbench Extension**:
Grimo 架構保留的未來方向，讓非開發角色也能透過 Agent Profile、skills 和 workflow recipes 進入同一套工作台。
_Avoid_: MVP promise

**Workflow Recipe**:
可重複執行的工作流程，將某個 Task Type 的專業工作實務拆成可推進、可審查、可留下 evidence 的步驟；coding 的 SDD 九步驟只是其中一種 recipe。
_Avoid_: One-off prompt, rigid user-facing state machine

**Task State Machine**:
Task 在 board/list 上呈現的外層狀態機，用來回答「這件工作現在在哪裡、需不需要人介入」。MVP 狀態包含 BACKLOG、DEFINING、READY、RUNNING、REVIEW、DONE 和 BLOCKED。
_Avoid_: Workflow Recipe, detailed execution trace, individual recipe step

**Task State**:
Task State Machine 裡的一個狀態，例如 BACKLOG、DEFINING、READY、RUNNING、REVIEW、DONE 或 BLOCKED。它是使用者看進度的語言，不是 agent 內部執行步驟。
_Avoid_: Workflow Step, role responsibility, quality sub-step

**State Workflow**:
某個 Task State 底下的工作規則。DEFINING / RUNNING 通常會展開成一組 Workflow Steps；READY 是等待 dispatch 的 queue/gate；REVIEW 是等待人類 approve/reject；DONE 是保存完成證據與 Wrap evidence 的狀態。
_Avoid_: Assuming every state always runs automated agent steps

**Skill**:
Agent 在 Workflow Recipe 某一步中使用的實務能力包，包含知識、工具使用方式、判斷規則或文件化經驗。使用者原本手動切換的 skill 開發流，會由 Workflow Recipe 和 Agent Profile 分配到對應 step，讓角色各司其職。
_Avoid_: Persona trait, vague capability label

**MVP Core Promise**:
Grimo 第一版不可退讓的使用者結果：AI agent 領走 Ready Task，完成開發，並交回可審查的 Review Materials。
_Avoid_: Only generating tickets, issue writer, planning-only assistant

**Definition Package**:
把原始想法收斂成可執行任務前，人類用來判斷是否要按 READY 的需求定義資料包。
_Avoid_: Issue draft, chat summary

**Review Materials**:
AI agent 完成開發後，人類用來 approve 或 reject 的審查資料包。
_Avoid_: Final diff only, loose transcript

**Human Review State**:
Task State Machine 中等待人類 approve 或 reject 的狀態；Task 必須先完成 AI self-review、必要測試和 Review Materials，才會停在這裡。
_Avoid_: AI reviewer still running, internal Quality Loop review

**Workflow Step**:
Grimo 在 Task 底下自動推進工作的內部階段；具體步驟由 Task Type / Workflow Recipe 決定，例如 coding recipe 會有 Discuss、Explore、Prototype、Spec、Usage、Tkt、Dev、Review。需要收尾時，結果保存成 DONE task 的 Wrap evidence，不把 Wrap 當成新的看板狀態。
_Avoid_: User-level Task

**Step Sub-workflow**:
Workflow Step 底下可重複使用的子流程，用來確保該 step 的 output 足夠可信。Coding recipe 目前主要的 Step Sub-workflow 是 Quality Loop。
_Avoid_: Board state, separate user Task, standalone project workflow

**Quality Loop**:
Grimo 在每個 Workflow Step 中自動執行的 Review、Rating、Fix 循環，用來提高輸出品質並決定是否能推進狀態。
_Avoid_: Manual user checklist, top-level Task status

**Quality Bar**:
由 Task Type / Workflow Recipe 提供的通用品質期待，用來輔助 Project Quality Gate 和 Task/Spec Acceptance Gate 定義。
_Avoid_: Fixed universal checklist, per-run ad hoc judgment with no prior rubric

**Project Quality Gate**:
在 Project 設計階段依 repo/codebase 型態與最佳實踐定義的 baseline 驗收制度，例如前端、後端、全端、CLI 或 library 各自需要的測試、build、lint、review criteria 與人工驗收條件。
_Avoid_: Redefining quality from scratch in every Task

**Project Planning Task**:
用來設計 Project 架構、開發標準、QA strategy 和 Project Quality Gate 的 Task，可由架構師類 Agent Profile 依 project-planning Workflow Recipe 推進。
_Avoid_: Treating planning-project as only a manual command

**Product Definition Task**:
用來釐清要做什麼、目標使用者、核心價值、MVP 範圍與成功條件的 Task；它必須先於 Project Planning Task。
_Avoid_: Starting architecture before product direction is clear

**Product Definition Review**:
當 Project 已有 PRD 或等效產品文件時，Product Definition Task 會改為檢查、更新和補齊現有方向，而不是從零重新定義。
_Avoid_: Rewriting a valid PRD from scratch

**Quality Gate**:
Project Quality Gate、Workflow Recipe Quality Bar 和 Task/Spec Acceptance Gate 共同形成的具體檢查集合，用來判斷 Task 是否能進入 REVIEW 或 DONE。
_Avoid_: One-size-fits-all testing requirement

**Task/Spec Acceptance Gate**:
單一 Task 或 Spec 根據內容從 Project Quality Gate 中挑選、補充或覆寫的驗收條件。
_Avoid_: Ignoring Project-level quality rules

**Task Type**:
Task 所屬的工作類型，用來決定適用的 Workflow Recipe、Agent Profile 和 Quality Bar；MVP 只內建 coding，未來可延伸 research、analysis、marketing、video production 或 finance。
_Avoid_: One universal quality rule for all work

**Primary Product Flow**:
Grimo 第一版的主要使用流程：使用者先建立或選擇 Project，進入 Task Management Interface，再透過 chat 建立或推進 Task。
_Avoid_: Chat-only homepage, treating chat as the product itself

**Task Management Interface**:
Grimo 用來查看 Task list、Task detail、READY、DEV、REVIEW、worker log 和 Review Materials 的工作管理介面。
_Avoid_: Hiding task state behind chat history

**Task List State**:
Task list / board 上給使用者追蹤進度的跨領域簡化狀態，也是 Task State Machine 的可見狀態集合，例如 BACKLOG、DEFINING、READY、RUNNING、REVIEW、DONE 和 BLOCKED；開發、研究、分析、行銷、影片製作等任務共用這組外層狀態。
_Avoid_: Discuss, Explore, Prototype, Spec, Usage, Tkt, or domain-specific steps as board columns

**BACKLOG**:
尚未排定或尚未開始定義的低承諾 Task 狀態，適合 follow-up、外部匯入或暫存想法。
_Avoid_: Work that is already being actively clarified

**Task Detail Evidence**:
Task detail 中用來建立信任和除錯的詳細證據，例如 CLAIMED、DEV、Wrap evidence、Workflow Step、Quality Loop、quality_score、fix history、worker log、run history、diff、測試輸出或其他 Task Type 的領域 evidence。
_Avoid_: Hiding evidence in raw chat transcript only

**Task-Forming Chat**:
Grimo 用來建立或推進 Task 的對話入口，目標是問清楚工作並收斂成可管理的 Task。
_Avoid_: General assistant chat, loose prompt history

**Task Conversation Thread**:
Task 底下完整、可回放的對話紀錄，包含使用者與 agent 討論、系統事件摘要、附件、引用檔案、外部連結與後續澄清；使用者從 Task 打開 `Chat` 時看到的是這條完整 thread。
_Avoid_: Ephemeral chat session, provider transcript as source of truth, board-level comment preview only

**Task Conversation Preview**:
Task 卡片、detail 摘要或 Chat 收合狀態中顯示的輕量摘要，包含最近幾則對話、重點摘要、未決問題與附件數；它幫使用者快速判斷上下文，但不取代完整 Task Conversation Thread。
_Avoid_: Hiding full conversation, showing raw transcript everywhere, confusing preview with Review Materials

**Task Attachment**:
附在 Task Conversation Thread、Task detail 或 Review Materials 上的檔案、截圖、錄影、log、文件或外部連結。附件可輔助討論、定義、審查或執行 evidence，但清單層只顯示數量或簡短提示。
_Avoid_: List-level source chip, generic label, only storing attachment in provider chat

**External Work Entry Client**:
Codex、Claude Code 或未來 connector 用來把外部對話、指令或 issue 類工作送進 Grimo Task system 的入口。
_Avoid_: Separate task system, provider-owned source of truth

**Ready Gate**:
人類在 Grimo Task Management Interface 中確認 Definition Package 後，Task 才能進入 READY 的產品關卡。
_Avoid_: External client directly marking work READY

**Dispatch Window**:
使用者手動開啟的一段有期限自動派工時間；READY 任務只有在 active Dispatch Window 內，或使用者手動開始單一 Task 時，才會被 Dispatcher 檢查並轉成 Agent Claim。MVP UI 應提供像「執行 1 小時」「執行到明早 8 點」「只跑選取任務」這類有邊界選項，可設定並行數，並可停止 claim 新任務。Dispatch Window 到期或停止後不硬殺已 RUNNING 任務，已開始的任務會執行到結束。
_Avoid_: 24/7 background auto-run, permanent auto toggle, killing running tasks when the window expires, READY means execute immediately

**Follow-up Task**:
Agent 在執行、審查或 Wrap evidence 中發現的新工作，會帶著來源 Task、理由和建議 priority 進入 BACKLOG 或 DEFINING，等待人類確認；由 DONE task 的 Wrap evidence 產生時，預設先回 BACKLOG，並保存來源 Task。
_Avoid_: Agent-created work that starts execution automatically

## Relationships

- **Grimo** presents itself to users as an **AI 開發工作台**.
- A **Project** owns the local repo/codebase where its **Task** work is defined, executed and reviewed.
- A **Task** is user-level work; **Task State Machine** shows its outer progress; **Workflow Step** and **Step Sub-workflow** are internal machinery that advance and improve that Task.
- **Grimo** is engineered internally as a **Local Agent Control Plane**.
- An **Agent Profile** may look human-legible in the UI, but its product meaning is a thin assignment/runtime/skills profile that can support coding and non-coding roles.
- **Future General Workbench Extension** is an architectural extension path, not the MVP positioning.
- **Agent-Facing Task System** is a capability of **Grimo**, not the primary product positioning.
- **Workflow Recipe**, **Skill**, **Quality Gate** and **Review Materials** turn repeatable professional practice into an executable product workflow.
- A user's manual skill development flow should be expressed through **Workflow Recipe**, **Agent Profile**, **Skill** and **Quality Loop**, not as a separate user-facing methodology.
- The **MVP Core Promise** starts from a **Definition Package**, but is only fulfilled when the user receives **Review Materials** after agent execution.
- **Primary Product Flow** starts from Project, continues in **Task Management Interface**, and uses **Task-Forming Chat** to create or advance work.
- A **Task** owns a **Task Conversation Thread**. Cards and collapsed chat surfaces may show a **Task Conversation Preview**, but full conversation and attachments remain accessible from Task detail or Chat.
- **External Work Entry Client** can create or advance the same **Task** records that the **Task Management Interface** shows.
- **External Work Entry Client** may create or advance a defining Task, but only the **Ready Gate** can move it to READY.
- **READY** means schedulable work; a **Dispatch Window** or manual task start is still required before Dispatcher can create an Agent Claim.
- **Task List State** is the shared outer progress abstraction across Task Types; **Task Detail Evidence** contains recipe steps, Quality Loop details and domain evidence for trust and debugging.
- A **State Workflow** explains what happens under a **Task State**; some states run workflow steps, while others are gates, queues, review points or evidence holders.
- A **Follow-up Task** may be proposed by an agent, but it still requires human confirmation before READY.
- **BACKLOG** holds low-commitment work before Grimo actively defines it.
- **Human Review State** starts only after AI self-review, required tests and Review Materials are ready.
- **Task Type** selects the relevant **Workflow Recipe** and **Quality Bar**; Project rules may add local constraints.
- MVP ships with **coding** as the only built-in **Task Type**, while the model remains extensible to research, analysis, marketing, video production and finance.
- **Project Quality Gate** is the baseline; **Task/Spec Acceptance Gate** adapts it for a specific Task.
- **Quality Gate** is used by **Quality Loop** and **Human Review State** to decide whether evidence is sufficient.
- **Product Definition Task** establishes product direction before **Project Planning Task** designs architecture and **Project Quality Gate**.
- **Product Definition Review** is the existing-artifact path of a **Product Definition Task**.

## Example dialogue

> **Dev:** "首頁要說 Grimo 是 Local Agent Control Plane 嗎？"
> **Domain expert:** "不要。首頁說 Grimo 是本地優先的 AI 開發工作台；Local Agent Control Plane 留給工程文件。"
>
> **Dev:** "Project 可以先代表一整個產品 workspace 嗎？"
> **Domain expert:** "MVP 先不要。Project 先是一個本機 repo/codebase，這樣 worktree、測試和審查證據才有明確歸屬。"
>
> **Dev:** "使用者要看到 Discuss、Explore、Prototype、Spec、Usage、Tkt、Dev、Review、Wrap 每個階段嗎？"
> **Domain expert:** "使用者追蹤 Task 狀態就好；這些 Workflow Step 和 Quality Loop 是 Grimo 自動優化與推進任務的內部機制。"
>
> **Dev:** "BACKLOG、DEFINING、READY、RUNNING、REVIEW、DONE 算不算一種狀態機？"
> **Domain expert:** "算。它是 Task State Machine，負責呈現外層進度；每個狀態底下可以有 State Workflow，但不代表每個狀態都會自動跑 agent。"
>
> **Dev:** "每個 workflow step 底下還有自己的子流程嗎？"
> **Domain expert:** "有。這叫 Step Sub-workflow；目前最重要的子流程是 Quality Loop，也就是 Review、Rating、Fix，直到 output 可信才往下一步。"
>
> **Dev:** "第一版只要把 chat 變成任務就算成功嗎？"
> **Domain expert:** "不算。那只是 Definition Package；第一版要讓 Ready Task 被 AI 做完，並交 Review Materials 給人審。"
>
> **Dev:** "Backend Engineer agent 要像 AI 同事一樣有 inbox 和長期人格嗎？"
> **Domain expert:** "不要。UI 可以用人類可讀的角色名，但 Agent Profile 本質是 runtime、skills 和指派規則。"
>
> **Dev:** "Agent Profile 只能是工程角色嗎？"
> **Domain expert:** "MVP 先用在 coding work，但模型不能寫死；未來行銷、財務或營運角色都可以透過對應 skills 加入。"
>
> **Dev:** "那 Grimo 現在要改叫 AI 工作台，不限開發嗎？"
> **Domain expert:** "不要。MVP 對外仍是 AI 開發工作台；架構保留未來泛工作角色，但不把第一版承諾拉寬。"
>
> **Dev:** "這套 SDD 流程是不是要取一個新的產品名？"
> **Domain expert:** "不用。它的本質是把可重複的開發實務工程化成 Workflow Recipe、Skills、Quality Gate 和 Review Materials。"
>
> **Dev:** "我現在手動使用的 planning / frontend / backend / QA / release skills，未來要怎麼放進 Grimo？"
> **Domain expert:** "不要讓使用者每次手動切 skill。Project 選 Workflow Recipe 後，由 Agent Profile 對應 skills、workflow step 和 Quality Loop；使用者主要負責決策與審查。"
>
> **Dev:** "第一版打開 Grimo 要先看到 chat 嗎？"
> **Domain expert:** "先建立或選擇 Project，進入 Task Management Interface；建立任務時再從 chat 開始。"
>
> **Dev:** "Task 點開 Chat 是新的空對話嗎？"
> **Domain expert:** "不是。每個 Task 都有自己的 Task Conversation Thread；點開 Chat 會看到完整對話、附件與後續澄清。收合時只顯示最近幾則和重點摘要。"
>
> **Dev:** "Codex 裡說一句話建立任務，跟 Linear 建 issue 類似嗎？"
> **Domain expert:** "類似。Codex 是 External Work Entry Client；它可以幫使用者建立 Grimo Task，但 Task 狀態和證據仍回到 Grimo 管。"
>
> **Dev:** "Codex 建好任務後可以直接 READY 給 agent 做嗎？"
> **Domain expert:** "不行。Codex 可以建立或推進 defining Task，但 READY 必須由人回到 Grimo 的 Ready Gate 確認。"
>
> **Dev:** "READY 任務會 24 小時被 AI 自動拉去做嗎？"
> **Domain expert:** "不會。READY 代表可排程；使用者要手動開啟 Dispatch Window 或手動開始單一 Task，Dispatcher 才會建立 Agent Claim。Window 到期後不再 claim 新任務，但已 RUNNING 的任務不硬殺。"
>
> **Dev:** "Task board 要把 Discuss、Explore、Spec、Dev、Review 全部攤開嗎？"
> **Domain expert:** "不要。Task board 顯示簡化 Task State Machine；細節頁才展開 Workflow Step、Quality Loop 和執行證據。"
>
> **Dev:** "Board 要顯示 CLAIMED、DEV、WRAP 嗎？"
> **Domain expert:** "不要。Board 顯示 RUNNING；CLAIMED、DEV 是細節頁的執行證據，Wrap evidence 則保存在 DONE task 裡，而且不是每筆任務都會出現。"
>
> **Dev:** "AI reviewer 還在跑時，Task 要放 REVIEW 嗎？"
> **Domain expert:** "不要。REVIEW 代表等人類 approve；AI 自審、單元測試和 E2E 等證據要先完成。"
>
> **Dev:** "進 REVIEW 前到底要跑哪些測試，可以執行時再猜嗎？"
> **Domain expert:** "不行。合格線要在 Project、spec 或 Workflow Recipe 設計階段先定義，執行時照那份 Quality Bar 收集 evidence。"
>
> **Dev:** "coding Task 的測試動作固定都是 unit、integration、E2E 嗎？"
> **Domain expert:** "不是。Project 設計階段會先依專案是前端、後端或全端定義 Project Quality Gate；Task/Spec 再挑選或補充適用項。"
>
> **Dev:** "所有任務都用同一條合格線嗎？"
> **Domain expert:** "不是。每一種 Task Type 都有自己的 Quality Bar；coding、research、analysis、marketing、video production、finance 的 evidence 不會一樣。"
>
> **Dev:** "planning-project 是 command、Task，還是 role 在做的事？"
> **Domain expert:** "它可以是一張 Project Planning Task，由架構師類 Agent Profile 依 project-planning Workflow Recipe 推進，產出 Project Quality Gate。"
>
> **Dev:** "建立 Project 後要直接做 architecture 嗎？"
> **Domain expert:** "不要。先用 Product Definition Task 釐清要做什麼和方向，再進 Project Planning Task。"
>
> **Dev:** "如果 Project 已經有 PRD，還要重新定義產品嗎？"
> **Domain expert:** "不用從零開始。Product Definition Task 變成 Product Definition Review，檢查並更新現有 PRD。"
>
> **Dev:** "MVP 要先內建 research、marketing、finance 嗎？"
> **Domain expert:** "不要。MVP 只內建 coding Task Type，但 schema 保留 research、analysis、marketing、video production、finance 等未來擴展。"
>
> **Dev:** "Agent 發現額外重構機會時，可以直接開工嗎？"
> **Domain expert:** "不行。它可以建立 Follow-up Task，附來源和理由；由 DONE task 的 Wrap evidence 產生時先回 BACKLOG，其他情境可進 BACKLOG 或 DEFINING，等人確認。"
>
> **Dev:** "還沒決定要不要做的 follow-up 要放哪？"
> **Domain expert:** "放 BACKLOG。DEFINING 代表 Grimo 已經開始問清楚和收斂。"

## Flagged ambiguities

- "Grimo 是什麼" 曾同時指向 **AI 開發工作台**、**Local Agent Control Plane** 和 **Agent-Facing Task System**。Resolved: user-facing canonical positioning is **AI 開發工作台**; engineering definition is **Local Agent Control Plane**; external-agent API is a capability.
- "Grimo 是否是泛 AI 工作台" 曾因非開發 Agent Profile 擴展而變模糊。Resolved: MVP positioning remains **AI 開發工作台**; **Future General Workbench Extension** is architectural runway only.
- "Project" 曾可能指產品 workspace 或多 repo 容器。Resolved: MVP **Project** means one local repo/codebase; multi-repo workspace is future scope.
- "Task" 曾可能指使用者工作或 workflow 拆出的步驟。Resolved: **Task** is user-level work; **Workflow Step** is the internal execution/evidence unit under a Task.
- "Task board 狀態" 曾可能被理解成一般欄位名稱。Resolved: BACKLOG / DEFINING / READY / RUNNING / REVIEW / DONE / BLOCKED form the **Task State Machine**.
- "每個狀態都有 workflow" 曾可能被理解成每個狀態都會自動跑 agent。Resolved: use **State Workflow** for the work rules under a state; only some states expand into active **Workflow Step** execution.
- "每個 work 都有 sub workflow" 曾可能混淆 work / step。Resolved: use **Workflow Step** for the main step and **Step Sub-workflow** for the reusable child flow such as **Quality Loop**.
- "Agent" 曾可能指厚 AI coworker、薄 runtime profile 或 coding-only role。Resolved: MVP uses thin **Agent Profile**; teammate-like naming is UI language only, and the model remains extensible to non-coding roles through skills.
- "專業工作實務工程化" 曾可能被命名成新的產品方法論。Resolved: do not add a new product term; express it through **Workflow Recipe**, **Skill**, **Quality Gate** and **Review Materials**.
- "Skill 開發流" 曾可能被理解成使用者仍要手動切換一串 skills。Resolved: Grimo should assign skills through **Workflow Recipe** steps and **Agent Profile** responsibilities, while the user handles decisions and review.
- "MVP 成功" 曾可能只代表產生 **Definition Package**。Resolved: MVP can start its story from definition, but the core promise is fulfilled only when **Review Materials** are produced after agent execution.
- "第一屏入口" 曾在 chat 和 task list 之間搖擺。Resolved: MVP primary flow is Project first, then **Task Management Interface**; **Task-Forming Chat** is used when creating or advancing a Task.
- "Task Chat" 曾可能被理解成一次性 provider session。Resolved: every **Task** owns a durable **Task Conversation Thread** with messages and attachments; collapsed surfaces show a **Task Conversation Preview** only.
- "外部入口權限" 曾可能讓 Codex 直接把工作送到 READY。Resolved: external clients can create or advance defining work only; **Ready Gate** is human-confirmed inside Grimo.
- "READY 自動執行" 曾可能代表 24/7 background auto-run。Resolved: **READY** means schedulable; execution requires a user-started **Dispatch Window** or manual single-task start.
- "Task 進度呈現" 曾可能把 SDD 階段或其他領域步驟直接當 board columns。Resolved: board/list uses shared **Task State Machine**; recipe steps and Quality Loop live in **Task Detail Evidence**.
- "RUNNING" 曾可能被拆成 CLAIMED / DEV / WRAP board columns。Resolved: board uses **RUNNING**; finer execution states live in **Task Detail Evidence**, and Wrap evidence belongs inside the DONE task rather than a guaranteed board state.
- "REVIEW" 曾可能同時代表 AI review 和 human review。Resolved: **Human Review State** means AI review/testing/evidence are complete and the task is waiting for human approval.
- "測試與驗收門檻" 曾可能在每次 run 中臨場判斷或套固定 checklist。Resolved: **Project Quality Gate** is defined during Project design; **Task/Spec Acceptance Gate** adapts it for each Task.
- "planning-project" 曾可能只被理解成手動 workflow command。Resolved: project planning can be represented as a **Project Planning Task** assigned to an architect-like **Agent Profile**.
- "Project onboarding" 曾可能直接從 architecture 開始。Resolved: start with **Product Definition Task**; only after direction is clear should **Project Planning Task** define architecture and quality gates.
- "已有 PRD 的 Project" 曾可能被迫從零重跑 product definition。Resolved: use **Product Definition Review** to review/update existing artifacts.
- "MVP Task Type scope" 曾可能拉到多種工作類型。Resolved: MVP has one built-in **coding** Task Type; future types such as research, analysis, marketing, video production and finance are extension points.
- "Agent 發現新工作" 曾可能直接自動執行。Resolved: agents may create **Follow-up Task** proposals only; execution still requires human confirmation.
- "未排定工作" 曾可能混入 DEFINING。Resolved: **BACKLOG** is the low-commitment holding state; **DEFINING** means active clarification has started.
