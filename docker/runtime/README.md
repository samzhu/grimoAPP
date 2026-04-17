# grimo-runtime Docker 映像

容器化 CLI 代理執行環境，預裝三個 AI 程式碼代理 CLI，供 Grimo 的沙箱容器使用。

## 映像內容

| 元件 | 版本 | 安裝方式 |
| --- | --- | --- |
| **基礎映像** | `node:20-slim`（Debian bookworm / glibc） | — |
| **Claude Code** | 2.1.112（建置日期：2026-04-17） | `curl -fsSL https://claude.ai/install.sh \| bash`（原生安裝器） |
| **Codex CLI** | 0.121.0 | `npm install -g @openai/codex@0.121.0` |
| **Gemini CLI** | 0.38.1 | `npm install -g @google/gemini-cli@0.38.1` |
| **git** | 2.39.x（Debian apt） | `apt-get install git` |

### 版本固定策略

- **Codex / Gemini**：精確固定版本號於 Dockerfile，確保可重現建置。
- **Claude Code**：curl 安裝器目前不支援指定版本，安裝 latest。每次建置後更新上方表格中的版本號。

## 建置指令

```bash
docker build --tag grimo-runtime:0.0.1-SNAPSHOT docker/runtime/
```

建置上下文為 `docker/runtime/` 目錄本身（自足，不需要 repo 其他檔案）。

## 映像大小

| 建置日期 | 大小 | 備注 |
| --- | --- | --- |
| 2026-04-17 | 957 MB | 初始建置（node:20-slim + 3 CLI + git） |

軟性目標 < 1 GB。超過時記錄於此但不阻礙驗收。

## 與上游 `agents-runtime` 的差異

自建映像而非使用 `ghcr.io/spring-ai-community/agents-runtime:latest`，原因：

| 比較項目 | `agents-runtime` | `grimo-runtime` |
| --- | --- | --- |
| 基礎映像 | `eclipse-temurin:17-jdk-jammy`（含 JDK 17 + Maven 3.9 + Python 3） | `node:20-slim`（僅 Node.js 執行環境） |
| CLI 涵蓋 | claude-code + gemini（缺少 codex） | claude-code + codex + gemini |
| 版本策略 | `@latest`（每次重建漂移） | codex/gemini 精確固定；claude-code 記錄建置日期版本 |
| 外部依賴 | 依賴 GHCR 可用性 | 本地建置，自足 |
| 映像大小 | 較大（含不需要的 JDK/Maven/Python） | 較小（僅需要的工具） |

來源：[agent-client repo](https://github.com/spring-ai-community/agent-client)

## 設計決策參考

- 為何不用 Alpine：Claude Code 原生二進位依賴 glibc `posix_getdents` symbol，Alpine 的 musl 不提供 → [#29559](https://github.com/anthropics/claude-code/issues/29559)
- 為何不用 npm 安裝 claude-code：npm 套件 v2.1.15 deprecated → [#20402](https://github.com/anthropics/claude-code/issues/20402)
- OAuth 不持久於容器：認證由 S006 處理 → [#22066](https://github.com/anthropics/claude-code/issues/22066)

## 下游使用

| 規格 | 使用方式 |
| --- | --- |
| S003 | `SandboxConfig("grimo-runtime:0.0.1-SNAPSHOT", hostDir, "/work")` |
| S005 | `docker exec -i <container> claude ...` |
| S006 | 認證透過 `-e ANTHROPIC_API_KEY=...` 環境變數注入 |
