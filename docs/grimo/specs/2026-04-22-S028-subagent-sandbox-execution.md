# S028: Subagent Sandbox Execution

> Spec: S028 | Size: M(13) | Status: ⏳ Design
> Date: 2026-04-22

---

## 1. Goal

實作 Docker-sandboxed subagent 執行管線。透過 REST API 派送任務 → 自動建立 git worktree → 啟動 Docker 容器（掛載 worktree 至 `/work`）→ 投射技能 → Claude Code YOLO 模式執行 → 收集 diff 與回應 → 回傳結果。支援訂閱帳號（OAuth token）和 API key 兩種認證方式。

本 spec 是 subagent 模組的第一個完整實作，串接既有的 S003（Sandbox SPI）、S005（CLI adapter）、S012/S016（Skill projection）、S018（Task/Project CRUD）。

取代：S020（Task Execution Pipeline）+ Backlog「委派協議」(v2 S008)。依賴：S027（WorktreePort）。

## 2. Approach

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| A: Sandbox.exec() 直接執行 claude CLI | yes | 最直接：`sandbox.exec("claude", "-p", prompt, "--dangerously-skip-permissions")` 在已掛載 worktree 的容器內執行。利用既有 `BindMountSandbox`，不需修改 CLI 模組。回傳 `ExecResult` 含完整 stdout/stderr。 |
| B: ContainerizedAgentModelFactory → AgentModel.call() | no | 需修改 Factory 支援 YOLO 旗標和長超時。AgentModel 封裝了 NDJSON streaming 協定，但 subagent 只需要一次性執行結果。過度抽象。 |
| C: agent-client AgentSession 多輪互動 | no | 需要 stdin/stdout pipe 進容器。`BindMountSandbox` 不支援 `startInteractive()`（throws UnsupportedOperationException）。架構不合。 |

### 2.1 Key Design Decisions

**D1 — Sandbox.exec() 而非 AgentModel**：Subagent 任務是「一次性執行」——送出 prompt，Claude Code 自主運作（讀檔、寫碼、跑測試），完成後回傳。`Sandbox.exec()` 完美匹配此模式。`AgentModel.call()` 封裝了 SDK 串流協定，增加複雜度但無額外價值。

**D2 — 環境變數與 CLAUDE.md**：Subagent 與 main agent 的 env vars 不同：

| Env var | Main agent (S006) | Subagent (S028) | 理由 |
|---------|-------------------|-----------------|------|
| `CLAUDE_CODE_OAUTH_TOKEN` | ✅ | ✅ | 認證 |
| `CLAUDE_CODE_DISABLE_CLAUDE_MDS` | `1` | **不設** | subagent 應讀 worktree 內的 CLAUDE.md（含專案脈絡） |
| `CLAUDE_CODE_DISABLE_AUTO_MEMORY` | `1` | `1` | 防止 subagent 修改 memory 檔案 |
| `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC` | `1` | `1` | 關閉遙測 |

**D3 — Task Execution 獨立表格**：新增 `grimo_task_execution` 表，而非擴充 `grimo_task`。理由：
1. 非所有 task 都有執行（研究、文件類不走此管線）
2. 同一 task 可重新執行（失敗後重試）
3. 執行生命週期（PENDING → RUNNING → SUCCEEDED/FAILED）與 task 生命週期（OPEN → IN_PROGRESS → ...）是兩個獨立狀態機
4. 關注點分離：task 模組管 CRUD，subagent 模組管執行

**D4 — 非同步執行**：`POST /api/tasks/{taskNumber}/execute` 立即回傳 `202 Accepted` + execution ID。執行在 background virtual thread 進行。Client 透過 `GET /api/tasks/{taskNumber}/execution/{executionId}` 輪詢狀態。JDK 25 `Executors.newVirtualThreadPerTaskExecutor()` 取代傳統 thread pool。

**D5 — Skill 投射策略**：

```
Git worktree (from project repo)
  └── .claude/skills/        ← 專案級 skills（含 references/），git 追蹤，自動在 worktree 內
  └── (+ managed skills)     ← SkillProjectionUseCase.projectToWorkDir(worktreePath)
                                從 ~/.grimo/skills/ 投射 SKILL.md
```

