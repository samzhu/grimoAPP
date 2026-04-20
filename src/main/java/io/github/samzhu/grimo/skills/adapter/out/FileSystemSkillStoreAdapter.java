package io.github.samzhu.grimo.skills.adapter.out;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.samzhu.grimo.core.domain.GrimoHomePaths;
import io.github.samzhu.grimo.skills.application.port.out.SkillStorePort;
import io.github.samzhu.grimo.skills.domain.SkillEntry;
import io.github.samzhu.grimo.skills.internal.Skills;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.stereotype.Component;

@Component
class FileSystemSkillStoreAdapter implements SkillStorePort {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSkillStoreAdapter.class);
    private static final String STATE_FILE = ".state.json";
    // agentskills.io: 1-64 chars, lowercase alphanum + hyphen, no leading/trailing/double hyphen
    private static final Pattern VALID_NAME =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$");

    private final ObjectMapper objectMapper;

    FileSystemSkillStoreAdapter() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<SkillEntry> loadAll() {
        Path skillsDir = GrimoHomePaths.skills();
        List<SkillsTool.Skill> raw = Skills.loadDirectory(skillsDir.toString());
        Map<String, Boolean> state = readStateJson(skillsDir.resolve(STATE_FILE));

        return raw.stream()
                .filter(this::isValid)
                .map(s -> toEntry(s, state))
                .toList();
    }

    @Override
    public void saveState(Map<String, Boolean> enabledMap) {
        Path stateFile = GrimoHomePaths.skills().resolve(STATE_FILE);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        Map<String, Object> skills = new LinkedHashMap<>();
        enabledMap.forEach((name, enabled) ->
                skills.put(name, Map.of("enabled", enabled)));
        root.put("skills", skills);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), root);
        } catch (IOException e) {
            log.warn("Failed to write state file {}: {}", stateFile, e.getMessage());
        }
    }

    private boolean isValid(SkillsTool.Skill raw) {
        var fm = raw.frontMatter();
        if (fm == null || !fm.containsKey("name") || !fm.containsKey("description")) {
            log.warn("Skipping skill at {}: missing required name or description",
                    raw.basePath());
            return false;
        }
        Object desc = fm.get("description");
        if (desc == null || desc.toString().isBlank()) {
            log.warn("Skipping skill at {}: description is empty",
                    raw.basePath());
            return false;
        }
        String name = raw.name();
        if (!VALID_NAME.matcher(name).matches() || name.contains("--")) {
            log.warn("Skipping skill at {}: name '{}' violates agentskills.io format",
                    raw.basePath(), name);
            return false;
        }
        String dirName = Path.of(raw.basePath()).getFileName().toString();
        if (!name.equals(dirName)) {
            log.warn("Skipping skill at {}: name '{}' does not match directory '{}'",
                    raw.basePath(), name, dirName);
            return false;
        }
        return true;
    }

    private SkillEntry toEntry(SkillsTool.Skill skill, Map<String, Boolean> state) {
        boolean enabled = state.getOrDefault(skill.name(), true);
        return new SkillEntry(skill, enabled);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> readStateJson(Path stateFile) {
        if (!Files.isRegularFile(stateFile)) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(
                    stateFile.toFile(), new TypeReference<>() {});
            Map<String, Object> skills = (Map<String, Object>) root.get("skills");
            if (skills == null) {
                return Map.of();
            }
            Map<String, Boolean> result = new LinkedHashMap<>();
            skills.forEach((name, value) -> {
                if (value instanceof Map<?, ?> m) {
                    Object enabled = m.get("enabled");
                    if (enabled instanceof Boolean b) {
                        result.put(name, b);
                    }
                }
            });
            return result;
        } catch (IOException e) {
            log.warn("Failed to read state file {}: {}", stateFile, e.getMessage());
            return Map.of();
        }
    }
}
