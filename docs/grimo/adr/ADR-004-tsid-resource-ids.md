# ADR-004 — TSID Resource IDs

**Status:** Accepted for S002 revision; supersedes ADR-003
**Date:** 2026-06-01

## Context

Grimo 的 Project、Task、Workflow Run 等 resource id 會出現在 URL、API response、log、BDD evidence 和 SQLite row。S002 原本依 ADR-003 採用 Mongo-style 24 個 lowercase hex 字元，但使用者希望改成更短、仍可依時間大致排序、且有維護中 Java 套件支援的方案。

`io.hypersistence:hypersistence-tsid` 提供 Java TSID 產生器。GitHub README 說明 TSID 字串是 13 個 Crockford base32 字元、URL safe、沒有 hyphens，且可以用 `TSID.Factory.getTsid().toString()` 建立；Maven Central 目前最新可用版本為 `2.1.4`。套件授權是 MIT License。

## Decision

Grimo MVP resource id 使用 Hypersistence TSID 的 canonical string：

```text
13 Crockford base32 uppercase characters
```

範例：

```text
01226N0640J7Q
```

Backend 透過 `io.hypersistence.tsid.TSID` 產生 canonical id：

```java
String id = TSID.Factory.getTsid().toString();
```

API response 直接回這個 ID，不加 entity prefix：

```json
{
  "id": "01226N0640J7Q",
  "name": "grimoAPP"
}
```

Resource type 由 REST path、table name、DTO type 和 UI context 表示，例如 `/api/projects/{id}` 已經代表 Project，所以 ID 不需要寫成 `prj_...`。

## Consequences

- ID 比 UUID canonical string、ULID 和 Mongo-style 24 hex 更短，適合本機 log、URL 和 UI 顯示。
- ID 可以大致依產生時間排序；如果 UI 需要嚴格建立時間排序，仍應使用 `createdAt`。
- SQLite 欄位維持 `TEXT PRIMARY KEY`，格式驗證為 `^[0-9A-HJKMNP-TV-Z]{13}$`。
- 需要在 backend 加入 `implementation("io.hypersistence:hypersistence-tsid:2.1.4")`；此版本不由 Spring Boot BOM 管理，所以版本要在 architecture dependency table 記錄。
- ID 只代表 resource identity，不是 security token；它會透露約略建立時間。
- ADR-003 的 Mongo-style 24 hex contract 不再作為 S002 目標 contract；既有 task / test 若已驗證 24 hex，需重開改成 TSID。

## Alternatives Considered

| Option | Result | Why |
| --- | --- | --- |
| Mongo-style 24 lowercase hex | Superseded | 形狀接近 MongoDB ObjectId，但需要自寫 generator；使用者改選 TSID 後，24 chars 也比必要長。 |
| `com.github.f4b6a3:tsid-creator` | Rejected for Grimo MVP | 原作者 repo 進入 maintenance mode；可用但新功能維護方向較保守。 |
| `io.hypersistence:hypersistence-tsid` | Accepted | MIT License、Maven Central 版本 `2.1.4`、API 簡單，輸出 13-char Crockford base32 string。 |
| UUIDv7 | Rejected for MVP resource IDs | 標準且時間排序友善，但 canonical string 是 36 chars，對 Grimo 目前 UI/log 來說偏長。 |
| ULID | Rejected for MVP resource IDs | 26 chars、可 lexicographic sort、URL safe，但仍比 TSID 13 chars 長。 |
| Nano ID | Rejected for persisted backend IDs | 預設 random-only，不自然保留建立時間順序；更適合前端暫存 key 或公開短碼。 |
| Prefixed ID such as `prj_...` | Rejected for canonical DB/API ID | REST path 和 DTO 已經提供 resource type；prefix 會增加長度，也讓不同 resource 的 ID format 分裂。 |

## References

- Hypersistence TSID README: <https://github.com/vladmihalcea/hypersistence-tsid>
- Maven Central `io.hypersistence:hypersistence-tsid`: <https://central.sonatype.com/artifact/io.hypersistence/hypersistence-tsid>
- GitHub license file: <https://github.com/vladmihalcea/hypersistence-tsid/blob/master/LICENSE>
- Maven repository versions: <https://repo1.maven.org/maven2/io/hypersistence/hypersistence-tsid/>
