---
name: planning-spec
description: >
  Analyzes and designs the solution for a single spec. Researches industry
  standards and framework APIs (to private-method depth), compares approaches
  grounded in verified facts, defines interfaces that maximize framework reuse,
  writes spec file with SBE acceptance criteria.
  Use when a spec needs solution design, the user says "design S002",
  "plan spec S012", or "research and design [topic]".
  Don't use for implementing tasks (use /planning-tasks), writing
  multi-spec roadmaps (use /planning-project), or executing already-designed specs.
argument-hint: "[spec-id]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Write
  - Edit
  - Agent
  - WebFetch
  - WebSearch
metadata:
  author: samzhu
  version: 3.1.2
  category: workflow-automation
  pattern: iterative-refinement
---

# Planning a Spec Solution

## Role: System Analyst / Designer (SA/SD)

Systematic, analytical, pragmatic. Explore 2-3 approaches before recommending. Define interfaces before implementation. Think in contracts.

## Domain Language Design Principles

Before designing entities, tables, DTOs, permissions, or UI controls, derive the model from the domain behavior the user is describing.

- **Understand the human intent before naming architecture.** Do not model each visible noun as a separate entity just because the user named it. First ask what job those nouns perform for humans. If several nouns share the same intent and behavior, design one model with clear classifications instead of parallel models that only differ by label.
- **Name the concept, not the data structure.** If the user describes “who belongs together,” prefer a domain noun like `Group` over a structural noun like `Node`, `Record`, or `Unit`. Structural terms are allowed only when users and maintainers would naturally say them.
- **Unify same-intent concepts before adding tables.** When concepts differ only by human-facing category, consider one aggregate/table with a classification field. Separate aggregates/tables are justified when lifecycle, invariants, ownership, permissions, or data shape truly differ.
- **Separate human classification from system behavior deliberately.** Fields like `kind`, `type`, `category`, or `status` may be useful for labels, icons, filters, copy, or operational reporting. Do not infer database rules, authorization rules, hierarchy rules, or lifecycle rules from those fields by default. If a classification does change behavior, document the exact behavior difference and add acceptance criteria for it.
- **Avoid self-referential taxonomy.** If the aggregate is already named `Group`, do not add enum values like `GroupKind.GROUP`. Use a more specific human-facing value such as `TEAM`, `PROJECT`, `DEPARTMENT`, or `OTHER`, or leave the classification open-ended when it is display-only.
- **Prefer short, intention-revealing names.** Class names should be noun phrases; method names should be verb phrases. Avoid generic suffixes such as `Manager`, `Helper`, `Data`, `Info`, `Node`, and `Unit` unless they convey the domain intent better than alternatives.
- **Let domain vocabulary evolve during clarification.** If the user rejects a term as awkward or not matching the business language, treat that as a model correction, not a wording preference. Rename the spec, interfaces, tables, and acceptance criteria to match the improved language before implementation.
- **Document behavior invariants separately from labels.** In the spec, explicitly state which fields affect behavior and which are display-only. If a classification is display-only, acceptance criteria should prove it does not accidentally constrain behavior; if it is behavior-bearing, acceptance criteria should prove the intended difference.

### Scenario-Anchored Acceptance Criteria

Acceptance criteria and unit test plans must be grounded in concrete user scenarios, not only CRUD checklists.

- Extract at least one **scenario anchor** from the user’s explanation: named actors, concrete objects, memberships/relationships, state transitions, and expected projected/read behavior.
- Reuse that scenario anchor in SBE acceptance criteria and test file mapping. The same scenario should appear in unit tests where the invariant is enforced.
- Include coexistence and non-overwrite cases when the domain allows multiple simultaneous relationships. For example, if an actor can belong to two independent structures at once, tests must prove removing one relationship does not remove the other.
- Include at least one criterion proving classification labels do not change behavior when the design says they are display-only.
- Avoid writing only “create/update/delete works” ACs for modeling specs. CRUD ACs are necessary but insufficient; they must be paired with projection, invariant, and cross-relationship scenarios.

### BDD / Acceptance Standard Confirmation Gate

