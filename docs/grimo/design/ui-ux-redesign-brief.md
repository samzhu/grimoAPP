# Grimo UI/UX Redesign Brief

**Last updated:** 2026-05-28
**Audience:** UI/UX designer, product designer, design engineer
**Purpose:** Provide enough product, workflow, screen, state, and implementation context for a full UI/UX redesign without needing to reverse-engineer the current frontend.

---

## 1. How To Use This Brief

This document is a design handoff for redesign work. It is not a request to preserve the current visual style pixel-for-pixel.

The redesign should preserve:

- Product semantics from `docs/grimo/PRD.md`.
- User-facing workflow boundaries: task creation, definition, ready gate, execution, review, wrap.
- Local-first, project-bound, task-centered information architecture.
- Human approval gates for `READY` and `REVIEW`.
- Evidence-first review experience.

The redesign may change:

- Layout, density, navigation model, typography, color, spacing, and component composition.
- Whether the main task surface is a board, list, split view, timeline, inbox, or hybrid.
- How task detail, evidence, quality score, and workflow progress are visualized.
- Interaction patterns, as long as the product gates remain clear.

Primary source of truth order:

| Layer | Source | Notes |
| --- | --- | --- |
| Product intent | `docs/grimo/PRD.md` | Owns workflow, terminology, gates, user promises. |
| Current UI workflow rules | `docs/grimo/design/ui-ux-workflow.md` | Current frontend implementation discipline and review process. |
| Current design tokens | `docs/grimo/design/tokens.json` | Current tokenized POC values, not necessarily final brand direction. |
| Current implementation | `frontend/src/*` | Useful for screen inventory and current behavior. |
| Visual baselines | `frontend/e2e/task-workbench.visual.spec.ts-snapshots/*.png` | Current screenshots, not final design target. |

### External Designer File Adoption

`docs/grimo/design/DESIGN.md` is an external designer proposal for an Arcane dark-mode workbench. It is useful input, but it is not an immediate replacement for the current UI.

Adopt it in three rounds:

1. Round 1: absorb information structure, state semantics, quality score, evidence model, and copy. Keep the current light color system.
2. Round 2: create a switchable `Arcane theme proposal` or screenshot mock, then discuss whether dark / cyan / glow belongs in the product.
3. Round 3: after brand direction is confirmed, update `docs/grimo/design/tokens.json` and site-wide color tokens.

Round 1 design execution should stay premium utilitarian and evidence-first: flat surfaces, compact typography, muted semantic colors, no glow-driven styling, and no global color-token rewrite.

---

## 2. Product Summary

Grimo is an **AI Development Workbench** and **local agent control plane**.

It is for developers who already use coding agents such as Codex, Claude Code, Gemini CLI / Antigravity CLI, OpenClaw, Aider, or similar tools, but need a stable way to turn work requests into tasks, route them through agent workflows, collect evidence, review the result, and keep the learning loop local.

Grimo is not:

- A new chat model.
- A thin launcher for one provider.
- A generic project management SaaS.
- A workflow console that exposes every internal step as a user-facing object.
- A cloud-only issue tracker.

Grimo is:

- A local-first workbench for one developer's codebases.
- A task system for coding-agent work.
- A control plane for project, task, workflow recipe, agent profile, execution, review materials, and Wrap evidence.
- A source of truth for workflow evidence, even when tasks originate from external clients or issue systems.

---

## 3. Target Users

Primary user:

- A developer who already uses at least one coding agent.
- Comfortable with repo paths, CLI tools, branches, worktrees, PRs, test commands, and review artifacts.
- Wants AI teammates to run work through repeatable workflows instead of ad hoc chat prompts.
- Wants to inspect complete evidence before approving work.

Core user needs:

- See all AI-related project work in one local place.
- Convert vague work requests into well-defined tasks.
- Know which tasks are blocked, ready, running, waiting for review, or done.
- Understand why an agent can or cannot start execution.
- Review evidence, risks, tests, fix history, and quality scores before approval.
- Keep task history and workflow evidence even if external providers, connectors, or cloud services fail.

---

## 4. Product Principles That Affect Design

### P1. Workbench First, Provider Second

The UI should lead with `Project`, `Task`, `Backlog`, `Ready Task`, `Agent Assignment`, `Running`, `Review`, `Done`, and `Blocked`.

Provider names such as Codex, Claude, Gemini, and OpenClaw are runtime choices, not the primary navigation model.

### P2. Chat Creates Work; Execution Needs Confirmation

Chat is a work-entry surface. It can form or refine tasks, but it must not make write actions feel automatic.

Task execution requires a human-confirmed `READY` state, a user-started dispatch window or manual task start, and dispatcher checks.

### P3. Task Is User-Level Work

A Task means one piece of user-visible work. It is not the same as an internal workflow step.

Internal steps such as `Discuss`, `Explore`, `Prototype`, `Spec`, `Usage`, `Tkt`, `Dev`, `Review`, and optional `Wrap` belong in task detail, workflow detail, or evidence views, not as top-level board columns.

### P4. Review Owns Approval

