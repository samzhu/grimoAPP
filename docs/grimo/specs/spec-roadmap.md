# Grimo — Spec Roadmap

**Status:** v0.1 · **Date:** 2026-04-16
**Source:** `docs/grimo/PRD.md` + `architecture.md`

> Estimation scale (six-dimension rubric, each 1–3):
> `Tech risk · Uncertainty · Dependencies · Scope · Testing · Reversibility`
> 6–8 → XS · 9–11 → S · 12–14 → M · 15–16 → L · 17–18 → XL (must decompose)

## Dependency graph (high level)

```
S000 ── S001 ── S002 ── S003
                   │      │
                   ▼      ▼
                 S004 ── S006 ── S007 ── S009 ── S015
                   │ ╲     ▲       │       ▲       ▲
                   │  ╲    │       │       │       │
                   ▼   ╲   │       S008 ───┘       │
                 S005 ── 006                       │
                   │                               │
                   ▼                               │
                 S011 ── S012 ── S013 ── S014 ─────┘
                           │
                           └─────────── S016 ── S018
                                          │       │
                                          ▼       │
                                        S017 ─────┘
                                                  │
       S020 (parallel, after S002)                │
                                                  ▼
                                                S019
                                                  │
                            S021a ── S021b ── S021c
                                                  │
                                                S022
```

## Milestone map

| M# | Name | Specs | Phase | Goal |
| --- | --- | --- | --- | --- |
| M0 | Foundation | S000–S003 | Phase 0 | buildable skeleton exports `/actuator/health` from Thymeleaf+HTMX shell |
| M1 | Single-CLI happy path | S004–S006 | Phase 1 | user chats with `claude-code` end-to-end via Web UI, streamed, persisted |
| M2 | Multi-CLI + switch | S007–S010 | Phase 2 | all three CLIs plug in; mid-session switch preserves context; main-agent read-only enforced |
| M3 | Sub-agent isolation | S011–S013 | Phase 3 | main-agent delegates a write-task; sub-agent in Docker with mounted worktree; diff returned |
| M4 | Router & jury | S014–S015 | Phase 4 | cost router routes by task class; jury command runs N-way parallel review |
| M5 | Harness extras | S016–S018 | Phase 5 | unified skills registry; agent-curated memory; skill-distillation proposer |
| M6 | Observability & boundaries | S019–S020 | Phase 6 | cost panel; Modulith boundary tests wired into CI |
| M7 | Native hardening | S021a–c | Phase 7 | production native binary path with `ProcessBuilder` sandbox adapter |
| M8 | Integration & release | S022 | Phase 8 | full stubbed E2E suite + release readiness |

---

## Milestone 0: Foundation (Phase 0)

**Goal.** A runnable Spring Boot 4.0.5 skeleton on JDK 25 with Modulith
verification green, Web UI shell serving `/actuator/health`, and every
dev/CI gate from `qa-strategy.md` §12 is green.

**Done when.** S000, S001, S002, S003 all completed.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S000 | Project Init — Gradle KTS, Modulith skeleton, CI | XS (7) | ✅ |
| S001 | Core domain primitives + GrimoHomePaths | XS (7) | 🔲 |
| S002 | Module skeleton + Modulith verify green | S (9) | 🔲 |
| S003 | Web shell (Thymeleaf + HTMX + SSE plumbing) | S (10) | 🔲 |

### S000 — Project Init · XS (7)

**Description.** Finish scaffolding after Spring Initializr. Add the
`graalvmNative` block for native packaging, enable virtual threads,
patch `.gitignore`, add a top-level README, and doc-sync the
package rename. See `2026-04-16-S000-project-init.md` for the
authoritative acceptance criteria.

**Dependencies.** none.

**Note — formatting gate deferred (2026-04-16).** The original AC3
"Spotless / Palantir Java Format" was dropped from S000 per user
decision; formatting will be handled in one shot by an external tool at
a later cleanup spec. AC3 is kept as a numbering placeholder in the
spec file so AC4..AC7 keep their identifiers across task files.

**Estimation.** Tech 1 · Uncert 1 · Deps 1 · Scope 2 · Test 1 · Rev 1 = **7 / XS**

---

### S001 — Core domain primitives + GrimoHomePaths · XS (7)

