# Grimo Workflow Skill Context 矩陣

狀態：draft
日期：2026-05-28

## 目的

這份文件定義 Grimo 的專案記憶要如何接到 repo 內的 skills。目標是避免 workflow drift：前端任務應該自動把 frontend UI 規範與 Webwright review prompt 帶進上下文；後端功能則應該自動把 architecture、database migration、unit test、integration test 責任帶進上下文。

核心規則：

```text
AGENTS.md 路由工作類型 -> skill 執行工作流 -> context pack 提供 Grimo 專案記憶 -> standards/playbooks/prompts 保存細節
```

## 目前觀察

目前 skills 已經有清楚的 workflow 骨架：

- `/defining-product -> /planning-project -> /planning-spec -> /planning-tasks -> /implementing-task -> /verifying-quality -> /shipping-release`
- 核心 lifecycle skills 通常都有宣告 input、output 與 next skill。
- `root-cause-debugging`、`springboot-project-architect`、`playwright-expert`、`handover`、`takeover` 這些支援型 skills 則提供聚焦能力。

目前缺口是：skills 會讀 `development-standards.md`、`qa-strategy.md` 這類寬泛文件，但還沒有一層明確 routing 說：

- frontend UI work 必須讀 frontend UI rules、design tokens、prototype handoff、Playwright visual gate、Webwright prompts；
- backend feature work 必須讀 backend architecture、database rules、testing rules、migration rules、release gate expectations；
- prompt library 是 project memory，不是 skill internals；
- skills 應保持為可重複使用的 workflow engines，不是每個 Grimo-specific rule 的儲存位置。

## 建議的 Context Pack Contract

每個 context pack 應該短、可路由，不應複製完整 standards 或 prompt files。

建議格式：

```markdown
# <Name> Context Pack

使用時機：
- <可觀察的觸發條件>

必讀：
- <project docs>
- <standards>
- <playbooks>
- <prompts>

必須記得：
- <不可違反的專案規則>

必要輸出：
- <必須產出或更新的 docs/code/tests/evidence>

驗證：
- <commands 或 evidence gates>
```

## 建議的 Context Packs

| Context pack | 目的 | 主要來源 |
| --- | --- | --- |
| `product-definition` | 產品想法、PRD、scope、critical path、SBE acceptance language | `PRD.md`、`glossary.md`、references |
| `project-planning` | 專案層 architecture、dependency versions、standards、roadmap、QA baseline | `architecture.md`、`development-standards.md`、`qa-strategy.md`、`spec-roadmap.md` |
| `planning-spec` | 單一 spec 設計要求與 context-pack selection | `spec-roadmap.md`、`architecture.md`、standards、ADRs |
| `frontend-ui` | React/Vite UI、layout、prototype parity、design tokens、UI state rules | `standards/frontend-ui.md`、`design/tokens.json`、`ui/prototype/*` |
| `visual-review` | Playwright visual gate 與 Webwright review 使用方式 | `qa-strategy.md`、`prompts/webwright/frontend-ui.md`、`frontend/e2e/*` |
| `backend-feature` | Spring/domain/application/adapter backend behavior | `architecture.md`、`standards/backend-spring.md`、`standards/testing.md` |
| `database-change` | Schema migration、rollback、seed data、repository tests | `standards/database.md`、`standards/testing.md`、backend migration docs |
| `agent-workflow` | Skills、workflow recipes、agent-facing task API、learning loop | `standards/agent-workflow.md`、`.agents/skills/*` |
| `release-quality` | Verification command registry、release gate、changelog、archive | `qa-strategy.md`、`scripts/verify-release.sh`、`CHANGELOG.md` |
| `debug-root-cause` | 重複 failure debugging、official-doc grounding、experiment cleanup | `handovers/archive/*`、`architecture.md`、technology-specific standards |
| `session-handoff` | Resume / shift continuity | `handovers/HANDOVER.md`、`handovers/archive/*` |
| `skill-development` | 建立或修改 agent skills | `.agents/skills/*`、skill-author references |

