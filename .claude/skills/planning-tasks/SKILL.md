---
name: planning-tasks
description: >
  Loop controller for per-spec development. Breaks a designed spec into
  BDD task files, runs the task loop (implementing-task ping-pong),
  performs final verification, consolidates results back to spec, and
  cleans up temporary task files.
  Use to start a spec, break into tasks, find next, or run auto loop.
argument-hint: "[spec-id] or [next] or [auto]"
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Write
  - Edit
  - Agent
metadata:
  category: workflow-automation
  pattern: context-aware-routing
---

# Planning Tasks — The Spec Loop Controller

## Role: Lead Engineer

Practical, organized, methodical. Break designs into incremental verification
steps. Run the task loop. Perform final verification. Always know what's next.

## Contract

```
Paths:  All paths relative to project root. Artifacts → docs/grimo/
Input:  docs/grimo/specs/*-<spec-id>-*.md (THE spec file, sections 1-5)
        docs/grimo/development-standards.md
Output: docs/grimo/tasks/YYYY-MM-DD-<spec-id>-<task-id>.md (temporary task files)
        Updated spec file (sections 6-7 added)
Valid:  Every SBE criterion has a corresponding task.
        Task files form ordered verification chain.
        After completion: results in spec file, task files deleted.
Next:   /implementing-task [spec-id] (task loop)
        /shipping-release (all pass)
        /verifying-quality [spec-id] (L+ or fail)
```

## Key Principles

### Spec File is the Living Document

- Task files in `docs/grimo/tasks/` are **temporary work items**
- After all tasks pass → results consolidated into spec file section 7
- Task files are then **deleted** (the spec file is the permanent record)

### POC Before Production

- When a spec introduces **new packages, SDKs, or unfamiliar APIs**, validate
  in an isolated `poc/<spec-id>/` folder first — never experiment directly in
  the project codebase.
- POC scope: minimal code proving packages work, APIs behave as documented,
  and key integration points connect.
- Only after POC passes → implement tasks in the actual project, referencing
  POC findings for correct API usage.

## Usage

```
/planning-tasks S002      # Break S002 into task files + start loop
/planning-tasks next      # Find next spec from roadmap
/planning-tasks auto      # Semi-auto loop (see below)
```

## When invoked with `next`

1. Read `docs/grimo/specs/spec-roadmap.md`
2. Find specs where dependencies `✅` and status is not `✅`
3. Artifact-driven routing:

| Status | Check | Action |
|---|---|---|
| `🔲` | — | → `/planning-spec [id]` |
| `⏳ Design` | Spec file has sections 1-5? | Yes → Phase 1. No → `/planning-spec [id]` |
| `⏳ Plan` | Task files exist? | Yes → Phase 2. No → Phase 1 |
| `⏳ Dev` | All tasks PASS? | Yes → Phase 3. No → Phase 2 |
| `✅` | — | Skip, find next |

## When invoked with a spec-id

Check which phase to enter based on existing artifacts.

### Phase 1: Create Task Files

Skip if task files already exist for this spec.

Before planning, check:
- Each acceptance criterion → one @Test? (if not, too coarse)
- Estimated size ≤ M? (if not, split)
- Criteria are concrete SBE examples? (if abstract, refine)

Too large or abstract → escalate to `/planning-spec [spec-id]`.

**Assess POC need:**

| Signal | POC needed? |
|---|---|
| Spec uses packages/SDKs not yet used in the project | Yes |
| Spec integrates with unfamiliar external APIs | Yes |
| High uncertainty on whether approach will work | Yes |
| Spec only modifies existing, well-understood code | No |
| All packages already validated by prior specs | No |

Record the decision in the spec file section 6 header:
`POC: required` or `POC: not required` with rationale.

**Create individual task files:**

Read the template from `references/task-file-template.md`.

**Add section 6 (Task Plan) to the spec file** — a lightweight index
of all tasks, their AC mapping, execution order, and POC decision.

Update `spec-roadmap.md` status to `⏳ Plan`.

### Phase 1.5: POC Validation (conditional)

Skip if POC assessment = `not required`.

**Goal**: Prove packages/SDKs/APIs work correctly in isolation before
touching the project codebase.

1. Create `poc/<spec-id>/` directory with its own minimal project setup
   (e.g., standalone build file, minimal dependencies — only what needs validation)
2. Implement the **minimum code** to validate:
   - Package imports and API calls work
   - Key integration points connect
   - Correct usage patterns (struct names, method signatures, config formats)
3. Run POC tests — all must pass
4. Document findings in the spec file section 6 under `### POC Findings`:
   - Correct API usage patterns (code snippets)
   - Gotchas discovered (deprecated APIs, version quirks)
   - Verified dependency versions
5. POC passes → proceed to Phase 2
6. POC fails → stop and report to user with findings

**POC directory is temporary** — cleaned up in Phase 3 after results
are consolidated into the spec file.

### Phase 2: Task Loop

1. Scan task files: `docs/grimo/tasks/*-<spec-id>-*.md`
2. Find first task with `Status: pending` (respect dependency order)
3. If found → `/implementing-task [spec-id]`
4. If all tasks `PASS` → proceed to Phase 3
5. If any task `FAIL` → stop and report to user

After `/implementing-task` returns, re-enter Phase 2 (check next task).

Update `spec-roadmap.md` status to `⏳ Dev` on first task start.

### Phase 3: Final Verification + Consolidation

All tasks PASS. Three steps:

