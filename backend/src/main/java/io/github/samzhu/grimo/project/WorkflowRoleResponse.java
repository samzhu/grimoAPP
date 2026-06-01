package io.github.samzhu.grimo.project;

import java.util.List;

/**
 * Thin Agent Profile metadata shown as a read-only workflow role preview.
 *
 * @param id stable role identifier
 * @param name user-facing role name
 * @param description concise responsibility summary
 * @param primarySteps workflow steps this role primarily contributes to
 * @see WorkflowRecipeResponse
 */
public record WorkflowRoleResponse(String id, String name, String description, List<String> primarySteps) {
}
