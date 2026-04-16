# S000: Project Init

> Spec: S000 | Size: XS (7) | Status: ✅ Done
> Date: 2026-04-16

---

## 1. Goal

Finish the project scaffold started by Spring Initializr so that every
item on the Bootstrap Checklist in `qa-strategy.md` §12 is green and
later specs (S001+) have a stable base to build on.

**State at spec creation (2026-04-16 13:45).** Spring Initializr output
is already on disk:

- `build.gradle.kts` — Spring Boot 4.0.5, Modulith 2.0.5, GraalVM native
  plugin 0.11.5, Java 25 toolchain, group `io.github.samzhu`.
- `settings.gradle.kts` — `rootProject.name = "grimo"`.
- Gradle wrapper at **9.4.1**.
- `src/main/java/io/github/samzhu/grimo/GrimoApplication.java` —
  `@SpringBootApplication` hello-world.
- `src/test/java/io/github/samzhu/grimo/GrimoApplicationTests.java` —
  `contextLoads()` smoke test.
- `src/main/resources/application.yaml`.
- `.gitignore`, `.gitattributes`, `HELP.md`, Gradle wrapper — present.

**What S000 still needs to do.**

1. Wire `spring.threads.virtual.enabled=true` into `application.yaml`
   (per architecture §4 and PRD P7).
2. Patch `.gitignore` for the two cases Initializr missed: macOS
   `.DS_Store` and Grimo's local state dir (`~/.grimo/` is off-tree, but
   defend against accidental symlinks into the repo).
3. Add a top-level `README.md` that emphasises the **native executable**
   as the packaging target.
4. **Doc-sync** — rename every remaining `com.grimo` → `io.github.samzhu.grimo`
   in `architecture.md`, `development-standards.md`, and
   `specs/spec-roadmap.md` so the documented package matches the one the
   code already uses.
5. Make `./docs/grimo/scripts/verify-tests-pass.sh` record a PASS after
   a green `./gradlew check`.

**Explicitly out of scope for S000.**

- **Spotless / Palantir Java Format / automated format gate.** Per user
  decision on 2026-04-16: formatting is not a per-build concern. It will
  be handled in one shot at a later cleanup spec using an external tool,
  not wired into `./gradlew check`.
- `bootBuildImage` / OCI container packaging. User preference: ship a
  native executable, not a container image.
- `gradle/libs.versions.toml` version catalog. Starts empty and grows
  spec-by-spec; adding pins here before a dep is consumed is YAGNI.
- Any JDBC, H2, Flyway, agent-client, Thymeleaf, HTMX dependency. Those
  enter the build in their owning spec (S005 for JDBC/H2, S003 for
  Thymeleaf+HTMX, S004 for agent-client, etc.).
- `.github/workflows/**` — user deferred CI.
- Any Modulith `package-info.java` / `@ApplicationModule` — S002.

## 2. Approach

XS spec. Single approach.

| Approach | Chosen | Rationale |
| --- | --- | --- |
| **A — Minimal patch over Initializr template.** Add only virtual-thread flag + `graalvmNative` block + `.gitignore` patch + README. No version catalog, no new dependencies, no formatter. | **yes** | Matches user's "pull only MVP essentials" directive. Each later spec adds its own deps when it actually needs them. |
| B — Introduce `gradle/libs.versions.toml` and migrate all existing pins | no | User explicitly said "先拉 MVP 就夠的套件". Introducing a catalog now would front-load config for ~20 pins that don't exist yet. Can be introduced later in a small refactor spec if pin sprawl becomes real. |
| C — Drop the GraalVM plugin, revisit later | no | User's target packaging is the native executable. The plugin is already configured and must work from day one (matches PRD D3). |

**Challenge — why not keep the catalog in the design?** A version
catalog only earns its keep when there are multiple subprojects or
when the same version is referenced from many places. Grimo is a
single-project monolith and most versions come from Spring BOMs
already. If in ten specs we find ourselves editing `build.gradle.kts`
too often, introduce a catalog then.

## 3. SBE Acceptance Criteria

### AC-1: clean build succeeds on JDK 25

```
Given  a fresh clone of the repo
And    JDK 25 is the active toolchain
When   ./gradlew build runs
Then   the build succeeds (exit 0)
And    no deprecation warnings from the Spring Boot / GraalVM plugins
       are emitted.
```

### AC-2: contextLoads test passes

```
Given  S000 is complete
When   ./gradlew test runs
Then   it exits 0
And    the JUnit report shows the test
       "io.github.samzhu.grimo.GrimoApplicationTests.contextLoads()"
       passing.
```

### AC-3: _deferred_

