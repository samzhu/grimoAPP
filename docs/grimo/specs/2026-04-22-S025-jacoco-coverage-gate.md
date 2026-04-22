# S025: JaCoCo Coverage Gate

> Spec: S025 | Size: XS (6) | Status: ✅ Done
> Date: 2026-04-22

---

## 1. Goal

**一句話：** 在 build.gradle.kts 配置 JaCoCo plugin，對 `application/service/` 和 `domain/` 套件強制行覆蓋率 ≥ 75%，並將涵蓋率檢查加入 verify-all.sh 驗證管道。

### 背景

qa-strategy.md §2 自 2026-04-16 即列 JaCoCo 0.8.12 為涵蓋率工具，目標 `application/service/` 和 `domain/` 套件行覆蓋率 ≥ 75%。但 `build.gradle.kts` 從未配置 `jacoco` plugin。`/verifying-quality` 的 Step 0.5 偵測到此 gap（QA 目標存在但工具未配置），觸發 `REJECT-BLOCKED`，建議開立本 spec。

### 依賴

無。純基礎設施，不引入新的生產程式碼。

---

## 2. Approach

### 2.1 Gradle `jacoco` plugin 配置

```kotlin
plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.12"  // qa-strategy.md §2 釘定
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)   // CI 工具消費
        html.required.set(true)  // 開發者瀏覽
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            includes = listOf(
                "io.github.samzhu.grimo.*.domain.*",
                "io.github.samzhu.grimo.*.application.service.*"
            )
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}
```

**Pattern 解釋：**
- `io.github.samzhu.grimo.*.domain.*` → 匹配所有模組的 `domain/` 套件（如 `core.domain.SessionId`、`session.domain.SessionEvent`）
- `io.github.samzhu.grimo.*.application.service.*` → 匹配所有模組的 `application/service/` 套件（如 `agent.application.service.MainAgentChatService`）
- 適配器（`adapter/`）和 port interface（`port/`）不在涵蓋率目標內 — 與 qa-strategy.md §2 一致（「適配器覆蓋率為副產品，非目標」）

### 2.2 verify-all.sh 新增 V4

```bash
# V4: Coverage gate (JaCoCo line coverage ≥ 75% for service + domain)
run_critical "V4-coverage" \
    ./gradlew jacocoTestCoverageVerification --console=plain
```

`jacocoTestCoverageVerification` 依賴 `jacocoTestReport` 依賴 `test`。由於 V2 已跑過 `test`，Gradle up-to-date check 不會重跑。

### 2.3 qa-strategy.md §6.1 新增 V4 條目

| # | 命令 | 層級 | 引入 spec | 環境需求 | 失敗處理 |
|---|------|------|-----------|----------|----------|
| V4 | `./gradlew jacocoTestCoverageVerification` | 涵蓋率 | S025 | 無 | CRITICAL — 阻擋出貨 |

### 2.4 不包含

| 項目 | 理由 |
|------|------|
| CI JaCoCo badge / PR comment | 未來 CI spec 負責 |
| 逐模組獨立涵蓋率閾值 | 單一 75% 規則足夠 MVP |
| integrationTest 涵蓋率 | E2E 涵蓋率是副產品，非目標 |

---

## 3. SBE Acceptance Criteria

**驗證命令：** `./gradlew test jacocoTestCoverageVerification`
Pass: 涵蓋率檢查通過（exit 0）且 HTML 報告產生。

---

**AC-1: JaCoCo plugin 配置且產生報告**

```
Given  build.gradle.kts 配置 jacoco plugin
When   ./gradlew test jacocoTestReport
Then   build/reports/jacoco/test/html/index.html 存在
And    build/reports/jacoco/test/jacocoTestReport.xml 存在
```

**AC-2: 涵蓋率驗證閘門 — service + domain ≥ 75%**

```
Given  AC-1 通過
When   ./gradlew jacocoTestCoverageVerification
Then   exit 0（涵蓋率 ≥ 75%）
```

**AC-3: verify-all.sh 包含 V4 涵蓋率命令**