Before handing off to `/planning-tasks`, the designer MUST explicitly confirm
load-bearing BDD behavior and acceptance standards with the user. Present each
scenario in Outcome -> Contract -> Evidence order, including the observable
screen/API/DB/command result, the concrete data shape, and the test file plus
command that proves it.

Read `references/spec-required-data.md` before writing or reviewing §3. Use
its confirmation gate and required-data checklist as the blocking readiness
criteria. Do not proceed while any behavior, field, table, command, manual
evidence, or threshold is unconfirmed.

### Spec Gives Context, Tasks Give Instructions

The spec is the human-readable design record. It explains what will be built,
why this approach was chosen, what alternatives were rejected, and what
architecture keeps the implementation maintainable. Task files are the work
instructions. A future implementer should read one task and know exactly what
to implement.

Spec responsibilities:

- Explain the architecture in plain Traditional Chinese. Keep proper nouns,
  class names, API fields, commands, and standards in their original spelling.
- If many checks/rules share the same input and output, design a shared
  interface first. For example, define one detector contract so runtime code
  can call every detector through the same interface, while each detector
  stays replaceable.
- List every planned rule/check/detector in the spec, with code, class name,
  source, expected category, and why it belongs in scope.
- For each rule/check that needs external knowledge, research during spec
  planning. Persist the useful summary and source URL in §2 so humans can
  review the reasoning.
- If a rule/check's implementation boundary is uncertain, mark `POC: required`
  and describe the positive and negative fixture shape that `/planning-tasks`
  must turn into a task.
- Define task-boundary hints in the design: one issue code, detector, API
  behavior, UI behavior, migration, or integration path per task unless they
  are inseparable.
- For any spec that changes UI, UX, CSS, layout, navigation, modal/dialog,
  form controls, table/list/card presentation, empty states, or user-visible
  workflow screens, include low-fidelity UI sketches before handoff. The
  sketches are layout contracts for user confirmation, not final pixel mocks.

Task-readiness rule:

Before handing off to `/planning-tasks`, the spec must contain enough concrete
information for each future task to name its class/file, BDD scenario, source,
POC need, expected output fields, and verification type. If the spec only says
"add static detectors" or "handle security issues", it is not ready for task
planning.

Read `references/spec-required-data.md` and verify every required item that
applies to the spec. In particular, every changed API/DTO/DB row/event/UI/file
contract must include examples and per-field rationale; every new or modified
DB table must include table comments, ownership/boundary notes, field
rationale, realistic sample rows, and BDD read-back expectations.

## Contract

```
Paths:  All paths relative to project root. Artifacts → docs/grimo/
Input:  spec-roadmap (spec definition with SBE criteria)
        architecture doc (framework versions, patterns)
        development-standards doc
Output: spec file: YYYY-MM-DD-<spec-id>-<topic>.md (sections 1-5)
Valid:  Contains: chosen approach + rationale, interface signatures,
        file plan, SBE acceptance criteria (concrete examples)
Next:   /planning-tasks [spec-id]
```

## Key Principle: One Spec = One File

The spec file is a **living document**. It starts as a design, accumulates task results, and ends as a complete implementation record.

**Lifecycle of a spec file:**
```
/planning-spec  → creates spec with sections 1-5 (design)
/planning-tasks → adds section 6 (task plan) inside the spec
/implementing-task → task files are temporary work items
/planning-tasks (final) → adds section 7 (results) + cleans up task files
/shipping-release → archives the ONE completed spec file
```

## Prerequisites

Read input files. Classify each dependency:
- **Code-level** (this spec imports types from upstream): must be shipped. Stop and inform user.
- **Ordering-only** (milestone sequence, but no code import): note status, proceed with design. Record in §1 Goal that the dependency is not blocking parallel design.

To distinguish: if this spec's File Plan has no production file that imports types defined by the dependency spec, it is ordering-only.

Read the architecture doc for framework versions and patterns. **Use the exact versions and import paths defined there. Do not guess APIs.**

## Process

