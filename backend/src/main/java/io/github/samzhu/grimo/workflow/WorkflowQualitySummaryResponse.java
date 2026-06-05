package io.github.samzhu.grimo.workflow;

import java.time.Instant;

/**
 * API response fragment for the latest Quality Loop attempt on one step.
 *
 * @param latestAttempt latest immutable attempt number for the step
 * @param latestScore latest quality score, nullable when rating has not happened
 * @param passed true when latestScore is greater than the PRD quality gate of 9
 * @param latestReviewSummary review findings from the latest attempt
 * @param latestFixSummary fix summary from the latest attempt
 * @param updatedAt server timestamp when the latest attempt was updated
 * @see WorkflowStepEvidenceResponse
 */
public record WorkflowQualitySummaryResponse(
		int latestAttempt,
		Double latestScore,
		boolean passed,
		String latestReviewSummary,
		String latestFixSummary,
		Instant updatedAt
) {
}
