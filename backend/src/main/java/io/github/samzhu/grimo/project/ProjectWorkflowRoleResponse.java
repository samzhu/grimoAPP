package io.github.samzhu.grimo.project;

import java.util.List;

/**
 * HTTP response body for a Project's persisted workflow role settings.
 *
 * @param id workflow role identifier
 * @param name user-facing role name
 * @param description concise responsibility summary
 * @param primarySteps workflow steps this role primarily contributes to
 * @param enabled whether this default role is enabled for the Project
 * @see ProjectResponse
 */
public record ProjectWorkflowRoleResponse(
		String id,
		String name,
		String description,
		List<String> primarySteps,
		boolean enabled
) {
	static ProjectWorkflowRoleResponse from(ProjectWorkflowRoleRecord role) {
		return new ProjectWorkflowRoleResponse(
				role.id(),
				role.name(),
				role.description(),
				role.primarySteps(),
				role.enabled()
		);
	}
}
