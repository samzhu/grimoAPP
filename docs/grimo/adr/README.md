# ADRs — Architecture Decision Records

Decisions made **during the planning phase** (PRD + architecture.md)
are not ADRs. The PRD's Decision Log (section 9) is the single source
of truth for those.

Only **new decisions that emerge during implementation** go here.

## Format

File name: `ADR-NNN-<slug>.md` (zero-padded 3-digit counter).

```markdown
# ADR-NNN — <Decision title>

- **Status:** proposed | accepted | superseded by ADR-NNN
- **Date:** YYYY-MM-DD
- **Deciders:** <github handles>
- **Context source:** <which spec / which incident drove this>

## Context

(2-4 sentences: what forced this decision)

## Decision

(1-2 sentences: what we will do)

## Consequences

- Positive: ...
- Negative: ...
- Follow-ups: ...

## Alternatives considered

- <option> — rejected because ...
```

When adding an ADR, update `docs/grimo/architecture.md` in the same PR
if the decision changes an architectural invariant.
