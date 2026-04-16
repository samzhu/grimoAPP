# Grimo — Development Standards

**Status:** v0.1 · **Date:** 2026-04-16
**Applies to:** every Java source file, every Gradle script, every
markdown artifact in this repo.

> Rules here are *normative*. Deviations require an ADR under
> `docs/grimo/adr/`.

## 1. Language & build

- **Java 25** (toolchain-pinned in `build.gradle.kts`). Preview
  features: **banned** (production binary must not need
  `--enable-preview`).
- Features used idiomatically: `record`, sealed types, pattern
  matching for `switch` and `instanceof`, virtual threads (`Thread.ofVirtual`,
  `Executors.newVirtualThreadPerTaskExecutor()`), `var` for
  block-local variables *only* when the right-hand side makes the
  type obvious.
- **Gradle Kotlin DSL** (`build.gradle.kts`). Do not introduce a
  Groovy `build.gradle`. Use version catalog (`gradle/libs.versions.toml`)
  for shared versions.
- One Gradle *subproject* per Spring Modulith module is **not**
  required — v1 is a single `:app` project with packages-as-modules.

## 2. Package layout (strict)

```
io.github.samzhu.grimo
├── GrimoApplication.java           # @SpringBootApplication, @EnableAsync
├── core/                           # @ApplicationModule(type = Type.OPEN)
│   ├── package-info.java
│   └── domain/...
├── session/                        # @ApplicationModule
│   ├── package-info.java           # allowedDependencies = { "core" }
│   ├── domain/...                  # POJOs, zero Spring
│   ├── application/
│   │   ├── port/in/SessionUseCase.java
│   │   ├── port/out/SessionRepository.java
│   │   └── service/SessionService.java
│   ├── adapter/in/event/SessionEventListener.java
│   └── adapter/out/advisor/SpringAiSessionAdapter.java
├── cli/ ...
├── router/ ...
├── subagent/ ...
├── skills/ ...
├── memory/ ...
├── jury/ ...
├── cost/ ...
├── web/                             # inbound adapters (controllers, SSE)
│   ├── package-info.java            # allowedDependencies = { "core", <all in-ports> }
│   └── adapter/in/web/...
└── native/                          # GrimoRuntimeHints + native-only helpers
```

### Module boundary rules

