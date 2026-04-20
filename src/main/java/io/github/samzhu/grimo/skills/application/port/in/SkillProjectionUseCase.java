package io.github.samzhu.grimo.skills.application.port.in;

import java.nio.file.Path;

/**
 * Projects enabled skills to a working directory's CLI-native skill path
 * (S016). Called before starting the main agent so Claude Code can
 * discover and load the projected skills.
 */
public interface SkillProjectionUseCase {

    /**
     * Copies each enabled skill's {@code SKILL.md} from
     * {@code ~/.grimo/skills/<name>/} to
     * {@code workDir/.claude/skills/<name>/SKILL.md}.
     */
    void projectToWorkDir(Path workDir);
}
