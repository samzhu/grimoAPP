package io.github.samzhu.grimo.subagent.internal;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for subagent execution (S028).
 *
 * <p>Auth: container must have CLI credentials available via one of:
 * <ul>
 *   <li>Mounted host credentials (S008 scope)</li>
 *   <li>{@code apiKey} override — injects {@code ANTHROPIC_API_KEY}</li>
 * </ul>
 *
 * <p>{@code CLAUDE_CODE_OAUTH_TOKEN} is never injected to avoid
 * account suspension risk.
 *
 * @param apiKey     optional Anthropic API key (API billing)
 * @param image      Docker image name for the subagent runtime
 * @param maxTurns   max agent turns for Claude Code ({@code --max-turns})
 * @param timeout    execution timeout
 */
@ConfigurationProperties(prefix = "grimo.subagent")
public record SubagentProperties(
    @Nullable String apiKey,
    String image,
    int maxTurns,
    Duration timeout
) {}
