# Grill-Me Protocol

## Loop Rules

1. **Ask one question at a time.** Prefer multiple-choice for speed; always allow free-form override. **S-sized exception:** may batch 2 closely-related questions in one turn if the answer to Q1 does not change Q2's options.
2. **Provide your recommended answer with every question.** One or two sentences on why. Convert the interview into "approve or override" — faster than asking the user to decide from scratch.
3. **Inspect before asking.** If the project files (roadmap, architecture doc, development standards, prior shipped specs, the codebase itself) already answer the question, read them instead. Do NOT ask what the source already reveals.
4. **Walk decision branches; don't flatten.** A spec-level decision (e.g., which library provides a capability) typically determines the next question (e.g., which adapter shape to expose, which errors translate across the port). Re-plan the next question based on the answer just received.
5. **Don't stop early.** Keep grilling until every load-bearing detail is pinned: scope boundaries, constraints, integration points, data shape, error strategy, and the acceptance-verification command.

## Focus Topics

- **Deliverable smell test** (ask FIRST, before implementation details). For each item in the roadmap entry's deliverable list, question its fit: "does this spec really need it, or is it misfiled?" Common misfits: a cross-cutting primitive that only 1–2 modules consume (belongs to one owning module); a feature-specific type that happens to be mentioned in the roadmap (belongs to the feature's own spec). Moving an item out costs less than over-designing it here. Grill implementation detail only on deliverables that pass the smell test.
- **Scope boundaries** — "This spec covers X but not Y — correct?"
- **Constraints** — performance, compatibility, deployment, concurrency, platform limits.
- **Integration points** — "This will interact with [A] and [B]. Any existing on disk that I missed by inspecting?"
- **Assumptions from the roadmap** that must be pinned before design.
- **Acceptance-verification command** — exactly which standard-pipeline command (per the QA strategy doc) gates this spec.

## Troubleshooting

### Dependency is in-progress but not a code-level dependency
**Cause:** The dependency is milestone ordering, not an import relationship.
**Solution:** Note the status and proceed with design. Record in §1 Goal: "parallel design; dep not blocking."

### Research finding contradicts the roadmap description
**Cause:** The roadmap is a coarse-grained draft; details may be outdated.
**Solution:** Surface the contradiction as the next grill question. When writing the spec, update the roadmap description in the same commit.

### User provides a new information source mid-grill
**Cause:** The user knows ecosystem context that the skill does not.
**Solution:** Immediately dispatch a research sub-agent to verify, then fold findings into §2 Approach. Do not defer to after the grill loop.