## Skill 矩陣

### 核心產品工作流

| Skill | 目前 input | 目前 output | 目前會讀或期待 | 應接的 context packs |
| --- | --- | --- | --- | --- |
| `defining-product` | 使用者的 product idea 或 topic | `docs/grimo/PRD.md`、`docs/grimo/glossary.md` | User brief、market/competitor research、可用時讀既有 repo docs | `product-definition` |
| `planning-project` | 已核定的 `docs/grimo/PRD.md` | `architecture.md`、`adr/*`、`development-standards.md`、`qa-strategy.md`、`specs/spec-roadmap.md`、`glossary.md` | `PRD.md`、repo inventory、dependency registries、official docs、status mode 時讀 `spec-roadmap.md` | `product-definition`、`project-planning`，並建立或刷新 `frontend-ui`、`backend-feature`、`database-change`、`release-quality` 等 domain packs |
| `planning-spec` | Roadmap entry、architecture、development standards | Sections 1-5 的單一 spec file | `spec-roadmap.md`、`PRD.md`、`architecture.md`、`development-standards.md`、prior research、active specs、official docs、ADRs | 一律 `planning-spec`；再依 touched surface 加 `frontend-ui`、`visual-review`、`backend-feature`、`database-change`、`agent-workflow` |
| `planning-tasks` | 已設計完成的 spec sections 1-5、development standards | Temporary task files、spec section 6 task plan、後續 section 7 consolidation | Spec file、`development-standards.md`、`spec-roadmap.md`、PRD、prior shipped spec results、POC findings | 讀取 spec 宣告的 context packs；除非缺漏，否則不要重新 discover |
| `implementing-task` | 一個 pending task file、spec、architecture、development standards | Production code、tests、task result status | Task file、spec、`architecture.md`、`development-standards.md`、`glossary.md`、POC folder、load-bearing framework APIs 的 official docs | 讀取 task 宣告的 context packs；frontend tasks 包含 `frontend-ui`/`visual-review`；backend tasks 依需要包含 `backend-feature`/`database-change` |
| `verifying-quality` | 已完成、需要 independent QA 的 spec/tasks | QA verdict、spec results update、可能更新 verification registry/script | Spec、`qa-strategy.md`、`development-standards.md`、`glossary.md`、`scripts/verify-release.sh`、build config | `release-quality` 加上 spec 宣告的所有 context packs |
| `shipping-release` | Section 7 local release PASS 的 spec、roadmap | Archived spec、changelog、roadmap status、必要時 commit/tag | Spec、`spec-roadmap.md`、`CHANGELOG.md`、tasks、`scripts/verify-release.sh`、可能需要 sync 的 docs | `release-quality` 加上 spec 宣告的所有 context packs，用來判斷哪些 docs 可能需要更新 |

### 驗證與工具 Skills

| Skill | 目前 input | 目前 output | 目前會讀或期待 | 應接的 context packs |
| --- | --- | --- | --- | --- |
| `playwright-expert` | Playwright setup/design/verify request，或帶 spec id 的 upstream handoff | `e2e/` workspace、Playwright spec files、`e2e/results/evidence.json` | Spec ACs、`qa-strategy.md` known limitations、skill 自己的 references/assets | frontend/UI 時接 `visual-review`；產生 acceptance evidence 時接 `release-quality` |
| `springboot-project-architect` | Existing Spring Boot project 或 new project config requirement | Optimized YAML/config、`{App}Properties`、validation result | Spring config files、build files、official Spring property docs、skill 自己的 references | `backend-feature`；persistence/config 觸及 DB 時加 `database-change`；verification command integration 時加 `release-quality` |
| `root-cause-debugging` | 重複不變的 error、failed build/test/CI/debug loop | Root-cause chain、minimal fix direction、cleaned experiment path | Error output、code/config grep、project README/docs、recent handovers、git history、official docs | `debug-root-cause`；再依 failing surface 加 technology pack，例如 `backend-feature`、`frontend-ui` 或 `database-change` |
| `using-git-worktrees` | Risky isolated work、SDK POC、multi-attempt debug、hotfix、editing subagent | `.worktrees/<name>/` workspace lifecycle | Git status、`.gitignore`、`.claude/settings*`、project policy | 重複 debug 時接 `debug-root-cause`；risky POC 時接 `planning-spec`；其他情況通常不需 domain pack |