Originally "Spotless is a required check". Removed 2026-04-16 —
formatting is not a per-build concern for S000; it will be handled in
one shot at a later cleanup spec with an external tool. AC-3 stays as a
numbering placeholder so AC-4..AC-7 keep their identifiers across
task files and the roadmap.

### AC-4: native executable produced (verification deferred)

```
Given  GraalVM for JDK 25 is the active toolchain
When   ./gradlew nativeCompile runs
Then   build/native/nativeCompile/grimo exists and is executable
And    running it with --help (or a trivial flag) starts, prints a
       Spring banner, and exits within 5s on an Apple Silicon laptop.
```

**Partial coverage in S000** (per user decision 2026-04-16):
- ✅ `graalvmNative` block is in `build.gradle.kts` with `imageName = "grimo"`
  and `--no-fallback`; Gradle parses it; the `nativeCompile` task is
  registered (verified by `./gradlew help --task nativeCompile`).
- ⏸ Actual binary production + runtime check is deferred until GraalVM
  for JDK 25 is installed on the dev machine or exercised by the nightly
  native smoke-test milestone (M7).


### AC-5: virtual threads enabled

```
Given  application.yaml is checked in
When   the test context loads
Then   the property spring.threads.virtual.enabled equals true
And    a smoke assertion in GrimoApplicationTests reads the property
       (or equivalent env check) to prove it.
```

### AC-6: verification log records PASS

```
Given  S000 is complete
When   ./docs/grimo/scripts/verify-tests-pass.sh runs
Then   a new line is appended to build/reports/grimo/verify-log.txt
       matching pattern "<ISO8601>\tPASS\tgradle test".
```

### AC-7: docs use the same package name as the code

```
Given  the code uses io.github.samzhu.grimo as its root package
When   grep -n "com\.grimo" docs/grimo/architecture.md \
        docs/grimo/development-standards.md \
        docs/grimo/specs/spec-roadmap.md runs
Then   it returns zero matches
And    the same grep for "io.github.samzhu.grimo" across those three
       files returns the expected package references (module map,
       package layout diagram, spec descriptions).
```

_Note: the old name `com.grimo` may still appear **inside this spec file**
as a historical reference (describing the rename task itself). That is
intentional and does not violate AC-7._

## 4. Interface / API Design

### 4.1 `build.gradle.kts` — diff against current

Add only the `graalvmNative` block for native packaging. **No new
dependencies, no formatter.**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.5"
}

group = "io.github.samzhu"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

extra["springModulithVersion"] = "2.0.5"