**Description.** Create the `io.github.samzhu.grimo.core.domain` package: records
for `SessionId`, `TurnId`, `TaskId`, `CorrelationId`, `AgentRole`
(sealed: `MAIN` / `SUB` / `JURY_MEMBER`), `ProviderId` (enum: `CLAUDE`,
`CODEX`, `GEMINI`), `Cost` (tokens-in / tokens-out / cents), and
`GrimoHomePaths` utility (the single authority for `~/.grimo/**` path
resolution). Zero Spring annotations on any of these.

**Dependencies.** S000.

**SBE criteria.**

- **AC1** `SessionId.random()` produces a UUID-based id printable as a
  short base-62 string.
- **AC2** `GrimoHomePaths.memory()` returns `~/.grimo/memory/` created
  on first call, respects `$GRIMO_HOME` env override.
- **AC3** Compile-time check: `io.github.samzhu.grimo.core.domain..*` has no
  Spring annotation import — enforced by an ArchUnit test.
- **AC4** `Cost.add(Cost)` and `Cost.zero()` work; total-cents is a
  long (no floating point).

**Estimation.** Tech 1 · Uncert 1 · Deps 1 · Scope 2 · Test 1 · Rev 1 = **7 / XS**

---

### S002 — Module skeleton + Modulith verify green · S (9)

**Description.** Create `package-info.java` with `@ApplicationModule`
in every planned module (`session`, `cli`, `router`, `subagent`,
`skills`, `memory`, `jury`, `cost`, `web`, `native`). Mark `core` as
`type = Type.OPEN`. Wire `spring-modulith-starter-core` and
`spring-modulith-starter-test`. Add a `ModuleArchitectureTest` that
runs `ApplicationModules.of(GrimoApplication.class).verify()` and
generates the module canvas into `build/spring-modulith-docs/`.

**Dependencies.** S001.

**SBE criteria.**

- **AC1** `./gradlew test` runs `ModuleArchitectureTest` which passes
  `ApplicationModules.verify()` on an empty module graph.
- **AC2** Each module's `allowedDependencies` lists only `core` (or
  empty) for now; no inter-module edges exist yet.
- **AC3** `build/spring-modulith-docs/modules-*.adoc` + C4 diagrams
  are generated by `Documenter`.
- **AC4** Adding an illegal cross-module reference (in a throwaway
  branch) causes `./gradlew test` to fail with a clear
  `Violations detected:` message.

**Estimation.** Tech 1 · Uncert 1 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **9 / S**

---

### S003 — Web shell (Thymeleaf + HTMX + SSE plumbing) · S (10)

**Description.** Wire `spring-boot-starter-web` +
`spring-boot-starter-thymeleaf` + `htmx-spring-boot:5.1.0` +
`htmx-spring-boot-thymeleaf:5.1.0`. Build a minimal page at `GET /`
that renders a chat frame (header, message list, input box), an
unconfigured "session" placeholder, and a working SSE route at
`GET /sse/heartbeat` that emits a timestamp token every second so we
can verify streaming end-to-end. Bind server to `127.0.0.1` (D15,
security). Add the `HandlerInterceptor` CSRF token skeleton.

**Dependencies.** S002.

**SBE criteria.**

- **AC1** `GET http://127.0.0.1:8080/` returns 200 with an HTML page
  containing `<div id="chat">` and the HTMX script tag.
- **AC2** `GET http://127.0.0.1:8080/sse/heartbeat` streams
  `text/event-stream` with at least three `event: heartbeat` frames
  within 3 s.
- **AC3** Server does not bind to `0.0.0.0` — verified by a Spring
  Boot test reading `ServerProperties.address`.
- **AC4** No `thymeleaf-layout-dialect` on the runtime classpath —
  Gradle dependency report asserts.

**Estimation.** Tech 2 · Uncert 1 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **10 / S**

---

## Milestone 1: Single-CLI happy path (Phase 1)

**Goal.** A real user can open the Web UI, type a message, see a
streamed reply from `claude-code`, and reopen the page tomorrow to
resume that session.

**Done when.** S004, S005, S006 all completed.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S004 | Claude CLI adapter + AgentClientPort (covers PRD AC6 fail-soft) | M (12) | 🔲 |
| S005 | Session persistence — spring-ai-session-jdbc 0.2.0 + H2 file | M (12) | 🔲 |
| S006 | End-to-end single-turn chat with SSE streaming | M (13) | 🔲 |

### S004 — Claude CLI adapter + AgentClientPort · M (12)

