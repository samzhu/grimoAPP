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

Systematic, analytical, pragmatic. Explore 2-3 approaches before recommending. Define interfaces before implementation. Think in contracts.

## Contract

```
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
- [ ] Inspect current state — list the project directory; diff against what the last planning step recorded. New or modified files may reshape scope.
- [ ] Re-sync PRD — scan the PRD for edits since design started. If the user's thinking has moved, ask before assuming.
- [ ] Research — identify the load-bearing framework APIs this spec touches; dispatch parallel sub-agents against the OFFICIAL docs BEFORE grilling. The architecture doc pins versions but docs drift; prior memory / training data is stale. Skip only for specs that touch nothing beyond pure standard library / already-validated surfaces.
- [ ] Clarify — confirm scope and constraints with user
- [ ] Explore — 2-3 approaches with trade-offs
- [ ] Confirm — present options, get user's choice
- [ ] Design — interfaces, data flow, file plan
- [ ] Document — write spec file
- [ ] Review — user reviews spec before handoff
```

### Research — parallel sub-agents on load-bearing framework docs

**Why before grilling.** Spec-level design decisions lock downstream implementation. Discovering API drift at implementation time means re-opening the design. Pay the lookup cost now, in parallel.

**When to dispatch.** Any spec that introduces or heavily uses a framework/SDK not yet exercised by a prior shipped spec, a framework surface the project has not yet touched, or a build-system/packaging feature with known drift risk.

**Skip when.** The spec only touches the language's standard library or surfaces already validated by a prior shipped spec's §7 Findings.

**Research persistence.** Findings MUST be persisted in the spec's §2.3 Research Citations — not just URLs, but a one-sentence summary per citation. This ensures future revisions and downstream specs don't need to re-research.

Read `references/research-protocol.md` for the full dispatch sequence, prior-art scan, sub-agent prompt template, and persistence rules.

### Clarify — grill-me style loop before designing

The roadmap entry is intentionally coarse-grained. Details emerge by asking. Treat clarification as a LOOP, not one-shot.

Core rules: one question at a time (S-sized may batch 2 related questions); always provide recommended answer; inspect files before asking; walk decision branches; don't stop early.

Read `references/grill-protocol.md` for the full loop rules, focus topics, and troubleshooting patterns.

### Confirm — present approaches, let user choose

After exploring approaches, present a comparison table and **wait for user's choice**. Do not pick for the user on M+ specs.

Format:
```
| Approach | Pros | Cons | Recommendation |
|----------|------|------|----------------|
| A: ...   | ...  | ...  | ⭐ Recommended |
| B: ...   | ...  | ...  |                |
```

For key design decisions (interface shape, data model, error strategy), explicitly state the decision and ask: "OK to proceed with this approach?"

### Challenge assumptions (built-in)

For every major design decision, briefly challenge:
- "Why not the simpler alternative?"
- "What breaks if this assumption is wrong?"
- Document the rationale in the spec's approach section.

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

Update the spec roadmap status to in-design.

**Glossary**: Before naming new types or protocols, check the project glossary. If introducing new domain concepts, add entries in the same commit.

### Acceptance verification command (mandatory)

Every spec MUST state in §3 the exact command used to verify its acceptance criteria. The command MUST be the project's standard pipeline entry declared in the QA strategy doc. Do NOT invent per-spec shell scripts.

Typical form:

    Run: `<ecosystem test command>`
    Pass: all tests carrying this spec's AC ids are green.

If this spec genuinely needs verification the standard pipeline cannot provide, DO NOT fork per-spec tooling. Escalate to /planning-project to extend the project-level pipeline so every future spec benefits.

**AC naming contract.** When writing §3 ACs, use the format declared in the QA strategy doc. Tests and task files reference ACs by the same id so the ecosystem test runner can correlate them.

## Doc Sync — after design decisions

```
- [ ] PRD scope still accurate?
- [ ] Spec roadmap needs new specs or dependency changes?
```

## Native Tooling Preference

When a task can be accomplished with a native tool's own CLI (e.g., `docker build`, `npm run`, shell script), prefer it over wrapping with the build system (e.g., Gradle task, Maven plugin).

**Prefer native when:**
- The tool invocation is a one-liner with no build-system inputs
- The artifact is independent of the compilation pipeline
- The tool has its own well-documented CLI

**Wrap with build system only when:**
- Build outputs are inputs to the build task graph (e.g., a jar depends on a generated file)
- Version/property interpolation from the build script is essential
- CI pipeline benefits from build-system up-to-date checking

Rationale: extra layers add plugin compatibility risk, version drift between plugin and tool, and maintenance burden. A direct `docker build` is more portable and debuggable than a build-system Docker plugin invocation.

## Forbidden File-Plan Patterns

An XS or S spec MUST NOT pre-create files — configs, placeholders, empty directories, version catalogs — for downstream specs that have not yet shipped. If file X is only needed by spec N, it lands in spec N, not earlier.

Exception: project-wide formatters, linters, CI configs, or other cross-cutting tooling where the cost of retro-fitting exceeds the cost of early adoption. These MUST be explicitly justified in the spec's Approach section (§2) and tied to an acceptance criterion.

Rationale: pre-populated config drift is a common source of "this was never needed" cleanup work, and it hides the real dependency graph between specs. A spec should add only what its own acceptance criteria demand.

## Handoff

After spec file is written, present a summary to the user:
- Chosen approach (one sentence)
- Key interfaces (signatures)
- AC count and coverage

**XS/S**: Ask user "Proceed to task planning?" — if confirmed, invoke `/planning-tasks [spec-id]`.

**M+**: Always wait for explicit user approval before handoff. The user may want to revise scope, interfaces, or approach.

## Return from /planning-tasks

Spec too large or unclear: re-analyze, split, refine design.

## Escalate

Requirements fundamentally unclear → invoke `/planning-project` to return to Tech Lead.
