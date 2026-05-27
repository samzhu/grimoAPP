# Grimo QA Strategy

## Scope

This QA strategy defines the release verification gate for the current Grimo workspace. Frontend UI work is high-risk because small CSS changes can break prototype parity while still passing TypeScript.

## Verification Command Registry

| ID | Command | Severity | Environment | Evidence |
| --- | --- | --- | --- | --- |
| V1 | `scripts/verify-release.sh` | CRITICAL | zsh, Node/npm | `temp/verify-release.log` |
| V2 | `npm run build` in `frontend/` | CRITICAL | Node/npm, installed frontend deps | Vite/TypeScript output |
| V3 | `npm run test:visual` in `frontend/` | CRITICAL | Playwright Chromium installed | screenshot baseline comparison and Playwright report |
| V4 | `scripts/run-webwright-visual-qa.sh ...` | CRITICAL when prototype parity is claimed | `.venv-webwright`, Playwright Chromium, Webwright backend credentials if using model configs | final script, logs, screenshots, self-reflection/manual review |

## Frontend UI Gate

For non-layout frontend changes, `scripts/verify-release.sh` and `npm run build` are sufficient automated gates.

For layout, responsive, or prototype parity changes, automated build evidence is not enough. Verification must include deterministic browser screenshots at the relevant viewport set. Webwright should then inspect the same user-visible workflow as an agent-assisted QA reviewer and preserve reusable artifacts.

Required viewport set:

- Desktop layout: `1366x768`, `1440x900`
- Responsive layout: add `390x844`, `820x1180`
- Prototype parity sweep: include `1920x1080` when the prototype has wide desktop behavior

## Webwright Role

Webwright is the agent-assisted visual QA layer. It should:

- open the local Vite app in a fresh browser context
- inspect the target surface against `docs/grimo/ui/prototype/index.html`
- capture screenshots only at critical points
- write a reusable script and logs under an ignored evidence directory
- rerun the final script from a fresh folder before the check is accepted

Webwright does not replace deterministic assertions. A passing Webwright review without Playwright screenshot evidence is `MANUAL-READY` at best, not `VERIFIED`.

## Installation

Playwright is installed as a frontend dev dependency pinned to `@playwright/test@1.60.0`.

Webwright is installed into a repo-local virtualenv so it does not pollute the user environment:

```bash
scripts/setup-webwright.sh
```

The current pinned Webwright source is `microsoft/Webwright@0be73c18ce31a0920c979b8f2aab12d11ef26b9c`.

## Test Strategy

| Change type | Required checks | Release effect |
| --- | --- | --- |
| TypeScript-only frontend change | `scripts/verify-release.sh` | Blocks on build or visual regression failure |
| Layout/CSS change | `scripts/verify-release.sh` plus review Playwright screenshot diffs | Blocks on screenshot diff unless baseline is intentionally updated |
| Responsive layout change | Add/update viewport coverage for `390x844`, `820x1180` | Blocks when mobile/tablet screenshots regress |
| Prototype parity claim | Run `scripts/run-webwright-visual-qa.sh` against the local app and preserve artifacts in the spec evidence | Blocks if no Webwright/manual visual evidence exists |
| New UI interaction | Add Playwright interaction path before baseline screenshot | Blocks if interaction cannot be executed from the real browser |

## Current Coverage

The deterministic Playwright suite currently covers:

- task board at `1366x768`, `1440x900`, `390x844`, `820x1180`
- selected task detail drawer
- create task dialog
- full page task detail

Webwright is installed and callable through `scripts/run-webwright-visual-qa.sh`, but task-specific Webwright runs remain opt-in because model/backend credentials are environment dependent.
