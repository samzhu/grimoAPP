# S004-T01: Backend Task API and Workflow Copy

> Spec: S004 | Task: T01 | Status: PASS | Owner: backend

## Purpose

使用者在某個 Project 裡新增 Task 後，backend 要建立一筆 Project-owned `BACKLOG` Task，並把該 Project 當下選定的 workflow definition 複製成 Task 自己的 workflow copy。建立 Task 不選 skill、不叫草稿、不啟動 active workflow run。

## Contract

- API: `POST /api/projects/{projectId}/tasks`
- API: `GET /api/projects/{projectId}/tasks`
- Request fields: `title`, `body`, `labels[]`
- Server-owned fields: `id`, `projectId`, `state=BACKLOG`, `source=manual`, `workflowRecipeId`, `workflowSummary`, `commentCount`, timestamps
- `tasks.project_id` must be `NOT NULL` and FK to `projects(id)`.
- Task creation writes `tasks`, `task_workflows`, and `task_workflow_steps` in one transaction.
- `task_workflow_runs` must remain empty after Task creation.

## BDD

Scenario: create a Project-owned Backlog Task with a copied workflow

- Given an existing Project uses workflow recipe `web-service-development`
- When the client posts a manual Task title, body and labels
- Then the response is `201 Created`
- And the Task state is `BACKLOG`
- And `workflowSummary.currentStep` and `workflowSummary.qualityScore` are `null`
- And SQLite has one `tasks` row owned by that Project
- And SQLite has one Task Workflow and ordered copied workflow step rows for that Task
- And SQLite has no active workflow run for that Task

Scenario: list only the requested Project's Tasks

- Given Project A and Project B each have Tasks
- When the client lists Tasks for Project A
- Then only Project A Tasks are returned, newest first
- And each response has nested `workflowSummary`

Scenario: invalid create does not persist bad rows

- Given no Project exists for `missing-project`
- When the client creates a Task under `missing-project`
- Then the response is `404`
- And no `tasks` or workflow copy rows are inserted
- When the client sends a blank title
- Then the response is `400`
- And row count is unchanged

## Files

- `backend/src/main/resources/schema.sql`
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectStore.java`
- `backend/src/main/java/io/github/samzhu/grimo/task/*`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowService.java`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStore.java`
- `backend/src/test/java/io/github/samzhu/grimo/task/TaskApiTests.java`

## Test Plan

- Red: add `TaskApiTests` for AC-S004-1/2/3 and persisted read-back.
- Green: implement minimal schema, store, service and controller.
- Refactor: keep Task root CRUD in `task` package; keep workflow copy in `workflow` package.

## Verification

```bash
cd backend
./gradlew test --tests '*TaskApiTests'
```

Also keep the POC green:

```bash
cd backend
./gradlew test --tests '*SqliteForeignKeyEnforcementPocTests'
```

## Notes

- Do not add request fields for `skill`, `state`, `source`, `workflowRecipeId`, `workflowSummary`, `acceptance`, `gaps`, `evidence`, or `commentCount`.
- Do not create active workflow runs here; S009-T01 owns first Chat transition.
- Do not store `step`, `score`, `comments`, `workflowSummary`, `acceptance`, `gaps`, or `evidence` as root `tasks` columns.

## Status

PASS

## Result

Date: 2026-06-04
Test: `createsBacklogTaskWithCopiedWorkflow`, `listsOnlyTasksForSelectedProject`, `rejectsInvalidCreateWithoutRows` (`backend/src/test/java/io/github/samzhu/grimo/task/TaskApiTests.java`)
Files changed:
- `backend/src/main/resources/application.yaml` (modified)
- `backend/src/main/resources/schema.sql` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/project/ProjectStore.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/project/ShortResourceIdGenerator.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/task/CreateTaskRequest.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/task/MissingProjectException.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskController.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskErrorHandler.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskRecord.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskResponse.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskService.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskStore.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/task/WorkflowSummaryResponse.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowRecord.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowService.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowStepRecord.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStore.java` (new)
- `backend/src/test/java/io/github/samzhu/grimo/task/TaskApiTests.java` (new)
Notes:
- RED: `cd backend && ./gradlew test --tests '*TaskApiTests'` failed as expected because `/api/projects/{projectId}/tasks` returned `404` and `tasks` table did not exist.
- GREEN: `cd backend && ./gradlew test --tests '*TaskApiTests'` passed.
- POC: `cd backend && ./gradlew test --tests '*SqliteForeignKeyEnforcementPocTests'` passed.
- Readability gate: new production classes have class-level Javadoc; new Services have `LoggerFactory`; `git diff --check` passed.
- Official docs consulted: Spring JDBC `JdbcClient` reference <https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html>, Spring `@Transactional` reference <https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html>, SQLite foreign key support <https://www.sqlite.org/foreignkeys.html>.
