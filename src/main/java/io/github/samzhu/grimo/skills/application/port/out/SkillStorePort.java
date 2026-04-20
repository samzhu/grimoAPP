package io.github.samzhu.grimo.skills.application.port.out;

import java.util.List;
import java.util.Map;

import io.github.samzhu.grimo.skills.domain.SkillEntry;

/**
 * Outbound port for skill storage — scans the file system and
 * manages the {@code .state.json} persistence file.
 */
public interface SkillStorePort {

    /** Scan skills directory and merge enable/disable state from .state.json. */
    List<SkillEntry> loadAll();

    /** Write enable/disable state to .state.json. Key = skill name, value = enabled. */
    void saveState(Map<String, Boolean> enabledMap);
}
