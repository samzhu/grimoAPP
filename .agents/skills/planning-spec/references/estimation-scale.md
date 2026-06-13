# Spec Estimation Scale Reference

Six-dimension diagnostic scoring system for spec size estimation.
Each dimension scores 1–3. The diagnostic total is `six_factor_score`;
the final planning value is `story_points` on a modified Fibonacci-style
scale.

## Formula

```
six_factor_score = tech_risk + uncertainty + dependencies + scope + testing + reversibility
story_points = calibrated mapping from six_factor_score
```

`six_factor_score` explains why the work is complex. `story_points` is the
roadmap / planning number. Do not write raw `six_factor_score` as final
story points.

| six_factor_score | Size | story_points | Design depth | User interaction |
|------------------|------|--------------|--------------|------------------|
| 6                | XS   | 1            | Skip approach comparison; recommend directly | 3-question intake + up to 1 grill question |
| 7–8              | XS   | 2            | Skip approach comparison; recommend directly | 3-question intake + up to 1 grill question |
| 9–10             | S    | 3            | Brief comparison | 3-4 questions, confirm approach |
| 11–12            | S/M  | 5            | Brief comparison + key contract checks | 3-4 questions, confirm approach |
| 13–14            | M    | 8            | Full comparison + interface definition | Confirm approach + key interfaces |
| 15–16            | L    | 13           | Deep design + PoC spike may be needed | Confirm at each phase boundary |
| 17–18            | XL   | 20           | Must be decomposed — do not ship as XL | N/A |

Why this split exists:

- The six dimensions prevent "gut feel only" estimation by forcing the
  planner to name risk, uncertainty, dependencies, scope, testing, and
  reversibility.
- Story points stay relative and coarse-grained. The modified Fibonacci-style gaps
  reflect increasing uncertainty and avoid false precision on large work.
- `20` means "too large for direct task planning"; split into 2+ specs, then
  estimate each child spec again.

---

## Dimension Definitions and Rubrics

### 1. Technical Risk (技術風險)

How likely is it that a technical assumption will prove wrong during
implementation?

Grounded in COCOMO II's **RESL (Risk Resolution) scale factor**, which
scores from "20% risk eliminated" (Very Low) to "100% risk eliminated"
(Extra High), and Boehm's Top-10 Software Risk Items checklist [4].

| Score | Label | Criteria |
|-------|-------|----------|
| 1 | Low | Well-known APIs; team has prior experience; documented patterns exist in the codebase. Corresponds to COCOMO II RESL "High" (risk largely eliminated). |
| 2 | Medium | New API surface but well-documented; or known API used in a novel combination. Corresponds to RESL "Nominal" (some residual risk). |
| 3 | High | Undocumented/pre-1.0 API; known open issues; requires spike to validate; library internals may need inspection. Corresponds to RESL "Low" (significant unresolved risk). Triggers parallel research sub-agents per SKILL.md Research section. |

### 2. Uncertainty (不確定性)

How well-defined are the requirements before design starts?

Grounded in McConnell's **Cone of Uncertainty** [5], which shows
estimates at project inception can vary 0.25x–4.0x, narrowing as
requirements stabilize. Also informed by PERT three-point estimation
[3] where the spread between optimistic and pessimistic values
quantifies uncertainty.

| Score | Label | Criteria |
|-------|-------|----------|
| 1 | Clear | Requirements fully specified in roadmap; no open questions; SBE drafts are concrete. McConnell's "post-requirements" phase (0.67x–1.5x). |
| 2 | Mostly clear | 1-2 open design questions that the grill loop should resolve; SBE drafts need refinement. McConnell's "post-product-definition" phase (0.5x–2.0x). |
| 3 | Ambiguous | Multiple open questions; scope may shift during grill; SBE drafts are placeholders. McConnell's "initial concept" phase (0.25x–4.0x). |

### 3. Dependencies (依賴關係)

How many upstream specs or external systems must be in place, and
how stable are they?

