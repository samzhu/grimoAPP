package io.github.samzhu.grimo.project;

import java.util.List;

/**
 * HTTP response body for a Project-level workflow recipe option.
 *
 * @param id stable workflow recipe identifier stored on Project
 * @param name user-facing workflow recipe name
 * @param description short workflow recipe summary
 * @param category display-only recipe classification
 * @param steps workflow steps shown on the Project Creation Page
 * @param roles read-only role preview for this workflow recipe
 * @param qualityLoopSummary short Quality Loop summary shown in the preview
 * @see WorkflowRecipeCatalog
 */
public record WorkflowRecipeResponse(
		String id,
		String name,
		String description,
		String category,
		List<WorkflowStepResponse> steps,
		List<WorkflowRoleResponse> roles,
		String qualityLoopSummary
) {
}
