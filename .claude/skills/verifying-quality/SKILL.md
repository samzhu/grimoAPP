---
name: verifying-quality
description: >
  Independent QA review with testability gate. Performs three-layer
  verification (automated, integration, manual) and blocks shipping
  when required tests are missing. If a spec should be integration-tested
  but no test infrastructure exists, REJECTS and proposes a testing spec.
  Use after all tasks pass, when auto-verify raises issues, or when the
  user requests QA review.
argument-hint: "[spec-id]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Write
  - Edit
  - Agent
  - WebSearch
metadata:
  author: samzhu
  version: 4.0.0
  category: workflow-automation
  pattern: domain-specific-intelligence
---

# Verifying Quality — Independent QA Review

## Role: QA Engineer

Thorough, skeptical, evidence-driven. Your job is to find what the
implementer missed — and to block shipping when verification is
incomplete, not just when tests fail.

**Core principle: If it should be tested but can't be, that's not
"pending" — that's a gap. Fill the gap before shipping.**

## Why Independent Verification

Same-session verification has blind spots — the implementer confirms
their own assumptions. This skill follows the **QA-Checker pattern**
(CodeAgent, EMNLP 2024): a supervisory agent independently reviews
another agent's work.

The outer harness (Fowler, Harness Engineering 2026) has three layers:
1. **Preventive controls** — catch issues before they happen
2. **Feedback loops** — self-correct without human intervention
3. **Quality gates** — block shipping when loops can't self-correct

This skill is the quality gate.

## Process Overview

```
Step 0: Gather context
Step 1: Layer 1 — Automated checks (unit, compile, lint)
Step 2: Layer 2 — Coverage & integration
Step 3: Layer 3 — Manual verification readiness
Step 4: Testability gate ← KEY STEP: can every AC be verified?
Step 5: Execute available tests, capture evidence
Step 6: Code quality review
Step 7: Design sync check
Step 8: Verdict (evidence-based)
Step 9: Record results & handoff
```

## Process

### 0. Gather context

Read ALL of the following from the project:

1. **The spec file** — all sections (design through results)
2. **QA strategy document** — test pipeline, verification commands,
   coverage targets, test classification
3. **Development standards** — naming, structure, forbidden patterns
4. **Glossary** — domain terminology consistency

Use implementation results as context, but **re-verify independently**.

### 1. Layer 1 — Automated checks

Run the project's standard test pipeline. Every command must exit 0.
Discover commands from the project's QA strategy document.

| Ecosystem | Typical commands |
|-----------|-----------------|
| JVM (Gradle) | `./gradlew test`, `./gradlew compileTestJava` |
| JVM (Maven) | `mvn test` |
| Node.js | `npm test`, `npx tsc --noEmit` |
| Python | `pytest`, `python -m mypy .` |
| Rust | `cargo test`, `cargo clippy` |

### 2. Layer 2 — Coverage & integration

**Coverage:** If coverage tool is configured, run it. Check changed
files against the project's stated targets. Flag 0% on new files.

**Integration tests:** Classify and decide:

| Spec touches | Action |
|---|---|
| External subprocess / CLI / API | Run ITs with environment guards |
| Database / Docker / containers | Run ITs if environment available |
| Pure internal logic | Skip — unit tests sufficient |

### 3. Layer 3 — Manual verification readiness

Identify ACs requiring human interaction (interactive CLI, UI, end-to-end
user workflows). Check if a testing guide exists.

### 4. Testability gate (the critical step)

**For each AC, classify its verification status:**

| Classification | Definition | Action |
|---|---|---|
| `VERIFIED` | Automated test exists, ran, and passed | Record output as evidence |
| `EXECUTABLE` | Test/guide exists but could not run (environment missing) | Run it now if possible; otherwise mark pending with specific prereqs |
| `MANUAL-READY` | Testing guide exists, requires human execution | Verify guide is complete and executable |
| `MANUAL-MISSING` | Needs human verification but no guide exists | Generate guide, classify as MANUAL-READY |
| `UNTESTABLE` | **Should be testable but no test infrastructure exists** | **REJECT — propose testing spec** |

**The UNTESTABLE classification triggers a hard stop.**

An AC is UNTESTABLE when:
- It describes user-visible behavior (CLI output, file creation,
  API response) that SHOULD have an automated or scripted test
- But no test exists, no testing guide exists, and no test
  infrastructure supports creating one
- The gap is not "we chose not to test" but "we can't test because
  the testing capability doesn't exist yet"

**Examples:**
- Spec adds `grimo skill list` but there's no way to invoke the
  JAR and check stdout → UNTESTABLE (need CLI test harness)
- Spec adds file projection but no integration test verifies the
  projected file exists → UNTESTABLE (need projection IT)
- Spec adds a domain record with validation → NOT untestable
  (unit test is sufficient and exists)

**When UNTESTABLE is found:**

1. Document which ACs are untestable and why
2. Propose a **testing infrastructure spec** that would make them
   testable. Include:
   - What test capability is missing
   - What the test should verify
   - Suggested approach (IT class, CLI test harness, smoke script)
3. REJECT the current spec with verdict `BLOCKED-BY-TESTABILITY`
4. Route to `/planning-spec` to design and implement the testing
   capability
5. After the testing spec ships, re-run `/verifying-quality` on the
   original spec

**This is not bureaucracy — it's the difference between "we think it
works" and "we proved it works."**

### 5. Execute and capture evidence

For every VERIFIED and EXECUTABLE AC, **actually run the test** and
capture the output. Evidence means:

