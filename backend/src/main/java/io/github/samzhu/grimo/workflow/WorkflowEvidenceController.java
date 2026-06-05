package io.github.samzhu.grimo.workflow;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API for reading Task workflow evidence under a Project-owned Task.
 *
 * @see WorkflowEvidenceService
 */
@RestController
@RequestMapping("/api/projects/{projectId}/tasks/{taskId}/workflow")
public class WorkflowEvidenceController {

	private final WorkflowEvidenceService workflowEvidenceService;

	public WorkflowEvidenceController(WorkflowEvidenceService workflowEvidenceService) {
		this.workflowEvidenceService = workflowEvidenceService;
	}

	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	TaskWorkflowDetailResponse getWorkflowDetail(
			@PathVariable String projectId,
			@PathVariable String taskId
	) {
		return workflowEvidenceService.findWorkflowDetail(projectId, taskId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到 Task workflow"));
	}
}
