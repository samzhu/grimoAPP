package io.github.samzhu.grimo.subagent.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Tracks a single subagent execution attempt for a task.
 * One task may have multiple executions (retry after failure).
 *
 * <p>Immutable record — state transitions produce new instances via
 * {@code with*} convenience methods.
 */
public record TaskExecution(
    String id,
    String taskId,
    int taskNumber,
    ExecutionStatus status,
    String prompt,
    @Nullable String branch,
    @Nullable String worktreePath,
    @Nullable String containerId,
    @Nullable String agentResponse,
    @Nullable String diff,
    @Nullable String errorMessage,
    @Nullable Instant startedAt,
    @Nullable Instant finishedAt,
    Instant createdAt
) {

    /** Transition to RUNNING — records container and worktree info. */
    public TaskExecution withRunning(String containerId, String branch, String worktreePath) {
        return new TaskExecution(id, taskId, taskNumber, ExecutionStatus.RUNNING, prompt,
                branch, worktreePath, containerId, null, null, null,
                Instant.now(), null, createdAt);
    }

    /** Transition to SUCCEEDED — records agent response and diff. */
    public TaskExecution withSucceeded(String agentResponse, String diff) {
        return new TaskExecution(id, taskId, taskNumber, ExecutionStatus.SUCCEEDED, prompt,
                branch, worktreePath, containerId, agentResponse, diff, null,
                startedAt, Instant.now(), createdAt);
    }

    /** Transition to FAILED — records error message. */
    public TaskExecution withFailed(String errorMessage) {
        return new TaskExecution(id, taskId, taskNumber, ExecutionStatus.FAILED, prompt,
                branch, worktreePath, containerId, null, null, errorMessage,
                startedAt, Instant.now(), createdAt);
    }
}
