package io.github.samzhu.grimo.project.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * A Grimo project — a local working directory bound to a name.
 */
public record Project(
    String id,
    String name,
    String workDir,
    @Nullable String description,
    Instant createdAt,
    Instant updatedAt
) {}
