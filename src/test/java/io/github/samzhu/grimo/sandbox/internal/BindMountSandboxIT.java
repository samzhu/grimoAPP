package io.github.samzhu.grimo.sandbox.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samzhu.grimo.sandbox.api.SandboxConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springaicommunity.sandbox.ExecSpec;

@DisabledInNativeImage
@DisplayName("[S003] BindMountSandbox IT")
class BindMountSandboxIT {

    private Path hostDir;
    private BindMountSandbox sandbox;

    @BeforeEach
    void setUp() throws IOException {
        hostDir = Files.createTempDirectory("s003-bind-mount-it-");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (sandbox != null && !sandbox.isClosed()) {
            sandbox.close();
        }
        if (hostDir != null) {
            try (var walk = Files.walk(hostDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("[S003] AC-1 bind-mount 主機目錄至容器 /work")
    void bindMountHostDirToContainerWork() throws IOException {
        // Given
        Files.writeString(hostDir.resolve("seed.txt"), "hello from host");
        var config = new SandboxConfig("alpine:3.21", hostDir, "/work");

        // When
        sandbox = new BindMountSandbox(config);

        // Then
        assertThat(sandbox).isNotNull();

        var catResult = sandbox.exec(ExecSpec.of("cat", "/work/seed.txt"));
        assertThat(catResult.stdout()).contains("hello from host");

        var lsResult = sandbox.exec(ExecSpec.of("ls", "-la", "/work"));
        assertThat(lsResult.stdout()).contains("seed.txt");
    }

    @Test
    @DisplayName("[S003] AC-2 容器內寫入即時反映於主機")
    void containerWriteReflectedOnHost() throws IOException {
        // Given
        var config = new SandboxConfig("alpine:3.21", hostDir, "/work");
        sandbox = new BindMountSandbox(config);

        // When
        sandbox.exec(ExecSpec.of("sh", "-c", "echo hello > /work/new.txt"));

        // Then
        var newFile = hostDir.resolve("new.txt");
        assertThat(newFile).exists();
        assertThat(Files.readString(newFile)).isEqualTo("hello\n");
    }
}
