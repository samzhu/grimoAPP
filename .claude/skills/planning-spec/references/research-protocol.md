# Research Protocol

## Hard Gate Rule

Research is Phase 2 of the planning-spec process. It MUST complete before Phase 3 (Clarify/Grill). Do NOT ask the user any grill questions until all research agents have returned and findings are integrated into your working context.

**Rationale:** Grilling before research leads to approach comparisons based on assumptions. When research findings arrive, they invalidate earlier assumptions, causing multiple approach pivots and wasted cycles. Research first → grill with facts → one-shot approach selection.

## Roadmap vs Spec Research — Scope Distinction

Roadmap planning（`/planning-project`）lists broad direction and coarse dependencies. It does NOT deeply research library APIs. **Spec planning（`/planning-spec`）is where deep research happens.** Never assume the roadmap's SBE draft or description has validated any API surface. Treat every library interaction as unverified until raw source confirms it.

## Step -1: Scan Existing Research (MANDATORY)

**Before doing ANY new research, check what's already been researched.**

Re-research is the most expensive form of waste — it consumes agent time, user patience, and context window. Prior research artifacts may already contain the answers needed.

**Action:**
1. Scan `docs/local/` for research notes related to this spec's topic
2. Check prior shipped specs' §7 Findings for validated API patterns
3. Check the spec file itself — it may already have §2.3 Research Citations from a prior design round
4. Read any competitive analysis or technology evaluation docs that touch this spec's domain

**If prior research exists:**
- Read it FIRST before dispatching any research agents
- Note what's still valid vs what needs re-verification (version changes, API drift)
- Only dispatch agents for gaps not covered by existing research

**If prior research contradicts the current spec design:**
- Flag the contradiction immediately — it may invalidate the entire approach
- This is a signal that the spec needs redesign, not more research on the wrong approach

**Skip ONLY when:** This is the first spec in the project (no prior artifacts exist).

## Step 0: Prior Art / Ecosystem Scan

Before investigating specific APIs, ask: **does the upstream ecosystem already provide a ready-made solution for this spec's goal?**

Check:
- The framework or library's GitHub org for existing images, templates, or starters
- Official docs for recommended patterns that match this spec's goal
- Container registries (Docker Hub, GHCR) for pre-built images if the spec involves containers
- Community repos for prior implementations of the same pattern

If a viable upstream solution exists, evaluate reuse vs build-own as the **first grill question**, before diving into implementation details.

## Step 0.5: Exhaust Pinned Libraries' Own API Surface (MANDATORY)

**Before researching how Library A integrates with Library B, map what Library A already offers on its own.**

Skipping this step is the #1 cause of multi-round user corrections. The root cause: assuming a library's scope from its name instead of reading its actual public API. 30 seconds of package browsing prevents multiple rounds of correction.

**Action:**
1. For each pinned library that this spec touches:
   - Fetch the repo tree (top-level packages only)
   - List every public **interface** and **abstract class** in the relevant module
   - Flag interfaces whose name matches this spec's domain concepts
2. For each flagged interface:
   - Fetch raw source to read the **full method list and Javadoc**
   - Record: "Library X already provides interface Y with methods [list]"
   - Note extension points: SPI, decorator hooks, builder parameters, advisor chains
3. Record findings in §2.3 Research Citations under "Library API Surface"
4. Any interface that already models the spec's domain concept becomes a **MANDATORY grill question**: "Should we use/extend this existing abstraction or build our own? What does it NOT provide that we need?"

**Anti-patterns this step prevents:**
- Designing a custom wrapper without first discovering the library already has an extension point designed for exactly that purpose
- Building a custom integration bridge when the library already has an SPI for it
- Researching "how to bridge Library A with Library B" when Library A's own API already covers the use case

**Skip ONLY when:** The spec touches exclusively standard library APIs or surfaces already fully mapped by a prior shipped spec's §7 Findings (with raw source citations, not just prose descriptions).

### Existing Stack Before New Dependencies (sub-rule of Step 0.5)

**Before evaluating any NEW dependency, validate what the existing stack already provides for this use case.**

This is a sequencing rule: research the capabilities of what you already have BEFORE researching what you might add. The question "does the existing stack already solve this?" must be answered first.

**Enforcement sequence:**
1. Map existing dependencies' capabilities for this spec's goal
2. Identify the specific gap (if any) that existing dependencies cannot fill
3. Only THEN evaluate candidate new dependencies to fill that gap
4. If the existing stack covers 80%+ of the requirement, design around it — don't add a dependency for the remaining 20%

