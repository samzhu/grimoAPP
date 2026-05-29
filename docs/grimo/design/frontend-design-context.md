# Frontend Design Context

This file preserves frontend UI/UX decisions, page-level rationale, browser-comment outcomes, and visual gate evidence for Grimo.

## 1. Purpose

Grimo frontend design work should leave a durable context trail. When requirements, browser comments, UI copy, responsive behavior, layout choices, or visual regression evidence appear during development, record the stable decision here so future UI work does not need to reconstruct it from chat history.

## 2. Global Design Principles

- **Decision:** Round 1 redesign keeps the current light color system.
  **Why:** The user explicitly wants to absorb information structure, state semantics, quality score, evidence, and copy first; dark / cyan / glow belongs to a later Arcane theme proposal.
  **Evidence:** `docs/grimo/design/ui-ux-redesign-brief.md`, `docs/grimo/design/DESIGN.md`, user direction on 2026-05-28.
  **Status:** active

- **Decision:** Treat `docs/grimo/design/DESIGN.md` as an external designer proposal with three adoption rounds.
  **Why:** The file describes a dark Arcane workbench direction, but the user explicitly staged adoption to reduce churn and preserve reviewability.
  **Evidence:** User direction on 2026-05-28.
  **Status:** active

  Round 1: absorb information structure, state semantics, quality score, evidence model, and copy only; keep the current light theme.

  Round 2: create a switchable `Arcane theme proposal` or screenshot mock for discussion; do not replace the default app theme.

  Round 3: after brand direction is confirmed, update `docs/grimo/design/tokens.json` and site-wide color tokens.

- **Decision:** Use premium utilitarian minimalism as the Round 1 execution frame.
  **Why:** Grimo is a dense local workbench, not a marketing page. The UI should stay readable, flat, light, evidence-first, and restrained while absorbing the richer state semantics from the designer file.
  **Evidence:** `minimalist-ui` skill, `design-taste-frontend` audit discipline, user direction on 2026-05-28.
  **Status:** active

- **Decision:** Avoid duplicate primary create-task CTAs in the same viewport.
  **Why:** Duplicate `新增 Task` buttons create unclear command hierarchy.
  **Evidence:** Browser comment comparing the Arcane mockup with two create buttons.
  **Status:** active

