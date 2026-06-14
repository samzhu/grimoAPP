# SQLite Data Modeling Best Practices

**Purpose:** Give Grimo specs and implementation tasks a shared rulebook for local SQLite schema design.

Grimo 是單機優先軟體，但 SQLite 只是部署型態，不會改變資料表設計的基本原則。使用者要搜尋、排序、驗證、重跑、檢視或回放的資料，必須先用正規化資料表保存；JSON / `[]` 只用在邊界 payload、UI projection、快取或原始外部輸入。

## Design Position

SQLite 是 Grimo local-first MVP 的主要資料庫。它適合單機、低安裝成本、可備份、可診斷的產品路徑；但 Grimo 的本地 database 仍是 product source of truth，不是 AI response dump。

設計原則：

1. Source-of-truth data stays relational.
2. JSON is an edge format, not the default storage model.
3. UI projection can be denormalized if it can be rebuilt.
4. Every parent-child product relationship needs database-level integrity.
5. Every multi-row product action needs a transaction.

## Normalize First

只要資料有自己的生命週期，就拆成 table。

| 資料形狀 | SQLite 設計 |
| --- | --- |
| `Project`、`Task`、`Workflow Run`、`Workflow Step`、`Quality Run`、`Message`、`Attachment` | 獨立 table，有自己的 `id`、owner FK、timestamps 和 lifecycle fields。 |
| `Task -> many workflow steps` | child table，例如 `task_workflow_steps`，不要塞成 `tasks.steps` JSON。 |
| `Step -> many review/rating/fix attempts` | child table，例如 `task_workflow_quality_runs`，讓 attempt 可排序、可唯一約束、可讀回驗證。 |
| Board summary、task card preview、latest score | projection / read model；可快取，但必須能從 source tables 重建。 |
| AI 原始輸出、provider payload、import/export blob | JSON column 或 file artifact；不要把它當成核心查詢模型。 |

Grimo workflow evidence 的預設形狀：

```text
tasks
task_workflow_runs
task_workflow_steps
task_workflow_quality_runs
```

`workflowSummary` 是給 Task board 掃描用的 projection。使用者在 board 看到「目前跑到哪一步、品質分數多少」；真正的 step history、score attempt、review findings 和 fix history 必須存在 workflow evidence tables。

## When JSON Is Acceptable

JSON / `[]` 可以用，但要先回答「這包資料壞掉時，能不能從其他 source of truth 重建？」

| 可以用 JSON 的情境 | 原因 |
| --- | --- |
| 外部 API 原始 response | 保留原始輸入，避免轉換時丟失 provider-specific fields。 |
| AI raw output / prompt transcript metadata | 內容本身是半結構化資料，通常先保存再由後續 parser 產生 projection。 |
| UI read model cache | 加速畫面，不做唯一 source of truth。 |
| plugin / adapter-specific metadata | 欄位可能依 provider 改變，核心流程只讀少數已投影欄位。 |
| export/import envelope | 邊界格式，不代表內部 storage。 |

不要用 JSON 的情境：

- array item 需要被搜尋、排序、分頁、統計或權限隔離。
- array item 需要 FK、unique constraint、state transition 或獨立 timestamp。
- array item 會被局部更新，例如單一 workflow step 完成或單次 quality attempt 改狀態。
- 測試需要證明資料不是 hard-coded response。

SQLite 官方支援 JSON functions，但 JSON 沒有獨立 storage type；JSON values 仍存在一般 SQLite value 裡。這代表 JSON 是查詢與轉換工具，不是取代資料表設計的理由。Source: <https://www.sqlite.org/json1.html>

## Connection Rules

每個 Grimo SQLite connection 必須明確設定基礎 PRAGMA，不要依賴 SQLite default。

```sql
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
```

Rules:

- `foreign_keys = ON` 是必要規則。SQLite 支援 FK，但 enforcement 是 connection-level setting；如果沒開，孤兒 child row 可能被寫入。Source: <https://www.sqlite.org/foreignkeys.html>
- `busy_timeout` 避免短暫 writer lock 直接讓使用者操作失敗；timeout 值要在 spec 裡說明，不要無限等待。Source: <https://www.sqlite.org/pragma.html#pragma_busy_timeout>
- `WAL` 適合本地互動式 app，讀寫可以並行；但 SQLite WAL 仍只有一個 writer，且不適合 network filesystem。Source: <https://www.sqlite.org/wal.html>
- `synchronous = NORMAL` 在 WAL mode 通常是互動式 app 的效能與安全平衡；若 spec 存放不可重建的重要資料，必須明確評估是否需要 `FULL`。Source: <https://www.sqlite.org/pragma.html#pragma_synchronous>

## Schema Rules

核心資料表預設使用這些規則：