- Each module's `package-info.java` carries `@ApplicationModule(
  displayName = "Grimo :: <Name>", allowedDependencies = { ... })`.
- **`domain/`** packages compile with no Spring on the classpath.
  Enforced by an ArchUnit test.
- **`internal/`** sub-packages are implementation details — siblings
  may not reference them.
- Use `@NamedInterface("...")` on packages that are published to other
  modules beyond the default package-as-interface.

## 3. Naming & coding conventions

- **Class naming**:
  - Use-case interface: `<Verb><Noun>UseCase` → `DelegateTaskUseCase`.
  - Use-case impl: `<Verb><Noun>Service`.
  - Outbound port: `<Noun>Port` → `SandboxPort`.
  - Adapter impl: `<Technology><Noun>Adapter` → `TestcontainersSandboxAdapter`.
  - Event (record): past tense → `SubagentCompleted`, `RouteDecided`.
  - Configuration: `<Feature>Config`.
  - Runtime hints: `<Feature>RuntimeHints`.
- **Package naming**: lowercase single words; multi-word as `subagent`
  not `sub_agent` or `sub-agent`.
- **Methods**: verbs. Queries return `Optional<T>` never `null` for
  single-entity lookups. Collections never return `null`.
- **Immutability**: domain types are `record`s or final classes.
  Mutable state is confined to `application/service/` where
  unavoidable, and then only under explicit synchronization or
  single-threaded virtual-thread dispatch.
- **Null discipline**: prefer `@org.jspecify.annotations.Nullable`
  (JSpecify 1.0 is JDK-shipped). No `javax` annotations anywhere.
- **Logging**: SLF4J with Spring Boot default. Never `System.out`.
  Log at INFO the intent, at DEBUG the parameters, at ERROR with the
  throwable + correlationId.

## 4. Dependency injection

- **Constructor injection only.** No field injection, no setter
  injection. `@Autowired` on constructors is unnecessary (Spring 4.3+
  auto-detects single constructor).
- **No `@ComponentScan` customization.** Rely on Spring Boot default
  + Modulith's package ownership.
- Configuration classes grouped under
  `<module>/adapter/out/**/Config.java` or at the module root
  if cross-cutting.

## 5. Spring AI & agent-client conventions

- **Never** call `new ClaudeAgentModel(...)` directly in business
  code. Always consume the `AgentClient` produced by the Spring AI
  auto-config, wrapped behind the module's outbound port
  (`AgentClientPort`).
- CLI-missing errors travel as typed domain events
  (`CliUnavailable`) not as Spring Boot exceptions with stack traces.
- Property keys Grimo owns use the `grimo.*` prefix. Third-party
  properties remain under their own namespace
  (`spring.ai.agents.claude-code.*`).

## 6. Thymeleaf & HTMX

- **BANNED:** `thymeleaf-layout-dialect` (Groovy runtime → native
  blocker). Use **parameterized fragments**: `th:fragment="page(content)"`.
- Templates live under `src/main/resources/templates/` with a
  mirror-of-controller-URL directory structure.
- HTMX attributes in HTML use `hx-*` (not `data-hx-*`).
- SSE token streams use a single shared `sse-connect` root per page;
  per-message swap targets are scoped by `hx-swap-oob="true"`.

## 7. Testing conventions

Detailed rationale in `qa-strategy.md`. The rules below are distilled
from upstream `spring-ai-community/agent-client`'s production-proven
strategy — see memory entry `reference_agent_client_testing.md` for
provenance.

### 7.1 Framework & slices

- Framework: **JUnit Jupiter 5.11+** + **AssertJ 3.26+** + Spring Boot Test.
- **Slices** preferred over `@SpringBootTest`:
  - `@ApplicationModuleTest` (Modulith) for per-module integration.
  - `@DataJdbcTest` for repository adapters.
  - `@WebMvcTest` for controller adapters.
  - Plain JUnit for `domain/` — no Spring.
- Full-context `@SpringBootTest` is **last resort**. When used, the
  class must end in `IT` (see §7.2).

### 7.2 Unit vs Integration — split by class-name suffix

- **`*Test.java`** — unit. Runs on every `./gradlew test`. No real
  CLI subprocess, no Docker daemon, no real credentials. Pure
  translation logic, record validation, builders, mappers.
- **`*IT.java`** — integration. Runs on `./gradlew integrationTest`
  (a dedicated task that filters by `include "**/*IT.class"`; source
  files live alongside unit tests, no separate source-set).
- Do NOT use `@Tag("integration")` — the name-based suffix is cleaner
  and matches the upstream pattern.

### 7.3 Do NOT mock CLI subprocesses

- **Never** `Mockito.mock(Process.class)` or similar for CLI pipes.
  Mocking a subprocess hides real failure modes (stdin/stdout
  encoding, timeouts, signal handling, partial streaming).
- Unit tests cover **pure logic only**: prompt serialization, JSON
  parsing, option builders, registry maps. Stub inputs, real
  translation code, real assertions.
- CLI integration tests use the **real binary on the host** (see §7.4).
  There is no fake intermediate layer.

### 7.4 CLI integration — host binary, not Testcontainers

- For CLI **functional correctness** (did claude-code actually
  produce what we expected?), invoke the **host binary** directly.
  Faster, simpler, inherits the developer's own `claude login` /
  `gemini auth` / `codex login` session — matches the P10
  subscription-native auth principle.
- Testcontainers is reserved for **sandbox-infrastructure**
  validation: did the container spawn with the right bind-mount, did
  worktree isolation hold, did cleanup remove the container. NOT for
  asserting CLI output content.
- Every class touching Testcontainers stays `@DisabledInNativeImage`.

### 7.5 Three-tier skip strategy for real-CLI ITs

Combine these per scenario; do not collapse to one:

1. **Class-level** `@DisabledIfEnvironmentVariable(named = "CI",
   matches = "true", disabledReason = "…")` — kill an entire IT
   class on CI runners where the CLI isn't installed (e.g., Codex).
2. **Opt-in** `@EnabledIfSystemProperty(named = "grimo.it.docker",
   matches = "true")` — for sandbox-infra ITs that need Docker.
3. **Graceful per-test** `Assumptions.assumeTrue(cliAvailable() &&
   credentialsPresent())` in `@BeforeEach` — the default for claude
   and gemini ITs (inherit the developer's shell auth).

Each provider module contributes a static
`<Provider>CliDiscovery.isCliAvailable()` helper used by #3.

### 7.6 TCK pattern for per-provider behavior

When ≥ 2 providers share behavioral contracts (stream tokens, surface
`CliUnavailable`, honor timeout), put the behavioral tests in an
abstract TCK class under `src/test/java/.../shared/`. Per-provider
ITs extend it and only wire the provider-specific object in
`@BeforeEach`.

Naming: `AbstractAgentCliAdapterTCK` (the TCK) →
`ClaudeAgentCliAdapterIT`, `CodexAgentCliAdapterIT`,
`GeminiAgentCliAdapterIT`.

Adding a 4th provider ≈ 30 LOC of new IT.

### 7.7 Secrets / API keys

Injected via the Gradle test task's `environment(...)` block; **never**
`System.setProperty`, **never** a checked-in `.env`:

```kotlin
tasks.withType<Test> {
    listOf("ANTHROPIC_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY")
        .forEach { k -> System.getenv(k)?.let { environment(k, it) } }
}
```

CI maps its `secrets:` straight to env. Missing key → IT self-skips
via `assumeTrue` (§7.5 tier 3).

### 7.8 Flaky-retry at task level, not in test code

For `integrationTest` only, enable
[org.gradle.test-retry](https://plugins.gradle.org/plugin/org.gradle.test-retry)
with `maxRetries = 2`. Acknowledges CLI streaming flakiness (token
races, socket resets) without polluting test bodies.

Unit tests do **NOT** retry — flaky unit tests are bugs, fix them.

### 7.9 Given / When / Then

Every test method carries a `// Given / When / Then` comment block
mirroring the SBE example in the owning spec file. The block is for
humans auditing the AC ↔ test mapping, not runtime behavior.