Grounded in COCOMO II's **SITE (Multi-site Development)** and **TEAM
(Team Cohesion)** factors [1], SEI's Taxonomy-Based Risk Identification
"external interface" risk category [6], and Mike Cohn's multi-team
planning guidance (Chapter 18 of *Agile Estimating and Planning*) [7].

| Score | Label | Criteria |
|-------|-------|----------|
| 1 | Standalone | Depends only on `core` (OPEN) or no other module; no external system. |
| 2 | Single dependency | Depends on 1 shipped spec + 1 external system (e.g., Docker Daemon). |
| 3 | Multiple dependencies | Depends on 2+ specs or 2+ external systems; or depends on an unshipped spec. |

### 4. Scope (範疇)

How many files, interfaces, and module boundaries does this spec touch?

Grounded in Mike Cohn's **"Amount of Work"** dimension of story points
[7] and COCOMO II's **CPLX (Product Complexity)** cost driver, which
scores across five sub-domains: control logic, computational logic,
device-dependent operations, data management, and UI management [1].

| Score | Label | Criteria |
|-------|-------|----------|
| 1 | Minimal | 1-3 production files; single module; no cross-module wiring. |
| 2 | Moderate | 4-8 production files; single module with API surface; or touches 2 modules. |
| 3 | Large | 9+ production files; or 3+ modules; or new module boundary setup. |

### 5. Testing (測試複雜度)

How complex is the test setup, and how many test types (T0–T6) are
involved?

Grounded in Ferrer, Chicano & Alba's quantitative model of testing
complexity [12], NASA SWEHB Section 7.6 on test estimation [13]
(which identifies test environment setup as an independent estimation
variable), and COCOMO II's **RELY (Required Reliability)** cost driver [1].

| Score | Label | Criteria |
|-------|-------|----------|
| 1 | Simple | Pure JUnit (T0) only; no Spring context, no external system. |
| 2 | Moderate | Needs Spring slice or `@ApplicationModuleTest` (T1-T2); or simple Testcontainers. |
| 3 | Complex | Needs Docker Daemon running (T3-T4); or multi-container; or real CLI binary; or flaky-prone external I/O. |

### 6. Reversibility (可逆性)

If the spec's design proves wrong, how hard is it to undo?

Grounded in Jeff Bezos' **Type 1 / Type 2 Decision** framework [9]
("one-way door" vs "two-way door"), Martin Fowler's argument that
reducing irreversibility is the key to taming complexity [10], Kent
Beck's identification of irreversibility as one of four "complexity
monsters" [11], and IFPUG FPA's **GSC14 (Facilitate Change)** [2].

| Score | Label | Criteria |
|-------|-------|----------|
| 1 | Two-way door | Internal types only; no published API; no persisted state; no downstream consumers yet. Can be fully reverted in one commit. |
| 2 | Partially reversible | Published API (`@NamedInterface`) with 1-2 consumers; or persisted schema with migration path. Revert requires coordinated changes. |
| 3 | One-way door | Published API with 3+ consumers; or breaking change to shipped data format; or external system integration that others depend on. Revert is a project-level event. |

---

## Story Point Calibration Examples

### Copy or label update -> six_factor_score 6 / XS / 1 point

| Dimension | Score | Rationale |
|-----------|-------|-----------|
| Tech risk | 1 | No framework or runtime behavior changes. |
| Uncertainty | 1 | Target wording is already known. |
| Dependencies | 1 | No upstream feature or external system dependency. |
| Scope | 1 | One visible label, message, or document paragraph. |
| Testing | 1 | Static review or existing snapshot coverage is enough. |
| Reversibility | 1 | Revert is one small text change. |

### Small contained fix -> six_factor_score 8 / XS / 2 points

| Dimension | Score | Rationale |
|-----------|-------|-----------|
| Tech risk | 1 | Uses existing patterns and no new dependency. |
| Uncertainty | 1 | Expected behavior is clear. |
| Dependencies | 1 | No upstream feature or external system dependency. |
| Scope | 2 | Touches a few nearby files or one small helper. |
| Testing | 2 | Needs one focused automated test or snapshot update. |
| Reversibility | 1 | Can be reverted without contract or data cleanup. |

