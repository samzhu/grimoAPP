# Grimo — 開發標準

**狀態：** v0.1 · **日期：** 2026-04-16
**適用範圍：** 本 repo 中的每個 Java 原始檔、每個 Gradle 腳本、每個 markdown 產物。

> 此處的規則為**規範性（normative）**。偏差需在 `docs/grimo/adr/` 下建立 ADR。

## 1. 語言與建置

- **Java 25**（在 `build.gradle.kts` 中以 toolchain 固定）。Preview 功能：**禁用**（生產二進位不得需要 `--enable-preview`）。
- 慣用功能：`record`、sealed 型別、`switch` 和 `instanceof` 的模式匹配、virtual threads（`Thread.ofVirtual`、`Executors.newVirtualThreadPerTaskExecutor()`）、`var`（僅用於右側型別顯而易見的區塊本地變數）。
- **Gradle Kotlin DSL**（`build.gradle.kts`）。不引入 Groovy 的 `build.gradle`。使用 version catalog（`gradle/libs.versions.toml`）管理共用版本。
- v1 **不強制**每個 Spring Modulith 模組對應一個 Gradle 子專案 — v1 是單一 `:app` 專案，以套件作為模組。

## 2. 套件配置（嚴格 · MVP）

> **2026-04-16 更新（S002）：** 容器優先 MVP 重新規劃後，下方僅列 MVP 真正落地的 6 個模組。原 v1 模組（`session`、`router`、`memory`、`jury`、`cost`、`web`、`nativeimage`）已移至 `architecture.md` §2.x Backlog 附錄；它們晉升時各自的 spec 會建立對應 `package-info.java`。

```
io.github.samzhu.grimo
├── GrimoApplication.java           # @SpringBootApplication, @EnableAsync
├── core/                           # @ApplicationModule(type = Type.OPEN)
│   ├── package-info.java
│   └── domain/...                  # POJOs，零 Spring（S001）
├── sandbox/                        # @ApplicationModule, allowedDependencies = {} 起步
│   ├── package-info.java
│   ├── domain/...                  # POJOs，零 Spring
│   ├── application/
│   │   ├── port/in/...UseCase.java
│   │   ├── port/out/...Port.java
│   │   └── service/...Service.java
│   ├── adapter/in/event/...
│   └── adapter/out/...             # Testcontainers / Docker 適配器（S003）
├── cli/                            # AgentCliPort + docker-exec 適配器（S005）
├── agent/                          # 主代理 CLI 直通（S007）
├── subagent/                       # 委派 + 工作樹 + 子代理生命週期（S008–S010）
└── skills/                         # SKILL.md 登錄檔 + 注入子代理容器（S011、S012）
```

**Backlog 套件命名注意：** 原生加固模組的套件名為 **`nativeimage`**（不是 `native`）— `native` 是 Java 保留字（JNI modifier），無法當套件名使用。Backlog 模組晉升時請使用 `nativeimage`。

### 模組邊界規則

- 每個模組的 `package-info.java` 攜帶 `@ApplicationModule(displayName = "Grimo :: <Name>", allowedDependencies = { ... })`。`core` 為 `type = ApplicationModule.Type.OPEN`；其餘模組以 `allowedDependencies = {}` 嚴格起步，由各自的 owning spec 在第一次跨模組引用時擴充（見 §13 Cross-Module Communication）。
- **`domain/`** 套件在沒有 Spring classpath 的情況下也能編譯。由 ArchUnit 測試強制執行。
- **`internal/`** 子套件為實作細節 — 兄弟模組不得引用它們。
- 對需要發佈給其他模組的套件使用 `@NamedInterface("...")`：同步埠用 `api`、事件型別用 `events`（命名約定見 §13）。
- `ModuleArchitectureTest`（S002）在每次 `./gradlew test` 跑 `ApplicationModules.of(GrimoApplication.class).verify()`，違反上述規則的引用會在 PR 階段被擋下。

## 3. 命名與編碼慣例

- **類別命名：**
  - 用例介面：`<動詞><名詞>UseCase` → `DelegateTaskUseCase`。
  - 用例實作：`<動詞><名詞>Service`。
  - 出站埠：`<名詞>Port` → `WorktreePort`。
  - 適配器實作：`<技術><名詞>Adapter` → `TestcontainersSandboxAdapter`。
  - 事件（record）：過去式 → `SubagentCompleted`、`RouteDecided`。
  - 配置：`<功能>Config`。
  - Runtime hints：`<功能>RuntimeHints`。
