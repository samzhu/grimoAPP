# Changelog

All notable shipped specs are listed here. Follow Keep a Changelog conventions.

## [Unreleased]

### Added
- **S001 — Core domain primitives + GrimoHomePaths** (2026-04-16).
  New `io.github.samzhu.grimo.core.domain` package: four typed id records
  (`SessionId`, `TurnId`, `TaskId`, `CorrelationId`), two enums
  (`AgentRole { MAIN, SUB, JURY_MEMBER }`, `ProviderId { CLAUDE, CODEX,
  GEMINI }`), a vendored `NanoIds` generator (21-char URL-safe ids,
  ~30 LOC, zero new runtime deps), and `GrimoHomePaths` — a static
  resolver for `~/.grimo/{memory, skills, sessions, worktrees, logs,
  config, db}` with `grimo.home` JVM-property and `$GRIMO_HOME` env-var
  overrides (precedence: property → env → `$HOME/.grimo`). ArchUnit
  `DomainArchitectureTest` guards AC-3 (no Spring / `jakarta.annotation`
  imports in `core.domain`). All domain types are immutable records or
  plain enums with zero Spring annotations. AC-4 (`Cost` arithmetic)
  deferred to the owning cost-telemetry spec (S019). See
  `docs/grimo/specs/archive/2026-04-16-S001-core-domain-primitives.md`
  section 7 for key usage patterns and test recipes.
- **S000 — Project Init** (2026-04-16). Spring Boot 4.0.5 modulith scaffold on
  JDK 25 with Gradle 9.4.1 wrapper and GraalVM native plugin 0.11.5
  (`graalvmNative { imageName = "grimo"; buildArgs += "--no-fallback" }`).
  Virtual-thread executor enabled via `spring.threads.virtual.enabled=true`.
  Hello-world `GrimoApplication` plus two JUnit tests (`contextLoads`,
  `virtualThreadsEnabled`). `.gitignore` hardened (macOS / `~/.grimo/` noise).
  Top-level `README.md` documenting the native-executable packaging path.
  AC-3 (automated formatter gate) and AC-4 (actual native binary run)
  intentionally deferred — see
  `docs/grimo/specs/archive/2026-04-16-S000-project-init.md` section 7.
