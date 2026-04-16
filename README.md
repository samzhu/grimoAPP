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
