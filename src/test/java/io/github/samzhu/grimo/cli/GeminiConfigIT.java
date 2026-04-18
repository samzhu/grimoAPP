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
 * Integration test for Gemini CLI authentication via
 * {@code GEMINI_API_KEY} env var in a containerized environment.
 *
 * <p>設計說明：Gemini CLI 的 OAuth 憑證以 AES-256-GCM 加密，
 * 密鑰衍生自 hostname + username（scrypt），容器的 hostname/username
 * 與主機不同，**無法解密**主機產生的憑證檔。
 * 因此必須使用 {@code GEMINI_API_KEY} env var 繞過 OAuth。
 *
 * <p>Skip strategy per dev-standards §7.5:
 * <ul>
 *   <li>Class-level: {@code @DisabledIfEnvironmentVariable(CI)}</li>
 *   <li>Opt-in: {@code @EnabledIfSystemProperty(grimo.it.docker=true)}</li>
 *   <li>Per-test: {@code assumeTrue(geminiKeyPresent())}</li>
 * </ul>
 */
@DisabledInNativeImage
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Docker daemon not available on CI runners")
@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")
@DisplayName("[S006] Gemini CLI Config IT")
class GeminiConfigIT {

    private static final String GRIMO_RUNTIME_IMAGE = "grimo-runtime:0.0.1-SNAPSHOT";

    @Test
    @DisplayName("[S006] AC-4: Gemini auth via GEMINI_API_KEY in container")
    void geminiAuthViaApiKey() throws IOException, InterruptedException {
        // Given
        String apiKey = System.getenv("GEMINI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "Skipping: GEMINI_API_KEY env var not set");

        try (var container = new GenericContainer<>(GRIMO_RUNTIME_IMAGE)
                .withCommand("sleep", "infinity")
                .withEnv("GEMINI_API_KEY", apiKey)
                .withEnv("GEMINI_CLI_HOME", "/tmp/gemini-home")) {
            container.start();

            // When
            ExecResult result = container.execInContainer(
                    "gemini", "-p", "hello", "--sandbox");

            // Then
            assertThat(result.getExitCode())
                    .describedAs("exit code; stdout=%s, stderr=%s",
                            result.getStdout(), result.getStderr())
                    .isZero();
            assertThat(result.getStdout()).isNotBlank();
        }
    }
}
