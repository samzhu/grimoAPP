# Grimo Agent Workflow Definition

**Status:** Draft reference
**Last verified:** 2026-06-14
**Source:** Pollack AI Lab Agent Workflow

## Purpose

這份文件把 Grimo 的 Task workflow model 映射成 Pollack AI Lab `Agent Workflow` 可以表達的 workflow / step / gate / sub-workflow 形狀。

它不是 PRD 的替代品。PRD 定義產品承諾；這份文件只定義工程上如何把 Task State、Workflow Run Status、Workflow Step、Quality Loop、trace 和 checkpoint 對齊。

## Source Grounding

Pollack `Agent Workflow` 的關鍵定義：

- `Workflow` 由多個 `Step` 組成；每個 step 做一件事，可以是 deterministic function、single LLM call，或 agentic CLI session。
- `Gate` 評估 step output，將流程導向 pass / fail path。
- Context 透過 typed keys 在 steps 之間流動。
- Workflow definition 會編譯成 graph IR，definition 與 execution 分離；同一份 workflow 可由 `LocalStepRunner`、`CheckpointingStepRunner` 或 `TemporalStepRunner` 執行。
- 每個 step transition 可 trace；checkpoint runner 可用 `runId + stepName` 跳過已完成 step。
- `Workflow` 本身也實作 `Step`，所以 sub-workflow 可以放在 `.then()`、`.branch()`、`.onPass()`、`.onFail()`、`.parallel()` 等接受 step 的位置。

Sources:

- <https://lab.pollack.ai/projects/agent-workflow>
- <https://lab.pollack.ai/docs/agent-workflow/examples>
- <https://lab.pollack.ai/docs/agent-workflow/durability>

Code blocks labeled `Pollack shape` are design sketches, not compile-ready Java. They use Pollack DSL vocabulary to show graph shape and sub-workflow boundaries before implementation names and generic types are finalized.

## Canonical Model

Grimo 的 Task workflow 有四層，不能混成同一個欄位：

| Layer | Answers | Canonical examples |
| --- | --- | --- |
| Task State | 使用者在 board/list 上看到這件 Task 在哪裡 | `BACKLOG`, `DEFINING`, `READY`, `RUNNING`, `REVIEW`, `DONE` |
| Workflow Run Status | 這次 workflow execution 自己現在在做什麼 | `not_started`, `running`, `waiting_dispatch`, `blocked`, `waiting_review`, `releasing`, `completed` |
| Workflow Step | Task 目前跑到哪個主要專業節點 | `Discuss`, `Explore`, `Dev`, `E2E-test`, `release` |
| Step Sub-workflow | 單一 step 的品質循環做到哪裡 | `Review -> Rating -> Gate -> Fix` |

Key rules:

1. Task State is not a Pollack step. It is a product projection.
2. Workflow Run Status is not Task State. It describes execution lifecycle inside a Task.
3. `REVIEW` is a Task State where the user reviews completed Review Materials.
4. Quality Loop `Review` is an inner step that checks one Workflow Step output.
5. `release` is a Workflow Step/action after the user approves in `REVIEW`; it is not a Task State.
6. `DONE` is reached only after `release` completes.
7. Reject from `REVIEW` returns the Task to `DEFINING`, not `RUNNING`.
8. `NEEDS_HUMAN` is a repair reason on a Task State or Workflow Run Status, not a seventh board state.

## Pollack Mapping

