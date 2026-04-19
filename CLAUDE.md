用繁體中文說明

## Principles

先了解產品設計資訊 docs/grimo/PRD.md

IMPORTANT: Follow these in every session.

- **First Principles Thinking**: Address root causes, not surface symptoms
- **Design-Intent Comments**: After understanding the requirement, document *why* the design was chosen
- **Web-Verify First**: Searching official docs is faster than trial-and-error — cite sources
- **Log-Driven Debugging**: When logs are insufficient to identify the root cause, add more logs and retest before planning a fix
- **No Deprecated APIs**: Check `architecture.md` for exact versions and import paths — do NOT guess

## Workflow Skills

7 skills form the development pipeline. Full reference: `.claude/skills/references/workflow-guide.md`

```
/defining-product → /planning-project → /planning-spec S00N
    → /planning-tasks S00N ⟺ /implementing-task (loop)
    → [subagent QA: /verifying-quality] → /shipping-release
```

Key: `/planning-tasks` is the hub. After all tasks pass, it spawns a
**subagent** to run `/verifying-quality` independently (fresh context
catches blind spots that same-session review misses).

## Where things live (read this before ls-ing)

**Project artefacts (in repo):**

| Path | What |
| --- | --- |
| `docs/grimo/PRD.md` | Product vision, **Critical Path**, MVP scope (Critical / Supporting / Backlog / Out), decision log |
| `docs/grimo/architecture.md` | Tech decisions, framework dependency table, module map, data flows |
| `docs/grimo/development-standards.md` | Code conventions, package layout, testing rules (§7), forbidden patterns |
| `docs/grimo/qa-strategy.md` | Test pipeline, verification commands (ecosystem-native preferred) |
| `docs/grimo/glossary.md` | Bilingual (zh-TW + English) domain terms |
| `docs/grimo/specs/spec-roadmap.md` | Live roadmap — all specs, milestones, Backlog |
| `docs/grimo/specs/YYYY-MM-DD-S<NNN>-<slug>.md` | In-flight spec (§1-5 design, §6 task plan, §7 results) |
| `docs/grimo/specs/archive/` | Shipped specs — permanent record |
| `docs/grimo/tasks/` | **Temporary** BDD task files; only exist between `/planning-tasks` and Phase 3; deleted on ship |
| `docs/grimo/CHANGELOG.md` | What shipped + when (appended by `/shipping-release`) |
| `docs/grimo/adr/ADR-NNN-<slug>.md` | In-development decisions that extend or contradict PRD |

## Spring AI Community Agent Client

- 主站文件：https://springaicommunity.mintlify.app/
- Agent Client：https://springaicommunity.mintlify.app/projects/incubating/agent-client