**Description.** Define outbound port
`io.github.samzhu.grimo.cli.application.port.out.AgentClientPort` (methods
`stream(PromptSpec): Flux<Token>`, `probe(): ProbeResult`). Provide the
production impl `AgentClientAdapter` wrapping Spring AI Community
`AgentClient.builder().agentModel(ClaudeAgentModel).build()`. Add a
`StubAgentClientAdapter` for tests (T0..T3). Register both with
distinct `@Qualifier("claude")`. Translate `CliNotFoundException` into
the domain event `CliUnavailable` (this is what satisfies **PRD AC6
fail-soft boot** — port must boot even if `claude` binary missing, and
surface a user-facing message at call time).

**Dependencies.** S001, S002.

**SBE criteria.**

- **AC1** Spring context starts successfully when `claude` is absent
  from `PATH` (verified with `@SpringBootTest` that shadows `PATH` to
  an empty directory).
- **AC2** Calling `AgentClientPort.probe()` returns
  `ProbeResult.notInstalled("claude")` instead of throwing.
- **AC3** `AgentClientPort.stream("hello")` against the stub produces
  a `Flux<Token>` with at least two tokens and completes without
  error.
- **AC4** Property `spring.ai.agents.claude-code.executable-path` is
  honored; setting a non-existent path surfaces as
  `ProbeResult.notInstalled` (not a boot failure).
- **AC5** Covers **PRD AC6** (fail-soft boot on missing CLIs).

**Estimation.** Tech 2 · Uncert 2 · Deps 2 · Scope 2 · Test 2 · Rev 2 = **12 / M**

---

### S005 — Session persistence wiring · M (12)

**Description.** Add `spring-ai-starter-session-jdbc:0.2.0` (group
`org.springaicommunity`). Configure H2 file mode at
`~/.grimo/db/grimo.mv.db` with `MODE=PostgreSQL`. Let the starter
create `AI_SESSION` + `AI_SESSION_EVENT` tables. Add Flyway at
migration `V1__grimo_baseline.sql` creating `grimo_cost`. Wire a
`SessionMemoryAdvisor` bean with `TurnCountTrigger(20)` +
`SlidingWindowCompactionStrategy(maxEvents=10)`. Expose a
`SessionUseCase` port (`open`, `resume`, `switchProvider`).

**Dependencies.** S001, S002.

**SBE criteria.**

- **AC1** First app start creates `~/.grimo/db/grimo.mv.db` and
  `AI_SESSION`/`AI_SESSION_EVENT` tables.
- **AC2** Persisting a `SessionEvent` and re-reading it via
  `SessionService.getEvents(sessionId)` round-trips the message
  content.
- **AC3** `SessionMemoryAdvisor` in a `@WebMvcTest` mock prepends
  prior events to the prompt given an existing `sessionId`.
- **AC4** Compaction fires at turn 20 and produces a
  `TurnsCompacted` domain event consumed by `cost` module.

**Estimation.** Tech 2 · Uncert 2 · Deps 2 · Scope 2 · Test 2 · Rev 2 = **12 / M**

---

### S006 — End-to-end single-turn chat · M (13)

**Description.** Wire `POST /chat?sessionId=...` → `ChatClient`
configured with the Claude `AgentClientPort` + `SessionMemoryAdvisor`.
Return `text/event-stream` bridging `ChatClient.stream().content()`.
HTMX page subscribes via `<div hx-ext="sse" sse-connect="..."
sse-swap="token">`. Persist the turn via the advisor after stream
completes. Show per-turn token count in a side-panel.

**Dependencies.** S003, S004, S005.

**SBE criteria.**

- **AC1** User submits "hello" via the web form; within 3 s, tokens
  appear in the chat DOM (SSE stream asserted by Selenide or by
  `WebTestClient`).
- **AC2** Refreshing the page and reusing the same `sessionId` shows
  the previous user+assistant turns rendered from `SessionService`.
- **AC3** Turn is persisted even if the browser disconnects before
  the final token (verified by closing SSE mid-stream and inspecting
  `AI_SESSION_EVENT`).
- **AC4** Per-turn token count is visible in the side panel.

**Estimation.** Tech 2 · Uncert 2 · Deps 3 · Scope 2 · Test 3 · Rev 1 = **13 / M**

---

## Milestone 2: Multi-CLI + switch (Phase 2)

**Goal.** All three CLIs work; mid-session switch preserves context
per PRD AC3; main-agent cannot write per PRD AC4.

