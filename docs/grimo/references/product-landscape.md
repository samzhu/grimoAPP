# Product Landscape Reference Notes

**Purpose:** Preserve market and product-reference context without keeping research detail in the PRD.

## Product Takeaways For Grimo

1. The market is moving from "chat with an AI" toward assigning work items to AI agents.
2. Grimo should keep a Task workbench as the user-facing surface, not expose low-level workflow engine concepts as the main product language.
3. External issue systems and agent providers should become connectors/adapters, while Grimo owns local Task, Workflow Evidence and Review Materials.
4. Grimo should support both actively launched local subagents and future external workers that claim Grimo Ready Tasks.
5. Grimo should present simplified Task states in list/board views, while keeping SDD workflow steps and Quality Loop evidence in Task detail.
6. Grimo should treat chat as a Task-forming surface, not the main product object; the product object is the Project/Task and its reviewable evidence.
7. Grimo's differentiator should be local-first evidence ownership plus quality-gated workflow, not generic AI coworker branding.

## 2026 Trend Analysis

As of 2026-05-25, AI coding-agent products are converging around five product patterns:

1. **Issue/task board as the control surface.** Symphony, Multica, Helio and Hermes-style dashboards all make the work item the object humans manage. Users track status, blockers, assignee, review and merge readiness; they do not supervise every low-level agent step.
2. **Agents become assignees or teammates.** Multica and Helio make agents appear beside humans in the same task/status surfaces. This helps users understand responsibility, but risks turning thin runtime profiles into heavy fictional coworkers.
3. **Local/runtime separation is now expected.** Multica and Nezha both lean into local execution: the product coordinates while Claude Code, Codex and other CLIs run on the user's machine. This supports Grimo's local-first direction.
4. **Evidence beats transcript.** Symphony emphasizes review packets, CI, videos for user-facing changes, and review handoff. gnhf preserves branch, commits, logs, token totals and review commands. The winning interaction is "show me why I can trust this," not "show me a chat log."
5. **Rigid workflow engines are being softened into goal-driven execution.** Symphony explicitly warns that treating agents as rigid state-machine nodes becomes limiting; the product state can be simple, while agents receive goals, tools and context to solve the work.

## Source Notes

### Symphony

Sources:

- <https://openai.com/zh-Hant/index/open-source-codex-orchestration-symphony/>

Product signal:

- OpenAI describes a shift from supervising Codex sessions to assigning work from an issue tracker.
- Symphony turns Linear-like task states into a control plane, with one workspace per issue and humans reviewing results.
- OpenAI notes that large-scale agent work needs guardrails, skills, tests, CI, and better definitions of "done" rather than manual steering.
- Symphony also warns against over-constraining agents as rigid state-machine nodes; agents should receive goals, tools and context.

Grimo implication:

- Grimo should use Task status as the main user surface, not raw session management.
- Grimo's workflow should produce Review Materials and quality evidence before asking humans to approve.
- Grimo should keep internal SDD/Quality Loop machinery visible as evidence, but not force it into the primary board columns.
- Grimo should let agents solve the goal inside guardrails instead of making every internal step a user-facing status.
- Symphony has a repo-owned `WORKFLOW.md` contract for prompt, runtime settings, hooks, tracker config and quality-bar-like status movement, but it does not formalize Grimo's `Task Type -> Workflow Recipe -> Quality Bar` product abstraction.
- Grimo should treat Symphony as a strong reference for coding workflow contracts, then productize it into multiple task types over time.

### Multica

Sources:

- <https://github.com/multica-ai/multica>
- <https://multica.ai/docs>
- <https://multica.ai/docs/how-multica-works>
- <https://multica.ai/docs/issues>
- <https://multica.ai/docs/agents>
- <https://multica.ai/docs/tasks>

Product signal:

- Multica positions itself as humans and agents in one workspace.
- Issues are the assignable unit of work; agents can be assignees, comment, be mentioned, lead projects and create follow-up issues.
- A local daemon owns execution on the user's machine, detects installed coding tools, polls for tasks, sends heartbeats and reports task status.
- Multica separates user-level issues from execution tasks: one issue can create multiple queued/running/completed task runs.
- User-visible issue statuses are simple: backlog, todo, in_progress, in_review, done, blocked, cancelled.

Grimo implication:

