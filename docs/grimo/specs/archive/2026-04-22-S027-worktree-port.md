# S027: Git Worktree Port

> Spec: S027 | Size: S(9) | Status: ✅ Done
> Date: 2026-04-22

---

## 1. Goal

為 subagent 任務執行提供 git worktree 生命週期管理。每個開發任務取得獨立的 worktree（`~/.grimo/worktrees/<taskId>/`），掛載到 Docker 容器的 `/work`，subagent 在其中工作後產生 diff。

Hybrid 方案：native `git` CLI（ProcessBuilder）負責 worktree 建立/刪除/列表；JGit 7.6.0 `FileRepository` 負責已建立 worktree 內的 diff/commit/status。此模式為 JGit 官方測試套件（`LinkedWorktreeTest.java`）採用的方式。

依賴：僅 `core`（`GrimoHomePaths`）+ JGit。為 S028 的前置依賴。取代 Backlog「工作樹管理員」(v2 S009)。

## 2. Approach

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| A: Hybrid — ProcessBuilder + JGit | yes | JGit 7.6.0 無 `git worktree add/remove/list` API（Eclipse Bug 477475，2015 至今未解）。native git 是建立/刪除的唯一路徑。JGit 可開啟已建立的 linked worktree 並提供 typed diff/commit/status API。 |
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
- architecture.md §2: JGit `7.6.0.202603022253-r` pinned [Implementation note: 從 7.1.1 升至 7.6.0]; `WorktreePort` planned as subagent outbound port
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
| `build.gradle.kts` | modify | 新增 `implementation("org.eclipse.jgit:org.eclipse.jgit:7.6.0.202603022253-r")` [Implementation note: 升級至最新穩定版] |
| `subagent/package-info.java` | modify | 更新 Javadoc 標記 S027 為第一個具體型別 |
| `subagent/domain/WorktreeInfo.java` | new | Git worktree 資訊 value object |
| `subagent/domain/WorktreeException.java` | new | Worktree 操作例外 |
| `subagent/application/port/out/WorktreePort.java` | new | Outbound port — worktree 生命週期 |
| `subagent/adapter/out/ProcessBuilderWorktreeAdapter.java` | new | Hybrid 實作（ProcessBuilder + JGit FileRepository） |
| `test/.../ProcessBuilderWorktreeAdapterTest.java` | new | T0 unit test — 需真實 git repo（`git init` in @TempDir），不需 Docker |

## 6. Task Plan

### POC Decision

**POC: embedded in T1。** JGit 7.6.0 為本專案首次使用，但 hybrid pattern 直接來自 JGit 官方 `LinkedWorktreeTest.java` 測試套件。風險低，POC 驗證嵌入 Task 1（AC-1 + AC-2 驗證 FileRepository 可正確開啟 linked worktree）。若 T1 失敗，停止回報並評估。

### Task Table

| Task | AC | 描述 | 依賴 | 估算 |
|------|------|------|------|------|
| T1 | AC-1, AC-2 | 基礎設施 + worktree create + JGit open | none | 主要工作量 — 新增依賴、domain types、port interface、adapter 骨架 |
| T2 | AC-3, AC-4, AC-5 | diff + remove + list | T1 | 在已建立的 adapter 上擴充三個操作 |
| T3 | AC-6 | git CLI 快速失敗 | T1 | 建構子中加入 git 存在性檢查 |

### Execution Order

```
T1 (infra + create + openJGit) → T2 (diff + remove + list)
                                → T3 (git CLI fast-fail)
```

T2 和 T3 彼此獨立，但都依賴 T1 的 adapter 骨架。

### AC Coverage Verification

| AC | Task | 測試方法 |
|------|------|------|
| AC-1 worktree create | T1 | ProcessBuilderWorktreeAdapterTest#ac1_* |
| AC-2 JGit open | T1 | ProcessBuilderWorktreeAdapterTest#ac2_* |
| AC-3 diff | T2 | ProcessBuilderWorktreeAdapterTest#ac3_* |
| AC-4 remove | T2 | ProcessBuilderWorktreeAdapterTest#ac4_* |
| AC-5 list | T2 | ProcessBuilderWorktreeAdapterTest#ac5_* |
| AC-6 git CLI not found | T3 | ProcessBuilderWorktreeAdapterTest#ac6_* |

## 7. Implementation Results

### Verification Results