- **套件命名：** 全小寫單字；多字以 `subagent` 形式（非 `sub_agent` 或 `sub-agent`）。
- **方法：** 動詞。查詢單一實體時回傳 `Optional<T>`，絕不回傳 `null`。集合絕不回傳 `null`。
- **不可變性：** 領域型別為 `record` 或 final 類別。可變狀態限制在 `application/service/` 中，且必須在明確的同步或單執行緒 virtual thread 派送下使用。
- **Null 紀律：** 優先使用 `@org.jspecify.annotations.Nullable`（JSpecify 1.0 隨 JDK 附帶）。任何地方均不使用 `javax` 注解。
- **日誌：** SLF4J 搭配 Spring Boot 預設。絕不使用 `System.out`。INFO 記錄意圖，DEBUG 記錄參數，ERROR 附帶 throwable + correlationId。

## 4. 依賴注入

- **僅使用建構子注入。** 不使用欄位注入，不使用 setter 注入。建構子上的 `@Autowired` 為多餘（Spring 4.3+ 自動偵測單一建構子）。
- **不自訂 `@ComponentScan`。** 依賴 Spring Boot 預設 + Modulith 的套件所有權。
- 配置類別分組於 `<module>/adapter/out/**/Config.java` 或模組根目錄（若為跨切面）。

## 5. Spring AI 與 agent-client 慣例

- **絕不**在業務邏輯程式碼中直接呼叫 `new ClaudeAgentModel(...)`。永遠使用 Spring AI 自動配置生成的 `AgentClient`，並包裝在模組的出站埠（`AgentClientPort`）後面。
- CLI 缺失錯誤以帶型別的領域事件（`CliUnavailable`）傳遞，而非帶有堆疊追蹤的 Spring Boot 例外。
- Grimo 自有的屬性鍵使用 `grimo.*` 前綴。第三方屬性保持在其自有命名空間下（`spring.ai.agents.claude-code.*`）。

## 6. Thymeleaf 與 HTMX

- **禁用：** `thymeleaf-layout-dialect`（Groovy 執行期 → native 阻礙）。使用**帶參數的 fragments**：`th:fragment="page(content)"`。
- 模板位於 `src/main/resources/templates/`，目錄結構鏡像控制器 URL。
- HTMX 屬性在 HTML 中使用 `hx-*`（非 `data-hx-*`）。
- SSE token 串流每頁使用單一共用的 `sse-connect` 根；每訊息交換目標以 `hx-swap-oob="true"` 限制範圍。

## 7. 測試慣例

詳細說明見 `qa-strategy.md`。以下規則源自上游 `spring-ai-community/agent-client` 的生產驗證策略 — 來源見記憶條目 `reference_agent_client_testing.md`。

### 7.1 框架與切片

- 框架：**JUnit Jupiter 5.11+** + **AssertJ 3.26+** + Spring Boot Test。
- **切片測試**優先於 `@SpringBootTest`：
  - `@ApplicationModuleTest`（Modulith）用於每模組整合測試。
  - `@DataJdbcTest` 用於儲存庫適配器。
  - `@WebMvcTest` 用於控制器適配器。
  - `domain/` 使用純 JUnit — 無 Spring。
- 完整上下文的 `@SpringBootTest` 為**最後手段**。使用時類別名稱必須以 `IT` 結尾（見 §7.2）。

### 7.2 單元測試 vs 整合測試 — 以類別名稱後綴區分

- **`*Test.java`** — 單元測試。每次 `./gradlew test` 都執行。無真實 CLI 子程序、無 Docker Daemon、無真實憑證。純翻譯邏輯、record 驗證、builders、mappers。
- **`*IT.java`** — 整合測試。執行於 `./gradlew integrationTest`（一個以 `include "**/*IT.class"` 過濾的專用任務；原始檔與單元測試並列，無獨立 source set）。
- **不使用 `@Tag("integration")`** — 基於名稱後綴的方式更簡潔，也符合上游模式。

### 7.3 不模擬 CLI 子程序

