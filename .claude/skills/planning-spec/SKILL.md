---
name: planning-spec
description: >
  Analyzes and designs the solution for a single spec. Compares approaches,
  defines interfaces, writes spec file with SBE acceptance criteria.
  Use when a spec needs solution design, or the user says "design S002."
argument-hint: "[spec-id]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Write
  - Edit
  - Agent
metadata:
  category: workflow-automation
  pattern: iterative-refinement
---

# Planning a Spec Solution

## Role: System Analyst / Designer (SA/SD)

Systematic, analytical, pragmatic. Explore 2-3 approaches before recommending.
Define interfaces before implementation. Think in contracts.

## Contract

```
Paths:  All paths relative to project root. Artifacts → docs/grimo/
Input:  docs/grimo/specs/spec-roadmap.md (spec definition with SBE criteria)
        docs/grimo/architecture.md (framework versions, patterns)
        docs/grimo/development-standards.md
Output: docs/grimo/specs/YYYY-MM-DD-<spec-id>-<topic>.md (THE spec file)
Valid:  Contains: chosen approach + rationale, interface signatures,
        file plan, SBE acceptance criteria (concrete examples)
Next:   /planning-tasks [spec-id]
```

## Key Principle: One Spec = One File

The spec file is a **living document**. It starts as a design, accumulates
task results, and ends as a complete implementation record.

**Lifecycle of a spec file:**
```
/planning-spec  → creates spec with sections 1-5 (design)
/planning-tasks → adds section 6 (task plan) inside the spec
/implementing-task → task files are temporary work items
/planning-tasks (final) → adds section 7 (results) + cleans up task files
/shipping-release → archives the ONE completed spec file
```

## Prerequisites

Read input files. Verify spec's dependencies are `✅`. If not met, stop.

Read `docs/grimo/architecture.md` for framework versions and patterns.
**Use the exact versions and import paths defined there. Do not guess APIs.**

## Process

```
- [ ] Inspect current state — list the project directory; diff against
      what the last planning step recorded. New or modified files may
      reshape scope (e.g., the user ran a scaffolder / cloned a
      template between skill invocations).
- [ ] Re-sync PRD — scan the PRD for edits since design started. If
      the user's thinking has moved, ask before assuming.
- [ ] Clarify — confirm scope and constraints with user
- [ ] Explore — 2-3 approaches with trade-offs
- [ ] Confirm — present options, get user's choice
- [ ] Design — interfaces, data flow, file plan
- [ ] Document — write spec file
- [ ] Review — user reviews spec before handoff
```

### Clarify — grill-me style loop before designing

The roadmap entry for this spec is intentionally coarse-grained.
Details emerge by asking. Treat clarification as a LOOP, not one-shot.

Loop rules:

1. **Ask one question at a time.** Prefer multiple-choice for speed;
   always allow free-form override.
2. **Provide your recommended answer with every question.** One or
   two sentences on why. Convert the interview into "approve or
   override" — faster than asking the user to decide from scratch.
3. **Inspect before asking.** If the project files (roadmap,
   architecture doc, development-standards, prior shipped specs, the
   codebase itself) already answer the question, read them instead.
   Do NOT ask what the source reveals.
4. **Walk decision branches; don't flatten.** A spec-level decision
   (e.g., which library provides a capability) typically determines
   the next question (e.g., which adapter shape to expose, which
   errors translate across the port). Re-plan the next question
   based on the answer just received.
5. **Don't stop early.** Keep grilling until every load-bearing
   detail is pinned: scope boundaries, constraints, integration
   points, data shape, error strategy, and the acceptance-
   verification command (see below).

Focus topics for the loop:

- **Deliverable smell test** (ask FIRST, before implementation
  details). For each item in the roadmap entry's deliverable list,
  question its fit: "does this spec really need it, or is it
  misfiled?" Common misfits: a cross-cutting primitive that only
  1–2 modules consume (belongs to one owning module); a feature-
  specific type that happens to be mentioned in the roadmap (belongs
  to the feature's own spec). Moving an item out of the spec (to
  Backlog or to its true owning spec) costs less than over-designing
  it here. Grill implementation detail only on deliverables that
  pass the smell test.
- **Scope boundaries** — "This spec covers X but not Y — correct?"
- **Constraints** — performance, compatibility, deployment,
  concurrency, platform limits.
