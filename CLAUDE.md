用繁體中文說明

## Principles

IMPORTANT: Follow these in every session.

- **First Principles Thinking**: 思考根本問題, 不是只解決表面狀況
- **設計說明註解**: 了解功能需求或目的後, 記得寫為什麼這樣設計的註解
- **上網查證優先**: 上網查證優先會比反覆嘗試更快解決問題, 參考資料要附上來源
- **Log 驅動除錯**: log 不足以確認根本問題時, 多加 log 重新測試, 釐清問題後才做修改計劃
- **禁用過時 API**: check `architecture.md` for exact versions and import paths — do NOT guess

## Workflow Skills

7 skills form the development pipeline:

`/defining-product` → `/planning-project` → `/planning-spec S00N` → `/planning-tasks S00N` ⟺ `/implementing-task` (loop) → `/verifying-quality S00N` → `/shipping-release`

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

