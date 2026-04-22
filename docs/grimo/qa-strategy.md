# Grimo — QA 策略

**狀態：** v0.1 · **日期：** 2026-04-16

## 1. 哲學

兩種互補的控制機制，源自 PRD 中的外殼工程（harness-engineering）準則：

- **計算型控制（Computational controls）** — 確定性驗證：單元測試、模組邊界檢查、契約測試、原生映像冒煙測試。這是主要的品質閘門。
- **推理型控制（Inferential controls）** — LLM 作為評審，針對行為契約（例如「CLI 切換後，下一個回應是否引用了先前的上下文？」）進行檢查。選擇性用於確定性斷言困難的 SBE 標準。

## 2. 框架與固定工具

| 關注點 | 工具 | 備注 |
| --- | --- | --- |
| 測試執行器 | JUnit Jupiter 5.11+ | `testImplementation("org.springframework.boot:spring-boot-starter-test")` 在 Boot 4.0.5 中引入此依賴 |
| 斷言 | AssertJ 3.26+ | 透過 `starter-test` 引入 |
| Modulith verify | `spring-modulith-starter-test` | `ApplicationModules.of(GrimoApplication.class).verify()` |
| 架構規則 | ArchUnit（Modulith 傳遞引入） | 領域層無 Spring 的斷言位於 `core/ArchitectureTest.java` |
| 容器測試 | Testcontainers 1.20.4 | **僅限 JVM classpath**；所有此類類別標記 `@DisabledInNativeImage` |
| HTTP 測試 | `MockMvc`（`@WebMvcTest`）+ `WebTestClient`（用於 SSE） | HTMX 互動以 `mockMvc.perform(...).andExpect(header().exists("HX-*"))` 涵蓋 |
| DB 測試 | `@DataJdbcTest`（H2 記憶體模式） | 生產環境使用 H2 檔案模式（D17） |
| 程式碼格式化 | _延後 — 在後續清理規格中以外部工具一次性執行_ | 非每次建置閘門（使用者 2026-04-16 決定） |
| 原生建置 | `org.graalvm.buildtools.native` 0.11.5 | 夜間 `nativeCompile` 冒煙測試 |
| 覆蓋率 | JaCoCo 0.8.13 | 全部程式碼行覆蓋率總和 ≥ 80%，排除純接線（`*Config`、`port/in/`、`port/out/`、`events/`、`package-info`）及第三方程式碼（`org.springaicommunity`）。透過 `classDirectories` 過濾，非 `rule.includes`（後者在 `element=BUNDLE` 下比對 bundle 名稱，無法過濾 class）。報告格式：HTML + XML + CSV。 |

## 3. 測試分類

| 層級 | 範疇 | 執行器 | 目標 CI 閘門 |
| --- | --- | --- | --- |
| **T0 Unit** | `domain/` records + 純服務 | 純 JUnit | PR 閘門（快速，每模組 < 10s） |
| **T1 Module** | 單一 `@ApplicationModule` 接線（無跨模組 beans） | `@ApplicationModuleTest` | PR 閘門 |
| **T2 Slice** | 每適配器的 `@WebMvcTest`、`@DataJdbcTest` | Spring slices | PR 閘門 |
| **T3 Contract** | 埠 ↔ 適配器契約（例如：`Sandbox` SPI 契約針對兩個實作執行） | `@SpringBootTest(classes = Sandbox*Config)` | PR 閘門 |
| **T4 Integration** | 端對端使用者可見行為（使用外部 CLI 的存根） | `@SpringBootTest` + WireMock-for-LLM | 夜間 + 標籤觸發 |
| **T5 Native smoke** | `nativeCompile` + 啟動 + `/actuator/health` | `gradle nativeTest` | 夜間（v1 不阻擋 PR） |
| **T6 Inferential** | LLM 評審對照 SBE 標準審查對話記錄 | 自訂執行器，選擇加入 | 每週 / 發版候選 |

## 4. SBE ↔ 測試對映

每個 PRD 驗收標準必須至少被一個測試引用。規格文件攜帶前向引用。反向索引位於此處。AC 覆蓋由 `/verifying-quality` Step 4（可測試性閘門）在每次 QA 審查時驗證。

| PRD AC | 主要測試類別 | 層級 |
| --- | --- | --- |
| AC1 簡單任務路由 | `RouterDecisionTest` | T1 |
| AC2 策略性升級 | `RouterDecisionTest` | T1 |
| AC3 CLI 切換重放 | `CliSwitchReplayIT` | T4 |
| AC4 主代理唯讀 | `MainAgentAllowlistTest` | T1 |
| AC5 子代理隔離 | `SubagentWorktreeIT`（Testcontainers） | T4 |
| AC6 軟失敗啟動 | `FailSoftBootIT`（spring-boot-test，存根 `PATH`） | T4 |
| AC7 陪審團審查 | `JuryReviewIT` | T4 |
| AC8 技能蒸餾 | `SkillDistillerTest` | T1 |
| AC9 記憶體整理 | `AutoMemoryToolsTest` | T1 |
| AC10 模組邊界 | `ModuleArchitectureTest.verify()` | T1 |

## 5. 按規格大小路由（自動驗證規則）

| 大小 | 合併時自動驗證 | 是否需要人工 QA |
| --- | --- | --- |
| **XS**（6-8 點） | `./gradlew test` 通過 + 相關 T0/T1 通過 | 否 |
| **S**（9-11） | 上述 + T2 slices + `./gradlew modulith:verify` | 否 |
| **M**（12-14） | 上述 + T3 契約測試 + 受影響路徑的整合測試（T4） | 否 |
| **L**（15-16） | 上述 + 完整 T4 執行 + 2 人程式碼審查 | **是** — 呼叫 `/verifying-quality` |
| **XL**（17-18） | 分解；不允許以 XL 出貨 | 不適用 |

