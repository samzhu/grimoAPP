package io.github.samzhu.grimo.agent;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springaicommunity.agents.claude.ClaudeAgentSessionRegistry;
import org.springaicommunity.agents.model.AgentSession;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Requires local claude CLI installation")
class MainAgentChatIT {

    private static boolean claudeAvailable() {
        try {
            var process = new ProcessBuilder("which", "claude")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void checkClaude() {
        Assumptions.assumeTrue(claudeAvailable(),
                "claude CLI not found on PATH — skipping IT");
    }

    @Test
    @DisplayName("[S007] AC-1: real claude session prompt returns non-empty response")
    void realClaudeSessionPromptReturnsNonEmpty() {
        // Given
        var registry = ClaudeAgentSessionRegistry.builder()
                .timeout(Duration.ofMinutes(5))
                .build();

        // When
        try (AgentSession session = registry.create(Path.of("").toAbsolutePath())) {
            var response = session.prompt("Reply with exactly: GRIMO_TEST_OK");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getText()).isNotBlank();
        }
    }
}