- **Integration points** — "This will interact with [A] and [B]. Any
  existing on disk that I missed by inspecting?"
- **Assumptions from the roadmap** that must be pinned before design.
- **Acceptance-verification command** — exactly which standard-
  pipeline command (per the QA strategy doc) gates this spec.

### Confirm — present approaches, let user choose

After exploring approaches, present a comparison table and **wait for
user's choice**. Do not pick for the user on M+ specs.

Format:
```
| Approach | Pros | Cons | Recommendation |
|----------|------|------|----------------|
| A: ...   | ...  | ...  | ⭐ Recommended |
| B: ...   | ...  | ...  |                |
```

For key design decisions (interface shape, data model, error strategy),
explicitly state the decision and ask: "OK to proceed with this approach?"

### Challenge assumptions (built-in)

For every major design decision, briefly challenge:
- "Why not the simpler alternative?"
- "What breaks if this assumption is wrong?"
- Document the rationale in the spec's approach section.

### Design depth scales with estimation

| Size | Depth | User interaction |
|------|-------|------------------|
| XS | Skip approach comparison. Recommend directly. | 3-question intake: (a) on-disk state, (b) packaging target, (c) pre-populate configs for future specs vs lazy-add per spec. **If prior context (earlier specs, memory, recent session) already answers a question, state the answer in §2 Approach and skip asking — do not re-ask what the source already reveals.** Plus up to 1 spec-specific grill question on a deliverable that passed the smell test. |
| S | Brief comparison. | 3-4 questions, confirm approach |
| M | Full comparison + interface definition. | Confirm approach + key interfaces |
| L+ | Deep design + PoC spike may be needed. | Confirm at each phase boundary |

### Spec File Structure

File: `docs/grimo/specs/YYYY-MM-DD-<spec-id>-<topic>.md`

Read the template from `references/spec-template.md`.

The template has 7 sections. `/planning-spec` fills sections 1-5.
Sections 6-7 are added later by `/planning-tasks`.

Update `spec-roadmap.md` status to `⏳ Design`.

**Glossary**: Before naming new types/protocols, check `docs/grimo/glossary.md`.
If introducing new domain concepts, add entries in the same commit.

### Acceptance verification command (mandatory)

Every spec MUST state in §3 the exact command used to verify its
acceptance criteria. The command MUST be the project's standard
pipeline entry declared in the QA strategy doc. Do NOT invent
per-spec shell scripts.

Typical form:

    Run: `<ecosystem test command>`
    Pass: all tests carrying this spec's AC ids — via @DisplayName /
          @Tag / name convention documented in the QA strategy — are
          green.

If this spec genuinely needs verification the standard pipeline cannot
provide, DO NOT fork per-spec tooling. Escalate to /planning-project
to extend the project-level pipeline so every future spec benefits.

**AC naming contract.** When writing §3 ACs, use the format declared
in the QA strategy doc. Tests and task files reference ACs by the
same id so the ecosystem test runner can correlate them without
shell parsing.

## Doc Sync — after design decisions

```
- [ ] PRD.md scope still accurate?
- [ ] spec-roadmap.md needs new specs or dependency changes?
```

## Forbidden File-Plan Patterns

An XS or S spec MUST NOT pre-create files — configs, placeholders,
empty directories, version catalogs — for downstream specs that have
not yet shipped. If file X is only needed by spec N, it lands in
spec N, not earlier.

Exception: project-wide formatters, linters, CI configs, or other
cross-cutting tooling where the cost of retro-fitting exceeds the cost
of early adoption. These MUST be explicitly justified in the spec's
Approach section (§2) and tied to an acceptance criterion.

Rationale: pre-populated config drift is a common source of
"this was never needed" cleanup work, and it hides the real
dependency graph between specs. A spec should add only what its own
acceptance criteria demand.

## Handoff

After spec file is written, present a summary to the user:
- Chosen approach (one sentence)
- Key interfaces (signatures)
- AC count and coverage

**XS/S**: Ask user "Proceed to task planning?" — if confirmed,
invoke `/planning-tasks [spec-id]`.

**M+**: Always wait for explicit user approval before handoff.
The user may want to revise scope, interfaces, or approach.

## Return from /planning-tasks

Spec too large or unclear: re-analyze, split, refine design.

## Escalate

Requirements fundamentally unclear → invoke `/planning-project` to return to Tech Lead.