### Existing flow small feature -> six_factor_score 10 / S / 3 points

| Dimension | Score | Rationale |
|-----------|-------|-----------|
| Tech risk | 1 | Uses known framework/API behavior. |
| Uncertainty | 2 | Needs 1-2 contract decisions, such as field name or empty-state behavior. |
| Dependencies | 1 | No unshipped upstream feature. |
| Scope | 2 | Touches a form/API/handler and nearby tests. |
| Testing | 2 | Needs focused unit/API/UI coverage, but no complex environment. |
| Reversibility | 2 | Published field or visible behavior may need coordinated cleanup if reverted. |

### Complete feature on existing patterns -> six_factor_score 12 / S/M / 5 points

| Dimension | Score | Rationale |
|-----------|-------|-----------|
| Tech risk | 2 | Known API is used in a new combination. |
| Uncertainty | 2 | User flow is understood, but edge cases need grill confirmation. |
| Dependencies | 2 | Depends on one shipped capability or one external runtime. |
| Scope | 2 | Crosses more than one application layer, but no new module boundary. |
| Testing | 2 | Needs integration-style coverage using existing test setup. |
| Reversibility | 2 | Revert requires coordinated contract and UI/test cleanup. |

### Cross-layer contract feature -> six_factor_score 14 / M / 8 points

| Dimension | Score | Rationale |
|-----------|-------|-----------|
| Tech risk | 2 | Framework surface is documented, but behavior must be verified in this codebase. |
| Uncertainty | 2 | Requirements are mostly clear, with several acceptance details to confirm. |
| Dependencies | 2 | Depends on one shipped capability plus one external system or runtime. |
| Scope | 3 | Crosses 3+ modules/layers or introduces a module boundary. |
| Testing | 3 | Needs environment-backed or end-to-end verification. |
| Reversibility | 2 | Persisted or public contract can be migrated, but not removed casually. |

### High-risk integration or schema change -> six_factor_score 16 / L / 13 points

| Dimension | Score | Rationale |
|-----------|-------|-----------|
| Tech risk | 3 | Source-level research or POC evidence is needed before design confidence. |
| Uncertainty | 2 | Main goal is known, but failure cases and rollback path need confirmation. |
| Dependencies | 3 | Depends on multiple upstream capabilities or external systems. |
| Scope | 3 | Crosses multiple modules or changes durable data shape. |
| Testing | 3 | Needs integration or runtime-backed verification with higher setup cost. |
| Reversibility | 2 | Migration path exists, but rollback requires coordinated changes. |

### XL work item -> six_factor_score 17-18 / XL / 20 points, split required

If `six_factor_score` reaches 17+, do not continue as one spec. Use `20`
only to signal "too large"; split the work into 2+ smaller specs and estimate
each one separately.

---

## Usage Notes

- **Three scoring moments**:
  1. **Initial estimate** — from roadmap, before grill (rough)
  2. **Re-score after grill** — `/planning-spec` may shift score after
     grill reveals hidden complexity or simplifies scope
  3. **Final re-score at ship** — `/shipping-release` re-scores against
     actual implementation evidence (Round-N pivots, scope creep / shrink,
     test complexity actually needed). Final `story_points` lands on
     `spec-roadmap.md` 點數 column; `six_factor_score` stays in the spec as
     diagnostic evidence. See `shipping-release/SKILL.md` § Re-score size.
- **Why re-score at ship matters:** The roadmap is the project's ground
  truth on spec size distribution. Stale initial estimates hide systematic
  underestimation patterns. Future planners use this data to calibrate
  scoring on new specs — recurring bucket shifts (e.g., M-shipping-as-L
  due to GraalVM AOT pitfalls per `debugging-playbook.md`) surface as
  signal not noise.
- **When in doubt, score higher.** Per McConnell [5], underestimation
  is the most common estimation failure mode. Overestimation just
  means more design rigor, which is cheap.
