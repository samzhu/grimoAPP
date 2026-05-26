# ADR-001 — Pollack AgentWorks SQLite Selection and Verification

**Status:** Accepted for local-first POC  
**Date:** 2026-05-25

## Context

Grimo 的內部 execution substrate 以 Pollack AI Lab AgentWorks 為主，尤其是 `Agent Workflow`。產品需要在本地保存 workflow checkpoint、step trace、quality score、review findings 與 fix history，因此 local-first MVP 需要先選定 SQLite path，並確認 Pollack 套件中哪些真的碰 SQL / JDBC storage。

這不是單純的 database preference。Grimo 跑在使用者自己的電腦上，不能預設環境一定滿足雲端服務或 CI runner 的條件；Java 版本、native library 權限、filesystem 權限、Docker、CLI provider 登入與 port availability 都可能不一致。因此 storage path 必須偏向低安裝成本、可診斷、可降級、可在單機環境恢復的設計。

這個決策收斂三件事：

1. `workflow-batch` 的 JDBC durability layer 是否能接 SQLite。
2. AgentWorks BOM 內各 Pollack 套件是否需要 SQLite adapter。
3. Java / Spring Boot backend 應使用哪個 SQLite JDBC driver。

版本來源以實際可解析依賴為準：

- `io.github.markpollack:agentworks-bom:1.0.12`
- Pollack Lab 文件列 Agent Workflow 0.6.0，但 Maven Central 上已發布的 `agentworks-bom:1.0.12` POM 目前解析 `workflow-batch` 0.5.0、`workflow-flows` 0.5.0 等版本。
- Backend dependency declaration 優先使用 BOM-managed versions；只有 BOM 指到不可解析 artifact 時才暫時指定明確 released version。
- `sqlite-jdbc` 與 Hibernate community dialect 由 Spring Boot dependency management 管理。

參考：

- Pollack AI Lab AgentWorks BOM: <https://lab.pollack.ai/projects/agentworks-bom>
- Pollack AI Lab Agent Workflow: <https://lab.pollack.ai/projects/agent-workflow>
- Pollack AI Lab Agent Workflow Durability: <https://lab.pollack.ai/docs/agent-workflow/durability>
- Xerial SQLite JDBC: <https://github.com/xerial/sqlite-jdbc>
- Flyway SQLite database driver reference: <https://documentation.red-gate.com/flyway/reference/database-driver-reference/sqlite>
- Hibernate ORM dialect documentation: <https://docs.hibernate.org/stable/orm/dialect/>
- Spring Data JDBC dialect documentation: <https://docs.spring.io/spring-data/relational/reference/jdbc/getting-started.html>
- jOOQ RDBMS support matrix: <https://www.jooq.org/download/support-matrix>
- SQLite WAL: <https://www.sqlite.org/wal.html>
- OpenJDK JEP 472 native access: <https://openjdk.org/jeps/472>

## Decision

Use SQLite as Grimo local-first MVP database path for Pollack Agent Workflow checkpoint / trace POC and Grimo workflow evidence.

Use `org.xerial:sqlite-jdbc` as the SQLite JDBC driver:

```kotlin
testRuntimeOnly("org.xerial:sqlite-jdbc")
```

The version is managed by Spring Boot dependency management. Current Gradle resolution:

```text
org.xerial:sqlite-jdbc -> 3.50.3.0
```

Use `org.hibernate.orm:hibernate-community-dialects` with `org.hibernate.community.dialect.SQLiteDialect` when JPA is needed.

## Pollack Storage Surface

| Pollack project / artifacts | POC version | Storage surface | SQLite decision |
| --- | ---: | --- | --- |
| Agent Workflow `workflow-batch` | 0.5.0 | Spring Data JPA entities + JDBC `DataSource`; `JdbcTraceRecorder` creates `step_transitions` | **Use SQLite for local MVP.** POC verified checkpoint skip, trace write, and WAL mode. |
| Agent Workflow `workflow-api/core/flows/tools/agents` | 0.5.0 | Workflow graph, step, gate, context, runner API; no owned DB | **No SQLite adapter needed.** Persistence is runner / recorder responsibility. |
| Agent Workflow `workflow-temporal` | 0.5.0 | Temporal Activity dispatch; durability lives in Temporal infrastructure | **Not local SQLite.** Future scale-out runner, not MVP local DB path. |
| Agent Journal `journal-core` | 1.1.0 | `JsonFileStorage` / `InMemoryStorage`; append-only journal | **No built-in SQLite path.** MVP can use JSON file; implement `JournalStorage` adapter only if Grimo needs journal in one DB. |
| Agent Memory `memory-core` | 0.1.0 | `FileSystemMemoryStore`; compacted memory files | **No built-in SQLite path.** MVP can use filesystem memory; implement adapter only if centralized query / backup is needed. |
| Agent Hooks `agent-hooks-core` | 0.6.2 | Hook SPI, events, decision model | **No DB surface.** Hook events can be projected into Journal or Grimo tables. |
| Agent Client `agent-client-core` | 0.18.0 | Plain Java client API for CLI agents | **No DB surface.** Grimo persists task / session / execution state. |
| Agent Judge `agent-judge-core/exec/file` | 0.10.0 | Deterministic, command, and file judges | **No DB surface.** Grimo persists verdict, score, and review evidence. |
| Agent Sandbox `agent-sandbox-core` | 0.9.2 | Local / Docker / E2B execution API; filesystem workspace | **No DB surface.** Grimo or Journal persists logs / artifacts. |
| Agent Experiment `experiment-core` | 0.2.0 | Dataset / experiment runner around filesystem datasets | **Not MVP DB substrate.** Import projections to SQLite only if benchmark dashboard is needed. |
| ACP Java SDK `acp-core` | 0.10.0 | Protocol schema / session / transport API | **No DB surface.** Session persistence belongs in Grimo adapter layer if needed. |

