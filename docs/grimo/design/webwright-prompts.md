# Grimo Webwright Prompt Library

## Purpose

Use these prompts when a frontend UI change needs agent-assisted visual review. Webwright prompts are not the source of truth; they are reusable review instructions derived from PRD, prototype, design tokens, and development standards.

## Usage

Start the frontend first:

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run dev
```

Then run Webwright from repo root:

```bash
./scripts/run-webwright-visual-qa.sh \
  -t "<prompt>" \
  --start-url http://127.0.0.1:5173 \
  --task-id <task-id>
```

## Task Board Prototype Parity

Use after changing task board layout, task card state, toolbar, Side Navigation, or Task Details Pane / Drawer behavior.

```text
Compare the local Grimo task workbench against docs/grimo/ui/prototype/index.html.

Viewport requirements:
- Check 1440x900 and 1366x768 desktop layouts.
- If responsive behavior changed, also check 820x1180 and 390x844.

Product behavior rules:
- Initial board entry must have no selected task card.
- A task card becomes selected only after the user clicks it or an explicit deep link opens it.
- Closing the Task Details Drawer must clear the selected-card presentation unless another visible task context owns it.
- 新增 Task must open the task creation dialog.
- 在完整頁開啟 must open the full task detail page.

Layout checks:
- Compare App Header height, brand alignment, project context alignment, and horizontal rhythm.
- Compare Side Navigation width, collapse control alignment, and active item spacing.
- Compare section head alignment, search field position, and 新增 Task button size/position.
- Compare board column widths, gaps, header alignment, card padding, card metadata alignment, and selected-card inset accent.
- Flag any overlap, inconsistent spacing, off-grid alignment, text clipping, or visual state that contradicts the prototype.

Output:
- List concrete findings with viewport, selector or visible text, screenshot reference, expected behavior, actual behavior, and severity.
- Do not suggest new product features.
- If no issue is found, say which rules were checked and what evidence supports that.
```

## Focused Initial State Check

Use when changing reducer initial state, selected task behavior, drawer behavior, routing, or deep-link behavior.

```text
Review only the Grimo task board initial and selected-task states.

Check these rules:
- On first load at http://127.0.0.1:5173, no task card should have selected styling.
- The Task Details Drawer should be closed on first load.
- After clicking one task card, exactly one card should show selected styling and the Task Details Drawer should describe that same task.
- After closing the Task Details Drawer, no task card should remain visually selected.

Output concrete findings with screenshots and selectors. Do not review unrelated layout issues unless they block these checks.
```

## Prompt Promotion Rule

When a Webwright prompt catches a real issue, promote the durable part:

1. Product behavior -> PRD, spec, or `docs/grimo/design/ui-ux-workflow.md`.
2. Visual/design rule -> `docs/grimo/development-standards.md` or `docs/grimo/design/tokens.json`.
3. Deterministic regression -> Playwright test or screenshot baseline.
4. Review wording -> this prompt library.