專案級 skills（如 `/planning-spec`）因為在 git repo 內，worktree 建立時自動包含完整目錄（含 `references/`）。Managed skills 透過既有 `SkillProjectionUseCase` 投射。**不需新增 skill injection 程式碼。**

**D6 — Claude CLI 命令**：

```bash
claude -p "<task prompt>" \
    --dangerously-skip-permissions \
    --max-turns 100 \
    --output-format json
```

`-p`（print mode）送出一次性 prompt。`--max-turns 100` 允許 agent 自主使用工具最多 100 輪。`--output-format json` 結構化輸出。`--dangerously-skip-permissions` 全權限（安全由 Docker sandbox 保證）。

**D7 — 認證優先序**：`CLAUDE_CODE_OAUTH_TOKEN`（訂閱帳號）> `ANTHROPIC_API_KEY`（API key）。從 Spring Boot 配置讀取：

```yaml
grimo:
  subagent:
    oauth-token: ${CLAUDE_CODE_OAUTH_TOKEN:}
    api-key: ${ANTHROPIC_API_KEY:}
    image: grimo-runtime:0.0.1-SNAPSHOT
    max-turns: 100
    timeout: 30m
```

### 2.3 Research Citations

- S003 §7: `BindMountSandbox.exec(ExecSpec)` uses `container.execInContainer()` with env var injection via `bash -c "export K='V'; ..."`
- S005 §7: `WrapperScriptGenerator` generates `docker exec -i` scripts; subagent bypasses this layer, using `Sandbox.exec()` directly
- S006 §7 Finding 1: `CLAUDE_CODE_OAUTH_TOKEN` expects bare accessToken, not full Keychain JSON
- S006 §2: `--bare` explicitly rejected — kills OAuth auth + skill loading
- S016 §7: `SkillProjectionUseCase.projectToWorkDir()` copies `SKILL.md` from `~/.grimo/skills/` to `<workDir>/.claude/skills/<name>/`
- deepwiki `claude-sdk-design-decisions.md`: subscription OAuth risk assessment — CLI-style reportedly re-allowed; API key is zero risk
- architecture.md §6.3: subagent data flow `DelegateTaskUseCase → WorktreePort → Sandbox → exec → diff → review`
- architecture.md §9: planned container security posture `--read-only`, `--cap-drop=ALL`, `--no-new-privileges` (deferred, noted as S010 TODO)

## 3. SBE Acceptance Criteria

Run: `./gradlew test`
Pass: all tests carrying S028 AC ids are green.

Integration tests（需 Docker + Claude CLI）：`./gradlew integrationTest`（CI 自動跳過）。

**AC-1: 任務派送 → 非同步執行**
Given 一個 OPEN 狀態的 task（#42）屬於 project "myapp"（workDir = `/tmp/myapp`）
When  `POST /api/tasks/42/execute { "prompt": "Add hello.txt with 'Hello World'" }`
Then  回傳 `202 Accepted` + `TaskExecution { id, status: RUNNING, ... }`
And   task #42 狀態自動轉為 `IN_PROGRESS`
And   `~/.grimo/worktrees/<taskId>/` 存在
And   Docker 容器已啟動，worktree 掛載在 `/work`

**AC-2: Claude Code YOLO 模式執行**
Given AC-1 觸發的執行
When  容器內 Claude Code 執行完成
Then  worktree 內有新增/修改的檔案
And   `TaskExecution.agentResponse` 包含 Claude 的回應 JSON
And   `TaskExecution.diff` 包含 unified diff

**AC-3: 執行完成 → task 狀態更新**
Given AC-2 執行成功
When  execution status 變為 `SUCCEEDED`
Then  task #42 狀態自動轉為 `IN_REVIEW`
And   Docker 容器已關閉
And   worktree 保留（等待 S029 diff review）

**AC-4: 執行失敗處理**
Given 一個會導致 Claude Code 失敗的 prompt（如容器映像不存在）
When  執行發生錯誤
Then  `TaskExecution.status` = `FAILED`
And   `TaskExecution.errorMessage` 包含錯誤描述
And   task 狀態轉為 `OPEN`（可重試）
And   Docker 容器已清理