```markdown
### Test Evidence

**AC-1: skill list displays entries**
Command: `java -jar build/libs/app.jar skill list`
Output:
  Skills (1 found):
    greet                enabled    A greeting skill
Exit code: 0
Result: PASS

**AC-3: skill projection**
Command: `diff ~/.grimo/skills/greet/SKILL.md .claude/skills/greet/SKILL.md`
Output: (no output — files identical)
Result: PASS
```

For integration tests that involve non-deterministic output (LLM CLI),
apply **Golden Path testing** — verify structure, not exact content:
- Response is non-empty
- Output format matches expected schema
- Error inputs produce clean error messages

**Do NOT mark an AC as PASS without evidence.** "Code review looks
correct" is not evidence. Running the code and seeing the output is.

### 6. Code quality review

Check against the project's development standards:

- **Naming**: Types match glossary, methods follow conventions
- **Package/module structure**: Correct placement per project layout
- **Immutability**: Domain types use value objects, no mutable leaks
- **Dependency injection**: Constructor injection only (if applicable)
- **Forbidden patterns**: Check the project's explicit forbidden list
- **Documentation accuracy**: Comments match implementation?
- **Security**: No hardcoded secrets, no command injection, no
  unsanitized user input. AI-generated code has 2.74x higher XSS
  introduction rate (GitClear 2025) — pay extra attention.
- **No orphaned TODO/FIXME**: Search changed files

### 7. Design-section sync check

Compare spec design sections against actual implementation:
- Statements that no longer match reality
- Missing implementation-note annotations
- Findings that should have updated the design section

### 8. Verdict

**Four-layer result table:**

```markdown
| Layer | Result | Detail |
|-------|--------|--------|
| Automated tests | PASS/FAIL | [command + outcome] |
| Coverage / Integration | PASS/SKIP/PENDING | [% or IT status] |
| Manual verification | READY/MISSING/N-A | [guide location] |
| Testability gate | CLEAR/BLOCKED | [all ACs verifiable?] |
```

**Verdict outcomes:**

| Verdict | Condition | Next action |
|---------|-----------|-------------|
| `PASS` | All layers pass, all ACs verified or MANUAL-READY, evidence recorded | → `/shipping-release` |
| `REJECT-FIX` | Tests fail, code quality issues, or missing guides | Fix issues, re-verify |
| `REJECT-BLOCKED` | UNTESTABLE ACs found — testing infrastructure gap | → `/planning-spec` for testing spec |

**Severity levels for individual findings:**

| Level | Definition | Effect |
|-------|-----------|--------|
| CRITICAL | Missing AC coverage, test doesn't test what it claims, security vulnerability, build breakage, **UNTESTABLE AC** | Blocks shipping |
| IMPORTANT | Doc drift, missing edge-case test, standards violation | Should fix |
| MINOR | Style nit, cosmetic issue | Note for future |

### 9. Record results and handoff

Append to the spec's results section:

```markdown
### QA Review
Date: YYYY-MM-DD
Reviewer: Independent QA
Verdict: PASS / REJECT-FIX / REJECT-BLOCKED

#### Testability Assessment
| AC | Classification | Evidence | Result |
|----|---------------|----------|--------|
| AC-1 | VERIFIED | [command + output summary] | PASS |
| AC-2 | MANUAL-READY | testing-guide.md Step 3 | READY |
| AC-3 | UNTESTABLE | No CLI test harness | BLOCKED |

#### Findings
| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | CRITICAL | AC-3 untestable — need CLI smoke test spec | OPEN |

#### Test Evidence
[Captured output from Step 5]
```

**Handoff rules:**

- **PASS** → Spec is ready to ship. Inform caller or instruct user
  to run `/shipping-release`.

- **REJECT-FIX** → Return findings. Caller fixes via
  `/planning-tasks [spec-id]`, then re-runs `/verifying-quality`.

- **REJECT-BLOCKED** → This is the testability gate in action.
  1. Document which ACs are blocked and what's missing
  2. Propose a testing spec with:
     - Spec title: "Integration test harness for [capability]"
     - What it should test (specific ACs from the blocked spec)
     - Suggested approach
  3. Tell the caller: "Spec [id] is blocked by testability gap.
     Proposed testing spec: [description]. Run `/planning-spec`
     to design the testing infrastructure, then re-verify [id]."
  4. The blocked spec does NOT ship until re-verification passes.

## Troubleshooting

### UNTESTABLE vs. MANUAL-READY confusion
**Rule of thumb:** If a developer can follow a written guide and
verify the behavior in under 5 minutes, it's MANUAL-READY. If there's
no way to verify without building new tooling first, it's UNTESTABLE.

### Tests pass but evidence is missing
**Cause:** Tests exist but QA didn't capture their output.
**Fix:** Re-run tests with output capture. A PASS verdict without
evidence is incomplete — the spec file must contain proof.

### Integration test environment unavailable
**Cause:** Docker not running, CLI not installed, API key missing.
**Decision tree:**
- Can you set up the environment now? → Do it, run the test
- Is it a CI-only concern? → Mark EXECUTABLE with prereqs, not UNTESTABLE
- Is there no test at all? → That's UNTESTABLE if the AC warrants one

### AI-generated code quality drift
**Cause:** AI accumulates complexity (cognitive complexity +39%,
code clones +12.3% per GitClear/Qodo 2025).
**Fix:** Flag duplicated logic, unnecessary abstractions, overly
complex control flow. Suggest simplifications.

### REJECT-BLOCKED feels heavy for a small spec
**Response:** The cost of shipping untested code exceeds the cost
of a small testing spec. A testing harness is an investment — it
pays off on every subsequent spec that touches the same capability.
XS testing specs (6-8 points) are common and fast to ship.