- **Decision:** Visual changes must pass the visual gate before completion.
  **Why:** Playwright snapshots provide stable desktop, tablet, and mobile evidence; behavior assertions capture interaction semantics that screenshots alone cannot.
  **Evidence:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`.
  **Status:** active

## 3. Page Context

### Task Workbench

**Purpose:** Main task management surface for tracking task state, finding work that needs human attention, and opening task detail/review flows.

**Current layout decisions:**

- Desktop uses `Focus + Board`: the top attention tray surfaces `REVIEW` and `BLOCKED` tasks; the Kanban board remains below for workflow state scanning.
- The focus tray is collapsible. Users may not want to process attention items immediately, so the tray can shrink to a summary row.
- The focus header is a single line: `待處理焦點` plus a small `需要你處理` status tag. The tag should not occupy its own row.
- Search and `新增 Task` stay in the same toolbar row on desktop. Mobile may stack them.
- Main navigation opens as an overlay by default. It only changes the workbench layout after the user presses the pin button.

**Responsive behavior:**

- Desktop: focus tray plus horizontal Kanban board.
- Tablet/mobile breakpoint: board is hidden and replaced with a grouped task list.
- Mobile list rows show state, task id, title, labels, updated time, score, and comments. Task source is not shown outside detail.
- Card labels must be user-facing categorization only and must come from the prototype-defined task label taxonomy. Do not place `source` values such as `chat` or skill/workflow capability names such as `task-forming` into list-level label chips.
- Cards and mobile rows may show Task Conversation Preview cues such as recent discussion summary, open question count, comment count, or attachment count, but they must not render the full raw transcript.

**Component notes:**

- `.focus-strip`: attention tray for human action. It must support expanded and collapsed states.
- `.focus-toggle`: controls focus tray collapse/expand. It must use exact visible labels `收合` and `展開`.
- `.board-grid`: desktop board only. Do not force Kanban columns into mobile.
- `.mobile-task-list`: mobile and tablet list surface. It groups tasks by board-facing state and includes `BLOCKED`.
- `.rail`: unpinned main nav is overlay. `.nav-pinned` is the only state that should allocate a left column.

**Open questions:**

- Whether focus collapse state should persist across reloads once user preferences exist.
- Whether attention tasks should include `READY` in addition to `REVIEW` and `BLOCKED`, especially when a user-started dispatch window is active.

### Task Detail

**Purpose:** Drawer/full-page review surface for task state, workflow step, quality, evidence, acceptance materials, gaps, and next action.

**Current layout decisions:**

- Detail remains secondary to the workbench until opened by selecting a task.
- Review actions should become real approve/reject controls only after state mutation exists.
- For now, focus card actions route to task detail instead of pretending to mutate review state.
- Full-page detail is the REVIEW work surface. It must show a human gate summary, approve/reject controls for REVIEW tasks, review materials, evidence package, timeline, risk notes, and linked work in the first desktop viewport.
- Task source is shown in task detail only. It should not be promoted in board cards, mobile rows, attention cards, search placeholder text, or create-task copy.
- Task detail is one of the only user input surfaces, alongside Chat. User-facing copy should describe returning to `Chat` to continue exploration or planning, not doing a separate context-repair job.
- Detail headers show task identity and Task List State only. Workflow recipe steps such as `Discuss` remain in `Stage & Quality`, not in header chips.
- Task detail may show Task Conversation Preview and attachment summaries. Full message history and attached files open through task `Chat` or full detail evidence surfaces.

**Responsive behavior:**

- Desktop can use drawer or full page.
- Mobile uses bottom sheet style drawer behavior from current CSS.

**Open questions:**

- How to render large evidence sets: diff, logs, screenshots, risk notes, review findings, and fix history.

### Attention Page

**Purpose:** Dedicated human-action queue for REVIEW approvals, BLOCKED recovery, and definition gaps that need more discussion or a concrete user decision.

**Current layout decisions:**

- The page is not a second Kanban board. It summarizes action counts, then lists REVIEW and BLOCKED tasks as the priority queue.
- REVIEW and BLOCKED share the main queue because both stop progress: REVIEW blocks DONE or optional WRAP, BLOCKED blocks dispatcher or workflow recovery.
- Definition gaps are secondary decision material in the right column, so users can scan incomplete tasks without mixing them into the urgent queue.
- Attention cards show real task labels, not recipe steps. `Prototype`, `Spec`, `Review`, and other recipe steps belong in Task detail / Workflow evidence, not list-level label chips.

**Responsive behavior:**

- Desktop uses a main queue plus right sidebar.
- Tablet/mobile stack summary cards, queue cards, and sidebar panels into one column.

**Component notes:**

- `.attention-summary`: three compact counters for review, blocked, and definition gaps.
- `.attention-task`: repeated task cards for the urgent queue.
- `.attention-sidebar`: secondary diagnostics for gap distribution, blocker summary, and recent handling notes.

## 4. Component Decisions

### Logo

- **Decision:** Use `frontend/src/assets/grimo-logo.png` in the topbar brand mark, with transparent outside corners and a 34px visual box matching `.topbar-menu`.
- **Why:** User supplied the logo, requested background removal, and asked for it to be the same size as the menu button.
- **Refinement:** The logo image may render slightly larger than the 34px brand box, but the topbar row must remain 52px tall on desktop.
- **Do not:** Fall back to the old text-only `G` mark, add a separate `.brand-mark` border/background, or let the logo resize the topbar row.
- **Verification:** `npm run build`, `npm run test:visual`.

### Create Task Button

- **Decision:** Keep one primary `新增 Task` button per viewport.
- **Why:** Repeated primary actions create ambiguity.
- **Do not:** Add another same-scope create button inside the focus tray or board mode controls.
- **Verification:** Browser comment and visual snapshots.

### Main Navigation

- **Decision:** Open nav as overlay until pinned.
- **Why:** Opening the nav should not keep shifting the board layout during ordinary navigation.
- **Do not:** Let `.nav-open` alone change grid columns.
- **Verification:** Playwright test `main navigation overlays until pinned`.

### Focus Tray

- **Decision:** Support expanded and collapsed states.
- **Why:** Users may want to acknowledge attention items without processing them immediately.
- **Do not:** Force attention cards to occupy vertical space permanently.
- **Verification:** Playwright test `attention focus can collapse and expand`.

### Task Detail Full Page

- **Decision:** Full-page REVIEW detail shows the human approval gate as the primary content, not just Acceptance and Evidence lists.
- **Why:** The page is where users decide approve/reject, so it needs a compact review summary, decision actions, execution timeline, risk notes, and linked work visible without hunting through the drawer.
- **Do not:** Make the full-page detail a sparse read-only duplicate of the drawer.
- **Verification:** Playwright test `full page detail baseline` asserts `審查結論`, `Approve`, and `Reject`; snapshot `task-detail-full-page-chromium-darwin.png`.

### Task Source

- **Decision:** Show task `source` only inside task detail.
- **Why:** User feedback on 2026-05-28: knowing where a task came from does not help list-level triage. Source remains provenance metadata, but it should not compete with state, gap, quality score, evidence, or next action.
- **Do not:** Display `source` in board cards, mobile list rows, the `待處理` page, search placeholder text, or create-task explanatory copy.
- **Verification:** `npm run build`, `npm run test:visual`.

### Task Labels

- **Decision:** Board, mobile, focus, and attention card chips show only user-facing task labels.
- **Why:** User feedback on 2026-05-29: `chat task-forming` on `GRM-144` was unclear because `chat` is source and `task-forming` is a skill/workflow capability, not a label.
- **Source:** Label options come from `docs/grimo/ui/prototype/index.html` `taskLabelPicker`: `bug`, `documentation`, `duplicate`, `enhancement`, `good first issue`, `help wanted`, `invalid`, `question`, `wontfix`, `frontend`, `backend`, `ci/cd`, `design`, `research`.
- **Do not:** Use `source`, workflow recipe steps, skill names, or ad hoc labels as card label chips.
- **Verification:** Playwright board baseline asserts `task-forming` is absent from `GRM-144` card; `task fixtures use prototype-defined labels` asserts fixture labels are all in the prototype taxonomy.

### Badge Semantics

- **Decision:** Task id, Task List State, task label, and metric badges must use distinct visual treatments.
- **Why:** User feedback on 2026-05-29: `GRM-144`, `BACKLOG`, and `frontend` looked too similar, making it unclear which chip was identity, state, or label.
- **Do not:** Render task id, state, and labels with the same neutral pill style.
- **Implementation:** `Badge` supports `kind="task-id"`, `kind="state"`, `kind="label"`, and `kind="metric"`; board card labels use `.badge.label`, detail headers use `.badge.task-id` and `.badge.state`.
- **Verification:** Playwright asserts `GRM-144` card labels use `.badge.label`, and task detail headers expose task id/state through `.badge.task-id` and `.badge.state`.

### Chat Return Path

- **Decision:** User-facing actions should say `Chat` or `回到 Chat 繼續探索或規劃`, not engineering terminology about context.
- **Why:** User feedback on 2026-05-28: context-filling language is engineering terminology, not a product action. The real interaction is returning to the chat interface to keep exploring, planning, clarifying, or refining the task.
- **Do not:** Present standalone list-level actions such as review-material or gap-view buttons. Attention cards and focus cards should route to `Chat` for discussion and clarification.
- **Input surfaces:** The places where users can type are Task detail and Chat. Other surfaces should route to one of those instead of implying a third input workflow.
- **Verification:** `npm run build`; visual snapshots need update because task detail, attention page, and workbench focus action copy changed.

### Task Conversation Thread And Preview

- **Decision:** Every Task owns a durable Task Conversation Thread. Opening task `Chat` shows the complete task thread: historical user messages, agent replies, attached files, referenced files, external links, clarifications, and important system event summaries.
- **Why:** User clarified that every task should open to complete conversation history and attachments, while collapsed Chat should show only recent messages and a key summary, similar to issue comments attached to work.
- **Preview rule:** Board cards, mobile rows, attention cards, detail summaries, and collapsed Chat may show Task Conversation Preview: recent messages, key summary, unresolved questions, comment count, and attachment count.
- **Do not:** Start a blank generic chat from a task, hide task attachments outside provider transcripts, render raw transcripts on list surfaces, or treat attachments as labels/source chips.
- **Input surfaces:** Chat is the main conversation surface; Task detail can accept structured task edits or review decisions. Other list-level surfaces should route back to one of these.
- **Verification:** Documentation-only decision for this update; future UI implementation should add Playwright assertions that task `Chat` is linked to the selected task and that cards show preview metadata rather than full transcripts.

### Task Attachments

- **Decision:** Attachments belong to Task Conversation Thread and Task detail first. They can be promoted or linked into Review Materials when they become evidence for approval.
- **Why:** Attachments help preserve context and discussion history, but list-level triage should stay focused on state, labels, quality, and next action.
- **Do not:** Display attachments as task labels, source metadata, or standalone board chips. Use attachment count or compact hints outside detail/chat.
- **Verification:** Documentation-only decision for this update.

### Attention Queue

- **Decision:** `待處理` is a human-action queue with count summary, priority task cards, and diagnostic sidebar.
- **Why:** A plain BLOCKED-only list hides REVIEW approvals and does not explain what action is blocking progress.
- **Do not:** Duplicate the full board, show every task with equal weight, or add separate list-level `審查材料` / `查看缺口` buttons.
- **Verification:** Playwright test `attention page baseline` asserts `優先處理`, absence of `審查材料` / `查看缺口`, and visible `Chat`; snapshot `attention-page-chromium-darwin.png`.

## 5. Visual Gate Log

### 2026-05-28 — Task Workbench Round 1

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`
- **Result:** passed
- **Snapshots changed:** Task workbench desktop, tablet, mobile, drawer, dialog snapshots changed during the first-round redesign.
- **Reason:** Added light-theme `Focus + Board`, mobile grouped list, single-row toolbar, logo, overlay-until-pinned navigation, and collapsible focus tray.

