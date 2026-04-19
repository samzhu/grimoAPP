package org.springaicommunity.agents.claude;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentSession;
import org.springaicommunity.agents.model.AgentSessionRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S011-T1: Verifies ClaudeSessionConnector can resume the most recent
 * session using --continue flag and return a standard AgentSession.
 *
 * <p>Requires host claude CLI installed and logged in.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("S011-T1: ClaudeSessionConnector")
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Requires host claude CLI — not available in CI")
class ClaudeSessionConnectorIT {

    private static boolean claudeAvailable;
    private static final Path WORK_DIR = Path.of("").toAbsolutePath();
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    @BeforeAll
    static void checkClaude() {
        try {
            var process = new ProcessBuilder("which", "claude").start();
            claudeAvailable = process.waitFor() == 0;
        } catch (Exception e) {
            claudeAvailable = false;
        }
        Assumptions.assumeTrue(claudeAvailable, "claude CLI not found — skipping");
    }

    @Test
    @Order(1)
    @DisplayName("S011 AC-1: continueLastSession returns AgentSession with --continue flag")
    void continueLastSession_returnsAgentSession() {
        // Given: a prior session exists (create one, plant context, close it)
        AgentSessionRegistry registry = ClaudeAgentSessionRegistry.builder()
                .timeout(TIMEOUT)
                .build();
        AgentSession first = registry.create(WORK_DIR);
        first.prompt("Remember: my favourite color is BLUE. Acknowledge briefly.");
        first.close();

        // When: continue last session via connector
        AgentSession resumed = ClaudeSessionConnector.continueLastSession(
                WORK_DIR, TIMEOUT, null, null);

        // Then: returns a valid AgentSession
        assertThat(resumed).isNotNull();
        assertThat(resumed.getSessionId()).isNotNull().isNotEmpty();
        assertThat(resumed).isInstanceOf(ClaudeAgentSession.class);

        // And: can prompt and get response referencing prior context
        AgentResponse response = resumed.prompt(
                "What is my favourite color? Just say the color.");
        assertThat(response.getText().toUpperCase()).contains("BLUE");

        resumed.close();
    }
}
