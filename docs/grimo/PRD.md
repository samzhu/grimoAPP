# Grimo — Product Requirements Document

**Status:** v0.1 DRAFT · **Owner:** samzhu · **Date:** 2026-04-16
**Next handoff:** `/planning-project`

---

## 1. Problem statement

Developers increasingly work with **multiple CLI AI agents** (Claude Code,
Codex CLI, Gemini CLI). In practice that produces four pains:

1. **Cost is uncontrollable.** Every task — trivial reformats, one-line
   questions, strategic refactors — runs against the same pro-tier model.
   Simple work consumes expensive tokens.
2. **Every answer is one model's opinion.** Code review, architecture
   decisions, and debugging benefit from contrasting viewpoints. A single
   CLI gives a single blind spot.
3. **Management is fragmented.** Each CLI owns its own skill directory,
   MCP server config, session layout, and allowlist syntax. A user who
   wants "this skill, on all three CLIs" today copies files by hand.
4. **Approval fatigue (核准疲勞).** Modern CLI agents prompt the human for
   approval on every file edit, shell exec, and network call. During a
   real refactor that's dozens of Y/N confirmations in a row; the human
   becomes a rubber stamp and safety devolves into noise. Turning approvals
   off trades safety for ergonomics — a bad choice. The right answer is to
   **move the danger into a box**, not to remove the check.

These pains compound: users standardize on whatever CLI they tried first
and pay for it — in tokens, in quality, and in attention — forever.

## 2. Solution — *Grimo*, a user harness*

> *Harness = the control, contracts, and state surrounding a model. An
> **agent** is `Model + Harness`. A **user harness** is the one a team
> assembles around off-the-shelf agents — as opposed to the builder
> harness that ships inside each CLI.* (See `glossary.md`.)

Grimo is a local-first **user harness** for CLI AI agents, built on the
Spring ecosystem. It sits in front of Claude Code / Codex / Gemini CLI
and gives the user:

- **One conversation, any CLI.** A `main-agent` role (the conversational
  entry point) whose underlying CLI can be switched mid-session without
  losing context.
- **Delegated execution in a box.** Write-heavy work is delegated to
  `sub-agents` running in Docker with a per-task git worktree. Main-agent
  itself is **read-only** (Read / Glob / Grep / WebFetch / WebSearch).
- **Cost-aware routing.** A router picks the cheapest CLI/model that fits
  the task class — with user override always available.
- **Multi-perspective review.** The *jury* command dispatches a single
  review task to N CLIs in parallel and aggregates consensus + dissent.
- **Unified skills, memory, and sessions.** Skills and memory live under
  `~/.grimo/` and are projected into every CLI's native surface.
- **Self-evolution.** After N successful runs of a recurring pattern,
  Grimo distills a draft `SKILL.md` for user review. Memory is curated
  by the agent itself via `AutoMemoryTools`.

## 3. Positioning

| Grimo **is** | Grimo **is not** |
| --- | --- |
| A harness assembled by the user around existing CLI agents | A new AI model, CLI, or chat client |
| A thin process-supervising orchestrator with Spring-Modulith boundaries | A Python agent framework or a LangChain replacement |
| Local-first, single-user developer tool | A multi-tenant SaaS or a consumer messaging bot |
| Opinionated about cost, isolation, and perspective | An unopinionated "do anything" shell |

## 4. Users & scenarios

### Target users

- Developers who already have **≥ 1 CLI agent installed** (Claude Code,
  Codex CLI, or Gemini CLI) and are comfortable on the terminal and with
  Docker.
- Want to **save money** — cheap model for simple work, expensive model
  reserved for strategic work.
- Want **multi-perspective** — several agents reviewing / researching a
  single artifact in parallel.
- Do **not** want to manage several CLI windows by hand.
- Pay for **subscription plans** (Claude Max, ChatGPT Plus / Codex,
  Gemini Advanced) and expect Grimo to defer to each CLI's native OAuth
  flow — **no API keys required**. (See D19.)

**Primary user persona:** a senior developer who reads English PRDs
comfortably and writes briefs in zh-TW; treats local-first tooling as a
privacy / latency win.

Representative scenarios:

- "Trivial chore" — rename a variable, format a timestamp, grep the
  codebase. Should run on the cheapest capable CLI.
