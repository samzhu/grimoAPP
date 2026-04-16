# Grimo — Spec Roadmap (v2 · re-prioritized 2026-04-16)

**Status:** v0.2 · **Date:** 2026-04-16
**Source:** `docs/grimo/PRD.md` + `architecture.md`

> **Re-plan note.** This roadmap was rewritten on 2026-04-16 to focus
> the MVP on a single vertical-slice proof: **container ops →
> containerized CLI → CLI config → main-agent chat → task dispatch →
> skill management → skill injection into sub-agent**. Every other
> capability (web UI, persistent session, CLI switch, cost router,
> jury, memory, native hardening, E2E test suite, …) is pushed to
> the Backlog below. When a backlog item is promoted, it gets a
> fresh spec id and a fresh grill-me loop.

> Estimation scale (six-dimension rubric, each 1–3):
> `Tech risk · Uncertainty · Dependencies · Scope · Testing · Reversibility`
> 6–8 → XS · 9–11 → S · 12–14 → M · 15–16 → L · 17–18 → XL (must decompose)

## Dependency graph (MVP)

```
                                  S000 ✅
                                    │
                                    ▼
                                  S001 ── S002
                                    │      │
                                    ▼      ▼
                                  S003 (container ops)
                                    │
                         ┌──────────┼──────────┐
                         ▼          ▼          ▼
                       S004  (runtime image with 3 CLIs)
                         │
                         ▼
                       S005  (CLI adapter via docker exec)
                         │
                         ▼
                       S006  (CLI config research + policy)
                         │
                         ▼
                       S007  (main-agent CLI passthrough)
                         │
                         ▼
                       S008 (dispatch protocol) ─┐
                         │                       │
                         ▼                       │
                       S009 (worktree mgmt)      │
                         │                       │
                         ▼                       │
                       S010 (sub-agent lifecycle)│
                                                 │
                       S011 (skills registry) ◄──┘
                         │
                         ▼
                       S012 (skill → container install)
```

## Milestone map (MVP)

| M# | Name | Prio (user) | Specs | Goal |
| --- | --- | --- | --- | --- |
| M0 | Foundation | — | S000–S002 | Buildable Spring Boot 4.0 modulith skeleton on JDK 25 |
| M1 | Container ops | 能操作容器 | S003 | Grimo can spawn / exec / bind-mount / stop Docker containers from Java |
| M2 | Containerized CLI | 能在容器內用 3 個 CLI | S004–S005 | `grimo-runtime` image ships with `claude` + `codex` + `gemini`; Java adapter invokes them via `docker exec` |
| M3 | CLI configuration | 研究 CLI 配置 | S006 | Claude-Code memory off, per-provider config conventions applied to all containerized invocations |
| M4 | Main-agent chat | main-agent 跟使用者對話 | S007 | `grimo chat` → containerized claude-code ↔ user terminal (CLI passthrough) |
| M5 | Task dispatch | 派送任務給 sub-agent | S008–S010 | Main-agent structurally delegates; Grimo spawns sub-agent container with a worktree; diff returned to user |
| M6 | Skill management | 管理 skill | S011 | Grimo lists/enables/disables skills under `~/.grimo/skills/` |
| M7 | Skill injection | 派送前安裝 skill 到 sub-agent | S012 | Relevant skills copied into sub-agent container before task runs |

---

## Milestone 0: Foundation

**Goal.** Buildable scaffold; Modulith verify green on empty module graph.
**Done when.** S000, S001, S002 all ✅.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S000 | Project Init — Gradle KTS + Spring Boot 4.0 scaffold | XS (7) | ✅ |
| S001 | Core domain primitives + GrimoHomePaths | XS (7) | ⏳ Design |
| S002 | Module skeleton + Modulith verify green | S (9) | 🔲 |

### S001 — Core domain primitives + GrimoHomePaths · XS (7)

**Description.** Create the `io.github.samzhu.grimo.core.domain`
package: records for `SessionId`, `TurnId`, `TaskId`, `CorrelationId`,
`AgentRole` (enum: `MAIN` / `SUB` / `JURY_MEMBER`), `ProviderId`
(enum: `CLAUDE` / `CODEX` / `GEMINI`), `NanoIds` generator, and
`GrimoHomePaths` utility. Zero Spring annotations on any of these.
**`Cost` is NOT in this spec** — it is owned by whatever spec later
promotes cost telemetry out of backlog.

**Dependencies.** S000 ✅.

