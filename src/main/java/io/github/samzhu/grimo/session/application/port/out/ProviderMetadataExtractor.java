package io.github.samzhu.grimo.session.application.port.out;

import java.util.Map;

import org.springaicommunity.agents.model.AgentResponse;

/**
 * Strategy for provider-specific metadata extraction from
 * {@link AgentResponse}. Each CLI provider (Claude, Codex, Gemini)
 * stores token counts in different keys within
 * {@code AgentResponseMetadata} providerFields.
 *
 * <p>{@code TurnRecorder} injects {@code List<ProviderMetadataExtractor>}
 * and matches via {@link #supports(AgentResponse)}. Adding a new
 * provider requires only one class — zero config, zero if-else.
 */
public interface ProviderMetadataExtractor {

    /** Provider identifier: "claude", "codex", "gemini" */
    String providerName();

    /** Match by model name prefix in AgentResponseMetadata.getModel() */
    boolean supports(AgentResponse response);

    /** Extract provider-specific token counts from providerFields HashMap */
    Map<String, Object> extractTokens(AgentResponse response);
}
