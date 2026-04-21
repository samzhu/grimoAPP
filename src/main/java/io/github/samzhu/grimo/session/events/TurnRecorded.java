package io.github.samzhu.grimo.session.events;

import org.jspecify.annotations.Nullable;

/**
 * Published after each conversation turn completes (S018 redesign).
 * Contains only serializable data — safe for Spring Modulith event
 * publication persistence (Jackson serialization).
 *
 * @param sessionId        CLI-assigned session ID
 * @param sessionType      "GRIMO" or "PROJECT"
 * @param projectId        bound project ID (null for GRIMO sessions)
 * @param userMessage      the user's input text
 * @param assistantMessage the AI's response text
 * @param provider         provider name from ProviderMetadataExtractor
 * @param model            model name (e.g. "claude-sonnet-4-6")
 * @param durationMs       response duration in milliseconds
 * @param finishReason     completion reason (e.g. "SUCCESS")
 * @param tokensIn         input token count (0 if unknown)
 * @param tokensOut        output token count (0 if unknown)
 */
public record TurnRecorded(
    String sessionId,
    String sessionType,
    @Nullable String projectId,
    String userMessage,
    String assistantMessage,
    String provider,
    @Nullable String model,
    long durationMs,
    @Nullable String finishReason,
    long tokensIn,
    long tokensOut
) {}
