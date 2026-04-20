# S016: MVP Manual Verification Gate

> Spec: S016 | Size: XS (7) | Status: ✅ Done
> Date: 2026-04-20

---

## 1. Goal

為 M5 出貨的三大 MVP 能力（對話、Session 恢復、Skill 登錄檔）加上**人工可驗證的 CLI 入口**，讓開發者能端對端確認功能運作。

**交付兩件事：**

1. **Skill CLI 子命令** — `grimo skill list/enable/disable`，暴露 S012 的 `SkillRegistryUseCase` 至終端。
2. **Skill 投影** — `grimo chat` 啟動 Claude Code 前，將已啟用 Skill 從 `~/.grimo/skills/` 複製至 `<workdir>/.claude/skills/`，使 Claude Code 能原生載入並使用。

**驗證矩陣（人工操作）：**

| 能力 | 已有基礎 | 本 spec 新增 |
|------|---------|-------------|
| 對話 | S007 `grimo chat` | — |
| Session 恢復 | S011 `grimo chat --resume` | — |
| Skill 管理 | S012 Java API | CLI 子命令（`grimo skill list/enable/disable`） |
| Skill 使用 | — | 投影至 `.claude/skills/` + 人工驗證 |

**依賴。** S007 ✅、S012 ✅。無程式碼層級阻塞。

**不包含。** Web UI（PRD D11 明確延後）。Skill 注入至 Docker 容器（S013）。Skill 複雜度評估（S015）。

### 1.1 Web vs CLI 評估

| 方案 | 評估 | 結論 |
|------|------|------|
| **CLI 子命令** | 零新依賴、與 PRD D11「CLI 直通」一致、現有 `ChatCommandRunner` 模式可複用 | ✅ 採用 |
| Web UI | 需引入 Spring MVC + Thymeleaf + HTMX + SSE，D11 明確延後至 Backlog | ❌ 延後 |

CLI 優先的理由：MVP 階段的驗證對象是開發者自己。Web UI 的價值在展示和多人協作，非當前需求。六邊形架構保證日後加入 Web adapter 時不影響核心邏輯。

---

## 2. Approach

### 2.1 Skill CLI 子命令

新增 `SkillCommandRunner implements ApplicationRunner`（`skills/adapter/in/cli/`），解析 `skill` 子命令：

```
java -jar grimo.jar skill list              → 列出所有 skill 及狀態
java -jar grimo.jar skill enable <name>     → 啟用
java -jar grimo.jar skill disable <name>    → 停用
```

遵循 `ChatCommandRunner` 既有模式：檢查 `args.getNonOptionArgs().contains("skill")`，不匹配則 return。

**輸出格式：**

```
Skills (2 found):
  greet       enabled   A greeting skill for MVP verification
  refactor    disabled  Code refactoring patterns
```

### 2.2 Skill 投影

新增 `SkillProjectionUseCase`（`skills/application/port/in/`）：

```java
public interface SkillProjectionUseCase {
    void projectToWorkDir(Path workDir);
}
```

實作邏輯（`SkillProjectionService`）：
1. 呼叫 `SkillRegistryUseCase.listEnabled()` 取得已啟用 Skill
2. 每個 Skill：複製 `Path.of(skill.basePath())/SKILL.md` → `workDir/.claude/skills/<name>/SKILL.md`
3. 使用 `Files.copy(..., REPLACE_EXISTING)` 覆寫已存在的同名 skill
4. 任何單檔失敗 → log.warn + 跳過（不中斷啟動）

**呼叫時機：** `ChatCommandRunner.run()` 中，在 `chatUseCase.startChat()` 之前呼叫 `skillProjection.projectToWorkDir(workDir)`。`--resume` 路徑也同樣投影。

### 2.3 跨模組接線

`agent` 模組需注入 `SkillProjectionUseCase`（來自 `skills` 模組）。依 development-standards §13 模式 B（同步埠呼叫）：

1. `skills/application/port/in/` 加 `@NamedInterface("api")`
2. `agent` 的 `allowedDependencies` 加 `"skills::api"`

這是 `agent` → `skills` 的首次跨模組依賴，架構上合理：主代理啟動前需要準備 skill 環境。

