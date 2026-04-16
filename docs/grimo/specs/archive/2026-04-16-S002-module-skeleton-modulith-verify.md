# S002: Module skeleton + Modulith verify passes

> Spec: S002 | Size: S (9) | Status: ✅ Done
> Date: 2026-04-16

---

## 1. Goal

Lay down the **Spring Modulith module boundary baseline** for the MVP
verticle slice. Every MVP module that has an owning spec gets a
`package-info.java` carrying `@ApplicationModule`; `core` is `OPEN`
(its types are referenced everywhere); the rest start with
`allowedDependencies = {}` and are expanded by their owning specs as
real cross-module use emerges.

A `ModuleArchitectureTest` runs `ApplicationModules.of(GrimoApplication.class).verify()`
on every `./gradlew test`. A second `DocumentationTests` produces the
module canvas + PlantUML diagrams under `build/spring-modulith-docs/`
(per PRD acceptance language).

S002 also codifies — once, in this spec and propagated via doc-sync to
`architecture.md` + `development-standards.md` — the **Cross-Module
Communication Policy** that every later spec must follow. The policy is
expressed in **Spring Modulith terms** (`@NamedInterface`,
`allowedDependencies`, events). Hexagonal layering inside a module
(`port/in`, `port/out`, `adapter/`) is a separate concern governed by
`development-standards.md` §2 and is owned by each module's spec, not
this one.

**Out of scope.**

- No business logic, no service classes, no adapter classes. The closed
  modules ship as **empty packages with a single `package-info.java`**.
- **No `web` / `nativeimage` module stubs.** Both are Backlog (PRD UI
  deferred; PRD D3 native hardening deferred). They land when their
  owning spec ships, not now (forbidden file-plan rule).
- No `@NamedInterface` annotation on any package yet — added by the
  first owning spec that actually exposes a port or event (likely S003
  for `sandbox`).
- No `events/` sub-packages — same reason; created by the first
  publisher.
- No Modulith pre-commit Gradle task wrapper — covered by AC-1 inside
  `./gradlew test` already; a dedicated `modulith:verify` task is
  Backlog (`spec-roadmap.md` Backlog row "模組邊界 CI 任務").

## 2. Approach

### 2.1 Decisions

