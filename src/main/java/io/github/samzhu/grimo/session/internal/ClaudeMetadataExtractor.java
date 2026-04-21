package io.github.samzhu.grimo.session.internal;

import java.util.HashMap;
import java.util.Map;

import org.springaicommunity.agents.model.AgentResponse;
import org.springframework.stereotype.Component;

import io.github.samzhu.grimo.session.application.port.out.ProviderMetadataExtractor;

/**
 * Claude-specific metadata extractor. Detects Claude CLI responses
 * via the {@code phaseCapture} key in {@code AgentResponseMetadata}
 * (a Claude-specific field from {@code claude-agent-sdk-java}).
 *
 * <p>Note: {@code AgentResponseMetadata.getModel()} returns empty
 * string in agent-client 0.12.2 — cannot be used for detection.
 * The {@code phaseCapture} key is reliably present in all Claude
 * CLI responses (verified by {@code AgentResponseMetadataPocIT}).
 */
@Component
public class ClaudeMetadataExtractor implements ProviderMetadataExtractor {

    @Override
    public String providerName() {
        return "claude";
    }

    @Override
    public boolean supports(AgentResponse response) {
        var meta = response.getMetadata();
        // Primary: check for Claude-specific "phaseCapture" key
        if (meta.containsKey("phaseCapture")) {
            return true;
        }
        // Fallback: model name prefix (future SDK versions may populate this)
        String model = meta.getModel();
        return model != null && model.startsWith("claude-");
    }

    @Override
    public Map<String, Object> extractTokens(AgentResponse response) {
        var meta = response.getMetadata();
        var map = new HashMap<String, Object>();
        if (meta.get("inputTokens") instanceof Number n)
            map.put("tokensIn", n.longValue());
        if (meta.get("outputTokens") instanceof Number n)
            map.put("tokensOut", n.longValue());
        return map;
    }
}