| Command | Result |
|---------|--------|
| `./gradlew test` | PASS — 6/6 S027 tests green, 0 regressions |
| `./gradlew compileTestJava` | PASS |
| `./gradlew jacocoTestCoverageVerification` | PASS (≥ 80%) |

E2E not required — no integration seams identified. S027 is pure ProcessBuilder + JGit, tested with real git binary in `@TempDir`.

### Key Findings

1. **JGit version upgrade.** Architecture.md 原 pin 7.1.1.202506271520-r（不存在於 Maven Central）。實際可用版本為 `7.1.1.202505221757-r`。使用者決定升級至最新穩定版 `7.6.0.202603022253-r`。architecture.md 已同步更新。

2. **openJGit metadata dir 解析。** `FileRepository` 需要傳入 `.git/worktrees/<name>/` metadata 目錄。Git 建立 worktree 時，metadata 目錄名稱可能與 branch 名稱不同（`/` 被替換）。實作透過掃描 metadata 目錄下的 `gitdir` 檔案，比對 checkout path 來找到正確的 metadata dir。

3. **diff 需要 staged files。** `git diff HEAD` 只顯示已追蹤（staged）或已修改的檔案差異。新增的 untracked 檔案不會出現在 diff 中。S028 的 subagent 在 worktree 中工作時，Claude Code YOLO 模式會自動 `git add`，因此 `diff HEAD` 是正確的語意。

4. **git CLI fast-fail via 建構子注入。** Development-standards §7.3 禁止 mock CLI subprocess。AC-6 的測試策略：package-private 建構子接受 `String gitCommand` 參數，測試傳入不存在的路徑。預設建構子使用 `"git"`。建構子呼叫 `git --version` 驗證可用性。

### Correct Usage Patterns

```java
// 建立 worktree
WorktreeInfo info = worktreePort.create("task123", projectWorkDir, "main");
// → ~/.grimo/worktrees/task123/ 建立，branch grimo/task-task123

// 開啟 JGit 進行 typed 操作
try (Git git = worktreePort.openJGit(info)) {
    Status status = git.status().call();
    // git.add().addFilepattern(".").call();
    // git.commit().setMessage("...").call();
}

// 取得 diff
String diff = worktreePort.diff(info);  // git diff HEAD

// 列出所有 worktrees
List<WorktreeInfo> list = worktreePort.list(projectWorkDir);

// 刪除 worktree
worktreePort.remove(info);  // git worktree remove --force
```

### AC Results

| AC | Status | Test Method |
|----|--------|-------------|
| AC-1 worktree create | ✅ | `ac1_createBuildsValidWorktreeWithCorrectBranch` |
| AC-2 JGit open | ✅ | `ac2_openJGitReturnsWorkingGitObjectForWorktree` |
| AC-3 diff | ✅ | `ac3_diffReturnsUnifiedDiffContainingChangedFile` |
| AC-4 remove | ✅ | `ac4_removeDeletesWorktreeDirectoryAndCleansGitWorktrees` |
| AC-5 list | ✅ | `ac5_listReturnsAllWorktreesWithCorrectInfo` |
| AC-6 git CLI fast-fail | ✅ | `ac6_constructorThrowsWhenGitCliNotFound` |

### Pending Verification

無。所有測試在本機可直接執行（僅需 git CLI，不需 Docker 或外部服務）。

### Design Sync

- §2 Approach 中 JGit 7.1.1 → 7.6.0，已在 §2 行內標註 `[Implementation note]`。
- §4 Interface — 實作與設計一致，新增 `gitCommand` 建構子參數（測試用，未改變公開 API）。
- §5 File Plan — JGit 版本已更新。

---

## QA Review（2026-04-22 · /verifying-quality 獨立審查）

### Verification Evidence

| 命令 | 結果 | 細節 |
|------|------|------|
| `./gradlew compileTestJava` | PASS | 無編譯錯誤 |
| `./gradlew test` | PASS | 6/6 S027 tests green（ProcessBuilderWorktreeAdapterTest），0 regressions |
| `./gradlew jacocoTestCoverageVerification` | PASS | 整體覆蓋率 ≥ 80%（gate passed） |

