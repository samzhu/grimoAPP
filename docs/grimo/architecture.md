# Grimo — Architecture

**Status:** v0.1 · **Owner:** Tech Lead (samzhu) · **Date:** 2026-04-16
**Source:** `docs/grimo/PRD.md` (v0.1)

---

## 1. Architectural style

- **Modular monolith** using **Spring Modulith 2.0.5**. Each bounded
  context is a top-level package with `package-info.java` carrying
  `@ApplicationModule`. Cross-module calls travel only through
  `@NamedInterface` packages; all other packages are `internal` by
  Modulith convention.
- **Hexagonal (ports & adapters)** inside each module. Every module has:
  - `domain/` — records, value objects, domain services. **Zero Spring
    annotations.** Compilable without the Spring classpath.
  - `application/port/in/` — use-case interfaces (commands/queries).
  - `application/port/out/` — repository + gateway interfaces.
  - `application/service/` — `@Service` use-case implementations. Only
    Spring-aware layer inside the module.
  - `adapter/in/**` — controllers, event listeners, CLI command handlers.
  - `adapter/out/**` — JDBC repositories, CLI wrappers, Docker clients.
  - `internal/` — implementation-only helpers (hidden from siblings).
- **Event-driven seams** via `ApplicationEventPublisher` +
  `@ApplicationModuleListener`. Cross-module coupling = domain events
  only. No direct bean references across modules except through a
  module's `@NamedInterface` port.
- **Local-first single process.** One Spring Boot app, one local JDBC
  store (H2 file mode by default), one Docker daemon on the host.

## 2. Module map

```
io.github.samzhu.grimo                                   # root (GrimoApplication)
├── core                                    # @ApplicationModule (open)
│   └── domain/ { Session, Turn, TaskId, AgentRole, Cost, ... }
│                                           # shared domain primitives
├── session                                 # SessionMemoryAdvisor wiring
├── cli                                     # CLI adapter (claude/codex/gemini)
├── router                                  # cost/complexity-aware CLI router
├── subagent                                # worktree + Docker sandbox lifecycle
├── skills                                  # SKILL.md registry + distiller
├── memory                                  # AutoMemoryTools under ~/.grimo/memory
├── jury                                    # N-way parallel review
├── cost                                    # per-turn token/cost telemetry
└── web                                     # Spring MVC + Thymeleaf + HTMX
```

`core` is marked `@ApplicationModule(type = Type.OPEN)` — every other
module may consume its public types. All other modules declare explicit
`allowedDependencies` in `package-info.java`.

### Module responsibilities & published ports

| Module | Inbound ports | Outbound ports | Emits events |
| --- | --- | --- | --- |
| `core` | — | — | — |
| `session` | `SessionUseCase` (open/resume/switch) | `SessionRepository` (from Spring AI) | `SessionOpened`, `SessionSwitched`, `TurnPersisted` |
| `cli` | `AgentConversationUseCase` | `AgentClientPort` (wraps `AgentClient`) | `TurnTokenStreamed`, `CliUnavailable` |
| `router` | `RouteUseCase` | `CliRegistryPort` | `RouteDecided` |
| `subagent` | `DelegateTaskUseCase` | `SandboxPort`, `WorktreePort` | `SubagentStarted`, `SubagentCompleted`, `SubagentFailed` |
| `skills` | `SkillRegistryUseCase`, `DistillUseCase` | `SkillStorePort` | `SkillProposed`, `SkillApproved` |
| `memory` | `MemoryUseCase` | `MemoryStorePort` | `MemoryRecorded` |
| `jury` | `JuryReviewUseCase` | `ReviewPort` (calls multiple CLIs) | `JuryVerdictReady` |
| `cost` | `CostQueryUseCase` | `CostStorePort` | — (consumes `TurnTokenStreamed`) |
| `web` | (controllers) | — | (consumes domain events for SSE push) |

## 3. Framework dependency table

All versions pinned 2026-04-16. Every "Verified" **yes** is backed by a
source-level spike (see `docs/grimo/PRD.md` risk register).

