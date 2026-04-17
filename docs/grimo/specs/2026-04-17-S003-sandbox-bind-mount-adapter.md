# S003: Sandbox SPI + bind-mount 適配器

> Spec: S003 | Size: M (13) | Status: ⏳ Design
> Date: 2026-04-17

---

## 1. Goal

讓 Java 程式碼可透過 `agent-sandbox-core` 0.9.1 的 `Sandbox` SPI 啟動 Docker 容器、bind-mount 主機目錄至容器 `/work`、對容器執行命令（多次互動）、最終關閉容器 — 全部以容器 ID 進行生命週期管理。

S003 是 M1（容器操作）的唯一規格，為下游所有容器化 CLI 呼叫（S004–S005）、子代理委派（S008–S010）及 Skill 注入（S012）提供基礎設施。

**範圍內：**
- 採用 `agent-sandbox-core` `Sandbox` SPI（`exec`、`close`、`files`、`workDir`）
- 自訂 `BindMountSandbox` 實作，內部包裝 Testcontainers `GenericContainer` + `withFileSystemBind`
- `SandboxManager` 介面 — 以容器 ID 管理多個並行沙箱的生命週期
- 整合測試（`@DisabledInNativeImage`）

**範圍外：**
- 容器安全加固（`--read-only`、`--cap-drop`、`--network` allowlist）→ S010
- `grimo-runtime` 映像建置 → S004
- CLI 呼叫適配器 → S005
- 子代理排程策略（max-concurrent、shutdown 清理）→ S010
- `ProcessBuilderSandboxAdapter`（native-safe fallback）→ Backlog 原生加固

## 2. Approach

### 2.1 Decisions