### 2.4 Research Citations

- **Claude Code skill 發現路徑：** `<project>/.claude/skills/<name>/SKILL.md` — 本專案的 `.claude/skills/` 目錄即為此格式，經每日使用驗證。
- **agentskills.io YAML frontmatter：** S012 §2.2 已驗證相容性（SnakeYAML 解析）。
- **`ChatCommandRunner` 模式：** S007 §7 已驗證 `ApplicationRunner` + `args.getNonOptionArgs()` 解析。

---

## 3. SBE Acceptance Criteria

**驗證命令：** `./gradlew test`
Pass: 所有攜帶 S016 AC id 的測試為綠色。

---

**AC-1: Skill 清單**

```
Given  ~/.grimo/skills/greet/SKILL.md 存在且格式正確
When   執行 java -jar grimo.jar skill list
Then   stdout 包含 "greet" 及其啟用狀態和描述
And    exit code = 0
```

**AC-2: Skill 啟用/停用**

```
Given  ~/.grimo/skills/greet/ 已存在
When   執行 java -jar grimo.jar skill disable greet
Then   ~/.grimo/skills/.state.json 中 greet.enabled = false
When   再執行 java -jar grimo.jar skill list
Then   greet 顯示為 disabled
When   執行 java -jar grimo.jar skill enable greet
Then   .state.json 中 greet.enabled = true
```

**AC-3: Skill 投影至工作目錄**

```
Given  ~/.grimo/skills/greet/SKILL.md 已啟用
When   執行 java -jar grimo.jar chat
Then   <workdir>/.claude/skills/greet/SKILL.md 被建立
And    檔案內容與 ~/.grimo/skills/greet/SKILL.md 一致
And    已停用的 skill 不被投影
```

**AC-4: 錯誤處理**

```
Given  無 skill 名為 nonexistent
When   執行 java -jar grimo.jar skill enable nonexistent
Then   stderr 顯示 "Skill not found: nonexistent"
And    exit code = 0（不因使用者錯誤而 crash）

When   執行 java -jar grimo.jar skill（無子命令）
Then   stdout 印出用法說明
```

---

## 4. Interface / API Design

```java
// === skills module: 新增投影埠 ===

// skills/application/port/in/SkillProjectionUseCase.java
public interface SkillProjectionUseCase {
    /** 將已啟用 skill 複製至 workDir/.claude/skills/<name>/SKILL.md */
    void projectToWorkDir(Path workDir);
}

// === skills module: CLI adapter ===

// skills/adapter/in/cli/SkillCommandRunner.java
@Component
class SkillCommandRunner implements ApplicationRunner {
    SkillCommandRunner(SkillRegistryUseCase registry) { ... }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("skill")) return;
        // parse: list | enable <name> | disable <name>
    }
}

// === agent module: 注入投影 ===

// agent/adapter/in/cli/ChatCommandRunner.java (修改)
@Component
class ChatCommandRunner implements ApplicationRunner {
    ChatCommandRunner(MainAgentChatUseCase chatUseCase,
                      SkillProjectionUseCase skillProjection) { ... }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("chat")) return;
        Path workDir = Path.of("").toAbsolutePath();
        skillProjection.projectToWorkDir(workDir);  // NEW: 投影 skill
        // ... existing chat/resume logic
    }
}
```

---

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `skills/application/port/in/package-info.java` | new | `@NamedInterface("api")` 暴露 UseCase 給 agent 模組 |
| `skills/application/port/in/SkillProjectionUseCase.java` | new | 投影埠介面 |
| `skills/application/service/SkillProjectionService.java` | new | 複製已啟用 skill 至 `workDir/.claude/skills/` |
| `skills/adapter/in/cli/SkillCommandRunner.java` | new | `skill list/enable/disable` CLI 子命令 |
| `agent/package-info.java` | modify | `allowedDependencies = { "skills::api" }` |
| `agent/adapter/in/cli/ChatCommandRunner.java` | modify | 注入 `SkillProjectionUseCase`，chat 前呼叫投影 |
| `src/test/java/.../skills/SkillCommandRunnerTest.java` | new | 單元測試：list/enable/disable/error |
| `src/test/java/.../skills/SkillProjectionServiceTest.java` | new | 單元測試：投影邏輯 + 停用 skill 不投影 |