**AC-5: Skill 可用性**
Given project repo 包含 `.claude/skills/planning-spec/SKILL.md` + `references/`
When  subagent 在 worktree 內啟動
Then  Claude Code 可發現並載入 `/planning-spec` skill
And   `references/spec-template.md` 等檔案在 worktree 內可讀

**AC-6: 訂閱帳號認證**
Given `grimo.subagent.oauth-token` 設定為有效的 OAuth token
When  subagent 在容器內執行
Then  Claude Code 成功使用訂閱帳號認證（非 API key）
And   容器環境變數 `CLAUDE_CODE_OAUTH_TOKEN` 已設定

**AC-7: API key 認證（備用）**
Given `grimo.subagent.oauth-token` 未設定
And   `grimo.subagent.api-key` 設定為有效的 API key
When  subagent 在容器內執行
Then  Claude Code 成功使用 API key 認證
And   容器環境變數 `ANTHROPIC_API_KEY` 已設定

**AC-8: 執行狀態查詢**
Given AC-1 觸發的執行
When  `GET /api/tasks/42/execution/<executionId>`
Then  回傳 `TaskExecution` 含當前 status、diff、agentResponse

**AC-9: Modulith 驗證通過**
Given subagent 模組新增跨模組依賴
When  `./gradlew test`（含 `ModuleArchitectureTest`）
Then  Spring Modulith verify 通過

## 4. Interface / API Design

### Domain Types

```java
// === subagent/domain/TaskExecution.java ===
public record TaskExecution(
    String id,                          // NanoIds.compact()
    String taskId,                      // FK to grimo_task.id
    int taskNumber,                     // for REST URL
    ExecutionStatus status,             // PENDING → RUNNING → SUCCEEDED | FAILED
    String prompt,                      // 派送給 subagent 的 prompt
    @Nullable String branch,            // grimo/task-<taskId>
    @Nullable String worktreePath,      // ~/.grimo/worktrees/<taskId>/
    @Nullable String containerId,       // Docker container ID
    @Nullable String agentResponse,     // Claude 回應 JSON
    @Nullable String diff,              // unified diff
    @Nullable String errorMessage,      // 失敗原因
    @Nullable Instant startedAt,
    @Nullable Instant finishedAt,
    Instant createdAt
) {}

// === subagent/domain/ExecutionStatus.java ===
public enum ExecutionStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED;
    public boolean isTerminal() { return this == SUCCEEDED || this == FAILED; }
}
```

### Inbound Port

```java
// === subagent/application/port/in/DelegateTaskUseCase.java ===
public interface DelegateTaskUseCase {
    /** 非同步派送任務至 subagent。立即回傳 PENDING 狀態的 TaskExecution。 */
    TaskExecution execute(int taskNumber, String prompt);

    /** 查詢執行狀態。 */
    Optional<TaskExecution> findExecution(String executionId);

    /** 查詢某 task 的所有執行記錄。 */
    List<TaskExecution> listExecutions(int taskNumber);
}
```

### Outbound Ports

```java
// === subagent/application/port/out/TaskExecutionPort.java ===
public interface TaskExecutionPort {
    void save(TaskExecution execution);
    Optional<TaskExecution> findById(String id);
    List<TaskExecution> findByTaskId(String taskId);
}
```

### REST API

```
POST   /api/tasks/{taskNumber}/execute
  Body: { "prompt": "..." }
  Response: 202 Accepted + TaskExecution

GET    /api/tasks/{taskNumber}/executions
  Response: 200 OK + List<TaskExecution>

GET    /api/tasks/{taskNumber}/executions/{executionId}
  Response: 200 OK + TaskExecution | 404
```

### Orchestration Flow

