package io.github.samzhu.grimo.task;

import java.time.Instant;
import java.util.List;

/**
 * API response body for a Task card summary.
 *
 * @param id server-generated Task id
 * @param projectId Project owner id
 * @param title Task title shown on the board
 * @param body first context for the Task detail
 * @param description compatibility alias for the first context
 * @param state Task State Machine value shown on the board
 * @param source system provenance for how the Task entered Grimo
 * @param workflowRecipeId Project workflow recipe inherited by the Task
 * @param workflowSummary board projection from workflow evidence; S004 returns empty values
 * @param createdAt server creation timestamp
 * @param updatedAt server update timestamp
 * @param acceptance future Definition Package projection; S004 returns an empty list
 * @param gaps future definition gap projection; S004 returns an empty list
 * @param evidence future verification evidence projection; S004 returns an empty list
 * @param labels normalized Task labels
 * @param commentCount future Task Conversation Thread projection; S004 returns zero
 * @see TaskController
 */
public record TaskResponse(
		String id,
		String projectId,
		String title,
		String body,
		String description,
		String state,
		String source,
		String workflowRecipeId,
		WorkflowSummaryResponse workflowSummary,
		Instant createdAt,
		Instant updatedAt,
		List<String> acceptance,
		List<String> gaps,
		List<String> evidence,
		List<String> labels,
		int commentCount
) {

	public static TaskResponse from(TaskRecord task) {
		return from(task, WorkflowSummaryResponse.empty());
	}

	public static TaskResponse from(TaskRecord task, WorkflowSummaryResponse workflowSummary) {
		return new TaskResponse(
				task.id(),
				task.projectId(),
				task.title(),
				task.body(),
				task.body(),
				task.state(),
				task.source(),
				task.workflowRecipeId(),
				workflowSummary,
				task.createdAt(),
				task.updatedAt(),
				List.of(),
				List.of(),
				List.of(),
				task.labels(),
				0
		);
	}
}
