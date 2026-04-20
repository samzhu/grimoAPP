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
  version: 5.0.0
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
Step 0: Gather context — read spec, QA strategy, dev standards
Step 1: Layer 1 — Automated checks (unit, compile, lint)
Step 2: Layer 2 — Coverage & integration
Step 3: Layer 3 — Manual verification readiness
Step 4: Testability gate — can every AC actually be verified?
Step 5: Execute tests in isolated environment, capture evidence
Step 6: Code quality review
Step 7: Design sync check
Step 8: Verdict (evidence-based)
Step 9: Record results to spec file & handoff
```

## Process

### 0. Gather context

Read the project's key documents to understand its conventions:

1. **The spec file** — all sections (design through results)
2. **QA strategy / test documentation** — discover the project's
   test pipeline commands, coverage targets, test classification
3. **Development standards / coding conventions** — naming rules,
   forbidden patterns, architectural constraints
4. **Glossary / domain model** — terminology consistency

Use any prior implementation results as context, but **re-verify
independently** — do not trust prior findings blindly.

### 1. Layer 1 — Automated checks

Run the project's standard test pipeline. Every command must exit 0.

**Discover commands from the project's own documentation.** Do not
assume any particular ecosystem. Look for:
- Test runner commands in README, QA docs, or CI configuration
- Build verification commands (compile, lint, type-check)
- Architecture/module boundary checks if the project enforces them

### 2. Layer 2 — Coverage & integration

**Coverage:** If the project has coverage tooling configured, run it
and check results for changed files against the project's stated
targets. Flag new production files with 0% coverage.

**Integration tests:** Determine whether the spec touches external
systems (subprocesses, databases, containers, APIs). If yes, run
integration tests with appropriate environment guards. If the
environment is unavailable, mark as pending — not a failure.

### 3. Layer 3 — Manual verification readiness

Identify ACs that require human interaction — interactive CLI
sessions, UI behavior, end-to-end user workflows that cannot be
fully automated. Check whether instructions exist for a human to
execute these verifications.

### 4. Testability gate

**For each AC, classify its verification status:**

| Classification | Definition | Action |
|---|---|---|
| `VERIFIED` | Automated test exists, ran, and passed | Record output as evidence |
| `EXECUTABLE` | Test exists but could not run (environment missing) | Run now if possible; otherwise mark pending with prereqs |
| `MANUAL-READY` | Written instructions exist for human verification | Confirm instructions are complete and actionable |
| `MANUAL-MISSING` | Needs human verification but no instructions exist | Write the instructions, reclassify as MANUAL-READY |
| `UNTESTABLE` | **Should be verifiable but no mechanism exists** | **REJECT — propose a spec to build the verification capability** |

**The UNTESTABLE classification triggers a hard stop.**

An AC is UNTESTABLE when:
- It describes observable behavior (output, side effects, state
  changes) that SHOULD have verification — automated or scripted
- But no test, no script, and no written instructions exist
- And the gap cannot be filled by writing instructions alone —
  it requires building new test infrastructure

**When UNTESTABLE is found:**

1. Document which ACs are untestable and why
2. Propose a **testing infrastructure spec** — what capability is
   missing, what it should verify, suggested approach
3. REJECT with verdict `BLOCKED-BY-TESTABILITY`
4. Route to the project's spec planning workflow to design the
   testing capability
5. After the testing spec ships, re-run this verification on the
   original spec

**This is not bureaucracy — it's the difference between "we think
it works" and "we proved it works."**

### 5. Execute tests and capture evidence

For every VERIFIED and EXECUTABLE AC, **actually run the
verification** and capture the output.

#### Isolated test environment (hermetic testing)

When the spec produces a runnable artifact (compiled binary,
packaged application, container image, installable tool), **do not
test in the source tree.** The source tree carries implicit context
(config files, cached state, sibling directories) that masks
whether the artifact works standalone.

**Principle: test the artifact, not the source tree.**

Protocol:
1. **Clean build** — run the project's clean + build commands
2. **Create isolated directory** — empty directory outside the
   source tree (or a subdirectory gitignored by the project)
3. **Copy only the artifact** — the built output, nothing else
4. **Set up minimal fixtures** — only test data the spec requires
5. **Run from the isolated directory** — capture all output
6. **Compare** — actual output vs expected per the AC
7. **Record evidence** — write results back to the spec file
8. **Tear down** — delete the isolated directory

When to use: any spec that produces an artifact with user-visible
behavior. Skip for pure library/internal API specs where unit tests
in the source tree are sufficient.

#### Evidence standard

Evidence means captured execution output — not "code review
suggests it works."

For each verified AC, record:
- The command that was run
- The actual output (or a meaningful summary)
- The comparison to expected behavior
- PASS or FAIL

For tests involving non-deterministic output (LLM responses, AI
tools), apply **Golden Path testing** — verify structure and
presence, not exact content:
- Output is non-empty
- Format matches expected schema
- Error inputs produce clean messages (no stack traces)

**Do NOT mark an AC as PASS without execution evidence.**

### 6. Code quality review

Check against the project's own standards. Common areas:

- **Naming conventions** — types, methods, packages match project norms
- **Architectural constraints** — correct module/layer placement
- **Immutability** — domain types properly encapsulated
- **Dependency injection** — follows project convention
- **Forbidden patterns** — check the project's explicit ban list
- **Documentation accuracy** — comments match implementation?
  This is the most common blind spot in AI-generated code.
- **Security** — no hardcoded secrets, no injection vectors.
  AI-generated code has a 2.74x higher XSS introduction rate
  (GitClear 2025) — scrutinize input handling.
- **No orphaned TODO/FIXME** in changed files

### 7. Design-section sync check

Compare the spec's design sections against actual implementation:
- Statements that no longer match reality
- Missing annotations marking implementation divergences
- Findings that should have updated the design documentation

### 8. Verdict

**Four-layer result table:**

```markdown
| Layer | Result | Detail |
|-------|--------|--------|
| Automated tests | PASS/FAIL | [commands + outcome] |
| Coverage / Integration | PASS/SKIP/PENDING | [coverage or IT status] |
| Manual verification | READY/MISSING/N-A | [instructions location] |
| Testability gate | CLEAR/BLOCKED | [all ACs verifiable?] |
```

**Verdict outcomes:**

| Verdict | Condition | Next action |
|---------|-----------|-------------|
| `PASS` | All layers pass, all ACs have evidence or are MANUAL-READY | Ship |
| `REJECT-FIX` | Tests fail, quality issues, or missing instructions | Fix, re-verify |
| `REJECT-BLOCKED` | UNTESTABLE ACs — verification infrastructure gap | Build test capability first |

**Severity levels for individual findings:**

| Level | Definition | Effect |
|-------|-----------|--------|
| CRITICAL | Missing AC coverage, test doesn't verify what it claims, security issue, build breakage, UNTESTABLE AC | Blocks shipping |
| IMPORTANT | Documentation drift, missing edge-case coverage, standards violation | Should fix before shipping |
| MINOR | Style nit, cosmetic issue | Note for future |

### 9. Record results and handoff

**All evidence goes into the spec file — the single permanent record.**

Append results to the spec's implementation/results section. Testing
instructions (how to test) may live separately, but test outcomes
and evidence (what happened when we tested) belong in the spec.

After recording evidence, **tear down the isolated test environment.**
The spec file preserves the proof; the test directory is ephemeral.

**Handoff:**

- **PASS** → Spec is ready to ship.

- **REJECT-FIX** → Return findings. Fix issues, then re-verify.

- **REJECT-BLOCKED** → Testability gate activated.
  1. Document which ACs are blocked and what verification capability
     is missing
  2. Propose a testing spec — title, scope, suggested approach
  3. The blocked spec does NOT ship until the testing capability
     exists and re-verification passes

## Troubleshooting

### UNTESTABLE vs. MANUAL-READY
**Rule of thumb:** If a developer can follow written instructions
and verify behavior in under 5 minutes, it's MANUAL-READY. If
verification requires building new tooling, it's UNTESTABLE.

### Evidence missing despite passing tests
**Cause:** Tests ran but output wasn't captured.
**Fix:** Re-run with output capture. A PASS without recorded
evidence is incomplete.

### Integration test environment unavailable
**Decision tree:**
- Can you set up the environment now? → Do it, run the test
- Is it a CI-only concern? → Mark EXECUTABLE with prereqs
- Is there no test at all? → UNTESTABLE if the AC warrants one

### AI-generated code quality drift
**Cause:** AI-generated code accumulates complexity over time
(cognitive complexity +39%, code duplication +12.3% per
GitClear/Qodo 2025 data).
**Fix:** Flag duplicated logic, unnecessary abstractions, overly
complex control flow.

### REJECT-BLOCKED feels heavy for a small change
**Response:** The cost of shipping unverified behavior exceeds the
cost of a small testing spec. Test infrastructure is an investment
that pays off on every subsequent change to the same capability.
