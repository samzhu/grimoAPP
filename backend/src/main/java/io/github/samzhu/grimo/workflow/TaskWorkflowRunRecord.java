package io.github.samzhu.grimo.workflow;

import java.time.Instant;

/**
 * SQLite row shape for a Task workflow execution run.
 *
 * @param id server-generated workflow run id
 * @param taskId Task owner id
 * @param taskWorkflowId immutable Task Workflow copy executed by this run
 * @param state workflow run state, such as ACTIVE
 * @param startedAt server timestamp when the run started
 * @param completedAt server timestamp when the run ended, or null while active
 * @param createdAt server creation timestamp
 * @param updatedAt server update timestamp
 * @see TaskWorkflowTransitionService
 */
public record TaskWorkflowRunRecord(
		String id,
		String taskId,
		String taskWorkflowId,
		String state,
		Instant startedAt,
		Instant completedAt,
		Instant createdAt,
		Instant updatedAt
) {
}
