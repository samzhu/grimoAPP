package io.github.samzhu.grimo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Side-effect test: writes per-module canvases ({@code .adoc}) and
 * PlantUML diagrams ({@code .puml}) under {@code build/spring-modulith-docs/}
 * so the QA strategy doc and the PRD's
 * {@code build/spring-modulith-docs/} reference have actual artefacts
 * to point at.
 *
 * <p>Split from {@link ModuleArchitectureTest} per Spring Modulith's
 * idiomatic two-test pattern (S002 §2 D6): verification is a hard build
 * gate; documentation generation is an artefact emitter that must not
 * mask a verify failure.
 *
 * <p>Output filenames are NOT asserted individually — Documenter's
 * naming may differ across Modulith patch versions. The contract is:
 * directory exists, contains at least one canvas {@code .adoc} and at
 * least one diagram {@code .puml}.
 */
class DocumentationTests {

    private static final Path DOCS_DIR = Path.of("build", "spring-modulith-docs");

    @Test
    @DisplayName("AC-3 Documenter writes module canvas + diagrams to build/spring-modulith-docs/")
    void writeDocumentationSnippets() throws IOException {
        // Given — the verified module set (same input as ModuleArchitectureTest)
        var modules = ApplicationModules.of(GrimoApplication.class);

        // When — emit canvas + diagrams (idiomatic Modulith chain;
        // default output folder is the Gradle build dir)
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases();

        // Then — directory exists and contains at least one .adoc and
        // one .puml artefact (Documenter's exact filenames are not
        // contractual, only their presence)
        assertThat(DOCS_DIR).isDirectory();

        try (Stream<Path> entries = Files.walk(DOCS_DIR)) {
            var files = entries.filter(Files::isRegularFile).toList();
            assertThat(files)
                .as("Documenter output under %s", DOCS_DIR)
                .anyMatch(p -> p.getFileName().toString().endsWith(".adoc"))
                .anyMatch(p -> p.getFileName().toString().endsWith(".puml"));
        }
    }
}
