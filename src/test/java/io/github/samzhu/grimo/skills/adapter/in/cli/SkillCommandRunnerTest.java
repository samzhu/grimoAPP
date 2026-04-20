package io.github.samzhu.grimo.skills.adapter.in.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.boot.DefaultApplicationArguments;

import io.github.samzhu.grimo.skills.application.port.in.SkillRegistryUseCase;
import io.github.samzhu.grimo.skills.domain.SkillEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SkillCommandRunnerTest {

    private SkillRegistryUseCase registry;
    private SkillCommandRunner runner;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outCapture;
    private ByteArrayOutputStream errCapture;

    private static SkillEntry entry(String name, boolean enabled) {
        return new SkillEntry(
                new SkillsTool.Skill("/path/" + name,
                        Map.of("name", name, "description", name + " skill"),
                        "body"),
                enabled);
    }

    @BeforeEach
    void setUp() {
        registry = mock(SkillRegistryUseCase.class);
        runner = new SkillCommandRunner(registry);
        originalOut = System.out;
        originalErr = System.err;
        outCapture = new ByteArrayOutputStream();
        errCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(errCapture));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("[S016] AC-1: 'skill list' shows skills with name, status, description")
    void skillListShowsEntries() {
        // Given
        when(registry.list()).thenReturn(List.of(
                entry("greet", true),
                entry("refactor", false)));
        var args = new DefaultApplicationArguments("skill", "list");

        // When
        runner.run(args);

        // Then
        var output = outCapture.toString();
        assertThat(output).contains("greet");
        assertThat(output).contains("enabled");
        assertThat(output).contains("greet skill");
        assertThat(output).contains("refactor");
        assertThat(output).contains("disabled");
    }

    @Test
    @DisplayName("[S016] AC-2: 'skill disable greet' calls registry.disable()")
    void skillDisableCallsRegistry() {
        // Given
        var args = new DefaultApplicationArguments("skill", "disable", "greet");

        // When
        runner.run(args);

        // Then
        verify(registry).disable("greet");
    }

    @Test
    @DisplayName("[S016] AC-2: 'skill enable greet' calls registry.enable()")
    void skillEnableCallsRegistry() {
        // Given
        var args = new DefaultApplicationArguments("skill", "enable", "greet");

        // When
        runner.run(args);

        // Then
        verify(registry).enable("greet");
    }

    @Test
    @DisplayName("[S016] AC-4: 'skill enable nonexistent' prints error to stderr")
    void skillEnableNonexistentPrintsError() {
        // Given
        doThrow(new IllegalArgumentException("Skill not found: nonexistent"))
                .when(registry).enable("nonexistent");
        var args = new DefaultApplicationArguments("skill", "enable", "nonexistent");

        // When
        runner.run(args);

        // Then
        var stderr = errCapture.toString();
        assertThat(stderr).contains("Skill not found: nonexistent");
    }

    @Test
    @DisplayName("[S016] AC-4: 'skill' without subcommand prints usage")
    void skillWithoutSubcommandPrintsUsage() {
        // Given
        var args = new DefaultApplicationArguments("skill");

        // When
        runner.run(args);

        // Then
        var output = outCapture.toString();
        assertThat(output).containsIgnoringCase("usage");
    }

    @Test
    @DisplayName("[S016] non-skill args does not interact with registry")
    void nonSkillArgsDoesNothing() {
        // Given
        var args = new DefaultApplicationArguments("chat");

        // When
        runner.run(args);

        // Then
        verifyNoInteractions(registry);
    }
}
