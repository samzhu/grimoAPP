package io.github.samzhu.grimo.session.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Immutable event record in the session event store (S018 redesign).
 *
 * <p>Aligned with Spring AI Session naming: {@code messageType},
 * {@code messageContent}, {@code messageData}. Each conversation
 * message produces one event. Events are append-only.
 *
 * @param id             unique event ID (NanoID)
 * @param sessionId      owning session ID
 * @param messageType    USER, ASSISTANT, SYSTEM, or TOOL
 * @param messageContent text content of the message
 * @param messageData    structured data (JSON) — tool calls, etc.
 * @param provider       CLI provider name (null for USER events)
 * @param model          model name (null for USER events)
 * @param metadata       JSON metadata (duration, tokens, etc.)
 * @param synthetic      true for compaction-generated events
 * @param branch         reserved for future branching (null)
 * @param createdAt      wall-clock timestamp
 */
public record SessionEvent(
    String id,
    String sessionId,
    MessageType messageType,
    @Nullable String messageContent,
    @Nullable String messageData,
    @Nullable String provider,
    @Nullable String model,
    @Nullable String metadata,
    boolean synthetic,
    @Nullable String branch,
    Instant createdAt
) {}