---

## Appendix: 人工驗證計畫

完成實作後，開發者按以下步驟驗證 MVP 三大能力：

```bash
# 0. 編譯
./gradlew bootJar

# 1. 建立測試 skill
mkdir -p ~/.grimo/skills/greet
cat > ~/.grimo/skills/greet/SKILL.md << 'EOF'
---
name: greet
description: "A greeting skill for MVP verification"
---
When the user asks you to greet, respond with exactly:
"Hello from Grimo Skill! MVP verification passed."
EOF

# 2. 驗證 Skill 管理（AC-1, AC-2）
java -jar build/libs/grimo-*.jar skill list
#   → greet  enabled  A greeting skill for MVP verification

java -jar build/libs/grimo-*.jar skill disable greet
java -jar build/libs/grimo-*.jar skill list
#   → greet  disabled  ...

java -jar build/libs/grimo-*.jar skill enable greet
#   → greet  enabled  ...

# 3. 驗證對話 + Skill 投影（AC-3 + S007 AC-1）
java -jar build/libs/grimo-*.jar chat
#   → 確認 .claude/skills/greet/SKILL.md 已建立
#   → 輸入「請用 greet skill 打招呼」
#   → 預期回應包含 "Hello from Grimo Skill!"
#   → /exit

# 4. 驗證 Session 恢復（S011 AC-1）
java -jar build/libs/grimo-*.jar chat --resume
#   → 預期接回上次 session
#   → 輸入「我剛才問了什麼？」
#   → 預期引用 greet 相關內容
#   → /exit
```

### 估算

| 維度 | 分數 | 理由 |
|------|------|------|
| 技術風險 | 1 | 檔案複製 + CLI args，技術已知 |
| 不確定性 | 1 | 經 grill 確認，範圍清晰 |
| 依賴關係 | 1 | S007 + S012 已出貨 |
| 範疇 | 2 | CLI 子命令 + 投影服務 + 跨模組接線 |
| 測試 | 1 | 單元測試 + 人工驗證 |
| 可逆性 | 1 | 純新增，隨時可移除 |
| **合計** | **7** | **XS** |

---

## 6. Task Plan

POC: not required — 所有 API 已由先前 spec 驗證（`ApplicationRunner` args → S007、`SkillRegistryUseCase` → S012、`Files.copy` → 標準 JDK、`.claude/skills/` → 本專案日常使用）。

| # | Task | AC | Status |
|---|------|----|--------|
| T01 | Skill CLI 子命令 + `@NamedInterface("api")` | AC-1, AC-2, AC-4 | PASS |
| T02 | Skill 投影 + ChatCommandRunner 整合 | AC-3 | PASS |

Execution order: T01 → T02

---

## 7. Implementation Results

### Verification
- `./gradlew test`: PASS
- `./gradlew compileTestJava`: PASS
- `./gradlew check` (含 Modulith verify): PASS

### Key Findings

1. **`@NamedInterface("api")` 開箱即用。** 在 `skills/application/port/in/` 加上 `@NamedInterface("api")` 後，`agent` 模組宣告 `allowedDependencies = { "skills::api" }` 即可合法引用 `SkillProjectionUseCase` 和 `SkillRegistryUseCase`。Modulith verify 無違規。
2. **`SkillsTool.Skill.basePath()` 是目錄路徑。** 投影時以 `Path.of(skill.basePath()).resolve("SKILL.md")` 取得來源檔案，直接 `Files.copy` 即可。
3. **`DefaultApplicationArguments` 的 non-option args 是有序的。** `skill` 後面的第二、三個 arg 分別是 action 和 name，用 `indexOf("skill") + 1/2` 取得。

### Correct Usage Patterns