**Done when.** S007–S010 all completed.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S007 | Codex CLI adapter | S (10) | 🔲 |
| S008 | Gemini CLI adapter | S (10) | 🔲 |
| S009 | CLI switch with compacted replay (PRD AC3) | **L (15)** — manual QA | 🔲 |
| S010 | Main-agent read-only enforcement (PRD AC4) | S (9) | 🔲 |

### S007 — Codex CLI adapter · S (10)

**Description.** Mirror S004 for `CodexAgentModel`. Register under
`@Qualifier("codex")`. Share the stub tooling. Read
`spring.ai.agents.codex.executable-path`.

**Dependencies.** S004.

**SBE criteria.** (parallel to S004 AC1–4, s/claude/codex/) plus:

- **AC1** Both `claude` and `codex` adapters can coexist; list-ports
  bean sees two `AgentClientPort` qualifiers.
- **AC2** Missing `codex` binary produces a distinct
  `CliUnavailable(codex)` event without affecting `claude`.

**Estimation.** Tech 2 · Uncert 1 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **10 / S**

---

### S008 — Gemini CLI adapter · S (10)

**Description.** Same as S007 for `GeminiAgentModel`. Note the
`System.setProperty("gemini.cli.path", ...)` side-effect the starter
performs — document it in the adapter.

**Dependencies.** S004.

**SBE criteria.** (parallel to S007).

**Estimation.** Tech 2 · Uncert 1 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **10 / S**

---

### S009 — CLI switch with compacted replay · L (15) · manual QA required

**Description.** Implement `SessionUseCase.switchProvider(sessionId,
newProviderId)`:
1. Flush outstanding events via `SessionMemoryAdvisor`.
2. Load full event list via `SessionService.getEvents`.
3. Apply the configured `CompactionStrategy` (default
   `SlidingWindowCompactionStrategy`) to produce a
   condensed bootstrap prompt.
4. Bind the session to the new `AgentClientPort` qualifier.
5. On next user message, the bootstrap prompt is prepended as a
   system message before the user message.
6. Emit `SessionSwitched(sessionId, from, to, compactedBytes)`.

Covers **PRD AC3**.

**Dependencies.** S005, S007, S008.

**SBE criteria.**

- **AC1** After 5 user/assistant turns with `claude`, user calls
  `POST /grimo/switch?provider=codex` — returns 200 with the new
  provider id.
- **AC2** Next `POST /chat` turn delivers bootstrap+new-message to
  codex; assertion via a `StubAgentClientAdapter("codex")` that
  captures the prompt.
- **AC3** Captured bootstrap contains references to entities named
  in prior turns (deterministic string-match test against a fixture
  session).
- **AC4** `SessionSwitched` event is published and observed in a
  Modulith-level integration test.
- **AC5** Manual QA (invoke `/verifying-quality`): run the live
  scenario from PRD AC3 and confirm codex's reply references
  OrderService by name.

**Estimation.** Tech 2 · Uncert 3 · Deps 3 · Scope 2 · Test 3 · Rev 2 = **15 / L**
(Kept as L per qa-strategy.md §5 — manual QA required; do not
decompose further since the feature is user-facing and benefits from
cohesive review.)

---

### S010 — Main-agent read-only enforcement · S (9)

**Description.** The `cli` module's `AgentConversationUseCase` accepts
a `Role role` parameter. When `role == AgentRole.MAIN`, the prompt is
augmented with a system-message preamble and a tool allowlist that
permits only Read / Glob / Grep / WebFetch / WebSearch. Any attempted
Edit / Write / Bash-mutation tool call is intercepted by a custom
`AgentCallAdvisor` and returned to the model as a structured error
message instructing delegation.

Covers **PRD AC4**.

**Dependencies.** S004.

**SBE criteria.**

- **AC1** With a stub model that requests `Edit`, the advisor returns
  a synthetic tool result `{"error":"main-agent is read-only;
  delegate to sub-agent"}` and no filesystem mutation occurs.
- **AC2** The same flow with `Read` succeeds and returns file content.
- **AC3** Main-agent system-message preamble is present in the
  captured request.

**Estimation.** Tech 1 · Uncert 1 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **9 / S**

---

## Milestone 3: Sub-agent isolation (Phase 3)

**Goal.** Main-agent can delegate a write task; Grimo spawns a Docker
sub-agent with a git worktree bind-mounted; the diff comes back for
user review.

**Done when.** S011, S012, S013 all completed.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S011 | Worktree manager (JGit) | S (9) | 🔲 |
| S012 | Sandbox port + Testcontainers adapter with bind-mount | M (13) | 🔲 |
| S013 | Sub-agent delegation lifecycle (PRD AC5) | M (14) | 🔲 |

