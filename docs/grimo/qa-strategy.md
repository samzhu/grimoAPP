# Grimo QA Strategy

## Scope

This QA strategy defines the release verification gate for the current Grimo workspace. Frontend UI work is high-risk because small CSS changes can break prototype parity while still passing TypeScript. Full-stack work adds a second risk: the frontend can look correct while `/api` wiring or backend persistence is unverified.

## Verification Command Registry

| ID | Command | Severity | Environment | Evidence |
| --- | --- | --- | --- | --- |
| V1 | `scripts/verify-release.sh` | CRITICAL | zsh, Node/npm, Java 25 toolchain | `temp/verify-release.log` |
| V2 | `./gradlew test` in `backend/` | CRITICAL for backend/API changes | Java 25 toolchain, Gradle wrapper | Gradle test output |
| V3 | `npm run build` in `frontend/` | CRITICAL | Node/npm, installed frontend deps | Vite/TypeScript output |
| V4 | `npm run test:visual` in `frontend/` | CRITICAL | Playwright Chromium installed | screenshot baseline comparison and Playwright report |
| V5 | `scripts/run-webwright-visual-qa.sh ...` | CRITICAL when prototype parity is claimed | `.venv-webwright`, Playwright Chromium, Webwright backend credentials if using model configs | final script, logs, screenshots, self-reflection/manual review |
| V6 | `npm run test:fullstack` in `frontend/` | CRITICAL when frontend calls `/api` | Node/npm, Java 25, ports 5173 and 8080 available | Playwright trace/report plus backend test log excerpt |

## BDD Contract References

開發者要先從 BDD 合約找「應該驗證什麼」，再選擇要跑哪個測試命令。不要只看 `scripts/verify-release.sh` 有沒有綠燈，因為 release gate 只能證明某些命令通過，不能直接說明每個 acceptance criteria 是否都有測到。

| Purpose | Path | What to check |
| --- | --- | --- |
| 全專案 BDD 寫法與測試綁定規則 | `docs/grimo/bdd-contract.md` | Scenario、Given/When/Then、`@ac`、`@layer`、`@api`、`@state` 的意思，以及 backend/frontend/full-stack 要如何映射到測試。 |
| 單一 spec 的產品行為合約 | `docs/grimo/specs/YYYY-MM-DD-S<NNN>-<slug>.md` 的 `## 3. BDD Contract` | 使用者要得到什麼結果、每個 scenario 對應哪個 AC、哪些 API 或 UI 狀態必須被驗證。 |
| 單一 spec 的測試證據入口 | 同一份 spec 的 Verification Bindings / Test Results | 每個 `@ac` 綁到哪個測試檔與 command，哪些已 verified，哪些還只是 planned。 |
| 後端 BDD-style 測試 | `backend/src/test/java/**` | JUnit 5 + MockMvc/WebTestClient/API request 測試；例如 Project API 行為目前放在 `backend/src/test/java/io/github/samzhu/grimo/project/ProjectApiTests.java`。 |
| 前端與整合 BDD-style 測試 | `frontend/e2e/*.spec.ts` | Playwright 驗收流程；長流程用 `test.step()` 保留 Given/When/Then 可讀性。 |

後端目前採用 BDD-style JUnit，不預設導入 Cucumber。也就是說，BDD 的共同語言寫在 spec 和 `docs/grimo/bdd-contract.md`，後端實作時用 `@Test`、`@DisplayName`、fixture、API assertion 來落地：

- `Scenario` 對應一個清楚的 test method 或 nested test。
- `@ac` 要保留在 `@DisplayName("AC-Sxxx-y: ...")`，讓測試報告可以回查 spec。
- `Given` 是 fixture、temporary database state、existing records 或 mock boundary。
- `When` 是 MockMvc/WebTestClient/API request。
- `Then` 是 HTTP status、JSON response、database state 或 domain event assertion。
- 只有測試很長或 Given/When/Then 不直覺時，才在 test code 加 `// Given`、`// When`、`// Then` 註解。

不要為了「看起來像 BDD」而額外加入 Cucumber。只有當 `.feature` 檔變成產品、QA、工程共同維護的主要協作文件時，才重新評估是否需要 Cucumber；在此之前，Markdown BDD contract + JUnit/Playwright binding 是本專案的預設方案。

## Verification Gates

`$verifying-quality` 要先追 BDD 合約，再看命令結果。判定 PASS 前，至少要確認本次 spec 的每個 `@ac` 都能回到一個 scenario、一個 Verification Binding，以及一個可重跑的 command。每個 `@ac` scenario 必須是 `VERIFIED`、`MANUAL-READY`，或明確記錄為 testing infrastructure gap。

如果 spec 會改 API、DTO、DB row、event payload、command output、UI form data 或 file format，`$verifying-quality` 要確認 BDD Contract 內有 realistic input/output 範例和每個欄位的型別/格式、規則、來源、設計理由、BDD assertion。缺欄位 contract 時，不應把測試綠燈當成完整驗收，因為測試可能只是在比對偶然 shape 或硬編碼範例。

`$verifying-quality` 還要做 generality gate：確認 production code 真的實作行為，而不是只針對已知測資或 BDD 範例回固定答案。若一個 AC 只用單一範例驗證，QA 必須至少要求一個非 fixture 的 probe、property/metamorphic relation、differential check，或 persisted read-back。若 production code 出現 hardcoded fixture branch、canned response、跳過 persistence、client-only 假成功，或只支援測試裡的那個值，判定 `GENERALITY-FAIL` 並擋 release。