## Rationale

1. **SQLite fits a variable local environment.** Single-user local execution benefits from a file database that can keep Project, Task, workflow evidence, checkpoint and trace state close to the workspace without requiring an external database service.
2. **`workflow-batch` is the real SQL surface.** The Pollack stack does not all need SQLite adapters. The POC should focus on `CheckpointingStepRunner`, `JdbcTraceRecorder`, and Grimo-owned workflow evidence tables.
3. **Xerial is the Java / Spring integration point.** `org.xerial:sqlite-jdbc` exposes standard JDBC URL shape `jdbc:sqlite:...`, which is what Spring `DataSource`, JPA, Flyway and Pollack `workflow-batch` expect.
4. **Hibernate dialect is not a driver.** `SQLiteDialect` helps ORM SQL generation but does not provide connectivity; Xerial is still required.
5. **Migration path is straightforward.** Flyway documents Xerial coordinates, URL format and default driver class for SQLite.
6. **Temporal is a different durability model.** `workflow-temporal` delegates durability to Temporal infrastructure, so it should be treated as future scale-out rather than local SQLite durability.

## Alternatives Considered

| Alternative | Decision | Reason |
| --- | --- | --- |
| H2 for local tests | Rejected | Does not match the local-first single-file database direction or SQLite-specific behavior. |
| Native SQLite C library directly | Rejected | Bypasses standard Spring / JDBC integration and increases packaging complexity. |
| Hibernate `SQLiteDialect` only | Rejected as driver | Dialect is not connectivity. It still needs a JDBC driver. |
| Spring Data JDBC only | Rejected as SQLite strategy | Spring Data JDBC does not list SQLite among directly supported dialects; unsupported DBs require a custom `JdbcDialect`. |
| Jdbi | Deferred | Useful DAO layer over JDBC, but still needs Xerial underneath. |
| jOOQ | Deferred | Strong SQLite support, but adds DSL / codegen cost. Reconsider if query complexity grows. |
| SQLDelight SQLite driver | Rejected for Java backend MVP | Strong Kotlin fit, less aligned with current Spring Boot Java backend. |
| Liquibase / Flyway | Rejected as driver | Migration tools, not drivers. Flyway remains a good migration candidate on top of Xerial. |
| Postgres only | Deferred for production path | Production-ready path remains open, but it should not block local-first MVP. |

## Consequences

- Local MVP can use SQLite for Grimo domain tables and Pollack workflow checkpoint / trace POC.
- Because Grimo runs on user machines, database initialization and runtime startup must expose actionable diagnostics for missing native access, unwritable filesystem paths, locked database files, WAL limitations and port conflicts instead of failing opaquely.
- `spring.datasource.url` should use `jdbc:sqlite:<file>`.
- JPA usage requires `org.hibernate.orm:hibernate-community-dialects` and `spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect`.
- Schema migration should prefer Flyway first because its SQLite reference directly supports Xerial and SQL scripts.
- SQLite should use WAL mode for local workflow / event workloads, with awareness that WAL is not suitable over network filesystems and SQLite still permits only one writer at a time.
- Grimo domain tables and Pollack workflow tables may share one SQLite database, but schema ownership, table namespace and migrations must be explicit in architecture.
- `journal-core` and `memory-core` are file-backed first; only implement SQLite adapters if product requirements need centralized DB query / backup / transaction boundaries.
- `review findings`, `quality_score` and `fix history` should live in Grimo workflow evidence schema. Agent Judge calculates verdicts; it does not persist Grimo evidence.
- Java 25 currently emits native access warnings when `sqlite-jdbc` loads its native library. Runtime commands may eventually need `--enable-native-access=ALL-UNNAMED` or a more precise module-scoped option.
- Native image compatibility is plausible but unproven. A later native build POC must cover Spring Boot native image, `sqlite-jdbc`, Hibernate `SQLiteDialect`, Flyway if adopted, and Pollack `workflow-batch` JPA repositories.

## Verification

POC tests:

- `backend/src/test/java/io/github/samzhu/grimo/GrimoApplicationTests.java`
- `backend/src/test/java/io/github/samzhu/grimo/poc/PollackWorkflowSqlitePocTests.java`
- `backend/src/test/java/io/github/samzhu/grimo/poc/PollackAgentWorksStorageSurfacePocTests.java`

Verified behavior:

1. BOM-managed `workflow-batch:0.5.0` checkpoint / trace path runs on SQLite.
2. `CheckpointingStepRunner` skips completed steps for the same `runId + stepName` and returns cached output.
3. `JdbcTraceRecorder` creates and writes `step_transitions` on SQLite 3.50.3.
4. SQLite file database can be created and switched to WAL mode.
5. BOM-managed `journal-core:1.1.0` can write / read events with `JsonFileStorage`.
6. `memory-core:0.1.0` can write / read memory with `FileSystemMemoryStore`.
7. BOM-managed `agent-judge-core:0.10.0`, `agent-sandbox-core:0.9.2`, `agent-client-core:0.18.0`, `agent-hooks-core:0.6.2` and `acp-core:0.10.0` do not require SQLite adapters for API loading / basic usage.

Verification command:

```bash
cd backend
./gradlew test --rerun-tasks
```

Result:

```text
BUILD SUCCESSFUL
```

Known warning:

```text
WARNING: java.lang.System::load has been called by org.sqlite.SQLiteJDBCLoader
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
```