The product state `REVIEW` means the AI's self-review, quality loop, tests or non-applicability explanation, and review materials are ready for the human.

Human approval happens in `REVIEW`, not after `WRAP`; tasks without cleanup or delivery-summary work can move from approved `REVIEW` directly to `DONE`.

### P5. Local-First Ownership

The local store is the source of truth for task, workflow evidence, review history, quality score, fix history, and Wrap evidence.

External issue trackers and providers are projections or execution channels.

### P6. Local Environment Is Variable

The UI must handle missing Java, Docker, git, provider CLI login, filesystem permissions, SQLite/native library support, and port availability.

Missing capabilities should produce understandable `BLOCKED / NEEDS_HUMAN` states with repair guidance.

---

## 5. Core Domain Model For Design

### Project

In MVP, a Project represents one local repo or codebase.

Project owns:

- Repo path / folder binding.
- Project-level workflow recipe.
- Quality gate baseline.
- Runtime capability assumptions.
- Task list and evidence history.

### Task

A Task represents one user-level piece of work.

Task fields designers must preserve or introduce:

| Field | Meaning |
| --- | --- |
| `id` | Human-readable task id, e.g. `GRM-188`. |
| `title` | Task title. |
| `state` | Board-facing state. |
| `source` | Provenance, e.g. `chat`, `codex`, `github_issue`, `manual`. |
| `skill` | Suggested or active skill area. |
| `score` | Quality score, usually out of 10. |
| `step` | Current workflow step, e.g. `Discuss`, `Ready boundary`, `Review`. |
| `updatedAt` | Last update text in the POC. |
| `description` | Short task summary. |
| `acceptance` | Acceptance gate or review material bullets. |
| `gaps` | Missing detail, missing tool, or unresolved issue. |
| `evidence` | Evidence chips, e.g. screenshot, typecheck, risk note. |
| `labels` | User-facing task labels shown on cards. Use the prototype-defined label taxonomy; do not duplicate `source`, workflow recipe steps, or skill/workflow capability names here. |
| `comments` | Discussion or comment count. |
| `conversationThread` | Complete task-owned discussion history: messages, attached files, referenced files, external links, clarifications, and system event summaries. Opens from task `Chat`. |
| `conversationPreview` | Lightweight card/detail/collapsed-chat summary: recent messages, key summary, open questions, and attachment count. It is not the full record. |
| `attachments` | Files, screenshots, videos, logs, docs, or links attached to the task conversation or task detail. List surfaces show count or compact hints only. |

### Board-Facing Task States

The board-facing state set is:

```text
BACKLOG
DEFINING
READY
RUNNING
REVIEW
DONE
BLOCKED
```

Design meaning:

| State | User-facing meaning | Design implication |
| --- | --- | --- |
| `BACKLOG` | Work exists but is not yet actively defined or ready. | Low urgency; should not look actionable like `READY`. |
| `DEFINING` | Task is being clarified into a definition package. | Needs conversation, research, gaps, or acceptance criteria. |
| `READY` | Definition package and quality gate were accepted; work is schedulable but not automatically running. | Show dispatch-window state, manual start affordance, and dispatcher/preflight readiness. |
| `RUNNING` | Agent work is active or claimed. | Show progress, active execution, worker log, and recoverability. |
| `REVIEW` | Human must approve or reject completed evidence. | This is a human inbox state. Review materials must be prominent. |
| `DONE` | Work completed; Wrap evidence may exist inside the task. | Completed record, summary, evidence, learnings when available. |
| `BLOCKED` | Needs human, environment, permission, dependency, or missing detail. | Should surface repair guidance and next best action. |

Important: `BLOCKED` is not currently a board column in the POC board; it appears in the `待處理` view. A redesign can keep that split or make blockers more visible, but blocked work must not disappear.

### Workflow Recipe Steps

For coding tasks, the first recipe is:

```text
Discuss -> Explore -> Prototype -> Spec -> Usage -> Tkt -> Dev -> Review -> DONE
```

Each main step has an automatic `Review -> Rating -> Fix` quality loop until `quality_score > 9`. Wrap evidence is stored inside the DONE task when cleanup, delivery summary, retro, or follow-up proposal exists.

These are internal workflow semantics. They should be inspectable, but the main product surface should stay task-oriented.

Do not style recipe steps, source values, or skill/workflow capability names as task labels in board, list, focus, attention cards, or detail headers. `Prototype`, `Spec`, `Review`, `chat`, and `task-forming` belong in their own semantic fields, not generic card chips. List-level chips should use only user-facing task labels from the existing prototype taxonomy.

Current prototype label taxonomy:

```text
bug, documentation, duplicate, enhancement, good first issue, help wanted,
invalid, question, wontfix, frontend, backend, ci/cd, design, research
```

### Task Conversation And Attachments

Each Task owns a durable Task Conversation Thread. Opening `Chat` from a task must show the full thread for that task, including historical user messages, agent replies, attached files, referenced files, external links, clarifications, and important system event summaries.

Collapsed and list-level surfaces should show a Task Conversation Preview instead of the raw transcript:

