# S004: `grimo-runtime` Docker 映像

> Spec: S004 | Size: S (10) | Status: ✅ Done
> Date: 2026-04-17

---

## 1. Goal

產生一個 Docker 映像 `grimo-runtime:<version>`，預裝三個 CLI 代理工具（claude-code、codex、gemini），供下游 S003 的 `BindMountSandbox` 與 S005 的 `AgentCliAdapter` 作為容器化 CLI 執行環境使用。

**範圍內：**
- `docker/runtime/Dockerfile`（基於 `node:20-slim`，安裝 3 CLI + git）
- `docker/runtime/README.md`（映像內容、版本、建置指令）
- 整合測試驗證映像內 CLI 可用性

**範圍外：**
- Gradle 建置整合（使用者直接執行 `docker build`）
- CLI 認證/憑證掛載 → S006
- 容器安全加固（`--read-only`、`--cap-drop`） → S010
- `AgentCliAdapter`（docker exec 呼叫 CLI）→ S005
- Sandbox bind-mount 機制 → S003

## 2. Approach

### 2.1 Decisions

| # | Decision | Chosen | Why |
| --- | --- | --- | --- |
| D1 | **基礎映像** | `node:20-slim`（Debian/glibc） | 三個 CLI 均基於 Node.js（codex >= 16、gemini >= 20、claude-code npm 需 >= 18）。Claude-code 原生二進位依賴 glibc — Alpine/musl 在 v2.1.63+ 硬 crash（`posix_getdents: symbol not found`）。來源：[#29559](https://github.com/anthropics/claude-code/issues/29559) |
| D2 | **claude-code 安裝** | `curl -fsSL https://claude.ai/install.sh \| bash`（原生安裝器） | npm 套件 `@anthropic-ai/claude-code` 在 v2.1.15 正式 deprecated，且實際損壞（缺 `sdk.mjs` entry point）。原生安裝器是唯一支援路徑。來源：[#20402](https://github.com/anthropics/claude-code/issues/20402)、[#10191](https://github.com/anthropics/claude-code/issues/10191)、[官方安裝文件](https://code.claude.com/docs/en/setup) |
| D3 | **codex 安裝** | `npm i -g @openai/codex@<pinned>` | Node.js 套件（非 Python）。`engines: { node: ">=16" }`。來源：[GitHub](https://github.com/openai/codex)、[package.json](https://raw.githubusercontent.com/openai/codex/main/codex-cli/package.json) |
| D4 | **gemini 安裝** | `npm i -g @google/gemini-cli@<pinned>` | Node.js 套件（非 Google Cloud SDK）。`engines: { node: ">=20.0.0" }`。來源：[GitHub](https://github.com/google-gemini/gemini-cli)、[package.json](https://raw.githubusercontent.com/google-gemini/gemini-cli/main/package.json) |
| D5 | **版本固定策略** | codex/gemini 精確固定版本號；claude-code 由 curl 安裝器決定（記錄建置日期版本） | 三個 CLI 都處於 0.x 快速迭代期，semver 保證薄弱。精確固定確保可重現建置。curl 安裝器目前不支援指定版本，以建置日期作為版本錨點。 |
| D6 | **建置方式** | 直接 `docker build` 指令 | 不引入 Gradle Docker plugin。映像建置與 Java 建置無關（Dockerfile 自足，不需要 Java 原始碼）。指令記錄在 README。 |
| D7 | **建置上下文** | `docker/runtime/`（自足目錄） | 映像只需 Dockerfile 本身，不需要 repo 其他檔案。最小上下文 = 最快建置。 |
| D8 | **額外系統工具** | `git`（apt） | 子代理在容器內操作 worktree 需要 git。`curl` 用於 claude-code 安裝器。 |

### 2.2 Challenges Considered

- **「為何不用上游 `agents-runtime`？」** `ghcr.io/spring-ai-community/agents-runtime:latest`（[agent-client/Dockerfile.agents-runtime](https://github.com/spring-ai-community/agent-client)）已有 claude-code + gemini，但：(a) 缺少 codex CLI；(b) 基於 `eclipse-temurin:17-jdk-jammy`，含不需要的 JDK 17 / Maven 3.9 / Python 3，膨脹映像；(c) CLI 版本用 `@latest` 不固定，每週重建漂移；(d) 依賴外部 GHCR 可用性。自建映像更精簡、版本更可控。
- **「為何不用 Alpine？」** Claude Code 原生二進位自 v2.1.63 起依賴 glibc `posix_getdents` symbol，Alpine 的 musl libc 不提供此 symbol，導致硬 crash。[#29559](https://github.com/anthropics/claude-code/issues/29559)。官方 Alpine 文件也不完整（[#18966](https://github.com/anthropics/claude-code/issues/18966)）。
- **「為何 claude-code 不用 npm？」** npm 套件在 v2.1.15 deprecated 後，`claude doctor` 標記為不支援，且 `sdk.mjs` entry point 缺失導致功能損壞。上游自己的 devcontainer 也被迫遷移至 curl 安裝器（[#20402](https://github.com/anthropics/claude-code/issues/20402)）。
- **「Docker 容器內的 OAuth 認證？」** 容器重啟後 OAuth 不持久（[#22066](https://github.com/anthropics/claude-code/issues/22066)）。S006 將處理認證策略（API key 環境變數注入）。S004 不烘焙認證。
- **「claude-code 版本不可固定？」** curl 安裝器目前只安裝 latest。記錄建置時版本於 README。未來若安裝器支援版本參數，更新 Dockerfile。

### 2.3 Research Citations (load-bearing)

**Claude Code CLI 安裝：**
- npm deprecated at v2.1.15，devcontainer 遷移：[#20402](https://github.com/anthropics/claude-code/issues/20402)
- npm 缺 `sdk.mjs` entry point：[#10191](https://github.com/anthropics/claude-code/issues/10191)
- Alpine/musl crash `posix_getdents`：[#29559](https://github.com/anthropics/claude-code/issues/29559)
- Alpine 官方文件不完整：[#18966](https://github.com/anthropics/claude-code/issues/18966)
- OAuth 不持久於容器：[#22066](https://github.com/anthropics/claude-code/issues/22066)
- 官方安裝文件：[code.claude.com/docs/en/setup](https://code.claude.com/docs/en/setup)

**Codex CLI：**
- npm 套件 `@openai/codex`，Node.js >= 16：[GitHub](https://github.com/openai/codex)

**Gemini CLI：**
- npm 套件 `@google/gemini-cli`，Node.js >= 20：[GitHub](https://github.com/google-gemini/gemini-cli)

**上游 `agents-runtime` 映像：**
- Dockerfile 位於 `agent-client` repo：[GitHub](https://github.com/spring-ai-community/agent-client)
- 含 claude-code + gemini（`@latest`），缺 codex，基於 `eclipse-temurin:17-jdk-jammy`

**藍圖描述修正：**
- 原描述「Node.js（claude-code）、Python（codex）、Google Cloud SDK（gemini）」不正確
- 三者均為 Node.js / 原生二進位工具，不需要 Python 或 gcloud SDK

## 3. SBE Acceptance Criteria

**Acceptance-verification command:**

```
docker build --tag grimo-runtime:0.0.1-SNAPSHOT docker/runtime/
./gradlew integrationTest
```

Pass condition: `docker build` 成功標記映像；所有 `@DisplayName` 以 `[S004] AC-<N>` 開頭的整合測試為綠色。整合測試標記 `@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")`。

### AC-1: docker build 成功並標記映像

```
Given  Docker Daemon 運行中
When   執行 docker build --tag grimo-runtime:0.0.1-SNAPSHOT docker/runtime/
Then   指令以 exit code 0 結束
And    docker images grimo-runtime 列出標記 0.0.1-SNAPSHOT 的映像
```

### AC-2: claude-code CLI 可用

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
When   docker run --rm grimo-runtime:0.0.1-SNAPSHOT claude --version
Then   stdout 印出版本號（格式 x.y.z）
And    exit code 為 0
```

### AC-3: codex 與 gemini CLI 可用

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
When   docker run --rm grimo-runtime:0.0.1-SNAPSHOT codex --version
And    docker run --rm grimo-runtime:0.0.1-SNAPSHOT gemini --version
Then   兩者均印出版本號且 exit code 為 0
```

### AC-4: 映像大小 < 1 GB（軟性目標）

```
Given  grimo-runtime:0.0.1-SNAPSHOT 映像已建置
When   docker image inspect grimo-runtime:0.0.1-SNAPSHOT --format '{{.Size}}'
Then   大小 < 1,073,741,824 bytes (1 GB)
Note   此為軟性目標——超過時記錄於 README 但不阻礙驗收
```

## 4. Interface / API Design

### 4.1 Dockerfile 結構（概要）

```dockerfile
FROM node:20-slim

# 系統依賴
RUN apt-get update && apt-get install -y --no-install-recommends \
        git curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Codex CLI（固定版本）
RUN npm install -g @openai/codex@<pinned-version>

# Gemini CLI（固定版本）
RUN npm install -g @google/gemini-cli@<pinned-version>

# Claude Code（原生安裝器 — 安裝至 ~/.local/bin/claude）
RUN curl -fsSL https://claude.ai/install.sh | bash
ENV PATH="/root/.local/bin:${PATH}"

# 工作目錄（S003 bind-mount 掛載點）
WORKDIR /work

# 長期存活容器（S003 用 sleep infinity 保持容器）
CMD ["sleep", "infinity"]
```

> 實作時精確版本號填入 Dockerfile，並同步記錄於 README。

### 4.2 建置指令

```bash
docker build --tag grimo-runtime:0.0.1-SNAPSHOT docker/runtime/
```

### 4.3 與下游規格的銜接

| 下游規格 | 如何使用 `grimo-runtime` |
| --- | --- |
| S003 | `SandboxConfig("grimo-runtime:0.0.1-SNAPSHOT", hostDir, "/work")` — 替換測試用的 `alpine:3.21` |
| S005 | `AgentCliAdapter` 透過 `docker exec -i <container> claude ...` 呼叫容器內 CLI |
| S006 | 認證策略：透過環境變數（`-e ANTHROPIC_API_KEY=...`）或唯讀掛載主機憑證目錄注入 |

## 5. File Plan

### New files

| File | Description |
| --- | --- |
| `docker/runtime/Dockerfile` | 主要交付物 — `node:20-slim` + claude-code + codex + gemini + git |
| `docker/runtime/README.md` | 映像內容文件：CLI 版本、建置指令、大小紀錄、與 `agents-runtime` 的差異說明 |

### New test files

| File | Description |
| --- | --- |
| `src/test/java/io/github/samzhu/grimo/sandbox/internal/RuntimeImageIT.java` | T3 整合測試：AC-2、AC-3、AC-4（CLI 可用性 + 映像大小）；`@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")` |

### Modified files

| File | Action | Description |
| --- | --- | --- |
| `docs/grimo/specs/spec-roadmap.md` | modify | S004 status → `⏳ Design`；修正描述（移除 Python / gcloud SDK） |

### Not touched

- `build.gradle.kts` — 不新增 Gradle task（直接用 docker 指令）
- `src/main/java/` — S004 無 Java 生產程式碼
- `core/` — 無新型別
- `application.yml` — 無新屬性
- `docker/runtime/.dockerignore` — 建置上下文為 `docker/runtime/` 本身，內容極少，不需要 ignore 檔

---

## 6. Task Plan

**POC: not required** — Dockerfile 使用標準 `docker build` + `npm install` + `curl` 安裝器，皆為成熟工具鏈，專案中無新 Java 套件或 SDK 需要事先驗證。

### 任務索引

| Task | 主題 | AC 對映 | 依賴 |
| --- | --- | --- | --- |
| T1 | 建立 Dockerfile（node:20-slim + 3 CLI + git） | AC-1 | — |
| T2 | 建立 README.md（版本、建置指令、大小紀錄） | — (文件) | T1 |
| T3 | 撰寫 RuntimeImageIT 整合測試 | AC-2, AC-3, AC-4 | T1 |

### 執行順序

```
T1 → T2（可與 T3 平行）
T1 → T3（可與 T2 平行）
```

### AC 覆蓋矩陣

| AC | 驗證方式 | Task |
| --- | --- | --- |
| AC-1 | `docker build` exit code 0 + `docker images` 列出標記 | T1（建置時驗證） |
| AC-2 | `@DisplayName("[S004] AC-2")` IT：`claude --version` | T3 |
| AC-3 | `@DisplayName("[S004] AC-3")` IT：`codex --version` + `gemini --version` | T3 |
| AC-4 | `@DisplayName("[S004] AC-4")` IT：映像大小 < 1 GB | T3 |

---

## 7. Implementation Results

**驗證日期：** 2026-04-17

### 驗證結果

| 命令 | 結果 |
| --- | --- |
| `./gradlew check` | ✅ exit 0（compile + unit tests + Modulith verify） |
| `./gradlew integrationTest -Dgrimo.it.docker=true` | ✅ exit 0（4 個 S004 IT + 2 個 S003 IT 全數通過） |
| `docker build --tag grimo-runtime:0.0.1-SNAPSHOT docker/runtime/` | ✅ exit 0 |

### 關鍵發現

1. **Claude Code 安裝器耗時較長**：curl 安裝器在 Docker build 中需約 220 秒（下載原生二進位），為建置瓶頸。codex + gemini 的 npm install 各約 20-30 秒。
2. **版本不可固定**：claude-code curl 安裝器不支援指定版本，建置時安裝 v2.1.112。已記錄於 README。
3. **build.gradle.kts 需修改**：spec §5 原列 `build.gradle.kts` 為 "Not touched"，但 T3 需新增 `integrationTest` task（含 `testClassesDirs`/`classpath` 設定、`grimo.*` 系統屬性傳遞）以及 `test` task 的 `exclude("**/*IT.class")`。此為專案基礎設施，非 S004 特有。

### 正確使用模式（供下游規格參考）

**Dockerfile 安裝 claude-code（唯一支援路徑）：**
```dockerfile
RUN curl -fsSL https://claude.ai/install.sh | bash
ENV PATH="/root/.local/bin:${PATH}"
```

**Dockerfile 安裝 npm CLI（精確固定版本）：**
```dockerfile
RUN npm install -g @openai/codex@0.121.0
RUN npm install -g @google/gemini-cli@0.38.1
```

**integrationTest Gradle task（Kotlin DSL）：**
```kotlin
tasks.register<Test>("integrationTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    include("**/*IT.class")
}
```

**IT 測試中的容器 CLI 驗證模式：**
```java
try (var container = new GenericContainer<>(IMAGE)
        .withCommand("sleep", "infinity")) {
    container.start();
    ExecResult result = container.execInContainer("claude", "--version");
    assertThat(result.getExitCode()).isZero();
    assertThat(result.getStdout().trim()).matches(".*\\d+\\.\\d+\\.\\d+.*");
}
```

### AC 結果表

| AC | 結果 | 驗證方式 |
| --- | --- | --- |
| AC-1 | ✅ | `docker build` exit 0，`docker images` 列出 `grimo-runtime:0.0.1-SNAPSHOT` |
| AC-2 | ✅ | `RuntimeImageIT.claudeCodeCliAvailable()` — `claude --version` → v2.1.112 |
| AC-3 | ✅ | `RuntimeImageIT.codexCliAvailable()` + `geminiCliAvailable()` — 版本號正確 |
| AC-4 | ✅ | `RuntimeImageIT.imageSizeUnderOneGb()` — 957 MB < 1 GB |

### 交付物清單

| 檔案 | 類型 |
| --- | --- |
| `docker/runtime/Dockerfile` | new |
| `docker/runtime/README.md` | new |
| `src/test/java/io/github/samzhu/grimo/sandbox/internal/RuntimeImageIT.java` | new |
| `build.gradle.kts` | modified（integrationTest task + test exclude IT） |
