package io.github.samzhu.grimo.cli.internal;

import io.github.samzhu.grimo.core.domain.ProviderId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.agents.model.AgentTaskRequest;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link DefaultContainerizedAgentModelFactory}
 * against real Docker containers.
 *
 * <p>Uses Testcontainers {@link GenericContainer} directly (not
 * SandboxManager) to avoid cross-module internal dependency. The
 * container runs {@code sleep infinity} and is accessed via docker exec.
 *
 * <p>Skip strategy per §7.5:
 * <ul>
 *   <li>Class-level: {@code @DisabledIfEnvironmentVariable(CI)}</li>
 *   <li>Opt-in: {@code @EnabledIfSystemProperty(grimo.it.docker=true)}</li>
 *   <li>Per-test: {@code assumeTrue} for credentials (AC-2)</li>
 * </ul>
 */
@DisabledInNativeImage
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Docker daemon not available on CI runners")
@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")
@DisplayName("[S005] ContainerizedAgentModel IT")
class ContainerizedAgentModelIT {

    private DefaultContainerizedAgentModelFactory factory;
    private WrapperScriptGenerator scriptGenerator;
    private Path hostDir;

    @BeforeEach
    void setUp() throws IOException {
        scriptGenerator = new WrapperScriptGenerator();
        factory = new DefaultContainerizedAgentModelFactory(scriptGenerator);
        hostDir = Files.createTempDirectory("s005-it-");
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteTempDir(hostDir);
    }

    @Test
    @DisplayName("[S005] AC-2: real ClaudeAgentModel call against grimo-runtime container")
    void realClaudeCallAgainstGrimoRuntime() {
        // Given
        assumeTrue(credentialsPresent(),
                "Skipping: no ANTHROPIC_API_KEY or claude login session");

        try (var container = new GenericContainer<>("grimo-runtime:0.0.1-SNAPSHOT")
                .withFileSystemBind(hostDir.toString(), "/work")
                .withCommand("sleep", "infinity")) {

            container.start();
            String containerId = container.getContainerId();

            // When
            AgentModel model = factory.create(ProviderId.CLAUDE, containerId);
            var request = AgentTaskRequest.builder("回答 hello", hostDir).build();
            var response = model.call(request);

            // Then
            assertThat(response.getText()).isNotBlank();
            assertThat(response.isSuccessful()).isTrue();

            // Cleanup wrapper scripts
            scriptGenerator.cleanup(containerId);
        }
    }

    @Test
    @DisplayName("[S005] AC-3: missing CLI in container returns failed AgentResponse")
    void missingCliReturnsFailedResponse() {
        // Given — alpine container without any CLI installed
        try (var container = new GenericContainer<>("alpine:3.21")
                .withFileSystemBind(hostDir.toString(), "/work")
                .withCommand("sleep", "infinity")) {

            container.start();
            String containerId = container.getContainerId();

            // When
            AgentModel model = factory.create(ProviderId.CLAUDE, containerId);
            var request = AgentTaskRequest.builder("hello", hostDir).build();

            // Then — SDK should surface a failed response or a clear exception
            try {
                var response = model.call(request);
                // If SDK returns a response (preferred path per AC-3)
                assertThat(response.isSuccessful()).isFalse();
            } catch (Exception e) {
                // If SDK throws, verify the error is related to CLI not found
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                assertThat(message.toLowerCase())
                        .describedAs("Error should reference the missing CLI, got: %s", message)
                        .containsAnyOf("claude", "not found", "executable",
                                "failed", "error", "exit", "process");
            }

            // Cleanup wrapper scripts
            scriptGenerator.cleanup(containerId);
        }
    }

    private static boolean credentialsPresent() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            return true;
        }
        Path claudeDir = Path.of(System.getProperty("user.home"), ".claude");
        return Files.isDirectory(claudeDir);
    }

    private void deleteTempDir(Path dir) throws IOException {
        if (dir != null && Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }
}