### S011 — Worktree manager · S (9)

**Description.** Port `WorktreePort` with `create(TaskId,
sourceBranch): Worktree` and `drop(TaskId)`. Impl uses JGit
`Git.open(repo).worktreeAdd()` into `~/.grimo/worktrees/<taskId>/`.
Handles cleanup on crash via startup sweep.

**Dependencies.** S001.

**SBE criteria.**

- **AC1** `WorktreePort.create(taskId, "main")` creates a detached
  worktree directory with a `.git/` file (pointer) and the expected
  HEAD commit.
- **AC2** `WorktreePort.drop(taskId)` removes the directory and
  unregisters the worktree from the parent repo.
- **AC3** Startup sweep removes stale worktrees for tasks that no
  longer exist in `grimo_subagent_task`.

**Estimation.** Tech 1 · Uncert 1 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **9 / S**

---

### S012 — Sandbox port + Testcontainers adapter · M (13)

**Description.** Port `SandboxPort` with methods `spawn(SpawnSpec):
Sandbox`, `exec(Sandbox, Command): ExecResult`, `close(Sandbox)`.
Default impl `TestcontainersSandboxAdapter` uses
`GenericContainer<>(DockerImageName.parse(image))
  .withWorkingDirectory("/work")
  .withFileSystemBind(worktreePath.toString(), "/work", BindMode.READ_WRITE)
  .withCommand("sleep", "infinity")`
before `start()`. (Direct Testcontainers — **not** `DockerSandbox`,
per PRD D9.) `@DisabledInNativeImage` on all tests using this.
Leave a native-safe `ProcessBuilderSandboxAdapter` stub for S021b.

**Dependencies.** S001.

**SBE criteria.**

- **AC1** `spawn` with a prepared worktree produces a container
  whose `/work` is the host worktree (verified by `exec "ls -la
  /work"`).
- **AC2** Writes inside the container appear immediately on the host
  worktree; the test commits them via JGit.
- **AC3** A second `spawn` with a different worktree cannot see the
  first (bind-mount isolation).
- **AC4** `close` stops and removes the container; host worktree
  survives.

**Estimation.** Tech 3 · Uncert 2 · Deps 2 · Scope 2 · Test 3 · Rev 2 = **13 / M**

---

### S013 — Sub-agent delegation lifecycle · M (14)

**Description.** Implement `DelegateTaskUseCase`:
1. Accept a structured `TaskSpec` from main-agent's structured
   output.
2. Reserve a sub-agent slot (bounded by
   `grimo.subagent.max-concurrent`).
3. Create worktree (S011), spawn sandbox (S012).
4. Run a sub-agent inside the container via the same
   `AgentClientPort` — but with full YOLO tool allowlist because the
   sandbox confines it.
5. Capture the resulting `git diff /work`.
6. Surface the diff in the Web UI for user review; on accept, merge
   the worktree branch into the source repo; on reject, drop the
   worktree.

Covers **PRD AC5**.

**Dependencies.** S004, S011, S012.

**SBE criteria.**

- **AC1** Delegating `edit /work/src/Foo.java` causes the file to be
  modified inside the worktree directory on the host.
- **AC2** Sibling sub-agent cannot list files outside its mount
  (`exec "ls /host"` returns "no such file" or empty).
- **AC3** After sub-agent completes, the Web UI shows the diff and
  an "accept / reject" UI.
- **AC4** Rejecting drops the worktree; accepting merges to the
  source branch.

**Estimation.** Tech 2 · Uncert 2 · Deps 3 · Scope 2 · Test 3 · Rev 2 = **14 / M**

---

## Milestone 4: Router & jury (Phase 4)

**Goal.** Cost-aware routing and multi-perspective review are live.

**Done when.** S014 and S015 completed.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S014 | Cost router heuristic v1 (PRD AC1, AC2) | S (9) | 🔲 |
| S015 | Jury command — N-way parallel review (PRD AC7) | M (13) | 🔲 |

### S014 — Cost router heuristic v1 · S (9)

**Description.** Port `RouteUseCase.decide(PromptSpec): RouteDecision`.
Decision engine is a rule-based classifier over:
- token length of the user message
- presence of keywords ("format", "rename", "grep" → trivial;
  "refactor", "architecture", "review PR" → strategic)
- session budget so far
- user override (`/grimo route codex`)

