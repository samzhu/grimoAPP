# Frontend Design Context Schema

Use this schema for `docs/grimo/design/frontend-design-context.md`.

## Required Sections

### 1. Purpose

State that this file preserves frontend UI/UX decisions, page-level rationale, visual gate evidence, and browser-comment outcomes for Grimo.

### 2. Global Design Principles

Capture rules that apply across pages.

Entry format:

```markdown
- **Decision:** <rule>
  **Why:** <rationale>
  **Evidence:** <browser comment, command, screenshot, or source>
  **Status:** active | superseded
```

### 3. Page Context

Create one subsection per product page, such as:

- Task Workbench
- Task Detail
- Task-forming Chat
- Projects
- Workflow
- Blockers

Entry format:

```markdown
#### <Page Name>

**Purpose:** <what this page is for>

**Current layout decisions:**
- <decision and rationale>

**Responsive behavior:**
- <desktop / tablet / mobile behavior>

**Component notes:**
- `<ComponentName or selector>`: <state, rationale, constraints>

**Open questions:**
- <question or none>
```

### 4. Component Decisions

Use this for reusable components shared across pages.

Entry format:

```markdown
### <Component>

- **Decision:** <what changed or must remain true>
- **Why:** <rationale>
- **Do not:** <rejected patterns>
- **Verification:** <command or visual snapshot>
```

### 5. Visual Gate Log

Append concise entries when visual snapshots or behavior tests change.

Entry format:

```markdown
### YYYY-MM-DD — <short title>

- **Commands:** `npm run build`, `npm run test:visual:update`, `npm run test:visual`
- **Result:** <pass/fail summary>
- **Snapshots changed:** <list or script summary>
- **Reason:** <intentional UI change>
```

### 6. Browser Comment Intake

Use this section for browser comments that affect design decisions.

Entry format:

```markdown
### YYYY-MM-DD — <target label>

- **Comment:** <user comment>
- **Decision:** <accepted / rejected / deferred>
- **Result:** <file or page impact>
- **Verification:** <command or screenshot>
```

## Capture Rules

- Preserve user language when it clarifies intent.
- Prefer concrete references: file path, selector, viewport, command, screenshot artifact.
- Separate accepted decisions from rejected design references.
- Avoid dumping long transcripts; summarize the stable decision and cite the evidence.
- When a later decision supersedes an old one, mark the old one as superseded instead of deleting it.
