# S001: Core domain primitives + GrimoHomePaths

> Spec: S001 | Size: XS (7) | Status: ⏳ Design
> Date: 2026-04-16

---

## 1. Goal

Create the base `io.github.samzhu.grimo.core.domain` package with
pure-Java value types that downstream modules (session, cli, router,
subagent, skills, memory, jury, cost, web) will consume.

All types are **immutable records or enums with zero Spring annotations**.
Tests assert (via ArchUnit) that no Spring import ever leaks into this
package.

Deliverables in one spec:

- **4 ID records** — `SessionId`, `TurnId`, `TaskId`, `CorrelationId`
  — each wraps a 21-char NanoID string. Record-typed wrappers give
  compile-time safety against passing the wrong id kind.
- **2 enums** — `AgentRole` (MAIN / SUB / JURY_MEMBER), `ProviderId`
  (CLAUDE / CODEX / GEMINI).
- **1 ID generator** — `NanoIds` utility, vendored inline
  (~30 lines). Zero new runtime dependency.
- **1 path utility** — `GrimoHomePaths` final class with static
  methods that resolve and lazily create `~/.grimo/{memory, skills,
  sessions, worktrees, logs, config, db}`. Honors the `grimo.home`
  JVM system property and the `$GRIMO_HOME` env var as overrides.
- **1 ArchUnit test** — asserts `core.domain` has no Spring or
  `jakarta.annotation` imports. Uses ArchUnit transitively provided
  by `spring-modulith-starter-test`.

