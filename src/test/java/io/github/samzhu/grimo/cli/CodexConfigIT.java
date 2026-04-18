package io.github.samzhu.grimo.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for Codex CLI authentication via RO-mounted
 * {@code ~/.codex/} directory in a containerized environment.
 *
 * <p>設計說明：Codex 預設 {@code AuthCredentialsStoreMode::File}（非 Keychain），
 * {@code ~/.codex/auth.json} 為明文 JSON。RO 掛載至容器 {@code /root/.codex/}
 * 並設定 {@code CODEX_HOME=/root/.codex}。
 *
 * <p>Skip strategy per dev-standards §7.5:
 * <ul>
 *   <li>Class-level: {@code @DisabledIfEnvironmentVariable(CI)}</li>
 *   <li>Opt-in: {@code @EnabledIfSystemProperty(grimo.it.docker=true)}</li>
 *   <li>Per-test: {@code assumeTrue(codexAuthPresent())}</li>
 * </ul>
 */
@DisabledInNativeImage
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Docker daemon not available on CI runners")
@EnabledIfSystemProperty(named = "grimo.it.docker", matches = "true")
@DisplayName("[S006] Codex CLI Config IT")
class CodexConfigIT {

    private static final String GRIMO_RUNTIME_IMAGE = "grimo-runtime:0.0.1-SNAPSHOT";

    @Test
    @DisplayName("[S006] AC-3: Codex auth via RO mount ~/.codex/ to container")
    void codexAuthViaMount() throws IOException, InterruptedException {
        // Given
        Path codexHome = Path.of(System.getProperty("user.home"), ".codex");
        Path authFile = codexHome.resolve("auth.json");
        assumeTrue(Files.exists(authFile),
                "Skipping: ~/.codex/auth.json not found");

        // 設計說明：Codex CLI 需要寫入 CODEX_HOME（cache、session 檔案），
        // 無法對整個目錄 RO 掛載。改為僅 RO 掛載 auth.json 至容器內
        // writable 的 CODEX_HOME，Codex 可讀取認證且可寫 cache。
        try (var container = new GenericContainer<>(GRIMO_RUNTIME_IMAGE)
                .withCommand("sleep", "infinity")
                .withFileSystemBind(authFile.toString(),
                        "/root/.codex/auth.json", BindMode.READ_ONLY)
                .withEnv("CODEX_HOME", "/root/.codex")) {
            container.start();

            // When — --skip-git-repo-check avoids "not inside a trusted directory" error;
            // --ephemeral avoids persisting session files to disk
            ExecResult result = container.execInContainer(
                    "codex", "exec", "respond with just the word hello",
                    "--skip-git-repo-check", "--ephemeral", "--json");

            // Then
            assertThat(result.getExitCode())
                    .describedAs("exit code; stdout=%s, stderr=%s",
                            result.getStdout(), result.getStderr())
                    .isZero();
            assertThat(result.getStdout()).isNotBlank();
        }
    }
}