**SBE.** See the in-progress spec file at
`docs/grimo/specs/2026-04-16-S001-core-domain-primitives.md` for the
full acceptance criteria (AC-1 SessionId 21-char NanoID · AC-2
GrimoHomePaths.memory() with `grimo.home` + `$GRIMO_HOME` override ·
AC-3 ArchUnit "no Spring in domain").

**Estimation.** Tech 1 · Uncert 1 · Deps 1 · Scope 2 · Test 1 · Rev 1 = **7 / XS**

### S002 — Module skeleton + Modulith verify green · S (9)

**Description.** Declare `package-info.java` with
`@ApplicationModule` for every module named in `architecture.md` §2
(`core`, `sandbox`, `cli`, `agent`, `subagent`, `skills`, `web` (stub),
`native` (stub)). `core` is `type = Type.OPEN`. Wire
`spring-modulith-starter-core` + `spring-modulith-starter-test`. Add
`ModuleArchitectureTest` that runs
`ApplicationModules.of(GrimoApplication.class).verify()` and generates
the module canvas.

**Dependencies.** S001.

**SBE (draft).**
- **AC-1** `./gradlew test` runs `ModuleArchitectureTest` which
  passes `ApplicationModules.verify()` on the current module graph.
- **AC-2** Illegal cross-module reference (tested on a throwaway
  branch) fails the test with a clear `Violations detected:` message.

**Estimation.** Tech 1 · Uncert 1 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **9 / S**

---

## Milestone 1: Container operations (priority "能操作容器")

**Goal.** Java code can spawn Docker containers, exec commands, bind-mount host dirs, and clean up — all with deterministic tests.
**Done when.** S003 ✅.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S003 | `Sandbox` port + Testcontainers adapter with bind-mount | M (13) | 🔲 |

### S003 — Sandbox port + Testcontainers adapter · M (13)

**Description.** Define `SandboxPort` (methods: `spawn(SpawnSpec) → Sandbox`, `exec(Sandbox, Command) → ExecResult`, `close(Sandbox)`). Default impl `TestcontainersSandboxAdapter` uses `GenericContainer<>(...).withWorkingDirectory("/work").withFileSystemBind(hostPath, "/work", BindMode.READ_WRITE).withCommand("sleep","infinity")` per PRD D9 — **not** `DockerSandbox`. All tests `@DisabledInNativeImage` (Testcontainers is JVM-only per `architecture.md`).

**Dependencies.** S002.

**SBE (draft).**
- **AC-1** `spawn` with a prepared host dir produces a container whose `/work` is the bind-mounted host dir (asserted via `exec "ls -la /work"`).
- **AC-2** Writes inside the container appear on the host immediately.
- **AC-3** Two parallel sandboxes cannot see each other's bind-mounts.
- **AC-4** `close` stops and removes the container; host dir survives.

**Estimation.** Tech 3 · Uncert 2 · Deps 2 · Scope 2 · Test 3 · Rev 1 = **13 / M**

---

## Milestone 2: Containerized CLI (priority "能在容器內用 3 個 CLI")

**Goal.** A Grimo-managed Docker image ships `claude-code`, `codex`, and `gemini` CLIs; a Java adapter invokes each via `docker exec`.
**Done when.** S004, S005 ✅.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S004 | `grimo-runtime` Docker image with 3 CLIs pre-installed | S (10) | 🔲 |
| S005 | `AgentCliAdapter` via `docker exec` (containerized adapter) | M (12) | 🔲 |

### S004 — `grimo-runtime` Docker image · S (10)

**Description.** Dockerfile under `docker/runtime/` produces an image installing Node.js (needed by claude-code), Python (codex), and Google Cloud SDK components as required (gemini). Publishes as `grimo-runtime:<version>` locally via a Gradle task (`./gradlew buildRuntimeImage`). Documents exact install commands + versions in a README adjacent to the Dockerfile.

**Dependencies.** S003.

**SBE (draft).**
- **AC-1** `./gradlew buildRuntimeImage` succeeds and tags `grimo-runtime:<version>` locally.
- **AC-2** `docker run --rm grimo-runtime:<version> claude-code --version` prints a version.
- **AC-3** Same for `codex --version` and `gemini --version`.
- **AC-4** Image size < 1 GB (soft target; documented).

**Estimation.** Tech 2 · Uncert 2 · Deps 2 · Scope 2 · Test 1 · Rev 1 = **10 / S**

### S005 — `AgentCliAdapter` via `docker exec` · M (12)