- Recent few messages.
- Key summary.
- Open questions.
- Attachment count or compact attachment hint.

Design rules:

- Do not start a blank chat session when the user opens `Chat` from a task.
- Do not render the full raw transcript on board cards, attention cards, or mobile rows.
- Do not use attachments as task labels or source chips.
- Attachments can become review evidence when promoted or linked, but ordinary attached files live in task conversation/detail first.
- The mental model is closer to issue comments attached to work than a standalone chat app, while Grimo adds workflow state, quality gates, evidence, and summaries.

Badge styling must make identity, state, and labels distinguishable at a glance:

| Badge role | Meaning | Visual direction |
| --- | --- | --- |
| Task id | Stable task identity such as `GRM-144` | Rectangular mono token, not a soft label pill. |
| Task List State | Board-facing state such as `BACKLOG` or `REVIEW` | Semantic state badge with stronger weight and status color. |
| Task label | User-facing categorization such as `frontend` | Softer category chip with a small marker and sans-serif text. |
| Metric | Compact value such as quality score | Neutral compact badge, distinct from labels. |

### Quality Score

Quality score is a workflow gate signal. It is not a vanity metric.

Design must communicate:

- Current score.
- Threshold, usually `> 9`.
- Whether evidence is sufficient.
- Whether task can advance or needs fix/review loop.
- Who needs to act next: system, agent, or human.

---

## 6. Primary Jobs And User Flows

### Flow A. Establish Project Context

1. User opens Grimo.
2. User selects or creates a Project.
3. Project binds to a local repo/codebase.
4. Project has workflow recipe, quality gate, and runtime capability expectations.

Design needs:

- Current project identity should be visible.
- Repo path should be available but not dominate every screen.
- Project switching and creation should be clear.
- If local repo or runtime binding is broken, the UI needs a diagnostic state.

### Flow B. Form Work From Chat Or External Entry

1. Work originates from Grimo Chat, Codex, Claude Code, local task creation, GitHub, Linear, Jira, or another connector.
2. Grimo creates or updates a Task.
3. Task enters `BACKLOG` or `DEFINING`.
4. Discussion, research, and acceptance hints turn it into a Definition Package.

Design needs:

- Chat should feel like a work-forming surface, not the whole product.
- A task candidate should be visible when chat identifies one.
- Missing details and open questions should be explicit.
- User should see when a task is created versus just discussed.
- Once work becomes a Task, task `Chat` should preserve the full conversation and attachments for that Task.
- Board, list, and collapsed Chat surfaces should expose only preview information: recent messages, key summary, unresolved questions, and attachment count.

### Flow C. Confirm Ready

1. Task in `DEFINING` has a Definition Package.
2. Human reviews scope, acceptance gate, and quality gate.
3. Human moves task to `READY`.
4. User assigns a thin Agent Profile, e.g. Backend Engineer, Architect, Code Reviewer.
5. Task waits for a user-started dispatch window or manual task start.

Design needs:

- `READY` must feel like a deliberate confirmation.
- Show that `READY` work is schedulable, not automatically running.
- Show whether a dispatch window is active, when it ends, and how to start or stop it.
- Dispatch window controls should be time-boxed choices such as `執行 1 小時`, `執行到明早 8 點`, or `只跑選取任務`, with a concurrency setting, not a permanent automation toggle.
- Show what still blocks dispatcher from claiming the task.
- Do not let external clients directly create `READY` work.

### Flow D. Dispatcher And Execution

1. User manually starts a dispatch window or starts a single `READY` task.
2. Dispatcher checks assignment, dependencies, runtime availability, repo binding, permissions, and risk level.
3. If all pass, an Agent Claim is created.
4. Task enters `RUNNING`.
5. Agent runs recipe steps in a worktree/sandbox and reports evidence.
6. If checks fail, task becomes `BLOCKED / NEEDS_HUMAN`.

Design needs:

- Distinguish `READY` from actively executing.
- Avoid implying a 24/7 background auto-run loop.
- Show remaining dispatch-window time, configured concurrency, queued READY count, current claims, and a stop-new-claims control.
- When a dispatch window expires, show that no new tasks will start while already `RUNNING` tasks continue to completion.
- Show preflight checks and failures.
- Show worker log, run history, current step, and recovery options.
- Make blocked repair actions concrete.

### Flow E. Review And Approve

1. DEV and AI self-review complete.
2. Quality loop and required evidence are complete.
3. Task enters `REVIEW`.
4. Human reviews Definition Package, execution outputs, quality score, diff, tests, risk notes, retro, reviewer findings, and fix history.
5. Human approves or rejects.

Design needs:

- `REVIEW` should act like a human review inbox.
- Evidence must be scannable, comparable, and trustworthy.
- Approval/rejection should be visually distinct from internal AI review.
- If rejected, the path back to Chat or Definition/Execution repair should be obvious.

### Flow F. Optional Wrap And Learning Loop