dependencies {
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

tasks.withType<Test> { useJUnitPlatform() }

// --- NEW: native packaging ---
graalvmNative {
    binaries {
        named("main") {
            imageName.set("grimo")
            buildArgs.add("--no-fallback")
        }
    }
    metadataRepository { enabled.set(true) }
}
```

### 4.2 `src/main/resources/application.yaml` — append

```yaml
spring:
  application:
    name: grimo
  threads:
    virtual:
      enabled: true           # P7 / architecture §4
```

### 4.3 `src/test/java/io/github/samzhu/grimo/GrimoApplicationTests.java` — extend

Add a property probe so AC-5 is enforced by a real assertion, not just
"we trust the YAML".

```java
package io.github.samzhu.grimo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GrimoApplicationTests {

    @Value("${spring.threads.virtual.enabled:false}")
    boolean virtualThreadsEnabled;

    @Test
    @DisplayName("AC-2 Spring context loads with zero business beans")
    void contextLoads() {
        // Empty body is sufficient — @SpringBootTest performs the check.
    }

    @Test
    @DisplayName("AC-5 virtual threads are enabled via application.yaml")
    void virtualThreadsEnabled() {
        assertThat(virtualThreadsEnabled).isTrue();
    }
}
```

### 4.4 `.gitignore` — append

```gitignore
# --- macOS ---
.DS_Store

# --- Grimo local state (safety net; real state lives in ~/.grimo) ---
.grimo/
grimo-home/
```

### 4.5 `README.md` — new, top level

```markdown
# Grimo

A user-harness for CLI AI agents (Claude Code · Codex · Gemini CLI).
See `docs/grimo/PRD.md` for product scope.

## Requirements

- **JDK 25** (LTS; GraalVM for JDK 25 if you want the native executable)
- **Docker** (only needed from S012 onwards — sub-agent sandbox)

## Quickstart

```bash
./gradlew build           # compile + tests
./gradlew bootRun         # start on the JVM
./gradlew nativeCompile   # build the native executable (shipping artifact)
./build/native/nativeCompile/grimo
```

Grimo ships as a **native executable**, not a container image. Run the
binary directly.
```

### 4.6 Doc-sync: `com.grimo` → `io.github.samzhu.grimo`

Mechanical rename across:

- `docs/grimo/architecture.md`
- `docs/grimo/development-standards.md`
- `docs/grimo/specs/spec-roadmap.md`

All prose references to `com.grimo`, every package tree diagram,
every FQCN example. No behavioural change; strictly a rename.

## 5. File Plan

| File | Action | Description |
| --- | --- | --- |
| `build.gradle.kts` | **modify** | Append `graalvmNative { binaries { named("main") { imageName = "grimo"; buildArgs += "--no-fallback" } }; metadataRepository { enabled = true } }`. No dep changes, no formatter plugin. |
| `src/main/resources/application.yaml` | **modify** | Append `spring.threads.virtual.enabled: true`. |
| `src/test/java/io/github/samzhu/grimo/GrimoApplicationTests.java` | **modify** | Add `@DisplayName` to existing test; add `virtualThreadsEnabled()` test. |
| `.gitignore` | **modify** | Append `.DS_Store`, `.grimo/`, `grimo-home/`. |
| `README.md` | **new** | Top-level Grimo quickstart (native-executable emphasis). |
| `docs/grimo/architecture.md` | **modify (pre-applied during spec design)** | Doc-sync: rename `com.grimo` → `io.github.samzhu.grimo`. Also corrected the agent-sandbox Maven coordinates: groupId is `org.springaicommunity` (NOT `.agents`), with both `agent-sandbox-core:0.9.1` and `agent-sandbox-docker:0.9.1` now listed in the Framework Dependency Table. Verified on Maven Central 2026-04-16. |
| `docs/grimo/development-standards.md` | **modify (pre-applied)** | Same `com.grimo` → `io.github.samzhu.grimo` rename. |
| `docs/grimo/specs/spec-roadmap.md` | **modify (pre-applied)** | Same rename wherever package names appear. |
| `docs/grimo/specs/2026-04-16-S000-project-init.md` | **new** | This spec file. |

**Explicitly NOT touched in S000:**

- `settings.gradle.kts` — Initializr output is correct as-is.
- `gradle/wrapper/**` — 9.4.1 is fine for Boot 4.0.5 (requires ≥ 8.9).
- `HELP.md` — Spring Initializr's default help; harmless, leave it.
- `.gitattributes` — existing 3-line setup is sufficient for S000.
- Any new directories under `src/main/resources/` — `templates/`,
  `skills/`, `db/migration/` will be created by the specs that use
  them (S003, S016, S005 respectively).

## 6. Task Plan

**POC: not required.**
Rationale: S000 introduces no new SDK or unfamiliar API. The GraalVM
native plugin is already in the Initializr template; virtual-thread
activation is a single-line Spring Boot property. Both patterns are
well-established and documented.

**Task index.** T01 (Wire Spotless) was removed on 2026-04-16 per user
decision to defer formatting. Remaining tasks keep their original IDs
for traceability.

| # | Task | Covers AC | Depends on | Status |
| --- | --- | --- | --- | --- |
| ~~T01~~ | ~~Wire Spotless formatter~~ | ~~AC-3~~ | — | **removed — scope change** |
| T02 | Enable virtual threads + `virtualThreadsEnabled()` test | AC-2, AC-5 | — | pending |
| T03 | Configure `graalvmNative` block → `grimo` executable | AC-4 | — | pending |
| T04 | Patch `.gitignore` + add `README.md` | AC-6 (precondition) | T02, T03 | pending |
| T05 | Run verification chain + record PASS line + doc-sync grep | AC-1, AC-2, AC-6, AC-7 | T02, T03, T04 | pending |

**Execution order:** T02 → T03 → T04 → T05 (T02 and T03 are independent
and could run in parallel; sequential execution keeps the trace
simple).

**AC coverage summary.**

| AC | Covered by |
| --- | --- |
| AC-1 clean build | T05 (`./gradlew clean check`) |
| AC-2 `contextLoads` passes | T02, T05 |
| AC-3 _deferred_ | — (removed 2026-04-16; see §3) |
| AC-4 native executable produced | T03 (config only; binary e2e deferred — see §3 AC-4 note) |
| AC-5 virtual threads enabled + test | T02 |
| AC-6 `verify-tests-pass.sh` records PASS | T05 (direct), T04 (precondition: clean git status) |
| AC-7 docs parity on `io.github.samzhu.grimo` | T05 (grep check; rename was pre-applied at spec-design time) |

Task files live under `docs/grimo/tasks/2026-04-16-S000-T0N.md` and are
temporary — they will be deleted after Phase 3 when Section 7 is
consolidated into this spec file.

## 7. Implementation Results

### Verification

| Check | Result |
| --- | --- |
| `./gradlew clean check` | ✅ `BUILD SUCCESSFUL in 3s` |
| `./gradlew test` (via `verify-tests-pass.sh`) | ✅ `build/reports/grimo/verify-log.txt` gained `2026-04-16T07:31:45Z\tPASS\tgradle test` |
| `verify-spec-coverage.sh S000 spec` | ✅ AC-1, AC-2, AC-5, AC-6, AC-7 all covered; AC-3/AC-4 correctly skipped (deferred) |
| `./gradlew help --task nativeCompile` | ✅ task registered by graalvm-native plugin (config valid) |
| `grep com\.grimo` across architecture / dev-standards / spec-roadmap | ✅ 0 matches (AC-7 clean) |
| Lint / format gate | ⏸ not run — Spotless deferred by user decision on 2026-04-16 |

### Key findings

- **Spring Initializr already covered most of S000's original scope.** The
  template ships `build.gradle.kts`, `settings.gradle.kts`, Gradle wrapper
  9.4.1, `GrimoApplication.java`, `GrimoApplicationTests.java`, and
  `application.yaml`. The actual S000 delta was 4 edits + 1 new file (no
  `.gitkeep` placeholders for future specs, no version catalog).
- **`verify-spec-coverage.sh` had a silent-pass bug.** Its regex
  `^### *(AC[0-9]+)` didn't match `### AC-1:` style headings, so every
  spec was vacuously reported as "nothing to check". Fixed in-repo on
  2026-04-16: regex accepts `AC-?[0-9]+`, deferred ACs (heading contains
  "deferred" / "\_deferred\_") are skipped, and task markdowns under
  `docs/grimo/tasks/` are now included in the marker search so
  infrastructure ACs (build-gate, logs, doc-sync) can be covered by task
  files instead of Java tests.
- **Native-executable pre-condition not in dev env.** The active JDK is
  OpenJDK 25.0.1 via SDKMAN (`/Users/samzhu/.sdkman/candidates/java/current`);
  `native-image` is not on PATH. Per user direction ("先用 JVM 測試就可以"),
  AC-4 was split: config-present PASS in T03; binary-run DEFERRED.
- **Agent-sandbox coordinates corrected.** While updating architecture.md
  during this spec we verified on Maven Central that both
  `org.springaicommunity:agent-sandbox-core:0.9.1` and
  `org.springaicommunity:agent-sandbox-docker:0.9.1` exist — groupId is
  `org.springaicommunity` (no `.agents` suffix). The agent-client family
  stays under `org.springaicommunity.agents`.

### Correct usage patterns

- **Virtual-thread flag + assertion** — the minimum viable proof:
  ```yaml
  # src/main/resources/application.yaml
  spring:
    threads:
      virtual:
        enabled: true
  ```
  ```java
  // src/test/java/io/github/samzhu/grimo/GrimoApplicationTests.java
  @SpringBootTest
  class GrimoApplicationTests {
      @Value("${spring.threads.virtual.enabled:false}")
      boolean virtualThreadsEnabled;

      @Test
      @DisplayName("AC-5 virtual threads are enabled via application.yaml")
      void virtualThreadsEnabled() {
          assertThat(virtualThreadsEnabled).isTrue();
      }
  }
  ```
- **GraalVM native Kotlin-DSL block** (plugin `org.graalvm.buildtools.native:0.11.5`):
  ```kotlin
  graalvmNative {
      binaries {
          named("main") {
              imageName.set("grimo")
              buildArgs.add("--no-fallback")
          }
      }
      metadataRepository { enabled.set(true) }
  }
  ```
  Registers the `nativeCompile` task with the shipping-artifact name
  `grimo`. Parses without executing; building the binary requires
  GraalVM for JDK 25 on PATH.

### AC results

| AC | Result | Notes |
| --- | --- | --- |
| AC-1 clean build succeeds on JDK 25 | ✅ PASS | `./gradlew clean check` BUILD SUCCESSFUL in 3s (T05) |
| AC-2 `contextLoads` passes | ✅ PASS | 2 JUnit tests green including `@DisplayName("AC-2 ...")` (T02, T05) |
| AC-3 formatter gate | ⏸ DEFERRED | Spotless dropped from S000 scope; formatting will be handled by an external tool at a later cleanup spec |
| AC-4 native executable produced | 🟡 PARTIAL | Config ✅ (T03); binary-run ⏸ deferred until GraalVM for JDK 25 is installed (or the M7 nightly smoke-test runs) |
| AC-5 virtual threads enabled | ✅ PASS | `virtualThreadsEnabled()` assertion green (T02) |
| AC-6 verify-tests-pass.sh records PASS | ✅ PASS | log line `2026-04-16T07:31:45Z\tPASS\tgradle test` appended (T05) |
| AC-7 docs parity on `io.github.samzhu.grimo` | ✅ PASS | `com.grimo` grep across 3 synced docs returns 0 (T05) |
