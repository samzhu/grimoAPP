package io.github.samzhu.grimo.project;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates Project creation rules before data is persisted.
 *
 * @see ProjectStore
 * @see WorkflowRecipeCatalog
 */
@Service
public class ProjectService {

	private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

	private final ProjectStore projectStore;
	private final WorkflowRecipeCatalog workflowRecipeCatalog;
	private final ShortResourceIdGenerator idGenerator;
	private final Clock clock;

	@Autowired
	public ProjectService(
			ProjectStore projectStore,
			WorkflowRecipeCatalog workflowRecipeCatalog,
			ShortResourceIdGenerator idGenerator
	) {
		this(projectStore, workflowRecipeCatalog, idGenerator, Clock.systemUTC());
	}

	ProjectService(
			ProjectStore projectStore,
			WorkflowRecipeCatalog workflowRecipeCatalog,
			ShortResourceIdGenerator idGenerator,
			Clock clock
	) {
		this.projectStore = projectStore;
		this.workflowRecipeCatalog = workflowRecipeCatalog;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	public List<ProjectResponse> listProjects() {
		return projectStore.findAll().stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public ProjectResponse createProject(CreateProjectRequest request) {
		String name = trimRequired(request.name(), "請填寫專案名稱與專案工作區");
		String workspacePath = trimRequired(request.workspacePath(), "請填寫專案名稱與專案工作區");
		String workflowRecipeId = trimRequired(request.workflowRecipeId(), "未知的專案工作流");
		WorkflowRecipeResponse recipe = workflowRecipeCatalog.findById(workflowRecipeId)
				.orElseThrow(() -> new IllegalArgumentException("未知的專案工作流"));

		if (projectStore.existsByWorkspacePath(workspacePath)) {
			logger.atWarn()
					.addKeyValue("workspacePath", workspacePath)
					.log("project.create.duplicate");
			throw new DuplicateProjectException(workspacePath);
		}

		Instant now = Instant.now(clock);
		ProjectRecord project = new ProjectRecord(
				idGenerator.newId(),
				name,
				request.description() == null ? "" : request.description().trim(),
				workspacePath,
				recipe.id(),
				"ACTIVE",
				now,
				now
		);
		List<ProjectWorkflowRoleRecord> workflowRoles = recipe.roles().stream()
				.map(role -> ProjectWorkflowRoleRecord.fromRecipeRole(project.id(), role, now))
				.toList();
		projectStore.insert(project);
		projectStore.insertWorkflowRoles(workflowRoles);
		logger.atInfo()
				.addKeyValue("projectId", project.id())
				.addKeyValue("workflowRecipeId", project.workflowRecipeId())
				.log("project.created");
		return ProjectResponse.from(project, recipe, workflowRoles);
	}

	private ProjectResponse toResponse(ProjectRecord project) {
		WorkflowRecipeResponse recipe = workflowRecipeCatalog.findById(project.workflowRecipeId())
				.orElse(new WorkflowRecipeResponse(
						project.workflowRecipeId(),
						project.workflowRecipeId(),
						"",
						"unknown",
						List.of(),
						List.of(),
						""
				));
		return ProjectResponse.from(project, recipe, projectStore.findWorkflowRoles(project.id()));
	}

	private static String trimRequired(String value, String message) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
