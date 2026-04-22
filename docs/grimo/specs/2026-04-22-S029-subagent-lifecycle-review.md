# S029: Subagent Lifecycle Events + Diff Review

> Spec: S029 | Size: S(10) | Status: ⏳ Design
> Date: 2026-04-22

---

## 1. Goal

為 S028 的 subagent 執行管線補全可觀測性和審核流程。發布領域事件（`SubagentStarted`/`Completed`/`Failed`）、將 subagent 對話記錄到 session event store、提供 diff 審核 API（approve → merge / reject → cancel）、PR 自動化（commit → push → gh pr create）、以及 worktree 清理。

這是 subagent 從「執行完成」到「程式碼合併」的最後一哩路。取代 Backlog「子代理生命週期 + diff 審查」(v2 S010)。

依賴：S028（SubagentExecutorService）、S017/S023（Session event store）。

## 2. Approach

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| A: 事件驅動 + REST 審核 | yes | 領域事件透過 Spring Modulith `@ApplicationModuleListener` 解耦模組。Diff 審核透過 REST API（approve/reject）讓 client 控制。PR 自動化用 ProcessBuilder 呼叫 native git + `gh` CLI。 |
| B: 同步管線（S028 直接呼叫所有步驟） | no | S028 的 executor 會變得過大。事件/審核/PR 是獨立關注點，應在獨立 listener 和 API 中處理。 |

### 2.1 Key Design Decisions

**D1 — 領域事件**：三個事件定義在 `subagent/domain/events/`，透過 `@NamedInterface("events")` 發布。

```
SubagentStarted   → 觸發時機：sandbox 建立完成，開始執行 Claude CLI
SubagentCompleted  → 觸發時機：execution status = SUCCEEDED
SubagentFailed     → 觸發時機：execution status = FAILED
```

消費者（跨模組）：
- `session` 模組：監聽 `SubagentCompleted`，將 prompt + response 寫入 session event store
- Future `notification` 模組：監聽所有三個事件

**D2 — Session 記錄**：Subagent 的 prompt + response 記錄為一對 USER + ASSISTANT `SessionEvent`。Session 綁定到 task 所屬的 project（`session_type = PROJECT`）。使用既有 `SessionEventPort.append()`。

```
SubagentCompleted event
  → SessionRecordingListener.on(event)
    → sessionEventPort.append(sessionId, userEvent(prompt))
    → sessionEventPort.append(sessionId, assistantEvent(response))
```

**D3 — Diff 審核流程**：

```
SubagentExecutor completes → execution.status = SUCCEEDED, task.status = IN_REVIEW
  │
  ▼
User reviews diff via GET /api/tasks/{n}/executions/{id}
  │
  ├── POST /api/tasks/{n}/executions/{id}/approve
  │     → git add + commit + push + gh pr create
  │     → task.status = DONE
  │     → worktree cleanup
  │
  └── POST /api/tasks/{n}/executions/{id}/reject
        → task.status = OPEN (可重試)
        → worktree cleanup
```

**D4 — PR 自動化**：在 worktree 內執行 native git 命令：

```bash
git -C <worktreePath> add -A
git -C <worktreePath> commit -m "feat(<taskNumber>): <task title>"
git -C <worktreePath> push -u origin <branch>
gh pr create --title "<task title>" --body "<diff summary>" --head <branch>
```

`gh` CLI 為可選依賴——不存在時跳過 PR 建立，僅 commit + push。

**D5 — Worktree 清理**：approve 或 reject 後，呼叫 `worktreePort.remove(worktreeInfo)` 刪除 worktree 目錄。Branch 保留在 git history 中。

**D6 — `gh` CLI 可選性**：PR 建立是 best-effort。若 `gh` 不在 PATH 上或 `gh auth status` 失敗，log warn 並跳過。PR URL 寫入 execution record（若成功）。

### 2.3 Research Citations

- architecture.md §2: 跨模組通訊合法模式 C — `@NamedInterface("events")` + `@ApplicationModuleListener`
- architecture.md §6.3: subagent planned events — `SubagentStarted`, `SubagentCompleted`, `SubagentFailed`
- S017 §7: `SessionEventPort.append()` 支援 USER + ASSISTANT event 寫入
- S023 §7: `parent_event_id` adjacency list — subagent session events 可掛在正確的 parent 下
- development-standards §7: 不允許 mock CLI subprocess — `gh` CLI 測試用 skip strategy

## 3. SBE Acceptance Criteria

Run: `./gradlew test`
Pass: all tests carrying S029 AC ids are green.

**AC-1: SubagentStarted 事件**
Given S028 的 SubagentExecutorService 開始執行
When  sandbox 建立完成
Then  `SubagentStarted` 事件發布，含 executionId、taskId、timestamp

**AC-2: SubagentCompleted 事件**
Given subagent 執行成功
When  execution status 變為 SUCCEEDED
Then  `SubagentCompleted` 事件發布，含 executionId、taskId、diff、timestamp

**AC-3: SubagentFailed 事件**
Given subagent 執行失敗
When  execution status 變為 FAILED
Then  `SubagentFailed` 事件發布，含 executionId、taskId、errorMessage、timestamp

**AC-4: Session 記錄**
Given `SubagentCompleted` 事件發布
When  `SessionRecordingListener` 接收事件
Then  session event store 新增 USER event（prompt）+ ASSISTANT event（response）
And   events 綁定到 task 所屬 project 的 session

