# Grimo — QA Strategy

**Status:** v0.1 · **Date:** 2026-04-16

## 1. Philosophy

Two complementary control regimes, per the harness-engineering canon
baked into the PRD:

- **Computational controls** — deterministic verifications: unit
  tests, module-boundary checks, contract tests, native-image smoke
  tests. These are the primary quality gate.
- **Inferential controls** — LLM-as-judge checks on behavioral
  contracts (e.g. "after CLI switch, does the next reply reference
  prior context?"). Used selectively on SBE criteria where
  deterministic assertions are hard.

## 2. Frameworks & fixed tools

| Concern | Tool | Notes |
| --- | --- | --- |
| Test runner | JUnit Jupiter 5.11+ | `testImplementation("org.springframework.boot:spring-boot-starter-test")` pulls this in on Boot 4.0.5 |
| Assertions | AssertJ 3.26+ | via `starter-test` |
| Modulith verify | `spring-modulith-starter-test` | `ApplicationModules.of(GrimoApplication.class).verify()` |
| Arch rules | ArchUnit (transitively from Modulith) | Domain-Spring-free assertion lives in `core/ArchitectureTest.java` |
| Container tests | Testcontainers 1.20.4 | **JVM classpath only**; `@DisabledInNativeImage` on every such class |
| HTTP tests | `MockMvc` (`@WebMvcTest`) + `WebTestClient` for SSE | HTMX interactions covered by `mockMvc.perform(...).andExpect(header().exists("HX-*"))` |
| DB tests | `@DataJdbcTest` on H2 memory mode | Production uses H2 file mode (D17) |
| Code formatting | _deferred — one-shot external tool run at a later cleanup spec_ | not a per-build gate (user decision 2026-04-16) |
| Native build | `org.graalvm.buildtools.native` 0.11.5 | nightly `nativeCompile` smoke-test |
| Coverage | JaCoCo 0.8.12 | target ≥ 75% line coverage on `application/service/` and `domain/` packages; adapter coverage is a side-effect, not a target |

## 3. Test taxonomy

| Tier | Scope | Runner | Target CI gate |
| --- | --- | --- | --- |
| **T0 Unit** | `domain/` records + pure services | plain JUnit | PR gate (fast, <10s per module) |
| **T1 Module** | Single `@ApplicationModule` wired (no cross-module beans) | `@ApplicationModuleTest` | PR gate |
| **T2 Slice** | `@WebMvcTest`, `@DataJdbcTest` per adapter | Spring slices | PR gate |
| **T3 Contract** | Port ↔ adapter contract (e.g. `SandboxPort` contract ran against both impls) | `@SpringBootTest(classes = Sandbox*Config)` | PR gate |
| **T4 Integration** | End-to-end user-visible behavior with stubs for external CLIs | `@SpringBootTest` + WireMock-for-LLM | Nightly + tag-triggered |
| **T5 Native smoke** | `nativeCompile` + boot + `/actuator/health` | `gradle nativeTest` | Nightly (not PR-blocking in v1) |
| **T6 Inferential** | LLM-judge of transcripts against SBE criteria | custom runner, opt-in | Weekly / release-candidate |

## 4. SBE ↔ Test mapping

Every PRD acceptance criterion must be cited by at least one test.
Spec docs carry the forward reference. Reverse index lives here and is
re-generated nightly by `scripts/verify-spec-coverage.sh`.

| PRD AC | Primary test class | Tier |
| --- | --- | --- |
| AC1 trivial routing | `RouterDecisionTest` | T1 |
| AC2 strategic escalation | `RouterDecisionTest` | T1 |
| AC3 CLI switch replay | `CliSwitchReplayIT` | T4 |
| AC4 main-agent read-only | `MainAgentAllowlistTest` | T1 |
| AC5 sub-agent isolation | `SubagentWorktreeIT` (Testcontainers) | T4 |
| AC6 fail-soft boot | `FailSoftBootIT` (spring-boot-test with `PATH` stubbed) | T4 |
| AC7 jury review | `JuryReviewIT` | T4 |
| AC8 skill distillation | `SkillDistillerTest` | T1 |
| AC9 memory curated | `AutoMemoryToolsTest` | T1 |
| AC10 module boundaries | `ModuleArchitectureTest.verify()` | T1 |

## 5. Routing by spec size (auto-verify rules)

| Size | Auto-verify on merge | Manual QA required |
| --- | --- | --- |
| **XS** (6-8 pts) | `./gradlew test` passes + relevant T0/T1 green | no |
| **S** (9-11) | above + T2 slices + `./gradlew modulith:verify` | no |
| **M** (12-14) | above + T3 contract tests + integration (T4) for touched paths | no |
| **L** (15-16) | above + full T4 run + code review by 2 humans | **yes** — invoke `/verifying-quality` |
| **XL** (17-18) | decompose; no spec ships as XL | n/a |

## 6. Verification pipeline

```
dev saves code
   ↓
./gradlew check  ──▶ compile + T0 + T1 + T2 + modulith verify
   ↓ (green)
scripts/verify-tests-pass.sh             ──▶ records PASS timestamp
scripts/verify-spec-coverage.sh S###     ──▶ asserts every AC in spec has a test
   ↓
CI PR gate: T0..T3 run, JaCoCo report, Dependabot/OWASP check
   ↓ (merge to main)
CI nightly:
  - full T4 integration
  - T5 nativeCompile + health probe
  - T6 inferential judge on recorded transcripts (once we have recordings)
```

## 7. Deterministic-only stubs for CLI agents

- `cli` module's outbound port `AgentClientPort` has a production
  impl (`AgentClientAdapter`) and a test impl (`StubAgentClientAdapter`)
  that replays canned `Flux<String>` streams.
- No real `claude`/`codex`/`gemini` invocation during T0..T3. T4
  may use a real CLI if the machine has it, but tests must skip
  with a clear message otherwise (never fail because a CLI is
  absent).

## 8. Native-image smoke-test recipe (T5)

Nightly GitHub Actions job:

```yaml
- uses: graalvm/setup-graalvm@v1
  with: { java-version: '25', distribution: 'graalvm' }
- run: ./gradlew nativeCompile
- run: ./build/native/nativeCompile/grimo --server.port=18080 --grimo.subagent.backend=process &
- run: for i in 1 2 3 4 5 6 7 8 9 10; do \
         curl -fsS http://127.0.0.1:18080/actuator/health && break || sleep 2; \
       done
- run: curl -fsS http://127.0.0.1:18080/actuator/health | grep '"status":"UP"'
- run: pkill -TERM -f grimo || true
```

Smoke-test failure does **not block the PR** in v1 — it raises an
issue tagged `native-regression` for the following sprint.

## 9. Coverage & dashboards

- JaCoCo HTML at `build/reports/jacoco/test/html/index.html`.
- Modulith-generated C4 + module canvas at
  `build/spring-modulith-docs/`.
- Spec-coverage report (generated by script below) at
  `build/reports/grimo/spec-coverage.md`.

## 10. Scripts (verification commands)

Under `docs/grimo/scripts/`:

- `verify-tests-pass.sh` — runs `./gradlew test` and writes a
  timestamped PASS/FAIL record to `build/reports/grimo/verify-log.txt`.
- `verify-spec-coverage.sh` — parses a spec file, extracts its
  `## Acceptance criteria` block, greps the test tree for matching
  `@DisplayName(...)` / `// AC<id>` annotations, and asserts each
  AC has at least one hit.

## 11. Escalation path

- **Flaky test** → mark `@Tag("flaky")`, open an issue tagged
  `flaky-test`, fix within two sprints or delete.
- **Failing native smoke-test** → non-blocking in v1; open
  `native-regression` issue; must be clean by M7 end.
- **L-size spec** → invoke `/verifying-quality` after implementation.
- **Policy deviation** (skip a test tier) → documented in the PR with
  ADR if it becomes a pattern.

## 12. Bootstrap checklist (what goes green on day 1 in S000)

- `./gradlew build` passes (empty app, only `GrimoApplication.java`).
- `./gradlew test` reports zero tests but exits 0.
- `./gradlew modulith:verify` passes on an empty module graph.
- `./gradlew nativeCompile` succeeds on the hello-world app.
- `scripts/verify-tests-pass.sh` invocation records a PASS line.
- Code formatting is intentionally NOT gated at build time (see §2).
