package io.github.samzhu.grimo.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.samzhu.grimo.project.ShortResourceIdGenerator;
import io.github.samzhu.grimo.project.WorkflowRecipeResponse;
import io.github.samzhu.grimo.project.WorkflowStepResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Copies Project workflow definitions into Task-owned immutable workflow rows.
 *
 * @see WorkflowEvidenceStore
 */
@Service
public class TaskWorkflowService {

	private static final Logger logger = LoggerFactory.getLogger(TaskWorkflowService.class);

	private final WorkflowEvidenceStore workflowEvidenceStore;
	private final ShortResourceIdGenerator idGenerator;

	public TaskWorkflowService(WorkflowEvidenceStore workflowEvidenceStore, ShortResourceIdGenerator idGenerator) {
		this.workflowEvidenceStore = workflowEvidenceStore;
		this.idGenerator = idGenerator;
	}

	public void copyFromProjectWorkflow(String taskId, WorkflowRecipeResponse recipe, Instant now) {
		TaskWorkflowRecord workflow = new TaskWorkflowRecord(
				idGenerator.newId(),
				taskId,
				"RECIPE",
				recipe.id(),
				null,
				now,
				now
		);
		workflowEvidenceStore.insertTaskWorkflow(workflow);
		workflowEvidenceStore.insertTaskWorkflowSteps(toWorkflowSteps(workflow.id(), recipe.steps(), now));
		logger.atInfo()
				.addKeyValue("taskId", taskId)
				.addKeyValue("taskWorkflowId", workflow.id())
				.addKeyValue("stepCount", recipe.steps().size())
				.log("task.workflow_copied");
	}

	private List<TaskWorkflowStepRecord> toWorkflowSteps(
			String taskWorkflowId,
			List<WorkflowStepResponse> steps,
			Instant now
	) {
		List<TaskWorkflowStepRecord> records = new ArrayList<>();
		for (int index = 0; index < steps.size(); index++) {
			WorkflowStepResponse step = steps.get(index);
			records.add(new TaskWorkflowStepRecord(
					idGenerator.newId(),
					taskWorkflowId,
					step.id(),
					step.name(),
					step.taskState(),
					(index + 1) * 10,
					now,
					now
			));
		}
		return records;
	}
}