### 2026-05-28 — Task Detail Full Page Completion

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`, `npm run build`
- **Result:** passed
- **Snapshots changed:** `frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-detail-full-page-chromium-darwin.png`
- **Reason:** Full-page REVIEW detail now includes the human gate decision area, approve/reject actions, review summary, execution timeline, risk notes, and linked work.

### 2026-05-28 — Attention Page Completion

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`
- **Result:** passed
- **Snapshots changed:** `frontend/e2e/task-workbench.visual.spec.ts-snapshots/attention-page-chromium-darwin.png`
- **Reason:** `待處理` now shows human-action counts, a REVIEW/BLOCKED priority queue, definition gaps, blocker summary, and handling notes.

### 2026-05-28 — Source Metadata Demotion

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`, `npm run build`
- **Result:** passed
- **Snapshots changed:** task workbench desktop/tablet/mobile, task detail drawer, create task dialog, and attention page snapshots.
- **Reason:** User said task source is not useful at list level. Source now appears only in task detail; board, mobile list, attention page, search placeholder, and create-task copy no longer promote it.
- **Snapshot summary:** `python3 scripts/visual-snapshot-summary.py --repo-root .` reports 8 changed baselines: 1 added attention page snapshot, 7 updated workbench/detail/dialog snapshots, and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-28 — Chat Return Path Copy

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed; visual gate now has 12 Playwright checks including `chat action returns to task-forming chat`.
- **Snapshots changed:** task workbench desktop/tablet/mobile, task detail drawer, task detail full page, and attention page snapshots.
- **Reason:** User clarified that list-level actions should show `Chat` and return to the chat interface for continued exploration or planning. There is no separate user-facing context-filling job; user input belongs in Task detail or Chat.
- **Snapshot summary:** Current summary reports 8 changed baselines in git status and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-28 — Attention Actions Collapse To Chat

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed; `attention page baseline` now asserts no `審查材料` or `查看缺口` buttons and a visible `Chat` action.
- **Snapshots changed:** task workbench desktop/tablet/mobile, task detail drawer, create task dialog, and attention page snapshots.
- **Reason:** User clarified that `審查材料` and `查看缺口` should not appear as list-level action buttons. Focus and attention cards now route to `Chat` for discussion and clarification.
- **Snapshot summary:** Current summary reports 8 changed baselines in git status and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-29 — Attention Cards Use Labels, Not Recipe Steps

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed; `attention page baseline` now asserts `Prototype` is absent from attention task cards.
- **Snapshots changed:** `frontend/e2e/task-workbench.visual.spec.ts-snapshots/attention-page-chromium-darwin.png`
- **Reason:** User clarified that labels are already defined and `Prototype` appearing as a chip looked like a task label. Attention cards now render `task.labels`; workflow recipe steps remain in Task detail / Workflow evidence.
- **Snapshot summary:** Current summary reports 1 changed baseline and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-29 — Detail Header Separates State From Step

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed; detail visual tests now assert recipe steps are absent from detail header chip rows.
- **Snapshots changed:** `frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-detail-drawer-chromium-darwin.png`, `frontend/e2e/task-workbench.visual.spec.ts-snapshots/task-detail-full-page-chromium-darwin.png`
- **Reason:** User selected `BACKLOG Discuss` and noted the design language was mixed. Detail headers now show task id and Task List State; workflow recipe steps remain in `Stage & Quality`.
- **Snapshot summary:** Current summary reports 3 changed baselines and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-29 — Card Chips Use User Labels Only

- **Commands:** `npm run build`, `npm run test:visual`, `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed; board visual tests now assert `task-forming` is absent from the `GRM-144` card.
- **Snapshots changed:** task workbench desktop/tablet/mobile, task detail drawer, create task dialog, task detail full page, and attention page snapshots.
- **Reason:** User asked what `chat task-forming` meant on the `GRM-144` card. `chat` is task source and `task-forming` is a skill/workflow capability, so neither belongs in card label chips. Card fixtures now use only labels from the prototype-defined label taxonomy; source stays in task detail.
- **Snapshot summary:** Current summary reports 8 changed baselines and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-29 — Mock Labels Follow Prototype Taxonomy

