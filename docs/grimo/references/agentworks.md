# Pollack AgentWorks Reference Notes

**Purpose:** Preserve research context behind the choice to design Grimo around Pollack AI Lab AgentWorks. PRD should only keep the product-level conclusions; technical selection evidence belongs in ADRs.

## Product Takeaways For Grimo

1. Grimo presents a Task workbench to users, while internal orchestration can be Pollack Agent Workflow.
2. The main SDD phases are workflow steps: Discuss, Explore, Prototype, Spec, Usage, Tkt, Dev, Auto-Review, Unit-test fe/be, Integration-test, E2E-test and Release; human approval is the REVIEW product gate after RUNNING evidence is complete.
3. Each main step runs an automatic Quality Loop subworkflow: sub-Review -> sub-Rating -> sub-Fix until the step passes its quality threshold.
4. Discuss remains an interactive chat/research step; users do not need to understand workflow internals.
5. AgentWorks components should be adopted by role:
   - Agent Workflow for orchestration, gates, step runners, checkpointing and trace.
   - Agent Client for CLI agent adapters.
   - Agent Sandbox for execution isolation.
   - Agent Judge for deterministic or LLM-backed review/rating.
   - Agent Journal / Agent Memory only where their file-backed model fits.
6. SQLite is the MVP local persistence path for Grimo-owned state and Pollack `workflow-batch` checkpoint / trace POC. Detailed storage decisions live in ADR-001.

## Source Notes

### Agent Workflow

Source: <https://lab.pollack.ai/projects/agent-workflow>

Key points:

- Agent Workflow provides typed workflow composition, steps, gates, tracing and portable runtime concepts.
- This maps well to Grimo's need for workflow-controlled SDD and quality-gated progress.

### Agent Workflow Durability

Source: <https://lab.pollack.ai/docs/agent-workflow/durability>

Key points:

- `CheckpointingStepRunner` uses JDBC checkpointing.
- `TemporalStepRunner` uses Temporal infrastructure.

Grimo implication:

- Local SQLite path maps to `CheckpointingStepRunner`, not to Temporal.
- Temporal remains a future scale-out path.

### Workflow Examples

Source: <https://lab.pollack.ai/docs/agent-workflow/examples>

Validated locally:

- sequential workflow
- branch
- gate
- repeatUntilOutput
- parallel
- onError recovery
- sub-workflow composition

Test file:

- `backend/src/test/java/io/github/samzhu/grimo/poc/PollackAgentWorkflowDslPocTests.java`

### AgentWorks BOM

Source: <https://lab.pollack.ai/projects/agentworks-bom>

Key points:

- `io.github.markpollack:agentworks-bom:1.0.12` is the dependency-management entry point.
- Published BOM POM and website can differ for some artifacts; Grimo should verify actual Gradle resolution before relying on versions.
- Backend defaults to BOM-managed versions. Explicit versions are only used when the published BOM points to an unavailable artifact.

### Agent Client

Source: <https://lab.pollack.ai/projects/agent-client>

Key points:

- Agent Client is a general Java agent engineering library and does not require Spring AI as a product runtime commitment.
- Spring Boot / Spring AI integration can be wiring convenience rather than product core.

Grimo implication:

- Grimo should own its Task, Session, Recipe, Execution and Evidence model.
- Provider libraries remain adapters, not product source of truth.

## ADR Links

- `docs/grimo/adr/ADR-001-pollack-workflow-sqlite-poc.md` — Pollack AgentWorks SQLite selection and verification.

## Workflow Definition Links

- `docs/grimo/references/grimo-agent-workflow-definition.md` — Grimo workflow / sub-workflow diagrams mapped to Pollack Agent Workflow concepts.