1. Approved work enters `WRAP` only when cleanup, delivery summary, or short retro is needed.
2. If no wrap work is needed, the task can move directly from approved `REVIEW` to `DONE`.
3. Learning Loop may propose skill or recipe improvements from wrap evidence or accumulated task history.
4. Task becomes `DONE`.

Design needs:

- Show delivered outcome and final evidence.
- Show learnings as proposals, not silent changes.
- Preserve history for future inspection.

---

## 7. Current Frontend App Inventory

Current local app URL:

```text
http://127.0.0.1:5173/
```

Frontend stack:

| Area | Current choice |
| --- | --- |
| Framework | React + Vite |
| Language | TypeScript |
| UI primitives | Custom React components |
| Chat primitives | `@assistant-ui/react` local runtime POC |
| Icons | `@phosphor-icons/react` |
| Styling | Single CSS file with custom properties |
| Visual tests | Playwright screenshots |

Main files:

| Surface | Current implementation |
| --- | --- |
| App shell and routing state | `frontend/src/App.tsx` |
| Navigation rail | `frontend/src/app/Navigation.tsx` |
| Task board | `frontend/src/features/task-board/TaskWorkbench.tsx` |
| Task detail drawer | `frontend/src/features/task-detail/TaskDetail.tsx` |
| Full task detail page | `frontend/src/features/task-detail/TaskDetailPage.tsx` |
| Create task dialog | `frontend/src/features/task-create/CreateTaskDialog.tsx` |
| Task-forming chat | `frontend/src/features/task-forming-chat/AssistantChat.tsx` |
| Blockers / needs-human view | `frontend/src/features/blockers/Blockers.tsx` |
| Projects view | `frontend/src/features/projects/Projects.tsx` |
| Workflow view | `frontend/src/features/workflow/Workflow.tsx` |
| Fixtures | `frontend/src/domain/task/task-fixtures.ts` |
| Types | `frontend/src/domain/task/task-types.ts` |
| CSS | `frontend/src/styles.css` |

Current nav items:

```text
Task 管理
待處理
專案
Chat
Workflow
```

Current app shell:

- Topbar with menu button, `G` brand mark, `Grimo`, current project label, project name, and repo path.
- Optional left rail navigation.
- Main surface changes by selected view.

Current limitation:

- The app is a POC. It has hardcoded project context and fixture tasks.
- Many strings expose implementation language, e.g. `assistant-ui primitives`, `POC adapter`.
- It should not be treated as final product copy.

---

## 8. Current Screens And Behavior

### 8.1 Task Management Board

Current purpose:

- Main workbench surface.
- Shows task state distribution.
- Lets user search and open task detail.
- Lets user create a task draft.

Current visible columns:

```text
BACKLOG
DEFINING
READY
RUNNING
REVIEW
DONE
```

Current card content:

- Task id.
- Title.
- Up to two labels.
- Updated time.
- Comment count.
- Optional conversation preview hint: recent discussion summary, open question count, or attachment count.
- Selected state accent after click.

Current behavior:

- Initial board entry has no selected task.
- Clicking a card selects it and opens task detail.
- Detail drawer can be closed.
- Detail drawer can be pinned.
- Full page detail can be opened from drawer.
- Search filters tasks by query.
- `新增 Task` opens the creation dialog.

Design notes:

- The current board is useful as a process overview, but may become too wide as states or task count grow.
- At narrow desktop widths, horizontal board scrolling exists but is not strongly signaled.
- For redesign, consider whether `REVIEW` and `BLOCKED` need inbox-style prominence beyond columns.

### 8.2 Task Detail Drawer

Current purpose:

- Quick inspect surface while staying on board.
- Shows task state, workflow step, quality, acceptance/review materials, evidence, gaps, and next action.

Current sections:

- Header: `任務詳情`, actions: full page, pin, close.
- Badges: task id, board state, workflow step.
- Title and description.
- `Stage & Quality`.
- `Acceptance Gate` or `Review Materials`.
- `Evidence`.
- `待補缺口`, only when gaps exist.
- Sticky action band.

Current action logic:

- If state is `READY`, action says `開始執行`.
- Otherwise action says `使用 Chat`.
- If state is `REVIEW`, bottom text says `等待人工審查`.

Design notes:

- Review materials need more hierarchy in redesign: evidence type, source, pass/fail, timestamp, command, screenshot, risk.
- Quality score needs more explanation and confidence than a numeric chip alone.
- For `REVIEW`, human approval/rejection controls should probably become primary, once implemented.
- The `Chat` action should open the task's existing Task Conversation Thread, not a generic global chat.
- The drawer may show Task Conversation Preview, but full message history and attachments belong in `Chat` or full task detail.

### 8.3 Full Task Detail Page

Current purpose:

- Larger task detail view for deeper review.

Current sections:

- Back button.
- Primary action.
- Task metadata badges.
- Title and description.
- Main column: Acceptance, Evidence.
- Sidebar: Stage & Quality, gaps if any.

Design notes:

- This should become the main evidence review workspace.
- It should support diff, test output, screenshots, logs, run history, reviewer findings, fix history, and risk notes.
- It should distinguish current task context from historical evidence.

