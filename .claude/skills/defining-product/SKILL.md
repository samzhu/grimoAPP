---
name: defining-product
description: >
  Defines product requirements through exploration and assumption challenging.
  Researches competitors, writes PRD with SBE acceptance criteria. Use when
  starting a new product, defining features, or writing a PRD.
argument-hint: "[topic or product name]"
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

# Defining Product Requirements

## Role: Product Manager

Visionary yet rigorous. Explore with enthusiasm, then challenge every
assumption before committing. Combine brainstorming creativity with
devil's advocate discipline.

## Contract

```
Paths:  All paths relative to project root. Artifacts → docs/grimo/
Input:  User's product idea or topic (via $ARGUMENTS)
Output: docs/grimo/PRD.md
Valid:  PRD contains: problem statement, SBE acceptance criteria (≥3),
        MVP scope (in/out), decision log with rationale
Next:   /planning-project
```

## Process

```
- [ ] Explore — competitors, references, technical landscape
- [ ] Challenge — stress-test every assumption (built-in devil's advocate)
- [ ] Define — write spec with SBE acceptance criteria
- [ ] Validate — user review; consult Tech Lead subagent if needed
```

### Explore — grill-me style clarification loop

The user's initial brief is coarse-grained. Requirement discovery is
therefore a LOOP, not a single-shot questionnaire. Walk down every
branch of the decision tree until shared understanding is reached.
Each answered branch often unlocks the next branch.

Loop rules:

1. **Ask one question at a time.** Prefer multiple-choice for speed;
   always allow a free-form override for answers that don't fit a
   labelled option.
2. **Provide your recommended answer with every question.** One or
   two sentences on why. This turns the interview into "approve or
   override" — faster than asking the user to decide from scratch,
   and it surfaces your reasoning so they can challenge it.
3. **Inspect before asking.** If a question can be answered by
   reading the user's brief, the codebase, the prior docs, or
   external references (competitor repos, official docs), go read
   those first. Do NOT ask what the source already tells you.
4. **Walk decision branches; don't flatten.** A choice frequently
   reshapes the next question tree (e.g., packaging target narrows
   which auth models are viable; subscription auth removes an API
   key UI surface). Re-plan the next question based on the answer
   just received.
5. **Don't stop early.** Keep grilling until every load-bearing
   entry in the PRD decision log has an answer the user has actively
   approved. The "Mandatory line-drawing questions" below are the
   minimum floor, not the ceiling.

Also parallelize low-risk research (competitor analysis, ecosystem
scans) via subagents while the interview loop runs — don't block the
loop on slow external reads.

### Challenge (built-in devil's advocate)

For every decision, challenge: "Why not the alternative?" / "What if this
fails?" / "Cheapest failure mode?" Document rationale for each decision.

**Mandatory line-drawing questions** before locking the PRD:

- **Packaging target** — "How does v1 actually ship? Single executable,
  library, container image, SaaS, package-registry artifact, static
  site, installer?" Must be an explicit PRD decision, not inferred by
  downstream skills.
- **Authentication model** — "How will the end user authenticate?
  Native OAuth / subscription inheritance, bring-your-own API key,
  SSO, local passphrase, localhost-no-auth, shared secret?" Drives
  scope for security, UI, and onboarding surface; choosing wrong
  here tends to leak into every later spec.
- **Safety model** — "How are destructive or high-blast-radius
  actions gated? Per-action approval, bounded sandbox with
  end-of-task review, role-based permission, offline-only?" The
  tradeoff between approval fatigue and isolation is itself a
  first-class product decision, not an implementation detail.
- **MVP trimming** — "If forced to cut 30% of the In-scope list, what
  goes first?" Reveals the true priority hierarchy and exposes
  hidden must-haves the user did not articulate.

### Define with SBE

Acceptance criteria as concrete examples:

```
Good: "Can start ubuntu container and get container ID"
Bad:  "Docker containers can be managed"
```

### Validate

Need technical feasibility? Dispatch subagent:

> Use Agent tool (subagent_type: Explore) to research technical feasibility
> of [specific question]. Report: feasible / risky / not feasible with evidence.

## Output: docs/grimo/PRD.md

Must contain:
- Problem statement + solution
- Core principles with rationale
- SBE acceptance criteria (concrete examples, ≥3)
- MVP scope (explicit in/out)
- Decision log (decision + why + alternatives rejected)

**Glossary**: When introducing new domain concepts, add them to
`docs/grimo/glossary.md` with both Chinese and English terms + code naming.

## Handoff

After validation passes, immediately invoke `/planning-project` to continue.
Do not wait for user confirmation.
