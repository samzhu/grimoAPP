/**
 * Grimo :: Skills — hosts {@code SkillRegistryUseCase} (S012) for
 * scanning {@code ~/.grimo/skills/*\/SKILL.md} and managing enable /
 * disable state. Skill injection into agent containers is S013.
 *
 * <p>Uses {@code core} for {@code GrimoHomePaths.skills()}.
 */
@ApplicationModule(
    displayName = "Grimo :: Skills",
    allowedDependencies = { "core" }
)
package io.github.samzhu.grimo.skills;

import org.springframework.modulith.ApplicationModule;