### 8.4 Create Task Dialog

Current purpose:

- Manual task draft creation.
- Creates work that can enter `DEFINING`.

Current fields:

- `標題`.
- `任務內容`.
- `Labels`.
- `建議 skill`.

Current copy:

```text
先建立可進入 DEFINING 的工作草稿；來源由系統依入口保存。
```

Important product rule:

- User should not select `source`; source is system provenance.
- User should not select workflow recipe per task; task inherits Project workflow.
- A new manual task should not imply immediate execution.

- Design notes:

- Redesign should help users provide goal, constraints, success criteria, risk, and missing background/detail without making creation heavy.
- User-facing copy should not use engineering context language for this work; route users to Task detail or `Chat` to continue exploration, planning, clarification, or task refinement.
- Consider progressive disclosure: minimal first, structured definition hints second.

### 8.5 Task-Forming Chat

Current purpose:

- Work entry and task definition support.
- Uses assistant-ui local runtime POC.

Current sections:

- Header.
- Empty thread state: `把討論轉成 Grimo Task`.
- Composer.
- Context strip with linked task or `未連結`.
- Side panels: current linked task and runtime strategy.

Current implementation copy that should be redesigned:

```text
assistant-ui primitives 嵌入 Grimo 版面，用於驗證 composer、thread 與 message 呈現。
目前接的是本地 POC adapter。之後 Spring Boot 提供 API 後，可替換 adapter，不需要重寫 UI。
```

Design notes:

- Replace implementation-facing copy with product-facing copy.
- Chat should show extracted task candidates, definition gaps, suggested next questions, and links to created/updated tasks.
- Chat should not visually imply that the system will execute code immediately.
- When opened from an existing Task, Chat is a durable Task Conversation Thread with complete message history, attached files, referenced files, external links, and follow-up clarifications.
- When Chat is collapsed or represented from a card/detail summary, show only recent messages, key summary, open questions, and attachment count.
- Attachments are not labels, source metadata, or board-level chips. Keep them in Chat/detail, and promote them to Review Materials only when they are evidence for approval.

### 8.6 Blockers / Needs-Human View

Current purpose:

- Collect tasks that need human intervention.

Current data:

- Blocked task id.
- Reason from `gaps`.
- Suggestion: permissions, user decision, or return to Chat.

Design notes:

- This is likely an important operational inbox.
- Blockers should show owner, type, urgency, exact failing capability, repair path, and whether task can retry automatically.
- `Chat` is the user-facing return path for continued exploration or planning; do not introduce a separate context-filling action.
- Attention cards should show real task labels, not workflow recipe steps.

### 8.7 Projects View

Current purpose:

- Manage local repo/codebase project binding.

Current UI:

- Existing project card.
- New project form with name and folder.

Design notes:

- Project onboarding matters because PRD says product direction comes before architecture.
- Future design should support Product Definition Review, Project Planning, architecture/standards/QA strategy readiness, and Project Quality Gate status.

### 8.8 Workflow View

Current purpose:

- Explain task lifecycle mapping and quality loop.

Current UI:

- Recipe state mapping table.
- Quality loop list: `審查`, `評分`, `修正`, `quality_score > 9`.

Design notes:

- This should help advanced users understand and configure workflows, but should not turn the whole product into a workflow engine console.
- Consider separating "workflow explanation", "project workflow settings", and "per-task execution trace".

---

## 9. Current Visual Baselines

Current visual screenshots are stored at:

```text
frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-workbench-desktop-1366-chromium-darwin.png
frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-workbench-desktop-1440-chromium-darwin.png
frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-workbench-tablet-820-chromium-darwin.png
frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-workbench-mobile-390-chromium-darwin.png
frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-detail-drawer-chromium-darwin.png
frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-detail-full-page-chromium-darwin.png
frontend/e2e/task-workbench.visual.spec.ts-snapshots/create-task-dialog-chromium-darwin.png
```

Current Playwright visual scenarios:

| Scenario | Viewport |
| --- | --- |
| Board baseline desktop | 1366 x 768 |
| Board baseline desktop | 1440 x 900 |
| Board baseline tablet | 820 x 1180 |
| Board baseline mobile | 390 x 844 |
| Selected task opens detail drawer | 1440 x 900 |
| Create task dialog baseline | 1440 x 900 |
| Full page detail baseline | 1440 x 900 |

Run visual checks:

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run test:visual
```

Update snapshots after intentional visual changes:

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run test:visual:update
```

---

## 10. Current Design Language

The current POC visual language is compact, utilitarian, and dashboard-like.

Current CSS tokens in `frontend/src/styles.css` mirror `docs/grimo/design/tokens.json`:

