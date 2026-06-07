# Grimo Context

Grimo 的領域語言用來區分使用者看見的任務狀態、AI 自動工作的內部步驟，以及人類審查點。

## Language

**Project**:
使用者在 Grimo 裡管理的一個本機 repo 或 codebase。
_Avoid_: Workspace, multi-repo container, folder-only record

**Project Path**:
Project 的主要開發目錄，是 backend 可操作的 repo / codebase path。
_Avoid_: Project Workspace, folderPath, Task Worktree

**Project Home**:
Grimo 為一個 Project 保存內部資料與證據的本機管理位置。
_Avoid_: External repo, app-wide data folder, Workspace, projectDataPath API field

**Task Worktree**:
單一 Task 執行時使用的隔離工作目錄。
_Avoid_: Project Path, Project, Workspace

**Auto-Review**:
Agent 完成主要工作後，先自動檢查成果是否足以交給人類審查的 RUNNING 內部工作步驟。
_Avoid_: Human Review, Review board state, AI Review

**Human Review**:
人類根據 Review Materials 決定 approve 或 reject 的審查點。
_Avoid_: Auto-Review, internal Quality Loop review

**Human Review State**:
Task State Machine 中等待人類 approve 或 reject 的外層狀態。
_Avoid_: Auto-Review still running, internal Quality Loop review

**Workflow Recipe**:
內建的一種 **Workflow Definition**，決定 Task 會經過哪些內部步驟與需要哪些能力。
_Avoid_: Per-task skill picker, ad hoc prompt sequence

**Workflow Definition**:
可以被 Project 選用的 workflow 來源；MVP 來源是內建 **Workflow Recipe**，未來也可以是 workflow file。
_Avoid_: Task-owned copy, active Workflow Run, UI projection

**Skill**:
Agent 在 Workflow Recipe 某一步使用的能力包。
_Avoid_: Task create form field, user-selected task category

**Workflow Run**:
Task 開始執行自己的 **Task Workflow** 後的一次執行脈絡。
_Avoid_: Backlog Task, Task root record, draft

**Task Workflow**:
Task 建立時從 Project Workflow Recipe 或 workflow file 複製出來、歸屬於該 Task 的固定流程副本。
_Avoid_: Active Workflow Run, live Project recipe, workflow evidence

**App Header**:
Grimo 畫面最上方的全域區域，顯示品牌、主選單入口和目前 Project context。
_Avoid_: Project onboarding area, page body, Topbar as product term

**Side Navigation**:
Grimo 用來切換主要 page view 的可收合側邊導覽。
_Avoid_: Navigation rail, Activity Bar, Project list, page content

**Main Content Area**:
App Header 下方顯示目前 page view 或 Project Selection Gate 的主要工作區。
_Avoid_: App Header, Side Navigation, page chrome, generic surface

**Task Details Pane**:
選到 Task 後顯示該 Task 摘要、品質、證據和下一步的輔助內容區。
_Avoid_: Task Chat, Review Materials, full Task page

**Task Details Drawer**:
Task Details Pane 以浮出覆蓋方式顯示的狀態。
_Avoid_: Permanent details pane, full Task page

## Relationships

- A **Project** has one primary **Project Path** in MVP.
- A **Project Home** belongs to exactly one **Project**.
- A **Task** belongs to exactly one **Project**.
- A **Task Worktree** belongs to one Task and is derived from a **Project Path** only when execution needs isolation.
- A **Project** selects a **Workflow Definition**.
- A **Task** copies its Project's **Workflow Definition** into a **Task Workflow** when created.
- A **Task Workflow** is immutable after creation; changing a live definition does not rewrite existing Task Workflows.
- A **Task** with **Task State** `BACKLOG` has a **Task Workflow** but has not started a **Workflow Run** until its Chat is opened for the first time.
- `workflowSummary.currentStep` describes active execution progress only; a **Task Workflow** first step must not be projected as current progress.
- The first Chat open for a `BACKLOG` **Task** atomically moves the Task to `DEFINING`, starts the **Workflow Run**, copies execution steps from the **Task Workflow**, and activates the opening step.
- A **Skill** is selected through **Workflow Definition** steps and Agent Profile responsibilities, not by the user during Task creation.
- **Auto-Review** happens before **Human Review**.
- **Human Review** is the user-facing decision point inside **Human Review State**.
- **Auto-Review** is still agent work; **Human Review State** starts only after Review Materials are ready.
- The **App Header** and **Side Navigation** frame the app; the selected page is shown in the **Main Content Area**.
- A **Task Details Pane** belongs to the selected **Task** and may appear as a **Task Details Drawer** when it floats over the **Main Content Area**.

