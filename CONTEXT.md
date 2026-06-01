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

**AI Review**:
Agent 完成主要工作後，先自行檢查成果是否足以交給人類審查的內部工作步驟。
_Avoid_: Human Review, Review board state

**Human Review**:
人類根據 Review Materials 決定 approve 或 reject 的審查點。
_Avoid_: AI Review, internal Quality Loop review

**Human Review State**:
Task State Machine 中等待人類 approve 或 reject 的外層狀態。
_Avoid_: AI reviewer still running, internal Quality Loop review

## Relationships

- A **Project** has one primary **Project Path** in MVP.
- A **Project Home** belongs to exactly one **Project**.
- A **Task Worktree** belongs to one Task and is derived from a **Project Path** only when execution needs isolation.
- **AI Review** happens before **Human Review**.
- **Human Review** is the user-facing decision point inside **Human Review State**.
- **AI Review** is still agent work; **Human Review State** starts only after Review Materials are ready.

## Example dialogue

> **Dev:** "Can we show one Review step for both AI and human review?"
> **Domain expert:** "No. **AI Review** is still agent work, while **Human Review** is where the user approves or rejects the result."
>
> **Dev:** "Should the create form ask for a Workspace or a projectPathSource?"
> **Domain expert:** "No. MVP uses one **Project Path** field; **Project Home** is internal, and **Task Worktree** is a later execution detail."

## Flagged ambiguities

- "Review" was used for both **AI Review** and **Human Review**; resolved: recipe previews should name them separately.
- "Workspace" was used for Project identity, local path and future isolated execution; resolved: MVP product language uses **Project** and **Project Path**, while isolated execution uses **Task Worktree**.
- "`projectPathSource` / `projectDataPath`" were considered as API fields; resolved: S003 exposes only **Project Path**, and keeps **Project Home** internal.