Outputs a `ProviderId` + a one-sentence justification. Emits
`RouteDecided` event. Default table ships in
`~/.grimo/config/router.yaml` with a packaged fallback.

Covers **PRD AC1, AC2**.

**Dependencies.** S004, S005.

**SBE criteria.**

- **AC1** "format this timestamp" → `gemini`; cost delta < $0.0005 in
  the telemetry (AC1 in PRD).
- **AC2** "refactor OrderService to hex" → `claude`; justification
  includes "structural refactor" (AC2 in PRD).
- **AC3** User override via `/grimo route claude` sticks for the
  remainder of the session until cleared.
- **AC4** Router decision is visible in the per-turn side panel.

**Estimation.** Tech 1 · Uncert 2 · Deps 2 · Scope 2 · Test 1 · Rev 1 = **9 / S**

---

### S015 — Jury command · M (13)

**Description.** Port `JuryReviewUseCase.review(TargetRef, n int):
JuryVerdict`. Dispatches the same review prompt to `n` distinct
`AgentClientPort` qualifiers in parallel on virtual threads. Each
reviewer runs **in its own Docker sandbox with a read-only worktree
mount** (consuming S012). Aggregates markdown per reviewer into a
consensus + divergence section; returns within a single user-visible
turn.

Covers **PRD AC7**.

**Dependencies.** S007, S008, S010, S012.

**SBE criteria.**

- **AC1** `POST /grimo/jury?target=PRD.md&n=3` returns a markdown
  response with sections `## Consensus`, `## Divergence`, `##
  <provider>` for each reviewer.
- **AC2** Each reviewer ran on a distinct provider (asserted from
  telemetry).
- **AC3** Per-provider token costs are displayed.
- **AC4** One reviewer failing (e.g., codex timeout) does not
  prevent the other two from returning — partial verdict is
  labelled.

**Estimation.** Tech 2 · Uncert 2 · Deps 3 · Scope 2 · Test 2 · Rev 2 = **13 / M**

---

## Milestone 5: Harness extras (Phase 5)

**Goal.** Unified skills registry, curated memory, and skill
distillation proposer are live.

**Done when.** S016, S017, S018 completed.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S016 | Skills registry + SKILL.md discovery | M (13) | 🔲 |
| S017 | Memory wiring — AutoMemoryTools (PRD AC9) | M (12) | 🔲 |
| S018 | Skill distillation proposer (PRD AC8) | M (14) | 🔲 |

### S016 — Skills registry + SKILL.md discovery · M (13)

**Description.** Scan `~/.grimo/skills/` and `classpath*:/skills/**/SKILL.md`
using `PathMatchingResourcePatternResolver` per the
"Spring AI Generic Agent Skills" pattern. Build a registry that exposes
the skill list as a tool in every `AgentClientPort` invocation. Each
skill is read on demand at tool-call time.

**Dependencies.** S001.

**SBE criteria.**

- **AC1** A skill dropped at `~/.grimo/skills/greet/SKILL.md` appears
  in the next turn's tool listing for every provider.
- **AC2** The skill's frontmatter (`name`, `description`) is
  surfaced in the tool description.
- **AC3** Invalid frontmatter produces a warning log and the skill
  is omitted (not a boot failure).
- **AC4** Built-in skills packaged under
  `src/main/resources/skills/` are discovered alongside user skills.

**Estimation.** Tech 2 · Uncert 2 · Deps 2 · Scope 3 · Test 2 · Rev 2 = **13 / M**

---

### S017 — Memory wiring · M (12)

**Description.** Wire `AutoMemoryTools` + `AutoMemoryToolsAdvisor`
from `spring-ai-agent-utils`. Configure base directory
`~/.grimo/memory/`. Register advisor on every `ChatClient`.

Covers **PRD AC9**.

**Dependencies.** S001.

**SBE criteria.**

- **AC1** A stub model calling `write(user_deployment.md, "...
  europe-west3 ...")` creates the file under `~/.grimo/memory/`.
- **AC2** `~/.grimo/memory/MEMORY.md` gains a one-line index entry.
- **AC3** A second chat session's first turn includes the memory
  content in the pre-prompt (verified via the stub capturing the
  request).

**Estimation.** Tech 2 · Uncert 2 · Deps 2 · Scope 2 · Test 2 · Rev 2 = **12 / M**

---

### S018 — Skill distillation proposer · M (14)

