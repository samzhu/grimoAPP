# Grimo Context

Grimo 的領域語言用來區分使用者看見的任務狀態、AI 自動工作的內部步驟，以及人類審查點。

## Language

**AI Review**:
Agent 完成主要工作後，先自行檢查成果是否足以交給人類審查的內部工作步驟。
_Avoid_: Human Review, Review board state

**Human Review**:
人類根據 Review Materials 決定 approve 或 reject 的審查點。
_Avoid_: AI Review, internal Quality Loop review

**Human Review State**:
Task State Machine 中等待人類 approve 或 reject 的外層狀態。
_Avoid_: AI reviewer still running, internal Quality Loop review

## Relationships

- **AI Review** happens before **Human Review**.
- **Human Review** is the user-facing decision point inside **Human Review State**.
- **AI Review** is still agent work; **Human Review State** starts only after Review Materials are ready.

## Example dialogue

> **Dev:** "Can we show one Review step for both AI and human review?"
> **Domain expert:** "No. **AI Review** is still agent work, while **Human Review** is where the user approves or rejects the result."

## Flagged ambiguities

- "Review" was used for both **AI Review** and **Human Review**; resolved: recipe previews should name them separately.
