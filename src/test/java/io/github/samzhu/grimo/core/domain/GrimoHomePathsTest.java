package io.github.samzhu.grimo.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link GrimoHomePaths} — exercise AC-2 of S001.
 *
 * <p>The {@code $GRIMO_HOME} env-var precedence is documented in spec
 * D7 but is intentionally not tested here — tampering with env from
 * JUnit is fragile (would require {@code junit-pioneer} or a forked
 * JVM). The system-property path is the primary, test-friendly override
 * and is fully covered.
 */
class GrimoHomePathsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setSystemProperty() {
        System.setProperty("grimo.home", tempDir.toString());
    }

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("grimo.home");
    }

    @Test
    @DisplayName("AC-2 memory() returns <grimo.home>/memory and creates it on disk")
    void memoryReturnsCreatedDirUnderOverride() {
        // Given — grimo.home = @TempDir (set in @BeforeEach)
        // When
        Path memory = GrimoHomePaths.memory();

        // Then — path is tempDir/memory and exists on disk
        assertThat(memory).isEqualTo(tempDir.resolve("memory"));
        assertThat(Files.isDirectory(memory)).isTrue();
    }

    @Test
    @DisplayName("memory() is idempotent — repeat calls return the same created path")
    void memoryIsIdempotent() {
        // Given — one prior call has created the directory
        Path first = GrimoHomePaths.memory();

        // When — call again
        Path second = GrimoHomePaths.memory();

        // Then — same path, still exists (no exception thrown)
        assertThat(second).isEqualTo(first);
        assertThat(Files.isDirectory(second)).isTrue();
    }

    @Test
    @DisplayName("all subdir accessors return <grimo.home>/<name> and create the dir")
    void allSubdirAccessorsWork() {
        // Given — grimo.home = @TempDir
        // When / Then — each accessor returns the correct child path
        assertThat(GrimoHomePaths.memory())
            .isEqualTo(tempDir.resolve("memory"))
            .isDirectory();
        assertThat(GrimoHomePaths.skills())
            .isEqualTo(tempDir.resolve("skills"))
            .isDirectory();
        assertThat(GrimoHomePaths.sessions())
            .isEqualTo(tempDir.resolve("sessions"))
            .isDirectory();
        assertThat(GrimoHomePaths.worktrees())
            .isEqualTo(tempDir.resolve("worktrees"))
            .isDirectory();
        assertThat(GrimoHomePaths.logs())
            .isEqualTo(tempDir.resolve("logs"))
            .isDirectory();
        assertThat(GrimoHomePaths.config())
            .isEqualTo(tempDir.resolve("config"))
            .isDirectory();
        assertThat(GrimoHomePaths.db())
            .isEqualTo(tempDir.resolve("db"))
            .isDirectory();
    }

    @Test
    @DisplayName("without grimo.home override, home() resolves to $HOME/.grimo")
    void homeFallsBackToUserHomeDotGrimo() {
        // Given — no grimo.home and no $GRIMO_HOME. The env-var path is
        // skipped gracefully if a developer's shell exports GRIMO_HOME
        // (per dev-standards §7.5 tier 3 — assumeTrue is the canonical
        // soft skip). The system-property override is already covered
        // by the other tests.
        System.clearProperty("grimo.home");
        assumeTrue(
            System.getenv("GRIMO_HOME") == null,
            "GRIMO_HOME env is set in the shell — fallback case not testable here");
        String userHome = System.getProperty("user.home");

        // When
        Path home = GrimoHomePaths.home();

        // Then — path matches $HOME/.grimo. We do NOT assert the directory
        // exists here (home() is a pure resolver; only the typed
        // accessors like memory() materialize directories).
        assertThat(home).isEqualTo(Path.of(userHome, ".grimo"));
    }
}
