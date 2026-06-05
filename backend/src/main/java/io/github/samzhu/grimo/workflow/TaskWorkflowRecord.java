package io.github.samzhu.grimo.workflow;

import java.time.Instant;

/**
 * SQLite row shape for a Task-owned immutable workflow copy.
 *
 * @param id server-generated Task Workflow id
 * @param taskId Task owner id
 * @param sourceType workflow definition source kind, such as RECIPE
 * @param sourceRef recipe id or future workflow file reference
 * @param sourceHash optional diagnostics hash; copied step rows remain the version source of truth
 * @param createdAt server creation timestamp
 * @param updatedAt server update timestamp
 * @see TaskWorkflowService
 */
public record TaskWorkflowRecord(
		String id,
		String taskId,
		String sourceType,
		String sourceRef,
		String sourceHash,
		Instant createdAt,
		Instant updatedAt
) {
}
