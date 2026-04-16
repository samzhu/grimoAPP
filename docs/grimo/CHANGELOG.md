# Changelog

All notable shipped specs are listed here. Follow Keep a Changelog conventions.

## [Unreleased]

### Added
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