**Out of scope.** No Spring Modulith `package-info.java` annotations
yet (that is S002). No adapter code. No event types yet (those land
with their owning module). **No `Cost` domain type** — the cost
concern is owned by the `cost` module (see S019 / cost telemetry
panel), not a cross-cutting `core.domain` primitive. Only two
consumers would use `Cost` (the `cost` module itself plus `web` via
the cost module's named interface), which does not clear the
"cross-cutting" bar for `core`.

## 2. Approach

XS spec — single approach. Key decisions:

| # | Decision | Chosen | Why |
| --- | --- | --- | --- |
| D1 | ID scheme | NanoID (21 chars, `[A-Za-z0-9_-]`) | User choice during grill-me. Shorter than UUID, URL-safe, no DB sortability requirement yet. |
| D2 | NanoID impl source | **Vendor inline** in `NanoIds` | `com.aventrix.jnanoid:jnanoid:2.0.0` stale since 2018; `io.viascom.nanoid:nanoid:1.0.1` drags `kotlin-stdlib`. Algorithm is ~30 lines — own it, zero dep, native-image trivial. |
| D3 | `Cost` ownership | **Deferred out of S001** | Grill-me revealed only `cost` + `web` consume it; doesn't clear the cross-cutting bar for `core`. Lands in the owning spec (earliest S019 — Cost telemetry panel), which will re-decide precision (µ¢ vs cents) at that time. |
| D4 | `AgentRole` shape | `enum { MAIN, SUB, JURY_MEMBER }` — not `sealed interface` | Roles are stateless identifiers in S001. Upgrading to a sealed hierarchy is cheap later if per-role payload emerges (probably S004 / S013). YAGNI. |
| D5 | `ProviderId` shape | `enum { CLAUDE, CODEX, GEMINI }` | Fixed list per PRD; extensibility is post-MVP. |
| D6 | `GrimoHomePaths` shape | `final class` with only `static` methods | Zero-state utility. Matches the JDK `java.nio.file.Paths` idiom. No DI (domain layer is Spring-free). Self-creates dirs via `Files.createDirectories` on first call (idempotent, thread-safe). |
| D7 | Override precedence | `grimo.home` system property → `$GRIMO_HOME` env → `~/.grimo` | Enhances roadmap AC2 (which only mentioned env var) — system property makes tests clean (`System.setProperty` works; tampering env from JUnit would require `junit-pioneer` or a forked JVM). Env var still honored for production users. |
| D8 | ArchUnit location | Test lives in S001 | Roadmap AC3 assigns the check to S001. `spring-modulith-starter-test` already brings ArchUnit onto the test classpath — no new dep. |
| D9 | `CorrelationId` vs `TurnId` | Two distinct records, identical shape | Different semantics. `TurnId` persists (DB row); `CorrelationId` is the SSE-stream scope and may span a jury fan-out that does NOT share a turn. Distinct record types give compile-time protection against accidental substitution. |

### Challenge

- **"Why not a single `ShortId` base type for all four id records?"** —
  Two-line record wrappers cost nothing; the compile-time protection
  against passing a `TaskId` where `SessionId` is expected is worth more
  than the DRY saved.
- **"Why mkdir inside the getter?"** — Roadmap AC2 specifies "created
  on first call". Callers want the path to exist on return; splitting
  creation to a separate call is friction.
- **"Why defer `Cost` out of `core`?"** — Cross-checked consumers:
  only the `cost` module owns it, and `web` consumes it via cost's
  named interface. One owner + one routed consumer does not justify
  a shared `core` primitive. Cost lands in its owning spec (S019 at
  the earliest); that spec re-decides precision (µ¢ vs cents).
- **"Why JVM system property as primary override?"** — Env-var
  manipulation from JUnit is fragile (`junit-pioneer` needed, and it
  uses reflection that is native-image hostile). System property is
  the test-friendly path; env-var still works for production users.

## 3. SBE Acceptance Criteria

**Acceptance-verification command:** `./gradlew test`.
Pass condition: all JUnit tests with `@DisplayName` beginning
`AC-<N>` (per the QA strategy AC-to-test contract) are green.

### AC-1: `SessionId.random()` produces a 21-character NanoID string

```
Given  NanoIds is the inlined generator in core.domain
When   SessionId id = SessionId.random()
Then   id.value() has length 21
And    id.value() matches regex `[A-Za-z0-9_-]{21}`
And    across 1000 invocations, all returned values are distinct
       (probabilistic collision probability negligible at this N).
```

### AC-2: `GrimoHomePaths.memory()` returns a created dir and honors overrides

```
Given  grimo.home system property is set to a @TempDir T
When   GrimoHomePaths.memory() is called
Then   the returned Path equals T/memory
And    T/memory exists on disk (was created by the call)
And    a subsequent memory() call returns the same path without error

Given  the grimo.home property is cleared AND $GRIMO_HOME env is set
       (e.g., via ProcessBuilder-invoked sub-JVM, or use property
       path for primary test and an integration test for env path)
When   GrimoHomePaths.memory() is called
Then   the returned Path equals $GRIMO_HOME/memory and exists.

Given  neither override is set
When   GrimoHomePaths.memory() is called
Then   the returned Path equals $HOME/.grimo/memory (resolved via
       System.getProperty("user.home")).
```

### AC-3: `core.domain` has no Spring or jakarta.annotation import

```
Given  the project compiles under JDK 25
When   the ArchUnit rule `noSpringDependenciesInDomain` runs via the
       standard test task
Then   zero classes under io.github.samzhu.grimo.core.domain depend on
       a class from a package starting with `org.springframework` or
       `jakarta.annotation`.
```

*(Roadmap AC4 on Cost arithmetic is deferred to the owning spec — see
D3 in §2 above.)*

## 4. Interface / API Design

All under `package io.github.samzhu.grimo.core.domain;`. Java 25,
records preferred, final classes where a utility namespace is needed.

### 4.1 ID records (× 4)

```java
public record SessionId(String value) {
    public SessionId {
        if (value == null || value.length() != 21) {
            throw new IllegalArgumentException(
                "SessionId must be a 21-character NanoID, got: " + value);
        }
    }
    public static SessionId random() {
        return new SessionId(NanoIds.generate());
    }
}
```

Identical shape for `TurnId`, `TaskId`, `CorrelationId`. (No shared
base to preserve compile-time distinction.)

### 4.2 `NanoIds` (vendored)

```java
public final class NanoIds {
    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-".toCharArray();
    private static final int SIZE = 21;
    // ALPHABET.length == 64 → every byte maps to a valid index via `b & 63`.

    private NanoIds() {}

    public static String generate() {
        byte[] bytes = new byte[SIZE];
        RNG.nextBytes(bytes);
        char[] out = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            out[i] = ALPHABET[bytes[i] & 63];
        }
        return new String(out);
    }
}
```

Algorithm specification (for reference): NanoID — 21-char output,
SecureRandom-backed, 64-char URL-safe alphabet. Collision resistance
equivalent to a random 126-bit id.

### 4.3 Enums

```java
public enum AgentRole { MAIN, SUB, JURY_MEMBER }
public enum ProviderId { CLAUDE, CODEX, GEMINI }
```

### 4.4 `GrimoHomePaths`

```java
public final class GrimoHomePaths {
    private GrimoHomePaths() {}

    /**
     * Resolves Grimo's home directory. Precedence:
     *   1. System property `grimo.home`
     *   2. Env var `$GRIMO_HOME`
     *   3. `$HOME/.grimo`  (via System.getProperty("user.home"))
     */
    public static Path home() {
        String prop = System.getProperty("grimo.home");
        if (prop != null && !prop.isBlank()) return Path.of(prop);
        String env = System.getenv("GRIMO_HOME");
        if (env != null && !env.isBlank()) return Path.of(env);
        return Path.of(System.getProperty("user.home"), ".grimo");
    }

    public static Path memory()    { return ensureDir(home().resolve("memory")); }
    public static Path skills()    { return ensureDir(home().resolve("skills")); }
    public static Path sessions()  { return ensureDir(home().resolve("sessions")); }
    public static Path worktrees() { return ensureDir(home().resolve("worktrees")); }
    public static Path logs()      { return ensureDir(home().resolve("logs")); }
    public static Path config()    { return ensureDir(home().resolve("config")); }
    public static Path db()        { return ensureDir(home().resolve("db")); }

    private static Path ensureDir(Path p) {
        try { Files.createDirectories(p); return p; }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
```

### 4.5 ArchUnit test (AC-3)

Placed in `src/test/java/io/github/samzhu/grimo/core/domain/DomainArchitectureTest.java`.

```java
class DomainArchitectureTest {

    @Test
    @DisplayName("AC-3 core.domain has no Spring or jakarta.annotation imports")
    void noSpringDependenciesInDomain() {
        var classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.samzhu.grimo.core.domain");

        ArchRule rule = noClasses()
            .that().resideInAPackage("io.github.samzhu.grimo.core.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.annotation..");

        rule.check(classes);
    }
}
```

## 5. File Plan

New production files (all under `src/main/java/io/github/samzhu/grimo/core/domain/`):

| File | Action | Description |
| --- | --- | --- |
| `SessionId.java` | new | Record wrapping a 21-char NanoID. |
| `TurnId.java` | new | Same shape as `SessionId`, distinct type. |
| `TaskId.java` | new | Same. |
| `CorrelationId.java` | new | Same (D9 rationale). |
| `AgentRole.java` | new | `enum MAIN, SUB, JURY_MEMBER`. |
| `ProviderId.java` | new | `enum CLAUDE, CODEX, GEMINI`. |
| `NanoIds.java` | new | Vendored generator. |
| `GrimoHomePaths.java` | new | Final class, static `Path` accessors, overrides. |

New test files (under `src/test/java/io/github/samzhu/grimo/core/domain/`):

| File | Action | Description |
| --- | --- | --- |
| `SessionIdTest.java` | new | AC-1: length, alphabet, uniqueness. |
| `GrimoHomePathsTest.java` | new | AC-2: `grimo.home` override via `System.setProperty` + `@TempDir`. |
| `DomainArchitectureTest.java` | new | AC-3: ArchUnit rule. |

Other:

| File | Action | Description |
| --- | --- | --- |
| `docs/grimo/specs/2026-04-16-S001-core-domain-primitives.md` | new | This spec file. |
| `docs/grimo/specs/spec-roadmap.md` | modify | S001 status → ⏳ Design. |

**Not touched** — no files pre-created for downstream specs (per the
Forbidden File-Plan Patterns rule). S002 will add
`package-info.java` with the Modulith `@ApplicationModule`
annotation; S001 deliberately stops short of that.

**No new dependencies.** All new code uses JDK + already-present
libraries (`spring-modulith-starter-test` transitively supplies
ArchUnit; `spring-boot-starter-test` supplies JUnit + AssertJ +
`@TempDir`).

## 6–7

_(Sections 6 and 7 are written later by `/planning-tasks S001` and
`/implementing-task`. Do not fill in here.)_
