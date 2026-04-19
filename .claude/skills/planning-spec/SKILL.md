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
  - WebFetch
metadata:
  author: samzhu
  version: 2.0.0
  category: workflow-automation
  pattern: iterative-refinement
---

# Planning a Spec Solution

## Role: System Analyst / Designer (SA/SD)

Systematic, analytical, pragmatic. Explore 2-3 approaches before recommending. Define interfaces before implementation. Think in contracts.

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
- [ ] Scan existing research — check for prior research notes, competitive analysis, or prior spec findings related to this spec's topic. Re-research is the most expensive form of waste.
- [ ] Re-sync PRD — scan the PRD for product-level decisions that constrain this spec. Verify the spec's goal aligns with the product's positioning (e.g., "manage X" vs "replace X" vs "bridge X and Y").
- [ ] Inspect current state — list the project directory; diff against what the last planning step recorded.
- [ ] Estimate (initial) — score the six dimensions from the roadmap entry to determine size bucket.

Phase 2 — Research (BLOCKING GATE — must complete before Phase 3)
- [ ] Step -1 — Scan existing research (prior notes, shipped spec findings,
      competitive analysis). Do NOT re-research what's already known.
- [ ] Step 0.5 — Map EXISTING dependencies' capabilities for this spec's goal.
      Sequence matters: understand what you already have BEFORE evaluating
      what you might add. For each pinned library this spec touches: fetch
      repo tree, list public interfaces, flag domain-matching abstractions.
- [ ] Existing stack audit — answer: "Does the current stack already solve
      this use case?" This must be answered by inspecting actual behavior
      (source code, POC runs), not by assuming capabilities from names/docs.
      If the existing stack covers 80%+ of the requirement, design around it.
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

Phase 3 — Clarify + Design (user interaction)
- [ ] Clarify — grill-me loop with user (research findings inform questions)
- [ ] Explore — 2-3 approaches with trade-offs (based on research FACTS, not assumptions)
- [ ] Confirm — present comparison table, get user's choice
- [ ] Re-estimate — re-score after grill; size may have changed
- [ ] Design — interfaces, data flow, file plan
- [ ] Document — write spec file
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

### Maximize framework reuse — build custom only as last resort

After a technology/framework is chosen (pinned in the architecture doc), the default posture is **reuse its types, interfaces, and patterns**. Custom abstractions are justified only when the framework genuinely cannot serve the need.

**Step 0.5 is the enforcement mechanism.** Before designing any interface, `research-protocol.md` Step 0.5 requires mapping every pinned library's public API surface. This step catches the most common reuse failure: assuming a library's scope from its name instead of reading its actual interfaces.

**Reuse checklist (run during Research, after Step 0.5 completes):**
1. Does the framework already define an interface for this capability? → Use it as the port.
2. Does the framework already define request/response types? → Use them, don't invent parallel DTOs.
3. Does the framework provide a configuration/extension point (builder, factory, path override, SPI)? → Use it, even if the mechanism is indirect (e.g., a wrapper script injected via a binary-path setting).
4. Does the framework provide an **advisor/interceptor chain**? → Use it for cross-cutting concerns (persistence, logging, metrics), don't wrap the entire class.
5. Does an upstream model/adapter already integrate with the infrastructure this spec needs? → Inject and configure, don't rewrite.

**Custom implementation is justified when:**
- The framework has a verified gap (confirmed by raw source inspection, not assumption).
- The gap is documented in §2 Challenges Considered with the source URL.
- The custom code follows the framework's own patterns (e.g., implements its SPI, returns its response types).

**Anti-patterns:**
- Designing a custom port interface + custom response types + custom parsing when the framework already provides all three. This creates a parallel type system that must be maintained alongside the framework's evolution.
- **Assuming a library's scope from its name.** Example: `agent-client` sounds like "just a CLI wrapper" but actually provides `AgentSession` (multi-turn abstraction), `AgentSessionRegistry` (lifecycle SPI), and `AgentCallAdvisor` (interceptor chain). Skipping Step 0.5 causes this failure mode — designing a custom persistence wrapper when the library already has an extension point designed for exactly that purpose.

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

## Troubleshooting — Known Failure Modes

### User keeps correcting your design direction
**Symptom:** User provides URLs to library source code you should have found yourself. Correction ratio > 2:1.
**Root cause:** Step 0.5 was skipped or under-scoped. You assumed a library's capabilities from its name or from the roadmap description, which is intentionally coarse-grained.
**Fix:** Stop the grill loop. Go back to Step 0.5. Fetch the repo tree for every pinned library this spec touches. List all public interfaces. Resume the grill only after the API surface is mapped.

### Approach comparison table gets rebuilt after research
**Symptom:** You presented a comparison table, then research findings invalidated it, forcing a rebuild.
**Root cause:** Phase 2 gate violation — grill questions were asked before research completed.
**Fix:** Never present a comparison table until ALL research agents have returned. This is a hard rule, not a guideline.

### Two libraries from the same ecosystem don't integrate
**Symptom:** You designed a bridge between Library A and Library B, but user points out Library A already has an SPI for this purpose.
**Root cause:** Researched Library B's API without first mapping Library A's extension points. Example: researching `spring-ai-session`'s `SessionMemoryAdvisor` without first discovering `agent-client`'s `AgentCallAdvisor` chain.
**Fix:** Step 0.5 requires mapping EACH library independently before researching their integration. The integration question comes AFTER both surfaces are mapped.

### Roadmap description contradicts actual API
**Symptom:** The roadmap says "use X" but X doesn't exist or works differently than described.
**Root cause:** Normal — roadmap is coarse-grained by design. Spec planning is where API verification happens.
**Fix:** Note the contradiction explicitly in the first grill question. Update the roadmap description in the same commit as the spec file.

### Spec designs a complex solution when the framework already solves it
**Symptom:** During `/planning-tasks` POC, the framework's native
capability is discovered to be sufficient. The spec's entire approach
(custom decorators, bridge code, external dependencies) is unnecessary.
**Root cause:** Research mapped what the libraries *expose* but didn't
test what the libraries *already do*. Example: research found
`AgentSessionRegistry` has `find()` and `resume()`, but didn't test
whether `resume()` actually restores conversation context — which it
does, making a custom persistence layer unnecessary.
**Fix:** Research must distinguish between "API surface mapping" and
"behavior validation." When the spec's core value proposition is "add
capability X that the framework lacks," research MUST verify the
framework genuinely lacks X. If verification requires running code,
declare `POC: required` with the specific hypothesis. Do NOT assume
a gap from docs alone.

### Spec introduces dependency to solve a non-existent problem
**Symptom:** The spec adds a new library (e.g., `spring-ai-session`)
to provide functionality that the existing stack already handles
natively (e.g., CLI's own session persistence via `--resume`).
**Root cause:** Research focused on the new library's capabilities
without first asking "does the existing stack already solve this?"
Step 0.5 mapped the library's API but didn't validate the product
requirement against the existing stack.
**Fix:** Before evaluating any NEW dependency, answer: "What does the
existing stack provide TODAY for this use case?" This question must
be answered by inspecting actual behavior (source code, test runs),
not by assuming capabilities from names or docs. If the existing
stack provides 80%+ of the requirement, design around it — don't
add a dependency for the remaining 20%.

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
