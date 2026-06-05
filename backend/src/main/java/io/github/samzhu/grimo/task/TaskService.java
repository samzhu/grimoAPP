package io.github.samzhu.grimo.task;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import io.github.samzhu.grimo.project.ProjectRecord;
import io.github.samzhu.grimo.project.ProjectStore;
import io.github.samzhu.grimo.project.ShortResourceIdGenerator;
import io.github.samzhu.grimo.project.WorkflowRecipeCatalog;
import io.github.samzhu.grimo.project.WorkflowRecipeResponse;
import io.github.samzhu.grimo.workflow.TaskWorkflowService;
import io.github.samzhu.grimo.workflow.WorkflowSummaryProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates Project-owned manual Task creation and read models.
 *
 * @see TaskStore
 * @see TaskWorkflowService
 */
@Service
public class TaskService {

	private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

	private final ProjectStore projectStore;
	private final TaskStore taskStore;
	private final TaskWorkflowService taskWorkflowService;
	private final WorkflowSummaryProjectionService workflowSummaryProjectionService;
	private final WorkflowRecipeCatalog workflowRecipeCatalog;
	private final ShortResourceIdGenerator idGenerator;
	private final Clock clock;

	@Autowired
	public TaskService(
			ProjectStore projectStore,
			TaskStore taskStore,
			TaskWorkflowService taskWorkflowService,
			WorkflowSummaryProjectionService workflowSummaryProjectionService,
			WorkflowRecipeCatalog workflowRecipeCatalog,
			ShortResourceIdGenerator idGenerator
	) {
		this(
				projectStore,
				taskStore,
				taskWorkflowService,
				workflowSummaryProjectionService,
				workflowRecipeCatalog,
				idGenerator,
				Clock.systemUTC()
		);
	}

	TaskService(
			ProjectStore projectStore,
			TaskStore taskStore,
			TaskWorkflowService taskWorkflowService,
			WorkflowSummaryProjectionService workflowSummaryProjectionService,
			WorkflowRecipeCatalog workflowRecipeCatalog,
			ShortResourceIdGenerator idGenerator,
			Clock clock
	) {
		this.projectStore = projectStore;
		this.taskStore = taskStore;
		this.taskWorkflowService = taskWorkflowService;
		this.workflowSummaryProjectionService = workflowSummaryProjectionService;
		this.workflowRecipeCatalog = workflowRecipeCatalog;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	public List<TaskResponse> listTasks(String projectId) {
		ensureProjectExists(projectId);
		var summaries = workflowSummaryProjectionService.findByProjectId(projectId);
		return taskStore.findByProjectId(projectId).stream()
				.map(task -> TaskResponse.from(task, summaries.getOrDefault(task.id(), WorkflowSummaryResponse.empty())))
				.toList();
	}

	@Transactional
	public TaskResponse createTask(String projectId, CreateTaskRequest request) {
		ProjectRecord project = ensureProjectExists(projectId);
		WorkflowRecipeResponse recipe = workflowRecipeCatalog.findById(project.workflowRecipeId())
				.orElseThrow(() -> new IllegalArgumentException("未知的專案工作流"));
		Instant now = Instant.now(clock);
		TaskRecord task = new TaskRecord(
				idGenerator.newId(),
				project.id(),
				trimTitle(request.title()),
				trimOptional(request.body(), 8000, "Task 內容太長"),
				"manual",
				"BACKLOG",
				project.workflowRecipeId(),
				normalizeLabels(request.labels()),
				now,
				now
		);

		taskStore.insert(task);
		taskWorkflowService.copyFromProjectWorkflow(task.id(), recipe, now);
		logger.atInfo()
				.addKeyValue("projectId", project.id())
				.addKeyValue("taskId", task.id())
				.addKeyValue("workflowRecipeId", task.workflowRecipeId())
				.log("task.created");
		return TaskResponse.from(task);
	}

	private ProjectRecord ensureProjectExists(String projectId) {
		return projectStore.findById(projectId).orElseThrow(MissingProjectException::new);
	}

	private static String trimTitle(String title) {
		if (title == null || title.trim().isEmpty()) {
			throw new IllegalArgumentException("請填寫 Task 標題");
		}
		String normalized = title.trim();
		if (normalized.length() > 140) {
			throw new IllegalArgumentException("Task 標題太長");
		}
		return normalized;
	}

	private static String trimOptional(String value, int maxLength, String errorMessage) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(errorMessage);
		}
		return normalized;
	}

	private static List<String> normalizeLabels(List<String> labels) {
		if (labels == null || labels.isEmpty()) {
			return List.of();
		}
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String label : labels) {
			if (label == null || label.trim().isEmpty()) {
				continue;
			}
			String value = label.trim();
			if (value.length() > 40) {
				throw new IllegalArgumentException("Task label 太長");
			}
			normalized.add(value);
		}
		if (normalized.size() > 10) {
			throw new IllegalArgumentException("Task labels 最多 10 個");
		}
		return new ArrayList<>(normalized);
	}
}