| Group / Package | Version | Primary import | Verified |
| --- | --- | --- | --- |
| `org.springframework.boot` (starter-parent) | **4.0.5** | — | yes (user-supplied) |
| `io.spring.dependency-management` (Gradle plugin) | **1.1.7** | — | yes |
| `org.graalvm.buildtools.native` (Gradle plugin) | **0.11.5** | — | yes |
| Java toolchain | **25** (LTS, GA 2025-09-16) | — | yes |
| `org.springframework.modulith:spring-modulith-bom` | **2.0.5** | `org.springframework.modulith.core.ApplicationModules` | yes |
| `org.springframework.modulith:spring-modulith-starter-core` | 2.0.5 | (auto) | yes |
| `org.springframework.modulith:spring-modulith-starter-test` | 2.0.5 | `org.springframework.modulith.test.ApplicationModuleTest` | yes |
| `org.springframework.modulith:spring-modulith-events-jdbc` | 2.0.5 | (auto) | yes |
| `org.springframework.modulith:spring-modulith-docs` | 2.0.5 | `org.springframework.modulith.docs.Documenter` | yes |
| `org.springframework.boot:spring-boot-starter-web` | 4.0.5 | `org.springframework.web.bind.annotation.*` | yes |
| `org.springframework.boot:spring-boot-starter-thymeleaf` | 4.0.5 | `org.thymeleaf.TemplateEngine` | yes |
| `org.springframework.boot:spring-boot-starter-jdbc` | 4.0.5 | `org.springframework.jdbc.core.JdbcTemplate` | yes |
| `org.springframework.boot:spring-boot-starter-actuator` | 4.0.5 | — | yes |
| `org.springframework.boot:spring-boot-starter-validation` | 4.0.5 | `jakarta.validation.*` | yes |
| `org.springframework.boot:spring-boot-devtools` (dev only) | 4.0.5 | — | yes |
| `com.h2database:h2` | 2.3.232 | `org.h2.Driver` | yes |
| `io.github.wimdeblauwe:htmx-spring-boot` | **5.1.0** | `io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxResponse` | yes |
| `io.github.wimdeblauwe:htmx-spring-boot-thymeleaf` | 5.1.0 | Thymeleaf dialect `htmx:*` | yes |
| `org.springaicommunity.agents:agent-client-core` | **0.12.2** | `org.springaicommunity.agents.client.AgentClient` | yes |
| `org.springaicommunity.agents:agent-model` | 0.12.2 | `org.springaicommunity.agents.model.AgentModel` | yes |
| `org.springaicommunity.agents:agent-claude` | 0.12.2 | `org.springaicommunity.agents.claude.ClaudeAgentModel` | yes |
| `org.springaicommunity.agents:agent-codex` | 0.12.2 | `org.springaicommunity.agents.codex.CodexAgentModel` | yes |
| `org.springaicommunity.agents:agent-gemini` | 0.12.2 | `org.springaicommunity.agents.gemini.GeminiAgentModel` | yes |
| `org.springaicommunity:spring-ai-starter-session-jdbc` | **0.2.0** | `org.springframework.ai.session.advisor.SessionMemoryAdvisor`; `org.springframework.ai.session.SessionService` | yes (separate release line from agent-client; requires Spring AI ≥2.0.0-M4) |
| `org.springaicommunity:spring-ai-session-management` | 0.2.0 | `org.springframework.ai.session.compaction.*` | yes |
| `org.springaicommunity:agent-sandbox-core` | **0.9.1** | `org.springaicommunity.sandbox.Sandbox` (SPI) | yes — on Maven Central under groupId `org.springaicommunity` (not `.agents`) |
| `org.springaicommunity:agent-sandbox-docker` | **0.9.1** | `org.springaicommunity.sandbox.docker.DockerSandbox` — **only used for ephemeral file-copy sandboxes; NOT the worktree path** (D9) | yes — same group as above |
| `org.testcontainers:testcontainers` | 1.20.4 | `org.testcontainers.containers.GenericContainer` | yes (JVM only; `@DisabledInNativeImage`) |
| `org.eclipse.jgit:org.eclipse.jgit` | 7.1.1.202506271520-r | `org.eclipse.jgit.api.Git` (worktree add/remove) | yes |
| `org.springframework.boot:spring-boot-starter-test` | 4.0.5 | (auto) | yes |
| `org.testcontainers:junit-jupiter` | 1.20.4 | `@Testcontainers` | yes (test classpath only) |
| `com.tngtech.archunit:archunit-junit5` | 1.3.0 | via Modulith verify API | yes (pulled transitively) |