- "Scoped refactor" — rewrite a service class, update its tests, run the
  suite. Should run in an isolated worktree with full tool access.
- "Architecture review" — ask three CLIs to critique a design doc and
  show me where they agree and where they diverge.
- "Cross-CLI continuity" — started a conversation on `claude`, ran out
  of quota, want to finish on `codex` without re-explaining context.
- "Install only what I have" — user only has `claude` on PATH; starting
  Grimo must succeed and `codex`/`gemini` features must fail **at call
  time**, not at boot.

## 5. Core principles (with rationale)

### P1 — Harness, not agent, not framework (*Wrap, don't replace · 包裝 CLI agent，不取代它們*)

Grimo does not invent a new agent. It does not replace LangChain. It
applies the harness-engineering canon (control + contracts + state,
guides + sensors, computational + inferential controls) to CLI agents
that already exist.

*Why:* the Agent's core value lives in the LLM + tool loop, and those
loops already work in Claude Code / Codex CLI / Gemini CLI. Rebuilding
them would be expensive and pointless. The value is in the assembly
around them.

### P2 — Read–write asymmetry · *Planning vs execution · 規劃與執行分離 · Isolation replaces control · 隔離取代管控*

Main-agent is **read-only** (plans, reads, searches, asks). Writes only
happen through **sub-agents** running in Docker with a mounted worktree,
with their host permissions intentionally YOLO *inside the box*.

