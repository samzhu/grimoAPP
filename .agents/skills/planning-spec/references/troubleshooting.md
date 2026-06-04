# Planning Spec Troubleshooting

Use this when the design loop stalls, the user corrects the agent repeatedly, or research findings invalidate the current plan.

## User Keeps Correcting Design Direction

Symptom: user provides URLs or source-code facts the designer should have found.

Root cause: dependency research was skipped or under-scoped.

Fix: stop the grill loop, return to research Step 0.5, map the pinned libraries' public interfaces, then resume clarification.

## Approach Comparison Rebuilt After Research

Symptom: comparison table was presented, then research invalidated it.

Root cause: Phase 2 research gate was violated.

Fix: never present approach comparisons until research agents return and findings are integrated.

## Same Ecosystem Libraries Do Not Integrate

Symptom: design bridges Library A and Library B, but one already has an SPI or extension point.

Root cause: one dependency was researched without mapping the other dependency's extension points.

Fix: map each library independently before researching integration.

## Roadmap Contradicts Actual API

Symptom: roadmap says "use X" but X does not exist or behaves differently.

Root cause: roadmap is intentionally coarse-grained.

Fix: note the contradiction in §2, design from verified facts, and update the roadmap if the spec title/dependency needs correction.

## Framework Already Solves The Problem

Symptom: `/planning-tasks` POC discovers the framework already provides the capability the spec planned to build.

Root cause: research mapped exposed APIs but did not validate existing behavior.

Fix: before adding custom code, inspect actual behavior or declare a POC-required hypothesis.

## New Dependency Solves A Non-Problem

Symptom: spec adds a dependency for behavior the existing stack already handles.

Root cause: research focused on the new dependency before asking what the current stack provides.

Fix: answer "what does the existing stack provide today?" from source, docs, or POC before evaluating new dependencies.

## Parser Or Serializer Misses The Standard

Symptom: implementation later discovers the parser cannot handle standard-compliant nested data, arrays, or multiline values.

Root cause: Step 0.75 did not inspect implementation behavior.

Fix: for parsers, serializers, codecs, and adapters, read implementation internals or create a POC fixture that exercises the standard's full shape.

## Parallel Type System

Symptom: spec defines a domain record that mirrors an existing framework record, causing mapping friction.

Root cause: framework type suitability was not checked before designing custom domain types.

Fix: use framework records directly or wrap them thinly when they are immutable, dependency-light, and accepted by framework consumers.
