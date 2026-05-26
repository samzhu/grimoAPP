# Grimo Frontend POC

This folder is a React + Vite proof of concept for implementing the OpenDesign Grimo workbench UI with assistant-ui React primitives.

## Scope

- Focus: frontend feasibility for the Grimo task workbench, task detail, project view, workflow view, and task-forming chat.
- Backend: intentionally stubbed. The POC uses assistant-ui `LocalRuntime` with a local `ChatModelAdapter`.
- Not included: `@assistant-ui/react-ai-sdk`, Assistant Cloud, provider SDKs, authentication, persistence, or Spring Boot API integration.

## Research Notes

- assistant-ui docs position the library as React UI for AI chat with components, runtimes, and primitives.
- The relevant assistant-ui pieces for this POC are `ThreadPrimitive`, `ComposerPrimitive`, `MessagePrimitive`, `AssistantRuntimeProvider`, and `useLocalRuntime` from `@assistant-ui/react`.
- The Spring Boot backend can later replace the local model adapter with a REST or SSE call while keeping the UI component structure.
- React official guidance used here: split UI into components, render arrays from data, keep local state close to the interaction, and use JSX/CSS through the build tool.

## Design Basis

- Source design: `../docs/grimo/ui/prototype/index.html`
- Visual tokens were ported from the prototype: `--bg`, `--surface`, `--fg`, `--muted`, `--border`, `--accent`, radius, mono font, and compact dashboard spacing.
- The layout keeps the OpenDesign surfaces: Task board, Task detail, Blockers, Projects, Chat, and Workflow.

## Commands

```bash
npm install
npm run dev -- --port 5173
npm run build
```

## Current Validation

- `npm run build` passes.
- Chrome renders `http://127.0.0.1:5173/`.
- The Chat view renders assistant-ui primitives inside the Grimo workbench layout.
