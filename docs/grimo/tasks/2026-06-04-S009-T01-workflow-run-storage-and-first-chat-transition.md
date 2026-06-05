# S009-T01: Workflow Run Storage and First Chat Transition

> Spec: S009 | Task: T01 | Status: PASS | Owner: backend | Depends on: S004-T01

## Purpose

使用者第一次從 `BACKLOG` Task 按 Chat 時，Task 才真正進入 workflow。Backend 要在同一個 transaction 內完成 `BACKLOG -> DEFINING`、建立 active workflow run、從 Task Workflow copy 複製 run steps，並啟動 opening step。

## Contract

- Source of truth tables: `task_workflow_runs`, `task_workflow_run_steps`, `task_workflow_quality_runs`
- Parent chain: `projects -> tasks -> task_workflows -> task_workflow_runs -> task_workflow_run_steps -> task_workflow_quality_runs`
- One Task can have at most one active workflow run for MVP.
- Opening transition must be idempotent enough to avoid duplicate active runs; second active run attempt fails or returns existing active run by explicit service rule.
- No public evidence write endpoint is added.

## BDD

Scenario: first Chat initializes workflow execution

- Given a `BACKLOG` Task has a Task Workflow copy
- And it has no active workflow run
- When backend opens Chat for that Task for the first time
- Then the Task state becomes `DEFINING`
- And SQLite has one active workflow run
- And run steps are copied from the Task Workflow in order
- And the opening step is `ACTIVE`
- And later recipe changes do not rewrite the copied run step labels

Scenario: workflow evidence cannot be orphaned or duplicated

- Given SQLite FK enforcement is enabled
- When test code inserts a run for a missing Task
- Then SQLite rejects the row
- When test code inserts a duplicate active run for the same Task
- Then SQLite rejects or service prevents it
- When test code inserts duplicate quality attempts for the same run step
- Then SQLite rejects the duplicate attempt

## Files

- `backend/src/main/resources/schema.sql`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowTransitionService.java`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceService.java`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStore.java`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/*Record.java`
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskStore.java`
- `backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStoreTests.java`

## Test Plan

- Red: write storage and transition tests against S004-created Tasks.
- Green: add schema, store, service and transaction boundary.
- Refactor: keep transition logic in service; keep SQL ownership rules in store/schema.

## Verification

```bash
cd backend
./gradlew test --tests '*WorkflowEvidenceStoreTests'
```

## Notes

- S004 creates Task Workflow copy; this task creates execution evidence only when Chat starts.
- `workflowSummary.currentStep` must still be implemented in S009-T02, not faked here.

## Status
PASS

## Result
Date: 2026-06-04
Test: `AC-S009-1: first Chat starts one active workflow run from copied Task Workflow steps` and `AC-S009-1: workflow evidence rejects orphan rows and duplicate active evidence` (`backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStoreTests.java`)
Files changed:
- `backend/src/main/resources/schema.sql` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/task/TaskStore.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStore.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowTransitionService.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowRunRecord.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowRunStepRecord.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowQualityRunRecord.java` (new)
- `backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStoreTests.java` (new)
- `docs/grimo/tasks/2026-06-04-S009-T01-workflow-run-storage-and-first-chat-transition.md` (modified)
Notes:
- RED: `cd backend && ./gradlew test --tests '*WorkflowEvidenceStoreTests'` failed at `compileTestJava` because `TaskWorkflowTransitionService` and `TaskWorkflowRunRecord` did not exist.
- GREEN: `cd backend && ./gradlew test --tests '*WorkflowEvidenceStoreTests'` passed 2 tests.
- The transition is internal only: no public evidence write endpoint was added. `TaskWorkflowTransitionService.openChatForBacklogTask(taskId)` returns the existing active run on repeated calls, otherwise atomically moves `BACKLOG -> DEFINING`, creates one active workflow run, and copies execution steps from the Task Workflow rows.
- `workflowSummary.currentStep` remains unchanged for S009-T02; this task only stores workflow execution evidence.
