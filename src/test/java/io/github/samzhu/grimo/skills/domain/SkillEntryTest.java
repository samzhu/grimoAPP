package io.github.samzhu.grimo.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;

/**
 * Unit tests for {@link SkillEntry} — S012 T1.
 * Validates domain record wraps SkillsTool.Skill + enabled state.
 */
class SkillEntryTest {

    @Test
    @DisplayName("S012-T1: SkillEntry wraps Skill and exposes name()")
    void wrapsSkillAndExposesName() {
        var skill = new SkillsTool.Skill(
                "/path/to/hello",
                Map.of("name", "hello", "description", "A greeting skill"),
                "# Hello\nBody.");

        var entry = new SkillEntry(skill, true);

        assertThat(entry.name()).isEqualTo("hello");
        assertThat(entry.enabled()).isTrue();
        assertThat(entry.skill().basePath()).isEqualTo("/path/to/hello");
        assertThat(entry.skill().content()).isEqualTo("# Hello\nBody.");
    }

    @Test
    @DisplayName("S012-T1: SkillEntry disabled state")
    void disabledState() {
        var skill = new SkillsTool.Skill(
                "/path/to/deploy",
                Map.of("name", "deploy", "description", "Deploy skill"),
                "body");

        var entry = new SkillEntry(skill, false);

        assertThat(entry.name()).isEqualTo("deploy");
        assertThat(entry.enabled()).isFalse();
    }
}