- **絕不**使用 `Mockito.mock(Process.class)` 或類似方式模擬 CLI 管道。模擬子程序會隱藏真實失敗模式（stdin/stdout 編碼、超時、信號處理、部分串流）。
- 單元測試僅涵蓋**純邏輯**：prompt 序列化、JSON 解析、option builders、registry maps。存根輸入，真實翻譯邏輯，真實斷言。
- CLI 整合測試使用**主機上的真實二進位**（見 §7.4）。不存在假的中間層。

### 7.4 CLI 整合測試 — 主機二進位，非 Testcontainers

- 對於 CLI **功能正確性**驗證（claude-code 是否真的產生我們預期的輸出？），直接呼叫**主機二進位**。更快、更簡單，且繼承開發者自己的 `claude login` / `gemini auth` / `codex login` Session — 符合 P10 訂閱帳號原生認證原則。
- Testcontainers 保留給**沙箱基礎設施**驗證：容器是否以正確的 bind-mount 啟動、工作樹隔離是否有效、清理是否移除了容器。**不**用於斷言 CLI 輸出內容。
- 所有接觸 Testcontainers 的類別都標記 `@DisabledInNativeImage`。

### 7.5 真實 CLI IT 的三層跳過策略

每個場景組合使用以下策略；不要合併成一個：

1. **類別層級** `@DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "…")` — 在未安裝 CLI 的 CI 執行器上（例如 Codex）殺掉整個 IT 類別。
2. **選擇加入** `@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")` — 用於需要 Docker 的沙箱基礎設施 IT。
3. **每測試的優雅跳過** `Assumptions.assumeTrue(cliAvailable() && credentialsPresent())` 在 `@BeforeEach` 中 — 這是 claude 和 gemini IT 的預設（繼承開發者的 shell 認證）。

每個提供者模組提供一個靜態 `<Provider>CliDiscovery.isCliAvailable()` 輔助方法供第 3 層使用。

### 7.6 每提供者行為的 TCK 模式

當 ≥ 2 個提供者共享行為契約（串流 token、回應 `CliUnavailable`、遵守超時），將行為測試放在 `src/test/java/.../shared/` 下的抽象 TCK 類別中。每個提供者的 IT 繼承它，只在 `@BeforeEach` 中注入提供者特定物件。

命名：`AbstractAgentCliAdapterTCK`（TCK）→ `ClaudeAgentCliAdapterIT`、`CodexAgentCliAdapterIT`、`GeminiAgentCliAdapterIT`。

新增第四個提供者 ≈ 30 LOC 新的 IT 程式碼。

### 7.7 密鑰 / API 金鑰

透過 Gradle 測試任務的 `environment(...)` 區塊注入；**絕不**使用 `System.setProperty`，**絕不**提交 `.env` 檔案：

```kotlin
tasks.withType<Test> {
    listOf("ANTHROPIC_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY")
        .forEach { k -> System.getenv(k)?.let { environment(k, it) } }
}
```

CI 將其 `secrets:` 直接對映至環境變數。金鑰缺失 → IT 透過 `assumeTrue`（§7.5 第 3 層）自我跳過。

### 7.8 在任務層級重試，不在測試程式碼中重試

