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
- [ ] Research — identify the load-bearing framework APIs this spec
      touches; dispatch parallel sub-agents against the OFFICIAL docs
      BEFORE grilling. architecture.md pins versions but docs drift;
      prior memory / training data is stale. Skip only for specs that
      touch nothing beyond pure JDK / already-validated surfaces.
- [ ] Clarify — confirm scope and constraints with user
- [ ] Explore — 2-3 approaches with trade-offs
- [ ] Confirm — present options, get user's choice
- [ ] Design — interfaces, data flow, file plan
- [ ] Document — write spec file
- [ ] Review — user reviews spec before handoff
```

### Research — parallel sub-agents on load-bearing framework docs

**Why before grilling, not during design.** Spec-level design
decisions (which API shape to adopt, which adapter layer to own,
which config knob to expose) lock downstream implementation. If the
framework has moved since architecture.md was pinned — a deprecated
method, a renamed config key, a new recommended idiom — discovering
it at `/implementing-task` time means re-opening the design. Pay
the lookup cost at spec-planning time, in parallel, while the user
answers the first grill questions.

**When to dispatch.** Any spec that introduces or heavily uses:
- A framework/SDK not yet exercised by a prior shipped spec.
- A framework surface (annotation, auto-configuration block,
  SPI / port) the project has not yet touched, even if the
  framework itself is familiar.
- A build-system, packaging, or CI-plugin feature with known drift
  risk (plugin DSLs rename frequently between versions).

**Skip when.** The spec only touches the language's standard library
or surfaces already validated by a prior shipped spec (e.g., XS
specs that add small types on top of shipped infrastructure). If a
prior spec in the same milestone just exercised the exact same API,
lean on its `§7 Findings` instead of re-fetching.

**Concrete sequence.**

1. **List the load-bearing APIs this spec touches** — read the
   roadmap deliverables + architecture doc module + any SBE drafts.
   Name each API by library + entrypoint (e.g.,
   `<library>: <class/annotation/function>`). One entry per distinct
   surface.

2. **Dispatch 1–3 sub-agents in parallel IMMEDIATELY** — before the
   first user grill question. Budget per sub-agent ≤ 10 tool calls.
   One sub-agent per distinct API surface is usually right; collapse
   only if two surfaces live in the same official-doc page. S-sized
   specs usually need 1–2; M+ specs 2–3.

3. **Begin the grill loop** while sub-agents run. Do NOT block.

4. **Integrate findings as they return.** Fold each finding into the
   spec's §2 Approach decision table with the cited URL. If a
   finding contradicts the roadmap's SBE draft (e.g., the
   roadmap assumes method X, docs show X is deprecated and Y is
   current), surface it as the next grill question BEFORE writing
   §4 interfaces.

5. **Cite every source in §2.** No uncited version numbers, no
   uncited API signatures. The citation is the audit trail when
   `/implementing-task` later re-fetches the same doc.

**Sub-agent prompt template** (adapt to the specific API surface):

```
Research [library@version]'s [specific API / entrypoint / pattern]
for a spec I'm designing. Library version is pinned in the project's
architecture doc. Goal: confirm the current official idiom and flag
any drift.

Investigate (≤ 10 tool calls, WebFetch the official docs page
directly — do not rely on blog summaries):
1. Current stable API signature — names, parameter order, return
   shape. Cite the exact docs URL.
2. Deprecated / removed APIs near this surface — anything the spec
   should avoid reaching for.
3. Recommended usage pattern — the canonical example from the
   official docs (config block, entrypoint placement, etc.).
4. Gotchas called out in the docs — nullability, concurrency,
   build/compile-time constraints relevant to this surface.

Output (≤ 400 words):
- Answer per question, each with a citation URL.
- One-paragraph "implication for this spec" — what the spec's §2
  Approach should lock in based on the findings.
- Gaps / items needing a second fetch.

Do NOT fabricate. If a docs page 404s or is behind anti-bot, say so.
```

**Verify before writing.** If a sub-agent's finding is the load-
bearing decision of the spec (the whole §2 Approach hinges on it),
do a second WebFetch to confirm before committing the spec file —
same rule as planning-project. A spec with a wrong API signature
becomes a task loop of corrections.

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

Read `references/estimation-scale.md` for the full six-dimension
rubric (tech risk, uncertainty, dependencies, scope, testing,
reversibility), scoring criteria (1–3 per dimension), worked examples,
and literature citations (COCOMO II, McConnell, Cohn, Bezos Type 1/2,
etc.). The rubric determines the size bucket:

| Size | Depth | User interaction |
|------|-------|------------------|
| XS (6–8) | Skip approach comparison. Recommend directly. | 3-question intake: (a) on-disk state, (b) packaging target, (c) pre-populate configs for future specs vs lazy-add per spec. **If prior context (earlier specs, memory, recent session) already answers a question, state the answer in §2 Approach and skip asking — do not re-ask what the source already reveals.** Plus up to 1 spec-specific grill question on a deliverable that passed the smell test. |
| S (9–11) | Brief comparison. | 3-4 questions, confirm approach |
| M (12–14) | Full comparison + interface definition. | Confirm approach + key interfaces |
| L+ (15–16) | Deep design + PoC spike may be needed. | Confirm at each phase boundary |

**XL (17–18) = mandatory split.** Decompose into 2+ specs before
proceeding. Tech risk = 3 triggers parallel research sub-agents
(see Research section above).

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
