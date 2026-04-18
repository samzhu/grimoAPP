package io.github.samzhu.grimo.cli.internal;

import io.github.samzhu.grimo.core.domain.ProviderId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WrapperScriptGeneratorTest {

    private final WrapperScriptGenerator generator = new WrapperScriptGenerator();

    @AfterEach
    void cleanup() {
        // Clean up any generated scripts
        generator.cleanup("test-abc123");
        generator.cleanup("test-xyz");
    }

    @Test
    @DisplayName("[S005] AC-infrastructure: generate CLAUDE wrapper script with correct content")
    void generateClaudeWrapperScript() throws IOException {
        // Given
        var provider = ProviderId.CLAUDE;
        var containerId = "test-abc123";

        // When
        Path scriptPath = generator.generate(provider, containerId);

        // Then
        assertThat(scriptPath).exists();
        assertThat(Files.isExecutable(scriptPath)).isTrue();

        String content = Files.readString(scriptPath);
        assertThat(content).contains("docker exec -i");
        assertThat(content).contains("test-abc123");
        assertThat(content).contains("claude");
    }

    @Test
    @DisplayName("[S005] AC-infrastructure: generate GEMINI wrapper script uses gemini binary")
    void generateGeminiWrapperScript() throws IOException {
        // Given
        var provider = ProviderId.GEMINI;
        var containerId = "test-xyz";

        // When
        Path scriptPath = generator.generate(provider, containerId);

        // Then
        String content = Files.readString(scriptPath);
        assertThat(content).contains("gemini");
        assertThat(content).doesNotContain("claude");
    }

    @Test
    @DisplayName("[S005] AC-infrastructure: cleanup removes generated wrapper script")
    void cleanupRemovesScript() {
        // Given
        Path scriptPath = generator.generate(ProviderId.CLAUDE, "test-abc123");
        assertThat(scriptPath).exists();

        // When
        generator.cleanup("test-abc123");

        // Then
        assertThat(scriptPath).doesNotExist();
    }
}
