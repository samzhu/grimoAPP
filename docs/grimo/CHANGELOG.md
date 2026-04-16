# 變更日誌

所有已出貨的規格均列於此處，遵循 Keep a Changelog 慣例。

## [未發布]

### 新增

- **S001 — 核心領域原語 + GrimoHomePaths**（2026-04-16）。新增 `io.github.samzhu.grimo.core.domain` 套件：四個帶型別的 id records（`SessionId`、`TurnId`、`TaskId`、`CorrelationId`）、兩個列舉（`AgentRole { MAIN, SUB, JURY_MEMBER }`、`ProviderId { CLAUDE, CODEX, GEMINI }`）、一個內建的 `NanoIds` 產生器（21 字元 URL 安全 id，約 30 LOC，零新執行期依賴），以及 `GrimoHomePaths` — 一個針對 `~/.grimo/{memory, skills, sessions, worktrees, logs, config, db}` 的靜態解析器，支援 `grimo.home` JVM 系統屬性與 `$GRIMO_HOME` 環境變數覆寫（優先順序：property → env → `$HOME/.grimo`）。ArchUnit 的 `DomainArchitectureTest` 守護 AC-3（`core.domain` 中無 Spring / `jakarta.annotation` import）。所有領域型別均為不可變 records 或純列舉，零 Spring 注解。AC-4（`Cost` 算術）延後至擁有成本遙測的規格（S019）。詳見 `docs/grimo/specs/archive/2026-04-16-S001-core-domain-primitives.md` 第 7 節的關鍵使用模式與測試配方。
- **S000 — 專案初始化**（2026-04-16）。Spring Boot 4.0.5 模組化骨架，運行於 JDK 25，使用 Gradle 9.4.1 wrapper 與 GraalVM native 外掛 0.11.5（`graalvmNative { imageName = "grimo"; buildArgs += "--no-fallback" }`）。透過 `spring.threads.virtual.enabled=true` 啟用 virtual thread 執行器。Hello-world `GrimoApplication` 加上兩個 JUnit 測試（`contextLoads`、`virtualThreadsEnabled`）。`.gitignore` 強化（macOS / `~/.grimo/` 雜訊）。頂層 `README.md` 記錄原生可執行檔打包路徑。AC-3（自動格式化閘門）與 AC-4（實際原生二進位執行）有意延後 — 詳見 `docs/grimo/specs/archive/2026-04-16-S000-project-init.md` 第 7 節。
