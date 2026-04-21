package io.github.samzhu.grimo.session.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Immutable event record in the session event store.
 *
 * <p>S023: Adjacency List tree — each event points to its parent via
 * {@code parentEventId}. Root events have {@code parentEventId = null}.
 *
 * @param id             unique event ID (NanoID)
 * @param sessionId      owning session ID
 * @param parentEventId  parent event in the tree (null for root)
 * @param messageType    USER, ASSISTANT, SYSTEM, or TOOL
 * @param messageContent text content of the message
 * @param messageData    structured data (JSON) — tool calls, etc.
 * @param provider       CLI provider name (null for USER events)
 * @param model          model name (null for USER events)
 * @param metadata       JSON metadata (duration, tokens, etc.)
 * @param synthetic      true for compaction-generated events
 * @param createdAt      wall-clock timestamp
 */
public record SessionEvent(
    String id,
    String sessionId,
    @Nullable String parentEventId,
    MessageType messageType,
    @Nullable String messageContent,
    @Nullable String messageData,
    @Nullable String provider,
    @Nullable String model,
    @Nullable String metadata,
    boolean synthetic,
    Instant createdAt
) {}
