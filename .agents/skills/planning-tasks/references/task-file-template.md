# Task File Template（Temporary Work Item）

File: `docs/grimo/tasks/YYYY-MM-DD-<spec-id>-<task-id>.md`

這些檔案是暫時 work item。全部 task 通過後，結果會整理回 spec，
task files 會刪除。

```markdown
# <spec-id>-<task-id>: [topic]

## 對應規格
<spec-id>：[spec topic]

## 這個 task 要做什麼
[用 2-5 句白話說明：這個 task 完成後，使用者、API、DB、UI、scanner output 或程式行為會多什麼。]

## 使用者情境（BDD）
Given（前提）[具體輸入、資料狀態、fixture 或使用者操作]
When（動作）[觸發的 API、event、scanner、command 或 UI 操作]
Then（結果）[可驗證輸出：DB row、API response、UI text、finding 欄位、command output]
And（而且）[必要的 negative case 或保護條件]

## 研究來源
- [官方文件 / upstream repo / local code path / prior spec findings]

## 先做 POC
- POC：required / not required — [原因]
- 若 required，建立 `poc/<spec-id>/<task-id-or-code>/`。
- Fixture：
  - `[positive-fixture]`: [應該命中的具體內容] → [預期結果]
  - `[negative-fixture]`: [不應該命中的具體內容] → [不回報 / 不改變]
- POC 跑完必須印出 `[spec-id] [task-id/code] POC PASS`。

## 正式程式怎麼做
- Class / file 名稱：[例如 `SensitiveDataExposure.java`]
- 入口：[controller / event listener / detector / service / UI component]
- 必要行為：
  - [逐點寫清楚要掃哪些輸入、產生哪些輸出、哪些欄位不能漏]
- Finding / response / DB 欄位：
  - `[field]`: [值怎麼來]

## 單元測試 / 整合測試
- `[TestClassName]`
  - `@DisplayName("<AC-id>: [should case]")`
  - `@DisplayName("<AC-id>: [should not case]")`

## 會改哪些檔案
- [file path]

## 驗證方式
執行：`[command that verifies this task]`

## 前置條件
- [task-id PASS 或「無」]

## 狀態
pending（待做）
```