*Why — three converging reasons:*
1. **Approval fatigue** (pain #4). When every write prompts a Y/N, the
   human rubber-stamps or disables the check. A sandbox removes the
   cost-benefit case for per-action prompts: the blast radius is already
   bounded by the box, so approval can move to a single diff-level
   checkpoint at the end. **Isolation replaces fine-grained permission
   control** — complex allowlists are expensive to maintain and easy to
   get wrong; a bind-mounted worktree inside Docker is cheap and
   unambiguous.
2. **Planning / execution split.** Planning benefits from strong
   reasoning (expensive models, read-only, long context). Execution
   benefits from precision and speed (cheaper models, write-capable,
   short-lived). Separating them lets each use the model it should.
3. **Safety + cost structural controls.** Read ops are cheap and
   non-destructive; they suit a long-running conversational context.
   Writes need isolation so a misbehaving agent cannot corrupt the host
   repo. This matches the harness-engineering "computational controls"
   principle — make the dangerous path structurally harder to take.

### P3 — Grimo owns the session

The session is a first-class domain concept **owned by Grimo**, stored
as event-sourced `Message`s (via Spring AI's `SessionMemoryAdvisor`).
It is not owned by any single CLI.

*Why:* agent-client 0.12.2's `AgentSession` is currently Claude-only;
CLI-portable conversation cannot be delegated to it. Owning the session
lets Grimo compact+replay history when the CLI switches.

### P4 — Modular monolith with event-driven seams

Bounded contexts are Spring Modulith `@ApplicationModule`s.
Cross-module communication is via `ApplicationEventPublisher` +
`@ApplicationModuleListener`. The domain core stays Spring-annotation-
free (plain records and interfaces).

*Why:* hexagonal + modular boundaries give us testable seams and cheap
future extraction (e.g., running a sub-agent pool out-of-process) while
keeping v1 a single deployable.

### P5 — Fail soft on missing CLIs

Grimo MUST boot successfully when any subset of `claude` / `codex` /
`gemini` is missing from `PATH`. Per-provider `agent-*` starters are
gated behind `@ConditionalOnProperty` + explicit opt-in. Missing CLI is
detected at **call time**, not refresh time.

*Why:* the #1 "new user" failure mode on multi-CLI tools is boot-time
explosion when one binary isn't installed yet. Never let that happen.

### P6 — Self-evolution is layered, not magical

v1 self-evolution = `AutoMemoryTools` (agent-curated memory) + skill
distillation (after successful pattern repetition, Grimo proposes a
draft SKILL.md for user review). No autonomous rule-tuning in MVP.

*Why:* closed-loop self-improvement (à la Hermes) is real but the
proposal→review→commit gate keeps the human in control and prevents
drift. Richer forms (auto-tuned guides, RL-style sensors) are deferred.

### P7 — Local-first

All state lives under `~/.grimo/`. No cloud backend, no account system,
no telemetry upload in MVP.

*Why:* developer tools live on the laptop. Local-first removes a large
class of privacy/compliance questions and keeps latency minimal.

### P8 — Unified management, native distribution · *統一管理，原生分發*

Users configure a skill / MCP server / memory / guide **once** in
Grimo's unified format. At invocation time, Grimo projects that config
into each CLI's native surface (Claude Code's `.claude/skills/`,
Gemini's own skill dir, Codex's config, etc.).

*Why:* using N agents should not cost N× management. The unified
authoring format is the thing that scales; native distribution is what
keeps the CLIs themselves happy.

### P9 — Cross-agent resilience · *跨 agent 即韌性*

Grimo's multi-CLI support is not a feature — it *is* the resilience
story. Any provider outage, quota exhaustion, pricing change, or
deprecation degrades Grimo to "run on the other CLIs until this one is
healthy again", not to "Grimo is down".

*Why:* locking a team to a single provider is a vendor-lock-in and
availability risk. The multi-CLI architecture we already need for
cost-routing and multi-perspective review doubles as the best possible
backup plan — no cold-standby infra to maintain.

### P10 — Subscription-native authentication · *訂閱帳號原生操作*

Users who pay for a Claude Max / ChatGPT Plus / Gemini Advanced
subscription must be able to drive Grimo end-to-end without creating,
managing, or entering any API key. Grimo defers all authentication to
the underlying CLI's own OAuth/login flow.

*Why:* our target users already pay a flat monthly subscription. Asking
them to also obtain and store API tokens (which bill per-token on top of
the subscription) would be a double-charge and a UX cliff. Using the CLI
directly inherits whatever auth the user set up with `claude login` /
`gemini auth` / `codex login`.

## 6. SBE acceptance criteria

*Concrete, executable examples. If any of these doesn't pass, MVP isn't
shipped.*

### AC1 — Cost routing picks the cheap model for trivial work

```
Given  a user prompt "format this ISO-8601 timestamp as local time"
When   the main-agent receives it
Then   the router classifies the task as "trivial/format"
And    dispatches to Gemini 2.5 Flash (not Claude Opus)
And    the session-cost panel shows delta ≤ $0.0005 for that turn.
```

### AC2 — Cost routing escalates for strategic work

```
Given  a user prompt "refactor OrderService to hexagonal architecture
       and update its Spring Modulith module declarations"
When   the main-agent receives it
Then   the router classifies the task as "strategic/refactor"
And    dispatches to Claude Opus 4.6 or Claude Sonnet 4.6
And    the session-cost panel logs the turn cost
And    a user-visible justification string is attached to the turn
       ("escalated: structural refactor, multi-file").
```

### AC3 — CLI switch preserves conversational context

```
Given  a user has held 5 turns of conversation with main-agent = claude
And    discussed an entity called "OrderService" in those turns
When   the user issues "/grimo switch codex"
And    then sends "can you also add a cancellation endpoint to it?"
Then   Grimo compacts the 5 prior turns into a bootstrap prompt
And    delivers <bootstrap>+<new user message> to codex in one call
And    codex's reply references OrderService by name without being told again.
```

### AC4 — Main-agent cannot write

```
Given  main-agent is running (any CLI)
When   main-agent attempts to invoke a write tool (Edit / Write / Bash
       with a mutation)
Then   the harness intercepts the call
And    returns a structured error "main-agent is read-only; delegate to sub-agent"
And    the host filesystem is unmodified
And    the error is visible to the user in the conversation log.
```

### AC5 — Sub-agent writes only inside its worktree

```
Given  main-agent delegates a task T1 that edits src/.../Foo.java
When   Grimo spawns sub-agent-T1 in a Docker sandbox
And    mounts the per-task worktree at /work inside the container
Then   sub-agent-T1 can Read/Write/Bash freely under /work
And    cannot read or write any host path outside /work
And    a sibling sub-agent T2 cannot see T1's worktree
And    when T1 completes, Grimo surfaces the diff for user review before merge.
```

### AC6 — Fail-soft boot on missing CLIs

```
Given  the host has `claude` installed but NOT `codex` or `gemini`
When   `./gradlew bootRun` starts Grimo
Then   the application context refreshes successfully within N seconds
And    the Web UI is reachable at http://localhost:8080/grimo
And    `main-agent = claude` is available
And    attempting `/grimo switch codex` returns
       "codex CLI not detected; install or set grimo.cli.codex.enabled=false"
       — no stack trace, no boot failure.
```

### AC7 — Jury review aggregates N CLIs in parallel

```
Given  a user runs "/grimo jury review PRD.md --n=3"
And    claude, codex, and gemini are all configured
When   Grimo dispatches three parallel review sub-agents
Then   each review runs in its own Docker sandbox with a read-only
       mount of the repo
And    within the same turn, Grimo returns a markdown report containing
       sections "## Consensus" and "## Divergence" and one "##
       <provider>" section per reviewer
And    per-provider token costs are shown.
```

### AC8 — Skill distillation proposes a draft after repetition

```
Given  over the last N sessions the user has asked main-agent to
       "scaffold a Spring Boot health endpoint" three times with
       similar outcomes
When   the skill-distiller job runs (nightly or on-demand)
Then   a draft `~/.grimo/skills/scaffold-health-endpoint/SKILL.md` is
       proposed in a "pending" state
And    the Web UI shows a "new skill draft" badge
And    the user can accept/edit/reject; accepted skills appear in every
       CLI adapter's tool listing on the next turn.
```

### AC9 — Memory is agent-curated and indexed

```
Given  during a conversation the user states "I always deploy to
       europe-west3"
When   main-agent decides this is a durable fact
Then   `AutoMemoryTools.write("user_deployment.md", ...)` persists it
       under `~/.grimo/memory/`
And    `~/.grimo/memory/MEMORY.md` contains a new one-line index entry
And    a later session's first turn includes the memory in the pre-prompt.
```

### AC10 — Spring Modulith boundaries verified in test

```
Given  the codebase is structured into @ApplicationModules
       (core, router, sub-agent, skills, memory, jury, cli-adapter, web-ui)
When   ./gradlew test runs
Then   ApplicationModules.of(GrimoApplication.class).verify() passes
And    no module has a non-named-interface dependency on another module
And    a generated module diagram is available at build/spring-modulith-docs/.
```

## 7. MVP scope

### Critical Path (user-ranked 2026-04-16 — drives milestone order)

The seven capabilities below form the vertical-slice demo for Grimo
v1. They are listed in priority order; each becomes one milestone in
`spec-roadmap.md` (M1 ↔ item 1, … M7 ↔ item 7). Items NOT on this
list but in-scope are supporting concerns; items absent entirely
default to Backlog.

1. **Container operations** — Grimo can spawn, exec, bind-mount, and
   clean up Docker containers from Java.
2. **Containerized CLI** — a `grimo-runtime` image ships with
   `claude-code`, `codex`, and `gemini` pre-installed; a Java adapter
   invokes each via `docker exec`.
3. **CLI configuration** — per-CLI config surveyed and Grimo policy
   applied to every containerized invocation (Claude-Code memory off,
   host credential store pass-through via read-only mounts, telemetry
   disabled where togglable).
4. **Main-agent chat** — `grimo chat` pipes stdin/stdout between the
   user's terminal and a containerized `claude-code`. CLI passthrough
   only; no web UI, no TUI in MVP.
5. **Task dispatch to sub-agent** — main-agent structurally delegates;
   Grimo spawns a sub-agent container with a per-task git worktree
   bind-mounted; captures the diff for user review before merge.
6. **Skill management** — Grimo lists / enables / disables skills
   under `~/.grimo/skills/`.
7. **Skill injection** — before sub-agent dispatch, relevant enabled
   skills are copied into the sub-agent container at each CLI's native
   skill path so the inner CLI picks them up.

### Supporting concerns (in MVP, not on Critical Path)

Infrastructure needed to deliver the Critical Path but not demo-level
capabilities on their own:

- Local-first single-user deployment, started with `./gradlew bootRun`
  or the native-image binary.
- Spring Modulith 2.0.5 module verification in tests.
- `native` Gradle profile that compiles the app to a native binary (a
  nightly smoke-test gate proves it still builds). Production-grade
  native UX — fast startup, reduced RSS, shipped artifact — is a
  post-MVP hardening concern, not a v1 launch gate.
- Virtual-thread executors for I/O-bound work
  (`spring.threads.virtual.enabled=true`).
- Fail-soft boot when any subset of CLIs is installed on the host.
- **Subscription-native auth (P10).** Grimo drives the underlying
  `claude` / `codex` / `gemini` CLIs through their own OAuth/login
  flows — no API-key form, no `ANTHROPIC_API_KEY` env expected. The
  user configures their CLI normally (`claude login` etc.) and Grimo
  reads the host credential store read-only inside the containerized
  CLI (per item 3 of the Critical Path).

### Backlog (was v0.1 MVP; deferred 2026-04-16 when Critical Path was ranked)

Each of these items will re-enter MVP only on explicit user demand,
and at that point gets a fresh `/planning-spec` grill loop. See
`spec-roadmap.md` §Backlog for effort estimates.

- `main-agent` CLI switch (claude ↔ codex ↔ gemini) via Grimo-owned
  session + compacted replay.
- Explicit read-only tool allowlist enforced on main-agent — in MVP
  this is handled structurally by container isolation plus the CLI
  configuration in Critical-Path item 3.
- Persistent session store (`spring-ai-session-jdbc` + H2 file). MVP
  accepts fresh session per `grimo chat` invocation.
- Cost router (heuristic v1), `Cost` domain type, cost telemetry panel.
- Jury command for N-way parallel review.
- `AutoMemoryTools`-backed memory under `~/.grimo/memory/`.
- Skill distillation proposer (harness-level auto-evolution).
- Local Web UI (Spring MVC + Thymeleaf + HTMX + SSE). MVP interface
  is CLI passthrough.
- Module boundary CI job (the in-test `ModuleArchitectureTest` covers
  it in MVP; a dedicated CI job is a luxury).
- E2E integration test suite.

### Out of scope (always)

- TUI adapter (owned terminal UI).
- Discord / Telegram / LINE adapters. Hexagonal inbound ports will be
  shaped to receive them without core changes when added.
- A2A **client-side** consumption (Spring AI A2A integration is
  currently server-side only; expose-as-server is a nice-to-have).
- Multi-user / multi-tenant. No accounts, no auth beyond localhost
  bind.
- Autonomous guide-tuning / rule-learning (self-evolution layer 2+).
- Mobile / Electron / native-platform UI.
- Remote agent pool / distributed sub-agents.
- Cloud-hosted session sync.
- Automated skill-execution without user review.
- Built-in VCS operations beyond worktree create/merge (no PR-
  creation UI in v1).

## 8. Architecture at a glance

```
         ┌─────────────────────── Adapter (in) ──────────────────────┐
         │  Web UI (Spring MVC + HTMX)   │   [future] TUI / Discord  │
         └───────────────┬───────────────────────────┬───────────────┘
                         │ HTTP / SSE                │
┌────────────────────────▼───────────────────────────▼────────────────┐
│                      Application Core (hex)                         │
│  ┌──────────┐  ┌─────────┐  ┌──────┐  ┌────────┐  ┌──────────────┐  │
│  │ core-    │  │ router  │  │ jury │  │ skills │  │ memory        │  │
│  │ harness  │◀─┤         │  │      │  │        │  │ (AutoMemory)  │  │
│  │ (session)│  └─────────┘  └──────┘  └────────┘  └──────────────┘  │
│  └────┬─────┘                                                       │
│       │ events (ApplicationEventPublisher)                          │
│  ┌────▼──────────┐                                                  │
│  │ sub-agent     │  ── spawns ──►  DockerSandbox + worktree         │
│  └───────────────┘                                                  │
└────────────────────────┬───────────────────────────┬────────────────┘
                         │ AgentClient (0.12.2)      │
         ┌───────────────▼─────────────┐   ┌─────────▼──────────────┐
         │  cli-adapter (ports out)    │   │  FS / Docker / Git      │
         │  claude · codex · gemini    │   │  (host resources)       │
         └─────────────────────────────┘   └─────────────────────────┘
```

Each name in the core row is a Spring Modulith `@ApplicationModule`.
Inbound adapters talk to ports via thin controllers; outbound adapters
wrap `AgentClient`, Testcontainers, and JGit/shell.

## 9. Decision log

| # | Decision | Why | Alternatives rejected |
| --- | --- | --- | --- |
| D1 | Position as a **user harness**, not an agent or framework | Research on Böckeler / Parallel / OpenAI "harness engineering" confirms this is the emerging canonical abstraction. It lets Grimo focus on control/contracts/state rather than inventing a new LLM interface. | "Another agent framework" (too crowded); "thin CLI launcher" (no cost routing or jury value). |
| D2 | Spring Boot **4.0.5** + Spring Modulith **2.0.5** + Java 25 | User-supplied build.gradle.kts locks these. Modulith 2.0.x *requires* Boot 4.0.x. Java 25 is LTS since 2025-09-16. | Boot 3.5 + Modulith 1.4.10 (conservative); rejected per user choice. |
| D3 | **`native` Gradle profile day-1**, production native image as **sprint-2 hardening** (revised 2026-04-16 after feasibility spike) | User enabled `org.graalvm.buildtools.native` in the initial build, so the profile must work from day one. But the feasibility spike returned STRONG-CAUTION: Testcontainers is not a runtime-native dependency; `agent-client` 0.12.2 has no native CI or published `RuntimeHints`; Modulith 2.0 has open native regressions (#1493 CGLIB). Ship JVM on JDK 25 in v1, run a **nightly `nativeCompile` smoke-test** from sprint 1 to surface hint gaps early without blocking delivery. | "Native AOT hard-gate on v1 launch" — rejected per spike evidence; "Drop GraalVM plugin entirely" — rejected, user-requested and the smoke-test discipline is cheap. |
| D4 | Modular monolith (Spring Modulith) over microservices | Single-user local tool. Microservices would add orchestration overhead with no benefit. Modulith verifies boundaries so future extraction stays cheap. | Pure microservices; rejected for MVP scope. |
| D5 | Hexagonal architecture inside the monolith | Explicit ports let us swap CLI adapters, sandbox backends, and inbound channels without touching core logic. Aligns with Modulith's "named interface" model. | Layered architecture with anemic services — less testable, less portable. |
| D6 | Main-agent **read-only**; writes via sub-agent in Docker (isolation replaces fine-grained permission control) | Three reasons: (a) **approval fatigue** — per-edit Y/N prompts push users to rubber-stamp or disable safety; bind-mounted Docker moves the approval gate to a single end-of-task diff. (b) **Planning vs execution** — planning needs strong reasoning (expensive, read-only); execution needs precise action (cheaper, sandboxed). (c) **Safety + cost** — makes the dangerous path structurally harder. Matches harness-engineering's "computational controls" canon. See P2. | Unified read/write main-agent with user-confirmation prompts — weaker guarantee, worse UX (approval fatigue), higher blast radius. Per-action fine-grained permission matrix — high maintenance cost, easy to misconfigure. |
| D7 | Grimo owns the session (via Spring AI `SessionMemoryAdvisor`), not agent-client's per-CLI `AgentSession` | `AgentSession` is Claude-only in 0.12.2; cannot deliver cross-CLI continuity. Session API (2026-04-15) provides event-sourced `Message`s that are CLI-agnostic. | Use `AgentSession` directly — would force main-agent = claude forever. |
| D8 | CLI-switch uses **compacted-history replay** | User explicitly confirmed: "將過往對話跟新訊息一起派發給新 CLI". Pragmatic, bounded, no custom CLI protocol. | Full CLI-neutral session protocol (reinvents A2A); switch-only-at-task-boundary (too restrictive for UX). |
| D9 | Grimo owns a `Sandbox` port; **default impl wraps Testcontainers `GenericContainer` directly** (NOT `DockerSandbox`) with `withFileSystemBind(worktree, "/work", RW)` called before `start()` | Confirmed by source spike (2026-04-16): `org.springaicommunity.sandbox.docker.DockerSandbox` **0.9.1** starts its container in the constructor, so there is no API hook (`withBindMount`, `volume(...)`, customizer) to inject a bind-mount before startup. Subclassing also fails. Testcontainers directly works; `DockerSandbox` remains usable for the *ephemeral file-copy* case via `sandbox.files()` if we ever need it. | Upstream PR to agent-sandbox (still required long-term, but not a v1 blocker); raw Docker Java API (more code, no Testcontainers helpers); Podman (fewer users). |
| D10 | Include all three `agent-claude`/`-codex`/`-gemini` starters; rely on the stack's **call-time (not boot-time) CLI probe**; disable per-provider via `spring.autoconfigure.exclude=...` when needed (revised 2026-04-16 after autoconfig source spike) | Spike confirms auto-configs are gated only by `@ConditionalOnClass`, with no `enabled` property; but they do NOT eagerly probe the CLI binary (`GeminiClient.create` / `ClaudeAgentModel` etc. defer CLI discovery until first call). Therefore P5 (fail-soft boot) is already satisfied by the library's natural behavior — a missing CLI surfaces as a clean `CliNotFoundException` at call-time. Grimo wraps each adapter to translate that into a user-visible "install `<cli>` or run /grimo switch <other>" message. Note property prefix is `spring.ai.agents.claude-code.*` (hyphen), not `claude.*`. | Invent a `grimo.cli.<provider>.enabled=true` gate — redundant given library already defers; adds config noise. Enable none by default — hurts new-user UX. |
| D11 | **REVISED 2026-04-16: v1 external channel = CLI passthrough** (`grimo chat` pipes stdin/stdout between the user's terminal and a containerized `claude-code`). Original decision "Local Web UI" is moved to Backlog per §7 re-plan. | User ranked "main-agent CLI chat" as Critical Path item 4 with no UI layer below it; building Spring MVC + HTMX just to host a chat form when the containerized CLI already owns terminal I/O is over-scoped for v1. Hex ports are still shaped to receive a Web UI later without core changes. | Original D11 "Local Web UI"; TUI (still out of scope); all channels day-1 (still overscoped). |
| D12 | Self-evolution scope = **Memory + Skills** (propose-only) | User chose it. Smallest credible self-evolving loop. Proposal-review-commit keeps human in control. | Memory-only (barely "self-evolving"); Memory+Skills+Guides (auto-tuning guides is R&D, not MVP). |
| D13 | Cost router is **heuristic v1** with user override | Avoid building a classifier model on day 1. Start with task-pattern + size + explicit override; learn from telemetry. | LLM-based classifier for routing (extra call per turn, latency hit); manual-only (no cost savings). |
| D14 | `~/.grimo/` is the single persistent state root | One place to back up, inspect, git-ignore. Matches `~/.claude`, `~/.codex`, `~/.gemini` user mental model. | Scatter state under XDG dirs — harder for users to reason about. |
| D15 | No auth, localhost-only bind in MVP | Local-first, single-user. Auth is a v2 concern. | Add Spring Security day-1 — overscoped. |
| D16 | Web UI = **Spring MVC + Thymeleaf + HTMX 2** via `io.github.wimdeblauwe:htmx-spring-boot:5.1.0`; **BAN `thymeleaf-layout-dialect`** (Groovy runtime is native-image hostile — use parameterized fragments) | Decision matrix (native AOT × streaming UX × Java-only × single-build) put MVC+Thymeleaf+HTMX far ahead. Boot 4.0.3+ baseline on `htmx-spring-boot` 5.1.0 matches our Boot 4.0.5; Thymeleaf is officially listed native-ready; `ChatClient.stream().content()` → `SseEmitter` → `hx-ext="sse"` is a 15-line glue path. | Vaadin Hilla (Boot 4 support still pre-GA on 24.x), React SPA (violates Java-only), WebFlux+SSE (has open AOT regressions on functional routes). |
| D17 | Persistent store = **H2 file-mode** by default (`~/.grimo/db/grimo;MODE=PostgreSQL`); PostgreSQL opt-in via `grimo.datasource.url` | Local-first; H2 has excellent native-image support; `spring-ai-session-jdbc` 0.2.0 ships an H2 dialect (`H2JdbcSessionRepositoryDialect`). MODE=PostgreSQL for parity when a user later points at Postgres. | SQLite via xerial (no first-class Spring starter, no dialect in session-jdbc); embedded Postgres (heavyweight); pure in-memory (loses history across restarts). |
| D18 | Persisted session API = **`spring-ai-community/spring-ai-session` 0.2.0** (artifact group `org.springaicommunity`, starter `spring-ai-starter-session-jdbc`) — **separate library from `agent-client` 0.12.2** | Spike corrected the initial mis-assumption that session API ships under 0.12.2. This library is on its own release cadence (0.1.0 → 0.2.0 → 0.3.0-SNAPSHOT), requires Spring AI ≥ 2.0.0-M4 and Spring Boot ≥ 4.0.2 (we're on 4.0.5 ✓). API churn expected — pin to `0.2.0` release, lock dependency versions explicitly. | Build our own persistence layer (reinvents the wheel; they already provide `SessionEvent`, `SessionMemoryAdvisor`, compaction strategies, JDBC starter, schema); target `0.3.0-SNAPSHOT` (unstable); defer session until a stable 1.x (delays CLI-switch AC3). |
| D19 | **Subscription-native auth only** in MVP — Grimo never prompts for an API key; the user logs in once via each CLI's own flow (`claude login`, `codex login`, `gemini auth`), and Grimo inherits that auth by invoking the CLI subprocess | Target-user profile explicitly pays subscription (Claude Max / ChatGPT Plus / Gemini Advanced). Asking for API tokens on top would be a double-charge and a UX cliff. `agent-client` 0.12.2's per-provider adapters already spawn the CLI binary as a subprocess — the CLI's native credential store (keychain / `~/.claude` / `~/.gemini` / `~/.codex`) is reused unchanged. | API-key auth surface in the Web UI (rejected: wrong billing story, duplicates config already stored by the CLIs); BYO-key fallback (rejected for MVP — adds a config path we don't need yet, can be added post-MVP behind `grimo.auth.api-key.enabled=true`). |

## 10. Risk register

| # | Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- | --- |
| R1 | Spring AI / Testcontainers / ProcessBuilder are a GraalVM-heavy combo → Native AOT could bleed time | High | Medium | **RESOLVED in planning:** D3 revised — JVM-first v1, `native` profile + nightly `nativeCompile` smoke-test from sprint 1 (tracks hint gaps). Testcontainers is JVM-only by policy (`@DisabledInNativeImage`). Production native image targeted in a sprint-2 hardening milestone (M7). |
| R2 | `DockerSandbox` host bind-mount is unsupported in 0.9.1 → worktree passthrough blocked | **Confirmed** | High | **RESOLVED in planning:** D9 revised — Grimo owns the `Sandbox` port; default impl wraps `GenericContainer` + `withFileSystemBind` directly. `DockerSandbox` is not used on the worktree path. Optional upstream PR tracked as a post-MVP courtesy. |
| R3 | CLI-switch replay loses fidelity (subtle tool-use history, partial diffs) | Medium | Medium | Compaction strategy is tested via AC3; document scope of "preserved" vs "lost" in the user guide. |
| R4 | Cost router heuristic mis-routes strategic tasks as trivial | Medium | Medium | User override is always available; telemetry on overrides drives heuristic refinement. |
| R5 | Skill-distillation proposes garbage skills → user trust erodes | Medium | Medium | Require N ≥ 3 successful reps and user approval before any skill is active; opt-in per-user setting. |
| R6 | Spring AI 0.12.2 APIs move under us (BOM shifts) | Medium | Low | Pin transitive deps; track changelog; gate upgrades through specs. |

## 11. Success metrics (post-MVP)

- **Cost delta:** median per-session cost drops ≥ 30% vs baseline of
  running the same prompts on Claude Opus alone.
- **Switch retention:** ≥ 80% of CLI-switch events are followed by a
  user message that references prior context without re-explanation
  (sensor: LLM-as-judge on transcripts).
- **Distilled skill adoption:** ≥ 1 distilled skill accepted per active
  user per 30 days.
- **Boot reliability:** 0 boot failures attributable to missing CLI
  binaries on a fresh laptop with any one CLI installed.
- **Module integrity:** `ApplicationModules.verify()` green on `main`
  at all times.

## 12. Open questions (for `/planning-project` to nail down)

1. Native-image **RuntimeHints** inventory — which Spring AI / Testcontainers /
   agent-client classes need explicit reachability hints?
2. Concrete **cost-router heuristic table** (task classes × CLIs × thresholds).
3. **Compaction strategy** choice: `SlidingWindowCompactionStrategy`
   parameters, or LLM-summarization of chunks on switch?
4. Exactly how to mount a **host worktree** into `DockerSandbox` —
   extension vs upstream PR vs direct Testcontainers.
5. **Security model** for the Web UI beyond localhost-only (CSRF,
   origin-pinning) — MVP minimum set.
6. **Skill-distillation cadence** — nightly cron vs on-demand vs after-
   task hook.

---

*Glossary: see `docs/grimo/glossary.md`. Every term appearing here in
bold or in `code style` is defined there.*