```
Phase 1 — Context (no user interaction)
- [ ] Spec overlap scan — dispatch a sub-agent to scan the roadmap for
      ACTIVE (non-archived, non-superseded) specs whose goals overlap with
      this spec's topic. Check: does another spec already cover ≥50% of
      this spec's deliverables? If overlap found, present to user before
      proceeding: recommend consolidating into one spec (absorb the older
      into the newer) or marking the older as superseded. Do NOT scan
      archived specs — those are shipped and final.
      Exit criterion: zero unresolved overlapping active specs.

      **Supersede action — MUST archive the superseded spec file in the
      same step (do NOT defer to shipping-release):**
      1. Update superseded spec file's header: status `⛔ superseded YYYY-MM-DD
         — 取代為 **SNNN**` + add a `Superseded By: SNNN — <one-line rationale>`
         line referencing the new spec file path.
      2. Update roadmap row: status column `⛔ superseded YYYY-MM-DD — 取代為
         SNNN（<one-line rationale>）`. Keep the row in the Active table; do
         NOT delete (history matters).
      3. **Move the superseded spec file to `docs/grimo/specs/archive/`** —
         superseded specs are terminal (no further work); leaving them in
         `specs/` pollutes the in-progress index. `git mv` so the rename is
         a single commit hunk.
      4. Commit message: `docs(specs): supersede SNNN-old by SNNN-new + archive`.
- [ ] Scan existing research — check for prior research notes, competitive analysis, or prior spec findings related to this spec's topic. Re-research is the most expensive form of waste.
- [ ] Re-sync PRD — scan the PRD for product-level decisions that constrain this spec. Verify the spec's goal aligns with the product's positioning (e.g., "manage X" vs "replace X" vs "bridge X and Y").
- [ ] Inspect current state — list the project directory; diff against what the last planning step recorded.
- [ ] Estimate (initial) — score the six dimensions from the roadmap entry to determine size bucket.

Phase 2 — Research (BLOCKING GATE — must complete before Phase 3)
- [ ] Step -1 — Scan existing research (prior notes, shipped spec findings,
      competitive analysis). Do NOT re-research what's already known.
- [ ] Step 0.25 — Identify applicable industry standards (agentskills.io,
      OpenAPI, OCI, A2A, etc.). Standards anchor all subsequent research:
      framework compliance is evaluated against the standard, not in isolation.
- [ ] Step 0.5 — Map EXISTING dependencies' capabilities for this spec's goal.
      Sequence matters: understand what you already have BEFORE evaluating
      what you might add. For each pinned library this spec touches: fetch
      repo tree, list public interfaces, flag domain-matching abstractions.
- [ ] Existing stack audit — answer: "Does the current stack already solve
      this use case?" This must be answered by inspecting actual behavior
      (source code, POC runs), not by assuming capabilities from names/docs.
      If the existing stack covers 80%+ of the requirement, design around it.
- [ ] Step 0.75 — Dependency behavior deep dive. For load-bearing deps,
      read to PRIVATE METHOD level. Identify internal engines, capability
      boundaries, extensibility mechanics (final? static? SPI?).
      Classify: interface-sound + impl-sound → use directly;
      interface-sound + impl-defective → rewrite same interface;
      interface-defective → build custom. See research-protocol.md.
- [ ] Research — dispatch parallel sub-agents on ALL load-bearing framework APIs
      (including interfaces discovered in Step 0.5).
      This phase is BLOCKING: do NOT ask the user any grill questions until all
      research agents have returned and findings are integrated.
      Skip ONLY when the spec touches nothing beyond pure standard library or
      surfaces already validated by a prior shipped spec's §7 Findings.
- [ ] Behavior validation gate — for each load-bearing design decision,
      classify confidence: Validated (source + behavior confirmed),
      Hypothesis (API exists but behavior unproven → POC required),
      or Unknown (stop, more research needed). See research-protocol.md.
- [ ] Enforcement gates (see research-protocol.md for details):
      - Library Surface Completeness Gate (Step 0.5) — can you name
        EVERY top-level package in each pinned library? Not just the
        classes in architecture.md — ALL packages.
      - Persistence Layer Audit (Step 0.75) — if spec uses ANY
        third-party Repository/DAO, have you read the IMPLEMENTATION
        source (not interface) and answered: what survives round-trip?
        what is lost? append or full-replace? schema compatible?
      - Downstream Consumer Schema Check (Step 0.75) — if spec
        designs or adopts a schema, have you verified it has columns
        for ALL downstream consumers' required fields?

Phase 3 — Clarify + Design (user interaction)
- [ ] Clarify — grill-me loop with user (research findings inform questions)
- [ ] Explore — 2-3 approaches with trade-offs (based on research FACTS, not assumptions)
- [ ] UI sketch checkpoint — if the spec touches UI/UX/CSS/layout or
      user-visible workflow screens, draw low-fidelity sketches before
      asking for final approach confirmation. Use ASCII wireframes or Mermaid
      flows inside the spec; show the concrete controls, labels, empty states,
      add/remove interactions, responsive layout changes, and modal/dialog
      structure. Ask the user to confirm or correct the sketch direction
      before treating the UI design as ready.
- [ ] Confirm — present comparison table, get user's choice
- [ ] Re-estimate — re-score after grill; size may have changed
- [ ] Design — interfaces, data flow, file plan
- [ ] Task-boundary design — identify how `/planning-tasks` should split the
      work. For repeated checks/rules, list each check separately with class
      name, source, positive scenario, negative scenario, and whether POC is
      required.
- [ ] NFR sweep — walk 5 ISO 25010-derived categories (Performance,
      Security, Reliability, Usability, Maintainability). For each: add
      an AC that quantifies the requirement, OR record `N/A — <reason>`
      in §3. Default-skipping is forbidden (see "NFR coverage sweep"
      below).
- [ ] BDD / acceptance confirmation — present the proposed §3 ACs to the
      user as concrete Given/When/Then scenarios and ask for confirmation of:
      behavior, negative cases, evidence type, verification command,
      deployment/manual checks, and pass/fail thresholds. Update the spec
      immediately after each correction. Exit criterion: every AC is either
      confirmed by the user or explicitly marked as inferred from source
      evidence with no open decision.
- [ ] Document — write spec file
- [ ] AC well-formedness check — run the 5-property checklist (Singular,
      Unambiguous, Implementation-free, Verifiable, Bounded) against
      every AC in §3 before handoff. See "AC well-formedness check" below.
- [ ] Review — user reviews spec before handoff
```