| Grimo concept | Pollack Agent Workflow concept | Notes |
| --- | --- | --- |
| Project Workflow Recipe | `Workflow` definition | Project 選定 recipe；Task 建立時複製成 Task Workflow。 |
| Task Workflow | copied workflow definition / metadata | Immutable Task-owned copy; not active execution evidence. |
| Workflow Run | `WorkflowExecutor` run with stable `runId` | One execution attempt for a Task Workflow. |
| Workflow Run Status | run lifecycle state / projection | `not_started`, `running`, `waiting_dispatch`, `blocked`, `waiting_review`, `releasing`, `completed`; not board-facing Task State. |
| Workflow Step | `Step` or sub-workflow-as-step | `Discuss` can be interactive; `Dev` can be agentic CLI; `release` is the final step after approval. |
| Quality Loop | `Workflow` used as sub-workflow | `Review -> Rating -> Gate -> Fix` repeats until pass or stop condition. |
| Ready Gate | `Gate` plus human decision state | Moves Task to `READY` only after Definition Package is accepted. |
| Dispatcher preflight | deterministic `Step` + gate | Checks profile, dependencies, runtime, permissions, and dispatch window. |
| Agent Claim | deterministic `Step` | Creates claim, worktree/sandbox, execution context, and `runId`. |
| Review Materials | deterministic aggregation step | Built after `RUNNING` evidence is complete and before Task enters `REVIEW`. |
| Human Review Decision | product gate after Review Materials | Approve starts `release`; reject returns to `DEFINING`. |
| Release | final Workflow Step/action | Runs after approve; completion moves Task to `DONE`. |
| Workflow Evidence | trace + Grimo evidence tables | Pollack trace records transitions; Grimo tables preserve user-facing evidence. |
| Crash recovery | `CheckpointingStepRunner` | Completed steps can be skipped; failed steps require explicit recovery decision. |

## Task Workflow Shape

```mermaid
flowchart TB
  Task["Task"] --> State["Task State Projection"]
  Task --> Run["Workflow Run"]
  Task --> Copy["Task Workflow - immutable copy"]

  State --> Backlog["BACKLOG"]
  State --> DefiningState["DEFINING"]
  State --> ReadyState["READY"]
  State --> RunningState["RUNNING"]
  State --> ReviewState["REVIEW - waiting review"]
  State --> DoneState["DONE"]

  Run --> RunStatus["Workflow Run Status"]
  RunStatus --> NotStarted["not_started"]
  RunStatus --> RunningRun["running"]
  RunStatus --> WaitingDispatch["waiting_dispatch"]
  RunStatus --> BlockedRun["blocked"]
  RunStatus --> WaitingReview["waiting_review"]
  RunStatus --> ReleasingRun["releasing"]
  RunStatus --> CompletedRun["completed"]

  Copy --> MainSteps["Main Workflow Steps"]
  MainSteps --> DefinitionSteps["Discuss / Explore / Prototype / Spec / Usage / Tkt"]
  MainSteps --> ExecutionSteps["Dev / Unit-test / Integration-test / E2E-test"]
  MainSteps --> ReleaseStep["release"]

  MainSteps --> QualityLoop["QualityLoopWorkflow on each step"]
  QualityLoop --> StepReview["Review"]
  StepReview --> Rating["Rating"]
  Rating --> Gate{"Gate pass?"}
  Gate -- "no" --> Fix["Fix"]
  Fix --> StepReview
  Gate -- "yes" --> StepDone["step output accepted"]

  ReviewState --> HumanDecision["HumanReviewDecisionGate"]
  HumanDecision -->|reject| DefiningState
  HumanDecision -->|approve| ReleasingRun
  ReleasingRun --> ReleaseStep
  ReleaseStep --> DoneState
```

## Top-Level Workflow

這張圖是 Pollack workflow 角度；它不等於看板 state machine。Task State 和 Workflow Run Status 是 workflow / gate output 的產品投影。