```
Given  verify-all.sh 已更新
When   ./docs/grimo/scripts/verify-all.sh
Then   輸出包含 V4-coverage 且結果為 PASS
```

**AC-4: qa-strategy.md §6.1 包含 V4 條目**

```
Given  qa-strategy.md 已更新
When   讀取 §6.1 驗證命令登錄表
Then   V4 行存在，命令為 jacocoTestCoverageVerification，失敗處理為 CRITICAL
```

---

## 4. Interface / API Design

### 4.1 無生產程式碼變更

純基礎設施 spec。變更限於 build 配置和腳本。

### 4.2 Gradle task 依賴圖

```
test → jacocoTestReport → jacocoTestCoverageVerification
```

### 4.3 涵蓋率報告路徑

| 報告 | 路徑 |
|------|------|
| HTML | `build/reports/jacoco/test/html/index.html` |
| XML | `build/reports/jacoco/test/jacocoTestReport.xml` |

---

## 5. File Plan

### 修改檔案

| File | Change |
|------|--------|
| `build.gradle.kts` | 新增 `jacoco` plugin + `jacocoTestReport` + `jacocoTestCoverageVerification` |
| `docs/grimo/scripts/verify-all.sh` | 新增 V4 涵蓋率命令 |
| `docs/grimo/qa-strategy.md` | §6.1 新增 V4 條目 |

**合計：** 0 新增、3 修改 = **3 個檔案接觸點**

---

### 估算

| 維度 | 分數 | 理由 |
|------|------|------|
| 技術風險 | 1 | Gradle 內建 plugin，標準 DSL |
| 不確定性 | 1 | qa-strategy.md 已定義目標和工具版本 |
| 依賴關係 | 1 | 無依賴 |
| 範疇 | 1 | 3 個檔案，純配置 |
| 測試 | 1 | 自我驗證 — 如果 coverageVerification 通過，配置就是對的 |
| 可逆性 | 1 | 移除 plugin block 即可回滾 |
| **合計** | **6** | **XS** |

---

## 6. Task Plan

### POC Decision

**POC: not required.** `jacoco` 是 Gradle 內建 plugin，標準 Kotlin DSL，無外部依賴。

### Task Summary

| Task | AC | Description | Depends On |
|------|----|-------------|------------|
| T01 | AC-1, AC-2, AC-3, AC-4 | JaCoCo plugin + coverage gate + registry update | — |

### Execution Order

```
T01 (all 4 ACs — single infrastructure task)
```

Task files: `docs/grimo/tasks/2026-04-22-S025-T01.md`

---

## 7. Implementation Results

### Verification Results

```
./gradlew test jacocoTestReport jacocoTestCoverageVerification → BUILD SUCCESSFUL
./docs/grimo/scripts/verify-all.sh → V1 PASS, V2 PASS, V3 PASS, V4 PASS, OVERALL PASS
```

### Key Findings

1. **JaCoCo 0.8.12 不支援 Java 25。** Class file major version 69 (Java 25) 導致 `Unsupported class file major version 69` 錯誤。JaCoCo 0.8.13（2025-04-02 發布）起有 Java 25 實驗性支援。已更新 `toolVersion` 為 `0.8.13`，同步更新 qa-strategy.md §2。

2. **涵蓋率通過 ≥ 75%。** `jacocoTestCoverageVerification` exit 0，表示 `service/` 和 `domain/` 的行覆蓋率達標。

### [Implementation note] Divergence from §2

- §2.1 寫 `toolVersion = "0.8.12"` — 實際使用 `0.8.13`（Java 25 相容性需要）。qa-strategy.md §2 已同步更新。

### AC Results

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 JaCoCo report generated | ✅ | `build/reports/jacoco/test/html/index.html` (25KB) + `jacocoTestReport.xml` (190KB) |
| AC-2 Coverage verification ≥ 75% | ✅ | `jacocoTestCoverageVerification` exit 0 |
| AC-3 verify-all.sh includes V4 | ✅ | `V4-coverage PASS` in output |
| AC-4 qa-strategy.md §6.1 includes V4 | ✅ | V4 row added |