- **Commands:** `npm run build`, `npm run test:visual`, `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed; visual gate now has 13 Playwright checks including `task fixtures use prototype-defined labels`.
- **Snapshots changed:** task workbench desktop/tablet/mobile, task detail drawer, create task dialog, task detail full page, and attention page snapshots.
- **Reason:** User clarified that labels were already defined and mock data should use those definitions instead of invented examples. React task fixtures now use `frontend/src/domain/task/task-labels.ts`, sourced from the prototype label picker, and the create-task label input offers the same options.
- **Snapshot summary:** Current summary reports 8 changed baselines and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-29 — Badge Semantics Are Visually Distinct

- **Commands:** `npm run build`, `npm run test:visual`, `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed; visual gate remains 13/13 with semantic badge assertions.
- **Snapshots changed:** task workbench desktop/tablet/mobile, task detail drawer, create task dialog, task detail full page, and attention page snapshots.
- **Reason:** User noted that labels, task ids such as `GRM-144`, and states such as `BACKLOG` looked too similar. Task id badges are now rectangular mono tokens, Task List State badges use semantic state styling, task labels use softer category chips with a small marker, and metrics use a neutral compact style.
- **Snapshot summary:** Current summary reports 8 changed baselines and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-29 — Logo Slightly Larger Without Topbar Growth

