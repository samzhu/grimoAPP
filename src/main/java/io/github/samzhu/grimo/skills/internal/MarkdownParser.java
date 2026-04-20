package io.github.samzhu.grimo.skills.internal;

import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * SnakeYAML-backed SKILL.md parser.
 * Same public interface as agent-utils {@code MarkdownParser},
 * but supports full YAML 1.1 (nested maps, lists, multi-line scalars).
 */
public class MarkdownParser {

    private final Map<String, Object> frontMatter;
    private final String content;

    public MarkdownParser(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            this.frontMatter = Map.of();
            this.content = "";
            return;
        }
        String trimmed = markdown.strip();
        if (!trimmed.startsWith("---")) {
            this.frontMatter = Map.of();
            this.content = trimmed;
            return;
        }
        int endIndex = trimmed.indexOf("\n---", 3);
        if (endIndex == -1) {
            this.frontMatter = Map.of();
            this.content = trimmed;
            return;
        }
        String yamlBlock = trimmed.substring(3, endIndex).strip();
        String body = trimmed.substring(endIndex + 4).strip();

        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = yaml.loadAs(yamlBlock, Map.class);
        this.frontMatter = (parsed != null) ? parsed : Map.of();
        this.content = body;
    }

    public Map<String, Object> getFrontMatter() {
        return new HashMap<>(frontMatter);
    }

    public String getContent() {
        return content;
    }
}
