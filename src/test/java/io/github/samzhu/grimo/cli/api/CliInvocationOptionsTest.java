package io.github.samzhu.grimo.cli.api;

import io.github.samzhu.grimo.core.domain.ProviderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CliInvocationOptions} static factories.
 * Verifies each provider's env vars and CLI flags match S006 research.
 */
class CliInvocationOptionsTest {

    @Test
    @DisplayName("[S006] AC-1: Claude factory returns correct env vars and empty flags")
    void claudeFactoryReturnsCorrectEnvVarsAndEmptyFlags() {
        // Given
        var token = "test-oauth-token";

        // When
        var opts = CliInvocationOptions.claude(token);

        // Then
        assertThat(opts.provider()).isEqualTo(ProviderId.CLAUDE);
        assertThat(opts.containerEnvVars())
                .containsEntry("CLAUDE_CODE_OAUTH_TOKEN", token)
                .containsEntry("CLAUDE_CODE_DISABLE_CLAUDE_MDS", "1")
                .containsEntry("CLAUDE_CODE_DISABLE_AUTO_MEMORY", "1")
                .containsEntry("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1")
                .hasSize(4);
        assertThat(opts.cliFlags()).isEmpty();
    }

    @Test
    @DisplayName("[S006] AC-1: Codex factory returns correct env vars and analytics-disable flag")
    void codexFactoryReturnsCorrectEnvVarsAndAnalyticsFlag() {
        // When
        var opts = CliInvocationOptions.codex();

        // Then
        assertThat(opts.provider()).isEqualTo(ProviderId.CODEX);
        assertThat(opts.containerEnvVars())
                .containsEntry("CODEX_HOME", "/root/.codex")
                .hasSize(1);
        assertThat(opts.cliFlags()).containsExactly("-c", "analytics.enabled=false");
    }

    @Test
    @DisplayName("[S006] AC-1: Gemini factory returns correct env vars and empty flags")
    void geminiFactoryReturnsCorrectEnvVarsAndEmptyFlags() {
        // Given
        var apiKey = "test-gemini-key";

        // When
        var opts = CliInvocationOptions.gemini(apiKey);

        // Then
        assertThat(opts.provider()).isEqualTo(ProviderId.GEMINI);
        assertThat(opts.containerEnvVars())
                .containsEntry("GEMINI_API_KEY", apiKey)
                .containsEntry("GEMINI_CLI_HOME", "/tmp/gemini-home")
                .hasSize(2);
        assertThat(opts.cliFlags()).isEmpty();
    }
}
