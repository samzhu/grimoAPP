package io.github.samzhu.grimo.task.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * A generic work item — research, development, documentation, analysis.
 * Not limited to dev tasks (worktree/Docker/PR is S020's concern).
 */
public record Task(
    String id,
    int taskNumber,
    @Nullable String projectId,
    String title,
    @Nullable String body,
    TaskStatus status,
    TaskPriority priority,
    @Nullable String labelsJson,
    @Nullable TaskSource source,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant closedAt
) {}
