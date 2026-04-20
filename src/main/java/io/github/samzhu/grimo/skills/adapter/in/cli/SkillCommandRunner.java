package io.github.samzhu.grimo.skills.adapter.in.cli;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.github.samzhu.grimo.skills.application.port.in.SkillRegistryUseCase;
import io.github.samzhu.grimo.skills.domain.SkillEntry;

@Component
class SkillCommandRunner implements ApplicationRunner {

    private final SkillRegistryUseCase registry;

    SkillCommandRunner(SkillRegistryUseCase registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> nonOption = args.getNonOptionArgs();
        if (!nonOption.contains("skill")) {
            return;
        }
        if (nonOption.size() < 2) {
            printUsage();
            return;
        }
        String action = nonOption.get(nonOption.indexOf("skill") + 1);
        switch (action) {
            case "list" -> listSkills();
            case "enable" -> toggleSkill(nonOption, true);
            case "disable" -> toggleSkill(nonOption, false);
            default -> printUsage();
        }
    }

    private void listSkills() {
        List<SkillEntry> skills = registry.list();
        if (skills.isEmpty()) {
            System.out.println("No skills found in ~/.grimo/skills/");
            return;
        }
        System.out.printf("Skills (%d found):%n", skills.size());
        for (SkillEntry entry : skills) {
            String status = entry.enabled() ? "enabled" : "disabled";
            Object desc = entry.skill().frontMatter().getOrDefault("description", "");
            System.out.printf("  %-20s %-10s %s%n", entry.name(), status, desc);
        }
    }

    private void toggleSkill(List<String> nonOption, boolean enable) {
        int skillIdx = nonOption.indexOf("skill");
        if (nonOption.size() < skillIdx + 3) {
            System.out.println("Usage: grimo skill " + (enable ? "enable" : "disable") + " <name>");
            return;
        }
        String name = nonOption.get(skillIdx + 2);
        try {
            if (enable) {
                registry.enable(name);
                System.out.println("Enabled: " + name);
            } else {
                registry.disable(name);
                System.out.println("Disabled: " + name);
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    private void printUsage() {
        System.out.println("Usage: grimo skill <command>");
        System.out.println("Commands:");
        System.out.println("  list                List all skills");
        System.out.println("  enable <name>       Enable a skill");
        System.out.println("  disable <name>      Disable a skill");
    }
}