1. 每張 table 都要有明確 owner：例如 `project_id`、`task_id`、`workflow_run_id`。
2. Parent-child relationship 用 FK 表達；測試要驗 FK enforcement 真的開啟。
3. Business uniqueness 用 `UNIQUE` 或 unique index 表達，不只靠 service 檢查。
4. Lifecycle 欄位用受限 enum text，例如 `state TEXT NOT NULL CHECK (...)`。
5. 系統欄位不能由 client 設定，例如 `id`、`state`、`created_at`、`updated_at`。
6. 大型 log、artifact、screenshot、trace file 預設放 filesystem，SQLite 存 metadata、hash、path、owner FK。
7. 若 SQLite 版本已確認支援，核心 table 優先使用 `STRICT`，避免髒型別進 source of truth。Source: <https://www.sqlite.org/stricttables.html>

Example:

```sql
CREATE TABLE task_workflow_quality_runs (
  id TEXT PRIMARY KEY,
  workflow_step_id TEXT NOT NULL
    REFERENCES task_workflow_steps(id) ON DELETE CASCADE,
  attempt INTEGER NOT NULL,
  quality_score REAL,
  passed INTEGER NOT NULL CHECK (passed IN (0, 1)),
  review_summary TEXT NOT NULL,
  fix_summary TEXT,
  created_at TEXT NOT NULL,
  UNIQUE (workflow_step_id, attempt)
) STRICT;
```

## Transaction Rules

使用者看到的一個產品動作，若會寫多張表，就必須包在同一個 transaction。

Examples:

| 使用者動作 | 必須同 transaction 的資料 |
| --- | --- |
| 建立 Project | `projects` + `project_workflow_roles` |
| 建立 Task | `tasks` + task workflow snapshot rows |
| 第一次打開 BACKLOG Task chat | `tasks.state` transition + `task_workflow_runs` + opening `task_workflow_steps` |
| 寫入一次 quality attempt | step state update + `task_workflow_quality_runs` + summary invalidation |
| approve Review Materials | human decision record + release workflow/action start row；`DONE` transition occurs after release completes |

SQLite transactions are atomic: 一個 transaction 的變更全部成功或全部不發生。Grimo 要把這個特性用在產品一致性上，不要讓使用者看到「Task 狀態已變，但 evidence 還沒寫完」這種半套資料。Source: <https://www.sqlite.org/atomiccommit.html>

## Projection Rules

Projection 是讀取方便，不是 source of truth。

| Projection | Source tables | Rule |
| --- | --- | --- |
| `TaskResponse.workflowSummary` | `task_workflow_runs`, `task_workflow_steps`, `task_workflow_quality_runs` | 可在 query 時組出；若快取，必須可重建。 |
| Task card message preview | `task_messages` / future thread tables | 只保存最近摘要；完整 thread 仍在 message tables。 |
| attachment count | `task_attachments` | count 可以 projection；attachment metadata 是 source。 |
| open questions count | future definition gap tables | count 是 projection；問題本體要有 row。 |

Spec 若新增 projection，必須寫清楚：

- source tables 是哪些；
- projection 何時更新或重建；
- 壞掉時如何透過 read-back / rebuild 驗證；
- API response 裡哪些欄位是 projection，不允許 client 寫入。

## Single-Machine Design Notes

Grimo 是單機使用，不代表可以忽略 production-grade data design。單機 SQLite 的重點不同：

- **Backup/export:** SQLite file 可以備份，但 WAL mode 會有 `-wal` / `-shm` files；不要在 database open 時只複製主檔。live backup 應使用 SQLite backup API 或 `VACUUM INTO`。Source: <https://www.sqlite.org/backup.html>
- **Diagnostics:** startup 要能檢查 DB path 可寫、native SQLite driver 可載入、FK enforcement 已開、WAL mode 成功、schema version 正確。
- **One writer:** local app 可以有多個 read paths，但所有 write paths 要短 transaction、避免長時間持有 writer lock。
- **Filesystem ownership:** 不要把 Grimo database 放在 network filesystem 當作同步方案；future sync 要另設 operation log、conflict policy 和 migration story。
- **Large artifacts:** DB row 不要塞大型 log/video/screenshot；使用 filesystem artifact + metadata row，避免 backup、query、migration 都變重。

## Spec Checklist

任何新增或修改 SQLite schema 的 spec，必須在 Storage section 回答：

1. 使用者會靠這些資料完成什麼結果？
2. 哪些 table 是 source of truth？
3. 哪些 JSON / projection 不是 source of truth？
4. 每張 table 的 parent / owner 是誰？
5. FK、unique、check constraints 是什麼？
6. 哪些欄位 client 不能設定？
7. 哪些寫入必須在同一個 transaction？
8. BDD 要怎麼讀回 DB row 證明不是 hard-coded API response？
9. 測試是否使用 temporary database，不碰使用者真資料？
10. connection PRAGMA 是否有自動化測試或 POC evidence？

如果一份 spec 只是把 `steps: []`、`messages: []`、`scores: []` 放進一個 JSON 欄位，必須先證明 array item 沒有獨立生命週期，也不需要查詢、排序、約束、局部更新或 evidence read-back。證明不了就拆表。
