# S001 T01 — Backend Project API with SQLite persistence

Status: PASS

## Purpose

建立 Spring Boot backend 的第一個 production API：`GET /api/workflow-recipes`、`GET /api/projects`、`POST /api/projects`。資料必須寫進 SQLite，而不是只放在記憶體。

## Requires

- Java 25
- Gradle wrapper
- SQLite JDBC dependency resolved by Spring Boot dependency management

## BDD

**Scenario: Project API creates and lists local Project**

- Given（前提）temporary SQLite database has no Project with `folderPath="/Users/samzhu/workspace/github-samzhu/grimoAPP"`
- When（動作）test sends `POST /api/projects` with `name="grimoAPP"`, `description="本機 AI 開發工作台"`, that `folderPath`, and `workflowRecipeId="coding"`
- Then（結果）response is `201 Created`
- And（而且）response body contains non-empty `id`, `workflowRecipeId="coding"`, `workflowRecipeName="開發工作流"`, and `status="ACTIVE"`
- And（而且）following `GET /api/projects` includes that Project

**Scenario: Project API rejects invalid inputs**

- Given（前提）temporary SQLite database already has one Project with `folderPath="/Users/samzhu/workspace/github-samzhu/grimoAPP"`
- When（動作）test sends another `POST /api/projects` with the same `folderPath`
- Then（結果）response is `409 Conflict`
- And（而且）blank `name`, blank `folderPath`, or unknown `workflowRecipeId` returns `400 Bad Request` with user-readable JSON error

**Scenario: Workflow recipe catalog is visible**

- Given（前提）backend starts with default workflow recipe catalog
- When（動作）test sends `GET /api/workflow-recipes`
- Then（結果）response is `200 OK`
- And（而且）body contains `id="coding"` and `name="開發工作流"`

## Implementation Notes

- Use `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-jdbc`, and `org.xerial:sqlite-jdbc`.
- Use Spring JDBC explicit SQL for S001; do not add JPA/Hibernate.
- Add schema initialization for `projects`.
- Tests must use temporary SQLite state and must not touch user data.

## Target Files

- `backend/build.gradle.kts`
- `backend/src/main/resources/application.yaml`
- `backend/src/main/resources/schema.sql`
- `backend/src/main/java/io/github/samzhu/grimo/project/*`
- `backend/src/test/java/io/github/samzhu/grimo/project/*`

## Verification

Run: `backend/gradlew -p backend test --tests '*Project*'`

Pass: Project API tests are green and fail in RED before implementation.

## Result

- RED: `backend/gradlew -p backend test --tests '*ProjectApiTests'` first failed on startup with Spring Data JDBC `NoDialectException`; SQLite is not in Spring Data Relational 4.0 direct dialect support.
- GREEN: switched S001 persistence to Spring JDBC `JdbcClient` with explicit SQL, backed by Xerial SQLite JDBC and `schema.sql`.
- Evidence: `backend/gradlew -p backend test --tests '*ProjectApiTests'` passed; `scripts/verify-release.sh` later passed `backend tests`.
