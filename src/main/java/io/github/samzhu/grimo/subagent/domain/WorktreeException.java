package io.github.samzhu.grimo.subagent.domain;

/**
 * Domain exception for worktree lifecycle operations — thrown when
 * git CLI invocation fails, worktree creation/removal errors, or
 * the git binary is not found on the system PATH.
 */
public class WorktreeException extends RuntimeException {

    public WorktreeException(String message) {
        super(message);
    }

    public WorktreeException(String message, Throwable cause) {
        super(message, cause);
    }
}