**Why this order matters:** Researching a new library's API creates anchoring bias — once you've mapped how Library X solves the problem, you'll design around it even if the existing stack could have solved it more simply. Research the existing stack first to avoid this trap.

**The question that must be answered before adding any dependency:**
- "What does the current stack provide TODAY for this use case?"
- This must be answered by inspecting actual behavior (source code, tests, POC runs), not by assuming capabilities from names or docs.

## Steps 1–5: Dispatch Sequence

1. **List ALL load-bearing APIs this spec touches.** Read the roadmap deliverables, architecture doc, and any SBE drafts. Name each API by library + entrypoint (e.g., `<library>: <class/annotation/function>`). One entry per distinct surface. **Be exhaustive** — under-scoping the API list is the #1 cause of repeated research rounds. **Include interfaces discovered in Step 0.5** — these are often the most important APIs and the ones most likely to be missed if Step 0.5 was skipped.
2. **Dispatch 3–5 sub-agents in parallel IMMEDIATELY** — one round, full coverage. Budget per sub-agent: 10 tool calls max. Cover the ENTIRE API surface relevant to this spec, not just the specific method in question. S-sized specs: 2–3 agents. M+ specs: 3–5 agents.
3. **WAIT for all agents to return.** Do NOT begin the grill loop while agents are running. Integrate ALL findings before the first user question.
4. **Integrate findings into working context.** Fold each finding into a research summary. If a finding contradicts the roadmap's SBE draft (e.g., roadmap assumes method X, docs show X is deprecated and Y is current), note it for the first grill question.
5. **Cite every source in §2.** No uncited version numbers, no uncited API signatures. The citation is the audit trail when `/implementing-task` later re-fetches the same doc.

## Sub-agent Prompt Template

Adapt to the specific API surface:

    Research [library@version]'s [specific API / entrypoint / pattern]
    for a spec I'm designing. Library version is pinned in the project's
    architecture doc. Goal: confirm the current official idiom and flag
    any drift.

    Investigate (≤ 10 tool calls):

    CRITICAL: Fetch RAW SOURCE CODE from GitHub (e.g.,
    raw.githubusercontent.com/.../SomeClass.java), not documentation
    page summaries. Docs may lag behind or omit critical details like
    constructor signatures, field visibility, and interface default methods.

    1. Current stable API signature — names, parameter order, return
       shape. Cite the exact source file URL.
    2. Constructor / Builder parameters — EVERY field. Note which accept
       Sandbox, which use System.setProperty, which have extension points.
    3. Deprecated / removed APIs near this surface — anything the spec
       should avoid reaching for.
    4. Recommended usage pattern — the canonical example from the
       official docs or test suite.
    5. Gotchas called out in the docs or source — nullability, concurrency,
       build/compile-time constraints relevant to this surface.

    Output (≤ 500 words):
    - Answer per question, each with a citation URL (raw source preferred).
    - One-paragraph "implication for this spec" — what the spec's §2
      Approach should lock in based on the findings.
    - Gaps / items needing a second fetch.

    Do NOT fabricate. If a docs page 404s or is behind anti-bot, say so.

## Raw Source Code Rule

**Fetch raw source, not docs summaries.** For load-bearing API decisions, always fetch the actual source file (e.g., `https://raw.githubusercontent.com/<org>/<repo>/refs/heads/main/<path>.java`). Documentation pages may:
- Lag behind the actual API
- Omit critical details (constructor parameters, field visibility, default methods)
- Summarize instead of showing exact signatures

When a sub-agent returns a finding that is the load-bearing decision of the spec, verify it against the raw source before presenting to the user.

## One Round Rule

**Complete research in ONE parallel dispatch.** Multiple sequential rounds indicate the first round was under-scoped.

Common under-scoping patterns to avoid:
- Researching only the specific method mentioned in the roadmap (research the entire class and its collaborators)
- Researching only one library when the spec touches multiple (dispatch one agent per library)
- Researching only the "happy path" API (also check extension points, factories, builders, configuration options)

If a sub-agent identifies a gap that requires a second fetch, that fetch should be a quick confirmation (1-2 tool calls), not a new research round.

## Verify Before Writing

If a sub-agent's finding is the load-bearing decision of the spec (the whole §2 Approach hinges on it), do a second WebFetch to confirm before committing the spec file. A spec with a wrong API signature becomes a task loop of corrections.

## Confidence Classification — Validated vs Hypothesis