> **Principle reminder:** every version here is the latest *stable* one
> validated against Boot 4.0.5 as of 2026-04-16. `spring-ai-session`
> is pre-1.0 by design — pin to `0.2.0` release; do not track
> `0.3.0-SNAPSHOT`.

### Banned / denied libraries

| Library | Reason |
| --- | --- |
| `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect` | Groovy runtime → GraalVM native blocker (D16). Use parameterized Thymeleaf fragments. |
| `spring-boot-starter-webflux` on the web adapter path | Open AOT regression on functional routes (R1); stay on MVC + virtual threads. |
| `cglib-based` manual proxying | Boot 4 native unsupports runtime CGLIB (#49350). |
| Testcontainers *inside* the native image | Not runtime-native-safe; wrap usages with `@DisabledInNativeImage`. |

## 4. Runtime & concurrency

- **Virtual threads on by default** via
  `spring.threads.virtual.enabled=true` (Spring Boot 4 first-class).
  Blocking I/O (ProcessBuilder, JDBC, HTTP) runs on virtual carriers.
  JEP 491 eliminated `synchronized` pinning on JDK 24+, so GraalVM for
  JDK 25 inherits this — no carrier starvation issue tracked.
- **Streaming path:** Spring AI `ChatClient.stream().content()` returns
  `Flux<String>`; bridged to `SseEmitter` by
  `adapter/in/web/ChatSseController`. HTMX page consumes via
  `<div hx-ext="sse" sse-connect="..." sse-swap="token">` — no bespoke
  JS.
- **Sub-agent dispatch:** virtual-thread-per-task executor; at most
  `grimo.subagent.max-concurrent` (default 2) sandbox containers at a
  time to cap host resource pressure.
- **Timeouts:** every outbound call has an explicit `Duration` timeout
  configured via `grimo.cli.<provider>.timeout` (default 5m), passed to
  `AgentClient` builders.

## 5. Storage & filesystem layout

### 5.1 GrimoHome (`~/.grimo/`)

```
~/.grimo/
├── config/
│   └── application.yml          # user overrides (merged by Boot)
├── db/
│   └── grimo.mv.db              # H2 file-mode DB (session, events, cost)
├── memory/
│   ├── MEMORY.md                # index (AutoMemoryTools convention)
│   └── <topic>.md               # user/feedback/project/reference entries
├── skills/
│   └── <skill-name>/
│       └── SKILL.md             # skill definition
├── sessions/                    # user-facing session exports (optional)
├── worktrees/
│   └── <task-id>/               # git worktree for one sub-agent task
└── logs/
    └── grimo.log
```

`GrimoHomePaths` (in `core`) is the single authority for path
resolution; no other module constructs paths from `System.getProperty
("user.home")` directly.

### 5.2 Relational store

- **Default:** H2 file mode — `jdbc:h2:file:~/.grimo/db/grimo;MODE=PostgreSQL;AUTO_SERVER=TRUE`
- **Opt-in:** PostgreSQL by setting `grimo.datasource.url` (and
  `username`/`password`) — session-jdbc has a matching
  `PostgresJdbcSessionRepositoryDialect`.
- **Tables** (owned by `spring-ai-session-jdbc` schema.sql):
  `AI_SESSION`, `AI_SESSION_EVENT` (+ primary-key/index definitions).
- **Modulith event publication tables** owned by
  `spring-modulith-events-jdbc`: `event_publication` +
  `event_publication_archive`.
- **Grimo-owned tables** (Flyway-managed under
  `src/main/resources/db/migration/`):
  - `grimo_cost` — per-turn token + $ telemetry (columns: `turn_id`,
    `session_id`, `provider`, `tokens_in`, `tokens_out`, `usd_cents`,
    `created_at`).
  - `grimo_subagent_task` — task lifecycle (`id`, `session_id`,
    `worktree_path`, `status`, `started_at`, `finished_at`,
    `exit_code`).
  - `grimo_skill_proposal` — pending distillations (`id`, `name`,
    `source_session_ids`, `draft_md`, `status`, `created_at`).

## 6. Key data flows

### 6.1 Single user prompt → streamed answer (happy path)

```
Browser       web            session         router          cli           provider CLI
  │   POST     │                │               │              │                │
  │──────────▶ │                │               │              │                │
  │            │─ classify ───▶│◀── getEvents ─│              │                │
  │            │                │  (advisor)    │              │                │
  │            │◀── RouteDecided (evt)          │              │                │
  │            │─ converse ───────────────────▶ │              │                │
  │            │                │               │─ stream ────▶│   ProcessBuilder
  │            │                │               │              │── claude CLI ▶│
  │            │◀── tokens (SSE) ───────────────│◀─── tokens ──│                │
  │◀── swap ── │                │               │              │                │
  │            │─ persistTurn ──▶ SessionMemoryAdvisor.after()                  │
```

### 6.2 Main-agent CLI switch (AC3)

```
 user → /grimo switch codex  →  web.SwitchController
                               ↓
                           session.switch(sessionId, codex)
                               ↓
                      SessionMemoryAdvisor pre-load events
                               ↓
                    CompactionStrategy.compact(events)
                               ↓
            router.bind(sessionId, codex)   ── RouteDecided evt ──▶ cli
                               ↓
     next user message → cli(codex).prompt(compactedSystem + userMessage)
```

Compaction strategy: `SlidingWindowCompactionStrategy` with
`maxEvents=20` by default; swappable for
`RecursiveSummarizationCompactionStrategy` when a session exceeds
`grimo.session.summarize-after-tokens`.

### 6.3 Main-agent delegates a write-task → sub-agent

```
 main-agent decides: "delegate: refactor OrderService"
            ↓ (structured task output)
 subagent.DelegateTaskUseCase.execute(taskSpec)
   ├─ WorktreePort.create(taskId)          → ~/.grimo/worktrees/<id>/
   ├─ SandboxPort.spawn(image, mount=/work:<worktree> RW)
   │     (Testcontainers GenericContainer.withFileSystemBind)
   ├─ inner agent runs with full Read/Write/Bash inside /work
   ├─ SandboxPort.exec("git diff") → diff returned to main-agent
   └─ user reviews diff → accept → worktree merge back
```

Sub-agent cannot read host paths outside `/work` (AC5 verified by
test; see `qa-strategy.md`).

## 7. Error-handling strategy

- **`CliUnavailableException`** — raised by `cli` adapter when the
  underlying binary is missing at call time. Not a 500; surfaces to the
  user as `"codex CLI not detected — run 'brew install codex' or
  /grimo switch claude"` via HTMX toast. Boot always succeeds (P5).
- **`SandboxSpawnException`** — Docker daemon down or image pull
  failed. User sees an actionable message; `sub-agent` degrades by
  refusing to start the task (never silently falls back to main-agent).
- **`CompactionBudgetException`** — compacted history + new message
  exceeds the target CLI's context budget. Router escalates to a
  higher-context provider or asks the user to shorten.
- **All domain events carry a `correlationId`** (one per user turn) so
  the Web UI SSE channel can scope token streams.

## 8. Native image strategy

- **JVM first (v1).** `./gradlew bootRun` and `./gradlew bootJar` are
  the primary delivery artifacts. JDK 25 + virtual threads.
- **`native` profile day-1.** `./gradlew nativeCompile` must produce a
  binary; `./gradlew nativeRun` must start and serve
  `/actuator/health`. This is a **nightly CI gate**, not a PR gate
  — keeps hint gaps surfaceable without blocking feature delivery.
- **Testcontainers is JVM-only.** `sub-agent` has two implementations:
  1. `TestcontainersSandboxAdapter` — default, JVM; used for tests and
     the JVM runtime.
  2. `ProcessBuilderSandboxAdapter` — native-safe; shells out to
     `docker run --mount type=bind,src=<worktree>,dst=/work ...`
     directly. Slightly less ergonomic (no automatic cleanup lifecycle)
     but avoids the Testcontainers runtime dependency. Selected via
     `grimo.subagent.backend=process|testcontainers` (default
     `testcontainers`, auto-flipped to `process` when `NativeDetector.inNativeImage() == true`).
- **Static hints registrar** (`io.github.samzhu.grimo.native.GrimoRuntimeHints`
  implementing `RuntimeHintsRegistrar`) registers:
  - Proxies for `AgentClient`, per-provider `*AgentModel` interfaces.
  - Jackson types for CLI I/O DTOs.
  - Thymeleaf template paths `classpath:/templates/**/*.html`.
  - H2 JDBC driver + session-jdbc schema resources.
- **`ApplicationModules.verify()`** stays in JVM tests only.
  `spring.modulith.runtime.verification-enabled=false` in production.

## 9. Security posture (MVP)

- **Bind-host:** `server.address=127.0.0.1` locked in
  `application.yml` so the Web UI is never exposed on a LAN by accident.
- **CSRF:** Spring Security not added in v1 (D15), but Thymeleaf
  forms carry a `X-CSRF` synchronizer token checked by an
  `HandlerInterceptor`.
- **Sandbox**: `--read-only` root filesystem with tmpfs for `/tmp`,
  `--security-opt=no-new-privileges`, `--cap-drop=ALL` unless a skill
  explicitly needs a capability, `--network=bridge` with an allowlist
  proxy (default: deny-all except user-declared domains in
  `grimo.sandbox.allowlist`).
- **Git operations** run as the host user; sub-agents cannot push
  (mount is per-worktree; no credential files mounted).

## 10. Observability

- **Actuator** endpoints on `127.0.0.1:8080/actuator`, restricted to
  `health`, `info`, `modulith`, `metrics` for MVP.
- **Per-turn cost** streamed alongside tokens via SSE event name
  `cost`. Aggregated view at `/grimo/cost`.
- **Session event log** (from `SessionMemoryAdvisor`) is replayable
  and exportable to `~/.grimo/sessions/<session-id>.jsonl`.

## 11. Deployment

- **Dev**: `./gradlew bootRun --args='--spring.profiles.active=dev'`.
  DevTools live reload, H2 file DB auto-init.
- **User install (JVM)**: `./gradlew bootJar` →
  `java -jar grimo-<v>.jar`. Requires JDK 25 runtime.
- **User install (native, M7)**: `./gradlew nativeCompile` →
  single-file `build/native/nativeCompile/grimo`. Requires Docker
  daemon reachable for sub-agent isolation.
- **Config precedence** (high→low): environment variables →
  `~/.grimo/config/application.yml` → packaged defaults.

## 12. Open technical questions resolved in later ADRs

These remain open and will become ADRs if a discovery during spec
work forces a divergence from assumptions here:

- Exact cost-router heuristic table (task class × model).
- Whether `RecursiveSummarizationCompactionStrategy` requires a
  dedicated "summarizer" model (e.g., Gemini 2.5 Flash) or reuses the
  active CLI.
- Long-running sub-agent observability (structured log scraping vs
  OpenTelemetry inside the sandbox).
- Skill-distillation trigger policy (timer vs on-success hook vs
  nightly).

---

*Refer to `development-standards.md` for code-level conventions and
`qa-strategy.md` for the verification pipeline.*