### 研究、設計與澄清 Skills

| Skill | 目前 input | 目前 output | 目前會讀或期待 | 應接的 context packs |
| --- | --- | --- | --- | --- |
| `deep-research` | External project/product name 或 URL | `docs/deepwiki/<product>/` research docs | External repo/source/docs、official specs、可選擇 tie-in Grimo roadmap | 只有當 research 要餵給 Grimo roadmap/spec decisions 時，才接 `project-planning` 或 `planning-spec` |
| `grill-me` | 使用者要求 stress-test plan | Conversation 中澄清 decisions | 如果問題可從 codebase 回答，會探索 codebase | Workflow 內使用時接 `product-definition` 或 `planning-spec`；其他情況不需 |
| `grill-with-docs` | 針對既有 domain model stress-test plan | 在 generic repos 更新 `CONTEXT.md`/ADRs | `CONTEXT.md`、`CONTEXT-MAP.md`、ADRs、code | Grimo 中應改接 `glossary.md`、`adr/*` 與相關 surface pack（`frontend-ui`、`backend-feature` 等） |
| `design-taste-frontend` | Landing page、portfolio 或 redesign brief | Frontend design direction 與 implementation guidance | User brief、references、existing design assets | 只有 Grimo frontend 明確要求設計 taste 時搭配 `frontend-ui`；task-board/dashboard product UI 不應套用，除非明確 redesign visual language |
| `minimalist-ui` | Clean editorial-style UI request | 特定 UI build 的 aesthetic rules | User brief 與 local UI files | 同 `design-taste-frontend`；除非產品設計有意改變，否則不應覆蓋 Grimo prototype/tokens |

### Skill、Session 與 Learning Skills

| Skill | 目前 input | 目前 output | 目前會讀或期待 | 應接的 context packs |
| --- | --- | --- | --- | --- |
| `skill-author` | New skill request 或 existing skill audit | New/optimized skill folder、validation report | Target skill folder、`skill-author` references、validator script | `skill-development`；若 skill 影響 Grimo workflow routing，另接 `agent-workflow` |
| `retro` | 使用者要求 retro，或 session 中有 user corrections / direction changes | Reusable trigger-action checklist、使用者批准後可能更新 skill/AGENTS | Conversation evidence、skill-building guide reference | findings 會影響 Grimo workflow 時接 `agent-workflow`；否則只在使用者選擇後做 project-agnostic persistence |
| `handover` | Current session context | `docs/grimo/handovers/HANDOVER.md` | Conversation、git branch/status/log、related files | `session-handoff`；handover 內應保存 active context packs，讓下一個 agent 正確恢復 |
| `takeover` | `docs/grimo/handovers/HANDOVER.md` | Archived handover 與 user briefing | Active handover、archive folder | `session-handoff`；briefing 後應恢復列出的 context packs 再繼續工作 |

## Planning Routing Rules（規劃路由規則）

`planning-spec` 應成為第一個明確分類 required context packs 的 skill。

建議在 spec header 增加：

```markdown
Required Context Packs:
- planning-spec
- frontend-ui
- visual-review
```

分類規則：

| 工作訊號 | 加入 context packs |
| --- | --- |
| Touches `frontend/`、React state、CSS、layout、navigation、modal、card/list/table UI | `frontend-ui` |
| Claims prototype parity、changes screenshot baseline、uses Playwright/Webwright，或 changes visual state | `visual-review` |
| Touches backend domain/application/adapter/API/config | `backend-feature` |
| Adds/changes persistence model、migration、repository、seed data、transaction behavior | `database-change` |
| Changes workflow recipe、skill routing、agent-facing API、learning loop、task state machine | `agent-workflow` |
| Changes verification scripts、QA strategy、release gate、acceptance evidence | `release-quality` |
| Investigates repeated failure 或 fixes 後 error unchanged | `debug-root-cause` |
| Changes `.agents/skills/*` 或 skill discovery/trigger behavior | `skill-development` |

