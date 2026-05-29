---
name: frontend-design-context
description: >
  Captures frontend UI and UX decisions, browser comments, visual regression evidence, and page-level design rationale into a maintained design context. Use when frontend requirements, UI adjustments, UX copy, responsive layout, visual testing, Webwright-style artifact review, Playwright snapshots, or design language discussions happen. Don't use for backend-only work, infrastructure tasks, API-only changes, or generic documentation unrelated to frontend design.
metadata:
  author: grimo
  version: 0.1.0
  category: workflow-automation
  pattern: context-aware
allowed-tools:
  - Read
  - Edit
  - Bash
---

# Frontend Design Context

Maintains a durable frontend design context for Grimo. It captures UI/UX requirements, browser comments, design-language decisions, page-level rationale, visual regression evidence, and snapshot update summaries so future frontend work can continue without rediscovering prior decisions.

## Procedures

### Step 1: Classify the frontend event
1. Treat the request as in scope when it mentions frontend UI, UX, copy, responsive behavior, visual testing, browser comments, screenshots, design language, layout, components, pages, or Playwright snapshots.
2. Skip this skill when the request is backend-only, API-only, infrastructure-only, or unrelated to product interface design.
3. If another more-specific implementation skill is also active, use this skill as the context capture and verification companion, not as the sole implementation workflow.

### Step 2: Read current design context
1. Read `docs/grimo/design/frontend-design-context.md` if it exists.
2. If it does not exist, create it from the structure in `references/context-schema.md`.
3. Read `docs/grimo/design/webwright-visual-testing-notes.md` when the request involves `npm run test:visual`, `npm run test:visual:update`, screenshots, snapshot diffs, or Webwright-style verification.
4. Read `docs/grimo/design/ui-ux-redesign-brief.md` when the request concerns broader redesign direction, information architecture, visual language, or external designer handoff.

### Step 3: Capture decisions while working
1. Record concrete UI/UX decisions as soon as they stabilize: page, component, decision, rationale, rejected alternative, user evidence, and verification.
2. Use `references/context-schema.md` to choose the correct section and entry format.
3. Keep entries factual and traceable. Prefer browser comment text, file paths, viewport names, commands, and screenshot artifact paths over broad summaries.
4. Do not record transient experiments that were reverted unless the failed experiment explains a decision that future agents might otherwise repeat.

### Step 4: Run visual gate when UI changed
1. Run `npm run build` from `frontend/` after source changes.
2. Run `npm run test:visual` from `frontend/`.
3. If visual tests fail, inspect actual screenshots and diffs before updating snapshots.
4. Run `npm run test:visual:update` only for intentional visual changes.
5. Run `npm run test:visual` again after snapshot update; final status must be reported.

### Step 5: Summarize changed visual artifacts
1. Run `python3 scripts/visual-snapshot-summary.py --repo-root .` from the repository root after visual snapshots change.
2. Use the script output to list changed snapshots, test result artifacts, and whether snapshots were updated.
3. Add a short reason for each snapshot group based on the UI decision, not just "updated snapshots."

### Step 6: Finalize context
1. Update `docs/grimo/design/frontend-design-context.md` with page-level and component-level context.
2. Update `docs/grimo/design/webwright-visual-testing-notes.md` only when the testing workflow itself changed.
3. In the final response, include changed files, visual gate commands and results, and the design context section updated.

## Examples

**Positive — should trigger and succeed**:
> User: "手機版應該類似 list 版面，看板版面難以呈現吧?"
> Skill: captures the mobile decision under the Task Workbench page, records why Kanban was rejected for mobile, implements or coordinates the change, runs visual gate, summarizes changed mobile snapshots, and updates the design context.

**Positive — should trigger and succeed**:
> User: "這個 search input 跟新增 Task 同一排就好"
> Skill: records the toolbar layout decision, updates the frontend, verifies desktop and mobile behavior, and adds the rationale to the page context.

**Negative — should NOT trigger**:
> User: "新增一個 backend endpoint 回傳 task list JSON"
> Skill: defer because the request is API-only unless the user also asks about UI behavior or frontend design implications.

## Error Handling

* If `docs/grimo/design/frontend-design-context.md` is missing: context has not been initialized. Create it from `references/context-schema.md`, then add the current decision.
* If `npm run test:visual` fails because the dev server cannot bind to localhost: sandbox restrictions blocked local server startup. Re-run with the approved escalation path and report the reason.
* If visual tests fail with screenshot diffs: the UI changed or regressed. Inspect actual screenshots before running `npm run test:visual:update`; update snapshots only for intentional design changes.
* If `scripts/visual-snapshot-summary.py` exits non-zero: required paths are missing or the command was run outside the repository root. Re-run with `--repo-root` pointing at the Grimo repo root.
* If a design decision conflicts with existing context: record the new decision as superseding the old one, cite the user evidence, and do not silently delete the earlier rationale.
