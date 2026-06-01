package io.github.samzhu.grimo.project;

/**
 * Workflow step metadata shown in the Project Creation Page preview.
 *
 * @param id stable step identifier
 * @param name user-facing step name
 * @param taskState Task State Machine state where this step usually contributes
 * @see WorkflowRecipeResponse
 */
public record WorkflowStepResponse(String id, String name, String taskState) {
}
