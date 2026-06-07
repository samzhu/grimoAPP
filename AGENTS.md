# AGENTS.md

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## Language

- 對 user 的所有說明、工作進度更新、final response 都使用繁體中文。
- 技術名詞、class name、package name、command、API field 保留原文。
- 只有在 user 明確要求英文或引用原文內容時，才切換成其他語言。
- **結果優先白話（Outcome-first Plain Language）**：除語法、API field、class name、package name、command、測試名稱、專有名詞外，文件和對 user 說明都優先使用繁體中文白話。描述功能時，先說使用者或產品得到什麼結果，再補技術人員需要的實作線索；不要讓讀者從「A 程式呼叫 B，B 寫入 C」自己推回功能目的。
- 寫法順序建議：先說「因為使用者要達成 X，所以功能會做到 Y」，再補「技術上由 A API / B table / C component 驗證」。例如：`建立專案前，使用者可以先確認這個工作流會帶哪些角色，不需要自己手動新增角色。技術上，建立專案頁面會先讀取工作流清單（GET /api/workflow-recipes），並從 workflow 的 roles[] 顯示角色。`
- 問設計或 task 清晰度時，先把抽象 contract 翻成畫面上會不會多顯示某個東西、按鈕能不能按、API response 會多哪個欄位，再問決策。user 說「白話一點」時，不要只換同義詞；要改用使用者看得到的畫面重講。例如不要先問「`steps[]` 是否保留 `optional` 欄位」，要先說「建立專案頁的流程旁邊要不要標這一步可不可以跳過」。

## Session Startup

- 開始任何產品、規劃、實作、驗證、出貨工作前，先讀 `docs/grimo/PRD.md`。
- 若 `docs/grimo/PRD.md` 不存在，而且任務需要產品脈絡，回報「No PRD found. Run `$defining-product` first.」並走 `$defining-product` 產生；不要手動猜產品內容。
- `docs/grimo/*` 是 workflow skills 逐步產生的工作檔案。缺檔通常代表對應 skill 尚未執行，不代表該檔案不重要。

## Core Rules

- **Feature First, Security Later**: MVP 階段以功能開發為主，Spring Security 設為 permit all。認證、授權、CSRF 等安全性在功能完成後再補；不要在開發階段加入擋住功能的安全設定。
- **Clean Code, Short Names**: 程式碼盡量符合 Clean Code：小而清楚的 function、單一責任、少副作用、少重複、少巢狀。命名要能說出領域意義，但不要冗長；優先用專案 glossary / spec 已定義的短詞。若名字需要用一句話才能看懂，先整理概念或拆小單位，而不是把整句話塞進 class / method / variable name。
- **Purpose-First Comments**: 註解和說明優先寫「為什麼需要這個檢查 / 規則 / 設計」，不要翻譯程式正在做什麼。好例子：`避免同一個 workspace 被建立成兩個 Project，後續 Task 才不會寫到錯的 repo。` 壞例子：`檢查 folderPath 是否重複。` 程式本身已經清楚的步驟不要加註解；只有業務意圖、設計取捨、非直覺限制、或未來容易誤改的地方才補白話說明。
- **First Principles Thinking**: 找根因，不只修表面症狀。
- **Spec-Linked Rationale**: 設計決策、framework 機制、trade-off、research alternatives 寫進 spec / ADR。source code 註解只留「spec ID + 簡短敘述 + override 提示」（最多 3 行）。業務邏輯 invariant 的 inline comment 與 log message 仍要在 source 簡明寫。
- **Documented POC Code**: POC、實驗測試、暫時驗證程式碼也必須交代用途。每個 POC 檔案或測試 class 至少要有簡短說明：驗證什麼假設、依據哪個 PRD / ADR / reference、如何執行、通過後對產品決策代表什麼。避免只留下「會過的測試」卻看不出為何存在。
- **Web-Verify First**: 牽涉外部框架、SDK、版本、import path、文件行為時，先查官方文件並引用來源；不要靠記憶猜。
- **Log-Driven Debugging**: log 不足以判斷根因時，先補 log 並重跑，再規劃修法。
- **No Deprecated APIs**: 依 `docs/grimo/architecture.md` 的版本與 import path 實作；不要使用 deprecated API。
- **Ecosystem-Managed Versions**: 新增 dependency 前先查 build system 管理的版本；不要 pin 一個降版版本。
- **Scope-Check Before Applying**: 套用 security / compliance finding 前，先搜尋 distinguishing identifier，確認目前 code 真的在 finding 範圍內。
- **Clean Experiments**: debug 前建立可還原點；失敗實驗先還原再試下一個；確認修法後，逐行檢查 diff 都能對應到實際修正。
- **Finish-Current-First**: 先完成手上的 spec / task（test + ship + commit）再開新需求。user mid-flight 提新需求時，先 acknowledge，再收尾當前工作。
- **Plain-Language Explanations**: 對 user 說明時，第一句先指到實體：file path:line、command、DB row、API response shape、或 UI 字串；接著用結果優先白話說明這個實體讓使用者完成什麼事。比較選項時，每個選項都說明：(a) 改哪個 file / command，(b) 使用者會看到什麼實際行為或產品結果，(c) 技術成本或風險。user 說「聽不懂」「白話一點」「重講」時，用新的實體 lead 重寫整段，先講產品結果，再講技術證據。