僅對 `integrationTest` 啟用 [org.gradle.test-retry](https://plugins.gradle.org/plugin/org.gradle.test-retry)，設定 `maxRetries = 2`。承認 CLI 串流的不穩定性（token 競爭、socket 重置），同時不污染測試體。

單元測試**不重試** — 不穩定的單元測試是 bug，修復它們。

### 7.9 Given / When / Then

每個測試方法都攜帶 `// Given / When / Then` 注解區塊，鏡像所屬規格檔案中的 SBE 範例。這個區塊是為人工審計 AC ↔ 測試對映所用，而非執行期行為。

## 8. 錯誤與例外處理

- 只使用來自 `domain/` 套件的**領域例外**（checked 或 unchecked）。絕不讓 `java.sql.*`、`java.io.*` 或 `java.net.*` 例外跨越模組邊界 — 在適配器邊緣翻譯它們。
- `@ControllerAdvice` 在 web 適配器中捕獲領域例外，並對映至 HTMX 友好的 partial（適當時使用帶 `HX-Retarget` header 的 HTTP 2xx，而非 5xx）。
- 程序層級的 `Thread.UncaughtExceptionHandler` 記錄日誌並發出 `grimo.uncaught` 指標 — 測試驗證它在乾淨執行中不會觸發。

## 9. Git 與提交慣例

- **分支：** 在 `main` 上工作；功能分支命名為 `spec/S<NNN>-<slug>`，例如 `spec/S003-web-shell`。
- **提交訊息：** Conventional Commits 風格，以規格 ID 為前綴：
  ```
  feat(S004): wire ClaudeAgentModel behind AgentClientPort
  fix(S006): drop a pinned token when SSE stream is cancelled
  test(S010): assert main-agent tool allowlist rejects Edit
  docs(arch): note Testcontainers JVM-only stance
  ```
- 每個邏輯步驟一個提交；無理由不壓縮（squash）。
- Pre-commit hook 執行 `./gradlew modulith:verify`（包裝 `ApplicationModules.verify()` 的自訂任務）。程式碼格式化有意**不在提交時**把關 — 由後續清理規格中的外部工具一次性處理。
- **禁止對 `main` 強制推送。** 句號。

## 10. 文件維護

- 每個新領域術語 → `docs/grimo/glossary.md`。
- 每個與 PRD 決策相矛盾或擴展的新決策 → ADR 位於 `docs/grimo/adr/ADR-NNN-<slug>.md`。
- 架構變更 → 在同一 PR 中更新 `architecture.md`。
- 規格位於 `docs/grimo/specs/S<NNN>-<slug>.md`；必要章節見 `qa-strategy.md`。

### 10.1 技術債追蹤

技術債統一記錄於 `spec-roadmap.md` 的 `## 技術債（Tech Debt）` 區段，與 Backlog 共用相同的晉升機制（`/planning-spec` grill 循環）。

**三種類型：**

| 類型 | 定義 | 範例 |
| --- | --- | --- |
| `bug` | 實作缺陷，已知但非阻塞出貨 | 錯誤處理路徑未覆蓋、邊界條件未測試 |
| `drift` | 文件與實作不符 | architecture.md 描述與實際行為矛盾、spec §2 設計碼過時 |
| `skip` | 測試編譯通過但未執行（環境不可用） | IT 需要 Docker / 外部服務，開發時跳過 |

**三條規則：**

1. **規格關閉時必須登記。** 實作過程中發現的技術債，在 spec §7 記錄後，同步在 `spec-roadmap.md` 技術債表新增條目。不得只記在 spec 而不索引。
2. **Architecture / development-standards 不符實作的發現，必須在同一 PR 修正。** 文件飄移的修正成本在發現當下最低。來不及修的標記 `drift` 進技術債。
3. **技術債晉升走正常 grill 循環。** 每次 `/planning-spec` 開始前檢視技術債表，可順手清掉的直接處理，需要獨立規格的走 Backlog 晉升流程。

## 11. 禁止的模式

- 繞過 Spring AOT 的執行期反射探索（使用來自設定的字串呼叫 `Class.forName`、未登錄服務的 `ServiceLoader`）。
- 靜態可變狀態。句號。使用 `@Component` 單例。
- 在 Bean 建立時執行 I/O 的 `@Bean` 方法（違反 P5 — 啟動必須在沒有任何 CLI 二進位的情況下成功）。
- 沒有 `throw` 或明確翻譯至領域例外的全捕獲 `catch (Exception e)`。
- 在 `core/domain/GrimoHomePaths` 和 `GrimoApplication.main` 以外的任何地方使用 `System.getenv(...)` / `System.getProperty(...)`。
- **模擬 CLI 子程序** — `Mockito.mock(Process.class)`、存根的 `ProcessBuilder`、偽造的 stdin/stdout 串流。在 `*Test` 中測試純翻譯；在 `*IT` 中以優雅跳過（§7.3–7.5）執行真實二進位。不存在假的中間層。

## 12. 程式碼審查清單（每個 PR 適用）

- [ ] `./gradlew check` 通過（編譯 + 單元測試 + Modulith verify）。
- [ ] 沒有新依賴未在 `architecture.md` 框架依賴表中新增對應欄位。
- [ ] 新領域術語 → glossary 條目（zh-TW + English）。
- [ ] 新公開 API → 測試涵蓋正常路徑 + 錯誤路徑 + 邊界。
- [ ] **CLI / 子程序適配器變更** — `*Test` 涵蓋純翻譯；`*IT` 以 `assumeTrue(cliAvailable && credentialsPresent)` 在 `@BeforeEach` 中執行真實二進位。**不使用** `Mockito.mock(Process…)`。若新增第二個以上的提供者，將共用行為提升至 TCK（§7.6）。
- [ ] 無新的跨模組直接類別引用 — 只使用事件或埠。
- [ ] 當新依賴為有意引入時，更新 `package-info.java` 的 `allowedDependencies`。
- [ ] 若需要新的 `RuntimeHints`，在 `io.github.samzhu.grimo.nativeimage.GrimoRuntimeHints` 中登錄（套件名為 `nativeimage`，因 `native` 是 Java 保留字），並由夜間原生冒煙測試執行。

## 13. Cross-Module Communication（S002 釘定 · 權威單一來源）

> 三種合法跨模組通訊模式 — 其餘均為 Modulith 違規，會被 `ModuleArchitectureTest`（`./gradlew test`）擋下。**本節為單一來源**；`architecture.md` §1 反向引用此節，不重複表述。
>
> 注意：本節是 **Spring Modulith 跨模組規則**（`@NamedInterface` / `allowedDependencies` / events）；模組內部的 hexagonal 分層（`domain/`、`port/in`、`port/out`、`adapter/`）由 §2 規範，兩者不可混用。

| 模式 | 何時用 | Modulith 機制 | `allowedDependencies` 寫法 |
| --- | --- | --- | --- |
| **A · `core` 型別** | 共用領域原語（`SessionId`、`TaskId`、`AgentRole`、…） | 直接 import `io.github.samzhu.grimo.core.domain.*`；`core` 為 `Type.OPEN` 不受 enforcement | **無需宣告** |
| **B · 同步埠呼叫** | 消費者**現在**就需要結果（如 `subagent.DelegateTaskUseCase` 使用 `sandbox` 模組提供的 `Sandbox` 實例並等候） | 出版者把埠放在 `@NamedInterface("api")` 標記的子套件中 | 消費者宣告 `allowedDependencies = { "<publisher> :: api" }` |
| **C · 非同步事件** | **預設模式**。出版者不在乎誰聽 / 想解耦（如 `cli` 發 `CliUnavailable` → `web` 推 SSE toast、`cost` 累計指標） | 出版者把事件 record 放在 `<module>/events/` 並標 `@NamedInterface("events")`；訂閱者用 `@ApplicationModuleListener void on(EventType e)` | 消費者宣告 `allowedDependencies = { "<publisher>::events" }` — 只看得到事件，看不到內部 service |

### 預設與禁止清單

- **預設用事件（模式 C）。** 非同步 + 事件讓出版者可以內部演化而不影響消費者，並支援 PRD P4「廉價未來解耦」（例如將 sub-agent pool 抽成獨立程序對出版者零修改）。
- **`@NamedInterface` 命名約定：** 同步埠用 `"api"`，事件用 `"events"`。其他名稱需在 ADR 中說明。
- **跨模組直接 bean 注入永遠禁止。** 不允許 `@Autowired SomeOtherModuleService` 跨模組界線。只有 named-interface 出版的型別對外可見。
- **不得跨模組引用 `internal/`。** Modulith 在 `internal/` 套件存在後自動強制；無例外。
- **`core` 是唯一的 OPEN 模組。** 任何未來 MVP 模組要翻成 `OPEN` 須走 ADR — `OPEN` 退出 cycle detection，是載荷的保護。

### 一次性違規警報長這樣（S002 §7 紀錄）

```
org.springframework.modulith.core.Violations:
  - Module 'subagent' depends on module 'cli' via
    io.github.samzhu.grimo.subagent.SubagentProbe ->
    io.github.samzhu.grimo.cli.CliMarker. Allowed targets: none.
```

訊息清楚指出兩個模組 + 兩個違規類別 + 「Allowed targets: none.」（消費模組的 `allowedDependencies = {}`）。修法：在 PR 內把消費模組的 `allowedDependencies` 加上 `"<publisher>::api"` 或 `"<publisher>::events"`，並確保被引用的型別位於對應的 `@NamedInterface` 套件中。