- **Commands:** `npm run build`, `npm run test:visual`, `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed; visual gate remains 13/13 and desktop board tests assert `.topbar` height stays 52px while `.brand-mark img` renders at 38px.
- **Snapshots changed:** task workbench desktop/tablet/mobile, task detail drawer, create task dialog, task detail full page, and attention page snapshots.
- **Reason:** Browser comment requested the topbar logo be slightly larger without increasing the row height. The brand box remains 34px; the image renders at 38px with visible overflow, so the row stays fixed.
- **Snapshot summary:** Current summary reports 8 changed baselines and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

### 2026-05-28 — Topbar Logo Background And Size

- **Commands:** `npm run build`, `npm run test:visual` (sandbox blocked dev server with `EPERM`), `npm run test:visual:update`, `npm run test:visual`, `python3 scripts/visual-snapshot-summary.py --repo-root .`
- **Result:** passed after updating intentional topbar logo snapshots; final visual gate is 12/12 passed.
- **Snapshots changed:** task workbench desktop/tablet/mobile, task detail drawer, create task dialog, task detail full page, and attention page snapshots.
- **Reason:** Browser comment requested background removal and menu-button-sized logo. Topbar logo now uses a transparent PNG and a 34px visual box matching `.topbar-menu`.
- **Snapshot summary:** Current summary reports 8 changed baselines in git status and local evidence artifacts in `frontend/test-results/.last-run.json` plus `frontend/playwright-report/index.html`.

## 6. Browser Comment Intake

### 2026-05-28 — Search Field Toolbar

- **Comment:** Search input should be on the same row as `新增 Task`.
- **Decision:** accepted
- **Result:** Desktop `.toolbar` no longer wraps; mobile keeps stacked controls.
- **Verification:** `npm run build`, `npm run test:visual`.

### 2026-05-28 — Mobile Task Layout

- **Comment:** Mobile should look like a list because board layout is hard to present.
- **Decision:** accepted
- **Result:** Mobile/tablet use `.mobile-task-list`; desktop keeps `.board-grid`.
- **Verification:** `npm run build`, `npm run test:visual`.

### 2026-05-28 — Main Navigation Pinning

- **Comment:** Main nav should float over the board unless pinned.
- **Decision:** accepted
- **Result:** Unpinned nav is overlay; pinned nav changes layout.
- **Verification:** Playwright test `main navigation overlays until pinned`.

### 2026-05-28 — Focus Tray Collapse

- **Comment:** `待處理焦點` needs collapse/expand because sometimes the user does not want to process it.
- **Decision:** accepted
- **Result:** Focus tray has `收合` and `展開` states.
- **Verification:** Playwright test `attention focus can collapse and expand`.

### 2026-05-28 — Focus Header Density

- **Comment:** `需要你處理` should sit after `待處理焦點`, not occupy two lines.
- **Decision:** accepted
- **Result:** `需要你處理` is a small inline status tag.
- **Verification:** `npm run build`, `npm run test:visual`.

### 2026-05-28 — Topbar Logo Background And Size

- **Comment:** Logo should have its background removed and match the menu button size.
- **Decision:** accepted
- **Result:** `frontend/src/assets/grimo-logo.png` now has transparent outside corners; `.brand-mark` and `.brand-mark img` are 34px to match `.topbar-menu`.
- **Verification:** `npm run build`, `npm run test:visual`.