**Description.** Port `AgentCliPort` with `stream(SpawnSpec, Prompt): Flux<Token>`. Default impl uses `SandboxPort` from S003 to start a `grimo-runtime` container, then `docker exec -i <container> <cli> ...` to pipe the prompt, capturing stdout streamingly. Supports all three providers (CLAUDE / CODEX / GEMINI) selected via `ProviderId`. Stub impl `StubAgentCliAdapter` for tests.

**Dependencies.** S004.

**SBE (draft).**
- **AC-1** `StubAgentCliAdapter.stream("hello", CLAUDE)` returns a `Flux<Token>` with canned tokens for test use.
- **AC-2** Real adapter against a locally-running `grimo-runtime` container passes a manual integration test (skipped in CI without Docker).
- **AC-3** Missing CLI (e.g., CLI binary not installed in image) surfaces a clean `CliNotFoundException`, not a raw docker-exec stderr dump.

**Estimation.** Tech 2 · Uncert 3 · Deps 3 · Scope 2 · Test 2 · Rev 2 = **14 / M**

---

## Milestone 3: CLI configuration (priority "研究 CLI 配置")

**Goal.** Document each CLI's config surface; apply Grimo's harness policy (e.g., Claude-Code memory off) to every containerized invocation.
**Done when.** S006 ✅.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S006 | CLI configuration research + harness policy application | S (11) | 🔲 |

### S006 — CLI configuration research + policy · S (11)

**Description.** Phase 1: WebFetch official docs for each CLI's config (env vars, config file, CLI flags). Document findings in a reference doc (`docs/grimo/cli-config-matrix.md`). Phase 2: express Grimo's policy as a reusable `CliInvocationOptions` record that each adapter applies at `docker exec` time. Specific policies in MVP: **Claude-Code memory disabled** (no CLAUDE.md auto-read, no project-level memory), **API-key / credential store pass-through** from host `~/.claude` / `~/.codex` / `~/.gemini` read-only mounts, **no telemetry** if each CLI exposes a toggle.

**Dependencies.** S005.

**SBE (draft).**
- **AC-1** `docs/grimo/cli-config-matrix.md` exists and lists every config surface for all 3 CLIs with linked official-docs URLs.
- **AC-2** When `AgentCliAdapter` invokes claude-code via S005 with `harness=true`, the spawned container has memory disabled (asserted by running a known-memory-trigger prompt and observing no memory retrieval in the reply).
- **AC-3** Adapter never crashes if a credential dir is absent on the host; surfaces a clean `CredentialsNotFoundException`.

**Estimation.** Tech 2 · Uncert 3 · Deps 2 · Scope 2 · Test 1 · Rev 1 = **11 / S**

---

## Milestone 4: Main-agent chat (priority "main-agent 跟使用者對話")

**Goal.** `grimo chat` opens a CLI passthrough between user's terminal and a containerized `claude-code` acting as the main-agent.
**Done when.** S007 ✅.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S007 | Main-agent CLI passthrough (`grimo chat`) | S (10) | 🔲 |

### S007 — Main-agent CLI passthrough · S (10)

**Description.** Spring Boot main-class entrypoint detects `chat` sub-command. Spawns a `grimo-runtime` container running `claude-code` with harness config from S006. Pipes user's stdin → container stdin; container stdout → user stdout (streaming line-by-line). Session ends on `Ctrl+D` or `/exit`. **No persistence in MVP** — each `grimo chat` invocation is a fresh session.

**Dependencies.** S006.

**SBE (draft).**
- **AC-1** Running `./build/libs/grimo-<v>.jar chat` (or `./gradlew bootRun --args='chat'`) starts an interactive session; typing "hello" yields a claude-code response.
- **AC-2** `/exit` or `Ctrl+D` cleanly stops the container and returns to the host shell.
- **AC-3** If Docker is not running, Grimo prints a clear "Start Docker Desktop" message and exits non-zero — no stack trace.

**Estimation.** Tech 2 · Uncert 2 · Deps 2 · Scope 2 · Test 1 · Rev 1 = **10 / S**

---

## Milestone 5: Task dispatch (priority "派送任務給 sub-agent")

**Goal.** Main-agent declares a structured delegation; Grimo spawns a sub-agent container with a worktree; sub-agent runs CLI in YOLO mode inside the sandbox; diff returned to user.
**Done when.** S008, S009, S010 ✅.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S008 | Delegation protocol (main-agent → Grimo) | M (12) | 🔲 |
| S009 | Worktree manager (JGit) | S (9) | 🔲 |
| S010 | Sub-agent lifecycle + diff review | M (13) | 🔲 |

