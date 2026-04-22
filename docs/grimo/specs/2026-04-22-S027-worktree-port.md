# S027: Git Worktree Port

> Spec: S027 | Size: S(9) | Status: ⏳ Design
> Date: 2026-04-22

---

## 1. Goal

為 subagent 任務執行提供 git worktree 生命週期管理。每個開發任務取得獨立的 worktree（`~/.grimo/worktrees/<taskId>/`），掛載到 Docker 容器的 `/work`，subagent 在其中工作後產生 diff。

Hybrid 方案：native `git` CLI（ProcessBuilder）負責 worktree 建立/刪除/列表；JGit 7.1.1 `FileRepository` 負責已建立 worktree 內的 diff/commit/status。此模式為 JGit 官方測試套件（`LinkedWorktreeTest.java`）採用的方式。

依賴：僅 `core`（`GrimoHomePaths`）+ JGit。為 S028 的前置依賴。取代 Backlog「工作樹管理員」(v2 S009)。

## 2. Approach

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| A: Hybrid — ProcessBuilder + JGit | yes | JGit 7.1.1 無 `git worktree add/remove/list` API（Eclipse Bug 477475，2015 至今未解）。native git 是建立/刪除的唯一路徑。JGit 可開啟已建立的 linked worktree 並提供 typed diff/commit/status API。 |
| B: 純 ProcessBuilder | no | 可行但放棄 JGit 的 typed API，回歸字串解析。 |
| C: 純 JGit | no | 不可能 — JGit 無 worktree create/remove/list 命令。 |

### 2.1 Key Design Decisions

**D1 — Worktree 路徑**：`GrimoHomePaths.worktrees().resolve(taskId)`。每個 task 一個 worktree，用 taskId（12-char NanoId）做目錄名。避免路徑衝突。

**D2 — Branch 命名**：`grimo/task-<taskId>`。`grimo/` 前綴明確標識 Grimo 建立的分支；taskId 保證唯一。

**D3 — git CLI 必須存在**：Adapter 初始化時檢查 `git --version`，缺少時拋出 `WorktreeException`。Development-standards §7 不允許 mock CLI subprocess。

**D4 — JGit 開啟 worktree 的入口**：`new FileRepository(<projectGitDir>/worktrees/<name>/)` — 傳入 metadata 目錄（`.git/worktrees/<name>/`），`guessWorkTreeOrFail()` 自動解析 checkout 路徑。注意：git 會將 branch 名的 `/` 替換為其他字元做目錄名，需用 `git worktree list --porcelain` 解析取得正確的 metadata 目錄名。

**D5 — Port 位置**：放在 `subagent` 模組的 outbound port。目前 subagent 是唯一消費者；若未來 jury review 等功能需要，可提取為共用模組。

### 2.3 Research Citations

