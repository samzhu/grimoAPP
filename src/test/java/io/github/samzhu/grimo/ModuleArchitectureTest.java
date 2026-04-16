package io.github.samzhu.grimo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Continuous gate: every {@code ./gradlew test} re-runs
 * {@link ApplicationModules#verify()} against the live module graph.
 * Adding a cross-module reference that violates a sibling module's
 * {@code allowedDependencies} (or contradicts the OPEN status of
 * {@code core}) fails this test before the offending commit reaches
 * main.
 *
 * <p>Pure JUnit — no Spring context required. {@code verify()} is a
 * structural check on the compiled classpath that scans for
 * {@code @ApplicationModule}-annotated {@code package-info.class} files
 * starting from the boot application's package.
 *
 * <p>The companion {@link DocumentationTests} writes the canvas + diagrams;
 * verification and documentation are split per Spring Modulith's
 * idiomatic two-test pattern (S002 §2 D6).
 */
class ModuleArchitectureTest {

    @Test
    @DisplayName("AC-1 ApplicationModules.verify() passes on the live module graph")
    void modulesVerify() {
        // Given — the modules visible from GrimoApplication's package
        // When  — verify the structural rules
        // Then  — no Violations thrown
        ApplicationModules.of(GrimoApplication.class).verify();
    }
}
