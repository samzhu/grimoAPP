package io.github.samzhu.grimo.subagent.internal;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for subagent execution (S028 + S030).
 *
 * <p>Auth priority (S030 D3):
 * <ol>
 *   <li>{@code apiKey} override — injects {@code ANTHROPIC_API_KEY}</li>
 *   <li>Credential Pool (S030) — injects {@code CLAUDE_CODE_OAUTH_TOKEN}
 *       or {@code ANTHROPIC_API_KEY} based on credential type</li>
 *   <li>CLI native credentials — no auth env var (fallback)</li>
 * </ol>
 *
 * @param apiKey     optional Anthropic API key (API billing, highest priority)
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
