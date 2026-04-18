package io.github.samzhu.grimo.cli.internal;

import io.github.samzhu.grimo.cli.api.ContainerizedAgentModelFactory;
import io.github.samzhu.grimo.core.domain.ProviderId;
import org.springaicommunity.agents.claude.ClaudeAgentModel;
import org.springaicommunity.agents.codex.CodexAgentModel;
import org.springaicommunity.agents.codex.CodexAgentOptions;
import org.springaicommunity.agents.codexsdk.CodexClient;
import org.springaicommunity.agents.gemini.GeminiAgentModel;
import org.springaicommunity.agents.gemini.GeminiAgentOptions;
import org.springaicommunity.agents.geminisdk.GeminiClient;
import org.springaicommunity.agents.model.AgentModel;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Assembles a containerised {@link AgentModel} by generating a wrapper
 * script that delegates CLI invocations via {@code docker exec -i} and
 * injecting the script path into each provider's SDK.
 *
 * <p>Design note: Claude uses {@code ClaudeAgentModel.builder().claudePath()}
 * (per-instance, no global state). Gemini uses {@code GeminiAgentOptions.setExecutablePath()}
 * (per-options instance). Codex uses {@code CodexAgentOptions.builder().executablePath()}.
 * MVP limits to one container at a time; S010 handles concurrent sub-agents.
 */
@Service
class DefaultContainerizedAgentModelFactory implements ContainerizedAgentModelFactory {

    private final WrapperScriptGenerator scriptGenerator;

    DefaultContainerizedAgentModelFactory(WrapperScriptGenerator scriptGenerator) {
        this.scriptGenerator = scriptGenerator;
    }

    @Override
    public AgentModel create(ProviderId provider, String containerId) {
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalArgumentException("containerId must not be blank");
        }

        Path wrapperScript = scriptGenerator.generate(provider, containerId);

        return switch (provider) {
            case CLAUDE -> ClaudeAgentModel.builder()
                    .claudePath(wrapperScript.toString())
                    .workingDirectory(Path.of("/work"))
                    .timeout(Duration.ofMinutes(5))
                    .build();
            case GEMINI -> {
                var opts = new GeminiAgentOptions();
                opts.setExecutablePath(wrapperScript.toString());
                yield new GeminiAgentModel(
                        GeminiClient.create(),
                        opts,
                        null);  // no Sandbox — wrapper script handles container routing
            }
            case CODEX -> {
                var opts = CodexAgentOptions.builder()
                        .executablePath(wrapperScript.toString())
                        .fullAuto(true)
                        .build();
                yield new CodexAgentModel(
                        CodexClient.create(),
                        opts,
                        null);  // Sandbox field upstream WIP
            }
        };
    }
}
