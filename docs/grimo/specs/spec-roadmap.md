# Grimo Spec Roadmap

**Status:** Active planning index  
**Last updated:** 2026-06-01

## Milestone 1 — Project Context Before Task Work

PRD Critical Path: 使用者先建立或選擇 Project，Project 決定工作流和品質基準，之後才進入 Task Management Interface。

| Spec ID | 標題 | 點數 | 相依 | 狀態 |
| --- | --- | ---: | --- | --- |
| S001 | Project onboarding with workflow selection | 5 | PRD, ADR-001, architecture baseline | ✅ local verification PASS |
| S002 | Workflow recipe role defaults during Project creation | 5 | S001, PRD P3/P4, workflow reference | 🟡 BDD confirmation in progress |

## Backlog

| Spec ID | 標題 | 點數 | 相依 | 狀態 |
| --- | --- | ---: | --- | --- |
| S003 | Task creation through backend API | TBD | S001, S002 | backlog |
| S004 | Task-forming chat creates or advances defining Task | TBD | S001, S003 | backlog |
| S005 | Ready Gate and Dispatch Window UI/API | TBD | S001, S002, S003 | backlog |
| S006 | Review Materials and human approve/reject flow | TBD | S001, S002, S003 | backlog |