| # | Decision | Chosen | Why |
| --- | --- | --- | --- |
| D1 | **沙箱抽象層** | `agent-sandbox-core` 0.9.1 `Sandbox` SPI | 設計良好的介面（`exec(ExecSpec) → ExecResult`、`close()`、`files()`、`workDir()`），含配套型別 `ExecSpec`、`ExecResult`、`SandboxFiles`、`ExecSpecCustomizer`。直接採用，不自定義 `SandboxPort`。**2026-04-17 修訂 D9。** 來源：[agent-sandbox GitHub](https://github.com/spring-ai-community/agent-sandbox)；[文件](https://springaicommunity.mintlify.app/projects/incubating/agent-sandbox) |
| D2 | **bind-mount 實作** | `BindMountSandbox implements Sandbox`，內部使用 `GenericContainer` + `withFileSystemBind(hostPath, "/work", READ_WRITE)` + `withCommand("sleep","infinity")` | `DockerSandbox` 0.9.1 在建構子中啟動容器，無法在 `start()` 前注入 bind-mount（PRD D9 spike 確認）。三參數 `withFileSystemBind` 在 Testcontainers 1.20.4 **未棄用**（兩參數版本已棄用）。來源：[GenericContainer Javadoc](https://javadoc.io/static/org.testcontainers/testcontainers/1.20.4/org/testcontainers/containers/GenericContainer.html) |
| D3 | **生命週期管理** | `SandboxManager`（create / get / close / listActive），以容器 ID 追蹤 | 沙箱是長期存活的 CLI agent 隔離層，可多次 `exec()` 互動、同時多個並存（如兩個不同模型的 Gemini）。以 `GenericContainer.getContainerId()` 作為 handle。 |
| D4 | **exec 機制** | 委派 `container.execInContainer(String...)`，包裝 bash script 注入 env vars | 復用 `DockerSandbox` 的 exec 慣例（`bash -lc` + env export + `exec "$@"`）。`execInContainer` 回傳 `Container.ExecResult`，轉譯為 `agent-sandbox-core` 的 `ExecResult` record。**無 stdin 支援**——命令必須非互動式。來源：[Testcontainers executing commands](https://java.testcontainers.org/features/commands/) |
| D5 | **SandboxFiles 實作** | Host 端檔案操作（bind-mount 雙向同步） | bind-mounted 目錄在主機和容器間雙向同步。`SandboxFiles` 操作可直接走主機 `java.nio.file` API，避免繞道 `execInContainer`，效能更好且更可靠。 |
| D6 | **安全加固** | S003 不實作 | architecture.md §9 的安全措施（`--read-only`、`--cap-drop=ALL`、`--security-opt`、`--network` allowlist）全部透過 `withCreateContainerCmdModifier` 設定，由 S010 落地。注意 Testcontainers issue [#8618](https://github.com/testcontainers/testcontainers-java/issues/8618)：`withCreateContainerCmdModifier` + 並行啟動可能 hang — S010 需注意。 |
| D7 | **測試映像** | `alpine:3.21` | 輕量級，足以驗證 bind-mount + exec + close。`SandboxConfig.imageName` 參數化，S004 落地後下游傳入 `grimo-runtime:<version>`。 |
| D8 | **@NamedInterface 套件** | `sandbox/api/`（扁平子套件） | Spring Modulith 2.0.5 官方範例僅展示一層深度子套件。深層路徑（如 `application/port/out/`）的 NamedInterface 偵測可靠性未被文件保證。扁平 `sandbox/api/` 最安全。來源：[Spring Modulith fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html) |
| D9 | **`allowedDependencies` 格式** | `"sandbox :: api"`（含空格） | 官方文件範例用雙冒號含空格。來源：同上 |
| D10 | **Ryuk 清理** | 依賴 Testcontainers Ryuk 作為 JVM shutdown 安全網 | `GenericContainer.close()` = stop + remove。Ryuk 在 JVM 異常退出時自動回收遺漏容器。不需額外 shutdown hook。來源：[Testcontainers lifecycle](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/) |

### 2.2 Challenges Considered

- **「為何不直接用 `DockerSandbox`？」** 建構子啟動容器，無法在啟動前注入 bind-mount。這是 PRD D9 的 spike 結論（2026-04-16）。`DockerSandbox.Builder` 有 `.image()`、`.withFile()`、`.customizer()` 但**無 `.mount()` 或 `.withFileSystemBind()`**。來源：agent-sandbox 0.9.1 GitHub 原始碼。
- **「為何不自定義 `SandboxPort`？」** `agent-sandbox-core` 的 `Sandbox` SPI 已涵蓋我們需要的所有操作（exec、close、files、workDir）。自定義埠是重新發明輪子，且失去與 Spring AI Community 生態的相容性。
- **「為何不用 `DockerSandbox` 做 exec 邏輯、只自己處理容器建立？」** 組合比抄寫更脆弱——`DockerSandbox` 在建構子中 hard-wire container creation，無法只借用 exec 邏輯而不觸發 container start。自行實作 exec 委派（直接呼叫 `container.execInContainer`）只需約 30 行程式碼。
- **「execInContainer 無 stdin — 下游怎麼辦？」** S003 只需非互動式命令。S005（CLI 適配器）需要 stdin 導入 prompt 給容器化 CLI — 屆時需改用 docker-java `ExecStartCmd` 低層 API 或 `ProcessBuilder`（`docker exec -i`）。S003 不阻塞此路徑。
- **「ConcurrentHashMap 追蹤夠嗎？」** S003 的 `SandboxManager` 預設用 `ConcurrentHashMap<String, BindMountSandbox>` 追蹤存活容器。並行安全、O(1) 查詢。S010 加入排程策略（max-concurrent semaphore、shutdown 有序關閉）時擴充此結構。

### 2.3 Research Citations (load-bearing)

**Testcontainers 1.20.4：**
- `withFileSystemBind(host, container, BindMode)` 三參數版 — 未棄用：[GenericContainer Javadoc](https://javadoc.io/static/org.testcontainers/testcontainers/1.20.4/org/testcontainers/containers/GenericContainer.html)
- `execInContainer` 回傳 `Container.ExecResult`（stdout/stderr/exitCode）；無 stdin 變體：[Executing commands](https://java.testcontainers.org/features/commands/)
- `close()` = stop + remove；`GenericContainer` implements `Closeable`：[Manual lifecycle](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/)
- 安全設定全走 `withCreateContainerCmdModifier`：[Advanced options](https://java.testcontainers.org/features/advanced_options/)
- 並行容器獨立運行；issue #8618 `withCreateContainerCmdModifier` + 並行 start 可能 hang：[Discussion #6406](https://github.com/testcontainers/testcontainers-java/discussions/6406)、[Issue #8618](https://github.com/testcontainers/testcontainers-java/issues/8618)
- `withFileSystemBind` host 路徑不存在時建立 root-owned 目錄（#8871）；掛載已存在路徑不受影響：[Issue #8871](https://github.com/testcontainers/testcontainers-java/issues/8871)

**agent-sandbox-core 0.9.1：**
- `Sandbox` SPI：`exec(ExecSpec) → ExecResult`、`close()`、`files() → SandboxFiles`、`workDir() → Path`、`isClosed()`：[GitHub](https://github.com/spring-ai-community/agent-sandbox)
- `DockerSandbox` 建構子啟動容器，Builder 無 bind-mount 方法：同上
- `ExecSpec`：不可變，含 command/env/timeout + builder + `shellCommand()` sentinel：同上
- `ExecResult` record：exitCode/stdout/stderr/duration + `success()`/`mergedLog()`：同上
- `SandboxFiles`：create/read/list/delete + `and()` chain back：同上
- `ExecSpecCustomizer`：cross-cutting hook（chain/identity/when）：同上

**Spring Modulith 2.0.5：**
- `@NamedInterface` 放在子套件 `package-info.java`（非模組根）：[Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- `allowedDependencies` 格式 `"module :: name"`：同上
- 官方範例只展示一層深度子套件；深層套件偵測可靠性未保證：同上

## 3. SBE Acceptance Criteria

**Acceptance-verification command:** `./gradlew test`
Pass condition: all JUnit tests with `@DisplayName` beginning `[S003] AC-<N>` are green.整合測試標記 `@DisabledInNativeImage`（Testcontainers 為 JVM 專用）。

### AC-1: bind-mount 主機目錄至容器 `/work`

```
Given  一個已存在的主機目錄 hostDir，其中包含檔案 seed.txt
When   SandboxManager.create(new SandboxConfig("alpine:3.21", hostDir, "/work"))
Then   回傳的 Sandbox 實例不為 null
And    sandbox.exec(ExecSpec.of("cat", "/work/seed.txt")) 的 stdout
       包含 seed.txt 的內容
And    sandbox.exec(ExecSpec.of("ls", "-la", "/work")) 的 stdout
       包含 "seed.txt"
```

### AC-2: 容器內寫入即時反映於主機

```
Given  一個已建立的 Sandbox，bind-mount hostDir → /work
When   sandbox.exec(ExecSpec.of("sh", "-c", "echo hello > /work/new.txt"))
Then   主機端 hostDir/new.txt 存在
And    其內容為 "hello\n"
```

### AC-3: 兩個並行沙箱互不可見

```
Given  hostDirA 和 hostDirB 是兩個不同的主機目錄
When   sandboxA = manager.create(configA) 且 sandboxB = manager.create(configB)
And    sandboxA.exec(ExecSpec.of("sh", "-c", "echo A > /work/who.txt"))
And    sandboxB.exec(ExecSpec.of("cat", "/work/who.txt"))
Then   sandboxB 的 exec 回傳 exitCode ≠ 0（檔案不存在）
And    hostDirB 中不存在 who.txt
And    manager.listActive().size() == 2
```

### AC-4: close 停止並移除容器；主機目錄保留

```
Given  一個已建立的 Sandbox，containerId = manager.listActive().get(0)
And    sandbox.exec(ExecSpec.of("sh", "-c", "echo persist > /work/keep.txt"))
When   manager.close(containerId)
Then   manager.get(containerId) 回傳 Optional.empty()
And    manager.listActive() 不包含 containerId
And    主機端 hostDir/keep.txt 仍存在，內容為 "persist\n"
```

## 4. Interface / API Design

### 4.1 Package Layout

```
io.github.samzhu.grimo.sandbox
├── package-info.java                        # @ApplicationModule(displayName="Grimo :: Sandbox",
│                                            #   allowedDependencies={})
├── api/
│   ├── package-info.java                    # @NamedInterface("api")
│   ├── SandboxManager.java                  # interface — 生命週期管理
│   └── SandboxConfig.java                   # record — 建立參數
└── internal/
    ├── BindMountSandbox.java                # implements Sandbox — GenericContainer 包裝
    ├── HostMountedSandboxFiles.java         # implements SandboxFiles — host 端檔案操作
    ├── TestcontainersSandboxManager.java    # implements SandboxManager — @Service
    └── SandboxModuleConfig.java             # @Configuration — bean 註冊
```

### 4.2 `SandboxManager` — 生命週期管理介面

```java
package io.github.samzhu.grimo.sandbox.api;

import java.util.List;
import java.util.Optional;
import org.springaicommunity.sandbox.Sandbox;

/**
 * 管理多個並行沙箱的生命週期。每個沙箱以 Docker 容器 ID 識別。
 * 沙箱是長期存活的 CLI agent 隔離層，支援多次 exec() 互動。
 *
 * <p>設計說明：以容器 ID（而非 TaskId）作為 handle，因為同一任務
 * 可能建立多個沙箱（如同時使用不同模型），且沙箱生命週期不一定
 * 與任務生命週期一致。
 */
public interface SandboxManager {

    /**
     * 建立並啟動一個新的沙箱容器。
     *
     * @param config 映像名稱、主機掛載路徑、容器掛載路徑
     * @return 已啟動的 Sandbox 實例（可多次 exec）
     * @throws org.springaicommunity.sandbox.SandboxException 容器啟動失敗
     */
    Sandbox create(SandboxConfig config);

    /**
     * 依容器 ID 查詢存活的沙箱。
     *
     * @param containerId Docker 容器 ID
     * @return 若存活則 Optional.of(sandbox)；否則 Optional.empty()
     */
    Optional<Sandbox> get(String containerId);

    /**
     * 關閉並移除指定容器。主機掛載目錄保留。
     * 對已關閉的 containerId 呼叫為 no-op。
     *
     * @param containerId Docker 容器 ID
     */
    void close(String containerId);

    /**
     * 列出所有存活沙箱的容器 ID。
     *
     * @return 不可變的容器 ID 列表
     */
    List<String> listActive();
}
```

### 4.3 `SandboxConfig` — 建立參數

```java
package io.github.samzhu.grimo.sandbox.api;

import java.nio.file.Path;

/**
 * 沙箱建立參數。映像名稱由呼叫者決定（S003 測試用 alpine:3.21，
 * S004 落地後改用 grimo-runtime）。
 *
 * @param imageName         Docker 映像名稱（如 "alpine:3.21"）
 * @param hostMountPath     主機端掛載目錄（必須已存在）
 * @param containerMountPath 容器內掛載路徑（慣例 "/work"）
 */
public record SandboxConfig(
    String imageName,
    Path hostMountPath,
    String containerMountPath
) {
    public SandboxConfig {
        if (imageName == null || imageName.isBlank()) {
            throw new IllegalArgumentException("imageName must not be blank");
        }
        if (hostMountPath == null) {
            throw new IllegalArgumentException("hostMountPath must not be null");
        }
        if (containerMountPath == null || containerMountPath.isBlank()) {
            throw new IllegalArgumentException("containerMountPath must not be blank");
        }
    }
}
```

### 4.4 `BindMountSandbox` — 內部實作（概要）

```java
package io.github.samzhu.grimo.sandbox.internal;

// implements org.springaicommunity.sandbox.Sandbox
// 內部持有 GenericContainer<?>，在建構時：
//   new GenericContainer<>(imageName)
//       .withWorkingDirectory(containerMountPath)
//       .withFileSystemBind(hostMountPath.toString(),
//                           containerMountPath,
//                           BindMode.READ_WRITE)
//       .withCommand("sleep", "infinity")
//       .start();
//
// exec(ExecSpec) — 套用 ExecSpecCustomizer chain，
//   將 command + env 包裝為 bash script，
//   委派 container.execInContainer(...)，
//   回傳 ExecResult(exitCode, stdout, stderr, duration)
//
// files() — 回傳 HostMountedSandboxFiles（直接走主機 NIO）
// workDir() — Path.of(containerMountPath)
// close() — container.stop()（stop + remove）
// isClosed() — closed flag
// getContainerId() — container.getContainerId()
```

### 4.5 `TestcontainersSandboxManager` — 內部實作（概要）

```java
package io.github.samzhu.grimo.sandbox.internal;

// @Service, implements SandboxManager
// 內部：ConcurrentHashMap<String, BindMountSandbox> registry
//
// create(config) — new BindMountSandbox(config) → 取得 containerId
//   → registry.put(containerId, sandbox) → return sandbox
// get(containerId) — Optional.ofNullable(registry.get(containerId))
// close(containerId) — registry.remove(containerId)?.close()
// listActive() — List.copyOf(registry.keySet())
```

### 4.6 跨模組通訊

| 消費者模組 | 需要的型別 | `allowedDependencies` 變更 |
| --- | --- | --- |
| `subagent` | `SandboxManager`、`SandboxConfig` | `"sandbox :: api"` |
| `cli` | `SandboxManager`、`SandboxConfig` | `"sandbox :: api"` |

消費者透過 Spring DI 注入 `SandboxManager`，呼叫 `create()` 取得 `Sandbox`（`agent-sandbox-core` SPI），後續直接使用 `Sandbox.exec()` / `.files()` / `.close()` — 無需額外依賴 sandbox 模組的內部型別。

`agent-sandbox-core` 是 library dependency（非 Modulith 模組），所有模組均可直接 import `org.springaicommunity.sandbox.*`。

### 4.7 No Custom Domain Types

S003 不在 `sandbox/domain/` 建立任何型別。領域型別完全由 `agent-sandbox-core` 提供：

| 型別 | 來源 |
| --- | --- |
| `Sandbox` | `org.springaicommunity.sandbox.Sandbox` |
| `ExecSpec` | `org.springaicommunity.sandbox.ExecSpec` |
| `ExecResult` | `org.springaicommunity.sandbox.ExecResult` |
| `SandboxFiles` | `org.springaicommunity.sandbox.SandboxFiles` |
| `SandboxException` | `org.springaicommunity.sandbox.SandboxException` |
| `ExecSpecCustomizer` | `org.springaicommunity.sandbox.ExecSpecCustomizer` |

唯二的 Grimo 自有型別：`SandboxManager`（介面）與 `SandboxConfig`（record），均在 `sandbox/api/`。

## 5. File Plan

### New production files

All under `src/main/java/io/github/samzhu/grimo/sandbox/`:

| File | Action | Description |
| --- | --- | --- |
| `api/package-info.java` | new | `@NamedInterface("api")` — sandbox 模組的公開 API 套件 |
| `api/SandboxManager.java` | new | 生命週期管理介面（create/get/close/listActive） |
| `api/SandboxConfig.java` | new | 建立參數 record（imageName, hostMountPath, containerMountPath） |
| `internal/BindMountSandbox.java` | new | `implements Sandbox` — GenericContainer + bind-mount |
| `internal/HostMountedSandboxFiles.java` | new | `implements SandboxFiles` — host 端 NIO 檔案操作 |
| `internal/TestcontainersSandboxManager.java` | new | `@Service implements SandboxManager` — ConcurrentHashMap 追蹤 |
| `internal/SandboxModuleConfig.java` | new | `@Configuration` — bean 註冊（如需要） |

### Modified production files

| File | Action | Description |
| --- | --- | --- |
| `sandbox/package-info.java` | modify | 更新 Javadoc（已在 doc-sync 中完成） |
| `build.gradle.kts` | modify | 新增 `implementation("org.springaicommunity:agent-sandbox-core:0.9.1")` + `implementation("org.testcontainers:testcontainers:1.20.4")` + `testImplementation("org.testcontainers:junit-jupiter:1.20.4")` |

### New test files

Under `src/test/java/io/github/samzhu/grimo/sandbox/`:

| File | Action | Description |
| --- | --- | --- |
| `api/SandboxConfigTest.java` | new | T0 unit：record 驗證（null/blank 參數） |
| `internal/BindMountSandboxIT.java` | new | T3 contract：AC-1, AC-2（bind-mount + exec + 雙向同步）；`@DisabledInNativeImage` |
| `internal/SandboxManagerIT.java` | new | T3 contract：AC-3, AC-4（並行隔離 + close 清理 + listActive）；`@DisabledInNativeImage` |

### Doc-sync

| File | Action | Description |
| --- | --- | --- |
| `docs/grimo/specs/spec-roadmap.md` | modify | S003 status → `⏳ Design` |

### Not touched

- `agent-sandbox-docker` — S003 不引入此依賴
- `sandbox/domain/` — 不建立；領域型別由 `agent-sandbox-core` 提供
- `sandbox/adapter/` — 不使用六邊形 adapter 子套件；改用 Modulith 慣例的 `api/` + `internal/`
- Archived specs — 永久記錄，不修改
- `core/` — 無新型別
- `application.yml` — 無新屬性

---

*§6 Task Plan 與 §7 Implementation Results 由 `/planning-tasks` 階段填入。*
