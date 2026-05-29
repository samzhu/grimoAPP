# Grimo Frontend UI/UX Workflow

## Purpose

Grimo UI work must preserve product intent, designer layout decisions, and verifiable implementation evidence. The working rule is:

```text
PRD language -> design source -> design language tokens -> component states -> browser evidence
```

This prevents the frontend from drifting into "looks close enough" implementation.

## Source Of Truth Layers

| Layer | Source | Owns | Change rule |
| --- | --- | --- | --- |
| Product behavior | `docs/grimo/PRD.md` | workflows, task states, user gates | Product/spec change required |
| Design source | `docs/grimo/ui/prototype/index.html` | layout geometry, copy, visual states | Compare exported CSS before implementation |
| Design language | `docs/grimo/design/tokens.json` | named visual decisions | Token change requires reason and visual baseline update |
| Implementation | `frontend/src/features/*`, `frontend/src/styles.css` | React surfaces and CSS mapping | Must use product names and token mapping |
| Review prompts | `docs/grimo/design/webwright-prompts.md` | repeatable UI review instructions | Prompt changes must cite the product/design rule they check |
| Evidence | `frontend/e2e/*`, Webwright artifacts under `temp/webwright/` | regression proof and review trail | Required before layout/prototype parity work ships |

## Task Entry Checklist

Before coding frontend UI:

1. Identify the user-facing surface: `task-board`, `task-detail`, `task-create`, `task-forming-chat`, `projects`, `blockers`, or `workflow`.
2. Find the matching prototype selector and CSS token in `docs/grimo/ui/prototype/index.html`.
3. Decide whether the task changes behavior, layout, responsive behavior, or design language.
4. Translate human UI feedback into one of: product behavior, design language, component state, or review prompt.
5. If it changes layout, update or add Playwright visual coverage before marking done.
6. If it claims prototype parity, run Webwright as a second reviewer and keep artifacts with the spec evidence.

## Default Interaction Principles

- Board entry has no selected task. Selection is a user action, not a default state.
- A selected task must show a subtle border/inset accent only while the selected task is the active work context.
- Drawer state and selected-card state must not contradict each other. If the drawer is closed by the user, clear selection unless another surface still owns the task context.
- Empty/default states are first-class states. They should describe what the user can do next without pretending a record is selected.
- `新增 Task` opens task creation; it must not be a dead button.
- `在完整頁開啟` opens a full task page; it must not be a dead button.

## Design Language Storage

Grimo stores design language in repo, not only in screenshots.

- `docs/grimo/design/tokens.json`: DTCG-style token inventory for decisions shared between design and frontend.
- `frontend/src/styles.css`: current CSS implementation, using prototype-compatible custom properties.
- `docs/grimo/ui/prototype/index.html`: exported source of truth while there is no live Figma sync.
- `docs/grimo/design/webwright-prompts.md`: repeatable prompts for agent-assisted UI review.
- Playwright screenshots: implementation baseline, not design source.
- Webwright artifacts: review evidence and reproducible browser script, not token source.

## Feedback Capture Rule

User feedback about frontend screens must not remain only in chat history.

| Feedback type | Store it in | Example |
| --- | --- | --- |
| Product behavior | `docs/grimo/PRD.md`, spec, or this workflow doc | Board entry has no selected task. |
| Persistent UI rule | `docs/grimo/development-standards.md` | Selected task uses subtle inset accent. |
| Reusable visual check | `docs/grimo/design/webwright-prompts.md` | Check initial board has zero selected task cards. |
| Numeric design decision | `docs/grimo/design/tokens.json` | Rail width, topbar height, card padding. |
| Regression proof | `frontend/e2e/*` | Playwright assertion or screenshot baseline. |

When a user says a screen "looks wrong", first name the rule, then decide where it belongs. If the rule affects repeatable review, add it to the Webwright prompt library. If the rule affects shipping correctness, also add deterministic Playwright coverage.

Token tiers:

| Tier | Example | Meaning |
| --- | --- | --- |
| Primitive | `color.green.55`, `space.2`, `radius.6` | Raw values; rarely used directly in feature CSS |
| Semantic | `color.bg`, `color.surface`, `color.accent`, `color.border` | Product-wide roles |
| Component | `taskCard.selectedInset`, `topbar.height`, `rail.width` | Layout/state decisions from prototype |
| Surface | `taskBoard.columnMinWidth`, `detailDrawer.width` | View-level geometry that must match screenshots |

## Design Change Protocol

Use this order for UI work:

1. **Capture**: point to the prototype selector, screenshot, or product requirement.
2. **Name**: add or reuse a token name before scattering values in CSS.
3. **Implement**: update feature/component code with the minimum surface change.
4. **Verify**: run `scripts/verify-release.sh`.
5. **Review**: inspect Playwright diff. If prototype parity is claimed, run Webwright and keep artifacts.
6. **Record**: mention token changes, screenshots, and remaining visual risk in the spec/task result.

## Research Notes

- The W3C Design Tokens Community Group format exists to exchange design decisions between tools and code, reducing custom glue work between design tools, translation tools, and docs.
- USWDS treats tokens as constrained palettes for typography, spacing, color, and similar style decisions, avoiding arbitrary one-off values.
- Figma variables can import/export DTCG JSON and support modes, so a future Figma workflow can sync with repo tokens instead of relying on manual screenshots.
- Storybook is useful later for component documentation, but Grimo should first stabilize product surfaces and visual regression gates because the current risk is full-page layout drift.

References:

- W3C Design Tokens Format Module 2025.10: https://www.w3.org/community/reports/design-tokens/CG-FINAL-format-20251028/
- U.S. Web Design System design tokens: https://designsystem.digital.gov/design-tokens/
- Figma variable modes and DTCG import/export: https://help.figma.com/hc/en-us/articles/15343816063383-Modes-for-variables
- Storybook rationale: https://storybook.js.org/docs/get-started/why-storybook