| # | Decision | Chosen | Why |
| --- | --- | --- | --- |
| D1 | **Module list** | **6 modules**: `core` (OPEN) + `sandbox`, `cli`, `agent`, `subagent`, `skills` | Each has an owning MVP spec immediately downstream (S003, S005, S007, S008–S010, S011). Backlog modules (`session`, `router`, `memory`, `jury`, `cost`, `web`, `nativeimage`) do **not** ship a stub — they land with their owning spec when promoted. Forbidden file-plan rule. |
| D2 | **`allowedDependencies` initial value** | `{}` for every closed module | Strict empty white-list. Each owning spec adds itself to consumer modules' `allowedDependencies` as real cross-module use emerges. Forces every cross-module reference to be explicitly justified by the spec that introduces it. |
| D3 | **`core` shape** | `@ApplicationModule(type = Type.OPEN, displayName = "Grimo :: Core")` | Per research: `Type.OPEN` allows siblings to access internals without restriction and excludes the module from cycle detection (citation: `docs.spring.io/spring-modulith/docs/2.0.5/api/org/springframework/modulith/ApplicationModule.Type.html`). Right idiom for a shared domain library that everyone depends on. |
| D4 | **Cross-Module Communication Policy** | Codified in §2.2; doc-synced to `architecture.md` §1 + new `development-standards.md` §13 | Single authoritative source. Policy in **Modulith terms** only — Hexagonal layering stays intra-module per dev-standards §2. |
| D5 | **`native` keyword** | Backlog module renamed `nativeimage` (in `development-standards.md` doc-sync) | `native` is a Java reserved word — illegal as package name. Discovered while planning S002. The module itself is Backlog (no MVP code), so this is a doc-only fix. |
| D6 | **Test split** | Two test classes: `ModuleArchitectureTest` (verify) + `DocumentationTests` (canvas) | Per Modulith reference docs idiomatic split — verification is a build gate, documentation is a side-effect artefact; different failure semantics. Citation: `docs.spring.io/spring-modulith/reference/documentation.html`. Both share `ApplicationModules.of(GrimoApplication.class)` via a static field. |
| D7 | **Documenter dependency** | No new dependency | `spring-modulith-docs` is already bundled inside `spring-modulith-starter-test` (already on Grimo's classpath via S000). Citation: `docs.spring.io/spring-modulith/reference/appendix.html`. |
| D8 | **AC-2 (negative) operationalization** | One-time manual verification, recorded in §7 Findings | Writing a permanent test that intentionally violates would always red the build. The `verify()` rejection capability is upstream-tested by Modulith's own 1.4k+ test suite — Grimo only needs to prove **once** that the alarm rings on this codebase. The captured `Violations detected:` message becomes audit trail. |
| D9 | **Spec file naming for Backlog modules in docs** | `architecture.md` §2 keeps an explicit "Backlog modules (promote on demand)" appendix listing the 7 deferred modules | Avoid destructive deletion. Promotion is reversible. Future readers see both "what's live" and "what's planned". |

### 2.2 Cross-Module Communication Policy

This is the policy S002 codifies. **Spring Modulith mechanics only** —
Hexagonal port/adapter layering inside a module is a separate concern
(see `development-standards.md` §2).

Three legal patterns; everything else is a Modulith violation:

| Pattern | When to use | Mechanism | `allowedDependencies` impact |
| --- | --- | --- | --- |
| **A. `core` types** | Domain primitives (`SessionId`, `TaskId`, `AgentRole`, …) needed everywhere | Direct import of `io.github.samzhu.grimo.core.domain.*` | None — `core` is `Type.OPEN`, access not enforced |
| **B. Synchronous cross-module port call** | Consumer needs the result **now** (e.g., `subagent.DelegateTaskUseCase` calls `sandbox.SandboxPort.spawn()` and waits) | Publisher exposes the port via `@NamedInterface("api")` on its `application/port/in/` package (or whichever package holds the port) | Consumer **must** declare `allowedDependencies = { "<publisher>::api" }` |
| **C. Asynchronous event** | Publisher does not care who listens / wants to decouple (e.g., `cli` publishes `CliUnavailable` → `web` shows toast, `cost` increments counter) | Publisher puts the event record under `<module>/events/` with `@NamedInterface("events")`; subscribers write `@ApplicationModuleListener void on(EventType e)` | Consumer declares `allowedDependencies = { "<publisher>::events" }` — sees the event class only, not the publisher's internals |

**Defaults & forbidden patterns:**

- **Default to events (Pattern C).** Async + event allows the publisher
  to evolve internally without consumer impact, and supports PRD P4
  "cheap future decoupling" (e.g., extracting the sub-agent pool to a
  separate process is zero-change for publishers).
- **Direct cross-module bean injection is permanently forbidden.** No
  `@Autowired SomeOtherModuleService` across module boundaries. Only
  the named-interface port is visible.
- **No cross-module reference to `internal/` packages.** Modulith
  enforces this automatically once `internal/` packages exist; this
  spec doesn't create any.
- **`core` is the only `OPEN` module.** No future MVP module flips to
  `OPEN` without an ADR — `OPEN` opts out of cycle detection, which is
  load-bearing protection.

This policy is what the `verify()` call in AC-1 actually enforces, and
what the one-time AC-2 manual proof demonstrates (by deliberately
violating Pattern B / forbidden-direct-injection and capturing the
rejection message).

### 2.3 Challenges considered

- **"Why not also stub `web` and `nativeimage`?"** Both are pure
  Backlog. `web` has no MVP code path; `nativeimage` has zero
  `RuntimeHints` to register before Backlog native hardening lands.
  Stubbing them now creates empty `package-info.java` files that no
  spec will touch for the entire MVP — that's the textbook
  forbidden-file-plan smell. The architecture.md Backlog appendix
  records the intended package names, which is enough.
- **"Why not pre-fill `allowedDependencies` per architecture.md §2's
  intended graph?"** Predicting cross-module references before the
  consuming spec exists is exactly the over-design forbidden-file-plan
  warns about. Owning specs add one line to the consumer's
  `package-info.java` when they actually need the access — total cost
  is one PR diff line, much cheaper than a wrong prediction in S002.
- **"Why two test classes instead of one?"** Modulith reference docs
  show the split idiomatically (verification vs documentation). They
  fail for different reasons (architecture violation vs IO/disk error)
  and merging them would force `Documenter` to run even when
  `verify()` already failed.
- **"Could AC-2 be automated via `ApplicationModules.detectViolations()`
  on a fixture SourceSet?"** Possible but over-engineering for a
  one-time alarm test — see D8. If a future spec needs continuous
  negative coverage (e.g., refactor that risks regressing the policy),
  introduce the fixture SourceSet at that time.
- **"Won't an empty package (`sandbox`/`cli`/`agent`/`subagent`/`skills`
  with only `package-info.java`) confuse Modulith?"** No. Modulith
  scans for `@ApplicationModule`-annotated `package-info.class` files;
  the package itself does not need additional types to be discovered.
  Validated by the standard Modulith reference example (a brand-new
  module is allowed to be empty before the first type lands).