**Why the gate matters:** Grilling before research leads to approach comparisons based on assumptions, which get invalidated by research findings, causing multiple pivots and wasted cycles. Research first → grill with facts → one-shot approach selection.

### Research — BLOCKING parallel sub-agents on load-bearing framework APIs

**HARD GATE.** Research MUST complete before the first grill question. Do NOT interleave research and grilling — this causes approach pivots when research findings invalidate assumptions made during early grill questions.

**Why before grilling.** Spec-level design decisions lock downstream implementation. Discovering API drift at implementation time means re-opening the design. Pay the lookup cost now, in parallel.

**When to dispatch.** Any spec that introduces or heavily uses a framework/SDK not yet exercised by a prior shipped spec, a framework surface the project has not yet touched, or a build-system/packaging feature with known drift risk.

**Skip when.** The spec only touches the language's standard library or surfaces already validated by a prior shipped spec's §7 Findings.

**Scope: full API surface, one round.** Dispatch enough sub-agents to cover the ENTIRE API surface relevant to this spec — not just the specific method in question. One round of 3-5 parallel agents is better than 3 rounds of 1-2 agents. Under-scoping the first round is the #1 cause of repeated research.

**Research persistence.** Findings MUST be persisted in the spec's §2.3 Research Citations — not just URLs, but a one-sentence summary per citation. This ensures future revisions and downstream specs don't need to re-research.

Read `references/research-protocol.md` for the full dispatch sequence, prior-art scan, sub-agent prompt template, and persistence rules.

### Clarify — grill-me style loop before designing

The roadmap entry is intentionally coarse-grained. Details emerge by asking. Treat clarification as a LOOP, not one-shot.

Core rules: one question at a time (S-sized may batch 2 related questions); always provide recommended answer; inspect files before asking; walk decision branches; don't stop early.