**Description.** Nightly (or on-demand via `/grimo distill`) job that
scans recent sessions for recurring patterns (same task class + same
router decision path + successful completion, N ≥ 3). For each
cluster, ask main-agent to propose a draft `SKILL.md`. Write to
`grimo_skill_proposal` table with status `PENDING`. Web UI shows a
"new skill draft" badge; user can accept/edit/reject.

Covers **PRD AC8**.

**Dependencies.** S005, S014, S016.

**SBE criteria.**

- **AC1** With a seeded set of 3 similar sessions, running
  `POST /grimo/distill` creates a `grimo_skill_proposal` row.
- **AC2** Accepting the proposal writes it to `~/.grimo/skills/
  <name>/SKILL.md` and the registry picks it up on the next turn.
- **AC3** Rejecting marks the row `REJECTED` and the pattern's
  minimum-reps counter resets.
- **AC4** The draft is user-readable and contains a
  `description:` frontmatter field.

**Estimation.** Tech 2 · Uncert 3 · Deps 3 · Scope 2 · Test 2 · Rev 2 = **14 / M**

---

## Milestone 6: Observability & boundaries (Phase 6)

**Goal.** Cost visibility and module-boundary CI enforcement.

**Done when.** S019, S020 completed.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S019 | Cost telemetry panel | XS (8) | 🔲 |
| S020 | Module boundary verification + CI wiring (PRD AC10) | XS (7) | 🔲 |

### S019 — Cost telemetry panel · XS (8)

**Description.** Add `GET /grimo/cost` Thymeleaf page showing a
table of `grimo_cost` rows for the active session and a session-total
strip. Consume `TurnTokenStreamed` events to increment.

**Dependencies.** S014.

**SBE criteria.**

- **AC1** After 3 turns with mixed providers, the page shows 3 rows
  and a correct session total (sum of `usd_cents`).
- **AC2** Page renders without JS; SSE updates the total live
  when a new turn completes.

**Estimation.** Tech 1 · Uncert 1 · Deps 2 · Scope 2 · Test 1 · Rev 1 = **8 / XS**

---

### S020 — Module boundary verification + CI wiring · XS (7)

**Description.** Promote `ModuleArchitectureTest` (S002) to a
required CI step. Add a Gradle `modulithVerify` task wrapping it. Wire
the nightly CI to publish the module canvas HTML.

Covers **PRD AC10**.

**Dependencies.** S002.

**SBE criteria.**

- **AC1** `./gradlew modulithVerify` is an alias for running that
  test; a failing module graph fails the task.
- **AC2** CI PR gate runs `modulithVerify`; a deliberate boundary
  violation on a fixture branch fails the PR.
- **AC3** Nightly build publishes `build/spring-modulith-docs/`
  as a CI artifact.

**Estimation.** Tech 1 · Uncert 1 · Deps 1 · Scope 1 · Test 2 · Rev 1 = **7 / XS**

---

## Milestone 7: Native hardening (Phase 7)

**Goal.** Production-grade native binary (fast startup, low RSS,
single-file ship). Delivers the ambition in D3 without gating v1 on it.

**Done when.** S021a, S021b, S021c completed and the native binary
passes the nightly smoke-test for 5 consecutive nights.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S021a | Static `GrimoRuntimeHints` registrar | M (13) | 🔲 |
| S021b | `ProcessBuilderSandboxAdapter` (native-safe) | M (13) | 🔲 |
| S021c | Nightly `nativeCompile` CI job (hardening) | XS (7) | 🔲 |

### S021a — Static RuntimeHints registrar · M (13)

**Description.** Implement `io.github.samzhu.grimo.native.GrimoRuntimeHints
implements RuntimeHintsRegistrar`. Register proxy / reflection /
resource hints for: `AgentClient` + per-provider `*AgentModel`
interfaces; Jackson DTOs used by CLI I/O; Thymeleaf template
resources; H2 JDBC + session-jdbc schema; Modulith's
`ApplicationModuleInformation` (but NOT `verify()` at runtime).
Register via `@ImportRuntimeHints`.

**Dependencies.** S016 (skills registry reflection pattern is
known).

**SBE criteria.**

- **AC1** `./gradlew nativeCompile` succeeds with zero `reflection
  config` warnings from the Spring AOT processor.
- **AC2** Tracing-agent-generated hints merged into
  `src/main/resources/META-INF/native-image/` are reviewed and
  committed.
- **AC3** A native binary starts and answers `/actuator/health`
  within 1s and < 150 MB RSS on an Apple Silicon dev machine.

