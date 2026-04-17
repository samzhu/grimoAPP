# Research Protocol

## Hard Gate Rule

Research is Phase 2 of the planning-spec process. It MUST complete before Phase 3 (Clarify/Grill). Do NOT ask the user any grill questions until all research agents have returned and findings are integrated into your working context.

**Rationale:** Grilling before research leads to approach comparisons based on assumptions. When research findings arrive, they invalidate earlier assumptions, causing multiple approach pivots and wasted cycles. Research first → grill with facts → one-shot approach selection.

## Step 0: Prior Art / Ecosystem Scan

Before investigating specific APIs, ask: **does the upstream ecosystem already provide a ready-made solution for this spec's goal?**

Check:
- The framework or library's GitHub org for existing images, templates, or starters
- Official docs for recommended patterns that match this spec's goal
- Container registries (Docker Hub, GHCR) for pre-built images if the spec involves containers
- Community repos for prior implementations of the same pattern

If a viable upstream solution exists, evaluate reuse vs build-own as the **first grill question**, before diving into implementation details.

## Steps 1–5: Dispatch Sequence

1. **List ALL load-bearing APIs this spec touches.** Read the roadmap deliverables, architecture doc, and any SBE drafts. Name each API by library + entrypoint (e.g., `<library>: <class/annotation/function>`). One entry per distinct surface. **Be exhaustive** — under-scoping the API list is the #1 cause of repeated research rounds.
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

## Research Persistence Rules

Research findings MUST be persisted in the spec file's §2.3 Research Citations section — not just in the conversation context.

- **Not just URLs.** Each citation includes a one-sentence finding summary explaining what was discovered and why it matters.
- **Format:** `[finding summary]: [URL]`
- **Group by topic.** Organize citations by library or surface, not by order of discovery.
- **Record contradictions.** If a finding contradicts the roadmap or architecture doc, note the contradiction explicitly so spec reviewers see it immediately.
- **Include raw source URLs.** Prefer `raw.githubusercontent.com` links over docs page URLs for API signatures.

This ensures future spec revisions don't need to re-research, downstream specs can reference upstream findings, and `/implementing-task` has an audit trail for API decisions.
