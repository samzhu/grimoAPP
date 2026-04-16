# Grimo Glossary / 詞彙表

Bilingual reference for every load-bearing domain term used in Grimo docs and
code. Add new terms here the first time they appear in a PRD, spec, or ADR.

> Style rule: **Code uses the English term verbatim** (class/package/prop
> names). Prose may use either, but in zh-TW prose prefer the Chinese term
> with the English in parentheses on first use.

## Core domain

| English (code) | 中文 (zh-TW) | Definition |
| --- | --- | --- |
| Grimo | Grimo | The product itself. Pronounced "grim-oh" — from *grimoire*, a book of procedures. A self-evolving **user harness** over CLI AI agents. |
| GrimoHome | Grimo 家目錄 | The persistent state directory, default `~/.grimo`. Contains `memory/`, `skills/`, `sessions/`, `worktrees/`, `logs/`, `config/`. |
| Harness | 外殼 / 挽具 | The set of controls, contracts, and state that wrap a model to form an agent. `Agent = Model + Harness`. Grimo is a **user harness**, not a framework. |
| User Harness | 使用者外殼 | A harness built *around* off-the-shelf agents (Claude Code, Codex, Gemini CLI) by the team using them — contrast with **builder harness** baked into the agent itself. |
| Main Agent | 主代理 | The conversational entry point. Reads-only — cannot Edit / Write / Bash-write. Runs outside Docker on the host. Configurable CLI (`claude` / `codex` / `gemini`). |
| Sub Agent | 子代理 | An isolated executor spawned for a delegated task. Runs **inside Docker** with a git worktree bind-mounted. Has full Read/Write/Bash/Edit (YOLO) inside the sandbox. |
| Session | 對話會話 | Grimo's event-sourced dialogue state, owned by Grimo (not by the underlying CLI). Built on Spring AI `SessionMemoryAdvisor`. Survives CLI switches. |
| CLI Switch | CLI 切換 | The operation of changing the main-agent's underlying CLI mid-session. Grimo compacts prior `Message` events and replays them as bootstrap to the new CLI. |
| Skill | 技能 | A reusable procedure on disk, format `SKILL.md` with YAML frontmatter. Discoverable by all CLI adapters via a unified registry. |
| Skill Distillation | 技能蒸餾 | The closed-loop process: after N successful runs of a recurring pattern, Grimo proposes a draft `SKILL.md`; user approves and commits. |
| Memory | 記憶 | Durable facts, curated by the agent itself via `AutoMemoryTools`. Lives in `~/.grimo/memory/` with `MEMORY.md` as the index. |
| Guide | 指導 | Feedforward control: prompts, tool allowlists, persona overlays applied **before** a turn runs. |
| Sensor | 感測器 | Feedback control: linters, tests, AI reviewers applied **after** a turn. |
| Jury | 陪審團 | The multi-perspective review pattern: dispatch one task to N CLIs in parallel, aggregate into consensus + divergence. |
| Router | 路由器 | The cost-and-complexity-aware component that picks which CLI/model runs a given turn. |
| Worktree | 工作樹 | A git worktree checked out into a per-task directory under `~/.grimo/worktrees/<task-id>/`. Bind-mounted into the sub-agent container. |
| Sandbox | 沙箱 | The Docker container in which a sub-agent runs. Backed by Spring AI `DockerSandbox` with a worktree bind-mount. |
| Contract | 契約 | The declared input/output/stopping-condition shape of a task — a harness primitive. |
| Control | 控制 | How work is decomposed, scheduled, and guarded — a harness primitive. |
| State | 狀態 | What persists across steps, branches, and sub-agents — a harness primitive (sessions, memory, skills, worktrees). |
| Approval Fatigue | 核准疲勞 | The UX failure mode where constant Y/N confirmations (per file edit, per shell command) push users to rubber-stamp or disable safety checks. Grimo answers it by moving approval to a single end-of-task diff inside a sandboxed worktree. |
| Subscription-Native Auth | 訂閱帳號原生認證 | Grimo authenticates by invoking the user's already-logged-in CLI binary, so a Claude Max / ChatGPT Plus / Gemini Advanced subscription is sufficient — no API key required. (See PRD D19 / P10.) |
| Vendor Lock-in Resilience | 跨 agent 韌性 | The property that any single provider outage, quota hit, or price change degrades Grimo to "run on the other CLIs", not to "down". (See PRD P9.) |

## Stack

| English (code) | 中文 (zh-TW) | Definition |
| --- | --- | --- |
| AgentClient | 代理客戶端 | Spring AI Community abstraction (`org.springaicommunity.agents`) that wraps CLI agents behind a unified builder API. Version pinned: 0.12.2. |
| AgentSession | 代理會話 | Low-level per-CLI session object from agent-client. **Currently Claude-only** — Grimo does *not* rely on this for CLI-portable sessions. |
| SessionMemoryAdvisor | 會話記憶顧問 | Event-sourced session advisor from Spring AI Session API (2026-04-15). Grimo's canonical session store. |
| AutoMemoryTools | 自動記憶工具 | File-backed long-term memory tools from `spring-ai-agent-utils`. |
| DockerSandbox | Docker 沙箱 | Testcontainers-backed sandbox backend from Spring AI `agent-sandbox`. |
| Spring Modulith | Spring 模組 | Module-boundary + event-publication library. Version pinned: 2.0.5. Each bounded context = one `@ApplicationModule`. |
| Application Module | 應用模組 | A top-level package under the Boot main class, guarded by `@ApplicationModule`. Externally exposed types are "named interfaces". |

## Command surface (planned)

| Command | 中文 | Effect |
| --- | --- | --- |
| `/grimo switch <cli>` | 切換 CLI | Swap main-agent CLI, replay compacted history into the new CLI. |
| `/grimo delegate <task>` | 委派任務 | Spawn a sub-agent in Docker with a fresh worktree. |
| `/grimo jury <task> --n=3` | 陪審團審查 | Dispatch to N CLIs in parallel, aggregate review. |
| `/grimo skills` | 列出技能 | List all discovered skills (built-in + `~/.grimo/skills/`). |
| `/grimo memory` | 記憶管理 | Inspect / prune `~/.grimo/memory/`. |
| `/grimo cost` | 成本報表 | Show this-session token/cost telemetry per CLI. |