測試 XML 報告：`build/test-results/test/TEST-io.github.samzhu.grimo.subagent.adapter.out.ProcessBuilderWorktreeAdapterTest.xml`
```
tests="6" skipped="0" failures="0" errors="0" time="0.899"
- S027 AC-1: create() builds valid worktree with correct branch
- S027 AC-2: openJGit() returns working Git object for worktree
- S027 AC-3: diff() returns unified diff containing changed file
- S027 AC-4: remove() deletes worktree directory and cleans .git/worktrees/
- S027 AC-5: list() returns all worktrees with correct info
- S027 AC-6: constructor throws WorktreeException when git CLI not found
```

### Testability Gate

| AC | 分類 | 驗證方式 |
|----|------|----------|
| AC-1 worktree create | VERIFIED | 自動化測試 — real git in @TempDir |
| AC-2 JGit open | VERIFIED | 自動化測試 — FileRepository + status check |
| AC-3 diff | VERIFIED | 自動化測試 — git add + diff HEAD |
| AC-4 remove | VERIFIED | 自動化測試 — 目錄不存在 + metadata 清理 |
| AC-5 list | VERIFIED | 自動化測試 — 2 worktrees, exact names |
| AC-6 git CLI fast-fail | VERIFIED | 自動化測試 — package-private constructor 注入非法路徑 |

**Testability gate: CLEAR** — 所有 6 個 AC 均有對應的自動化測試執行通過。

### Code Quality Findings

**IMPORTANT（已修正）**

1. **WorktreePort.java Javadoc 版本錯誤**：class-level Javadoc 寫 "JGit 7.1.1 lacks these APIs"，但 `build.gradle.kts` 和 `ProcessBuilderWorktreeAdapter` Javadoc 均正確寫 7.6.0。已修正為 7.6.0。

2. **WorktreePort.remove() Javadoc 誤稱刪除 branch**：原始 Javadoc 寫 "Removes the worktree **and its branch**"，但 `git worktree remove --force` 不刪除 branch，實作亦無 `git branch -d` 呼叫，AC-4 測試也不驗證 branch 刪除。Javadoc 已修正為明確說明 branch 不被刪除，由呼叫者自行決定。

**MINOR（已修正）**

3. **ProcessBuilderWorktreeAdapterTest 缺少 @AfterEach 清除 System property**：`@BeforeEach` 設定 `System.setProperty("grimo.home", ...)` 但無對應 `@AfterEach clearProperty()`。`GrimoHomePathsTest` 有正確的清除模式。已新增 `@AfterEach void clearSystemProperty()` 與標準保持一致，防止測試結束後屬性指向已刪除的 tempDir。

### Standards Compliance

- 命名慣例：PASS（`WorktreePort`、`ProcessBuilderWorktreeAdapter`、`WorktreeException`、`WorktreeInfo` 均符合 §3）
- Given/When/Then 結構：PASS — 所有 6 個測試均有完整的 // Given / // When / // Then / // And 區塊（§7.9）
- 無 Mockito mock CLI subprocess（§7.3、§11）：PASS
- 無 System.out/System.err（§3）：PASS（production code）
- 無靜態可變狀態（§11）：PASS（僅 `static final` 常數）
- 無 null 回傳（§3）：PASS
- 無 TODO/FIXME（§10）：PASS
- Logger 使用 SLF4J（§3）：PASS

### Design Drift Check

- §2 Approach：正確描述 hybrid 設計及 JGit Bug 477475，JGit 版本 `[Implementation note]` 已標記。
- §4 Interface：`list()` Javadoc 說「Scans .git/worktrees/ or parses git worktree list --porcelain」，實作只使用 `git worktree list --porcelain`（未直接掃描目錄）。文件已精確，實作為 porcelain 解析路徑，正確。
- `WorktreePort` 的 `remove()` branch 行為現已與實作一致。
- architecture.md JGit 版本：已更新為 7.6.0（由實作者在 §7 Key Finding #1 中記錄並同步）。

### Four-Layer Verdict Table

| Layer | Result | Detail |
|-------|--------|--------|
| Automated tests | PASS | 6/6 S027 tests green; 0 regressions; `./gradlew test` exit 0 |
| Coverage / Integration | PASS | `jacocoTestCoverageVerification` exit 0; aggregate ≥ 80% |
| Manual verification | N/A | No interactive or UI behavior in this spec |
| Testability gate | CLEAR | All 6 ACs have VERIFIED automated tests |

### Verdict

**PASS**

S027 所有驗收標準均有對應測試並執行通過。三個 Javadoc 問題（IMPORTANT × 2，MINOR × 1）已在本次 QA 審查中直接修正並重新驗證（tests still pass after fixes）。S027 可出貨。