### Pending QA Research: Mutation Testing

Mutation testing 是 generality gate 的候選強化手段，但目前尚未接進 Grimo release gate。下一步要先透過 spec 設計，不直接把工具塞進 `scripts/verify-release.sh`。

待研究問題：

- 後端 Java/Spring Boot 是否導入 PIT，並決定 Gradle task、JUnit 5 相容性、執行時間、mutation score 門檻和哪些 package 先納入。
- 前端 TypeScript/React 是否導入 Stryker，並決定是否只針對 pure logic、API client、reducer 或核心 domain helper，不把高成本 UI snapshot 全部納入。
- Mutation testing 要如何和 existing generality probe 搭配：mutation score 用來證明測試能抓錯，generality probe 用來證明 production code 不是硬編碼範例。
- 哪些 spec 類型必須要求 mutation testing：核心 domain logic、validation rule、state transition、parser/normalizer、calculation-like behavior；layout-only 或純 wiring task 可先豁免並記錄原因。
- `scripts/verify-release.sh` 是否要把 mutation testing 設為 CRITICAL、optional extended gate，或只在 `$verifying-quality` 對高風險 spec 手動觸發。

這項研究要由 backlog spec `S008 QA generality and mutation testing strategy` 設計，完成後再更新本文件的 Verification Command Registry 和 release gate。

For backend/API changes, `scripts/verify-release.sh` must include backend tests before the spec can ship. Controller tests should prove HTTP status and response body shape. Repository or service tests should prove persistence and duplicate/validation behavior.

For frontend-to-backend changes, `scripts/verify-release.sh` must include `npm run test:fullstack` in `frontend/`. The full-stack config starts Spring Boot with temporary SQLite state and Vite with the `/api` proxy, then exercises the user flow from a browser.

For non-layout frontend changes, `scripts/verify-release.sh` and `npm run build` are sufficient automated gates only when the feature does not call backend APIs.

For layout, responsive, or prototype parity changes, automated build evidence is not enough. Verification must include deterministic browser screenshots at the relevant viewport set. Webwright should then inspect the same user-visible workflow as an agent-assisted QA reviewer and preserve reusable artifacts.

Required viewport set:

- Desktop layout: `1366x768`, `1440x900`
- Responsive layout: add `390x844`, `820x1180`
- Prototype parity sweep: include `1920x1080` when the prototype has wide desktop behavior

## Webwright Role

Webwright is the agent-assisted visual QA layer. It should:

- open the local Vite app in a fresh browser context
- inspect the target surface against `docs/grimo/ui/prototype/index.html`
- capture screenshots only at critical points
- write a reusable script and logs under an ignored evidence directory
- rerun the final script from a fresh folder before the check is accepted

Webwright does not replace deterministic assertions. A passing Webwright review without Playwright screenshot evidence is `MANUAL-READY` at best, not `VERIFIED`.

## Installation

Playwright is installed as a frontend dev dependency pinned to `@playwright/test@1.60.0`.

Webwright is installed into a repo-local virtualenv so it does not pollute the user environment:

```bash
scripts/setup-webwright.sh
```

The current pinned Webwright source is `microsoft/Webwright@0be73c18ce31a0920c979b8f2aab12d11ef26b9c`.

## Test Strategy

| Change type | Required checks | Release effect |
| --- | --- | --- |
| Backend API or persistence change | `scripts/verify-release.sh` including `./gradlew test` in `backend/` | Blocks on compile, API test, or persistence test failure |
| TypeScript-only frontend change | `scripts/verify-release.sh` | Blocks on build or visual regression failure |
| Layout/CSS change | `scripts/verify-release.sh` plus review Playwright screenshot diffs | Blocks on screenshot diff unless baseline is intentionally updated |
| Responsive layout change | Add/update viewport coverage for `390x844`, `820x1180` | Blocks when mobile/tablet screenshots regress |
| Prototype parity claim | Run `scripts/run-webwright-visual-qa.sh` against the local app and preserve artifacts in the spec evidence | Blocks if no Webwright/manual visual evidence exists |
| New UI interaction | Add Playwright interaction path before baseline screenshot | Blocks if interaction cannot be executed from the real browser |
| Frontend calls backend API | Backend API tests plus Playwright full-stack interaction using real `/api` wiring | Blocks if the UI works only with mocked or fixture data |

## Full-Stack API Gate

When a spec introduces or changes frontend-to-backend behavior:

1. Backend must expose the behavior through a real HTTP endpoint under `/api`.
2. Frontend must call a relative `/api/...` URL so Vite can proxy it in development.
3. Playwright must exercise the user action from the browser and assert the observable UI result.
4. At least one verification path must prove the backend received and persisted the data, either by API response, follow-up `GET`, or isolated test database assertion.
5. Tests must use temporary state, not the user's real local Grimo database.

## Current Coverage

The deterministic Playwright suite currently covers:

- task board at `1366x768`, `1440x900`, `390x844`, `820x1180`
- selected task detail drawer
- create task dialog
- full page task detail

Webwright is installed and callable through `scripts/run-webwright-visual-qa.sh`, but task-specific Webwright runs remain opt-in because model/backend credentials are environment dependent.
