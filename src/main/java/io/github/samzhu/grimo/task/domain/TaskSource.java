package io.github.samzhu.grimo.task.domain;

import org.jspecify.annotations.Nullable;

/**
 * Origin of a task: CHAT (created during conversation), MANUAL (REST API),
 * or WEBHOOK (external trigger).
 */
public record TaskSource(
    String type,
    @Nullable String ref
) {}
