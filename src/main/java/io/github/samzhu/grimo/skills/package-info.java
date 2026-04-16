/**
 * Grimo :: Skills — hosts {@code SkillRegistryUseCase} (S011) for
 * scanning {@code ~/.grimo/skills/*\/SKILL.md} and managing enable /
 * disable state, plus skill injection into sub-agent containers (S012).
 *
 * <p>Empty in S002. The first concrete type lands with S011.
 *
 * <p>{@code allowedDependencies = {}} starts as the strictest white-list
 * — see Cross-Module Communication Policy in
 * {@code development-standards.md} §13.
 */
@ApplicationModule(
    displayName = "Grimo :: Skills",
    allowedDependencies = {}
)
package io.github.samzhu.grimo.skills;

import org.springframework.modulith.ApplicationModule;
