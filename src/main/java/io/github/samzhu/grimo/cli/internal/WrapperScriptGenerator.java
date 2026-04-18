package io.github.samzhu.grimo.cli.internal;

import io.github.samzhu.grimo.core.domain.ProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Generates a shell wrapper script that delegates CLI invocations to
 * a running Docker container via {@code docker exec -i}.
 *
 * <p>Design note: each SDK (claude-code-sdk, gemini-sdk, codex-sdk)
 * internally spawns a CLI binary via ProcessBuilder. The wrapper script
 * is the only injection point that lets us redirect execution into a
 * container without modifying the SDK. stdin/stdout pipes transparently
 * through {@code docker exec -i}.
 */
class WrapperScriptGenerator {

    private static final Logger log = LoggerFactory.getLogger(WrapperScriptGenerator.class);
    private static final Path WRAPPER_DIR = Path.of(System.getProperty("java.io.tmpdir"), "grimo", "wrappers");

    /**
     * Generates a wrapper script for the given provider and container,
     * returns the script path (already chmod +x).
     */
    Path generate(ProviderId provider, String containerId) {
        String cliBinary = switch (provider) {
            case CLAUDE -> "claude";
            case CODEX -> "codex";
            case GEMINI -> "gemini";
        };

        // Script conditionally forwards API key env vars if set on the host
        String scriptContent = """
                #!/bin/bash
                ENV_ARGS=""
                [ -n "$ANTHROPIC_API_KEY" ] && ENV_ARGS="$ENV_ARGS -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY"
                [ -n "$GEMINI_API_KEY" ] && ENV_ARGS="$ENV_ARGS -e GEMINI_API_KEY=$GEMINI_API_KEY"
                [ -n "$OPENAI_API_KEY" ] && ENV_ARGS="$ENV_ARGS -e OPENAI_API_KEY=$OPENAI_API_KEY"
                exec docker exec -i $ENV_ARGS "%s" "%s" "$@"
                """.formatted(containerId, cliBinary);

        try {
            Files.createDirectories(WRAPPER_DIR);
            Path scriptPath = WRAPPER_DIR.resolve("grimo-%s-%s.sh".formatted(cliBinary, containerId));
            Files.writeString(scriptPath, scriptContent);
            Files.setPosixFilePermissions(scriptPath, PosixFilePermissions.fromString("rwxr-xr-x"));
            log.debug("Generated wrapper script: {}", scriptPath);
            return scriptPath;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate wrapper script for " + provider, e);
        }
    }

    /**
     * Removes all wrapper scripts for the given container.
     */
    void cleanup(String containerId) {
        try {
            if (!Files.isDirectory(WRAPPER_DIR)) {
                return;
            }
            try (var stream = Files.list(WRAPPER_DIR)) {
                stream.filter(p -> p.getFileName().toString().contains(containerId))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                                log.debug("Cleaned up wrapper script: {}", p);
                            } catch (IOException e) {
                                log.warn("Failed to delete wrapper script: {}", p, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to list wrapper directory for cleanup: {}", WRAPPER_DIR, e);
        }
    }
}