### S008 — Delegation protocol · M (12)

**Description.** Define how main-agent signals "delegate this task". MVP candidates (to be grilled at spec-planning time): (a) MCP tool that main-agent calls, Grimo intercepts; (b) structured JSON fragment in main-agent output Grimo parses; (c) explicit user command `grimo delegate "…"`. Pick one in grill-me. Produce a `TaskSpec` domain record that downstream specs consume.

**Dependencies.** S007.

**SBE — draft, to refine in planning-spec.**
- **AC-1** A known delegation trigger in main-agent's response produces a `TaskSpec` event `TaskDelegated`.
- **AC-2** Invalid / malformed trigger yields a user-visible error, not a silent drop.

**Estimation.** Tech 2 · Uncert 3 · Deps 2 · Scope 2 · Test 2 · Rev 2 = **13 / M**

### S009 — Worktree manager · S (9)

**Description.** `WorktreePort.create(TaskId, sourceBranch) → Worktree` and `.drop(TaskId)`. Uses JGit `Git.open(repo).worktreeAdd()` into `~/.grimo/worktrees/<taskId>/`. Startup sweep removes orphan worktrees.

**Dependencies.** S001 (for `TaskId`).

**SBE (draft).**
- **AC-1** `WorktreePort.create(taskId, "main")` creates a detached worktree dir with correct HEAD.
- **AC-2** `WorktreePort.drop(taskId)` removes the dir and un-registers the worktree.
- **AC-3** Startup sweep removes stale worktrees for unknown task ids.

**Estimation.** Tech 1 · Uncert 1 · Deps 2 · Scope 2 · Test 2 · Rev 1 = **9 / S**

### S010 — Sub-agent lifecycle + diff review · M (13)

**Description.** `DelegateTaskUseCase.execute(TaskSpec)`:
1. Reserve sub-agent slot (bounded by `grimo.subagent.max-concurrent`, default 2).
2. Create worktree (S009), spawn sandbox (S003) with worktree bind-mount.
3. Run inner CLI via S005 in **YOLO mode** (full write allowlist inside the sandbox).
4. Capture `git diff /work` output.
5. Print the diff to the user; on `y` → merge the worktree branch back; on `n` → drop worktree.

**Dependencies.** S003, S005, S008, S009.

**SBE (draft).**
- **AC-1** Delegating an edit-file task modifies the file inside the worktree on the host.
- **AC-2** Sibling sub-agent cannot access the first worktree.
- **AC-3** On accept, the change merges into the host repo; on reject, host repo is unchanged.

**Estimation.** Tech 2 · Uncert 2 · Deps 3 · Scope 2 · Test 3 · Rev 2 = **14 / M**

---

## Milestone 6: Skill management (priority "main-agent 管理 skill")

**Goal.** Grimo lists, enables, and loads skills from `~/.grimo/skills/`.
**Done when.** S011 ✅.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S011 | Skills registry + enable/disable commands | S (10) | 🔲 |

### S011 — Skills registry · S (10)

**Description.** Scan `~/.grimo/skills/*/SKILL.md` using JDK NIO + `YAMLFactory` for frontmatter. Build `SkillRegistryUseCase` exposing `list()`, `enable(name)`, `disable(name)`, `get(name) → Optional<Skill>`. Skills are discovered on startup + on demand (`list()` re-scans). Enablement state persisted to `~/.grimo/skills/.state.json`.

**Dependencies.** S001.

**SBE (draft).**
- **AC-1** A skill at `~/.grimo/skills/hello/SKILL.md` appears in `list()`.
- **AC-2** Invalid frontmatter logs a warning and skips that skill (does not crash).
- **AC-3** `disable("hello")` persists; after restart, `list()` still marks it disabled.

**Estimation.** Tech 2 · Uncert 2 · Deps 2 · Scope 2 · Test 1 · Rev 1 = **10 / S**

---

## Milestone 7: Skill injection into sub-agent (priority "派送前安裝 skill")

**Goal.** Before a sub-agent container runs, Grimo copies relevant enabled skills from host `~/.grimo/skills/` into the container so the inner CLI picks them up through its native skill path.
**Done when.** S012 ✅.

| # | Spec | Points | Status |
| --- | --- | --- | --- |
| S012 | Skill injection into sub-agent container | M (12) | 🔲 |

### S012 — Skill injection · M (12)

