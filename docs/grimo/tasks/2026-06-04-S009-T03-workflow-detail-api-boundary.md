# S009-T03: Workflow Detail Read API Boundary

> Spec: S009 | Task: T03 | Status: PASS | Owner: backend | Depends on: S009-T01

## Purpose

Task detail 需要能讀出 ordered workflow step evidence 和 Quality Loop attempts，讓 QA 和後續 UI 可以判斷 agent 是否真的跑過流程。S009 只開 read API，不開 public evidence write API。

## Contract

- API: `GET /api/projects/{projectId}/tasks/{taskId}/workflow`
- Response includes `taskId`, `projectId`, `workflowRunId`, `workflowSource`, `steps[]`.
- `steps[]` are ordered by `stepOrder`.
- Each step has nested `qualitySummary` from latest quality attempt, or `null`.
- Cross-project access returns `404`.
- Public `POST/PUT/PATCH/DELETE /workflow` endpoints are not available.

## BDD

Scenario: read ordered workflow detail

- Given a Task has an active workflow run
- And ordered run steps have quality attempts
- When the client reads the workflow detail
- Then response steps are ordered by `stepOrder`
- And latest quality attempt is nested under the correct step
- And pending steps return `qualitySummary=null`

Scenario: project isolation protects workflow evidence

- Given Project A has Task A with workflow evidence
- And Project B exists
- When the client reads Task A through Project B's URL
- Then response is `404`

Scenario: evidence write is not public

- Given S009 read API exists
- When the client sends `POST`, `PUT`, `PATCH`, or `DELETE` to the workflow URL
- Then the response is `405` or `404`
- And no workflow evidence row changes

## Files

- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceController.java`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowDetailResponse.java`
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStore.java`
- `backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceApiTests.java`

## Test Plan

- Red: add API tests for detail, isolation and no-public-write behavior.
- Green: implement read controller and response mapping.
- Refactor: keep test fixtures internal; do not add public mutation routes for convenience.

## Verification

```bash
cd backend
./gradlew test --tests '*WorkflowEvidenceApiTests'
```

## Notes

- This task does not require visible frontend work.
- If frontend types are added, they should be passive response types only.

## Status
PASS

## Result
Date: 2026-06-05
Test: `AC-S009-3: workflow detail returns ordered step evidence and latest quality result`, `AC-S009-4: workflow detail cannot be read across Project boundaries`, and `AC-S009-5: workflow evidence write operations are not public API` (`backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceApiTests.java`)
Files changed:
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceController.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceService.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceStore.java` (modified)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/TaskWorkflowDetailResponse.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowSourceResponse.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowStepEvidenceResponse.java` (new)
- `backend/src/main/java/io/github/samzhu/grimo/workflow/WorkflowQualitySummaryResponse.java` (new)
- `backend/src/test/java/io/github/samzhu/grimo/workflow/WorkflowEvidenceApiTests.java` (new)
- `docs/grimo/tasks/2026-06-04-S009-T03-workflow-detail-api-boundary.md` (modified)
Notes:
- RED: `cd backend && ./gradlew test --tests '*WorkflowEvidenceApiTests'` failed because `GET /api/projects/{projectId}/tasks/{taskId}/workflow` returned `404` instead of `200`.
- GREEN: `cd backend && ./gradlew test --tests '*WorkflowEvidenceApiTests'` passed.
- Static check: `rg -n "@(PostMapping|PutMapping|PatchMapping|DeleteMapping).*workflow|/workflow" backend/src/main/java/io/github/samzhu/grimo` found only the read controller route and existing `/workflow-recipes`; no public workflow evidence write mapping was added.
- The read API verifies Project ownership before returning detail. Cross-project reads return `404`, and unsupported write methods return `405` without changing workflow run, step, or quality rows.