### 2.4 Research citations (load-bearing)

- `@ApplicationModule(displayName, allowedDependencies, type)` attribute
  set: `https://docs.spring.io/spring-modulith/docs/2.0.5/api/org/springframework/modulith/ApplicationModule.html`
- `Type.OPEN` semantics (no enforcement, excluded from cycle detection):
  `https://docs.spring.io/spring-modulith/docs/2.0.5/api/org/springframework/modulith/ApplicationModule.Type.html`
- `ApplicationModules.of(Class).verify()` API:
  `https://docs.spring.io/spring-modulith/reference/verification.html`
- `Documenter` location + methods + Gradle output folder
  (`build/spring-modulith-docs/`):
  `https://docs.spring.io/spring-modulith/docs/2.0.5/api/org/springframework/modulith/docs/Documenter.html`
- `Documenter` bundled in `spring-modulith-starter-test`:
  `https://docs.spring.io/spring-modulith/reference/appendix.html`
- Idiomatic two-test split:
  `https://docs.spring.io/spring-modulith/reference/documentation.html`

## 3. SBE Acceptance Criteria

**Acceptance-verification command:** `./gradlew test`.
Pass condition: all JUnit tests with `@DisplayName` beginning
`AC-<N>` are green (per the QA strategy AC-to-test contract). AC-2 is
discharged once during S002 implementation and recorded in §7 Findings;
its surrogate test in the standard pipeline is AC-1 staying green
(the same machinery that would have rejected the violation).

### AC-1: `ApplicationModules.verify()` passes on the live module graph

```
Given  the 6 module package-info.java files exist with their
       @ApplicationModule annotations as designed
And    the standard test classpath includes spring-modulith-starter-core
       and spring-modulith-starter-test
When   ./gradlew test runs ModuleArchitectureTest.modulesVerify()
Then   ApplicationModules.of(GrimoApplication.class).verify()
       returns without throwing
And    the JUnit report shows the test green.
```

### AC-2: an illegal cross-module reference triggers a `Violations detected:` message (one-time manual)

```
Given  S002 is otherwise complete and AC-1 is green
When   the implementer adds a temporary, deliberately illegal cross-
       module reference (e.g., a class in `subagent` package directly
       importing a placeholder type the implementer creates inside
       `cli`'s base package, with cli still declaring
       allowedDependencies = {}) and runs ./gradlew test
Then   the ModuleArchitectureTest fails with an exception whose
       message begins "Violations detected:" and names both
       modules involved
And    the implementer captures the exact violation message into
       §7 Findings
And    deletes the temporary classes
And    re-runs ./gradlew test, which passes (return-to-green).
```

