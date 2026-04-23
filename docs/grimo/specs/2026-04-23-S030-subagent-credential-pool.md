# S030: Subagent Credential Pool

> Spec: S030 | Size: XS(7) | Status: ⏳ Design
> Date: 2026-04-23

---

## 1. Goal

為 subagent 執行管線提供 credential pool 管理。使用者透過 Main Agent 引導執行 `claude setup-token`，取得 1 年期 OAuth token 後存入 Grimo（H2）。支援多帳號：依優先序（PRIORITY）或隨機（RANDOM）選取 credential。Token 過期自動跳過。

S028 目前只支援 `ANTHROPIC_API_KEY` env var 注入（零封號風險但按量計費）。本 spec 加入 `CLAUDE_CODE_OAUTH_TOKEN` 注入路徑，讓訂閱用戶享受固定月費。請求仍透過容器內官方 `claude` binary 發出，與 T3 Code 被 Boris Cherny 確認安全的模式相同。

依賴：S028 ✅（`SubagentExecutorService.buildEnvVars()`）、S018 ✅（REST API 基礎）。

## 2. Approach

| Approach | Chosen | Rationale |
|----------|--------|-----------|
| A: H2 credential 表 + REST CRUD + CredentialResolver | yes | 與現有 JDBC 生態一致；六邊形架構直接套用；未來可擴展 Bedrock/Vertex credentials |
| B: `~/.grimo/credentials.json` 檔案 | no | 需新增 file I/O port；不在現有 JDBC 生態中；多帳號管理需自行實作 JSON merge |
| C: `application.yaml` env var binding | no | 使用者要求 Grimo 管理 token，非靜態配置；不支援多帳號、策略選擇 |

### 2.1 Key Design Decisions

**D1 — Credential Pool 而非單一 Credential**：使用者可能有多個 Claude 帳號（Personal Max + Work Team）。Pool 設計支援優先序和隨機兩種選取策略，擴展性優於單一 credential 欄位。

**D2 — 選取策略**：

| 策略 | 行為 |
|------|------|
| `PRIORITY` | 依 `sort_order` ASC 取第一個未過期的 credential |
| `RANDOM` | 從所有未過期的 credentials 中隨機選取 |

策略存為全域設定（`grimo_setting` 表 key-value），預設 `PRIORITY`。

**D3 — 認證優先序（與官方 CLI 一致）**：

```
1. grimo.subagent.api-key (ANTHROPIC_API_KEY)   ← S028 已實作，最高優先
2. Credential Pool (CLAUDE_CODE_OAUTH_TOKEN)     ← 本 spec 新增
3. CLI native credentials                        ← fallback
```

`ANTHROPIC_API_KEY` 有值時，credential pool 不生效。這確保 API Key 使用者不受影響。

**D4 — Main Agent 引導流程**：

```
使用者 ↔ Main Agent（主機 Claude CLI）
              │
              ├─ Main Agent: "需要設定 subagent 認證，請執行 claude setup-token"
              │
              ├─ 使用者在終端機執行 claude setup-token → 瀏覽器 OAuth → 取得 token
              │
              ├─ 使用者將 token 貼回對話
              │
              ├─ Main Agent 呼叫 POST /api/credentials 存入 Grimo
              │
              └─ 完成。後續 subagent 自動使用。
```

Main Agent 是 UX 層，使用者不需手動 curl。

**D5 — Token 過期處理**：`expires_at` 欄位記錄 token 到期時間（`setup-token` 為產生時 +1 年）。`CredentialResolverService` 選取時自動跳過 `expires_at < now()` 的 credential。429 Rate Limit 自動 failover 為未來增強（需解析 claude CLI stderr + 重試邏輯）。

**D6 — S028 spec D7 備註更新**：S028 spec D7 加一行：「`CLAUDE_CODE_OAUTH_TOKEN` 注入能力由 S030 Credential Pool 提供，非直接硬編碼。」

### 2.2 封號風險判定

| 因素 | 分析 |
|------|------|
| 請求路徑 | `docker exec → claude -p` → 官方 binary 發出 HTTPS → ✅ 合規 |
| Token 來源 | `claude setup-token`（官方命令） → ✅ 合法取得 |
| Boris Cherny 確認 | 「包裝 Claude Code 的本地工具可用訂閱方案」 → ✅ |
| 風險 | Anthropic 未來可能改變政策 → `ANTHROPIC_API_KEY` 為最高優先兜底 |

### 2.3 Research Citations

- competitive-analysis.md §11: OpenClaw 封號事件 — Boris Cherny 確認「透過官方 Claude Code 進程發出的請求」可用訂閱
- deepwiki `claude-sdk-design-decisions.md` §2.7: `ANTHROPIC_API_KEY` 是唯一零風險路徑
- 官方 auth 文件: `setup-token` 產生 1 年期 `sk-ant-oat01-...` token，注入為 `CLAUDE_CODE_OAUTH_TOKEN`
- GitHub Issue #37512: 設定 `CLAUDE_CODE_OAUTH_TOKEN` env var 會靜默刪除 macOS Keychain credentials（不影響 Grimo — token 已獨立存入 H2）
- Python SDK Issue #559: `CLAUDE_CODE_OAUTH_TOKEN` 在 2026-02 已被確認支援
- 競品研究：T3 Code（環境繼承）、OpenAB（顯式注入）、Docker sbx（proxy 攔截）— 無一從 Keychain 提取

