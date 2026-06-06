# Spec File Template

File: `docs/grimo/specs/YYYY-MM-DD-<spec-id>-<topic>.md`

```markdown
# <spec-id>: [Topic]

> 規格：<spec-id> | 大小：XS/S/M(N) | 狀態：⏳ Design
> 日期：YYYY-MM-DD
> 對應：PRD §X.Y / ADR-NNN / spec-roadmap row <spec-id>

---

## 1. 目標

[用一段白話說明這個 spec 要讓使用者、API、DB、UI 或背景流程多什麼能力。第一句要讓非專家也看得懂。]

## 2. 研究與設計

### 2.1 查到的事實

| 來源 | 查到什麼 | 對設計的影響 |
|------|----------|--------------|
| [source URL / local file] | [fact] | [design consequence] |

### 2.2 架構設計

[說明共同介面、資料流、module 邊界。若很多檢查項目輸入輸出相同，先設計共用 interface，讓 runtime 用同一介面操作全部實作。]

### 2.x Screen Flow Contract

[若本 spec 改 frontend page flow、navigation、onboarding、empty state、error state、success destination、CTA hierarchy 或跨頁跳轉，必填本段並套用 `docs/grimo/design/screen-flow-contract.md`。若不適用，寫 `N/A — <reason>`。]

Flow Header:

| 欄位 | 內容 |
| --- | --- |
| Flow name |  |
| Persona |  |
| User goal |  |
| Entry point |  |
| Success endpoint |  |
| Out of scope |  |

State Matrix:

| State | Data condition | 使用者看到什麼 | Primary action | Forbidden behavior | Evidence |
| --- | --- | --- | --- | --- | --- |
| loading |  |  |  |  |  |
| empty |  |  |  |  |  |
| ready |  |  |  |  |  |
| error |  |  |  |  |  |
| success |  |  |  |  |  |

Flow Steps:

| Step | Outcome | Screen / surface | User action | System response | Next state | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 1 |  |  |  |  |  |  |

Low-fidelity wireflow:

```text
[ASCII wireflow or Mermaid flow]
```

CTA/navigation rules:

- Primary action:
- Secondary actions:
- Cancel/back/retry:
- Success destination:
- No-context behavior:
- Duplicate primary CTA check:

Verification Mapping:

| Behavior | Required evidence |
| --- | --- |
|  |  |

### 2.3 做法比較

| 做法 | 採用 | 理由 |
|----------|--------|-----------|
| A: ... | yes/no | ... |
| B: ... | yes/no | ... |

### 2.4 Task 邊界提示

| Task 候選 | Class / file | 來源 | 正向情境 | 反向情境 | POC |
|-----------|--------------|------|----------|----------|-----|
| T01 | `[ClassName]` | [URL / local file] | [should report/change] | [should not report/change] | required / not required |

## 3. BDD Contract

> 必填檢查：套用 `references/spec-required-data.md`。任何 API、DTO、DB row、event、command、UI form 或 file format 變更，都必須有 realistic example、欄位設計表、系統欄位限制和 BDD read-back / assertion。

驗證命令：

執行：`[project standard verification command]`
通過條件：所有帶 `<spec-id>` AC id 的 scenario 都有對應 test evidence，且標記為 `@state:verified` 前必須已通過驗證命令。

BDD 呈現原則：每個 AC 先寫「使用者結果」，再寫「Contract」，最後寫「Scenario + 驗證綁定」。Scenario 用 Gherkin 語法，但文字要用結果優先白話；技術名詞、API path、DTO、field name、command 保留原文。

AC 覆蓋矩陣：

| AC | 使用者結果 | Contract / 可觀察輸出 | Layer | State |
|----|------------|------------------------|-------|-------|
| AC-<spec-id>-1 | [使用者完成什麼或避免什麼問題] | [`GET /api/...` response / DB row / UI text / command output] | backend, api | proposed |
| AC-<spec-id>-2 | ... | ... | frontend, fullstack | proposed |

Feature: [使用者能理解的功能名稱]

### Rule: [一條 business rule，只放同一條規則底下的 scenarios]

使用者結果：
[先用 1-3 句說明使用者會看到什麼、能完成什麼、或錯誤時不會發生什麼壞結果。]

Contract：
[如果是 API/DTO，寫清楚 HTTP method + path、status、request/response shape。collection API 必須依 `docs/grimo/architecture.md` 的 API response standard 呈現：不分頁清單用 `CollectionResponse<T>` 的 `content[]`，分頁清單用 `PageResponse<T>` 的 `content[]` + `page`。至少列出兩筆 `content[]` item，避免誤寫成單一物件。若每個 item 有 child array，例如 `roles[]`、`items[]`、`errors[]`，要呈現 child array 的欄位；也要呈現空陣列或負向案例。]

```json
{
  "content": [
    {
      "id": "example-a",
      "name": "Example A",
      "children": [
        {
          "id": "child-1",
          "name": "Child 1",
          "description": "使用者看得懂的用途"
        }
      ]
    },
    {
      "id": "example-b",
      "name": "Example B",
      "children": []
    }
  ]
}
```

Field contract:

| 欄位 | 型別/格式 | 規則 | 來源 | 設計理由 | BDD 要驗什麼 |
| --- | --- | --- | --- | --- | --- |
| `id` | string | system-owned | backend | 讓 resource 可被 URL/log/test 追蹤 | client 不能覆寫；response 有值 |

```gherkin
@spec:<spec-id>
@ac:AC-<spec-id>-1
@layer:backend,api
@api:GET /api/...
@state:proposed
Scenario: [先說使用者結果，不只說技術行為]
  Given [已知資料狀態、fixture 或使用者所在畫面]
  When [使用者操作、API call、command 或 event]
  Then [使用者看得到的結果，或 API response / DB row / command output]
  And [必要的保護條件、negative case 或不該發生的副作用]
  # 技術證據：[精準寫出 status/body field/DB row/test assertion]
