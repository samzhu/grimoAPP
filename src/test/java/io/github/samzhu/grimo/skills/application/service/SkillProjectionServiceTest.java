package io.github.samzhu.grimo.skills.application.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.SkillsTool;

import io.github.samzhu.grimo.skills.application.port.in.SkillRegistryUseCase;
import io.github.samzhu.grimo.skills.domain.SkillEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillProjectionServiceTest {

    private SkillRegistryUseCase registry;
    private SkillProjectionService service;

    @TempDir
    Path sourceDir;

    @TempDir
    Path workDir;

    @BeforeEach
    void setUp() {
        registry = mock(SkillRegistryUseCase.class);
        service = new SkillProjectionService(registry);
    }

    private SkillEntry createSkillOnDisk(String name, boolean enabled) throws IOException {
        Path skillDir = sourceDir.resolve(name);
        Files.createDirectories(skillDir);
        String content = """
                ---
                name: %s
                description: "%s skill"
                ---
                Skill instructions for %s.
                """.formatted(name, name, name);
        Files.writeString(skillDir.resolve("SKILL.md"), content);
        return new SkillEntry(
                new SkillsTool.Skill(skillDir.toString(),
                        Map.of("name", name, "description", name + " skill"),
                        "Skill instructions for " + name + "."),
                enabled);
    }

    @Test
    @DisplayName("[S016] AC-3: enabled skill is projected to workDir/.claude/skills/")
    void enabledSkillIsProjected() throws IOException {
        // Given
        SkillEntry greet = createSkillOnDisk("greet", true);
        when(registry.listEnabled()).thenReturn(List.of(greet));

        // When
        service.projectToWorkDir(workDir);

        // Then
        Path projected = workDir.resolve(".claude/skills/greet/SKILL.md");
        assertThat(projected).exists();
        assertThat(Files.readString(projected))
                .isEqualTo(Files.readString(sourceDir.resolve("greet/SKILL.md")));
    }

    @Test
    @DisplayName("[S016] AC-3: disabled skill is NOT projected")
    void disabledSkillIsNotProjected() throws IOException {
        // Given
        createSkillOnDisk("disabled-skill", false);
        when(registry.listEnabled()).thenReturn(List.of()); // disabled = not in listEnabled

        // When
        service.projectToWorkDir(workDir);

        // Then
        Path projected = workDir.resolve(".claude/skills/disabled-skill/SKILL.md");
        assertThat(projected).doesNotExist();
    }

    @Test
    @DisplayName("[S016] AC-3: projection overwrites existing file")
    void projectionOverwritesExisting() throws IOException {
        // Given
        SkillEntry greet = createSkillOnDisk("greet", true);
        when(registry.listEnabled()).thenReturn(List.of(greet));
        Path target = workDir.resolve(".claude/skills/greet/SKILL.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "old content");

        // When
        service.projectToWorkDir(workDir);

        // Then
        assertThat(Files.readString(target)).doesNotContain("old content");
        assertThat(Files.readString(target))
                .isEqualTo(Files.readString(sourceDir.resolve("greet/SKILL.md")));
    }

    @Test
    @DisplayName("[S016] AC-3: empty enabled list produces no files")
    void emptyListProducesNoFiles() {
        // Given
        when(registry.listEnabled()).thenReturn(List.of());

        // When
        service.projectToWorkDir(workDir);

        // Then
        assertThat(workDir.resolve(".claude/skills")).doesNotExist();
    }
}
