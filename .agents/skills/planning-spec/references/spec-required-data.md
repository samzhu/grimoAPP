# Spec Required Data

Use this checklist before a spec is considered ready for `/planning-tasks`.

## Required Sections

Every spec file must contain sections 1-5 during `/planning-spec`:

1. `## 1. 目標`
2. `## 2. 研究與設計`
3. `## 3. BDD Contract`
4. `## 4. 介面與 API 設計`
5. `## 5. 檔案規劃`

Sections 6-7 are reserved for `/planning-tasks` and implementation results.

## Required Data Checklist

### 1. Goal

- Plain-language one-line outcome.
- Spec id, size, status, date, source references.
- Dependencies classified as code-level, ordering-only, downstream, or blocked.
- Scope boundaries: what this spec does and explicitly does not do.
- Overlap scan result against active specs.

### 2. Research And Design

- Existing repo facts with paths, current behavior, and design impact.
- External framework/API/standard citations when behavior may drift.
- Approach comparison with selected option and rejected alternatives.
- Confidence classification for load-bearing decisions: Validated, Hypothesis, or Unknown.
- User journey or data-flow diagram when the behavior crosses multiple states, services, APIs, or screens.
- Screen Flow Contract when the spec changes frontend page flow, navigation, onboarding, empty state, error state, success destination, CTA hierarchy, or cross-page transition. Must follow `docs/grimo/design/screen-flow-contract.md` and include Flow Header, State Matrix, Flow Steps, low-fidelity wireflow, CTA/navigation rules, and Verification Mapping.
- Domain language and glossary impact; update glossary when introducing new product concepts.
- Task-boundary hints: future task name, target file/class, positive scenario, negative scenario, and POC need.

### 3. BDD Contract

- Verification command from `docs/grimo/qa-strategy.md`.
- AC matrix with AC id, user result, observable contract/output, layer, and state.
- Scenario metadata: `@spec`, `@ac`, `@layer`, `@api` when applicable, and `@state`.
- Given/When/Then scenario written in domain language, not test-framework mechanics.
- Verification Bindings for each scenario: test file(s), command, and manual/deployment condition if any.
- Generality expectation for every AC that could be hardcoded: dynamic input, boundary/negative case, persisted read-back, differential check, property, or metamorphic relation.
- NFR sweep covering Performance, Security, Reliability, Usability, and Maintainability. Use explicit `N/A — reason` only when legitimate.

### 4. Data Contract

Any API, DTO, DB row, event payload, command output, UI form data, file format, log line, or external contract change must include:

- Realistic request/input example.
- Realistic response/output example.
- For collection responses, at least two `content[]` items and an empty-array or negative-case shape when relevant.
- Per-field table: field, type/format, rule, source, design rationale, and what BDD asserts.
- System-owned fields that clients must not set, such as `id`, `state`, `source`, `workflowRecipeId`, `createdAt`, workflow transition fields, or projections.
- Error/negative cases with exact status, body, UI text, command output, or DB state.

For UI flow contracts, include exact visible text or role for loading, empty,
ready, error, and success states. If one of these states is intentionally not
possible, write `N/A - <reason>` rather than silently skipping it.

### 5. Storage Contract

Any new or changed DB table must include:

- SQL definition.
- A comment block immediately before each table that states:
  - table name
  - product purpose
  - owner / parent aggregate
  - ownership boundary
  - data that must not be stored in this table
- Field rationale table for every load-bearing column.
- Index/unique/FK rationale when they enforce behavior or performance.
- Realistic sample rows for every table, including parent rows needed to understand the FK chain.
- At least one sample row set that exercises a negative, empty, cross-project, or non-happy-path condition when relevant.
- BDD read-back expectation that names which query/API/test proves the sample rows are used, not hardcoded.

Recommended table comment shape:

```sql
-- table: <table_name>
-- 用途: <user/product result this table preserves>
-- owner: <parent table/aggregate and how ownership is verified>
-- 不存: <nearby data that belongs elsewhere>
CREATE TABLE IF NOT EXISTS <table_name> (
    ...
);
```

### 6. Interface And API Design

- Backend controller/service/store signatures when backend behavior changes.
- Frontend types/client signatures when frontend consumes or sends the contract.
- Validation/default/normalization rules.
- Projection rules: source tables/events, selection order, empty-state behavior, and forbidden root fields.
- Public vs internal API boundary, especially when MVP security is permit-all.
- POC-required interfaces marked clearly when research could not validate behavior.

### 7. File Plan

- Every production file to add/modify.
- Every test file to add/modify.
- Documentation, roadmap, changelog, QA, or release-gate file changes.
- Screen flow documentation changes when frontend page flow changes: active spec UI subsection plus any durable update to `docs/grimo/design/screen-flow-contract.md`, `docs/grimo/design/ui-ux-workflow.md`, or `docs/grimo/design/frontend-design-context.md`.
- Action per file: new, modify, verify, or delete.
- Why each file is in scope; avoid placeholder files for downstream specs.

## Confirmation Gate

Before handoff, present the user with the load-bearing ACs in Outcome -> Contract -> Evidence order:

1. Outcome: visible user/API/DB/UI/command result.
2. Contract: concrete request/response/schema/file/row shape.
3. Evidence: exact test file and command.

Ask one confirmation question at a time when answers can affect later questions. Do not replace this with a passive "review the spec" request.

If any load-bearing behavior, data field, DB table, verification command, deployment/manual check, or pass/fail threshold is unconfirmed, the spec is not ready for `/planning-tasks`.
