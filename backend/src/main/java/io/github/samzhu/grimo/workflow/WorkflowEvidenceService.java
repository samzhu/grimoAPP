package io.github.samzhu.grimo.workflow;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read-only workflow evidence service for Task detail screens.
 *
 * @see WorkflowEvidenceController
 */
@Service
public class WorkflowEvidenceService {

	private static final Logger logger = LoggerFactory.getLogger(WorkflowEvidenceService.class);

	private final WorkflowEvidenceStore workflowEvidenceStore;

	public WorkflowEvidenceService(WorkflowEvidenceStore workflowEvidenceStore) {
		this.workflowEvidenceStore = workflowEvidenceStore;
	}

	public Optional<TaskWorkflowDetailResponse> findWorkflowDetail(String projectId, String taskId) {
		Optional<TaskWorkflowDetailResponse> detail = workflowEvidenceStore.findWorkflowDetail(projectId, taskId);
		logger.atDebug()
				.addKeyValue("projectId", projectId)
				.addKeyValue("taskId", taskId)
				.addKeyValue("found", detail.isPresent())
				.log("task.workflow_detail_read");
		return detail;
	}
}