**Description.** When `DelegateTaskUseCase` (S010) spawns the sub-agent container, it:
1. Queries `SkillRegistryUseCase.listEnabled()`.
2. Filters to skills relevant to the task spec (MVP: all enabled; richer filtering is backlog).
3. For each selected skill, copies the skill directory into the sub-agent container at **the CLI's native skill path** — claude-code → `/root/.claude/skills/<name>/`, codex and gemini per their docs surveyed in S006.
4. Verifies the skill is visible to the CLI before running the task.

**Dependencies.** S006 (for per-CLI skill path), S010, S011.

**SBE (draft).**
- **AC-1** With one enabled skill `hello`, after sub-agent container starts (claude-code provider), `/root/.claude/skills/hello/SKILL.md` exists inside the container.
- **AC-2** Task run references the skill in its prompt and produces output consistent with the skill's instructions (asserted on a fixture skill).
- **AC-3** A disabled skill is NOT injected.

**Estimation.** Tech 2 · Uncert 3 · Deps 3 · Scope 2 · Test 2 · Rev 2 = **14 / M**

---

## Summary (MVP)

| Milestone | Specs | Points |
| --- | --- | --- |
| M0 Foundation | S000, S001, S002 | 23 |
| M1 Container ops | S003 | 13 |
| M2 Containerized CLI | S004, S005 | 24 |
| M3 CLI config | S006 | 11 |
| M4 Main-agent chat | S007 | 10 |
| M5 Task dispatch | S008, S009, S010 | 36 |
| M6 Skill management | S011 | 10 |
| M7 Skill injection | S012 | 14 |
| **Total** | **13 specs** | **141 points** |

Compared to the pre-re-plan v1 roadmap (23 specs / 269 points), the
MVP surface shrinks ~48% and focuses on the single vertical proof of
"containerized agent orchestration with user-managed skills". Every
other capability is available but parked.

Next action: `/planning-spec S002` (S001 is already in design).

---

## Backlog

Items below were in the v1 MVP roadmap and are now **deferred until
explicitly promoted**. Each will be re-spec'd with its own grill-me
loop when promoted; estimates below are v1 carry-overs and will be
revisited.

| Capability | Prior spec ref (v1) | Reason deferred | Rough effort |
| --- | --- | --- | --- |
| Persistent session (spring-ai-session-jdbc + H2) | old S005 | No persistence needed to prove the MVP vertical slice. MVP accepts fresh session per `grimo chat`. | M (12) |
| Web UI (Thymeleaf + HTMX + SSE) | old S003 + S006 | CLI passthrough is the MVP interface. Re-promote when a demo-worthy UI is needed. | M×2 (23) |
| CLI switch with compacted replay | old S009 | Main-agent is Claude-Code-only in MVP; multi-CLI main is a later concern. | L (15) — manual QA |
| Codex / Gemini main-agent roles | old S007, S008 | MVP main = Claude only. The Codex/Gemini CLIs are already in the `grimo-runtime` image via S004 for sub-agent use. | S×2 (20) |
| Explicit main-agent read-only tool allowlist | old S010 | Container isolation + S006 CLI config already neutralizes write paths from main-agent in MVP. Re-examine when main-agent runs outside a container or when finer per-tool gating is needed. | S (9) |
| Cost router (heuristic v1) | old S014 | No routing in MVP; main = claude-code, sub = main's choice within the image. | S (9) |
| Jury (N-way parallel review) | old S015 | Secondary feature; requires multi-CLI + comparison UI. | M (13) |
| `AutoMemoryTools` wiring | old S017 | MVP runs stateless per chat session. | M (12) |
| Skill distillation proposer | old S018 | Harness-level auto-evolution; promote only after the manual skill-management loop (S011/S012) has soak time. | M (14) |
| Cost telemetry panel + `Cost` domain type | old S019 | Pending cost router. `Cost` lands in whatever spec owns it (no `Cost` in `core.domain`). | XS (8) |
| Module boundary CI job | old S020 | S002's JUnit ArchUnit test covers it in-test; a dedicated CI job is luxury. | XS (7) |
| Native image hardening (`RuntimeHints`, `ProcessBuilderSandboxAdapter`, nightly smoke) | old S021a–c | Stretch goal per PRD D3. JVM-first in MVP. | M×2 + XS (33) |
| E2E integration test suite | old S022 | Promoted once the vertical slice has user-visible behavior worth regression-testing. | S (10) |

**Backlog policy.** An item does not jump the queue. When the user
promotes an item, re-enter `/planning-spec` with a fresh grill loop
(do NOT reuse the v1 draft acceptance criteria blindly — the
environment will have changed).
