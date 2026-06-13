# Grimo 前端設計文件索引

這份索引讓設計、規格和實作能從同一個入口讀起。`CLAUDE.md` / `AGENTS.md` 只放短指引；完整前端設計脈絡放在這裡，避免 agent 入口檔膨脹。

## 讀取順序

| 順序 | 文件 | 什麼時候讀 | 它負責什麼 |
| --- | --- | --- | --- |
| 1 | `docs/grimo/PRD.md` | 所有產品、規劃、前端變更前 | 產品目的、Critical Path、Project / Task / Workflow 語義。 |
| 2 | `docs/grimo/design/frontend-design-context.md` | 改 UI、UX、文案、狀態、layout 前 | 目前有效的前端決策、頁面語義、視覺驗證紀錄。 |
| 3 | `docs/grimo/design/screen-flow-contract.md` | 改 onboarding、empty/error/success、navigation、CTA 前 | Flow Header、State Matrix、Flow Steps、wireflow、驗證對應。 |
| 4 | Active spec，例如 `docs/grimo/specs/2026-06-07-S011-project-setup-hero-main-content-area.md` | 改特定需求前 | 該 spec 的研究、BDD contract、檔案規劃和驗收證據。 |
| 5 | `docs/grimo/design/ui-ux-workflow.md` | 要判斷前端工作流程或設計證據要放哪裡時 | 設計來源層級、token / screenshot / prompt / evidence 的職責。 |
| 6 | `docs/grimo/design/human-centered-agent-ui-principles.md` | 改 Chat、Task、agent execution、onboarding、error/recovery UX 前 | Norman / Swiss cheese / AI agent UX 轉成 Grimo 的可探索性、控制感、防錯和 evidence-first 原則。 |
| 7 | `docs/grimo/design/tokens.json` | 改 spacing、layout 尺寸、color、component token 前 | 目前 token key 和設計語言值。 |
| 8 | `docs/grimo/design/webwright-prompts.md` | 需要 agent-assisted visual review 時 | 可重複的 Webwright 視覺審查 prompt。 |

## S011 快速入口

S011 的前端設計讀取順序固定為：

1. `docs/grimo/PRD.md`
2. `docs/grimo/design/frontend-design-context.md`
3. `docs/grimo/design/screen-flow-contract.md`
4. `docs/grimo/specs/2026-06-07-S011-project-setup-hero-main-content-area.md`
5. `docs/grimo/design/tokens.json`

S011 的 current UI 名詞是 `App Header`、`Side Navigation`、`Main Content Area`、`Project Selection Gate`、`Project Setup Hero`、`Task Details Pane`。舊 selector 只允許出現在 deprecated mapping 或 historical evidence，不可作為新 acceptance selector。

## 文件更新規則

- 改 page flow：先更新 active spec 的 Screen Flow Contract，再改 wireflow 或 code。
- 改 selector / token 名稱：同步更新 active spec、`frontend-design-context.md`、`tokens.json` 和 Playwright assertions。
- 改視覺基準：先看 actual screenshot，再更新 snapshot，最後把命令和結果記回 `frontend-design-context.md`。
- 改 review prompt：只把可重複檢查的規則放進 `webwright-prompts.md`，不要把一次性意見寫成永久規則。
