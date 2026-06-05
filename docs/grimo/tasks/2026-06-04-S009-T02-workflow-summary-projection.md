# S009-T02: Workflow Summary Projection

> Spec: S009 | Task: T02 | Status: PASS | Owner: backend | Depends on: S009-T01

## Purpose

Task board 要顯示目前 workflow step 和最新 quality score，但這兩個值必須從 active run/evidence 查出來，不能存在 `tasks.step`、`tasks.score` 或 `tasks.workflow_summary` JSON。

## BDD

Scenario: Task list summary is projected from workflow evidence

- Given Project A has one Task with active `Discuss` and latest quality score `8.5`
- And Project A has another Task with active `Dev` and latest quality score `10`
- When the client lists Tasks for Project A
- Then each Task response contains its own projected `workflowSummary`
- And `Discuss` Task has `currentStep=Discuss` and `qualityScore=8.5`
- And `Dev` Task has `currentStep=Dev` and `qualityScore=10`

Scenario: Backlog Task has no workflow progress projection

- Given a `BACKLOG` Task has copied workflow steps but no active run
- When the client lists Tasks
- Then `workflowSummary.currentStep` is `null`
- And `workflowSummary.qualityScore` is `null`

Scenario: Task root stays normalized

- Given S009 schema is initialized
- When the test inspects `tasks` columns
- Then `step`, `score`, and `workflow_summary` columns do not exist

## Files

- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowSummaryProjectionService.java`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStore.java`
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskResponse.java`
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskStore.java`
- `backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowSummaryProjectionTests.java`
- `backend/src/test/java/io/github/samzhu/grimo/task/TaskApiTests.java`

## Test Plan

- Red: add two different Tasks with different active steps/scores to catch hardcoded summaries.
- Green: implement batched project-scoped projection.
- Refactor: avoid N+1 query per Task if a simple project-scoped join/batch query is enough.

## Verification

```bash
cd backend
./gradlew test --tests '*WorkflowSummaryProjectionTests' --tests '*TaskApiTests'
```

## Notes

- A Task with no active run must not project the first copied planned step as current progress.
- The projection can be query-time; no cache table is required for this task.

## Status
PASS

## Result
Date: 2026-06-04
Test: `AC-S009-2: Task list projects current step and latest quality score from workflow evidence` and `AC-S009-2: BACKLOG Task does not project planned workflow copy as progress` (`backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowSummaryProjectionTests.java`)
Files changed:
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskResponse.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskService.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStore.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowSummaryProjectionService.java` (new)
- `backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowSummaryProjectionTests.java` (new)
- `docs/grimo/tasks/2026-06-04-S009-T02-workflow-summary-projection.md` (modified)
Notes:
- RED: `cd backend && ./gradlew test --tests '*WorkflowSummaryProjectionTests' --tests '*TaskApiTests'` failed because the Discuss Task returned `workflowSummary.currentStep = null` instead of `Discuss`.
- GREEN: `cd backend && ./gradlew test --tests '*WorkflowSummaryProjectionTests' --tests '*TaskApiTests'` passed.
- `TaskService.listTasks(projectId)` now uses `WorkflowSummaryProjectionService` to load summaries in one project-scoped query. The projection ranks `BLOCKED -> ACTIVE -> PENDING`, then reads the selected step's latest quality attempt by highest `attempt`.
- `BACKLOG` Tasks with only copied workflow steps still return `workflowSummary.currentStep = null` and `workflowSummary.qualityScore = null`; no `step`, `score`, or `workflow_summary` columns were added to `tasks`.