| Token | Current value | Role |
| --- | --- | --- |
| `--bg` | `oklch(98% 0.004 245)` | App background. |
| `--surface` | `oklch(100% 0 0)` | Main surface and cards. |
| `--surface-soft` | `oklch(96.5% 0.004 245)` | Subtle panels and chips. |
| `--fg` | `oklch(18% 0.012 245)` | Primary text. |
| `--muted` | `oklch(49% 0.018 245)` | Secondary text. |
| `--border` | `oklch(90% 0.007 245)` | Borders. |
| `--accent` | `oklch(55% 0.135 152)` | Primary action / selected context. |
| `--warn` | `oklch(66% 0.135 78)` | Warning. |
| `--danger` | `oklch(58% 0.16 28)` | Danger / blocked. |
| `--info` | `oklch(57% 0.14 248)` | Info. |
| `--radius` | `6px` | Default radius. |
| `--radius-lg` | `8px` | Panels/dialogs. |
| `--detail-width` | `clamp(460px, 34vw, 720px)` | Detail drawer. |

Current typography:

- System UI sans: Apple / Segoe / system.
- Mono: JetBrains Mono / SF Mono / ui-monospace.
- Small, dense text.
- No marketing hero typography.

Current shape and hierarchy:

- 6px to 8px radius.
- Thin borders.
- Minimal shadows.
- Green primary action.
- Badges as small pill chips.
- Panels use header strip + body.

Design direction note:

- A redesign can replace the visual language, but should stay workbench-first, scannable, and credible for developer operations.
- Avoid a landing-page hero aesthetic for the product surface.
- Avoid decorative cards for everything; this is an operational product.
- Avoid making chat visually dominate the product, because PRD says Task Management Interface is the main interface.

---

## 11. Responsive And Layout Behavior

Current behavior:

- Topbar height is normally `52px`.
- At widths below `920px`, topbar wraps and project path can occupy a second line.
- Expanded nav rail width is `172px`.
- At widths below `920px`, rail becomes horizontal.
- Board columns are `repeat(6, minmax(184px, 1fr))`.
- At widths below `920px`, board becomes single-column vertical.
- Detail drawer is fixed on desktop; at widths below `920px`, it becomes a bottom sheet with height `min(72vh, 620px)`.
- Chat view is two-column: main chat + 340px side panel.
- At widths below `1180px`, chat stacks to one column.

Responsive design questions:

- Should mobile use a board, list, inbox, or state-tabs pattern?
- Should desktop prioritize all columns visible, or prioritize selected task + review detail?
- Should `REVIEW` be an inbox separate from the board on small screens?
- Should `BLOCKED` be globally surfaced as a status banner or persistent inbox count?
- Should project path be always visible, collapsed, or behind a project switcher?

---

## 12. UX Issues Observed In Current POC

These are observations from opening the current frontend at `http://127.0.0.1:5173/` on 2026-05-28.

### Issue 1. POC Copy Leaks Implementation Details

Examples:

- `assistant-ui primitives 嵌入 Grimo 版面`
- `本地 POC adapter`
- `正式版改接 Spring Boot REST/SSE`

Why it matters:

- Users need product language, not implementation notes.
- Designers should replace these with work-forming language.

### Issue 2. Project Context Is Hardcoded And Verbose

Current topbar shows:

```text
目前專案 grimo/web /Users/samzhu/workspace/github-samzhu/grimo/apps/web
```

Why it matters:

- The repo path is useful but visually heavy.
- Project identity should be visible without stealing hierarchy from the active task surface.

### Issue 3. Board Scrolling Affordance Is Weak

At narrower desktop widths, the board horizontally scrolls.

Why it matters:

- Users may not notice later states offscreen.
- `REVIEW` and `DONE` can become hidden even though they represent important state.

### Issue 4. Review Evidence Is Too Shallow

Current evidence chips are text-only labels such as:

```text
Playwright screenshot
typecheck
risk note
```

Why it matters:

- Review is a core value proposition.
- Evidence needs trust signals: command, result, timestamp, source, artifact link, diff, screenshot preview, risk status.

### Issue 5. Quality Score Needs More Context

Current score is displayed as `9.4 / 10` with pass/fail text.

Why it matters:

- Users need to know who scored it, what rubric applied, what evidence backed it, and what changed after fix attempts.

### Issue 6. `READY` And Dispatcher Are Under-Explained

Current `READY` task shows action `開始執行`.

Why it matters:

- `READY` does not mean execution can skip dispatcher checks.
- `READY` does not mean Grimo is running a 24/7 background auto-dispatch loop.
- The UI should show assignment, dependencies, capability checks, risk, whether claim is allowed, whether a dispatch window is active, and when that window ends.

### Issue 7. `BLOCKED` Is Separate From Main Board

Current board does not include `BLOCKED`; it appears in `待處理`.

Why it matters:

- This can reduce noise on the main board, but blockers need strong visibility.
- A redesign should intentionally decide how blocked work is surfaced.

### Issue 8. Chat Is Functionally Present But Product Semantics Are Thin

Current chat page shows a composer and empty state but not yet task candidates or extracted gaps.

Why it matters:

- The PRD makes task-forming chat central to `Discuss`.
- Chat should show how conversation becomes structured task data.

---

## 13. Redesign Goals

The redesign should answer these questions better than the current POC:

