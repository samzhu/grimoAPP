package io.github.samzhu.grimo.skills.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.samzhu.grimo.skills.domain.SkillEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FileSystemSkillStoreAdapter} — S012 AC-1, AC-2, AC-3.
 * Uses real temp directory with fixture SKILL.md files.
 */
class FileSystemSkillStoreAdapterTest {

    @TempDir
    Path tempDir;

    private FileSystemSkillStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        System.setProperty("grimo.home", tempDir.toString());
        adapter = new FileSystemSkillStoreAdapter();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("grimo.home");
    }

    @Test
    @DisplayName("S012 AC-1: valid skill appears in loadAll() with correct frontMatter")
    void ac1_validSkillAppearsInList() throws IOException {
        Path helloDir = tempDir.resolve("skills/hello");
        Files.createDirectories(helloDir);
        Files.writeString(helloDir.resolve("SKILL.md"), """
                ---
                name: hello
                description: "A greeting skill"
                metadata:
                  author: samzhu
                  version: "1.0.0"
                ---
                # Hello Skill
                Greet the user warmly.
                """);

        List<SkillEntry> entries = adapter.loadAll();

        assertThat(entries).hasSize(1);
        SkillEntry entry = entries.getFirst();
        assertThat(entry.name()).isEqualTo("hello");
        assertThat(entry.enabled()).isTrue();
        assertThat(entry.skill().frontMatter()).containsEntry("description", "A greeting skill");
        @SuppressWarnings("unchecked")
        var metadata = (java.util.Map<String, Object>) entry.skill().frontMatter().get("metadata");
        assertThat(metadata).containsEntry("author", "samzhu");
    }

    @Test
    @DisplayName("S012 AC-2: invalid SKILL.md (no frontmatter) is skipped")
    void ac2_invalidSkillSkipped() throws IOException {
        // Valid skill
        Path helloDir = tempDir.resolve("skills/hello");
        Files.createDirectories(helloDir);
        Files.writeString(helloDir.resolve("SKILL.md"), """
                ---
                name: hello
                description: "A greeting skill"
                ---
                # Hello
                """);

        // Invalid skill — no frontmatter
        Path brokenDir = tempDir.resolve("skills/broken");
        Files.createDirectories(brokenDir);
        Files.writeString(brokenDir.resolve("SKILL.md"), "# Just markdown, no YAML frontmatter");

        List<SkillEntry> entries = adapter.loadAll();

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().name()).isEqualTo("hello");
    }

    @Test
    @DisplayName("S012 AC-2: skill with missing name is skipped")
    void ac2_missingNameSkipped() throws IOException {
        Path noNameDir = tempDir.resolve("skills/noname");
        Files.createDirectories(noNameDir);
        Files.writeString(noNameDir.resolve("SKILL.md"), """
                ---
                description: "No name field"
                ---
                body
                """);

        List<SkillEntry> entries = adapter.loadAll();

        assertThat(entries).isEmpty();
    }

    @Test
    @DisplayName("S012 AC-2: skill with name mismatch to directory is skipped")
    void ac2_nameMismatchSkipped() throws IOException {
        Path wrongDir = tempDir.resolve("skills/wrong-dir");
        Files.createDirectories(wrongDir);
        Files.writeString(wrongDir.resolve("SKILL.md"), """
                ---
                name: correct-name
                description: "Name does not match directory"
                ---
                body
                """);

        List<SkillEntry> entries = adapter.loadAll();

        assertThat(entries).isEmpty();
    }

    @Test
    @DisplayName("S012 AC-3: disable persists to .state.json and survives reload")
    void ac3_disablePersistsAcrossReload() throws IOException {
        Path helloDir = tempDir.resolve("skills/hello");
        Files.createDirectories(helloDir);
        Files.writeString(helloDir.resolve("SKILL.md"), """
                ---
                name: hello
                description: "A greeting skill"
                ---
                # Hello
                """);

        // First load — default enabled
        List<SkillEntry> before = adapter.loadAll();
        assertThat(before.getFirst().enabled()).isTrue();

        // Save disabled state
        adapter.saveState(java.util.Map.of("hello", false));

        // Simulate restart — new adapter instance
        var newAdapter = new FileSystemSkillStoreAdapter();
        List<SkillEntry> after = newAdapter.loadAll();

        assertThat(after).hasSize(1);
        assertThat(after.getFirst().name()).isEqualTo("hello");
        assertThat(after.getFirst().enabled()).isFalse();
    }

    @Test
    @DisplayName("S012 AC-3: .state.json file contains correct structure")
    void ac3_stateJsonStructure() throws IOException {
        Path helloDir = tempDir.resolve("skills/hello");
        Files.createDirectories(helloDir);
        Files.writeString(helloDir.resolve("SKILL.md"), """
                ---
                name: hello
                description: "A greeting skill"
                ---
                body
                """);

        adapter.saveState(java.util.Map.of("hello", false));

        Path stateFile = tempDir.resolve("skills/.state.json");
        assertThat(stateFile).exists();
        String json = Files.readString(stateFile);
        assertThat(json).contains("\"version\"");
        assertThat(json).contains("\"hello\"");
        assertThat(json).contains("\"enabled\"");
    }

    @Test
    @DisplayName("S012 AC-2: skill with empty description is skipped")
    void ac2_emptyDescriptionSkipped() throws IOException {
        Path emptyDescDir = tempDir.resolve("skills/emptydesc");
        Files.createDirectories(emptyDescDir);
        Files.writeString(emptyDescDir.resolve("SKILL.md"), """
                ---
                name: emptydesc
                description: ""
                ---
                body
                """);

        List<SkillEntry> entries = adapter.loadAll();

        assertThat(entries).isEmpty();
    }

    @Test
    @DisplayName("S012 AC-2: skill with null description is skipped")
    void ac2_nullDescriptionSkipped() throws IOException {
        Path nullDescDir = tempDir.resolve("skills/nulldesc");
        Files.createDirectories(nullDescDir);
        Files.writeString(nullDescDir.resolve("SKILL.md"), """
                ---
                name: nulldesc
                description:
                ---
                body
                """);

        List<SkillEntry> entries = adapter.loadAll();

        assertThat(entries).isEmpty();
    }

    @Test
    @DisplayName("S012: empty skills directory returns empty list")
    void emptyDirectory() {
        List<SkillEntry> entries = adapter.loadAll();
        assertThat(entries).isEmpty();
    }
}