```java
// 跨模組依賴接線（skills::api → agent）
// skills/application/port/in/package-info.java
@NamedInterface("api")
package io.github.samzhu.grimo.skills.application.port.in;

// agent/package-info.java
@ApplicationModule(allowedDependencies = { "skills::api" })

// Skill 投影 — Files.copy with REPLACE_EXISTING
Path source = Path.of(entry.skill().basePath()).resolve("SKILL.md");
Path target = workDir.resolve(".claude/skills/" + entry.name() + "/SKILL.md");
Files.createDirectories(target.getParent());
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

// CLI 子命令偵測 — ApplicationRunner pattern
List<String> nonOption = args.getNonOptionArgs();
if (!nonOption.contains("skill")) return;
String action = nonOption.get(nonOption.indexOf("skill") + 1);
```

### AC Results

| AC | Result | Notes |
|----|--------|-------|
| AC-1 | PASS | `SkillCommandRunnerTest.skillListShowsEntries()` — 驗證 list 輸出含名稱、狀態、描述 |
| AC-2 | PASS | `SkillCommandRunnerTest.skillEnableCallsRegistry()` + `skillDisableCallsRegistry()` |
| AC-3 | PASS | `SkillProjectionServiceTest` (4 tests) + `ChatCommandRunnerTest` inOrder verify (3 tests) |
| AC-4 | PASS | `SkillCommandRunnerTest.skillEnableNonexistentPrintsError()` + `skillWithoutSubcommandPrintsUsage()` |

### Pending Verification

- ⏳ **人工驗證計畫**（Appendix）— 需 `./gradlew bootJar` + 主機 claude CLI + `~/.grimo/skills/greet/` 測試 skill。開發者手動執行。

---

## QA Review (獨立審查 · 2026-04-20)

**審查員：** 獨立 QA subagent
**審查命令：** `./gradlew test`、`./gradlew compileTestJava`、`./gradlew check`
**最終裁定：** ⚠️ **CONDITIONAL PASS — 1 項技術債需登記**

---

### 1. 建置與測試結果

| 指令 | 結果 |
|------|------|
| `./gradlew compileTestJava` | PASS — 無編譯錯誤 |
| `./gradlew test` | PASS — 全數通過，0 failures，0 errors，0 skipped |
| `./gradlew check` (含 Modulith verify) | PASS — ModuleArchitectureTest 通過，模組邊界合規 |

---

### 2. AC → 測試對映驗證

| AC | 測試方法（@DisplayName） | 結果 |
|----|--------------------------|------|
| AC-1 Skill 清單 | `[S016] AC-1: 'skill list' shows skills with name, status, description` | PASS |
| AC-2 啟用/停用 | `[S016] AC-2: 'skill enable greet' calls registry.enable()` | PASS |
| AC-2 啟用/停用 | `[S016] AC-2: 'skill disable greet' calls registry.disable()` | PASS |
| AC-3 投影至工作目錄（啟用） | `[S016] AC-3: enabled skill is projected to workDir/.claude/skills/` | PASS |
| AC-3 投影至工作目錄（停用不投影） | `[S016] AC-3: disabled skill is NOT projected` | PASS |
| AC-3 投影覆寫 | `[S016] AC-3: projection overwrites existing file` | PASS |
| AC-3 空清單 | `[S016] AC-3: empty enabled list produces no files` | PASS |
| AC-3 chat 前投影順序 | `[S016] AC-3: 'chat' projects skills before startChat` | PASS |
| AC-3 chat --resume 前投影順序 | `[S016] AC-3: 'chat --resume' projects skills before resumeChat` | PASS |
| AC-4 不存在 skill | `[S016] AC-4: 'skill enable nonexistent' prints error to stderr` | PASS |
| AC-4 無子命令顯示用法 | `[S016] AC-4: 'skill' without subcommand prints usage` | PASS |

所有 AC 均有對應 `@DisplayName` 測試，且攜帶 `// Given / When / Then` 區塊（符合 development-standards §7.9）。

---

### 3. 生產程式碼審查

**SkillProjectionUseCase.java** — Javadoc 準確，與實作邏輯一致（`basePath()` → resolve `SKILL.md` → `Files.copy REPLACE_EXISTING`）。

**SkillProjectionService.java** — 實作符合 §2.2 設計：`listEnabled()` 取得啟用 skill → 逐個 copy → `IOException` 以 `log.warn` 跳過（不中斷啟動）。SLF4J 日誌使用正確。

