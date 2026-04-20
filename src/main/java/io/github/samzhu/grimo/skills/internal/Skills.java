package io.github.samzhu.grimo.skills.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.SkillsTool;

/**
 * Graceful Skills loader.
 * Same method signature as agent-utils {@code Skills}, with improvements:
 * (1) single-file errors skip instead of crashing (AC-2),
 * (2) missing directory returns empty list instead of throwing,
 * (3) uses Grimo {@link MarkdownParser} (SnakeYAML).
 */
public class Skills {

    private static final Logger log = LoggerFactory.getLogger(Skills.class);
    private static final String SKILL_FILE = "SKILL.md";

    private Skills() {}

    public static List<SkillsTool.Skill> loadDirectory(String rootDirectory) {
        Path root = Path.of(rootDirectory);
        if (!Files.isDirectory(root)) {
            log.warn("Skills directory does not exist: {}", rootDirectory);
            return List.of();
        }

        List<SkillsTool.Skill> skills = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root, 2)) {
            paths.filter(p -> p.getFileName().toString().equals(SKILL_FILE))
                 .forEach(skillFile -> {
                     try {
                         String markdown = Files.readString(skillFile);
                         MarkdownParser parser = new MarkdownParser(markdown);
                         String basePath = skillFile.getParent().toString();
                         skills.add(new SkillsTool.Skill(
                                 basePath,
                                 parser.getFrontMatter(),
                                 parser.getContent()));
                     } catch (Exception e) {
                         log.warn("Skipping invalid skill at {}: {}",
                                 skillFile, e.getMessage());
                     }
                 });
        } catch (Exception e) {
            log.warn("Failed to walk skills directory {}: {}",
                    rootDirectory, e.getMessage());
        }
        return List.copyOf(skills);
    }
}
