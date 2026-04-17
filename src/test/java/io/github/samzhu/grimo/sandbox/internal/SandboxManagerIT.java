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
import org.springaicommunity.sandbox.Sandbox;

@DisabledInNativeImage
@DisplayName("[S003] SandboxManager IT")
class SandboxManagerIT {

    private TestcontainersSandboxManager manager;
    private Path hostDirA;
    private Path hostDirB;

    @BeforeEach
    void setUp() throws IOException {
        manager = new TestcontainersSandboxManager();
        hostDirA = Files.createTempDirectory("s003-mgr-it-a-");
        hostDirB = Files.createTempDirectory("s003-mgr-it-b-");
    }

    @AfterEach
    void tearDown() throws IOException {
        // 關閉所有存活沙箱
        for (var id : manager.listActive()) {
            manager.close(id);
        }
        // 清理暫存目錄
        deleteTempDir(hostDirA);
        deleteTempDir(hostDirB);
    }

    @Test
    @DisplayName("[S003] AC-3 兩個並行沙箱互不可見")
    void parallelSandboxesIsolated() {
        // Given
        var configA = new SandboxConfig("alpine:3.21", hostDirA, "/work");
        var configB = new SandboxConfig("alpine:3.21", hostDirB, "/work");

        // When
        Sandbox sandboxA = manager.create(configA);
        Sandbox sandboxB = manager.create(configB);

        sandboxA.exec(ExecSpec.of("sh", "-c", "echo A > /work/who.txt"));
        var catResult = sandboxB.exec(ExecSpec.of("cat", "/work/who.txt"));

        // Then — sandboxB 看不到 sandboxA 的檔案
        assertThat(catResult.exitCode()).isNotEqualTo(0);
        assertThat(hostDirB.resolve("who.txt")).doesNotExist();
        assertThat(manager.listActive()).hasSize(2);
    }

    @Test
    @DisplayName("[S003] AC-4 close 停止並移除容器；主機目錄保留")
    void closeRemovesContainerKeepsHostDir() {
        // Given
        var config = new SandboxConfig("alpine:3.21", hostDirA, "/work");
        Sandbox sandbox = manager.create(config);
        String containerId = manager.listActive().getFirst();

        sandbox.exec(ExecSpec.of("sh", "-c", "echo persist > /work/keep.txt"));

        // When
        manager.close(containerId);

        // Then
        assertThat(manager.get(containerId)).isEmpty();
        assertThat(manager.listActive()).doesNotContain(containerId);
        assertThat(hostDirA.resolve("keep.txt")).exists();
        assertThat(hostDirA.resolve("keep.txt")).hasContent("persist\n");
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