```
POST /api/tasks/42/execute { prompt }
  │
  ▼
SubagentExecutorService.execute(taskNumber=42, prompt)
  │
  ├─ 1. task = taskUseCase.findByNumber(42)         → validate OPEN
  ├─ 2. project = projectUseCase.findById(task.projectId)
  ├─ 3. execution = TaskExecution(PENDING, prompt, ...)
  ├─ 4. taskExecutionPort.save(execution)
  ├─ 5. taskUseCase.updateStatus(42, IN_PROGRESS)
  ├─ 6. return execution (client sees 202)           → ★ 以下在 virtual thread 背景執行
  │
  ▼ (background)
  ├─ 7.  worktreeInfo = worktreePort.create(task.id, projectWorkDir, "main")
  ├─ 8.  skillProjection.projectToWorkDir(worktreeInfo.checkoutPath)
  ├─ 9.  sandbox = sandboxManager.create(SandboxConfig(
  │          image, worktreeInfo.checkoutPath, "/work"))
  ├─ 10. execution = execution.withRunning(containerId, branch, worktreePath)
  ├─ 11. envVars = buildEnvVars(oauthToken, apiKey)
  ├─ 12. result = sandbox.exec(ExecSpec.of(
  │          "claude", "-p", prompt,
  │          "--dangerously-skip-permissions",
  │          "--max-turns", maxTurns,
  │          "--output-format", "json")
  │        .env(envVars))
  ├─ 13. diff = worktreePort.diff(worktreeInfo)
  ├─ 14. execution = execution.withSucceeded(result.stdout, diff)
  ├─ 15. taskExecutionPort.save(execution)
  ├─ 16. taskUseCase.updateStatus(42, IN_REVIEW)
  └─ 17. sandboxManager.close(containerId)

  on error → execution.withFailed(error), task → OPEN, cleanup sandbox
```

### Database Schema

```sql
CREATE TABLE IF NOT EXISTS grimo_task_execution (
    id                VARCHAR(12)   PRIMARY KEY,
    task_id           VARCHAR(12)   NOT NULL,
    task_number       INT           NOT NULL,
    execution_status  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    prompt            CLOB          NOT NULL,
    branch            VARCHAR(200),
    worktree_path     VARCHAR(500),
    container_id      VARCHAR(100),
    agent_response    CLOB,
    diff_summary      CLOB,
    error_message     VARCHAR(2000),
    started_at        TIMESTAMP,
    finished_at       TIMESTAMP,
    created_at        TIMESTAMP     NOT NULL,
    FOREIGN KEY (task_id) REFERENCES grimo_task(id)
);

CREATE INDEX IF NOT EXISTS idx_execution_task
    ON grimo_task_execution(task_id, execution_status);
```

### Configuration

```yaml
grimo:
  subagent:
    oauth-token: ${CLAUDE_CODE_OAUTH_TOKEN:}
    api-key: ${ANTHROPIC_API_KEY:}
    image: grimo-runtime:0.0.1-SNAPSHOT
    max-turns: 100
    timeout: 30m
```

```java
@ConfigurationProperties(prefix = "grimo.subagent")
public record SubagentProperties(
    @Nullable String oauthToken,
    @Nullable String apiKey,
    String image,
    int maxTurns,
    Duration timeout
) {}
```

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `subagent/package-info.java` | modify | `allowedDependencies = { "sandbox::api", "task::api", "project::api", "skills::api" }` |
| `subagent/domain/TaskExecution.java` | new | 執行記錄 domain record |
| `subagent/domain/ExecutionStatus.java` | new | 執行狀態 enum |
| `subagent/application/port/in/DelegateTaskUseCase.java` | new | Inbound port — 任務派送 |
| `subagent/application/port/in/package-info.java` | new | `@NamedInterface("api")` |
| `subagent/application/port/out/TaskExecutionPort.java` | new | Outbound port — 執行持久化 |
| `subagent/application/service/SubagentExecutorService.java` | new | Orchestrator — 串接 worktree/sandbox/CLI/skill |
| `subagent/adapter/in/web/SubagentRestController.java` | new | REST API — execute / query |
| `subagent/adapter/out/JdbcTaskExecutionAdapter.java` | new | JDBC 實作 — grimo_task_execution |
| `subagent/internal/SubagentModuleConfig.java` | new | `@Configuration` + `SubagentProperties` |
| `src/main/resources/schema.sql` | modify | 新增 `grimo_task_execution` 表 |
| `src/main/resources/application.yaml` | modify | 新增 `grimo.subagent.*` 配置 |
| `test/.../SubagentExecutorServiceTest.java` | new | T0 unit test — mock ports |
| `test/.../SubagentRestControllerTest.java` | new | T2 `@WebMvcTest` — mock DelegateTaskUseCase |
| `test/.../JdbcTaskExecutionAdapterTest.java` | new | T0 — 真實 H2 + schema.sql |