**AC-5: Diff Approve → commit + push + PR**
Given 一個 SUCCEEDED 的 execution
When  `POST /api/tasks/42/executions/<id>/approve`
Then  worktree 內的變更已 commit（message 含 task number + title）
And   branch 已 push 到 remote
And   response 包含 PR URL（若 `gh` 可用）
And   task #42 狀態轉為 `DONE`
And   worktree 已刪除

**AC-6: Diff Reject → 回到 OPEN**
Given 一個 SUCCEEDED 的 execution
When  `POST /api/tasks/42/executions/<id>/reject`
Then  task #42 狀態轉為 `OPEN`
And   worktree 已刪除
And   execution status 標記為 `REJECTED`

**AC-7: gh CLI 不存在時降級**
Given `gh` 不在 PATH 上
When  approve 流程執行
Then  commit + push 正常執行
And   PR 建立跳過（log warn）
And   response 的 prUrl 為 null

**AC-8: Modulith 事件驗證**
Given subagent 模組發布事件
When  `./gradlew test`
Then  Modulith verify 通過：事件定義在 `@NamedInterface("events")`，消費者用 `@ApplicationModuleListener`

## 4. Interface / API Design

### Domain Events

```java
// === subagent/domain/events/package-info.java ===
@NamedInterface("events")
package io.github.samzhu.grimo.subagent.domain.events;

// === subagent/domain/events/SubagentStarted.java ===
public record SubagentStarted(
    String executionId, String taskId,
    String containerId, Instant timestamp
) {}

// === subagent/domain/events/SubagentCompleted.java ===
public record SubagentCompleted(
    String executionId, String taskId,
    String prompt, String agentResponse,
    String diff, Instant timestamp
) {}

// === subagent/domain/events/SubagentFailed.java ===
public record SubagentFailed(
    String executionId, String taskId,
    String errorMessage, Instant timestamp
) {}
```

### Review Port (Inbound)

```java
// === subagent/application/port/in/ExecutionReviewUseCase.java ===
public interface ExecutionReviewUseCase {
    /** Approve: commit + push + PR + cleanup. */
    ReviewResult approve(String executionId, @Nullable String commitMessage);

    /** Reject: reset task + cleanup. */
    void reject(String executionId);
}

// === subagent/domain/ReviewResult.java ===
public record ReviewResult(
    String branch,
    String commitHash,
    @Nullable String prUrl,
    @Nullable Integer prNumber
) {}
```

### PR Port (Outbound)

```java
// === subagent/application/port/out/PullRequestPort.java ===
public interface PullRequestPort {
    /** git add + commit + push in the worktree. */
    String commitAndPush(WorktreeInfo info, String message);

    /** gh pr create. Returns PR URL or null if gh unavailable. */
    @Nullable String createPullRequest(String branch, String title, String body);

    /** Check if gh CLI is available and authenticated. */
    boolean isGhAvailable();
}
```

### REST API

```
POST   /api/tasks/{taskNumber}/executions/{executionId}/approve
  Body: { "commitMessage": "..." }  (optional)
  Response: 200 OK + ReviewResult

POST   /api/tasks/{taskNumber}/executions/{executionId}/reject
  Response: 204 No Content
```

### Session Recording Flow

```
SubagentCompleted event
  │
  ▼
SessionRecordingListener (@ApplicationModuleListener)
  │
  ├─ session = findOrCreateProjectSession(taskProjectId)
  ├─ userEvent = SessionEvent(USER, prompt, metadata={source:"subagent", executionId})
  ├─ assistantEvent = SessionEvent(ASSISTANT, agentResponse, metadata={...})
  └─ sessionEventPort.append(session.id, [userEvent, assistantEvent])
```

### ExecutionStatus 擴充

```java
public enum ExecutionStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED, REJECTED;
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == REJECTED;
    }
}
```

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `subagent/package-info.java` | modify | `allowedDependencies` 新增 `"session::api"` |
| `subagent/domain/events/package-info.java` | new | `@NamedInterface("events")` |
| `subagent/domain/events/SubagentStarted.java` | new | 執行開始事件 |
| `subagent/domain/events/SubagentCompleted.java` | new | 執行成功事件 |
| `subagent/domain/events/SubagentFailed.java` | new | 執行失敗事件 |
| `subagent/domain/ReviewResult.java` | new | 審核結果 value object |
| `subagent/domain/ExecutionStatus.java` | modify | 新增 `REJECTED` 狀態 |
| `subagent/application/port/in/ExecutionReviewUseCase.java` | new | Inbound port — diff 審核 |
| `subagent/application/port/out/PullRequestPort.java` | new | Outbound port — git + gh 操作 |
| `subagent/application/service/ExecutionReviewService.java` | new | 審核 orchestrator |
| `subagent/application/service/SubagentExecutorService.java` | modify | 注入 `ApplicationEventPublisher`，發布三個事件 |
| `subagent/adapter/in/web/SubagentRestController.java` | modify | 新增 approve/reject endpoints |
| `subagent/adapter/out/ProcessBuilderPullRequestAdapter.java` | new | git commit/push + gh pr create |
| `session/package-info.java` | modify | `allowedDependencies` 新增 `"subagent::events"` |
| `session/application/listener/SessionRecordingListener.java` | new | `@ApplicationModuleListener` — 記錄 subagent 對話 |
| `test/.../ExecutionReviewServiceTest.java` | new | T0 unit test — mock ports |
| `test/.../ProcessBuilderPullRequestAdapterTest.java` | new | T0 — 需真實 git repo |
| `test/.../SessionRecordingListenerTest.java` | new | T1 — `@ApplicationModuleTest` |
