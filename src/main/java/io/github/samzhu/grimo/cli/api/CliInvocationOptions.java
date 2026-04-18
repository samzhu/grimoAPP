package io.github.samzhu.grimo.cli.api;

import io.github.samzhu.grimo.core.domain.ProviderId;
import java.util.List;
import java.util.Map;

/**
 * 每個 CLI provider 的容器化呼叫配置。包含注入容器的環境變數
 * 和附加至 CLI 命令的旗標。
 *
 * <p>設計說明：此 record 封裝 S006 驗證確認的配置組合。
 * 呼叫者（S007 主代理 / S010 子代理）在建立容器（env vars）
 * 和組裝 wrapper script（CLI flags）時消費這些選項。
 *
 * <p>每個靜態工廠方法代表一個經 S006 IT 驗證可行的配置。
 */
public record CliInvocationOptions(
    ProviderId provider,
    Map<String, String> containerEnvVars,
    List<String> cliFlags
) {
    /**
     * Claude Code — 訂閱認證 + 記憶體停用 + 遙測停用。
     *
     * <p>認證：macOS 上 {@code claude login} 存於 Keychain，容器無法存取。
     * 透過 {@code CLAUDE_CODE_OAUTH_TOKEN} env var 傳遞 OAuth token
     * （由 {@code claude setup-token} 或 Keychain 提取取得）。
     *
     * <p>記憶體停用：{@code CLAUDE_CODE_DISABLE_CLAUDE_MDS=1} 停用所有 CLAUDE.md 載入，
     * {@code CLAUDE_CODE_DISABLE_AUTO_MEMORY=1} 停用自動記憶體。
     * 不使用 {@code --bare}（會殺掉 skill 載入，與 S012 衝突）。
     *
     * <p>遙測停用：{@code CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1}
     * 停用遙測 + 自動更新 + 回饋。
     *
     * @param oauthToken 從 macOS Keychain 或 claude setup-token 取得的 OAuth token
     */
    public static CliInvocationOptions claude(String oauthToken) {
        return new CliInvocationOptions(
            ProviderId.CLAUDE,
            Map.of(
                "CLAUDE_CODE_OAUTH_TOKEN", oauthToken,
                "CLAUDE_CODE_DISABLE_CLAUDE_MDS", "1",
                "CLAUDE_CODE_DISABLE_AUTO_MEMORY", "1",
                "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1"
            ),
            List.of()
        );
    }

    /**
     * Codex CLI — 認證由 RO 掛載 {@code auth.json} 單檔提供；遙測停用。
     *
     * <p>認證：Codex 預設 {@code AuthCredentialsStoreMode::File}（非 Keychain），
     * {@code ~/.codex/auth.json} 為明文 JSON。僅 RO 掛載 {@code auth.json}
     * 至 {@code /root/.codex/auth.json}（Codex CLI 需寫入 cache/session 至
     * CODEX_HOME，無法對整個目錄 RO 掛載）。
     *
     * <p>遙測停用：{@code -c analytics.enabled=false} CLI flag。
     *
     * <p>呼叫者需 RO bind-mount {@code ~/.codex/auth.json → /root/.codex/auth.json}。
     */
    public static CliInvocationOptions codex() {
        return new CliInvocationOptions(
            ProviderId.CODEX,
            Map.of("CODEX_HOME", "/root/.codex"),
            List.of("-c", "analytics.enabled=false")
        );
    }

    /**
     * Gemini CLI — API key 認證；遙測預設已關閉。
     *
     * <p>認證：Gemini CLI 的 OAuth 憑證以 AES-256-GCM 加密，
     * 密鑰衍生自 hostname + username（scrypt），容器內無法解密主機檔案。
     * 必須使用 {@code GEMINI_API_KEY} env var 繞過 OAuth。
     *
     * <p>{@code GEMINI_CLI_HOME} 重導至 {@code /tmp/gemini-home}
     * 避免容器內嘗試讀寫預設的 {@code ~/.gemini/}。
     *
     * @param apiKey Google AI Studio 的 Gemini API key
     */
    public static CliInvocationOptions gemini(String apiKey) {
        return new CliInvocationOptions(
            ProviderId.GEMINI,
            Map.of(
                "GEMINI_API_KEY", apiKey,
                "GEMINI_CLI_HOME", "/tmp/gemini-home"
            ),
            List.of()
        );
    }
}
