package io.github.samzhu.grimo.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
		String name = trimRequired(request.name(), "請填寫專案名稱與專案工作流");
		String workflowRecipeId = trimRequired(request.workflowRecipeId(), "未知的專案工作流");
		WorkflowRecipeResponse recipe = workflowRecipeCatalog.findById(workflowRecipeId)
				.orElseThrow(() -> new IllegalArgumentException("未知的專案工作流"));
		String projectId = idGenerator.newId();
		String projectPath = resolveProjectPath(projectId, request.projectPath());

		if (projectStore.existsByProjectPath(projectPath)) {
			logger.atWarn()
					.addKeyValue("projectPath", projectPath)
					.log("project.create.duplicate");
			throw new DuplicateProjectException(projectPath);
		}

		Instant now = Instant.now(clock);
		ProjectRecord project = new ProjectRecord(
				projectId,
				name,
				request.description() == null ? "" : request.description().trim(),
				projectPath,
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
				.addKeyValue("projectPath", project.projectPath())
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

	private static String resolveProjectPath(String projectId, String requestedProjectPath) {
		if (requestedProjectPath == null || requestedProjectPath.trim().isEmpty()) {
			return createManagedProjectPath(projectId);
		}
		Path path = Path.of(requestedProjectPath.trim()).toAbsolutePath().normalize();
		if (!Files.exists(path) || !Files.isDirectory(path) || !Files.isReadable(path)) {
			throw new IllegalArgumentException("請輸入有效的本機資料夾路徑");
		}
		return path.toString();
	}

	private static String createManagedProjectPath(String projectId) {
		Path path = Path.of(System.getProperty("user.home"), ".grimo", "projects", projectId)
				.toAbsolutePath()
				.normalize();
		try {
			Files.createDirectories(path);
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("無法建立 Grimo 預設專案路徑");
		}
		return path.toString();
	}

	private static String trimRequired(String value, String message) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