## 8. Error & exception handling

- Only **domain exceptions** (checked or unchecked) from `domain/`
  packages. Never let a `java.sql.*`, `java.io.*`, or `java.net.*`
  exception cross a module boundary — translate at the adapter edge.
- `@ControllerAdvice` catches domain exceptions in the web adapter
  and maps to HTMX-friendly partials (HTTP 2xx with a `HX-Retarget`
  header where appropriate, not 5xx).
- A process-level `Thread.UncaughtExceptionHandler` logs + emits a
  `grimo.uncaught` metric — tests verify it is not firing during a
  clean run.

## 9. Git & commit conventions

- **Branch**: work on `main`; feature branches named
  `spec/S<NNN>-<slug>` e.g. `spec/S003-web-shell`.
- **Commit message**: Conventional Commits flavor, prefixed by spec
  ID:
  ```
  feat(S004): wire ClaudeAgentModel behind AgentClientPort
  fix(S006): drop a pinned token when SSE stream is cancelled
  test(S010): assert main-agent tool allowlist rejects Edit
  docs(arch): note Testcontainers JVM-only stance
  ```
- One commit per logical step; never squash without a reason.
- Pre-commit hook runs `./gradlew modulith:verify` (custom task wrapping
  `ApplicationModules.verify()`). Code formatting is intentionally NOT
  gated at commit time — it is handled in one shot by an external tool
  at a later cleanup spec.
- **No force-push to `main`**. Period.

## 10. Documentation upkeep

- Every new domain term → `docs/grimo/glossary.md`.
- Every new decision that contradicts or extends a PRD decision →
  ADR in `docs/grimo/adr/ADR-NNN-<slug>.md`.
- Architecture deltas → update `architecture.md` in the same PR.
- Specs live under `docs/grimo/specs/S<NNN>-<slug>.md`; see
  `qa-strategy.md` for the required sections.

## 11. Forbidden patterns

- Reflective discovery at runtime that bypasses Spring AOT
  (`Class.forName` with a string from config, `ServiceLoader` of
  non-registered services).
- Static mutable state. Period. Use `@Component` singletons.
- `@Bean` methods that perform I/O during bean creation (violates
  P5 — boot must succeed without any CLI binary).
- Catch-all `catch (Exception e)` without a `throw` or explicit
  translation to a domain exception.
- Any use of `System.getenv(...)` / `System.getProperty(...)` outside
  `core/domain/GrimoHomePaths` and `GrimoApplication.main`.
- **Mocking a CLI subprocess** — `Mockito.mock(Process.class)`,
  stubbed `ProcessBuilder`, faked stdin/stdout streams. Test pure
  translation in `*Test`; exercise the real binary in `*IT` with a
  graceful skip (§7.3–7.5). No fake middle layer.

## 12. Code-review checklist (applied on every PR)

- [ ] `./gradlew check` green (compile + unit tests + Modulith verify).
- [ ] No new dependency without a corresponding row in
      `architecture.md` Framework Dependency Table.
- [ ] New domain term → glossary entry (zh-TW + English).
- [ ] New public API → tests cover happy path + error path + boundary.
- [ ] **CLI / subprocess adapter changes** — `*Test` covers pure
      translation; `*IT` exercises the real binary with
      `assumeTrue(cliAvailable && credentialsPresent)` in
      `@BeforeEach`. NO `Mockito.mock(Process…)`. If 2nd+ provider
      added, promote shared behavior into a TCK (§7.6).
- [ ] No new direct cross-module class reference — events or ports only.
- [ ] `package-info.java` `allowedDependencies` updated when a new
      dep is intentional.
- [ ] If new `RuntimeHints` are needed, registered in
      `io.github.samzhu.grimo.native.GrimoRuntimeHints` and exercised by the
      nightly native smoke test.
