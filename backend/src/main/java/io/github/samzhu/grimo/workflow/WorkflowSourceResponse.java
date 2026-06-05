package io.github.samzhu.grimo.workflow;

/**
 * API response fragment describing where a Task Workflow copy came from.
 *
 * @param type workflow source kind, such as RECIPE
 * @param ref recipe id or future workflow file reference
 * @param hash optional diagnostics hash; copied step rows remain the version source of truth
 * @see TaskWorkflowDetailResponse
 */
public record WorkflowSourceResponse(String type, String ref, String hash) {
}