This AC produces an audit-trail entry proving the policy has teeth on
this codebase. Continuous protection comes from AC-1 staying green.

### AC-3: Documenter writes the module canvas and diagrams

```
Given  AC-1 is green
When   ./gradlew test runs DocumentationTests.writeDocumentationSnippets()
Then   the directory build/spring-modulith-docs/ exists
And    contains at least the per-module canvas (.adoc) files for the
       6 modules and a top-level PlantUML overview (e.g.,
       components.puml or equivalent per Documenter's output naming)
And    the test exits green.
```

(This satisfies PRD §AC paragraph that says "the generated module
diagram lives at `build/spring-modulith-docs/`".)

## 4. Interface / API Design

### 4.1 `core` — the OPEN module

`src/main/java/io/github/samzhu/grimo/core/package-info.java`:

```java
/**
 * Grimo :: Core — shared domain primitives (records, enums, path
 * utilities) consumed by every other module.
 *
 * <p>Marked {@link org.springframework.modulith.ApplicationModule.Type#OPEN}
 * because every sibling module references core types directly; consumers
 * do not need to declare core in their {@code allowedDependencies}.
 * OPEN also excludes core from Modulith's cycle-detection pass, which
 * is the correct semantics for a shared-kernel module that everyone
 * depends on.
 */
@ApplicationModule(
    displayName = "Grimo :: Core",
    type = ApplicationModule.Type.OPEN
)
package io.github.samzhu.grimo.core;

import org.springframework.modulith.ApplicationModule;
```

### 4.2 Closed MVP modules — empty package-info template

For each of `sandbox`, `cli`, `agent`, `subagent`, `skills`, the package
contains exactly one file. Template (showing `sandbox`):

`src/main/java/io/github/samzhu/grimo/sandbox/package-info.java`:

```java
/**
 * Grimo :: Sandbox — hosts the {@code SandboxPort} (S003) and its
 * Testcontainers-backed adapter for spawning bind-mounted Docker
 * containers used by sub-agent execution and CLI invocations.
 *
 * <p>Empty in S002. The first concrete type lands with S003.
 *
 * <p>{@code allowedDependencies = {}} starts as the strictest
 * white-list (no cross-module access permitted). When S003 needs
 * access to {@code core} types it does so for free (core is OPEN).
 * Other dependencies are added by the consuming spec at the moment
 * the cross-module reference is introduced — see the Cross-Module
 * Communication Policy in {@code architecture.md} §1.
 */
@ApplicationModule(
    displayName = "Grimo :: Sandbox",
    allowedDependencies = {}
)
package io.github.samzhu.grimo.sandbox;

import org.springframework.modulith.ApplicationModule;
```

Same shape for `cli`, `agent`, `subagent`, `skills` — only the
`displayName`, package name, and the one-line "what lands here later"
sentence differ.

`displayName` per module:

| Package | `displayName` | First owning spec |
| --- | --- | --- |
| `core` | `Grimo :: Core` | (already shipped in S001) |
| `sandbox` | `Grimo :: Sandbox` | S003 |
| `cli` | `Grimo :: CLI` | S005 |
| `agent` | `Grimo :: Agent` | S007 (`grimo chat`) |
| `subagent` | `Grimo :: Subagent` | S008–S010 |
| `skills` | `Grimo :: Skills` | S011 |

### 4.3 `ModuleArchitectureTest` — AC-1

`src/test/java/io/github/samzhu/grimo/ModuleArchitectureTest.java`:

```java
package io.github.samzhu.grimo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Continuous gate: every {@code ./gradlew test} re-runs
 * {@link ApplicationModules#verify()} against the live module graph.
 * Adding a cross-module reference that violates a sibling module's
 * {@code allowedDependencies} fails this test before the offending
 * commit reaches main.
 *
 * <p>Pure JUnit — no Spring context required. {@code verify()} is a
 * structural check on the compiled classpath.
 */
class ModuleArchitectureTest {

    @Test
    @DisplayName("AC-1 ApplicationModules.verify() passes on the live module graph")
    void modulesVerify() {
        // Given — the modules visible from the boot application's package
        // When — verify the structural rules
        // Then — no Violations thrown
        ApplicationModules.of(GrimoApplication.class).verify();
    }
}
```

### 4.4 `DocumentationTests` — AC-3

`src/test/java/io/github/samzhu/grimo/DocumentationTests.java`:

```java
package io.github.samzhu.grimo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Side-effect test: writes per-module canvases (.adoc) and PlantUML
 * diagrams under {@code build/spring-modulith-docs/} so the QA strategy
 * doc and the PRD's "build/spring-modulith-docs/" reference have
 * actual artefacts to point at.
 *
 * <p>Split from {@link ModuleArchitectureTest} per Spring Modulith's
 * idiomatic two-test pattern: verification is a hard build gate;
 * documentation generation is an artefact emitter that should not
 * mask a verify failure.
 */
class DocumentationTests {

    @Test
    @DisplayName("AC-3 Documenter writes module canvas + diagrams to build/spring-modulith-docs/")
    void writeDocumentationSnippets() {
        // Given — the verified module set
        var modules = ApplicationModules.of(GrimoApplication.class);

        // When — emit canvas + diagrams (idiomatic single-call form)
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases();

        // Then — Documenter writes to build/spring-modulith-docs/ by
        // default (Gradle build folder auto-detect). Test asserts
        // file presence after the call returns.
        // (Concrete file-existence assertion lives in the test body
        // and is finalised in implementation.)
    }
}
```

The file-existence assertion concretely checks
`Path.of("build", "spring-modulith-docs").toFile().isDirectory()` plus
that the directory is non-empty after the writes complete. The
implementation step finalises this (the Documenter file naming is
checked against the actual output rather than guessed).

### 4.5 No new Gradle dependency

`build.gradle.kts` already pulls in:
- `org.springframework.modulith:spring-modulith-starter-core`
  (provides `ApplicationModules`)
- `org.springframework.modulith:spring-modulith-starter-test`
  (transitively provides `Documenter` via `spring-modulith-docs`, and
  ArchUnit if any later spec wants it)

No edits to `build.gradle.kts`.

## 5. File Plan

### New production files

All under `src/main/java/io/github/samzhu/grimo/`:

| File | Action | Description |
| --- | --- | --- |
| `core/package-info.java` | new | `@ApplicationModule(type = OPEN, displayName = "Grimo :: Core")` |
| `sandbox/package-info.java` | new | Empty package + `@ApplicationModule(displayName = "Grimo :: Sandbox", allowedDependencies = {})` |
| `cli/package-info.java` | new | Same shape, `Grimo :: CLI` |
| `agent/package-info.java` | new | Same shape, `Grimo :: Agent` |
| `subagent/package-info.java` | new | Same shape, `Grimo :: Subagent` |
| `skills/package-info.java` | new | Same shape, `Grimo :: Skills` |

### New test files

Under `src/test/java/io/github/samzhu/grimo/`:

| File | Action | Description |
| --- | --- | --- |
| `ModuleArchitectureTest.java` | new | AC-1: continuous `verify()` gate |
| `DocumentationTests.java` | new | AC-3: Documenter emits canvas + diagrams |

### Doc-sync (mandatory in this PR)

| File | Action | Description |
| --- | --- | --- |
| `docs/grimo/architecture.md` | modify §1 | Extend "事件驅動接縫" paragraph to reference the Cross-Module Communication Policy with the three legal patterns; cite §2 of S002 spec |
| `docs/grimo/architecture.md` | modify §2 | Replace 10-module map with the live MVP map (6 modules) + add a "Backlog modules — promote on demand" appendix listing `session`, `router`, `memory`, `jury`, `cost`, `web`, `nativeimage` (note: `native` is a Java reserved word; Backlog name is `nativeimage`); update the per-module responsibility table accordingly |
| `docs/grimo/development-standards.md` | modify §2 | Replace the package-layout tree with the 6 live modules; mention the Backlog package list (`nativeimage` — never `native`); cite Cross-Module Communication Policy section |
| `docs/grimo/development-standards.md` | add §13 | New section "Cross-Module Communication" reproducing the three-pattern policy table from S002 spec §2.2 (single source of truth: this section is authoritative; architecture.md §1 cross-references it) |
| `docs/grimo/PRD.md` | modify line 242 | Replace the stale module enumeration `(core、router、sub-agent、skills、memory、jury、cli-adapter、web-ui)` with `(core、sandbox、cli、agent、subagent、skills)`; add inline note referencing the Backlog list |
| `docs/grimo/glossary.md` | modify | Expand the existing "Application Module" entry with `displayName` / `allowedDependencies` / `Type.OPEN` notes; add new entry "Named Interface" (`@NamedInterface`) — point of cross-module access; add new entry "Allowed Dependencies" — the explicit white-list mechanism |
| `docs/grimo/specs/spec-roadmap.md` | modify | Flip S001 status `⏳ 規劃中` → `✅`; rewrite the S002 description block to match the final 6-module decision (drop `agent`/`web`/`native` from the previous draft enumeration; reference S002 spec file); set S002 status to `⏳ Design`; correct any other roadmap drift surfaced during this work |

### New spec file

| File | Action | Description |
| --- | --- | --- |
| `docs/grimo/specs/2026-04-16-S002-module-skeleton-modulith-verify.md` | new | This file. |

### Not touched

- `build.gradle.kts` — no new dependency required (research D7).
- `application.yaml` — no Modulith runtime properties needed for v1
  (verify is a test-time check; the production-runtime `spring.modulith.runtime.verification-enabled=false` flag from architecture.md §8 is set in a future native-hardening spec).
- `core/domain/` — already shipped in S001; `core` gets only the new
  module-level `package-info.java` at `core/`, not at `core/domain/`.
- No `events/` sub-packages anywhere — created by first publisher
  (forbidden file-plan).
- No `@NamedInterface` annotations anywhere — created by first
  publisher.
- No `web` / `nativeimage` packages — Backlog (D1, D5).
- No `internal/` packages anywhere — created by first owner.

---

## 6. Task Plan

**POC: not required.** All packages already on the classpath since
S000 (`spring-modulith-starter-core` provides `ApplicationModules`;
`spring-modulith-starter-test` transitively provides `Documenter`).
The API surface is trivial — one `verify()` call + a 4-line
`Documenter` chain — and method signatures + Gradle output path were
already validated by parallel research during `/planning-spec` (see
§2.4 citations). A POC would be byte-for-byte identical to the
production test code, providing no new information.

### Tasks

| # | Task | Target | AC | Status | Depends |
| --- | --- | --- | --- | --- | --- |
| T01 | 6 `package-info.java` + `ModuleArchitectureTest` | 6 prod files + 1 test file | **AC-1** | pending | — |
| T02 | `DocumentationTests` | 1 test file | **AC-3** | pending | T01 |
| T03 | One-time manual AC-2 violation proof + record `Violations detected:` message | Procedural; captures audit trail in T03 task file | **AC-2** | pending | T01 |
| T04 | Doc-sync sweep | architecture.md (§1, §2), development-standards.md (§2, new §13), PRD.md (line ~242), glossary.md | — | pending | T03 |

**Execution order.** T01 → T02 → T03 → T04.

- T01 first: `ModuleArchitectureTest` cannot reach green until all 6
  packages exist; bundling avoids a long-running RED state.
- T02 after T01: `Documenter` needs the verified module set.
- T03 after T01 (T02 not strictly required, but T01+T02 make a clean
  green baseline before deliberately breaking it).
- T04 last: doc-sync is a reviewable PR-hygiene sweep with no
  automated AC; doing it after the audit-trail capture in T03 lets
  any wording surfaced during the manual proof be reflected in
  development-standards.md §13.

### AC coverage

| AC | Task | Test class | `@DisplayName` prefix |
| --- | --- | --- | --- |
| AC-1 | T01 | `ModuleArchitectureTest` | `AC-1 …` |
| AC-2 | T03 | (no JUnit test — surrogate is AC-1; audit trail in §7) | (n/a) |
| AC-3 | T02 | `DocumentationTests` | `AC-3 …` |

Every live AC has either a primary JUnit test (`@DisplayName` matches
the QA AC-to-test contract) or, for AC-2, an explicit one-time
manual proof recorded in the spec's §7 Findings.

---

## 7. Implementation Results

**Completed:** 2026-04-16.

### Verification

- `./gradlew clean check` → `BUILD SUCCESSFUL` (compile + unit tests + Modulith verify embedded in `ModuleArchitectureTest`).
- JUnit tally: **13/13 passing**, 0 failures, 0 skipped, 0 errors across **6 test classes**.

| Test class | Tests | Covers |
| --- | --- | --- |
| `ModuleArchitectureTest` | 1 | **AC-1** (`ApplicationModules.verify()` passes on the live 6-module graph) |
| `DocumentationTests` | 1 | **AC-3** (Documenter writes `build/spring-modulith-docs/` canvas + diagrams) |
| `DomainArchitectureTest` | 1 | (S001 carry-over — domain layer Spring-free) |
| `GrimoApplicationTests` | 2 | (S000 carry-over — context loads + virtual threads) |
| `GrimoHomePathsTest` | 4 | (S001 carry-over) |
| `SessionIdTest` | 4 | (S001 carry-over) |

AC-to-test binding confirmed by `@DisplayName` prefixes (`AC-1 …`, `AC-3 …`) per the QA AC-to-test contract. AC-2 deliberately has no continuous JUnit test — audit trail captured in §7 Findings below per spec §2 D8.

### AC results

| AC | Status | Evidence |
| --- | --- | --- |
| AC-1 | ✅ | `ModuleArchitectureTest.modulesVerify` green every `./gradlew test`; `ApplicationModules.of(GrimoApplication.class).verify()` discovers the 6 module `package-info.class` files and finds zero violations. |
| AC-2 | ✅ | One-time manual proof on 2026-04-16 — temporary `cli/CliMarker.java` + `subagent/SubagentProbe.java` triggered `org.springframework.modulith.core.Violations: - Module 'subagent' depends on module 'cli' via io.github.samzhu.grimo.subagent.SubagentProbe -> io.github.samzhu.grimo.cli.CliMarker. Allowed targets: none.` Temp classes deleted; build returned to green. The continuous protection is AC-1 staying green on `main`. |
| AC-3 | ✅ | `DocumentationTests.writeDocumentationSnippets` green; `build/spring-modulith-docs/` populated with `components.puml` + `module-<name>.{adoc,puml}` × 6 = 13 artefacts. |

### Files delivered

```
src/main/java/io/github/samzhu/grimo/
├── core/package-info.java          @ApplicationModule(displayName="Grimo :: Core", type=Type.OPEN)
├── sandbox/package-info.java       @ApplicationModule(displayName="Grimo :: Sandbox",  allowedDependencies={})
├── cli/package-info.java           @ApplicationModule(displayName="Grimo :: CLI",      allowedDependencies={})
├── agent/package-info.java         @ApplicationModule(displayName="Grimo :: Agent",    allowedDependencies={})
├── subagent/package-info.java      @ApplicationModule(displayName="Grimo :: Subagent", allowedDependencies={})
└── skills/package-info.java        @ApplicationModule(displayName="Grimo :: Skills",   allowedDependencies={})

src/test/java/io/github/samzhu/grimo/
├── ModuleArchitectureTest.java     # AC-1 — verify() gate (every ./gradlew test)
└── DocumentationTests.java         # AC-3 — Documenter canvas + diagrams emitter
```

Doc-sync (T04, applied in this PR):

```
docs/grimo/architecture.md           §1 (cross-module policy paragraph) + §2 (6-module map) + §2.x (Backlog appendix)
docs/grimo/development-standards.md  §2 (6-module package layout) + §12 (nativeimage fix) + §13 (NEW: Cross-Module Communication — authoritative single source)
docs/grimo/PRD.md                    line 242 (AC10 module enumeration aligned to MVP)
docs/grimo/glossary.md               Application Module (expanded), Named Interface (NEW), Allowed Dependencies (NEW)
docs/grimo/specs/spec-roadmap.md     S001 → ✅, S002 → ✅ (this section)
```

No new Gradle dependencies. `spring-modulith-starter-core` (S000) provides `ApplicationModules`; `spring-modulith-starter-test` (S000) transitively supplies `Documenter` via `spring-modulith-docs` — no edit to `build.gradle.kts`.

### Key usage patterns (for future specs)

**Declaring a new closed module's `package-info.java`:**

```java
@ApplicationModule(
    displayName = "Grimo :: <Name>",
    allowedDependencies = {}                    // strict empty; expand on first cross-module use
)
package io.github.samzhu.grimo.<module>;

import org.springframework.modulith.ApplicationModule;
```

**Expanding `allowedDependencies` when a real cross-module need arrives** (the owning spec edits the consumer's `package-info.java`):

```java
@ApplicationModule(
    displayName = "Grimo :: Subagent",
    allowedDependencies = {
        "sandbox::api",      // synchronous port access
        "cli::events"        // event subscription
    }
)
```

**Re-running the canvas locally** (after any module-graph change):

```bash
./gradlew test --tests "io.github.samzhu.grimo.DocumentationTests"
ls build/spring-modulith-docs/
# components.puml + module-<name>.{adoc,puml} × N
```

**Diagnosing a Modulith violation**: read the `Violations:` exception message from the `ModuleArchitectureTest` failure — it names both modules + both classes + states `Allowed targets: <list>`. Either (a) move the consumed type behind the publisher's `@NamedInterface("api"|"events")` and add the entry to the consumer's `allowedDependencies`, or (b) reconsider whether the cross-module reference is necessary at all (prefer events per dev-standards.md §13 Pattern C).

### Findings / follow-ups

- **Modulith API import paths** — load-bearing reminder for future specs:
  - `org.springframework.modulith.ApplicationModule` (annotation) + nested `Type.OPEN` / `Type.CLOSED`
  - `org.springframework.modulith.core.ApplicationModules` (the verifier — note the `core` sub-package, distinct from Grimo's `core` module)
  - `org.springframework.modulith.docs.Documenter` (the canvas emitter — `docs` sub-package; transitively in `spring-modulith-starter-test`)
- **Violation message wording.** The actual exception message prefix is `Violations: - …`, not the spec's tentative `Violations detected: …` wording. Both are recognisable; AC-2 semantics unchanged. See dev-standards.md §13 for the canonical sample.
- **Documenter Gradle output path.** `build/spring-modulith-docs/` is auto-detected — no `Documenter.Options.withOutputFolder(...)` needed.
- **Empty modules + Modulith verify.** `verify()` runs cleanly on modules whose only file is `package-info.java` (no classes yet) — confirmed by all 5 closed MVP modules. The package-info `.class` is enough for Modulith to register the module.
- **`native` keyword discovery.** Spotted during planning — `native` is a Java reserved word and cannot be a package name. dev-standards.md §2 + §12 corrected to `nativeimage`. The Backlog module name is **`nativeimage`**; future native-hardening spec must use this name.
- **Bonus inline fix.** dev-standards.md §12 PR checklist line referenced the impossible `io.github.samzhu.grimo.native.GrimoRuntimeHints` — corrected to `nativeimage` in the same doc-sync PR.
- **Glossary growth.** Added `Named Interface` and `Allowed Dependencies` plus an expanded `Application Module` entry. Future Modulith-related specs should reference these instead of redefining.