**Step 1: Deterministic checks**

Run the project's standard pipeline commands as declared in the QA
strategy doc. Prefer ecosystem-native commands; fall back to custom
scripts only when explicitly noted there.

Common shape:

```
<ecosystem test command>        # e.g., gradlew test, pytest, npm test
<ecosystem coverage command>    # if coverage gating is part of the pipeline
<ecosystem arch/boundary check> # if project has module / arch rules
```

Each command must exit 0. If any fails, identify the failing task and
re-enter Phase 2. Do NOT introduce a bespoke shell wrapper around
these commands just to "normalize output" — the runner's exit code IS
the signal.

**AC coverage verification** piggybacks on the same test run: each
spec's live ACs must appear as @DisplayName / @Tag / test-name
per the AC-to-test contract documented in the QA strategy. If the
standard runner does not surface missing-AC-coverage, add a single
ecosystem-native test for that (e.g., a JUnit test that parses the
spec file and asserts a matching @DisplayName exists) — do NOT reach
for shell scripts.

**Step 2: Consolidate results into spec file**

Add section 7 (Implementation Results) to the spec file:
- Verification results (tests, lint, format)
- Key findings from implementation
- Correct usage patterns (code snippets — the most valuable part)
- AC results table

Update spec file status line to `✅ Done`.

**Step 3: Clean up temporary files**

```bash
rm docs/grimo/tasks/*-<spec-id>-*.md
rm -rf poc/<spec-id>/
```

Task details are now preserved in spec section 6 (plan + POC findings) +
section 7 (results). The spec file is the single permanent record.

### Routing

| Spec size | Checks | Action |
|---|---|---|
| XS/S/M | ✅ | Stop. Tell the user the spec is ready; ask them to run `/shipping-release`. This skill cannot auto-invoke shipping (it is marked `disable-model-invocation: true` because its actions — commit, tag, archive — require explicit user authorization). |
| XS/S/M | ❌ | Identify failing task. Re-enter loop. |
| L+ | ✅ | Always → `/verifying-quality [spec-id]` |

## Semi-Auto Mode (`auto`)

```
/planning-tasks auto
```

| Size | Behavior |
|---|---|
| **XS/S** | Full auto: plan → task loop → verify → consolidate → ship. Only stop on failure. |
| **M** | Stop after design for user confirmation. Then auto through task loop → ship. |
| **L+** | Stop at every phase boundary. Equivalent to manual mode. |

Stop conditions:
- Any task `FAIL`
- Checks fail
- Spec needs human judgment (M+ design decisions)
- No more specs available
- User interrupts

## Scope Change Mid-Flight

When the user changes requirements while the task loop is running
("drop that", "we don't need it", "defer AC-N to later"), handle the
change at this skill level. Do NOT let `/implementing-task`
reinterpret scope mid-RED.

Procedure:

1. **Stop the current task.** If a task was in progress when the
   change arrived, mark its status `SUPERSEDED` with a one-line note
   (`removed YYYY-MM-DD by user: <reason>`). SUPERSEDED is NOT a
   failure — it is a valid terminal state and Phase 3 treats it as
   "covered by explicit deferral".
2. **Propagate the change:**
   - Delete task files rendered obsolete.
   - Update `Depends On` fields of surviving tasks that previously
     depended on the removed task.
   - Update the spec's §3 acceptance criteria: mark the removed AC
     as deferred (heading convention per the QA strategy doc) with
     date and reason.
   - Update the spec's §6 task plan (table, POC rationale, AC
     coverage).
   - Update the roadmap entry if the overall spec scope collapsed.
3. **Resume the loop.** Re-enter Phase 2 against the new task graph.
   Find the next pending task.
4. **Audit downstream impact.** If the change touches
   project-level decisions, update the relevant docs in the same
   sweep. A scope change that doesn't propagate creates stale
   references. Walk this checklist explicitly for every scope change
   — state either "touched" or "intentionally not touched — reason:
   …" for each entry. No silent skips.

   ```
   - [ ] The in-flight spec file (§3 ACs, §4 interfaces, §5 file
         plan, §6 task plan, §7 results)
   - [ ] The roadmap doc (entry description, dependency graph,
         milestone table, AC summary)
   - [ ] The architecture doc (module map, ports, framework table,
         data flows, storage)
   - [ ] The development-standards doc (if a convention changes)
   - [ ] The QA strategy doc (if test pipeline changes)
   - [ ] The PRD decision log (if a prior decision is reversed or
         refined; note date + reason)
   - [ ] The glossary (if domain terms added / renamed / removed)
   - [ ] The memory directory (add a project memory entry when the
         change is load-bearing for future sessions — scope
         re-plans, stack migrations, policy reversals)
   ```

## Handoff

After Phase 1 or when Phase 2 finds a pending task, immediately invoke
`/implementing-task [spec-id]`. Do not wait for user confirmation.

After Phase 3 verification passes (XS/S/M), **stop and tell the user
the spec is ready to ship**. Instruct them to run `/shipping-release`
themselves. This skill cannot auto-invoke the shipping skill: shipping
is intentionally gated with `disable-model-invocation: true` because
its actions (commit, tag, archive) require explicit user authorization.

After Phase 3 for L+ specs, immediately invoke
`/verifying-quality [spec-id]`. Do not wait for user confirmation.

## Escalate

Spec too large or criteria too vague → invoke `/planning-spec [spec-id]` to return to SA/SD.
