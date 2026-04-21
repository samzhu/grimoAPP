package io.github.samzhu.grimo.session.events;

import org.jspecify.annotations.Nullable;

/**
 * Published after each conversation turn completes. Contains only
 * serializable data extracted from {@code AgentResponse} — safe for
 * Spring Modulith event publication persistence (Jackson serialization).
 *
 * @param sessionId        CLI-assigned session ID
 * @param turnNumber       1-based turn counter
 * @param userMessage      the user's input text
 * @param assistantMessage the AI's response text
 * @param model            model name (e.g. "claude-sonnet-4-20250514")
 * @param durationMs       response duration in milliseconds
 * @param finishReason     completion reason (e.g. "SUCCESS")
 * @param provider         provider name from ProviderMetadataExtractor (e.g. "claude")
 * @param tokensIn         input token count (0 if unknown)
 * @param tokensOut        output token count (0 if unknown)
 */
public record TurnRecorded(
    String sessionId,
    int turnNumber,
    String userMessage,
    String assistantMessage,
    @Nullable String model,
    long durationMs,
    @Nullable String finishReason,
    String provider,
    long tokensIn,
    long tokensOut
) {}