```mermaid
flowchart TD
  Input["Work input: chat, local task, external item"] --> Intake["TaskIntake"]
  Intake --> Backlog["Task state: BACKLOG"]

  Backlog --> FirstChat{"First Chat opens or defining starts?"}
  FirstChat -- "no" --> Backlog
  FirstChat -- "yes" --> Definition["DefinitionWorkflow"]
  Definition --> Defining["Task state: DEFINING<br/>Run status: running"]

  Definition --> ReadyGate{"Ready Gate accepted?"}
  ReadyGate -- "no: clarify" --> Definition
  ReadyGate -- "no: repair needed" --> NeedsHuman["Task state unchanged<br/>repair reason: NEEDS_HUMAN"]
  ReadyGate -- "yes" --> Ready["Task state: READY<br/>Run status: waiting_dispatch"]

  Ready --> DispatchStart{"User starts single task or dispatch window?"}
  DispatchStart -- "no" --> Ready
  DispatchStart -- "yes" --> Dispatch["DispatchClaimWorkflow"]
  Dispatch --> PreflightGate{"Preflight pass?"}
  PreflightGate -- "no" --> NeedsHuman
  PreflightGate -- "yes" --> Execution["ExecutionWorkflow"]

  Execution --> Running["Task state: RUNNING<br/>Run status: running"]
  Execution --> ReviewMaterials["Build Review Materials"]
  ReviewMaterials --> WaitingReview["Task state: REVIEW<br/>Run status: waiting_review"]

  WaitingReview --> HumanGate{"Human review decision"}
  HumanGate -- "reject" --> Definition
  HumanGate -- "approve" --> Release["ReleaseWorkflow<br/>Run status: releasing"]
  Release --> Done["Task state: DONE<br/>Run status: completed"]
  Done --> Learning["Optional LearningProposalWorkflow"]
  Learning --> Archive["Archive done evidence"]
```

## Sub-Workflow: Task Intake

Goal: 把入口內容保存成 Project-owned Task 或附加到既有 Task，不啟動正式 execution。

```mermaid
flowchart TD
  Input["Raw input: chat message, CLI request, issue"] --> Classify["Classify work intent"]
  Classify --> Existing{"Matches existing Task?"}
  Existing -- "yes" --> Attach["Append to Task Conversation Thread"]
  Existing -- "no" --> Create["Create Task in BACKLOG"]
  Attach --> Preview["Update conversation preview and open questions"]
  Create --> Preview
  Preview --> Output["Task intake output"]
```

Pollack shape:

```java
Workflow.define("task-intake")
    .step(classifyWorkIntent)
    .branch(existingTaskFound)
        .then(appendToConversation)
        .otherwise(createBacklogTask)
    .then(updateConversationPreview)
    .then(emitTaskIntakeOutput);
```

## Sub-Workflow: Definition

Goal: 形成可由人類確認的 Definition Package。每個主要 step 都包 `QualityLoopWorkflow`。

```mermaid
flowchart LR
  Discuss["Discuss"] --> Q1["QualityLoop"]
  Q1 --> Explore["Explore"]
  Explore --> Q2["QualityLoop"]
  Q2 --> PrototypeDecision{"Prototype needed?"}
  PrototypeDecision -- "yes" --> Prototype["Prototype"]
  Prototype --> Q3["QualityLoop"]
  Q3 --> Spec["Spec"]
  PrototypeDecision -- "no" --> Spec
  Spec --> Q4["QualityLoop"]
  Q4 --> Usage["Usage"]
  Usage --> Q5["QualityLoop"]
  Q5 --> Tkt["Tkt"]
  Tkt --> Q6["QualityLoop"]
  Q6 --> Package["Definition Package"]
```

Pollack shape:

```java
Workflow.define("definition")
    .step(discuss)
    .then(qualityLoop("discuss"))
    .then(explore)
    .then(qualityLoop("explore"))
    .branch(prototypeNeeded)
        .then(prototype)
        .then(qualityLoop("prototype"))
        .otherwise(skipPrototype)
    .then(spec)
    .then(qualityLoop("spec"))
    .then(usage)
    .then(qualityLoop("usage"))
    .then(tkt)
    .then(qualityLoop("tkt"))
    .then(buildDefinitionPackage);
```

## Sub-Workflow: Quality Loop

Goal: 對任一主要 step 的 output 做 review、rating、gate、fix，直到 Gate 通過或達到明確停止條件。