## Workflow Routing

`$defining-product` -> `$planning-project` -> `$planning-spec SNNN` -> `$planning-tasks SNNN` <-> `$implementing-task` -> `$verifying-quality SNNN` -> `$shipping-release SNNN`

| User intent / repo state | Use |
| --- | --- |
| 新產品、需求探索、PRD 尚未存在 | `$defining-product` |
| PRD 已核定，要產生 architecture、standards、QA strategy、roadmap | `$planning-project` |
| 要設計單一 spec 或修正 spec 設計 | `$planning-spec SNNN` |
| spec sections 1-5 已存在，要切 BDD task 或推進 task loop | `$planning-tasks SNNN` |
| task loop 指派單一 pending task | `$implementing-task` |
| 所有 task PASS 後，要獨立驗證品質 | `$verifying-quality SNNN` |
| QA PASS / local release PASS 後，要整理 docs、archive spec、更新 changelog / roadmap、commit / tag | `$shipping-release SNNN` |

- `local implementation PASS` / task PASS 不等於可 release；下一步是 `$verifying-quality SNNN`。
- 只有 QA PASS / local release PASS / ready-to-ship 的 spec 尚未進 `docs/grimo/specs/archive/`、未清 `docs/grimo/tasks/`、未更新 `docs/grimo/CHANGELOG.md` 與 `spec-roadmap.md`、或缺 tag 時，才用 `$shipping-release SNNN` 收尾。
- `/planning-tasks` 是 hub。所有 task PASS 後，由它安排 `/verifying-quality` 做獨立檢查。

## Workflow Artifacts

這些檔案由 workflow skills 產生與維護；缺檔時依 `Workflow Routing` 進入對應 skill，不要手動猜內容。

| Path | What |
| --- | --- |
| `docs/grimo/PRD.md` | Product vision, **Critical Path**, MVP scope (Critical / Supporting / Backlog / Out), decision log |
| `docs/grimo/architecture.md` | Tech decisions, framework dependency table, module map, data flows |
| `docs/grimo/development-standards.md` | Code conventions, package layout, testing rules (§7), forbidden patterns |
| `docs/grimo/qa-strategy.md` | Test pipeline, verification commands (ecosystem-native preferred) |
| `docs/grimo/glossary.md` | Bilingual (zh-TW + English) domain terms |
| `docs/grimo/design/README.md` | Frontend design docs index — UI/UX work starts here after PRD |
| `docs/grimo/design/frontend-design-context.md` | Current frontend UI/UX decisions, page semantics, visual evidence |
| `docs/grimo/design/screen-flow-contract.md` | Required flow contract for onboarding, empty/error/success state, navigation, CTA changes |
| `docs/grimo/design/tokens.json` | Current design token names and values |
| `docs/grimo/design/webwright-prompts.md` | Reusable Webwright visual review prompts |
| `docs/grimo/specs/spec-roadmap.md` | Live roadmap — all specs, milestones, Backlog |
| `docs/grimo/specs/YYYY-MM-DD-S<NNN>-<slug>.md` | In-flight spec (§1-5 design, §6 task plan, §7 results) |
| `docs/grimo/specs/archive/` | Shipped specs — permanent record |
| `docs/grimo/tasks/` | **Temporary** BDD task files; only exist between `/planning-tasks` and Phase 3; deleted on ship |
| `docs/grimo/CHANGELOG.md` | What shipped + when (appended by `/shipping-release`) |
| `docs/grimo/adr/ADR-NNN-<slug>.md` | In-development decisions that extend or contradict PRD |

## Reference Docs

- Spring AI 2.0.0-M7: https://docs.spring.io/spring-ai/reference/2.0/index.html
- Pollack AI Lab: https://lab.pollack.ai/