## 3. SBE Acceptance Criteria

Run: `./gradlew test`
Pass: all tests carrying S030 AC ids are green.

**AC-1: 建立 credential**
Given 使用者提供 label="personal-max", provider="claude", type="oauth_token", value="sk-ant-oat01-...", expiresAt=+1year
When  `POST /api/credentials { label, provider, credentialType, secretValue, expiresAt }`
Then  回傳 `201 Created` + `Credential { id, label, provider, credentialType, sortOrder, ... }`
And   `secretValue` 不在回傳 JSON 中（遮蔽）

**AC-2: 列出 credentials（遮蔽 secret）**
Given 已建立 2 個 credentials
When  `GET /api/credentials`
Then  回傳 2 筆，每筆 `secretValue` 顯示為 `"sk-ant-***...***"` 遮蔽格式

**AC-3: PRIORITY 策略選取**
Given 2 個 credentials（sort_order=1 未過期，sort_order=2 未過期）
And   策略為 `PRIORITY`
When  `CredentialResolverService.resolve("claude")` 被呼叫
Then  回傳 sort_order=1 的 credential

**AC-4: RANDOM 策略選取**
Given 3 個未過期 credentials
And   策略為 `RANDOM`
When  `CredentialResolverService.resolve("claude")` 被呼叫 100 次
Then  3 個 credential 都被選中至少 1 次

**AC-5: 過期 token 自動跳過**
Given 2 個 credentials（sort_order=1 已過期，sort_order=2 未過期）
And   策略為 `PRIORITY`
When  `CredentialResolverService.resolve("claude")` 被呼叫
Then  回傳 sort_order=2 的 credential（跳過已過期的）

**AC-6: buildEnvVars 整合**
Given credential pool 有一個 claude oauth_token credential
And   `grimo.subagent.api-key` 未設定
When  `SubagentExecutorService.buildEnvVars()` 被呼叫
Then  env 包含 `CLAUDE_CODE_OAUTH_TOKEN` = credential 的 secretValue
And   env 不包含 `ANTHROPIC_API_KEY`

**AC-7: API Key 優先於 Credential Pool**
Given credential pool 有一個 claude oauth_token credential
And   `grimo.subagent.api-key` 設定為 `sk-ant-api03-...`
When  `SubagentExecutorService.buildEnvVars()` 被呼叫
Then  env 包含 `ANTHROPIC_API_KEY`
And   env 不包含 `CLAUDE_CODE_OAUTH_TOKEN`

**AC-8: 刪除 credential**
Given 已建立 1 個 credential
When  `DELETE /api/credentials/{id}`
Then  回傳 `204 No Content`
And   credential 已從 DB 移除

**AC-9: 更新策略設定**
Given 預設策略為 `PRIORITY`
When  `PUT /api/settings/credential-strategy { "value": "RANDOM" }`
Then  回傳 `200 OK`
And   後續 resolve 使用 RANDOM 策略

**AC-10: Modulith 驗證通過**
Given subagent 模組新增 credential 相關依賴
When  `./gradlew test`（含 `ModuleArchitectureTest`）
Then  Spring Modulith verify 通過

## 4. Interface / API Design

### Domain Types

```java
// === subagent/domain/Credential.java ===
public record Credential(
    String id,                          // NanoIds.compact()
    String label,                       // user-defined: "personal-max"
    String provider,                    // "claude"
    String credentialType,              // "oauth_token", "api_key"
    String secretValue,                 // actual token/key
    int sortOrder,                      // priority ordering (1 = highest)
    @Nullable Instant expiresAt,        // setup-token: +1 year
    Instant createdAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /** Mask secret for display: "sk-ant-***...***" */
    public String maskedSecret() {
        if (secretValue.length() <= 10) return "***";
        return secretValue.substring(0, 6) + "***..." + 
               secretValue.substring(secretValue.length() - 3);
    }
}

// === subagent/domain/CredentialStrategy.java ===
public enum CredentialStrategy {
    PRIORITY, RANDOM
}
```

### Inbound Ports

```java
// === subagent/application/port/in/CredentialUseCase.java ===
public interface CredentialUseCase {
    Credential create(String label, String provider, String credentialType,
                      String secretValue, @Nullable Instant expiresAt);
    List<Credential> listAll();
    Optional<Credential> findById(String id);
    void delete(String id);
    void updateSortOrder(String id, int newSortOrder);
}

// === subagent/application/port/in/CredentialResolverUseCase.java ===
public interface CredentialResolverUseCase {
    /** Resolve best credential for provider, based on strategy + expiry. */
    Optional<Credential> resolve(String provider);
}
```

### Outbound Ports

