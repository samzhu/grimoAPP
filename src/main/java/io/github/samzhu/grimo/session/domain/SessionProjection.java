package io.github.samzhu.grimo.session.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Materialized summary of a session — one row per session in
 * {@code grimo_session} (S018 redesign).
 *
 * @param id              session ID (NanoID from CLI)
 * @param sessionType     "GRIMO" or "PROJECT"
 * @param projectId       bound project ID (null for GRIMO sessions)
 * @param status          ACTIVE or CLOSED
 * @param turnCount       number of completed turns
 * @param totalTokensIn   cumulative input tokens
 * @param totalTokensOut  cumulative output tokens
 * @param totalDurationMs cumulative response duration in milliseconds
 * @param eventVersion    CAS version for optimistic concurrency
 * @param workDir         working directory path
 * @param createdAt       session creation time
 * @param lastActiveAt    last turn timestamp
 */
public record SessionProjection(
    String id,
    String sessionType,
    @Nullable String projectId,
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