1. What project am I working in?
2. What work exists?
3. What needs my attention now?
4. Which tasks are ready for agents?
5. Which tasks are running, and what are they doing?
6. Which tasks need review?
7. What evidence must I inspect before approval?
8. Why is a task blocked?
9. What exactly will happen if I click the primary action?
10. How did this task get here, and what source does it sync with?
11. What workflow recipe governs this task?
12. What did the AI learn, and what workflow improvements are proposed?
13. What was the recent conversation context, and where can I open the full task conversation and attachments?

---

## 13.1 Round 1 Intake Notes

Round 1 should absorb information architecture and workbench semantics from the Arcane design direction without adopting the dark color system yet.

Keep for Round 1:

- A focus view or attention band that makes tasks requiring human action visible immediately.
- A `needs review` / `needs attention` count near the top-level workbench controls.
- Larger review cards for tasks that are waiting on human approval, especially `REVIEW` tasks with quality score and evidence readiness.
- A compact board or list below the focus area so the user can still understand the full workflow state.
- List-level action buttons should route to `Chat` for discussion and clarification; do not add separate `審查材料` or `查看缺口` buttons in attention/focus cards.
- Task cards and collapsed Chat should show Task Conversation Preview only: recent messages, key summary, open questions, and attachment count.
- Opening `Chat` from a Task should reveal the complete Task Conversation Thread with attachments and links.

Do not copy for Round 1:

- Duplicate create-task CTAs in the same viewport. The workbench should have one primary `新增 Task` entry point, with secondary creation paths only when they are context-specific and visually subordinate.
- Dark theme, cyan glow, starfield background, or Arcane color tokens. These belong to the later theme proposal round.
- Decorative effects that compete with scanning task state, quality, evidence, and next action.

Design implication:

- The first redesigned task workbench can be a hybrid of `Focus + Board`: an attention-first review lane at the top, followed by the existing workflow board. This should help the user quickly see what must be handled now while preserving the current system model.

---

## 14. Redesign Deliverables Requested From UI/UX

Recommended design deliverables:

- Product information architecture.
- Navigation model for desktop and mobile.
- Main task workbench design.
- Task detail / review evidence design.
- Task-forming chat design.
- Create task flow.
- Project onboarding / project switcher flow.
- Blocked / needs-human repair flow.
- Workflow recipe / quality loop explanation view.
- Visual design system: colors, typography, spacing, radius, elevation, icons.
- Component state matrix.
- Responsive behavior definitions.
- Empty, loading, error, blocked, review, and done states.

Helpful optional deliverables:

- Design tokens in DTCG-compatible JSON.
- Clickable prototype.
- Screenshot annotations for priority user flows.
- Copy deck for product language.
- Accessibility notes.

---

## 15. Required Component States

### Task Card

Needs states:

- Default.
- Hover.
- Focus.
- Selected.
- Blocked.
- Needs human.
- Ready.
- Running.
- Review waiting.
- Done.
- External sync-conflict indicator, only when it requires human attention.
- Has unread comments.
- Has attachments.
- Has open questions.
- Has missing details or open questions.

### Task Detail

Needs states:

- Drawer.
- Full page.
- Pinned drawer.
- Closed drawer.
- No selected task.
- Ready to execute.
- Running.
- Review pending.
- Rejected.
- Blocked.
- Done.

### Evidence Item

Needs states:

- Passed command.
- Failed command.
- Screenshot/image artifact.
- Diff artifact.
- Log artifact.
- Risk note.
- Reviewer finding.
- Fix history item.
- Missing evidence.
- Not applicable with reason.

### Chat Composer

Needs states:

- Empty.
- Typing.
- Sending.
- Message failed.
- Linked to task.
- Creating new task candidate.
- Updating existing task.
- Open question prompts.
- Attach files, screenshots, logs, docs, or links.
- Promote an attachment to evidence when it becomes review material.

### Primary Actions

Needs clear copy and state for:

- Create task draft.
- Continue definition.
- Confirm ready.
- Run preflight.
- Start execution.
- Use Chat.
- Approve review.
- Reject review.
- Retry blocked task.
- Open evidence.
- Chat.
- Create follow-up task.

---

## 16. Content And Copy Guidelines

Use product language:

- `Task`
- `Project`
- `Definition Package`
- `Ready Task`
- `Review Materials`
- `Evidence`
- `Quality Gate`
- `Dispatcher`
- `Agent Claim`
- `Wrap evidence`
- `Learning Proposal`

Avoid exposing implementation language to ordinary users:

- `assistant-ui primitives`
- `POC adapter`
- `Spring Boot REST/SSE`
- `React component`
- `fixture`
- `LocalRuntime`

Good user-facing wording examples:

| Current idea | Better product-facing direction |
| --- | --- |
| POC adapter | Local task-forming engine |
| assistant-ui primitives | Chat workspace |
| typecheck | TypeScript check passed |
| risk note | Remaining risk |
| capability probe | Local capability check |
| source chips | Work origin in task detail only |

Designer should define final terminology and tone, but product semantics must remain intact.

---

## 17. Accessibility And Interaction Requirements

Design should account for:

