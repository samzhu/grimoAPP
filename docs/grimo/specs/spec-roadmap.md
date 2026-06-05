# Grimo Spec Roadmap

**Status:** Active planning index  
**Last updated:** 2026-06-03

## Milestone 1 — Project Context Before Task Work

PRD Critical Path: 使用者先建立或選擇 Project，Project 決定工作流和品質基準，之後才進入 Task Management Interface。

| Spec ID | 標題 | 點數 | 相依 | 狀態 |
| --- | --- | ---: | --- | --- |
| S001 | Project onboarding with workflow selection | 5 | PRD, ADR-001, architecture baseline | ✅ local verification PASS |
| S004 | Task creation through backend API | M(14) | S001, S002, S003 | ⏳ Plan |
| S009 | Workflow evidence schema and summary projection | M(13) | S001, S002, S004 | ⏳ Plan |

## ✅ Shipped

| Spec ID | 標題 | 點數 | 版本 |
| --- | --- | ---: | --- |
| S002 | Workflow recipe role defaults | M(14) | — |
| S003 | Project management list and simple projectPath contract | M(13) | — |

## Backlog

| Spec ID | 標題 | 點數 | 相依 | 狀態 |
| --- | --- | ---: | --- | --- |
| S005 | Task-forming chat creates or advances defining Task | TBD | S001, S004, S009 | backlog |
| S006 | Ready Gate and Dispatch Window UI/API | TBD | S001, S002, S003, S004, S009 | backlog |
| S007 | Review Materials and human approve/reject flow | TBD | S001, S002, S003, S004, S009 | backlog |
| S008 | QA generality and mutation testing strategy | TBD | qa-strategy, development-standards, verifying-quality skill | backlog |
