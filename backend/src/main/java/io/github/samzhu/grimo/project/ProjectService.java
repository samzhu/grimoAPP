package io.github.samzhu.grimo.project;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
	private final Clock clock;

	@Autowired
	public ProjectService(ProjectStore projectStore, WorkflowRecipeCatalog workflowRecipeCatalog) {
		this(projectStore, workflowRecipeCatalog, Clock.systemUTC());
	}

	ProjectService(ProjectStore projectStore, WorkflowRecipeCatalog workflowRecipeCatalog, Clock clock) {
		this.projectStore = projectStore;
		this.workflowRecipeCatalog = workflowRecipeCatalog;
		this.clock = clock;
	}

	public List<ProjectResponse> listProjects() {
		return projectStore.findAll().stream()
				.map(this::toResponse)
				.toList();
	}

	public ProjectResponse createProject(CreateProjectRequest request) {
		String name = trimRequired(request.name(), "請填寫專案名稱與對應資料夾");
		String folderPath = trimRequired(request.folderPath(), "請填寫專案名稱與對應資料夾");
		String workflowRecipeId = trimRequired(request.workflowRecipeId(), "未知的專案工作流");
		WorkflowRecipeResponse recipe = workflowRecipeCatalog.findById(workflowRecipeId)
				.orElseThrow(() -> new IllegalArgumentException("未知的專案工作流"));

		if (projectStore.existsByFolderPath(folderPath)) {
			logger.warn("project.create.duplicate folderPath={}", folderPath);
			throw new DuplicateProjectException(folderPath);
		}

		Instant now = Instant.now(clock);
		ProjectRecord project = new ProjectRecord(
				"prj_" + UUID.randomUUID(),
				name,
				request.description() == null ? "" : request.description().trim(),
				folderPath,
				recipe.id(),
				"ACTIVE",
				now,
				now
		);
		projectStore.insert(project);
		logger.info("project.created projectId={} workflowRecipeId={}", project.id(), project.workflowRecipeId());
		return ProjectResponse.from(project, recipe);
	}

	private ProjectResponse toResponse(ProjectRecord project) {
		WorkflowRecipeResponse recipe = workflowRecipeCatalog.findById(project.workflowRecipeId())
				.orElse(new WorkflowRecipeResponse(project.workflowRecipeId(), project.workflowRecipeId(), "", "unknown"));
		return ProjectResponse.from(project, recipe);
	}

	private static String trimRequired(String value, String message) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
