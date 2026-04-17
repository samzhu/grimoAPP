# Research Protocol

## Step 0: Prior Art / Ecosystem Scan

Before investigating specific APIs, ask: **does the upstream ecosystem already provide a ready-made solution for this spec's goal?**

Check:
- The framework or library's GitHub org for existing images, templates, or starters
- Official docs for recommended patterns that match this spec's goal
- Container registries (Docker Hub, GHCR) for pre-built images if the spec involves containers
- Community repos for prior implementations of the same pattern

If a viable upstream solution exists, evaluate reuse vs build-own as the **first grill question**, before diving into implementation details.

## Steps 1–5: Dispatch Sequence

1. **List the load-bearing APIs this spec touches.** Read the roadmap deliverables, architecture doc, and any SBE drafts. Name each API by library + entrypoint (e.g., `<library>: <class/annotation/function>`). One entry per distinct surface.
2. **Dispatch 1–3 sub-agents in parallel IMMEDIATELY** — before the first user grill question. Budget per sub-agent: 10 tool calls max. One sub-agent per distinct API surface is usually right; collapse only if two surfaces live in the same official-doc page. S-sized specs usually need 1–2; M+ specs 2–3.
3. **Begin the grill loop** while sub-agents run. Do NOT block.
4. **Integrate findings as they return.** Fold each finding into the spec's §2 Approach decision table with the cited URL. If a finding contradicts the roadmap's SBE draft (e.g., roadmap assumes method X, docs show X is deprecated and Y is current), surface it as the next grill question BEFORE writing §4 interfaces.
5. **Cite every source in §2.** No uncited version numbers, no uncited API signatures. The citation is the audit trail when `/implementing-task` later re-fetches the same doc.

## Sub-agent Prompt Template

Adapt to the specific API surface:

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

## Verify Before Writing

If a sub-agent's finding is the load-bearing decision of the spec (the whole §2 Approach hinges on it), do a second WebFetch to confirm before committing the spec file. A spec with a wrong API signature becomes a task loop of corrections.

## Research Persistence Rules

Research findings MUST be persisted in the spec file's §2.3 Research Citations section — not just in the conversation context.

- **Not just URLs.** Each citation includes a one-sentence finding summary explaining what was discovered and why it matters.
- **Format:** `[finding summary]: [URL]`
- **Group by topic.** Organize citations by library or surface, not by order of discovery.
- **Record contradictions.** If a finding contradicts the roadmap or architecture doc, note the contradiction explicitly so spec reviewers see it immediately.

This ensures future spec revisions don't need to re-research, downstream specs can reference upstream findings, and `/implementing-task` has an audit trail for API decisions.