## Example dialogue

> **Dev:** "Can we show one Review step for both AI and human review?"
> **Domain expert:** "No. **Auto-Review** is still agent work, while **Human Review** is where the user approves or rejects the result."
>
> **Dev:** "Should the create form ask for a Workspace or a projectPathSource?"
> **Domain expert:** "No. MVP uses one **Project Path** field; **Project Home** is internal, and **Task Worktree** is a later execution detail."
>
> **Dev:** "Should the Task create form ask for a **Skill**?"
> **Domain expert:** "No. The Project's **Workflow Definition** already defines the fixed development flow and the skills needed by each step."
>
> **Dev:** "Can we create a **Task** before the user selects a **Project**?"
> **Domain expert:** "No. A **Task** without a **Project** is an orphan and cannot own workflow evidence correctly."
>
> **Dev:** "Should creating a **Task** immediately create a **Workflow Run**?"
> **Domain expert:** "No. Creating a **Task** puts it in `BACKLOG` and copies a **Task Workflow**; opening Chat for that BACKLOG Task the first time moves it into `DEFINING` and starts the **Workflow Run**."
>
> **Dev:** "Can the first Chat open update Task state first and create workflow rows later?"
> **Domain expert:** "No. The first Chat open is one transition. The user should not see a Task in `DEFINING` without a run, or a run while the Task is still `BACKLOG`."
>
> **Dev:** "Can a `BACKLOG` Task card show the **Task Workflow** first step as `workflowSummary.currentStep`?"
> **Domain expert:** "No. `currentStep` means active execution progress. The Task Workflow can tell us the planned first step, but it is not current progress."
>
> **Dev:** "Can recipe or workflow file edits update existing **Task Workflow** rows?"
> **Domain expert:** "No. A Task Workflow is the Task-owned copy created at Task creation time. Later changes require an explicit migration or rebase action."
>
> **Dev:** "Does a file-based Workflow Definition require a content hash to define the Task Workflow version?"
> **Domain expert:** "No. The copied Task Workflow rows are the version. A hash is optional diagnostic metadata, not the source of truth."
>
> **Dev:** "Should the first-run Project setup message go in the **App Header**?"
> **Domain expert:** "No. The **App Header** shows global Project context; onboarding belongs in the **Main Content Area**."
>
> **Dev:** "Is the left menu a navigation rail?"
> **Domain expert:** "No. It is **Side Navigation** because it expands with labels and switches page views."

## Flagged ambiguities

- "Review" was used for both **Auto-Review** and **Human Review**; resolved: recipe previews should name `Auto-Review` as RUNNING evidence and reserve REVIEW for human approval.
- "Workspace" was used for Project identity, local path and future isolated execution; resolved: MVP product language uses **Project** and **Project Path**, while isolated execution uses **Task Worktree**.
- "`projectPathSource` / `projectDataPath`" were considered as API fields; resolved: S003 exposes only **Project Path**, and keeps **Project Home** internal.
- "`skill`" appeared as a Task creation field in prototype UI; resolved: Task creation does not choose Skill because the Project **Workflow Definition** defines the fixed development flow and required skills.
- "Task without Project" was considered by the fixture/demo board path; resolved: real Task creation always requires a selected **Project** and stored `project_id`.
- "`Task draft` / `Task 草稿`" was used for newly created work; resolved: it is just a **Task** with **Task State** `BACKLOG`, not a separate Task kind.
- "Workflow run on Task creation" was considered; resolved: `BACKLOG` Task creation creates a **Task Workflow**, not a **Workflow Run**; the first Chat open for that Task is the workflow entry.
- "First Chat transition" was considered as separate Task update plus workflow initialization; resolved: it is a single atomic transition from `BACKLOG` to `DEFINING` with active run creation.
- "`workflowSummary.currentStep` from copied workflow" was considered; resolved: no active run means `currentStep = null`, even when Task Workflow steps exist.
- "Updating copied Task Workflow when definition changes" was considered; resolved: Task Workflow is immutable, and changes require explicit migration/rebase.
- "Requiring `source_hash` for file-based workflow definitions" was considered; resolved: no, the copied Task Workflow rows are sufficient source of truth, and hash remains optional diagnostics.
- "Topbar / Rail / Main surface" were used as mixed design and implementation terms; resolved: product language uses **App Header**, **Side Navigation**, and **Main Content Area**.
