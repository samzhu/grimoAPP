package io.github.samzhu.grimo.session.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Materialized summary of a session — one row per session in
 * {@code grimo_session}.
 *
 * <p>Updated incrementally by {@code TurnRecorder} after each turn.
 * Fork lineage ({@code parentId} + {@code forkTurn}) is schema-reserved
 * for future cross-provider switch and Claude Code {@code /branch}.
 *
 * @param id              session ID (= CLI-assigned UUID)
 * @param parentId        null for root sessions; set when forked or switched
 * @param forkTurn        turn number at fork point (null for root)
 * @param provider        CLI provider name (dynamic via ProviderMetadataExtractor)
 * @param status          ACTIVE or CLOSED
 * @param turnCount       number of completed turns
 * @param totalTokensIn   cumulative input tokens
 * @param totalTokensOut  cumulative output tokens
 * @param totalDurationMs cumulative response duration in milliseconds
 * @param eventVersion    CAS version for optimistic concurrency (S014 compaction)
 * @param workDir         working directory path
 * @param createdAt       session creation time
 * @param lastActiveAt    last turn timestamp
 */
public record SessionProjection(
    String id,
    @Nullable String parentId,
    @Nullable Integer forkTurn,
    String provider,
    SessionStatus status,
    int turnCount,
    long totalTokensIn,
    long totalTokensOut,
    long totalDurationMs,
    long eventVersion,
    @Nullable String workDir,
    Instant createdAt,
    Instant lastActiveAt
) {}