- **XL = mandatory split.** If `six_factor_score` reaches 17+, decompose into 2+
  specs before proceeding.
- **Tech risk 3 triggers research.** Any spec with tech_risk = 3 must
  dispatch parallel sub-agents to verify load-bearing APIs before the
  first grill question (see SKILL.md Research section).
- **Reversibility 1 is common early.** MVP specs naturally score low
  on reversibility because no downstream consumers exist yet. This
  is expected and correct — the score should reflect the *current*
  state, not hypothetical future consumers.
- **Story points are not time.** They are relative planning values. Do not
  translate `1, 2, 3, 5, 8, 13, 20` directly into hours or days.

---

## References

[1] Boehm, B.W. et al. *Software Cost Estimation with COCOMO II*.
    Prentice Hall, 2000. ISBN: 0-13-026692-2.
    Also: Boehm, B.W. (1996). "Cost Models for Future Software Life
    Cycle Processes: COCOMO 2.0." *Annals of Software Engineering*.
    https://link.springer.com/article/10.1007/BF02249046

[2] Albrecht, A.J. (1979). "Measuring Application Development
    Productivity." *Proceedings of the IBM Applications Development
    Symposium*. Standardized by IFPUG: *Function Point Counting
    Practices Manual, Release 4.3.1*. https://ifpug.org/ifpug-standards/fpa

[3] Malcolm, D.G. et al. (1959). "Application of a Technique for
    Research and Development Program Evaluation." *Operations Research*,
    7(5), 646-669. Modern application: PMI, *PMBOK Guide*, 6th Ed.,
    Section 6.4.

[4] Boehm, B.W. (1991). "Software Risk Management: Principles and
    Practices." *IEEE Software*, 8(1), 32-41.
    DOI: 10.1109/52.62930

[5] McConnell, S. *Software Estimation: Demystifying the Black Art*.
    Microsoft Press, 2006. ISBN: 978-0-7356-0535-0.

[6] Carr, M. et al. (1993). *Taxonomy-Based Risk Identification*.
    CMU/SEI-93-TR-006.
    https://www.sei.cmu.edu/documents/1077/1993_005_001_16166.pdf

[7] Cohn, M. *Agile Estimating and Planning*. Prentice Hall, 2005.
    ISBN: 978-0-13-147941-8. Summary:
    https://www.mountaingoatsoftware.com/blog/what-are-story-points

[8] Scaled Agile, Inc. "WSJF." *Scaled Agile Framework (SAFe)*.
    https://framework.scaledagile.com/wsjf
    Theory: Reinertsen, D.G. *The Principles of Product Development
    Flow*. Celeritas Publishing, 2009.

[9] Bezos, J. "2015 Letter to Shareholders." Amazon.com, Inc., 2016.
    https://s2.q4cdn.com/299287126/files/doc_financials/annual/2015-Letter-to-Shareholders.PDF

[10] Fowler, M. "Is Design Dead?" martinfowler.com, 2004.
     https://martinfowler.com/articles/designDead.html

[11] Beck, K. "Taming Complexity with Reversibility." TidyFirst
     (Substack), November 17, 2023.
     https://tidyfirst.substack.com/p/taming-complexity-with-reversibility

[12] Ferrer, J., Chicano, F. & Alba, E. (2013). "Estimating software
     testing complexity." *Information and Software Technology*, 55(12),
     2125-2139. DOI: 10.1016/j.infsof.2013.07.007

[13] NASA. "Software Test Estimation and Testing Levels." *SWEHB*,
     Section 7.6.
     https://swehb.nasa.gov/display/SWEHBVD/7.6+-+Software+Test+Estimation+and+Testing+Levels

[14] Atlassian. "Fibonacci Story Points."
     https://www.atlassian.com/agile/project-management/fibonacci-story-points

[15] Scrum.org. "Myth 9: Story Points are Required in Scrum."
     https://www.scrum.org/resources/blog/myth-9-story-points-are-required-scrum