- Keyboard navigation across nav, board/list, task cards, drawer, modal, and chat composer.
- Clear focus states.
- Accessible names for icon-only actions.
- Distinguishable state without relying only on color.
- Sufficient contrast for muted text, chips, borders, and selected states.
- Large enough touch targets on mobile.
- Avoiding hidden horizontal scroll traps.
- Clear modal focus and escape behavior.
- Long task titles and long repo paths.
- Evidence lists with many artifacts.

Current implementation already uses some accessible labels:

- Topbar menu has `收合主選單` / `展開主選單`.
- Detail actions have labels such as `固定任務詳情`, `關閉任務詳情`.
- Create dialog uses `role="dialog"` and `aria-modal="true"`.

These should be preserved or improved.

---

## 18. Current Verification Commands

Run frontend:

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run dev -- --port 5173
```

Build:

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run build
```

Visual regression:

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run test:visual
```

Visual snapshot update:

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run test:visual:update
```

---

## 19. Redesign Acceptance Criteria

A proposed redesign should be considered incomplete unless it covers:

- Project context.
- Task management as the main product surface.
- Task-forming chat as a work-entry surface.
- Task Conversation Thread as the complete task-owned discussion and attachment record.
- Task Conversation Preview on cards, detail summaries, and collapsed Chat states.
- Board-facing task states or an equivalent progress model.
- A focus view or attention band that surfaces tasks requiring human action before the full board.
- A single primary create-task CTA per viewport, avoiding duplicate `新增 Task` buttons with the same scope.
- `READY` human confirmation and dispatcher/preflight distinction.
- `RUNNING` execution progress and recoverability.
- `REVIEW` human approval with complete review materials.
- `BLOCKED / NEEDS_HUMAN` repair guidance.
- Evidence, quality score, fix history, risk, and reviewer findings.
- Local-first project/repo ownership.
- Responsive behavior for desktop, tablet, and mobile.
- Empty, loading, error, blocked, and review states.
- Product copy that does not expose POC implementation details.

Minimum view coverage:

| View | Required redesign coverage |
| --- | --- |
| Task workbench | Main work tracking, filtering/searching, state overview, attention cues. |
| Task detail | Summary, gates, conversation preview, attachments, evidence, workflow, quality, actions. |
| Review workspace | Approval/rejection, evidence comparison, risk, fix history. |
| Task-forming chat | Conversation to structured task, full task thread when linked, attachments, open questions, task candidate. |
| Create task | Manual draft creation without workflow or source overexposure. |
| Blockers | Needs-human repair queue. |
| Projects | Local repo/codebase binding and readiness. |
| Workflow | Recipe and quality loop explanation/configuration. |

---

## 20. Open Design Questions

These are intentionally left open for the designer:

1. Should the primary task surface be a Kanban board, grouped list, inbox, timeline, or hybrid?
2. Should `REVIEW` be a dedicated inbox instead of just another state column?
3. Should `BLOCKED` live in the main board, a separate needs-human inbox, or both?
4. How should quality score be visualized so it feels trustworthy, not decorative?
5. How should evidence scale from three chips to dozens of artifacts?
6. What is the best mobile model for task state navigation?
7. How visible should repo paths be in the global shell?
8. Should workflow recipe configuration be part of Project Settings or a standalone Workflow area?
9. How should task-forming chat show extracted task fields without turning into a form too early?
10. How should external work item sync conflicts be represented?
11. How should Learning Loop proposals appear after wrap?
12. How much personality should Grimo have while staying credible for engineering work?
13. How much of Task Conversation Preview belongs on a card before it becomes too dense?

---

## 21. Do Not Lose These Product Constraints

- Chat is not the product center; Task Management Interface is.
- New tasks do not execute immediately.
- External clients cannot bypass Ready Gate.
- Task source is provenance and should be system-controlled.
- Task source belongs in task detail only; list, board, attention, and creation surfaces should prioritize state, gaps, quality, evidence, and next action instead.
- Workflow recipe is Project-level, not selected per task at creation time.
- `REVIEW` is for human approval after evidence is ready.
- `WRAP` is optional cleanup and summary after approval, not a guaranteed board state.
- Follow-up tasks are proposals, not automatic scope expansion.
- Local store is source of truth.
- Provider/runtime is adapter detail, not product identity.
- Agent Profile is thin and human-readable, not a full AI coworker personality model.
- Board-facing states stay simple; internal recipe steps belong in detail/evidence/workflow views.

---

## 22. Reference Links Inside Repo

Use these while redesigning:

- Product PRD: `docs/grimo/PRD.md`
- UI workflow discipline: `docs/grimo/design/ui-ux-workflow.md`
- Design tokens: `docs/grimo/design/tokens.json`
- Review prompts: `docs/grimo/design/webwright-prompts.md`
- Frontend README: `frontend/README.md`
- Visual test spec: `frontend/e2e/task-workbench.visual.spec.ts`
- Current frontend CSS: `frontend/src/styles.css`
- Current task fixtures: `frontend/src/domain/task/task-fixtures.ts`