- [JGit Bug 477475 — git worktree support missing since 2015](https://bugs.eclipse.org/bugs/show_bug.cgi?id=477475): confirmed no worktree add/remove/list in JGit API
- [JGit LinkedWorktreeTest.java — ProcessBuilder + FileRepository hybrid pattern](https://github.com/eclipse-jgit/jgit/blob/master/org.eclipse.jgit.test/tst/org/eclipse/jgit/internal/storage/file/LinkedWorktreeTest.java): JGit's own test uses native git to create worktrees, then opens with FileRepository
- [JGit BaseRepositoryBuilder.guessWorkTreeOrFail()](https://github.com/eclipse-jgit/jgit/blob/master/org.eclipse.jgit/src/org/eclipse/jgit/lib/BaseRepositoryBuilder.java): resolves checkout path from `.git/worktrees/<name>/gitdir` file
- architecture.md §2: JGit `7.1.1.202506271520-r` pinned; `WorktreePort` planned as subagent outbound port
- `GrimoHomePaths.worktrees()` returns `~/.grimo/worktrees/`, creates dir on demand

## 3. SBE Acceptance Criteria

Run: `./gradlew test`
Pass: all tests carrying S027 AC ids are green.

**AC-1: worktree 建立**
Given 一個有至少一個 commit 的 git 專案
When  呼叫 `worktreePort.create("task123abc", projectWorkDir, "main")`
Then  `~/.grimo/worktrees/task123abc/` 存在且為有效 git worktree
And   branch `grimo/task-task123abc` 已建立
And   worktree 內容與 main branch 一致

**AC-2: worktree 內 JGit 操作**
Given AC-1 建立的 worktree
When  呼叫 `worktreePort.openJGit(worktreeInfo)` 取得 `Git` 物件
Then  `git.status().call()` 回傳乾淨狀態
And   worktree 路徑指向 `~/.grimo/worktrees/task123abc/`

**AC-3: worktree diff**
Given AC-1 的 worktree 且在其中新增檔案 `hello.txt`
When  呼叫 `worktreePort.diff(worktreeInfo)`
Then  回傳的 diff 字串包含 `hello.txt`

**AC-4: worktree 刪除**
Given AC-1 的 worktree
When  呼叫 `worktreePort.remove(worktreeInfo)`
Then  `~/.grimo/worktrees/task123abc/` 目錄不存在
And   main repo 的 `.git/worktrees/` 已清理

**AC-5: worktree 列表**
Given 同一專案有 2 個 worktree
When  呼叫 `worktreePort.list(projectWorkDir)`
Then  回傳 2 個 `WorktreeInfo`，各含正確的 checkoutPath 和 branchName

**AC-6: git CLI 不存在時的快速失敗**
Given 系統 PATH 上找不到 `git`
When  建構 Adapter
Then  拋出 `WorktreeException` 含訊息 "git CLI not found"

## 4. Interface / API Design

```java
// === subagent/domain/WorktreeInfo.java ===
public record WorktreeInfo(
    String taskId,          // e.g. "k7m3p2q9r4s1"
    String branchName,      // e.g. "grimo/task-k7m3p2q9r4s1"
    Path checkoutPath,      // e.g. ~/.grimo/worktrees/k7m3p2q9r4s1/
    Path projectGitDir      // e.g. /Users/sam/project/.git/
) {}

// === subagent/domain/WorktreeException.java ===
public class WorktreeException extends RuntimeException {
    public WorktreeException(String message) { super(message); }
    public WorktreeException(String message, Throwable cause) { super(message, cause); }
}

// === subagent/application/port/out/WorktreePort.java ===
public interface WorktreePort {
    /** git worktree add -b grimo/task-<taskId> <path> <baseBranch> */
    WorktreeInfo create(String taskId, Path projectWorkDir, String baseBranch);

    /** Opens the linked worktree as a JGit Git instance for typed operations. */
    Git openJGit(WorktreeInfo info);

    /** git -C <checkoutPath> diff HEAD — returns unified diff string. */
    String diff(WorktreeInfo info);

    /** git worktree remove --force <checkoutPath> */
    void remove(WorktreeInfo info);

    /** Scans .git/worktrees/ or parses git worktree list --porcelain. */
    List<WorktreeInfo> list(Path projectWorkDir);
}
```

### create 流程

```
taskId = "k7m3p2q9r4s1"
worktreePath = GrimoHomePaths.worktrees().resolve(taskId)
branchName = "grimo/task-" + taskId
projectGitDir = resolveGitDir(projectWorkDir)

ProcessBuilder: git -C <projectWorkDir> worktree add
    -b <branchName> <worktreePath> <baseBranch>

return WorktreeInfo(taskId, branchName, worktreePath, projectGitDir)
```

### openJGit 流程

```
// 用 git worktree list --porcelain 取得正確的 metadata 目錄名
metadataDir = parseWorktreeMetadataDir(projectGitDir, checkoutPath)
return new Git(new FileRepository(metadataDir.toFile()))
```

### diff 流程

```
ProcessBuilder: git -C <checkoutPath> diff HEAD
return stdout
```

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `build.gradle.kts` | modify | 新增 `implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.1.202506271520-r")` |
| `subagent/package-info.java` | modify | 更新 Javadoc 標記 S027 為第一個具體型別 |
| `subagent/domain/WorktreeInfo.java` | new | Git worktree 資訊 value object |
| `subagent/domain/WorktreeException.java` | new | Worktree 操作例外 |
| `subagent/application/port/out/WorktreePort.java` | new | Outbound port — worktree 生命週期 |
| `subagent/adapter/out/ProcessBuilderWorktreeAdapter.java` | new | Hybrid 實作（ProcessBuilder + JGit FileRepository） |
| `test/.../ProcessBuilderWorktreeAdapterTest.java` | new | T0 unit test — 需真實 git repo（`git init` in @TempDir），不需 Docker |
