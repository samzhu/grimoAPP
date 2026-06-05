package io.github.samzhu.grimo.workflow;

import java.time.Instant;

/**
 * SQLite row shape for one Quality Loop attempt on a workflow run step.
 *
 * @param id server-generated quality run id
 * @param workflowRunStepId workflow run step owner id
 * @param attempt immutable attempt number for the step
 * @param outputSummary summary of the step output under review
 * @param outputRef optional local artifact reference
 * @param reviewSummary review findings for this attempt
 * @param qualityScore nullable quality score for this attempt
 * @param fixSummary fix summary produced after review
 * @param createdAt server creation timestamp
 * @param updatedAt server update timestamp
 * @see TaskWorkflowRunStepRecord
 */
public record TaskWorkflowQualityRunRecord(
		String id,
		String workflowRunStepId,
		int attempt,
		String outputSummary,
		String outputRef,
		String reviewSummary,
		Double qualityScore,
		String fixSummary,
		Instant createdAt,
		Instant updatedAt
) {
}
