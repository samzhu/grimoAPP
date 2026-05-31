package io.github.samzhu.grimo.project;

/**
 * HTTP response body for a Project-level workflow recipe option.
 *
 * @param id stable workflow recipe identifier stored on Project
 * @param name user-facing workflow recipe name
 * @param description short workflow recipe summary
 * @param category display-only recipe classification
 * @see WorkflowRecipeCatalog
 */
public record WorkflowRecipeResponse(
		String id,
		String name,
		String description,
		String category
) {
}
