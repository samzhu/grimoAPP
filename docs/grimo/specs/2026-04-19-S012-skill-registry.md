# S012: Skill 登錄檔

> Spec: S012 | Size: XS (8) | Status: ✅ Done
> Date: 2026-04-19

---

## 1. Goal

為 Grimo 建立 Skill 登錄檔。掃描 `~/.grimo/skills/*/SKILL.md`，遵循 [agentskills.io](https://agentskills.io/specification) 開放標準驗證格式。建立 `SkillRegistryUseCase`，公開 `list()`、`listEnabled()`、`enable(name)`、`disable(name)`、`get(name) → Optional<Skill>`。啟用/停用狀態持久化至 `~/.grimo/skills/.state.json`。

**框架整合策略。** 重寫 `spring-ai-agent-utils` 0.7.0 的 `Skills` + `MarkdownParser`，保持相同方法簽名，內部改用 SnakeYAML 解析以支援完整 agentskills.io YAML frontmatter（巢狀 metadata、YAML list、多行 scalar）。產出型別為框架原生的 `SkillsTool.Skill` record，可直接被 `SkillsFunction` → `ToolCallback` → `ChatClient` 消費，無需型別轉換。

**依賴。** S001（`GrimoHomePaths.skills()`，已出貨）。無程式碼層級阻塞。

**不包含。** Skill 注入至容器（S013）。Skill 複雜度評估（S015，另行規劃）。

## 2. Approach

### 2.1 框架整合：重寫 Skills + MarkdownParser，保留介面

`spring-ai-agent-utils` 0.7.0 的 `MarkdownParser` 為自製逐行 split 解析器，不支援巢狀 YAML（`metadata:` map、`allowed-tools:` list、`description: >` 多行 scalar 全部解析失敗）。`Skills` 類全 static、無 SPI、`MarkdownParser` 硬編碼在 private 方法中無法替換。

Grimo 在 `skills/internal/` 重寫這兩個類別，保持相同的公開方法簽名：

| 類別 | 原版問題 | Grimo 版改進 |
|------|---------|-------------|
| `MarkdownParser(String)` | 自製 flat parser — `metadata:` 值為空字串、YAML list 被跳過 | 內部用 **SnakeYAML**（Boot classpath 已有 `org.yaml:snakeyaml`），正確解析巢狀 Map/List/多行 scalar |
| `Skills.loadDirectory(String)` | 目錄不存在/檔案讀取失敗直接拋 `RuntimeException` 全部炸掉 | 單檔錯誤 → **跳過 + log.warn**（AC-2）；目錄不存在 → 空 list + log.warn |

**產出型別不變：** `SkillsTool.Skill` record（`org.springaicommunity.agent.tools.SkillsTool.Skill`）。三個欄位 `basePath`（String）、`frontMatter`（Map<String, Object>）、`content`（String）為 public record 可直接 `new Skill(basePath, frontMatter, content)` 建構。

**整合路徑圖：**

```
Grimo MarkdownParser（SnakeYAML）
  ↓
Grimo Skills.loadDirectory()
  ↓
List<SkillsTool.Skill>        ← 框架原生型別
  ↓
SkillRegistryUseCase          ← S012（list/enable/disable/get）
  ↓                     ↓
.state.json          SkillsFunction(map)  ← 未來：ChatClient ToolCallback 整合
                        ↓
                    ToolCallback          ← Spring AI 核心介面
```

### 2.2 agentskills.io 標準相容

遵循 [agentskills.io specification](https://agentskills.io/specification)。此為業界事實標準，Claude Code / OpenAI Codex / VS Code Copilot / Gemini CLI / Hermes 等 26+ 平台相容。

**SKILL.md 標準格式：**

```yaml
---
name: hello                    # 必填，1-64 字元，小寫英數字+連字號
description: "A greeting skill" # 必填，1-1024 字元
license: MIT                   # 選用
compatibility: "macOS, Linux"  # 選用，1-500 字元
metadata:                      # 選用，任意 key-value（S015 寫入此處）
  author: samzhu
  version: 1.0.0
allowed-tools:                 # 選用（實驗性）
  - Read
  - Glob
---
# Skill 指令內容（Markdown body）
```

**驗證規則（源自規範）：**

| 規則 | 來源 |
|------|------|
| `name` 必填，1-64 字元，僅 `[a-z0-9]` + `-`，不可 `-` 開頭/結尾，不可 `--` | agentskills.io §name |
| `name` 必須與父目錄名稱一致 | agentskills.io §name |
| `description` 必填，1-1024 字元，非空 | agentskills.io §description |
| `metadata` 選填，任意 key-value 映射 | agentskills.io §metadata |

**無效 SKILL.md 定義：** 無 frontmatter、缺 `name` 或 `description`、`name` 格式不符、`name` 與目錄名不一致、或檔案為空。記錄 WARN 日誌並跳過（AC-2）。

**新發現的 Skill 預設啟用**（opt-out 模式）。使用者放入 SKILL.md = 想要使用。

### 2.3 Research Citations

**agentskills.io：**
- `name`（1-64, lowercase alphanum + hyphen）+ `description`（1-1024）為唯二必填欄位。`metadata` 為自由 key-value 擴充點。26+ 平台相容。[source](https://agentskills.io/specification)

**spring-ai-agent-utils 0.7.0（v0.7.0 tag SHA: b032745）：**
- `SkillsTool.Skill`：FQN `org.springaicommunity.agent.tools.SkillsTool.Skill`，`public static record Skill(String basePath, Map<String, Object> frontMatter, String content)`。`name()` 從 frontMatter 取。`toXml()` 輸出所有 frontMatter 至 XML。[source](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.7.0/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/SkillsTool.java)
- `Skills`：全 `public static` 方法，無 SPI，無 protected 方法。`loadDirectory()` 內部硬編碼 `new MarkdownParser()`。目錄不存在/讀取失敗拋 `RuntimeException`。[source](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.7.0/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/utils/Skills.java)
- `MarkdownParser`：自製逐行 split，不用 SnakeYAML。僅支援 flat key:value。`Map<String, Object>` 值實際全為 `String`。YAML list/nested map/multi-line scalar 均無法解析。[source](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.7.0/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/utils/MarkdownParser.java)
- `SkillsFunction`：`implements Function<SkillsInput, String>`，建構子接受 `Map<String, Skill>`。Grimo 填充此 map 即可產出 `ToolCallback`。[source](同上 SkillsTool.java)
- 傳遞依賴：`spring-ai-client-chat` 為 `provided`（不傳遞）；`spring-web` 為 `provided`；`flexmark-html2md-converter` 0.64.8 會傳遞。[source](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.7.0/spring-ai-agent-utils/pom.xml)
- 擴展點：`SubagentDefinition`/`SubagentExecutor`/`SubagentResolver`（common 模組）為正式 SPI。`TaskRepository` 為公開介面。`ToolCallback`/`ToolCallbackProvider`（Spring AI core）為生態整合點。

**Claude Code skills：**
- 遵循 agentskills.io + 專有擴充（`model`、`effort`、`context: fork`、`hooks`、`paths`、`when_to_use`）。Personal skills 放在 `~/.claude/skills/<name>/SKILL.md`。[source](https://code.claude.com/docs/en/skills)

**Hermes Agent skills：**
- 相容 agentskills.io。`version`/`author`/`license` 為頂層必填。`metadata.hermes` 命名空間放平台擴充。Markdown body 要求固定章節（When to Use / Quick Reference / Procedure / Pitfalls / Verification）。[source](https://hermes-agent.nousresearch.com/docs/developer-guide/creating-skills)

**agent-client 0.12.2：** 零 skill 管理能力。全文搜尋確認無 `skill` 引用。

**Spring AI core Skill：** `AnthropicSkill` 是 Anthropic API 的 cloud-hosted 能力（xlsx/pdf 生成），與本地 SKILL.md prompt 注入機制完全不同。兩套概念無繼承關係。

### 2.4 模組歸屬

`skills` 模組（已有 `package-info.java` 空殼）。`allowedDependencies` 更新為 `{ "core" }` 以引用 `GrimoHomePaths`。

**package-info.java Javadoc 飄移修正：** 現有 Javadoc 將 S011 誤標為 SkillRegistryUseCase、S012 誤標為 Skill injection。正確對應：S012 = Skill 登錄檔、S013 = Skill 注入。S012 出貨時修正。

Hexagonal 分層：

```
skills/
├── domain/
│   └── SkillEntry.java                   # domain record（Skill + enabled 狀態）
├── application/
│   ├── port/in/
│   │   └── SkillRegistryUseCase.java     # 用例介面
│   ├── port/out/
│   │   └── SkillStorePort.java           # 出站埠（檔案系統抽象）
│   └── service/
│       └── SkillRegistryService.java     # @Service 用例實作
├── adapter/out/
│   └── FileSystemSkillStoreAdapter.java  # Grimo Skills + .state.json
└── internal/
    ├── MarkdownParser.java               # SnakeYAML 版，同介面
    └── Skills.java                       # graceful 版，同介面，產出 SkillsTool.Skill
```

### 2.5 .state.json 格式

```json
{
  "version": 1,
  "skills": {
    "hello": { "enabled": true },
    "deploy": { "enabled": false }
  }
}
```

`version` 欄位預留未來格式升級。新發現的 Skill（不在 `.state.json` 中）預設 `enabled: true`。S015 未來可在 skill 物件下擴充 `complexity`、`recommendedModel` 等計算欄位。

### 2.6 信心分類

| 決策 | 信心 | 依據 |
|------|------|------|
| `SkillsTool.Skill` 可直接 new 建構 | **Validated** | public record，三欄位均為 constructor 參數 |
| SnakeYAML 在 Boot classpath | **Validated** | `spring-boot-starter` 傳遞引入 `org.yaml:snakeyaml` |
| `SkillsFunction(Map<String, Skill>)` 可手動建構 | **Validated** | public constructor，接受任意來源的 Skill map |
| Grimo MarkdownParser 以 SnakeYAML 解析 agentskills.io frontmatter | **Hypothesis** | SnakeYAML 支援完整 YAML 1.1，但需 POC 驗證 frontmatter 分離邏輯（`---` 分隔符處理） |

**POC: required** — SnakeYAML 解析 SKILL.md frontmatter 的 `---` 分隔符處理需驗證。SnakeYAML 的 `Yaml.load()` 是否自動處理 `---` 開頭/結尾，或需手動擷取 frontmatter 區段再餵入。

---

## 3. SBE Acceptance Criteria

**驗證命令：**

```
Run: ./gradlew test
Pass: all tests carrying S012 AC ids are green.
```

---

### AC-1: Skill 出現在 list() 中

```
Given  ~/.grimo/skills/hello/SKILL.md 存在且格式正確
       frontmatter 含 name: hello, description: "A greeting skill"
       frontmatter 含 metadata: { author: samzhu, version: "1.0.0" }
When   SkillRegistryUseCase.list()
Then   回傳的 List 包含 name="hello" 的 SkillEntry
And    該 SkillEntry 的 skill.frontMatter 含 description = "A greeting skill"
And    該 SkillEntry 的 skill.frontMatter 含 metadata map 有 author = "samzhu"
And    該 SkillEntry 的 enabled = true（預設）
```

### AC-2: 無效 SKILL.md 記錄警告並跳過

```
Given  ~/.grimo/skills/broken/SKILL.md 存在但無 YAML frontmatter
And    ~/.grimo/skills/hello/SKILL.md 存在且格式正確
When   SkillRegistryUseCase.list()
Then   回傳的 List 僅包含 "hello"
And    日誌中包含 WARN 記錄，提及 "broken"
And    應用程式不崩潰
```

### AC-3: disable() 持久化跨重啟

```
Given  ~/.grimo/skills/hello/SKILL.md 存在且格式正確
When   SkillRegistryUseCase.disable("hello")
Then   ~/.grimo/skills/.state.json 記錄 hello.enabled = false
And    重新建構 SkillRegistryUseCase 後 list() 中 hello 的 enabled = false
And    SkillRegistryUseCase.listEnabled() 不包含 "hello"
```

---

## 4. Interface / API Design

### 4.1 SkillEntry（domain record）

```java
package io.github.samzhu.grimo.skills.domain;

import org.springaicommunity.agent.tools.SkillsTool;

/**
 * Grimo 的 Skill 登錄項。包裝框架原生 SkillsTool.Skill + 啟用狀態。
 *
 * <p>skill 欄位直接使用 agent-utils 的 record 型別，
 * 確保可被 SkillsFunction / ToolCallback / ChatClient 直接消費。
 *
 * <p>Zero Spring annotations — pure domain.
 */
public record SkillEntry(
    SkillsTool.Skill skill,   // 框架原生型別
    boolean enabled           // 來自 .state.json，預設 true
) {
    /** 便利方法：取 skill name。 */
    public String name() { return skill.name(); }
}
```

### 4.2 SkillRegistryUseCase（入站埠）

```java
package io.github.samzhu.grimo.skills.application.port.in;

import io.github.samzhu.grimo.skills.domain.SkillEntry;
import java.util.List;
import java.util.Optional;

public interface SkillRegistryUseCase {

    /** 所有已發現的 skill，含啟用/停用狀態。 */
    List<SkillEntry> list();

    /** 僅已啟用的 skill（S013 注入時使用）。 */
    List<SkillEntry> listEnabled();

    /** 依名稱查詢單一 skill。 */
    Optional<SkillEntry> get(String name);

    /** 啟用 skill 並持久化至 .state.json。 */
    void enable(String name);

    /** 停用 skill 並持久化至 .state.json。 */
    void disable(String name);
}
```

### 4.3 SkillStorePort（出站埠）

```java
package io.github.samzhu.grimo.skills.application.port.out;

import io.github.samzhu.grimo.skills.domain.SkillEntry;
import java.util.List;
import java.util.Map;

public interface SkillStorePort {

    /** 掃描 skills 目錄 + 合併 .state.json 中的啟用狀態。 */
    List<SkillEntry> loadAll();

    /** 寫入 .state.json。key = skill name, value = enabled。 */
    void saveState(Map<String, Boolean> enabledMap);
}
```

### 4.4 Grimo MarkdownParser（internal，同介面）

```java
package io.github.samzhu.grimo.skills.internal;

import org.yaml.snakeyaml.Yaml;
import java.util.Map;

/**
 * SnakeYAML-backed SKILL.md parser。
 * 保持與 agent-utils MarkdownParser 相同的公開介面，
 * 但支援完整 YAML 1.1（巢狀 map、list、multi-line scalar）。
 */
public class MarkdownParser {

    private final Map<String, Object> frontMatter;
    private final String content;

    public MarkdownParser(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            this.frontMatter = Map.of();
            this.content = "";
            return;
        }
        // 手動擷取 --- 之間的 YAML 區段
        String trimmed = markdown.strip();
        if (!trimmed.startsWith("---")) {
            this.frontMatter = Map.of();
            this.content = markdown.strip();
            return;
        }
        int endIndex = trimmed.indexOf("\n---", 3);
        if (endIndex == -1) {
            this.frontMatter = Map.of();
            this.content = markdown.strip();
            return;
        }
        String yamlBlock = trimmed.substring(3, endIndex).strip();
        String body = trimmed.substring(endIndex + 4).strip(); // skip \n---

        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = yaml.loadAs(yamlBlock, Map.class);
        this.frontMatter = (parsed != null) ? parsed : Map.of();
        this.content = body;
    }

    public Map<String, Object> getFrontMatter() {
        return new java.util.HashMap<>(frontMatter);
    }

    public String getContent() {
        return content;
    }
}
```

### 4.5 Grimo Skills（internal，同介面 + graceful 錯誤處理）

```java
package io.github.samzhu.grimo.skills.internal;

import org.springaicommunity.agent.tools.SkillsTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Graceful 版 Skills loader。
 * 保持與 agent-utils Skills 相同的方法簽名，改進：
 * (1) 單檔錯誤跳過而非炸掉全部（AC-2）
 * (2) 目錄不存在回傳空 list 而非拋例外
 * (3) 使用 Grimo MarkdownParser（SnakeYAML）
 */
public class Skills {

    private static final Logger log = LoggerFactory.getLogger(Skills.class);
    private static final String SKILL_FILE = "SKILL.md";

    public static List<SkillsTool.Skill> loadDirectory(String rootDirectory) {
        Path root = Path.of(rootDirectory);
        if (!Files.isDirectory(root)) {
            log.warn("Skills directory does not exist: {}", rootDirectory);
            return List.of();
        }

        List<SkillsTool.Skill> skills = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root, 2)) {
            paths.filter(p -> p.getFileName().toString().equals(SKILL_FILE))
                 .forEach(skillFile -> {
                     try {
                         String markdown = Files.readString(skillFile);
                         MarkdownParser parser = new MarkdownParser(markdown);
                         String basePath = skillFile.getParent().toString();
                         skills.add(new SkillsTool.Skill(
                             basePath,
                             parser.getFrontMatter(),
                             parser.getContent()
                         ));
                     } catch (Exception e) {
                         log.warn("Skipping invalid skill at {}: {}", skillFile, e.getMessage());
                     }
                 });
        } catch (Exception e) {
            log.warn("Failed to walk skills directory {}: {}", rootDirectory, e.getMessage());
        }
        return List.copyOf(skills);
    }
}
```

### 4.6 FileSystemSkillStoreAdapter

```java
package io.github.samzhu.grimo.skills.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.samzhu.grimo.core.domain.GrimoHomePaths;
import io.github.samzhu.grimo.skills.application.port.out.SkillStorePort;
import io.github.samzhu.grimo.skills.domain.SkillEntry;
import io.github.samzhu.grimo.skills.internal.Skills;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Component
class FileSystemSkillStoreAdapter implements SkillStorePort {

    private static final String STATE_FILE = ".state.json";
    // agentskills.io: 1-64 chars, lowercase alphanum + hyphen, no leading/trailing/double hyphen
    private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$");

    private final ObjectMapper objectMapper;

    FileSystemSkillStoreAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SkillEntry> loadAll() {
        Path skillsDir = GrimoHomePaths.skills();
        List<SkillsTool.Skill> raw = Skills.loadDirectory(skillsDir.toString());
        Map<String, Boolean> state = readStateJson(skillsDir.resolve(STATE_FILE));

        return raw.stream()
            .filter(this::isValid)
            .map(s -> toEntry(s, state))
            .toList();
    }

    @Override
    public void saveState(Map<String, Boolean> enabledMap) {
        // {"version":1,"skills":{"name":{"enabled":true},...}}
        Path stateFile = GrimoHomePaths.skills().resolve(STATE_FILE);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        Map<String, Object> skills = new LinkedHashMap<>();
        enabledMap.forEach((name, enabled) ->
            skills.put(name, Map.of("enabled", enabled)));
        root.put("skills", skills);
        // write with ObjectMapper
    }

    private boolean isValid(SkillsTool.Skill raw) {
        var fm = raw.frontMatter();
        if (fm == null || !fm.containsKey("name") || !fm.containsKey("description")) {
            log.warn("Skipping skill at {}: missing required name or description", raw.basePath());
            return false;
        }
        String name = raw.name();
        if (!VALID_NAME.matcher(name).matches() || name.contains("--")) {
            log.warn("Skipping skill at {}: name '{}' violates agentskills.io format", raw.basePath(), name);
            return false;
        }
        // name 必須與目錄名一致
        String dirName = Path.of(raw.basePath()).getFileName().toString();
        if (!name.equals(dirName)) {
            log.warn("Skipping skill at {}: name '{}' ≠ directory '{}'", raw.basePath(), name, dirName);
            return false;
        }
        return true;
    }

    private SkillEntry toEntry(SkillsTool.Skill skill, Map<String, Boolean> state) {
        boolean enabled = state.getOrDefault(skill.name(), true); // 預設啟用
        return new SkillEntry(skill, enabled);
    }
}
```

### 4.7 SkillRegistryService

```java
package io.github.samzhu.grimo.skills.application.service;

import io.github.samzhu.grimo.skills.application.port.in.SkillRegistryUseCase;
import io.github.samzhu.grimo.skills.application.port.out.SkillStorePort;
import io.github.samzhu.grimo.skills.domain.SkillEntry;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
class SkillRegistryService implements SkillRegistryUseCase {

    private final SkillStorePort store;

    SkillRegistryService(SkillStorePort store) {
        this.store = store;
    }

    @Override
    public List<SkillEntry> list() {
        return store.loadAll();
    }

    @Override
    public List<SkillEntry> listEnabled() {
        return store.loadAll().stream().filter(SkillEntry::enabled).toList();
    }

    @Override
    public Optional<SkillEntry> get(String name) {
        return store.loadAll().stream()
            .filter(e -> e.name().equals(name))
            .findFirst();
    }

    @Override
    public void enable(String name) { updateState(name, true); }

    @Override
    public void disable(String name) { updateState(name, false); }

    private void updateState(String name, boolean enabled) {
        List<SkillEntry> all = store.loadAll();
        if (all.stream().noneMatch(e -> e.name().equals(name))) {
            throw new IllegalArgumentException("Skill not found: " + name);
        }
        Map<String, Boolean> states = new HashMap<>();
        all.forEach(e -> states.put(e.name(), e.enabled()));
        states.put(name, enabled);
        store.saveState(states);
    }
}
```

---

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `build.gradle.kts` | modify | 新增 `implementation("org.springaicommunity:spring-ai-agent-utils:0.7.0")` |
| `src/main/java/.../skills/package-info.java` | modify | `allowedDependencies = { "core" }`；修正 Javadoc（S012=登錄檔，S013=注入） |
| `src/main/java/.../skills/domain/SkillEntry.java` | new | domain record：SkillsTool.Skill + enabled |
| `src/main/java/.../skills/application/port/in/SkillRegistryUseCase.java` | new | 用例介面：list, listEnabled, get, enable, disable |
| `src/main/java/.../skills/application/port/out/SkillStorePort.java` | new | 出站埠：loadAll, saveState |
| `src/main/java/.../skills/application/service/SkillRegistryService.java` | new | @Service 用例實作 |
| `src/main/java/.../skills/adapter/out/FileSystemSkillStoreAdapter.java` | new | Grimo Skills + .state.json + agentskills.io 驗證 |
| `src/main/java/.../skills/internal/MarkdownParser.java` | new | SnakeYAML 版，同 agent-utils 介面 |
| `src/main/java/.../skills/internal/Skills.java` | new | Graceful 版，同介面，產出 SkillsTool.Skill |
| `src/test/java/.../skills/domain/SkillEntryTest.java` | new | T0：domain record 驗證 |
| `src/test/java/.../skills/internal/MarkdownParserTest.java` | new | T0：SnakeYAML 解析 vs agent-utils 原版對比 |
| `src/test/java/.../skills/application/service/SkillRegistryServiceTest.java` | new | T0：mock SkillStorePort，驗證 list/enable/disable |
| `src/test/java/.../skills/adapter/out/FileSystemSkillStoreAdapterTest.java` | new | T0：真實 temp 目錄 + fixture SKILL.md，驗證 AC-1~3 |
| `docs/grimo/architecture.md` | modify | §3 框架依賴表新增 `spring-ai-agent-utils` 0.7.0 |

---

## 6. Task Plan

**POC: required** — SnakeYAML 解析 SKILL.md frontmatter 的 `---` 分隔符處理。

### POC Findings

POC 8/8 通過（`SkillRegistryPocTest`）。驗證結果：
- **設計假說成立** — SnakeYAML `Yaml.loadAs()` 需手動擷取 `---` 之間的 YAML 區段（spec §4.4 方案正確）
- `SkillsTool.Skill` 為 public record，可直接 `new Skill(basePath, frontMatter, content)` 建構
- `Skill.name()` 從 `frontMatter.get("name")` 取值
- 巢狀 Map（metadata）、YAML List（allowed-tools）、多行 scalar（`>` folded）均正確解析
- SnakeYAML 2.5 在 Boot classpath 上（由 `spring-boot-starter` 傳遞引入）

### Task 概覽

| Task | 描述 | AC 對應 | 依賴 |
|------|------|---------|------|
| T1 | MarkdownParser + Skills + Domain + Ports | AC-1 基礎 | — |
| T2 | Service + Adapter + AC-1/AC-2/AC-3 整合測試 | AC-1, AC-2, AC-3 | T1 |

### AC-to-Task 對應

| AC | Task | 測試驗證 |
|----|------|---------|
| AC-1 Skill 出現在 list() | T1（解析）+ T2（整合） | FileSystemSkillStoreAdapterTest, SkillRegistryServiceTest |
| AC-2 無效 SKILL.md 跳過 | T2 | FileSystemSkillStoreAdapterTest |
| AC-3 disable() 持久化 | T2 | FileSystemSkillStoreAdapterTest, SkillRegistryServiceTest |

---

## 7. Implementation Results

**Status: ✅ Done** — 2026-04-20

### Verification Results

```
./gradlew test          → BUILD SUCCESSFUL (71 tests, 0 failures)
./gradlew compileTestJava → BUILD SUCCESSFUL
```

### Key Findings

1. **ObjectMapper 自行建構。** `FileSystemSkillStoreAdapter` 改為無參建構子，內部 `new ObjectMapper()`。專案無 `spring-boot-starter-web` 或 `spring-boot-starter-json`，Jackson `ObjectMapper` 無 auto-configured bean。此為實作偏離 §4.6 設計（原設計注入 ObjectMapper）。
2. **SnakeYAML 手動擷取 frontmatter。** POC 驗證 `Yaml.loadAs()` 不自動處理 `---` 分隔符。需先手動 `substring()` 擷取 YAML 區段，再餵入 SnakeYAML。§4.4 方案正確。
3. **agentskills.io 驗證。** `name` 格式（`[a-z0-9]` + `-`）、name 與目錄名一致性、`name` + `description` 必填 — 均以 WARN log + 跳過處理，不中斷應用程式。
4. **SkillsTool.Skill.name()** 從 `frontMatter.get("name")` 取值，已驗證可直接使用。

### Correct Usage Patterns

```java
// MarkdownParser — SnakeYAML frontmatter parsing
var parser = new MarkdownParser(markdownContent);
Map<String, Object> fm = parser.getFrontMatter();  // defensive copy
String body = parser.getContent();

// Skills — graceful directory loading
List<SkillsTool.Skill> skills = Skills.loadDirectory(dir.toString());
// Returns empty list if directory missing; skips invalid files with WARN

// SkillEntry — domain record
var entry = new SkillEntry(skill, true);
entry.name();    // delegates to skill.name()
entry.enabled(); // from .state.json, default true

// SkillRegistryUseCase — use case interface
List<SkillEntry> all = registry.list();
List<SkillEntry> enabled = registry.listEnabled();
registry.disable("hello");  // persists to .state.json
```

### AC Results

| AC | 結果 | 測試 |
|----|------|------|
| AC-1 Skill 出現在 list() 中 | ✅ | `FileSystemSkillStoreAdapterTest.ac1_validSkillAppearsInList` |
| AC-2 無效 SKILL.md 跳過 | ✅ | `FileSystemSkillStoreAdapterTest.ac2_invalidSkillSkipped`, `ac2_missingNameSkipped`, `ac2_nameMismatchSkipped` |
| AC-3 disable() 持久化 | ✅ | `FileSystemSkillStoreAdapterTest.ac3_disablePersistsAcrossReload`, `ac3_stateJsonStructure` |

### Design Drift

- **§4.6 FileSystemSkillStoreAdapter** — 原設計注入 `ObjectMapper`，實作改為無參建構子自行建構。原因：專案無 Jackson auto-config bean。[Implementation note]

### Pending Verification

（無 — 所有測試均可在本機執行）

---

### QA Review

Date: 2026-04-20
Reviewer: Independent QA subagent
Verdict: **PASS**

#### Deterministic Checks

| 指令 | 結果 |
|------|------|
| `./gradlew test` | BUILD SUCCESSFUL — 全部 23 個 S012 測試通過，0 失敗，0 跳過 |
| `./gradlew compileTestJava` | BUILD SUCCESSFUL |
| `ModuleArchitectureTest.verify()` | PASS — skills 模組邊界無違規 |

#### AC-to-Test Mapping

| AC | 測試檔案 | @DisplayName | 結果 |
|----|----------|-------------|------|
| AC-1 | `FileSystemSkillStoreAdapterTest` | `S012 AC-1: valid skill appears in loadAll() with correct frontMatter` | PASS |
| AC-1 | `SkillRegistryServiceTest` | `S012 AC-1: list() delegates to store.loadAll()` | PASS |
| AC-1 | `SkillRegistryServiceTest` | `S012 AC-1: listEnabled() filters disabled skills` | PASS |
| AC-1 | `SkillRegistryServiceTest` | `S012 AC-1: get() returns matching skill` | PASS |
| AC-1 | `SkillRegistryServiceTest` | `S012 AC-1: get() returns empty for unknown skill` | PASS |
| AC-2 | `FileSystemSkillStoreAdapterTest` | `S012 AC-2: invalid SKILL.md (no frontmatter) is skipped` | PASS |
| AC-2 | `FileSystemSkillStoreAdapterTest` | `S012 AC-2: skill with missing name is skipped` | PASS |
| AC-2 | `FileSystemSkillStoreAdapterTest` | `S012 AC-2: skill with name mismatch to directory is skipped` | PASS |
| AC-3 | `FileSystemSkillStoreAdapterTest` | `S012 AC-3: disable persists to .state.json and survives reload` | PASS |
| AC-3 | `FileSystemSkillStoreAdapterTest` | `S012 AC-3: .state.json file contains correct structure` | PASS |
| AC-3 | `SkillRegistryServiceTest` | `S012 AC-3: disable() calls saveState with updated map` | PASS |
| AC-3 | `SkillRegistryServiceTest` | `S012 AC-3: enable() calls saveState with updated map` | PASS |

#### Issues Found

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | IMPORTANT | AC-2 規格要求「日誌中包含 WARN 記錄，提及 'broken'」，但 `ac2_invalidSkillSkipped` 測試僅驗證回傳清單大小，沒有斷言 WARN log 輸出。運行時確認 log 確實輸出（測試 stdout 可見），但 log 輸出未被測試程式碼正式斷言。 | OPEN |
| 2 | IMPORTANT | `description` 值的非空驗證缺失。spec §2.2 規定 description 必填且非空（1-1024 字元），但 `isValid()` 只做 `containsKey("description")`。若 YAML 寫 `description:` (null 值) 或 `description: ""` (空字串)，仍通過驗證。此邊界情況未在測試中覆蓋。 | OPEN |
| 3 | IMPORTANT | 全部測試方法缺少 `// Given / // When / // Then` 區塊（development-standards §7.9 明文要求）。適配器測試（`FileSystemSkillStoreAdapterTest`）中的 `ac3_disablePersistsAcrossReload` 有隱式三段結構但未以注解標示。 | OPEN |
| 4 | MINOR | `SkillRegistryService` 類沒有類別層級 Javadoc（其他產出類別均有）。該類為 package-private 且為實作，Javadoc 非嚴格必要，但一致性略低。 | OPEN |
| 5 | MINOR | `FileSystemSkillStoreAdapter` 與 `SkillRegistryService` 類均沒有類別層級 Javadoc。相比之下，`MarkdownParser`、`Skills`、`SkillEntry` 均有完整 Javadoc。設計偏離（§4.6 §4.7）已在 §7 Design Drift 正式記載。 | OPEN |

#### Code Quality Summary

- **套件結構**：符合 §2 hexagonal 分層（`domain/`、`application/port/in|out/`、`application/service/`、`adapter/out/`、`internal/`）✓
- **DI**：`SkillRegistryService` 使用建構子注入（§4）✓；`FileSystemSkillStoreAdapter` 無參建構子自建 `ObjectMapper`（設計偏離已在 §7 記載，可接受）✓
- **不可變性**：`SkillEntry` 為 record；`MarkdownParser` 欄位均 final；`ObjectMapper` 為 final 欄位（非靜態可變）✓
- **禁止模式**：無 `System.getenv` 違規（`GrimoHomePaths` 為唯一合法使用點）；無靜態可變狀態；無 `Mockito.mock(Process.class)` ✓
- **日誌**：使用 SLF4J；WARN 在所有跳過情境正確輸出；無 `System.out` ✓
- **安全性**：無硬編碼秘鑰；無指令注入向量 ✓
- **Modulith verify**：`ModuleArchitectureTest` PASS；`skills` 模組 `allowedDependencies = { "core" }` 正確配置 ✓
- **package-info.java Javadoc**：已正確修正（S012=登錄檔，S013=注入）✓
- **architecture.md**：已更新 `spring-ai-agent-utils 0.7.0` 框架依賴表 ✓

#### Verdict Rationale

所有 3 個 AC 均有對應的 `@DisplayName` 測試且通過。建置乾淨、Modulith 邊界無違規。核心功能（掃描、驗證、持久化、重啟恢復）均有測試覆蓋。發現 2 個 IMPORTANT 問題（log 斷言缺失、description 空值邊界）和 3 個 MINOR 問題（Given/When/Then 結構、Javadoc 缺失），均不阻擋出貨，建議在技術債表登記追蹤。

---

### QA Review (Independent Re-verification)

Date: 2026-04-20
Reviewer: Independent QA (manual `/verifying-quality`)
Verdict: **PASS**

#### Deterministic Checks

| 指令 | 結果 |
|------|------|
| `./gradlew test` | BUILD SUCCESSFUL |
| `./gradlew compileTestJava` | BUILD SUCCESSFUL |

#### AC-to-Test Mapping (re-verified)

| AC | 測試 | @DisplayName | 結果 |
|----|------|-------------|------|
| AC-1 | `FileSystemSkillStoreAdapterTest` | `S012 AC-1: valid skill appears in loadAll() with correct frontMatter` | PASS |
| AC-1 | `SkillRegistryServiceTest` | `S012 AC-1: list() delegates to store.loadAll()` | PASS |
| AC-1 | `SkillRegistryServiceTest` | `S012 AC-1: listEnabled() filters disabled skills` | PASS |
| AC-1 | `SkillRegistryServiceTest` | `S012 AC-1: get() returns matching skill` | PASS |
| AC-1 | `SkillRegistryServiceTest` | `S012 AC-1: get() returns empty for unknown skill` | PASS |
| AC-2 | `FileSystemSkillStoreAdapterTest` | `S012 AC-2: invalid SKILL.md (no frontmatter) is skipped` | PASS |
| AC-2 | `FileSystemSkillStoreAdapterTest` | `S012 AC-2: skill with missing name is skipped` | PASS |
| AC-2 | `FileSystemSkillStoreAdapterTest` | `S012 AC-2: skill with name mismatch to directory is skipped` | PASS |
| AC-2 | `FileSystemSkillStoreAdapterTest` | `S012 AC-2: skill with empty description is skipped` | PASS |
| AC-2 | `FileSystemSkillStoreAdapterTest` | `S012 AC-2: skill with null description is skipped` | PASS |
| AC-3 | `FileSystemSkillStoreAdapterTest` | `S012 AC-3: disable persists to .state.json and survives reload` | PASS |
| AC-3 | `FileSystemSkillStoreAdapterTest` | `S012 AC-3: .state.json file contains correct structure` | PASS |
| AC-3 | `SkillRegistryServiceTest` | `S012 AC-3: disable() calls saveState with updated map` | PASS |
| AC-3 | `SkillRegistryServiceTest` | `S012 AC-3: enable() calls saveState with updated map` | PASS |

#### Prior QA Issues Status

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | IMPORTANT | AC-2 WARN log 斷言缺失 | OPEN — 建議登記技術債 |
| 2 | IMPORTANT | `description` 空值邊界驗證 | **FIXED** — `isValid()` 新增 `desc == null \|\| desc.toString().isBlank()` 檢查 + 2 個新測試（`ac2_emptyDescriptionSkipped`, `ac2_nullDescriptionSkipped`） |
| 3 | IMPORTANT | Given/When/Then 注解缺失 | OPEN — 建議登記技術債 |
| 4 | MINOR | `SkillRegistryService` 缺類別 Javadoc | OPEN |
| 5 | MINOR | `FileSystemSkillStoreAdapter` 缺類別 Javadoc | OPEN |

#### New Findings

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 6 | IMPORTANT | `development-standards.md` §2 skills 模組描述寫 `（S011、S012）` 應為 `（S012、S013）` — 與 package-info.java 同類型的 spec ID 飄移 | **FIXED** — 已原地修正 |

#### Code Quality Summary (independent)

- **套件結構**：完全符合 hexagonal 分層 ✓
- **domain/ 零 Spring**：`SkillEntry` 為純 record，零 Spring 注解 ✓
- **DI**：`SkillRegistryService` 建構子注入 `SkillStorePort` ✓
- **不可變性**：所有 domain 型別為 record/final ✓
- **禁止模式**：無 `System.getenv`（僅 `GrimoHomePaths`）、無靜態可變狀態、無 mock CLI process ✓
- **日誌**：SLF4J WARN 在全部跳過路徑正確輸出，無 System.out ✓
- **安全性**：SnakeYAML `Yaml()` 預設建構子有任意物件反序列化風險，但此處僅解析受信任的本地 SKILL.md 檔案，非外部輸入，風險可接受 ✓
- **Modulith verify**：PASS ✓
- **architecture.md**：依賴表已更新 ✓
- **glossary.md**：`Skill` 術語已存在 ✓
- **TODO/FIXME**：無孤立項 ✓

#### Verdict

**PASS** — 所有 3 個 AC 均有對應測試且通過（14 個 AC 相關測試 + 11 個輔助測試 = 25 個 S012 測試）。先前 QA 的 #2 已修正。development-standards.md 飄移 (#6) 已原地修正。剩餘 OPEN 項（#1 log 斷言、#3 Given/When/Then、#4-5 Javadoc）均為不阻擋出貨的改善項。
