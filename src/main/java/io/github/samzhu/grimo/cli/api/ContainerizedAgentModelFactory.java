package io.github.samzhu.grimo.cli.api;

import io.github.samzhu.grimo.core.domain.ProviderId;
import org.springaicommunity.agents.model.AgentModel;

/**
 * Creates an {@link AgentModel} backed by a containerised CLI.
 *
 * <p>Callers own the container lifecycle (via {@code SandboxManager});
 * they pass only the {@code containerId}. This factory assembles a
 * wrapper script + the provider's SDK {@code AgentModel}.
 *
 * <p>Design note: returns the standard agent-client {@link AgentModel}.
 * Callers may {@code instanceof StreamingAgentModel} to check streaming
 * support. Claude supports sync + streaming + iterate; Gemini / Codex
 * support sync only.
 */
public interface ContainerizedAgentModelFactory {

    /**
     * Creates an {@link AgentModel} for the given provider, targeting
     * the specified running container.
     *
     * @param provider    CLAUDE / CODEX / GEMINI
     * @param containerId ID of a running Docker container
     * @return AgentModel (Claude additionally implements StreamingAgentModel + IterableAgentModel)
     * @throws IllegalArgumentException if containerId is blank
     */
    AgentModel create(ProviderId provider, String containerId);
}
