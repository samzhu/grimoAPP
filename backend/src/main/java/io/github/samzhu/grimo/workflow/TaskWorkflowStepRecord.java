package io.github.samzhu.grimo.workflow;

import java.time.Instant;

/**
 * SQLite row shape for copied immutable Task Workflow step metadata.
 *
 * @param id server-generated Task Workflow step id
 * @param taskWorkflowId Task Workflow owner id
 * @param stepKey stable workflow step key from the Project workflow definition
 * @param stepLabel user-facing workflow step label copied at Task creation
 * @param taskState outer Task State where the step usually contributes
 * @param stepOrder copied step order, spaced by tens for future insertion
 * @param createdAt server creation timestamp
 * @param updatedAt server update timestamp
 * @see TaskWorkflowRecord
 */
public record TaskWorkflowStepRecord(
		String id,
		String taskWorkflowId,
		String stepKey,
		String stepLabel,
		String taskState,
		int stepOrder,
		Instant createdAt,
		Instant updatedAt
) {
}
