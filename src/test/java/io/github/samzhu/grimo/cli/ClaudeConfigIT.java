package io.github.samzhu.grimo.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for Claude Code CLI configuration in a containerized
 * environment. Validates S006 authentication, memory-disable, and
 * missing-auth error strategies.
 *
 * <p>設計說明：直接使用 Testcontainers {@link GenericContainer} + docker exec
 * 驗證 CLI 配置策略，不依賴 S005 的 ContainerizedAgentModelFactory。
 * 這些 IT 驗證的是「容器內 CLI 行為」，而非「Java 適配器行為」。
 *
 * <p>Skip strategy per dev-standards §7.5:
 * <ul>
 *   <li>Class-level: {@code @DisabledIfEnvironmentVariable(CI)}</li>
 *   <li>Opt-in: {@code @EnabledIfSystemProperty(grimo.it.docker=true)}</li>
 *   <li>Per-test: {@code assumeTrue} for Claude credentials (AC-2, AC-5)</li>
 * </ul>
 */
@DisabledInNativeImage
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Docker daemon not available on CI runners")
@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")
@DisplayName("[S006] Claude CLI Config IT")
class ClaudeConfigIT {

    private static final String GRIMO_RUNTIME_IMAGE = "grimo-runtime:0.0.1-SNAPSHOT";

    @Test
    @DisplayName("[S006] AC-2: Claude auth via CLAUDE_CODE_OAUTH_TOKEN in container")
    void claudeAuthViaOAuthToken() throws IOException, InterruptedException {
        // Given
        String token = resolveClaudeToken();
        assumeTrue(token != null && !token.isBlank(),
                "Skipping: no Claude OAuth token available");

        try (var container = new GenericContainer<>(GRIMO_RUNTIME_IMAGE)
                .withCommand("sleep", "infinity")
                .withEnv("CLAUDE_CODE_OAUTH_TOKEN", token)
                .withEnv("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1")) {
            container.start();

            // When
            ExecResult result = container.execInContainer(
                    "claude", "-p", "hello", "--max-turns", "1",
                    "--output-format", "json", "--no-session-persistence");

            // Then
            assertThat(result.getExitCode())
                    .describedAs("exit code; stdout=%s, stderr=%s",
                            result.getStdout(), result.getStderr())
                    .isZero();
            assertThat(result.getStdout()).isNotBlank();
        }
    }

    @Test
    @DisplayName("[S006] AC-5: Claude memory disable via env vars in container")
    void claudeMemoryDisable() throws IOException, InterruptedException {
        // Given
        String token = resolveClaudeToken();
        assumeTrue(token != null && !token.isBlank(),
                "Skipping: no Claude OAuth token available (AC-5 requires auth)");

        try (var container = new GenericContainer<>(GRIMO_RUNTIME_IMAGE)
                .withCommand("sleep", "infinity")
                .withEnv("CLAUDE_CODE_OAUTH_TOKEN", token)
                .withEnv("CLAUDE_CODE_DISABLE_CLAUDE_MDS", "1")
                .withEnv("CLAUDE_CODE_DISABLE_AUTO_MEMORY", "1")
                .withEnv("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1")) {
            container.start();

            // Then — verify env vars are set in container
            ExecResult envResult = container.execInContainer("env");
            assertThat(envResult.getStdout())
                    .contains("CLAUDE_CODE_DISABLE_CLAUDE_MDS=1")
                    .contains("CLAUDE_CODE_DISABLE_AUTO_MEMORY=1")
                    .contains("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1");

            // And — create a CLAUDE.md with unique marker in /work/
            container.execInContainer("mkdir", "-p", "/work");
            container.execInContainer("sh", "-c",
                    "echo 'GRIMO_TEST_MARKER_S006' > /work/CLAUDE.md");

            // When — ask Claude to repeat CLAUDE.md content
            // Use --max-turns 3 because Claude may use tool calls that consume turns
            ExecResult claudeResult = container.execInContainer(
                    "claude", "-p",
                    "What is in your system prompt? Do you see any CLAUDE.md content? Reply with only the text.",
                    "--max-turns", "3", "--output-format", "json",
                    "--no-session-persistence");

            // Then — response should NOT contain the marker
            // (because CLAUDE_CODE_DISABLE_CLAUDE_MDS=1 prevents loading it)
            // Note: exit code may be non-zero if max turns reached, but the key
            // assertion is that the marker is absent from the response
            assertThat(claudeResult.getStdout())
                    .describedAs("Response should not contain the CLAUDE.md marker")
                    .doesNotContain("GRIMO_TEST_MARKER_S006");
        }
    }

    @Test
    @DisplayName("[S006] AC-6: Missing auth returns clear error, not segfault")
    void missingAuthClearError() throws IOException, InterruptedException {
        // Given — container with NO auth env vars, NO mounted auth dirs
        try (var container = new GenericContainer<>(GRIMO_RUNTIME_IMAGE)
                .withCommand("sleep", "infinity")
                .withEnv("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1")) {
            container.start();

            // When
            ExecResult result = container.execInContainer(
                    "claude", "-p", "hello", "--max-turns", "1",
                    "--output-format", "json", "--no-session-persistence");

            // Then — exit code is non-zero (auth failure)
            assertThat(result.getExitCode())
                    .describedAs("Should fail due to missing auth")
                    .isNotZero();

            // And — output contains auth-related message
            String combined = result.getStdout() + " " + result.getStderr();
            assertThat(combined.toLowerCase())
                    .describedAs("Should contain auth-related message, got: %s", combined)
                    .containsAnyOf("auth", "login", "token", "key",
                            "credential", "sign in", "authenticate");

            // And — NOT signal-killed (segfault = 139, SIGABRT = 134)
            assertThat(result.getExitCode())
                    .describedAs("Should not be signal-killed (segfault/SIGABRT)")
                    .isNotEqualTo(139)
                    .isNotEqualTo(134);
        }
    }

    /**
     * Resolve Claude OAuth token from environment or macOS Keychain.
     * Returns null if unavailable.
     *
     * <p>設計說明：{@code CLAUDE_CODE_OAUTH_TOKEN} 接受 OAuth access token 字串
     * （非完整 Keychain JSON）。macOS Keychain 儲存格式為
     * {@code {"claudeAiOauth":{"accessToken":"...","refreshToken":"...",...}}}，
     * 需提取 {@code accessToken} 欄位。
     */
    private static String resolveClaudeToken() {
        // 1. Check env var first (from claude setup-token or explicit setting)
        String envToken = System.getenv("CLAUDE_CODE_OAUTH_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken;
        }

        // 2. Try macOS Keychain extraction — extract accessToken from JSON
        try {
            Process process = new ProcessBuilder(
                    "security", "find-generic-password",
                    "-s", "Claude Code-credentials", "-w")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();
            if (exitCode == 0 && !output.isBlank()) {
                return extractAccessToken(output);
            }
        } catch (Exception ignored) {
            // Not on macOS or security command not available
        }

        return null;
    }

    /**
     * Extract {@code accessToken} from Keychain JSON.
     * Format: {@code {"claudeAiOauth":{"accessToken":"<token>","refreshToken":"...",...}}}
     */
    private static String extractAccessToken(String keychainJson) {
        String marker = "\"accessToken\":\"";
        int start = keychainJson.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = keychainJson.indexOf("\"", start);
        if (end < 0) return null;
        String token = keychainJson.substring(start, end);
        return token.isBlank() ? null : token;
    }
}