```mermaid
flowchart TD
  StepOutput["Main step output"] --> Review["Review"]
  Review --> Rating["Rating"]
  Rating --> Score{"Gate pass?"}
  Score -- "yes" --> Pass["Pass output to next main step"]
  Score -- "no" --> Stop{"Stop condition reached?"}
  Stop -- "yes" --> Block["Emit blocked evidence / NEEDS_HUMAN repair reason"]
  Stop -- "no" --> Fix["Fix"]
  Fix --> Review
```

Pollack shape:

```java
Workflow.define("quality-loop")
    .repeatUntilOutput(qualityPassedOrStopped)
        .step(review)
        .then(rating)
        .then(routeGateResult)
        .then(fixIfNeeded)
    .end();
```

Implementation note: Pollack examples show both `repeatUntilOutput` and `gate` primitives. Grimo should keep the rating parser deterministic where possible, and store raw review/rating/fix evidence in Grimo tables when it affects user-facing Review Materials or recovery.

## Sub-Workflow: Dispatch Claim

Goal: `READY` 不等於自動執行；只有使用者啟動 dispatch window 或單一 Task 後，才進 preflight 與 claim。

```mermaid
flowchart TD
  Ready["READY Task"] --> Start{"User started execution?"}
  Start -- "no" --> StayReady["Stay READY"]
  Start -- "single task" --> Preflight["Preflight"]
  Start -- "dispatch window" --> Capacity{"Concurrency slot available?"}
  Capacity -- "no" --> Wait["Wait in READY queue"]
  Capacity -- "yes" --> Preflight
  Preflight --> Runtime{"Profile, dependency, runtime, permission pass?"}
  Runtime -- "no" --> NeedsHuman["READY + NEEDS_HUMAN repair reason"]
  Runtime -- "yes" --> Claim["Create Agent Claim"]
  Claim --> Workspace["Prepare worktree / sandbox / runId"]
  Workspace --> Running["Task state: RUNNING<br/>Run status: running"]
```

Pollack shape:

```java
Workflow.define("dispatch-claim")
    .gate(userExecutionWindowGate)
        .onFail(stayReady)
        .onPass(checkCapacity)
    .then(preflight)
    .gate(preflightGate)
        .onPass(createAgentClaim)
        .onFail(markNeedsHuman)
    .end();
```

## Sub-Workflow: Execution

Goal: 在 `RUNNING` 裡完成 Dev、Unit-test、Integration-test 和 E2E-test evidence，並建立 Review Materials。

```mermaid
flowchart TD
  Claim["Agent Claim context"] --> Dev["Dev"]
  Dev --> DevQL["QualityLoop"]
  DevQL --> Unit["Unit-test"]
  Unit --> UnitQL["QualityLoop"]
  UnitQL --> Integration["Integration-test"]
  Integration --> IntegrationQL["QualityLoop"]
  IntegrationQL --> E2E["E2E-test"]
  E2E --> E2EQL["QualityLoop"]
  E2EQL --> Materials["Review Materials"]
```

Pollack shape:

```java
Workflow.define("execution")
    .step(devAgentStep)
    .then(qualityLoop("dev"))
    .then(runUnitTests)
    .then(qualityLoop("unit-test"))
    .then(runIntegrationTests)
    .then(qualityLoop("integration-test"))
    .then(runE2eTests)
    .then(qualityLoop("e2e-test"))
    .then(buildReviewMaterials);
```

## Gate: Human Review Decision And Release

Goal: 人類在 `REVIEW` 檢視 Review Materials 後決定 approve / reject。Approve 後才執行 `release`；release 完成後才進 `DONE`。

```mermaid
flowchart TD
  Materials["Review Materials"] --> ReviewState["Task state: REVIEW<br/>Run status: waiting_review"]
  ReviewState --> HumanGate{"Human review decision"}
  HumanGate -- "reject" --> RedefinePath["Return to DefinitionWorkflow"]
  HumanGate -- "approve" --> Release["release step: merge, cleanup, delivery summary, short retro if needed"]
  Release --> ReleaseQL["QualityLoop for release"]
  ReleaseQL --> Done["Task state: DONE<br/>Run status: completed"]
```

Pollack shape:

