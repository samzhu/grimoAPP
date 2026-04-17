package io.github.samzhu.grimo.sandbox.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SandboxConfigTest {

    private static final Path HOST_PATH = Path.of("/tmp/test-sandbox");

    @Test
    @DisplayName("[S003] AC-0 SandboxConfig rejects null imageName")
    void rejectsNullImageName() {
        // Given null imageName
        // When / Then
        assertThatThrownBy(() -> new SandboxConfig(null, HOST_PATH, "/work"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imageName");
    }

    @Test
    @DisplayName("[S003] AC-0 SandboxConfig rejects blank imageName")
    void rejectsBlankImageName() {
        // Given blank imageName
        // When / Then
        assertThatThrownBy(() -> new SandboxConfig("  ", HOST_PATH, "/work"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imageName");
    }

    @Test
    @DisplayName("[S003] AC-0 SandboxConfig rejects null hostMountPath")
    void rejectsNullHostMountPath() {
        // Given null hostMountPath
        // When / Then
        assertThatThrownBy(() -> new SandboxConfig("alpine:3.21", null, "/work"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hostMountPath");
    }

    @Test
    @DisplayName("[S003] AC-0 SandboxConfig rejects null containerMountPath")
    void rejectsNullContainerMountPath() {
        // Given null containerMountPath
        // When / Then
        assertThatThrownBy(() -> new SandboxConfig("alpine:3.21", HOST_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("containerMountPath");
    }

    @Test
    @DisplayName("[S003] AC-0 SandboxConfig accepts valid parameters")
    void acceptsValidParameters() {
        // Given valid parameters
        // When
        var config = new SandboxConfig("alpine:3.21", HOST_PATH, "/work");

        // Then
        assertThat(config.imageName()).isEqualTo("alpine:3.21");
        assertThat(config.hostMountPath()).isEqualTo(HOST_PATH);
        assertThat(config.containerMountPath()).isEqualTo("/work");
    }
}
