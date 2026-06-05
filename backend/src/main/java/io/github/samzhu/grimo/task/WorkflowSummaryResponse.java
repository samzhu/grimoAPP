package io.github.samzhu.grimo.task;

/**
 * Board projection for current workflow progress.
 *
 * @param currentStep active workflow step label; null before the Task enters workflow
 * @param qualityScore latest Quality Loop score; null before workflow evidence exists
 * @see TaskResponse
 */
public record WorkflowSummaryResponse(String currentStep, Double qualityScore) {

	public static WorkflowSummaryResponse empty() {
		return new WorkflowSummaryResponse(null, null);
	}
}