Read `references/grill-protocol.md` for the full loop rules, focus topics, and troubleshooting patterns.

### User Journey Simulation — before comparing approaches

When designing a capability that involves switching between multiple variants (providers, backends, strategies), before comparing technical approaches, must write 2-3 concrete end-to-end user journey scenarios that exercise every transition direction. Each variant must appear as both source and target in at least one scenario.

**Exit criterion:** A scenario table where no transition direction is uncovered.

**Rationale:** Approach comparisons grounded only in API capabilities miss integration-level constraints that only surface when you simulate the full user flow. A design that looks correct for "add provider B" may break when the user switches back to provider A — a transition the designer never considered because no scenario exercised it.

**Anti-pattern:** Designing a context-replay mechanism for providers B and C, then discovering the user also needs replay when switching *back* to provider A — because no scenario tested the A→B→A round-trip.

### UI Sketch Checkpoint — before confirming UI specs

If the spec changes any user-facing screen, the designer must create a
`Low-Fidelity UI Sketches` subsection before final confirmation. This applies
to frontend pages, modals, forms, tabs, tables, cards, empty states,
responsive layout, CSS-only polish, copy/layout changes, and navigation.

The sketch may be ASCII wireframe, Mermaid flow, or a compact text layout, but
it must show concrete UI elements rather than abstract component names:

- Page/modal structure: header, body, footer, primary/secondary actions.
- Controls: dropdowns, checkboxes, segmented tabs, add/remove buttons, empty
  state CTA, disabled states, loading states when relevant.
- Data shown in each row/card: labels, counts, risk/status badges, version,
  author/category, or other fields visible to the user.
- Responsive behavior when relevant: desktop vs tablet/mobile arrangement.
- User action loop: what the user clicks, what appears, what can be removed or
  edited, and what submit sends.

The sketch must explicitly state what it is **not**: not final pixels, not a
new design system, and not permission to add unrelated visual decoration.
Avoid vague placeholders like `[component]` or `[data here]`; use realistic
labels from the product so the user can correct the workflow before
implementation.

