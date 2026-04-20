package io.github.samzhu.grimo.skills.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MarkdownParser} — S012 T1.
 * Validates SnakeYAML-backed parsing of agentskills.io SKILL.md frontmatter.
 */
class MarkdownParserTest {

    @Test
    @DisplayName("S012-T1: nested metadata map parses correctly")
    void nestedMetadataMap() {
        String skillMd = """
                ---
                name: hello
                description: "A greeting skill"
                metadata:
                  author: samzhu
                  version: "1.0.0"
                allowed-tools:
                  - Read
                  - Glob
                ---
                # Hello Skill
                Greet the user warmly.
                """;

        var parser = new MarkdownParser(skillMd);

        assertThat(parser.getFrontMatter())
                .containsEntry("name", "hello")
                .containsEntry("description", "A greeting skill");

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parser.getFrontMatter().get("metadata");
        assertThat(metadata)
                .containsEntry("author", "samzhu")
                .containsEntry("version", "1.0.0");

        @SuppressWarnings("unchecked")
        List<String> tools = (List<String>) parser.getFrontMatter().get("allowed-tools");
        assertThat(tools).containsExactly("Read", "Glob");

        assertThat(parser.getContent()).startsWith("# Hello Skill");
    }

    @Test
    @DisplayName("S012-T1: multi-line folded scalar parses correctly")
    void multiLineFoldedScalar() {
        String skillMd = """
                ---
                name: deploy
                description: >
                  A multi-line description
                  that should be folded.
                ---
                # Deploy
                """;

        var parser = new MarkdownParser(skillMd);

        assertThat(parser.getFrontMatter().get("name")).isEqualTo("deploy");
        String desc = (String) parser.getFrontMatter().get("description");
        assertThat(desc).contains("A multi-line description");
    }

    @Test
    @DisplayName("S012-T1: no frontmatter returns empty map and full content")
    void noFrontmatter() {
        String markdown = "# Just a markdown file\nNo YAML here.";

        var parser = new MarkdownParser(markdown);

        assertThat(parser.getFrontMatter()).isEmpty();
        assertThat(parser.getContent()).isEqualTo("# Just a markdown file\nNo YAML here.");
    }

    @Test
    @DisplayName("S012-T1: null input returns empty map and empty content")
    void nullInput() {
        var parser = new MarkdownParser(null);

        assertThat(parser.getFrontMatter()).isEmpty();
        assertThat(parser.getContent()).isEmpty();
    }

    @Test
    @DisplayName("S012-T1: blank input returns empty map and empty content")
    void blankInput() {
        var parser = new MarkdownParser("   ");

        assertThat(parser.getFrontMatter()).isEmpty();
        assertThat(parser.getContent()).isEmpty();
    }

    @Test
    @DisplayName("S012-T1: unclosed frontmatter returns empty map")
    void unclosedFrontmatter() {
        String markdown = "---\nname: broken\ndescription: no closing";

        var parser = new MarkdownParser(markdown);

        assertThat(parser.getFrontMatter()).isEmpty();
    }

    @Test
    @DisplayName("S012-T1: getFrontMatter returns defensive copy")
    void defensiveCopy() {
        String skillMd = """
                ---
                name: hello
                description: "A greeting skill"
                ---
                body
                """;

        var parser = new MarkdownParser(skillMd);
        Map<String, Object> fm1 = parser.getFrontMatter();
        fm1.put("extra", "injected");

        assertThat(parser.getFrontMatter()).doesNotContainKey("extra");
    }
}
