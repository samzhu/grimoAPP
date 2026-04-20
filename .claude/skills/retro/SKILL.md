---
name: retro
description: Evidence-based retrospective on the preceding conversation or task. Use when the user asks to review the workflow, says "what could be improved", "lessons learned", "post-mortem", "retrospective", "review this", "哪裡可以優化", "哪裡可以改進", "回顧一下", "檢討", or types /retro. Also use proactively when a task has obviously circled — direction changed 3+ times or the user had to correct the assistant more than twice. Produces a reusable trigger-action checklist, not apologies or vague reflections. Works for any task type: coding, research, design, writing, planning.
---

# Retrospective Protocol

Produce an evidence-grounded retrospective that yields a reusable artifact, not a feel-good reflection. Execute all seven steps in order. No step may be skipped or merged.

## Banned phrases (violations require restart)

- Banned openings: "好的我來反思", "感謝指正", "您說得對", "抱歉", "Thanks for pointing this out", "You're right", "Great question", "Let me reflect"
- Banned filler: "考慮不周", "經驗不足", "下次會注意", "可以更仔細", "insufficient context", "would have been better to", "I should have"
- Banned self-credit: "we figured it out together", "with your help" — user interventions are autonomy gaps, not collaboration credits

## Step 1 — Turning Points Ledger

List every point where approach, design, or direction materially changed in the preceding work. For each, tag the trigger source:

- (A) User-triggered — user provided a link, correction, keyword, or contradicting fact
- (B) Self-discovered — found without user prompting

Output the A:B ratio as a headline number.

## Step 2 — 5 Whys with Evidence

For each (A) turning point, run 5 Whys. Each Why answer must cite a concrete artifact:

- A specific file that was not read (with path or name)
- A specific keyword that was not searched (with exact string)
- A specific tool that was not invoked (with tool name)
- A specific assumption that was not verified (with where/how to verify)

If a Why cannot be grounded in a concrete artifact, it is wrong — retry.

## Step 3 — Root Cause (single selection)

Pick exactly one primary root cause. Multi-select is not allowed.

a) Started proposing solutions before inventorying existing resources or constraints
b) Treated already-decided constraints as open options for re-discussion
c) Shallow research — names and summaries only, no source / docs / examples
d) Scope too wide — too many unknowns at once, no narrowing
e) Ignored information the user already provided earlier in the conversation
f) Other (must be a concrete, non-abstract description)

## Step 4 — Counterfactual

Answer honestly: "If the user had not intervened at all, where would I have stopped, with what wrong answer, and what downstream damage would it have caused?"

Grounding rule: the counterfactual must be consistent with the A:B ratio from Step 1. If A was high, do not claim "I would have figured it out eventually".

## Step 5 — Trigger-Action Checklist (main deliverable)

Format each item exactly:

"When {specific trigger}, before {specific phase}, must {specific action with tool / keyword / path / exit criterion}."

Requirements:

- Each {trigger} must be recognizable from future conversation patterns, not abstract intent
- Each {action} must specify: which tool, what keyword or path, what condition proves completion
- Banned action verbs: "consider", "think about", "be careful", "pay attention to", "keep in mind"
- Minimum 3 items, maximum 7

## Step 6 — Persist the Artifact

Tell the user exactly where to save the checklist so it auto-triggers next time:

- Reusable skill: `.claude/skills/{name}/SKILL.md` or `~/.claude/skills/{name}/SKILL.md`
- Slash command: `~/.claude/commands/{name}.md`
- Project memory: `CLAUDE.md` (only if fewer than 5 items)

Include the exact path and the phrase or command that will invoke it.

## Step 7 — One-Line Verdict

Close with exactly one sentence:

"The single most expensive mistake this session was {X}, costing roughly {N} rounds of conversation; checklist saved at {path}, auto-triggers on {phrase}."

No closing emoji, no "hope this helps", no "let me know if you need anything else".

## Examples

**Example A — research task that circled**
User had to provide 4 links before the assistant discovered an existing library. Step 1 gives A:B = 4:1. Step 3 selects (a). Step 5 produces e.g. "When asked to design a component on top of an existing framework, before proposing any architecture, must list the framework's already-provided primitives by searching the framework-community org on GitHub and reading top 3 README files."

**Example B — coding task with repeated failures**
Step 1 gives A:B = 2:3. Step 3 selects (c). Step 5 produces e.g. "When writing code against a library, before first implementation, must read the library's public API via source or generated docs, not summary blogs, with exit criterion 'can name three public classes and their constructors'."

## Output budget

Steps 1-4 combined should fit in one screen. Step 5 is the deliverable — put the most effort there. Steps 6-7 are one-liners.