**Estimation.** Tech 3 · Uncert 3 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **13 / M**

---

### S021b — ProcessBuilderSandboxAdapter · M (13)

**Description.** Native-safe impl of `SandboxPort` that shells out to
`docker run --mount type=bind,src=<worktree>,dst=/work
--security-opt=no-new-privileges --cap-drop=ALL
--read-only --tmpfs /tmp <image> sleep infinity` via
`ProcessBuilder`. Parses `docker inspect` output for lifecycle.
Selected automatically when `NativeDetector.inNativeImage()`.

**Dependencies.** S012.

**SBE criteria.**

- **AC1** Under `-Dspring.aot.enabled=true`,
  `ProcessBuilderSandboxAdapter` is the active bean.
- **AC2** S013's sub-agent test suite passes against the
  `process` backend (same ACs, different impl).
- **AC3** No Testcontainers class is reachable from the native
  image (verified via `native-image --trace-class-initialization`
  or equivalent report).

**Estimation.** Tech 3 · Uncert 2 · Deps 2 · Scope 2 · Test 3 · Rev 1 = **13 / M**

---

### S021c — Nightly nativeCompile CI job · XS (7)

**Description.** GitHub Actions workflow from `qa-strategy.md` §8,
promoted to a required nightly job. On failure, open a
`native-regression` issue tagged for the next sprint.

**Dependencies.** S021a, S021b.

**SBE criteria.**

- **AC1** Workflow runs on schedule; builds native, starts binary,
  curls `/actuator/health`, and archives the binary as an artifact.
- **AC2** On failure, `gh issue create` auto-opens an issue with
  the failing log attached.
- **AC3** Five consecutive passing nights → M7 done-condition met.

**Estimation.** Tech 1 · Uncert 1 · Deps 2 · Scope 1 · Test 1 · Rev 1 = **7 / XS**

---

## Milestone 8: Integration & release (Phase 8)

**Goal.** Full stubbed E2E coverage + release-readiness checklist.

**Done when.** S022 completed.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S022 | End-to-end integration test suite with stubbed CLIs | S (10) | 🔲 |

### S022 — E2E integration suite · S (10)

**Description.** One `@SpringBootTest` per user-visible scenario
(AC1–10 from PRD) using the `StubAgentClientAdapter` for each
provider. Covers: cost routing (AC1, AC2), CLI switch (AC3), read-only
(AC4), sub-agent isolation (AC5), fail-soft boot (AC6, already in
S004), jury (AC7), skill distillation (AC8), memory (AC9), module
boundaries (AC10).

**Dependencies.** S019 (for cost telemetry assertions), S018 (for
distillation), S015 (jury), S009 (switch), S013 (subagent).

**SBE criteria.**

- **AC1** Every PRD AC has at least one `@DisplayName("ACn ...")`
  test in `src/test/java/com/grimo/integration/`.
- **AC2** `docs/grimo/scripts/verify-spec-coverage.sh docs/grimo/PRD.md`
  (via a special mode that inspects PRD instead of a spec) reports
  "all covered".
- **AC3** Suite runs in under 10 minutes on a laptop.

**Estimation.** Tech 1 · Uncert 1 · Deps 3 · Scope 2 · Test 3 · Rev 1 = **10 / S**

---

## Summary

| Phase | Specs | Total points |
| --- | --- | --- |
| M0 Foundation | S000, S001, S002, S003 | 7 + 7 + 9 + 10 = 33 |
| M1 Single-CLI | S004, S005, S006 | 12 + 12 + 13 = 37 |
| M2 Multi-CLI + switch | S007, S008, S009, S010 | 10 + 10 + 15 + 9 = 44 |
| M3 Sub-agent | S011, S012, S013 | 9 + 13 + 14 = 36 |
| M4 Router & jury | S014, S015 | 9 + 13 = 22 |
| M5 Harness extras | S016, S017, S018 | 13 + 12 + 14 = 39 |
| M6 Observability | S019, S020 | 8 + 7 = 15 |
| M7 Native hardening | S021a, S021b, S021c | 13 + 13 + 7 = 33 |
| M8 Release | S022 | 10 |
| **Total** | **23 specs** | **269 points** |

One **L-sized** spec (S009 CLI switch) requires manual QA via
`/verifying-quality`. No XL specs; all sized to fit a single
`/planning-spec` → `/planning-tasks` loop.

## Next action

Immediate handoff: `/planning-spec S000`.
