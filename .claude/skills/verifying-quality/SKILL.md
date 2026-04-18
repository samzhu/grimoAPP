---
name: verifying-quality
description: >
  Manual QA review for L+ specs or when auto-verify fails. Checks feature
  completeness, test coverage, code quality against spec. Use for large
  specs, when auto-verify raised issues, or manual QA is requested.
  Do NOT use for XS/S/M specs that passed auto-verify.
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
  version: 1.0.0
  category: workflow-automation
  pattern: domain-specific-intelligence
---

# Verifying Quality (Manual QA)

## Role: QA Engineer

Thorough, skeptical, detail-oriented. Verify what was built matches spec.

## Contract

```
Paths:  All paths relative to project root. Artifacts → docs/grimo/
Input:  docs/grimo/specs/*-<spec-id>-*.md (THE spec file with sections 1-7)
        docs/grimo/qa-strategy.md
        Code and test files
Output: Updated spec file section 7 with QA verdict appended
Valid:  Every criterion checked. Every issue has severity.
Next:   /shipping-release (pass) or /planning-tasks [spec-id] (reject)
```

## When to use

- L+ specs (13+ story points) — always manual QA
- Auto-verify in `/planning-tasks` raised issues
- User explicitly requests manual review

For XS/S/M specs that passed auto-verify, skip this — go to `/shipping-release`.

## Process

### 0. Read inputs

Read the spec file sections 1-7 and the QA strategy doc. Use section 7
(Implementation Results) as context — don't repeat checks that already
passed. Focus on gaps and deeper inspection.

### 1. Run deterministic checks first

Run the project's standard pipeline commands as declared in the QA
strategy doc. Prefer ecosystem-native commands over custom scripts.

Common shape (adapt to the project's actual pipeline):

```
<ecosystem test command>           # e.g., ./gradlew check
<ecosystem coverage command>       # e.g., ./gradlew jacocoTestCoverageVerification
<ecosystem arch/boundary check>    # e.g., modulith verify (if part of test suite)
```

Each command must exit 0. If the QA strategy doc lists project-specific
verification scripts, run those as well.

### 2. Spec compliance

```
| Criterion            | Test File   | Test Name         | Result |
|----------------------|-------------|-------------------|--------|
```

### 3. Test coverage

Error paths? Edge cases? Cleanup/teardown?

### 4. Code quality

Against `docs/grimo/development-standards.md` and `docs/grimo/glossary.md`:
- Correct patterns, naming, structure
- Type/function names match glossary entries
- No deprecated APIs, security issues
- No orphaned TODO/FIXME

### 5. Verdict

**Pass:**
```
QA PASSED — [spec-id]
Spec: N/N | Coverage: OK | Quality: OK
```

**Reject** (with severity):
```
QA REJECTED — [spec-id]
1. [CRITICAL] ...
2. [IMPORTANT] ...
3. [MINOR] ...
```

### 6. Update spec file

Append QA findings to section 7 of the spec file:

```markdown
### QA Review
Date: YYYY-MM-DD
Verdict: PASS | REJECT

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | CRITICAL/IMPORTANT/MINOR | ... | OPEN/FIXED |
```

Update `spec-roadmap.md` to `⏳ QA` on pass.

## Troubleshooting

### Tests pass but AC coverage is incomplete
**Cause:** Tests exist but don't reference AC ids in their names/tags.
**Solution:** Check the AC-to-test naming contract in the QA strategy
doc. Add `@DisplayName("AC-N ...")` or equivalent to uncovered tests.

### Deterministic checks fail on unrelated tests
**Cause:** A prior spec's test is broken by this spec's changes.
**Solution:** Fix the regression before proceeding. If the fix is
non-trivial, file it as a `bug` tech debt entry and note in QA verdict.

### Code diverges from spec design sections
**Cause:** Implementation discovered constraints not anticipated in design.
**Solution:** Update spec sections 2/4 with `[Implementation note]`
annotations and record divergences in section 7 Key Findings.

## Handoff

After QA verdict:
- **PASS** → immediately invoke `/shipping-release`. Do not wait for user confirmation.
- **REJECT** → update spec file, then immediately invoke `/planning-tasks [spec-id]`.
