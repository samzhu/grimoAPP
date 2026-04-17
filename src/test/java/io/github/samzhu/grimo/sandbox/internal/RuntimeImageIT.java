package io.github.samzhu.grimo.sandbox.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Container.ExecResult;

/**
 * grimo-runtime 映像整合測試。
 * 驗證映像內的 CLI 工具可用性與映像大小。
 * 需要 Docker Daemon 運行中且 grimo-runtime:0.0.1-SNAPSHOT 映像已建置。
 */
@DisabledInNativeImage
@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")
@DisplayName("[S004] RuntimeImage IT")
class RuntimeImageIT {

    private static final String IMAGE = "grimo-runtime:0.0.1-SNAPSHOT";

    // 為何用版本號正規表達式而非精確字串：
    // CLI 版本隨建置時間變動，只需確認格式正確即可。
    private static final String VERSION_PATTERN = "\\d+\\.\\d+\\.\\d+";

    @Test
    @DisplayName("[S004] AC-2 claude-code CLI 可用")
    void claudeCodeCliAvailable() throws Exception {
        // Given
        try (var container = new GenericContainer<>(IMAGE)
                .withCommand("sleep", "infinity")) {
            container.start();

            // When
            ExecResult result = container.execInContainer("claude", "--version");

            // Then
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout().trim()).matches(".*" + VERSION_PATTERN + ".*");
        }
    }

    @Test
    @DisplayName("[S004] AC-3 codex CLI 可用")
    void codexCliAvailable() throws Exception {
        // Given
        try (var container = new GenericContainer<>(IMAGE)
                .withCommand("sleep", "infinity")) {
            container.start();

            // When
            ExecResult result = container.execInContainer("codex", "--version");

            // Then
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout().trim()).matches(".*" + VERSION_PATTERN + ".*");
        }
    }

    @Test
    @DisplayName("[S004] AC-3 gemini CLI 可用")
    void geminiCliAvailable() throws Exception {
        // Given
        try (var container = new GenericContainer<>(IMAGE)
                .withCommand("sleep", "infinity")) {
            container.start();

            // When
            ExecResult result = container.execInContainer("gemini", "--version");

            // Then
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout().trim()).matches(".*" + VERSION_PATTERN + ".*");
        }
    }

    @Test
    @DisplayName("[S004] AC-4 映像大小 < 1 GB")
    void imageSizeUnderOneGb() throws Exception {
        // Given
        long oneGb = 1_073_741_824L;

        // When — 使用 docker inspect 取得映像大小
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "image", "inspect", IMAGE,
                "--format", "{{.Size}}");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        int exitCode = process.waitFor();

        // Then
        assertThat(exitCode).isZero();
        long sizeBytes = Long.parseLong(output);
        assertThat(sizeBytes)
                .as("映像大小 %d bytes 應 < 1 GB (%d bytes)", sizeBytes, oneGb)
                .isLessThan(oneGb);
    }
}
