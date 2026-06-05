package io.github.samzhu.grimo.workflow;

import java.util.Map;

import io.github.samzhu.grimo.task.WorkflowSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds Task board workflow summaries from normalized workflow evidence rows.
 *
 * @see WorkflowEvidenceStore
 */
@Service
public class WorkflowSummaryProjectionService {

	private static final Logger logger = LoggerFactory.getLogger(WorkflowSummaryProjectionService.class);

	private final WorkflowEvidenceStore workflowEvidenceStore;

	public WorkflowSummaryProjectionService(WorkflowEvidenceStore workflowEvidenceStore) {
		this.workflowEvidenceStore = workflowEvidenceStore;
	}

	public Map<String, WorkflowSummaryResponse> findByProjectId(String projectId) {
		Map<String, WorkflowSummaryResponse> summaries = workflowEvidenceStore.findWorkflowSummariesByProjectId(projectId);
		logger.atDebug()
				.addKeyValue("projectId", projectId)
				.addKeyValue("summaryCount", summaries.size())
				.log("task.workflow_summary_projected");
		return summaries;
	}
}
