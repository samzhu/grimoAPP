package io.github.samzhu.grimo.workflow;

import java.util.List;

/**
 * API response body for Task detail workflow evidence.
 *
 * @param taskId Task id from the nested API path
 * @param projectId Project owner id from the nested API path
 * @param workflowRunId active workflow run id, or null before the Task starts workflow
 * @param workflowSource immutable Task Workflow source metadata
 * @param steps ordered execution step evidence for the active workflow run
 * @see WorkflowEvidenceController
 */
public record TaskWorkflowDetailResponse(
		String taskId,
		String projectId,
		String workflowRunId,
		WorkflowSourceResponse workflowSource,
		List<WorkflowStepEvidenceResponse> steps
) {
}