## 6. 驗證管道

```
開發者儲存程式碼
   ↓
docs/grimo/scripts/verify-all.sh         ──▶ 執行驗證命令登錄表中的所有命令（V1-V4）
   ↓（通過）
CI PR 閘門：執行 T0..T3、JaCoCo 報告（≥ 80%）、Dependabot/OWASP 檢查
   ↓（合併至 main）
CI 夜間：
  - 完整 T4 整合測試
  - T5 nativeCompile + health probe
  - T6 推理評審對話記錄（一旦有記錄）
```

### 6.1 驗證命令登錄表（Verification Command Registry）

**權威單一來源。** `docs/grimo/scripts/verify-all.sh` 是此表的可執行版本。

**維護者：`/verifying-quality`。** 每次 QA 審查時，`/verifying-quality` 負責對照 build file 和 QA 目標，偵測 gap 並更新此表 + `verify-all.sh`。若需要新的驗證工具（如 JaCoCo）但尚未配置，`/verifying-quality` 會 `REJECT-BLOCKED` 並建議開立 spec。

| # | 命令 | 層級 | 引入 spec | 環境需求 | 失敗處理 |
|---|------|------|-----------|----------|----------|
| V1 | `./gradlew compileTestJava` | 編譯 | S000 | 無 | CRITICAL — 阻擋出貨 |
| V2 | `./gradlew test` | T0-T2 單元 + 切片 | S000 | 無 | CRITICAL — 阻擋出貨 |
| V3 | `./gradlew integrationTest` | T4 E2E | S024 | claude CLI + 有效登入 | SKIP if unavailable — 記錄 skip 原因 |
| V4 | `./gradlew jacocoTestCoverageVerification` | 行覆蓋率 ≥ 80%（全部程式碼扣除純接線 + 第三方） | S025 | 無 | CRITICAL — 阻擋出貨 |

**失敗處理規則：**
- `CRITICAL`：命令必須 exit 0，否則阻擋出貨。
- `SKIP if unavailable`：環境不可用時允許跳過，但必須記錄 skip 原因。環境可用時必須 exit 0。

**一致性規則：**
- `verify-all.sh` 的命令清單必須與此表完全一致。
- `/verifying-quality` 每次執行時自動對帳（build file vs 此表），發現 gap 即修正或 REJECT-BLOCKED。

## 7. CLI 代理的確定性存根

- `cli` 模組的出站埠 `AgentClientPort` 有一個生產實作（`AgentClientAdapter`）和一個測試實作（`StubAgentClientAdapter`），後者重放預設的 `Flux<String>` 串流。
- T0..T3 期間不執行真實的 `claude`/`codex`/`gemini` 呼叫。T4 如果機器上有安裝 CLI 可以使用真實 CLI，但測試必須在 CLI 缺失時以清楚的訊息跳過（絕不因 CLI 缺失而失敗）。

## 8. 原生映像冒煙測試配方（T5）

夜間 GitHub Actions 任務：

```yaml
- uses: graalvm/setup-graalvm@v1
  with: { java-version: '25', distribution: 'graalvm' }
- run: ./gradlew nativeCompile
- run: ./build/native/nativeCompile/grimo --server.port=18080 --grimo.subagent.backend=process &
- run: for i in 1 2 3 4 5 6 7 8 9 10; do \
         curl -fsS http://127.0.0.1:18080/actuator/health && break || sleep 2; \
       done
- run: curl -fsS http://127.0.0.1:18080/actuator/health | grep '"status":"UP"'
- run: pkill -TERM -f grimo || true
```

冒煙測試失敗在 v1 中**不阻擋 PR** — 它會建立一個標記 `native-regression` 的 issue，供下一個 sprint 處理。

## 9. 覆蓋率與儀表板

- JaCoCo HTML：`build/reports/jacoco/test/html/index.html`。
- JaCoCo CSV：`build/reports/jacoco/test/jacocoTestReport.csv`（方便腳本解析逐 class 覆蓋率）。
- Modulith 生成的 C4 + 模組畫布：`build/spring-modulith-docs/`。

## 10. 腳本（驗證命令）

位於 `docs/grimo/scripts/`：

- `verify-all.sh` — 驗證命令登錄表（§6.1）的可執行版本。執行所有 V1-V4 命令，記錄結果至 `build/reports/grimo/verify-all-log.txt`。

## 11. 升級路徑

- **不穩定的測試** → 標記 `@Tag("flaky")`，開立 `flaky-test` issue，在兩個 sprint 內修復或刪除。
- **失敗的原生冒煙測試** → v1 中為非阻擋；開立 `native-regression` issue；必須在 M7 結束前清除。
- **L 大小規格** → 實作完成後呼叫 `/verifying-quality`。
- **策略偏差**（跳過測試層級）→ 在 PR 中記錄，若成為模式則建立 ADR。

## 12. 啟動清單（S000 第一天需通過）

- `./gradlew build` 通過（空應用，只有 `GrimoApplication.java`）。
- `./gradlew test` 報告零測試但以 0 退出。
- `./gradlew modulith:verify` 在空模組圖上通過。
- `./gradlew nativeCompile` 在 hello-world 應用上成功。
- `docs/grimo/scripts/verify-all.sh` 呼叫所有 V1-V4 且記錄 PASS。
- 程式碼格式化有意不在建置時把關（見 §2）。
