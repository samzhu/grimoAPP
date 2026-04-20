package io.github.samzhu.grimo.skills.application.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.skills.application.port.in.SkillProjectionUseCase;
import io.github.samzhu.grimo.skills.application.port.in.SkillRegistryUseCase;
import io.github.samzhu.grimo.skills.domain.SkillEntry;

@Service
class SkillProjectionService implements SkillProjectionUseCase {

    private static final Logger log = LoggerFactory.getLogger(SkillProjectionService.class);

    private final SkillRegistryUseCase registry;

    SkillProjectionService(SkillRegistryUseCase registry) {
        this.registry = registry;
    }

    @Override
    public void projectToWorkDir(Path workDir) {
        List<SkillEntry> enabled = registry.listEnabled();
        if (enabled.isEmpty()) {
            log.debug("No enabled skills to project");
            return;
        }
        Path targetBase = workDir.resolve(".claude/skills");
        for (SkillEntry entry : enabled) {
            try {
                Path source = Path.of(entry.skill().basePath()).resolve("SKILL.md");
                Path target = targetBase.resolve(entry.name()).resolve("SKILL.md");
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                log.debug("Projected skill '{}' to {}", entry.name(), target);
            } catch (IOException e) {
                log.warn("Failed to project skill '{}': {}", entry.name(), e.getMessage());
            }
        }
        log.info("Projected {} skill(s) to {}", enabled.size(), targetBase);
    }
}
