package io.github.samzhu.grimo.skills.domain;

import org.springaicommunity.agent.tools.SkillsTool;

/**
 * Skill registry entry: wraps framework-native {@link SkillsTool.Skill}
 * with enable/disable state from {@code .state.json}.
 *
 * <p>The {@code skill} field uses the agent-utils record type directly,
 * ensuring compatibility with {@code SkillsFunction} / {@code ToolCallback}
 * / {@code ChatClient}.
 *
 * <p>Zero Spring annotations — pure domain.
 */
public record SkillEntry(
        SkillsTool.Skill skill,
        boolean enabled
) {

    /** Convenience: extracts skill name from frontMatter. */
    public String name() {
        return skill.name();
    }
}