For UI specs, the Confirm step must include the sketch direction. If the user
corrects the interaction (for example "not checkboxes; use a dropdown +
新增 + removable list"), update the sketch and ACs immediately before task
planning.

### Confirm — present approaches, let user choose

After exploring approaches, present a comparison table and **wait for user's choice**. Do not pick for the user on M+ specs.

**Every cell in the table must be grounded in research findings or codebase inspection.** If a Pros/Cons claim is based on an assumption rather than a verified fact, mark it as "(assumed)" and explain why verification was not possible.

Format:
```
| Approach | Pros | Cons | Recommendation |
|----------|------|------|----------------|
| A: ...   | ...  | ...  | ⭐ Recommended |
| B: ...   | ...  | ...  |                |
```

**Anti-pattern:** presenting an approach comparison based on training-data assumptions, then rebuilding the table after research findings arrive. The comparison table should be presented ONCE, based on research facts.

For key design decisions (interface shape, data model, error strategy), explicitly state the decision and ask: "OK to proceed with this approach?"

### Confirm BDD behavior and acceptance standards

After the user confirms the approach, convert the design into draft BDD
scenarios and ask the user to confirm them before task planning. Read
`references/spec-required-data.md` for the required question format and data
fields. The user must see the concrete behavior, data fields, decision,
recommended answer, risk, and spec sections affected by a different choice.

### Maximize framework reuse — build custom only as last resort

After a technology/framework is chosen, default to reusing its types,
interfaces, extension points, and patterns. Read
`references/framework-reuse.md` before designing custom ports, adapters,
serializers, parsers, or DTOs. Custom code is allowed only for verified gaps.

### Challenge assumptions (built-in)

For every major design decision, briefly challenge:
- "Why not the simpler alternative?"
- "What breaks if this assumption is wrong?"
- Document the rationale in the spec's approach section.

### Research Sufficiency Gate — classify confidence before designing

After research completes but BEFORE writing the spec, classify each
load-bearing design decision:

| Confidence | Meaning | Action |
|---|---|---|
| **Validated** | Raw source confirms the API/behavior exists and works as assumed | Design with confidence. Cite source in §2.3. |
| **Hypothesis** | Research suggests it should work, but no hands-on proof. E.g., "this SPI *should* support decoration" or "these two libraries *should* integrate" | **Mark as POC-required** in spec §2. Design the approach but flag uncertainty. |
| **Unknown** | Research could not determine whether the approach works. E.g., library has no docs for this use case, or the behavior depends on runtime interaction | **Stop. More research needed** — dispatch targeted agents, or ask the user for guidance. Do NOT design around unknowns. |

**When research is insufficient to validate a key design decision:**

1. Do NOT guess and write the spec as if it's validated.
2. Explicitly declare `POC: required` in spec §2 with:
   - **What to test**: the specific design hypothesis (not "does the SDK work")
   - **Why research couldn't answer it**: what's missing from docs/source
   - **Suggested POC scope**: minimal test that would confirm or deny
3. The spec's §4 (Interface Design) may still be written, but annotate
   hypothesis-dependent interfaces with `[needs POC validation]`.
4. `/planning-tasks` will execute the POC plan before creating task files.

**Examples:**
- Research finds `AgentSession` has `resume()` method → **Validated**
  (raw source confirms). Design can rely on it.
- Research finds `SessionService` exists but no evidence it integrates
  with `AgentSession` → **Hypothesis**. POC needed: "Can we bridge
  these two APIs? Does the type conversion work?"
- Research finds the framework's SPI *might* support decoration but
  no existing implementation demonstrates it → **Hypothesis**. POC
  needed: "Build a minimal decorator, verify the framework accepts it."

**Anti-pattern:** Writing a confident spec §2 Approach when the core
design decision is actually a hypothesis. This pushes risk downstream
to `/planning-tasks`, where discovering the hypothesis is wrong wastes
all task planning effort.

### Design depth scales with estimation

Read `references/estimation-scale.md` for the full six-dimension rubric (tech risk, uncertainty, dependencies, scope, testing, reversibility), scoring criteria (1–3 per dimension), worked examples, and literature citations. The rubric determines the size bucket:

| Size | Depth | User interaction |
|------|-------|------------------|
| XS (6–8) | Skip approach comparison. Recommend directly. | 3-question intake plus up to 1 spec-specific grill question. If prior context already answers a question, state the answer in §2 Approach and skip asking. |
| S (9–11) | Brief comparison. | 3-4 questions, confirm approach |
| M (12–14) | Full comparison + interface definition. | Confirm approach + key interfaces |
| L+ (15–16) | Deep design + PoC spike may be needed. | Confirm at each phase boundary |

**XL (17–18) = mandatory split.** Decompose into 2+ specs before proceeding. Tech risk = 3 triggers parallel research sub-agents (see Research section above).

### Spec File Structure

Read the template from `references/spec-template.md`. The template has 7 sections. `/planning-spec` fills sections 1-5. Sections 6-7 are added later by `/planning-tasks`.

Roadmap update: add spec row to the Active table with `📐 in-design` status. Write ALL design content (research findings, approach rationale, interface designs, AC details) to the spec file §1-5 — never to the roadmap. The roadmap is a lean index: SpecID / 標題 / 點數 / 相依 / 狀態 columns only.

**Glossary**: Before naming new types or protocols, check the project glossary. If introducing new domain concepts, add entries in the same commit.

### Spec Writing Style — Plain Language, Not Verbose

Specs are read by humans (including junior engineers) who need to understand the design and implement it. Write for clarity, not impressiveness.

**Rules:**
1. **Write prose in Traditional Chinese.** Keep proper nouns, class names, code, commands, API fields, issue codes, and standards in their original spelling.
2. **Lead with a one-liner.** §1 Goal opens with a single sentence a non-expert can understand — what the spec does in plain language, before any technical detail.
3. **Use visual flow diagrams.** An ASCII diagram showing "user does X → system does Y → DB gets Z" conveys more than three paragraphs of prose.
4. **Show example data, not just schema.** Every table definition must include a concrete example showing what 2-3 rows look like after a realistic usage scenario. Include column values, not just column names.
5. **Use tables for comparisons.** "Why A not B" is a table, not paragraphs. Each row is one dimension, each cell is one fact.
6. **Label fields with plain-language sources and rationale.** For JSON/metadata fields, show where each value comes from, who is allowed to set it, the validation/default rule, why the field exists, and what BDD must assert — not just the field name.
7. **Keep it concise.** If a section can be a 5-row table instead of 5 paragraphs, use the table. Cut adjectives. Cut "as mentioned above." Cut any sentence that restates what the reader just read.
8. **BDD must sound like a real scenario.** Use `Given（前提）`, `When（動作）`, `Then（結果）`, `And（而且）`. The scenario should match what the user described, or a better researched real-world case. Do not write only abstract implementation phrases.

### Acceptance verification command (mandatory)

Every spec MUST state in §3 the exact command used to verify its acceptance criteria. The command MUST be the project's standard pipeline entry declared in the QA strategy doc. Do NOT invent per-spec shell scripts.

Typical form:

    Run: `<ecosystem test command>`
    Pass: all tests carrying this spec's AC ids are green.

If this spec genuinely needs verification the standard pipeline cannot provide, DO NOT fork per-spec tooling. Escalate to /planning-project to extend the project-level pipeline so every future spec benefits.

**AC naming contract.** When writing §3 ACs, use the format declared in the QA strategy doc. Tests and task files reference ACs by the same id so the ecosystem test runner can correlate them.

### NFR coverage sweep (before writing §3)

Before writing §3 ACs, walk Performance, Security, Reliability, Usability,
and Maintainability. For each, either add a measurable AC or record explicit
`N/A — <reason>` in §3. Silent skipping is forbidden. The required table shape
is in `references/spec-required-data.md`.

### AC well-formedness check (mandatory before handoff)

Every AC in §3 MUST be singular, unambiguous, implementation-free,
verifiable, and bounded. Rewrite vague ACs until the pass/fail evidence is an
observable API response, UI state, DB row, command output, file content, log
line, or manual inspection result.

## Doc Sync — after design decisions

```
- [ ] PRD scope still accurate?
- [ ] Spec roadmap needs new specs or dependency changes?
- [ ] ADR governance check — does this spec's chosen approach contradict
      a decision previously recorded in the PRD decision log or
      architecture doc? If yes, create an ADR. Trigger: the design is
      CONFIRMED (post-grill, post-POC), not during exploration.
      POC/research-phase findings do NOT require an ADR — only confirmed
      plan changes that contradict existing documented decisions.
```

## Native Tooling Preference

Prefer a native tool's own CLI for one-line independent artifacts, such as
`docker build`, `npm run`, or shell scripts. Wrap with the build system only
when build outputs, version interpolation, or CI task-graph behavior require
it.

## Forbidden File-Plan Patterns

XS/S specs MUST NOT pre-create configs, placeholders, empty directories, or
version catalogs for downstream specs. Cross-cutting tooling is allowed only
when justified in §2 and tied to an AC.

## Troubleshooting — Known Failure Modes

Read `references/troubleshooting.md` when the user corrects the design more
than twice, research invalidates the approach, a framework seems to lack an
expected capability, or `/planning-tasks` reports that the spec is unclear.

## Handoff

After spec file is written, present a summary to the user:
- Chosen approach (one sentence)
- Key interfaces (signatures)
- AC count and coverage
- BDD / acceptance confirmation status:
  - list any ACs confirmed directly by the user
  - list any ACs inferred from source evidence
  - list any remaining unconfirmed behavior or threshold

If any load-bearing BDD behavior, verification command, deployment/manual
evidence, or pass/fail threshold is unconfirmed, do NOT ask "Proceed to task
planning?" yet. Ask the next confirmation question instead.

**XS/S**: Ask user "Proceed to task planning?" — if confirmed, invoke `/planning-tasks [spec-id]`.

**M+**: Always wait for explicit user approval before handoff. The user may want to revise scope, interfaces, or approach.

## Return from /planning-tasks

Spec too large or unclear: re-analyze, split, refine design.

## Escalate

Requirements fundamentally unclear → invoke `/planning-project` to return to Tech Lead.
