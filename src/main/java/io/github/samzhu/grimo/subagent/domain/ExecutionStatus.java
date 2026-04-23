package io.github.samzhu.grimo.subagent.domain;

/**
 * Lifecycle of a single subagent execution: PENDING -> RUNNING -> SUCCEEDED | FAILED.
 * Independent from {@link io.github.samzhu.grimo.task.domain.TaskStatus}.
 */
public enum ExecutionStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