After all research agents return, classify EACH load-bearing design
decision before writing the spec:

| Confidence | Evidence required | Spec annotation |
|---|---|---|
| **Validated** | Raw source confirms API exists with expected signature and behavior. Or a prior shipped spec's §7 proved it in production code. **Or a POC test has exercised the API and confirmed actual runtime behavior.** | Cite source URL in §2.3. No further action. |
| **Hypothesis** | Docs suggest it works. Source shows the API exists. But actual runtime behavior (return values, error paths, integration with other APIs) is unproven. | Mark `[needs POC validation]` in §2. Declare `POC: required` with specific test plan. **Do NOT write a committed approach around a hypothesis.** |
| **Unknown** | Could not determine from docs or source. Behavior depends on runtime interaction, undocumented conventions, or versions we haven't tested. | **Do not design around it.** Either dispatch a targeted research agent to resolve, or ask the user. |

### API Surface Mapping vs Behavior Validation

This is the single most important distinction in research. Confusing the
two leads to specs designed around assumptions that collapse at POC time.

| Type | What it answers | Method | Sufficient for design? |
|---|---|---|---|
| **API surface mapping** | "What methods exist?" | Read source code, list interfaces | Necessary but NOT sufficient |
| **Behavior validation** | "What do those methods actually DO?" | Run the code in a POC | **Required** for load-bearing decisions |

**When behavior validation is required (mandatory POC):**
- The spec's core value proposition is "add capability X that the
  framework lacks" — you must PROVE the framework lacks X, not assume it
- The spec bridges two libraries that have never been integrated before
  in this project — prove the integration works
- The spec relies on a specific runtime behavior (return values, state
  changes, error semantics) that docs don't explicitly guarantee

**When API surface mapping is sufficient (no POC needed):**
- The spec uses a well-documented, widely-used API in its standard way
- A prior shipped spec already validated this exact pattern in §7
- The API's behavior is trivially predictable from its signature

**Anti-pattern: Designing a complex solution for a non-existent gap.**
Research may show that Library A "lacks" persistence. But if you only
mapped Library A's API without testing its actual behavior, you might
discover (too late) that Library A delegates persistence to an
underlying system that already handles it. The gap was assumed, not
verified. A 15-minute POC would have caught this before days of
spec design and task planning.

## Research Persistence Rules

Research findings MUST be persisted in the spec file's §2.3 Research Citations section — not just in the conversation context.

- **Not just URLs.** Each citation includes a one-sentence finding summary explaining what was discovered and why it matters.
- **Format:** `[finding summary]: [URL]`
- **Group by topic.** Organize citations by library or surface, not by order of discovery.
- **Record contradictions.** If a finding contradicts the roadmap or architecture doc, note the contradiction explicitly so spec reviewers see it immediately.
- **Include raw source URLs.** Prefer `raw.githubusercontent.com` links over docs page URLs for API signatures.

This ensures future spec revisions don't need to re-research, downstream specs can reference upstream findings, and `/implementing-task` has an audit trail for API decisions.

## Cross-Cutting Research Persistence

When research during spec planning produces findings that go **beyond the scope of the current spec** (e.g., competitive analysis, technology selection rationale, memory architecture research, rejected alternatives with detailed reasoning), persist them in `docs/local/` as a research note file.

**When to create a `docs/local/` research note:**
- Research covers a topic that multiple future specs will reference (e.g., a database comparison that informs the current spec and future related specs)
- Competitive analysis is updated with new findings (e.g., Hermes self-evolution analysis)
- A technology was deeply evaluated and rejected — the rejection rationale saves future re-research

**Format:** `docs/local/<topic>-research.md` with sections: conclusions table, key findings, rejected alternatives, reference sources.

**What stays in the spec vs what goes to `docs/local/`:**
- Spec §2.3: citations directly relevant to THIS spec's approach decision
- `docs/local/`: broader research that informs the ecosystem, not just one spec

**Anti-pattern:** Research findings that exist only in the conversation context. If the conversation is lost, the research must be re-done. Persist early.

## Spec Change Tracking

When research during planning leads to **new spec proposals** (e.g., current spec planning discovers a sub-topic that warrants its own spec), or changes to existing spec dependencies/estimates:

1. Record the new/changed spec in `spec-roadmap.md` immediately (status 🔲)
2. Update the dependency graph if the new spec changes the critical path
3. Note the source in the v-note at the top of the roadmap (e.g., "new spec split from current spec during planning")
4. Add tech debt entries if architecture.md or other docs drift from the new design