```

驗證綁定（Verification Bindings）：

- backend: `[TestClassName]`
- frontend/fullstack: `[spec file]`
- command: `[verification command]`

### Rule: [下一條 business rule]

[重複使用者結果 → Contract → Scenario → 驗證綁定。]

### 非功能需求檢查

| 分類 | 對應驗收 | 說明 |
|---|---|---|
| Performance | AC-N | — |
| Security | — | N/A — <reason> |
| Reliability | AC-N | — |
| Usability | — | N/A — <reason> |
| Maintainability | AC-N | — |

## 4. 介面與 API 設計

[Code signatures、struct definitions、key patterns。用白話說每個欄位從哪裡來，以及 runtime 怎麼呼叫這些介面。]

### Storage

如果本 spec 新增或修改 DB table，必須填寫本段；沒有 DB schema 變更時寫 `N/A — <reason>`。

```sql
-- table: <table_name>
-- 用途: <使用者或產品因這張表得到什麼能力>
-- owner: <parent table / aggregate；如何驗證 ownership>
-- 不存: <容易誤放但應該屬於其他表或 projection 的資料>
CREATE TABLE IF NOT EXISTS <table_name> (
    id TEXT PRIMARY KEY
);
```

Schema field rationale:

| Table | Field | 型別/格式 | 規則 | 設計理由 | BDD 要驗什麼 |
| --- | --- | --- | --- | --- | --- |
| `<table_name>` | `id` | TEXT | primary key, system-owned | row identity | persisted read-back uses returned id |

Storage sample data:

Context / parent rows:

| table | id | 說明 |
| --- | --- | --- |
| `<parent_table>` | `<parent-id>` | <why this parent exists> |

`<table_name>`:

| `id` | `<parent_id>` | `<field>` |
| --- | --- | --- |
| `<row-a>` | `<parent-id>` | `<realistic value>` |

BDD read-back expectation:

- `<API/query/test>` must read the sample rows back through the public or intended read path.
- The sample must include enough varied values to fail a hardcoded implementation.

## 5. 檔案規劃

| 檔案 | 動作 | 說明 |
|------|--------|-------------|
| path/to/file | new/modify | ... |

---

<!-- Sections 6-7 added by /planning-tasks after implementation -->

## 6. Task 規劃

POC：required / not required — [rationale]

| # | Task | AC | 狀態 |
|---|------|----|--------|
| T01 | ... | AC-1 | pending（待做） |

執行順序：T01 → T02 → ...

### POC Findings
<!-- Added after POC validation, if POC was required -->
- Verified packages: [package@version]
- Correct API patterns: [code snippets]
- Gotchas: [issues discovered]

## 7. 實作結果

### 驗證結果
- Tests: pass/fail
- Lint: pass/fail
- Format: pass/fail

### 實作發現
[What was learned during implementation — API gotchas, patterns, etc.]

### Correct Usage Patterns
[Code snippets showing the RIGHT way to use the API, based on actual
implementation experience. This is the most valuable part — future
specs reference this.]

### AC 結果

| AC | 結果 | 備註 |
|----|--------|-------|
| AC-1 | pass | ... |
| AC-2 | pass | ... |
```