## Prompt Management Rule（提示詞管理規則）

Prompts 是 project memory，不是 skill internals，除非該 prompt 本身屬於 portable reusable skill。

建議位置：

```text
docs/grimo/prompts/
  webwright/
    frontend-ui.md
  reviewer/
    frontend.md
    backend.md
```

規則：

- Prompt file 必須引用它檢查的 product/design/testing rule。
- Context pack 只 reference prompt files，不 inline 長 prompt。
- 當 prompt 抓到真實缺陷時，要把 durable rule promote 到 `standards/*` 或相關 spec；若影響 release correctness，還要加 deterministic coverage。
- Prompt changes 會改變 agent behavior，因此應像 code 一樣 review。

## Standards / Playbooks / Prompts 分工

用這個分工避免所有東西塞進同一份文件：

| Folder | 意義 | 範例 |
| --- | --- | --- |
| `standards/` | 穩定 rules 與 invariants | "Task board initial entry must not preselect a task." |
| `playbooks/` | 某類工作的執行步驟 | "For frontend layout change: compare prototype, update visual test, run Webwright." |
| `prompts/` | 可重複使用的 agent/tool prompt text | Webwright task-board review prompt |
| `design/` | Design-language assets | `tokens.json` |
| `ui/prototype/` | Design source export | OpenDesign handoff files |
| `.agents/skills/` | Portable/repo-scoped workflow engines | `planning-spec`、`verifying-quality` |

## Required Skill Updates（必要 Skill 更新）

這些只是設計要求；本文件尚未實作它們。

1. `planning-spec`
   - 讀完 PRD/architecture 後，增加 "Context Pack Classification" phase。
   - 每個 spec 都寫入 `Required Context Packs`。
   - UI specs 必須要求 low-fidelity UI sketch，並加上 `frontend-ui` 與 `visual-review`。

2. `planning-tasks`
   - 從 spec 讀 `Required Context Packs`。
   - 把相關 pack names 複製到每個 task file。
   - 如果 spec 明顯碰 frontend/backend/database 卻沒有對應 pack，拒絕 task planning。

3. `implementing-task`
   - RED 前先讀 task 的 context packs。
   - 在 task result 記錄使用過哪些 packs。

4. `verifying-quality`
   - AC verification classification 前先讀 spec context packs。
   - 用 `visual-review` 要求 prototype parity tasks 必須有 Webwright evidence。
   - 用 `database-change` 要求 persistence specs 必須有 migration/integration evidence。

5. `shipping-release`
   - 用 context packs 判斷哪些 docs 可能需要 sync。
   - 如果有 `frontend-ui` 或 `visual-review`，檢查 standards/prompts/tokens 是否需要更新並納入 release commit。

6. `handover` / `takeover`
   - Handover note 保存 active context packs。
   - Takeover 繼續工作前必須恢復列出的 packs。

## Immediate Follow-Up Plan（立即後續計畫）

1. 建立 `docs/grimo/context-packs/` 與 initial packs：
   - `frontend-ui.md`
   - `visual-review.md`
   - `backend-feature.md`
   - `database-change.md`
   - `release-quality.md`
   - `skill-development.md`
2. 把目前 frontend UI workflow details 從 `docs/grimo/design/ui-ux-workflow.md` 移到 `standards/frontend-ui.md` 與 `playbooks/frontend-change.md`。
3. 把 Webwright prompts 從 `docs/grimo/design/webwright-prompts.md` 移到 `docs/grimo/prompts/webwright/frontend-ui.md`。
4. `docs/grimo/design/tokens.json` 保留在 `design/`。
5. 將 `development-standards.md` 改成 routing index，避免變成單一巨大 standards file。
6. 等 docs layout 穩定後，再更新 workflow skills。
