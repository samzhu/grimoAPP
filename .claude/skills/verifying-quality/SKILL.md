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

### 0. Read spec file

Read the spec file sections 1-7. Use section 7 (Implementation Results)
as context — don't repeat checks that already passed. Focus on gaps
and deeper inspection.

### 1. Run deterministic checks first

```bash
docs/grimo/scripts/verify-tests-pass.sh .
docs/grimo/scripts/verify-spec-coverage.sh [spec-id] .
```

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

## Handoff

After QA verdict:
- **PASS** → immediately invoke `/shipping-release`. Do not wait for user confirmation.
- **REJECT** → update spec file, then immediately invoke `/planning-tasks [spec-id]`.
