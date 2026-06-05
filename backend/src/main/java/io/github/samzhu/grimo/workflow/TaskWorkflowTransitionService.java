package io.github.samzhu.grimo.workflow;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.samzhu.grimo.project.ShortResourceIdGenerator;
import io.github.samzhu.grimo.task.TaskRecord;
import io.github.samzhu.grimo.task.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts workflow execution evidence when a BACKLOG Task first enters Chat.
 *
 * @see WorkflowEvidenceStore
 * @see TaskStore
 */
@Service
public class TaskWorkflowTransitionService {

	private static final Logger logger = LoggerFactory.getLogger(TaskWorkflowTransitionService.class);

	private final TaskStore taskStore;
	private final WorkflowEvidenceStore workflowEvidenceStore;
	private final ShortResourceIdGenerator idGenerator;
	private final Clock clock;

	@Autowired
	public TaskWorkflowTransitionService(
			TaskStore taskStore,
			WorkflowEvidenceStore workflowEvidenceStore,
			ShortResourceIdGenerator idGenerator
	) {
		this(taskStore, workflowEvidenceStore, idGenerator, Clock.systemUTC());
	}

	TaskWorkflowTransitionService(
			TaskStore taskStore,
			WorkflowEvidenceStore workflowEvidenceStore,
			ShortResourceIdGenerator idGenerator,
			Clock clock
	) {
		this.taskStore = taskStore;
		this.workflowEvidenceStore = workflowEvidenceStore;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	@Transactional
	public TaskWorkflowRunRecord openChatForBacklogTask(String taskId) {
		return workflowEvidenceStore.findActiveRunByTaskId(taskId)
				.orElseGet(() -> startRun(taskId));
	}

	private TaskWorkflowRunRecord startRun(String taskId) {
		TaskRecord task = taskStore.findById(taskId)
				.orElseThrow(() -> new IllegalArgumentException("找不到 Task"));
		if (!"BACKLOG".equals(task.state())) {
			throw new IllegalStateException("只有 BACKLOG Task 能啟動第一次 Chat workflow");
		}

		Instant now = Instant.now(clock);
		int moved = taskStore.moveState(taskId, "BACKLOG", "DEFINING", now);
		if (moved != 1) {
			return workflowEvidenceStore.findActiveRunByTaskId(taskId)
					.orElseThrow(() -> new IllegalStateException("Task workflow transition was already claimed"));
		}

		TaskWorkflowRecord workflow = workflowEvidenceStore.findTaskWorkflowByTaskId(taskId)
				.orElseThrow(() -> new IllegalStateException("Task 尚未複製 workflow"));
		List<TaskWorkflowStepRecord> plannedSteps = workflowEvidenceStore.findTaskWorkflowSteps(workflow.id());
		if (plannedSteps.isEmpty()) {
			throw new IllegalStateException("Task Workflow 沒有 steps，不能啟動 workflow run");
		}

		TaskWorkflowRunRecord run = new TaskWorkflowRunRecord(
				idGenerator.newId(),
				taskId,
				workflow.id(),
				"ACTIVE",
				now,
				null,
				now,
				now
		);
		workflowEvidenceStore.insertWorkflowRun(run);
		workflowEvidenceStore.insertWorkflowRunSteps(toRunSteps(run.id(), plannedSteps, now));
		logger.atInfo()
				.addKeyValue("taskId", taskId)
				.addKeyValue("workflowRunId", run.id())
				.addKeyValue("stepCount", plannedSteps.size())
				.log("task.workflow_started");
		return run;
	}

	private List<TaskWorkflowRunStepRecord> toRunSteps(
			String workflowRunId,
			List<TaskWorkflowStepRecord> plannedSteps,
			Instant now
	) {
		List<TaskWorkflowRunStepRecord> runSteps = new ArrayList<>();
		for (int index = 0; index < plannedSteps.size(); index++) {
			TaskWorkflowStepRecord plannedStep = plannedSteps.get(index);
			boolean openingStep = index == 0;
			runSteps.add(new TaskWorkflowRunStepRecord(
					idGenerator.newId(),
					workflowRunId,
					plannedStep.stepKey(),
					plannedStep.stepLabel(),
					plannedStep.taskState(),
					plannedStep.stepOrder(),
					openingStep ? "ACTIVE" : "PENDING",
					openingStep ? now : null,
					null,
					now,
					now
			));
		}
		return runSteps;
	}
}