- Dispatcher and Agent Claim are important product concepts between READY and execution.
- Grimo should keep user-level Task separate from run-level Task Execution / Workflow Step Execution.
- Grimo should expose local runtime health and blocked reasons clearly.
- Grimo should not copy Multica's "any status can jump anywhere" rule if Grimo's value is quality-gated workflow.

### gnhf

Source: <https://github.com/kunchenguid/gnhf>

Product signal:

- gnhf runs long-lived autonomous loops toward an objective.
- Each successful iteration becomes a small committed and documented change; failed iterations roll back except where repair is needed.
- Each run ends with a permanent summary covering branch, iterations, tokens, diff stats, notes/log paths and review commands.
- It is agent-agnostic across Claude Code, Codex, Rovo Dev, OpenCode, GitHub Copilot CLI, Pi and ACP targets.

Grimo implication:

- Grimo should persist branch, run log, review command and diff-stat evidence as first-class Review Materials.
- Long-running autonomous work needs clean iteration boundaries and recoverable checkpoints.
- Agent/provider abstraction matters, but Grimo should own the work/evidence model.

### Helio

Source: <https://www.helio.im/>

Product signal:

- Helio positions AI colleagues as working in the same channels, taking the same tickets and shipping the same work as humans.
- Helio's task surface emphasizes same statuses, same assignees, review handoff and visible responsibility.
- Its platform story spans channels, tasks, coding sessions, AI teammates, email and meetings.

Grimo implication:

- "Responsibility needs to be legible" is a useful product principle: users need to know who drafted, who approved and what is blocked.
- Grimo should borrow the shared task/status/review model, but avoid over-expanding into general workforce, meetings, email or marketing agents during MVP.
- Grimo's Agent Profile should remain thin and work-focused, not become a fictional full employee persona.

### Nezha

Source: <https://github.com/hanshuaikang/nezha>

Product signal:

- Nezha is a desktop AI coding app for running Claude Code and Codex across multiple projects.
- Its product value is unifying task lifecycle tracking, terminal/session output, code browsing and Git workflow in one interface.
- It highlights the shift from human-centered IDEs to parallel AI-assisted programming, where human attention becomes the scarce resource.

Grimo implication:

- Grimo should respect the local desktop/operator experience: fast project switching, terminal/session visibility and Git context matter.
- Nezha validates the local app direction, but Grimo should differentiate on workflow evidence, Definition Package, Review Materials and quality gates rather than being a lightweight IDE.

### Linear Agents

Source: <https://linear.app/agents>

Product signal:

- Agents are treated as workspace contributors that can be assigned or mentioned.
- The issue/work item remains the shared coordination object.

Grimo implication:

- Agent Profile should be human-legible and thin.
- Task assignment should be about work ownership, not provider choice.

### GitHub Third-Party Agents

Source: <https://docs.github.com/en/copilot/concepts/agents/about-third-party-agents>

Product signal:

- Claude, Codex and Copilot coding agents can be initiated from Issues, PR comments, Agents tab and VS Code.

Grimo implication:

- Grimo should treat GitHub as a future work-item connector, not as the only task system.

### Claude Code

Source: <https://code.claude.com/docs/en/features-overview>

Product signal:

- Skills, subagents, hooks, MCP and plugins are becoming native extension surfaces for coding agents.

Grimo implication:

- Grimo should provision skills/MCP per Task and Workflow Recipe rather than hard-code provider behavior.

### Codex CLI

Source: <https://developers.openai.com/codex/cli>

Product signal:

- Local agents have their own approval, sandbox and MCP model.

Grimo implication:

- Provider-specific runtime rules belong in adapters and capability checks.

### Gemini CLI / Antigravity CLI Transition

Source: <https://developers.googleblog.com/en/an-important-update-transitioning-gemini-cli-to-antigravity-cli/>

Product signal:

- Provider CLIs can change quickly.

Grimo implication:

- Grimo core must not depend on a single provider lifecycle or namespace.

### Local Materials

Sources:

- Hermes Agent dashboard screenshot material.

Product signal:

- Task board, dispatcher nudge, profile lanes, dependencies, events, worker log and run history are useful Task workbench affordances.

Grimo implication:

- UI should keep the Task workbench surface while internal workflow state remains detailed and traceable.
