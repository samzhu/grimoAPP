package io.github.samzhu.grimo.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HTTP request body for creating a Grimo Project.
 *
 * @param name user-facing Project name shown in the Projects view
 * @param description optional user-facing context for the Project
 * @param folderPath local repo or codebase path owned by the Project
 * @param workflowRecipeId Project-level workflow recipe identifier
 * @see ProjectController
 */
public record CreateProjectRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 500) String description,
		@NotBlank @Size(max = 1000) String folderPath,
		@NotBlank @Size(max = 80) String workflowRecipeId
) {
}
