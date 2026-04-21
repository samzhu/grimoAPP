package io.github.samzhu.grimo.session.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Immutable event record in the session event store.
 *
 * <p>Each conversation turn produces two events: {@code USER} (the
 * user's message) and {@code ASSISTANT} (the AI's response). Events
 * are append-only — never updated or deleted.
 *
 * @param sequence     auto-increment PK (null when not yet persisted)
 * @param eventId      UUID, unique per event
 * @param sessionId    CLI-assigned session ID
 * @param turnNumber   increments per turn (USER and ASSISTANT share the same turn)
 * @param eventType    USER, ASSISTANT, or SUMMARY
 * @param payloadJson  message content as JSON
 * @param metadataJson model/duration/tokens (nullable — USER events have no metadata)
 * @param synthetic    true for compaction-generated events (S014)
 * @param branch       dot-separated agent path (null = root conversation)
 * @param createdAt    wall-clock timestamp
 */
public record SessionEvent(
    @Nullable Long sequence,
    String eventId,
    String sessionId,
    int turnNumber,
    EventType eventType,
    String payloadJson,
    @Nullable String metadataJson,
    boolean synthetic,
    @Nullable String branch,
    Instant createdAt
) {}
