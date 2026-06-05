package io.github.samzhu.grimo.workflow;

import java.time.Instant;

/**
 * SQLite row shape for one ordered execution step in a workflow run.
 *
 * @param id server-generated workflow run step id
 * @param workflowRunId workflow run owner id
 * @param stepKey stable workflow step key copied from the Task Workflow
 * @param stepLabel user-facing step label copied into execution evidence
 * @param taskState outer Task state associated with this step
 * @param stepOrder copied execution order
 * @param state execution step state, such as ACTIVE or PENDING
 * @param startedAt server timestamp when the step started, or null while pending
 * @param completedAt server timestamp when the step ended, or null while not completed
 * @param createdAt server creation timestamp
 * @param updatedAt server update timestamp
 * @see TaskWorkflowRunRecord
 */
public record TaskWorkflowRunStepRecord(
		String id,
		String workflowRunId,
		String stepKey,
		String stepLabel,
		String taskState,
		int stepOrder,
		String state,
		Instant startedAt,
		Instant completedAt,
		Instant createdAt,
		Instant updatedAt
) {
}
