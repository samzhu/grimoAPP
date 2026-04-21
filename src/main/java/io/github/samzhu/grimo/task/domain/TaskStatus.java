package io.github.samzhu.grimo.task.domain;

/**
 * Task lifecycle: OPEN -> IN_PROGRESS -> IN_REVIEW -> DONE | CANCELLED.
 * Any state can transition to CANCELLED.
 */
public enum TaskStatus {
    OPEN, IN_PROGRESS, IN_REVIEW, DONE, CANCELLED;

    public boolean isTerminal() {
        return this == DONE || this == CANCELLED;
    }
}
