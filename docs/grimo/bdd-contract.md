# Grimo BDD Contract

## Purpose

BDD Contract 是 Grimo spec 中描述行為的 canonical source。它定義「系統應該如何表現」，但不綁定 Cucumber、JUnit、Playwright 或任何單一 runner。

換句話說：

- BDD Contract 是跨前後端共用的行為規範。
- Backend、frontend、full-stack 測試各自用所在技術棧實作同一份 contract。
- Cucumber/Gherkin 是可選執行工具，不是 Grimo 的預設必需品。

## Source Model

每個 spec 的 BDD section 應使用下列層級：

```text
Feature
  Rule
    Scenario
      Given
      When
      Then
      And / But
    Verification Bindings
```

`Feature` 對應 spec 的功能範圍；`Rule` 對應一條 business rule；`Scenario` 是可被自動化或人工驗證的 concrete example。

## Scenario Header

每個 scenario 必須帶 trace metadata：

```md
@spec:S002
@ac:AC-S002-8
@layer:backend,fullstack
@api:POST /api/projects
@state:automated
Scenario: Project creation snapshots workflow roles
```

欄位規則：

| Field | Required | Meaning |
| --- | --- | --- |
| `@spec` | yes | Spec id，例如 `S002`。 |
| `@ac` | yes | Acceptance criterion id。 |
| `@layer` | yes | `backend`, `frontend`, `fullstack`, `manual`, `docs` 的逗號清單。 |
| `@api` | when applicable | REST resource and method，例如 `GET /api/workflow-recipes`。 |
| `@state` | yes | `planned`, `automated`, `manual-ready`, `blocked`, `verified`。 |

## Step Language

Steps 使用 business/domain language，避免把框架細節寫進 contract。

Good:

```gherkin
Given the workflow recipe "web-service-development" has six preconfigured roles
When the client creates a Project with that workflowRecipeId
Then the response contains six workflowRoles
And SQLite contains six project_workflow_roles rows for that Project
```

Avoid:

```gherkin
Given ProjectApiTests creates a MockMvc request builder
When mockMvc.perform(post("/api/projects")) is called
Then jsonPath("$.workflowRoles") has size 6
```

框架細節應出現在 verification binding 的實作測試，不出現在 BDD Contract。

## Verification Bindings

每個 scenario 下方必須列出對應實作位置：

```md
Verification Bindings:

- backend: `backend/src/test/java/io/github/samzhu/grimo/project/ProjectApiTests.java`
- fullstack: `frontend/e2e/project-onboarding.fullstack.spec.ts`
```

Binding 必須能讓 `$verifying-quality` 追到：

- 哪個測試檔驗證這個 scenario。
- 哪個 command 會執行該測試。
- 失敗時要回哪個 task/spec 修。

## Data Contract Examples

每個會改 API、DTO、DB row、event payload、command output、UI form data 或 file format 的 spec，都必須在 BDD Contract 裡放資料合約範例。不要只寫「欄位符合 schema」；要讓開發者看懂每個欄位為什麼存在、誰決定它、測試要怎麼證明它不是硬編碼。

每個資料合約至少包含：

- 一個 realistic request/input 範例。
- 一個 realistic response/output 範例；collection 要至少兩筆 `content[]`，並包含空陣列或反向案例需要的 shape。
- 每個欄位的設計說明表，包含 `欄位`, `型別/格式`, `規則`, `來源`, `設計理由`, `BDD 要驗什麼`。
- 如果 spec 會新增或修改 DB table，Storage contract 必須固定包含：每張 table 的用途註解、ownership / boundary、不可存放的資料、欄位設計表，以及至少一組 realistic sample rows。Sample rows 要呈現 FK 關係和反向/空狀態需要的 shape，不能只列欄位名稱。
- 明確列出禁止由 client 設定的系統欄位，例如 `state`, `source`, `workflowRecipeId`, `id`, `createdAt`。

建議格式：

| 欄位 | 型別/格式 | 規則 | 來源 | 設計理由 | BDD 要驗什麼 |
| --- | --- | --- | --- | --- | --- |
| `title` | string | trim、必填、不可空白 | user input | 使用者在列表中掃描工作標題 | blank title 失敗；成功 response 保留 normalized title |
| `state` | enum string | 系統固定值 | backend rule | 避免 client 直接跳過 workflow | request override 不生效；response 是預期 state |

