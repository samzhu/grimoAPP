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

### 2.3 做法比較

| 做法 | 採用 | 理由 |
|----------|--------|-----------|
| A: ... | yes/no | ... |
| B: ... | yes/no | ... |

### 2.4 Task 邊界提示

| Task 候選 | Class / file | 來源 | 正向情境 | 反向情境 | POC |
|-----------|--------------|------|----------|----------|-----|
| T01 | `[ClassName]` | [URL / local file] | [should report/change] | [should not report/change] | required / not required |

## 3. 驗收條件（SBE）

驗證命令：

執行：`[project standard verification command]`
通過條件：所有帶 `<spec-id>` AC id 的測試都是綠燈。

| AC | 優先級 | 驗證方式 | 標題 |
|----|----------|--------|-------|
| AC-1 | 必做 | Test | [short title] |
| AC-2 | 建議 | Demo | ... |

**AC-1: [title]**
- Given（前提）[明確資料狀態、輸入範圍、fixture 或使用者操作]
- When（動作）[觸發的 API、event、command、scanner 或 UI 操作]
- Then（結果）[外部看得到的結果：HTTP status、response body、DB row、UI 字串、finding 欄位、command output]
- And（而且）[必要的保護條件或 negative case]

**AC-2: ...**

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