```java
Workflow.define("review-release")
    .gate(humanReviewDecisionGate)
        .onFail(returnToDefinition)
        .onPass(release)
    .then(qualityLoop("release"))
    .then(markDone);
```

## Sub-Workflow: Learning Proposal

Goal: 從 Done task evidence 提出 skill / recipe 改善，但不自動套用。

```mermaid
flowchart TD
  DoneEvidence["Done task evidence"] --> Detect["Detect repeatable improvement"]
  Detect --> WorthIt{"Worth proposing?"}
  WorthIt -- "no" --> Archive["Archive evidence only"]
  WorthIt -- "yes" --> Proposal["Create Learning Proposal"]
  Proposal --> HumanDecision{"Human approves proposal?"}
  HumanDecision -- "no" --> Archive
  HumanDecision -- "yes" --> Backlog["Create follow-up Task in BACKLOG"]
```

Pollack shape:

```java
Workflow.define("learning-proposal")
    .step(detectRepeatableImprovement)
    .gate(proposalWorthItGate)
        .onFail(archiveEvidenceOnly)
        .onPass(createLearningProposal)
    .end();
```

## Context Keys

Minimum typed context keys for the MVP definition:

| Context key | Type | Producer | Consumer |
| --- | --- | --- | --- |
| `project_id` | `ProjectId` | task intake | all steps |
| `task_id` | `TaskId` | task intake | all steps |
| `task_state` | `TaskState` | gates / projection steps | UI projection |
| `workflow_run_status` | `WorkflowRunStatus` | workflow executor / gates | task detail, recovery, dispatch |
| `workflow_recipe_id` | `WorkflowRecipeId` | Project settings | workflow executor |
| `run_id` | `String` | dispatch claim | checkpoint / trace |
| `agent_profile_id` | `AgentProfileId` | ready gate / dispatcher | dispatch / execution |
| `definition_package` | `DefinitionPackage` | definition workflow | ready gate / review |
| `current_step` | `WorkflowStepKey` | workflow executor | task detail / workflowSummary |
| `quality_score` | `QualityScore` | rating step | quality gate |
| `review_findings` | `ReviewFindings` | Quality Loop review step | fix / materials |
| `fix_history` | `FixHistory` | Quality Loop fix step | review / materials |
| `acceptance_evidence` | `AcceptanceEvidence` | execution workflow | human review |
| `review_materials` | `ReviewMaterials` | execution workflow | human review |
| `human_review_decision` | `APPROVE` / `REJECT` | human review gate | release / redefine path |
| `release_evidence` | `ReleaseEvidence` | release workflow | done / learning / follow-up task |

## Execution Runner Decision

```mermaid
flowchart TD
  WorkflowDef["Same Workflow definition"] --> Runner{"StepRunner"}
  Runner --> Local["LocalStepRunner: short deterministic operations"]
  Runner --> Checkpoint["CheckpointingStepRunner: MVP durable local run"]
  Runner --> Temporal["TemporalStepRunner: future distributed execution"]

  Checkpoint --> SQLite["SQLite via workflow-batch + Grimo tables"]
  Checkpoint --> Resume["Resume by runId; completed steps skipped"]
  Checkpoint --> Failed["FAILED steps require operator diagnosis before reset"]
```

MVP decision: use `CheckpointingStepRunner` for Task execution runs that need durability. Keep `LocalStepRunner` for short deterministic operations where replay is cheap. Do not design MVP around Temporal.

## Open Design Questions

1. How long should a `QualityLoopWorkflow` run before emitting blocked evidence or a `NEEDS_HUMAN` repair reason?
2. Should `Discuss` be one interactive sub-workflow instance, or multiple resumable chat turns under the same step checkpoint?
3. Which `Workflow Run Status` values are persisted source-of-truth rows, and which are derived UI projections?
4. Which workflow evidence belongs in Pollack trace only, and which must be duplicated into Grimo domain tables for user-facing review?
5. How should external Codex / Claude Code workers map their own session IDs to Grimo `run_id` and `Agent Claim`?
