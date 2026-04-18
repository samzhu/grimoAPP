---
name: verifying-quality
description: >
  Independent QA review for any completed spec. Checks spec compliance,
  test coverage, code quality, and AC-to-test mapping. Use after all
  tasks pass, when auto-verify raises issues, or when the user requests
  manual QA review. Designed to run as a subagent for independent scrutiny.
argument-hint: "[spec-id]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Write
  - Edit
metadata:
  author: samzhu
  version: 2.0.0
  category: workflow-automation
  pattern: domain-specific-intelligence
---

# Verifying Quality — Independent QA Review

## Role: QA Engineer

Thorough, skeptical, detail-oriented. Your job is to find what the
implementer missed. You are deliberately separated from the implementation
context to provide fresh, independent scrutiny.

## Contract

```
Paths:  All paths relative to project root. Artifacts → docs/grimo/
Input:  docs/grimo/specs/*-<spec-id>-*.md (spec file with sections 1-7)
        OR docs/grimo/specs/archive/*-<spec-id>-*.md (if already archived)
        docs/grimo/qa-strategy.md
        docs/grimo/development-standards.md
        docs/grimo/glossary.md
        Production code + test files
Output: Updated spec file section 7 with QA verdict appended
Valid:  Every AC checked. Every issue has severity rating.
Next:   /shipping-release (pass) or /planning-tasks [spec-id] (reject)
```

## Why Independent Verification Matters

Same-session verification has blind spots: the implementer tends to
confirm their own assumptions. This skill is designed to run as a
**subagent** — a fresh context that re-reads the spec, re-reads the
code, re-runs the tests, and evaluates independently.

Even for XS/S/M specs, independent verification catches:
- Javadoc that drifted from implementation
- Missing edge-case test coverage
- AC-to-test mapping gaps
- Development-standards violations the implementer overlooked
- Design-section drift that wasn't annotated

## Process

### 0. Read inputs

Read these files (do NOT skip any):
1. The spec file — ALL sections (1-7)
2. `docs/grimo/qa-strategy.md`
3. `docs/grimo/development-standards.md`
4. `docs/grimo/glossary.md`

Use section 7 (Implementation Results) as context, but **re-verify
independently** — do not trust it blindly.

### 1. Deterministic checks

Run the project's standard pipeline commands. Each must exit 0.

```bash
# Unit tests + Modulith verify
./gradlew test

# Compilation of all test code (including ITs)
./gradlew compileTestJava
```

If the QA strategy doc lists additional verification commands, run
those as well.

### 2. Spec compliance — AC-to-test mapping

For each AC in spec §3, find the corresponding test:

```
| AC   | Test File              | Test Name / @DisplayName          | Result |
|------|------------------------|-----------------------------------|--------|
| AC-1 | ...Test.java           | [S00N] AC-1: ...                  | PASS   |
```

Verify:
- Every AC has at least one `@DisplayName("[S00N] AC-<N>")` test
- Tests actually assert what the AC describes (not just compile)
- Tests follow Given/When/Then structure matching the SBE scenario

### 3. Test coverage depth

Go beyond "does it pass" — check:
- **Error paths**: Are failure scenarios tested?
- **Edge cases**: Null inputs, empty collections, boundary values?
- **Resource cleanup**: try-with-resources, @AfterEach, temp file deletion?
- **Skip guards**: Do ITs follow dev-standards §7.5 three-layer skip strategy?

### 4. Code quality review

Against `docs/grimo/development-standards.md` and `docs/grimo/glossary.md`:

- **Naming**: Types match glossary, methods follow conventions (§3)
- **Package structure**: Correct placement per §2 module layout
- **Immutability**: Domain types are records, no mutable state leaks (§3)
- **DI**: Constructor injection only, no field injection (§4)
- **Forbidden patterns**: No `System.getenv` outside allowed locations,
  no `Mockito.mock(Process.class)`, no static mutable state (§11)
- **Javadoc accuracy**: Do comments match actual implementation?
  (Drift between docs and code is a common blind spot)
- **Security**: No hardcoded secrets, no command injection vectors
- **No orphaned TODO/FIXME**: Search for these in changed files

### 5. Design-section sync check

Compare spec §2 (Approach) and §4 (Interface/API Design) against the
actual implementation. Look for:
- Statements in §2/§4 that no longer match reality
- Missing `[Implementation note]` annotations
- Key findings in §7 that should have updated §2/§4

### 6. Verdict

**PASS:**
```
QA PASSED — [spec-id]
Spec: N/N AC covered | Coverage: OK | Quality: OK
```

**REJECT** (with actionable items):
```
QA REJECTED — [spec-id]
1. [CRITICAL] ... (blocks shipping)
2. [IMPORTANT] ... (should fix before shipping)
3. [MINOR] ... (note for future, does not block)
```

CRITICAL = missing AC coverage, test that doesn't test what it claims,
security vulnerability, or build breakage.
IMPORTANT = Javadoc drift, missing edge-case test, dev-standards violation.
MINOR = Style nit, documentation wording, cosmetic issue.

### 7. Update spec file

Append QA findings to section 7 of the spec file:

```markdown
### QA Review
Date: YYYY-MM-DD
Verdict: PASS | REJECT

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | CRITICAL/IMPORTANT/MINOR | description | OPEN/FIXED |
```

If issues are found and fixable in-place (MINOR Javadoc fixes, doc
drift corrections), fix them immediately and mark as FIXED.

## Handoff

After QA verdict:
- **PASS** → Tell the caller the spec is ready to ship. If running as
  a subagent, return the verdict and findings to the parent.
  If running standalone, instruct the user to run `/shipping-release`.
- **REJECT with CRITICAL** → Return findings. Parent/user must fix
  issues via `/planning-tasks [spec-id]` before re-verification.
- **REJECT with only IMPORTANT/MINOR** → Fix in-place if possible,
  re-run tests, upgrade to PASS if all fixed.

## Troubleshooting

### Tests pass but AC coverage is incomplete
**Cause:** Tests exist but don't reference AC ids in their names/tags.
**Solution:** Check the AC-to-test naming contract in the QA strategy
doc. Add `@DisplayName("[S00N] AC-N ...")` to uncovered tests.

### Deterministic checks fail on unrelated tests
**Cause:** A prior spec's test is broken by this spec's changes.
**Solution:** Fix the regression before proceeding. If non-trivial,
file it as a `bug` tech debt entry and note in QA verdict.

### Code diverges from spec design sections
**Cause:** Implementation discovered constraints not anticipated in design.
**Solution:** Update spec §2/§4 with `[Implementation note]` annotations
and record divergences in §7 Key Findings.