如果欄位只是為了相容現有 UI 而暫時存在，也要寫清楚，例如「display default，後續 spec 會取代」。如果欄位會影響行為，BDD 必須有正向或反向案例證明它真的影響行為；如果欄位是 display-only，BDD 要避免把它誤寫成 lifecycle rule。

## Framework Mapping

BDD Contract 到實作框架的對應如下：

| BDD Contract | Java / Spring backend | Playwright frontend/full-stack | Documentation |
| --- | --- | --- | --- |
| `Feature` | test class group or package | spec file or `test.describe` | spec title |
| `Rule` | nested class or display-name group | `test.describe()` | BDD subsection |
| `Scenario` | `@Test` + `@DisplayName` | `test(...)` | concrete example |
| `Given` | fixture, temporary DB, existing records | browser state, temp directory, seeded API state | precondition |
| `When` | MockMvc/WebTestClient/API request | click/fill/select/API request | event/action |
| `Then` | status/jsonPath/DB assertion | `expect(...)` UI/API assertion | expected outcome |
| `@ac` | display name or test method comment | test title or annotation | traceability |
| `@api` | MockMvc/WebTestClient endpoint | Vite proxy/API request evidence | REST contract |

## Playwright Convention

Playwright tests may use `test.step()` to make reports match BDD structure:

```ts
test("AC-S002-3/4 creates Project with selected workflow", async ({ page }) => {
  await test.step("Given the user is on Projects view", async () => {
    await page.goto("/");
    await page.getByRole("button", { name: "專案" }).click();
  });

  await test.step('When the user clicks "建立專案"', async () => {
    await page.getByRole("button", { name: "建立專案" }).click();
  });

  await test.step("Then the Project Creation Page shows workflow roles", async () => {
    await expect(page.getByText("Product Manager")).toBeVisible();
  });
});
```

Do not add Cucumber solely for report wording. Use Cucumber only when `.feature` files become a collaboration artifact for non-engineering stakeholders.

## Backend Convention

Backend tests should keep the AC id in `@DisplayName` and assert observable API or persistence behavior:

```java
@Test
@DisplayName("AC-S002-8 creates Project with workflow role settings")
void createsProjectWithWorkflowRoleSettings() throws Exception {
    // Given: workflow recipe exists in the default catalog
    // When: client creates a Project with web-service-development
    // Then: response and SQLite contain workflow role settings
}
```

Comments are optional when the test body is already clear. The important rule is traceability: the AC id and scenario intent must be visible in the test report.

## Required Spec Shape

New specs should include a BDD section like this:

```md
## BDD Contract

Feature: Project Creation

Rule: Project must be created from selected workflow and local path

@spec:S002
@ac:AC-S002-8
@layer:backend,fullstack
@api:POST /api/projects
@state:automated
Scenario: Project creation snapshots workflow roles
Given the workflow recipe "web-service-development" has six preconfigured roles
And the user provided a local Project Workspace
When the client creates a Project with that workflowRecipeId
Then the response contains workflowRecipeId "web-service-development"
And the response contains six workflowRoles
And SQLite contains six project_workflow_roles rows for that Project

Verification Bindings:

- backend: `backend/src/test/java/io/github/samzhu/grimo/project/ProjectApiTests.java`
- fullstack: `frontend/e2e/project-onboarding.fullstack.spec.ts`
```

## Quality Rules

- One scenario should verify one observable behavior or business rule.
- Prefer 3-5 core steps per scenario; split long flows into multiple scenarios.
- Use `Scenario Outline` style tables only when the examples share the same rule and differ only by input/output values.
- Do not duplicate scenario text with only framework wording changed.
- Do not write `Given` steps as user actions; user actions belong in `When`.
- Do not mark `@state:verified` until `$verifying-quality` has run the command and recorded evidence.
- If a scenario cannot be automated yet but can be checked by a human in under 5 minutes, mark `@state:manual-ready` and provide instructions.
- If verification needs new infrastructure, mark `@state:blocked` and create a testing infrastructure spec before shipping.

## References

- Cucumber BDD describes BDD as discovery, formulation, and automation around concrete examples.
- Gherkin provides the `Feature`, `Rule`, `Scenario`, `Given`, `When`, `Then`, `Background`, `Scenario Outline`, and `Examples` vocabulary.
- Playwright provides native `test`, `expect`, and `test.step()` support, so Grimo can keep BDD semantics without forcing Cucumber into frontend tests.
- Spring REST Docs can be introduced later if API tests should generate REST documentation snippets from MockMvc/WebTestClient.