**SkillCommandRunner.java** — 實作符合 §2.1 / §4 介面設計。`ApplicationRunner` 模式、non-option args 解析、`indexOf("skill") + 1/2` 取得 action/name，均與 §7 Key Findings 一致。

**ChatCommandRunner.java** — 已注入 `SkillProjectionUseCase`，在 `startChat()` 和 `resumeChat()` 前均呼叫 `skillProjection.projectToWorkDir(workDir)`，與 §2.2 「`--resume` 路徑也同樣投影」一致。

**agent/package-info.java** — `allowedDependencies = { "skills::api" }` 正確宣告，Modulith verify 通過。

**skills/application/port/in/package-info.java** — `@NamedInterface("api")` 正確套用，符合 development-standards §13 模式 B。

---

### 4. 程式碼品質問題

**發現：[TD] `SkillCommandRunner` 使用 `System.out` / `System.err`（違反 development-standards §3）**

`SkillCommandRunner.java` 內有 9 處 `System.out.println/printf` 和 1 處 `System.err.println`，違反 development-standards §3 規定「絕不使用 `System.out`」。

評估：
- CLI 輸出（`skill list` 的表格）本質上是「使用者介面輸出」而非日誌，業界常見做法是允許 CLI adapter 直接寫 stdout/stderr。
- 此模式已在 S011 `ChatCommandRunner`（`System.err.println(e.getMessage())`）先例存在，屬於已知的一致性問題（非 S016 新引入）。
- `SkillCommandRunner` 為 CLI adapter（`adapter/in/cli/`），屬於最外層；`System.out` 的使用在 CLI 情境下有合理性。
- 建議在 `spec-roadmap.md` 技術債表新增 `drift` 條目，說明 development-standards §3 的 `System.out` 禁令是否適用於 CLI adapter，或應豁免。

**其餘問題：** 無。

---

### 5. 設計飄移檢查

| 設計決策（§2/§4） | 實際實作 | 結論 |
|-------------------|----------|------|
| `SkillProjectionUseCase` 介面在 `skills/application/port/in/` | 位置正確 | 符合 |
| `SkillProjectionService` 實作 | 位置正確，套件私有 `@Service` | 符合 |
| `SkillCommandRunner implements ApplicationRunner` | 已實作，套件私有 `@Component` | 符合 |
| `ChatCommandRunner` 注入 `SkillProjectionUseCase` | 已注入，建構子注入（符合 §4） | 符合 |
| `agent/package-info.java` 宣告 `allowedDependencies = { "skills::api" }` | 已宣告 | 符合 |
| `skills/application/port/in/package-info.java` 加 `@NamedInterface("api")` | 已加 | 符合 |
| `IOException` → `log.warn` + 跳過 | 已實作（不中斷啟動） | 符合 |
| `REPLACE_EXISTING` 覆寫 | 已實作 | 符合 |

無設計飄移。

---

### 6. 技術債登記建議

| 類型 | 描述 | 建議行動 |
|------|------|----------|
| `drift` | `SkillCommandRunner` 使用 `System.out`，違反 development-standards §3；`ChatCommandRunner` 的 `System.err` 前例也屬同類。§3 的禁令是否應豁免 CLI adapter 層需要釐清。 | 在 `spec-roadmap.md` 技術債表新增條目，下次 `/planning-spec` 時決策是否豁免或遷移至日誌 |

---

### 7. 總結

- 功能實作完整，所有 AC 均有對應測試且全數為綠色。
- 跨模組接線（`@NamedInterface("api")` + `allowedDependencies`）正確，Modulith verify 通過。
- Javadoc 與實作一致，無錯誤描述。
- `System.out` 使用為已知 drift（非 S016 新引入模式，S011 已有先例），建議登記技術債而非阻擋出貨。
- 人工驗證計畫（Appendix）標記為 ⏳，屬設計預期的手動步驟，不影響自動化驗證結果。

**裁定：CONDITIONAL PASS — 可出貨，請同步在 `spec-roadmap.md` 技術債表新增 `System.out drift` 條目。**
