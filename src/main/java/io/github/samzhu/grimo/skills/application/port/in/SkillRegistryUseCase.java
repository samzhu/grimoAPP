package io.github.samzhu.grimo.skills.application.port.in;

import java.util.List;
import java.util.Optional;

import io.github.samzhu.grimo.skills.domain.SkillEntry;

/**
 * Inbound port for the Skill registry (S012).
 * Provides discovery, query, and enable/disable operations for skills.
 */
public interface SkillRegistryUseCase {

    /** All discovered skills with their enable/disable state. */
    List<SkillEntry> list();

    /** Only enabled skills (used by S013 for container injection). */
    List<SkillEntry> listEnabled();

    /** Look up a single skill by name. */
    Optional<SkillEntry> get(String name);

    /** Enable a skill and persist state to .state.json. */
    void enable(String name);

    /** Disable a skill and persist state to .state.json. */
    void disable(String name);
}
