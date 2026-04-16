package io.github.samzhu.grimo.core.domain;

/**
 * Typed identifier for a delegated sub-agent task.
 *
 * <p>Assigned when a user ({@code MAIN} agent) dispatches work to a
 * sub-agent — see S008/S010 where {@code TaskId} flows into worktree
 * management and sandbox orchestration.
 *
 * <p>21-char NanoID, validated by the compact constructor. Distinct
 * record type from {@link SessionId} / {@link TurnId} /
 * {@link CorrelationId} for compile-time safety (S001 D1, D9).
 */
public record TaskId(String value) {

    public TaskId {
        if (value == null || value.length() != 21) {
            throw new IllegalArgumentException(
                "TaskId must be a 21-character NanoID, got: " + value);
        }
    }

    public static TaskId random() {
        return new TaskId(NanoIds.generate());
    }
}