```java
// === subagent/application/port/out/CredentialPort.java ===
public interface CredentialPort {
    void save(Credential credential);
    Optional<Credential> findById(String id);
    List<Credential> findAll();
    List<Credential> findByProviderOrderBySortOrder(String provider);
    void deleteById(String id);
}

// === subagent/application/port/out/SettingPort.java ===
public interface SettingPort {
    Optional<String> get(String key);
    void set(String key, String value);
}
```

### REST API

```
POST   /api/credentials
  Body: { "label", "provider", "credentialType", "secretValue", "expiresAt?" }
  Response: 201 Created + Credential (masked secret)

GET    /api/credentials
  Response: 200 OK + List<Credential> (masked secrets)

DELETE /api/credentials/{id}
  Response: 204 No Content

PUT    /api/credentials/{id}/sort-order
  Body: { "sortOrder": 2 }
  Response: 200 OK

PUT    /api/settings/credential-strategy
  Body: { "value": "RANDOM" }
  Response: 200 OK

GET    /api/settings/credential-strategy
  Response: 200 OK + { "value": "PRIORITY" }
```

### buildEnvVars 整合

```java
// SubagentExecutorService.buildEnvVars() — 修改後
Map<String, String> buildEnvVars() {
    var env = new HashMap<String, String>();

    // Auth priority: API Key > Credential Pool > CLI native
    String apiKey = props.apiKey();
    if (apiKey != null && !apiKey.isBlank()) {
        env.put("ANTHROPIC_API_KEY", apiKey);
    } else {
        credentialResolver.resolve("claude").ifPresent(cred -> {
            switch (cred.credentialType()) {
                case "oauth_token" -> env.put("CLAUDE_CODE_OAUTH_TOKEN", cred.secretValue());
                case "api_key"     -> env.put("ANTHROPIC_API_KEY", cred.secretValue());
            }
        });
    }

    env.put("CLAUDE_CODE_DISABLE_AUTO_MEMORY", "1");
    env.put("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1");
    return env;
}
```

### Database Schema

```sql
CREATE TABLE IF NOT EXISTS grimo_credential (
    id              VARCHAR(12)   PRIMARY KEY,
    label           VARCHAR(100)  NOT NULL UNIQUE,
    provider        VARCHAR(50)   NOT NULL,
    credential_type VARCHAR(50)   NOT NULL,
    secret_value    VARCHAR(2000) NOT NULL,
    sort_order      INT           NOT NULL DEFAULT 1,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL
);

CREATE TABLE IF NOT EXISTS grimo_setting (
    setting_key     VARCHAR(100)  PRIMARY KEY,
    setting_value   VARCHAR(500)  NOT NULL
);
```

### Example Data

```
grimo_credential:
| id           | label        | provider | credential_type | secret_value     | sort_order | expires_at          |
|--------------|-------------|----------|-----------------|------------------|------------|---------------------|
| cred1234abcd | personal-max | claude   | oauth_token     | sk-ant-oat01-... | 1          | 2027-04-23 10:00:00 |
| cred5678efgh | work-team    | claude   | oauth_token     | sk-ant-oat01-... | 2          | 2027-04-23 10:00:00 |
| cred9012ijkl | api-backup   | claude   | api_key         | sk-ant-api03-... | 3          | NULL                |

grimo_setting:
| setting_key          | setting_value |
|---------------------|---------------|
| credential-strategy | PRIORITY      |
```

## 5. File Plan

| File | Action | Description |
|------|--------|-------------|
| `subagent/domain/Credential.java` | new | Credential domain record + maskedSecret + isExpired |
| `subagent/domain/CredentialStrategy.java` | new | PRIORITY / RANDOM enum |
| `subagent/application/port/in/CredentialUseCase.java` | new | CRUD inbound port |
| `subagent/application/port/in/CredentialResolverUseCase.java` | new | Resolve best credential |
| `subagent/application/port/out/CredentialPort.java` | new | Credential persistence port |
| `subagent/application/port/out/SettingPort.java` | new | Key-value setting port |
| `subagent/application/service/CredentialService.java` | new | CRUD 實作 |
| `subagent/application/service/CredentialResolverService.java` | new | PRIORITY / RANDOM 選取邏輯 |
| `subagent/adapter/in/web/CredentialRestController.java` | new | REST API — CRUD + strategy |
| `subagent/adapter/out/JdbcCredentialAdapter.java` | new | JDBC 實作 — grimo_credential |
| `subagent/adapter/out/JdbcSettingAdapter.java` | new | JDBC 實作 — grimo_setting |
| `subagent/application/service/SubagentExecutorService.java` | modify | buildEnvVars() 加 credential pool 整合 |
| `src/main/resources/schema.sql` | modify | 新增 grimo_credential + grimo_setting 表 |
| `test/.../CredentialServiceTest.java` | new | CRUD unit test |
| `test/.../CredentialResolverServiceTest.java` | new | PRIORITY / RANDOM / expiry 選取測試 |
| `test/.../CredentialRestControllerTest.java` | new | @WebMvcTest — REST API |
| `test/.../JdbcCredentialAdapterTest.java` | new | 真實 H2 + schema.sql |
| `test/.../SubagentExecutorServiceTest.java` | modify | 加 AC-6, AC-7 credential pool 整合測試 |
