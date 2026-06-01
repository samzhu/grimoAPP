package io.github.samzhu.grimo.project;

import java.time.Instant;
import java.util.List;

/**
 * Persisted Project-level snapshot of a workflow role.
 *
 * @param projectId owning Project identifier
 * @param id workflow role identifier
 * @param name user-facing role name
 * @param description concise responsibility summary
 * @param primarySteps workflow steps this role primarily contributes to
 * @param enabled whether this default role is enabled for the Project
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @see ProjectStore
 */
public record ProjectWorkflowRoleRecord(
		String projectId,
		String id,
		String name,
		String description,
		List<String> primarySteps,
		boolean enabled,
		Instant createdAt,
		Instant updatedAt
) {
	static ProjectWorkflowRoleRecord fromRecipeRole(String projectId, WorkflowRoleResponse role, Instant now) {
		return new ProjectWorkflowRoleRecord(
				projectId,
				role.id(),
				role.name(),
				role.description(),
				role.primarySteps(),
				true,
				now,
				now
		);
	}
}
