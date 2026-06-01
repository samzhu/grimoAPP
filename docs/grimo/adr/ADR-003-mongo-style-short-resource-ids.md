# ADR-003 — Mongo-Style Short Resource IDs

**Status:** Superseded by ADR-004 for Grimo MVP API contracts
**Date:** 2026-06-01
**Superseded By:** ADR-004 — TSID Resource IDs

## Context

Grimo 的 Project、Task、Workflow Run 等 resource 需要穩定 ID。使用者會在 URL、API response、log、BDD evidence 和 SQLite row 裡看到這些 ID。MVP 不需要全域協調的 sequence，也不希望使用 36 字元 UUID 讓 UI 和 log 變長。

MongoDB `ObjectId` 是可參考的短 ID 形狀：12 bytes，通常以 24 個 hexadecimal characters 表示；內容包含 4-byte timestamp、5-byte process random 和 3-byte counter。MongoDB 文件也提醒它大致依建立時間排序，但不是嚴格 monotonic，因為時間解析度是秒，而且 client clocks 可能不同。

## Decision

Grimo MVP resource ID 使用 Mongo-style short ID：

```text
24 lowercase hexadecimal characters
```

範例：

```text
665f1e2a7b9c0d1e2f3a4b5c
```

產生規則：

| bytes | 內容 | 用途 |
| --- | --- | --- |
| 0-3 | Unix timestamp seconds, big-endian | 讓 ID 大致可依建立時間排序，也方便 debug 時看出約略建立時間。 |
| 4-8 | 5 secure random bytes generated once per backend process | 降低不同機器、不同 process 之間碰撞機率。 |
| 9-11 | 3-byte atomic counter initialized randomly | 同一 process 同一秒內大量建立 resource 時仍能遞增避免碰撞。 |

API response 直接回這個 ID，不加 entity prefix：

```json
{
  "id": "665f1e2a7b9c0d1e2f3a4b5c",
  "name": "grimoAPP"
}
```

Resource type 由 REST path、table name、DTO type 和 UI context 表示，例如 `/api/projects/{id}` 已經代表 Project，所以 ID 不需要寫成 `prj_...`。

## Consequences

- ID 比 UUID canonical string 短，適合本機 log、URL 和 UI 顯示。
- 不需要 database sequence；backend 可以在 insert 前產生 ID。
- SQLite 欄位可維持 `TEXT PRIMARY KEY`，格式驗證為 `^[0-9a-f]{24}$`。
- ID 只代表 resource identity，不是 security token；它會洩漏約略建立時間。
- ID 不是嚴格排序保證；如果 UI 需要正確建立時間排序，仍應使用 `createdAt`。
- 若未來需要跨系統標準 ID 或更嚴格毫秒排序，可另開 ADR 評估 UUIDv7 或 ULID，不在 S002 混入。

## Alternatives Considered

| Option | Result | Why |
| --- | --- | --- |
| UUIDv7 | Rejected for MVP resource IDs | 標準且時間排序友善，但 canonical string 是 36 chars，對 Grimo 目前 UI/log 來說偏長。 |
| ULID | Rejected for MVP resource IDs | 26 chars、可 lexicographic sort、URL safe，但仍比 Mongo-style 24 hex 長，且會引入另一套 alphabet/validation。 |
| Nano ID | Rejected for persisted backend IDs | 21 chars 且 URL-friendly，但預設是 random-only，不自然保留建立時間順序；更適合前端暫存 key 或公開短碼。 |
| Prefixed ID such as `prj_...` | Rejected for canonical DB/API ID | 可讀性高，但 REST path 和 DTO 已經提供 resource type；prefix 會增加長度，也讓不同 resource 的 ID format 分裂。 |

## References

- MongoDB BSON ObjectId: <https://www.mongodb.com/docs/manual/reference/bson-types/#objectid>
- UUIDv7 in RFC 9562: <https://www.ietf.org/rfc/rfc9562#section-5.7>
- ULID specification: <https://github.com/ulid/spec>
- Nano ID documentation: <https://github.com/ai/nanoid>
