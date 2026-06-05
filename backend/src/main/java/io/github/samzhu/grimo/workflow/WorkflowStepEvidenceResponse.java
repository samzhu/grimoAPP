package io.github.samzhu.grimo.workflow;

import java.time.Instant;

/**
 * API response fragment for one ordered workflow execution step.
 *
 * @param stepKey stable workflow step key copied from the Task Workflow
 * @param stepLabel user-facing workflow step label copied into execution evidence
 * @param taskState outer Task State usually associated with this step
 * @param stepOrder copied execution order
 * @param state execution step state, such as ACTIVE or PENDING
 * @param startedAt server timestamp when the step started, or null while pending
 * @param completedAt server timestamp when the step completed, or null while active/pending
 * @param qualitySummary latest Quality Loop summary for this step, or null when no attempt exists
 * @see TaskWorkflowDetailResponse
 */
public record WorkflowStepEvidenceResponse(
		String stepKey,
		String stepLabel,
		String taskState,
		int stepOrder,
		String state,
		Instant startedAt,
		Instant completedAt,
		WorkflowQualitySummaryResponse qualitySummary
) {
}
