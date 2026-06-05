package io.github.samzhu.grimo.task;

import java.time.Instant;
import java.util.List;

/**
 * SQLite row shape for a Project-owned Task root record.
 *
 * @param id server-generated Task id
 * @param projectId Project owner id from the nested API path
 * @param title normalized Task title shown on the board
 * @param body normalized first context from the Create Task dialog
 * @param source system provenance, always manual for S004
 * @param state Task State Machine value, always BACKLOG on creation
 * @param workflowRecipeId Project workflow recipe copied onto the Task root
 * @param labels normalized labels stored as JSON text in SQLite
 * @param createdAt server creation timestamp
 * @param updatedAt server update timestamp
 * @see TaskStore
 */
public record TaskRecord(
		String id,
		String projectId,
		String title,
		String body,
		String source,
		String state,
		String workflowRecipeId,
		List<String> labels,
		Instant createdAt,
		Instant updatedAt
) {
}
