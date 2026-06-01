package io.github.samzhu.grimo.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * SQLite-backed persistence for Grimo Projects.
 *
 * @see ProjectService
 */
@Repository
public class ProjectStore {

	private final JdbcClient jdbcClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ProjectStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<ProjectRecord> findAll() {
		return jdbcClient.sql("""
						SELECT id, name, description, workspace_path, workflow_recipe_id, status, created_at, updated_at
						FROM projects
						ORDER BY created_at DESC
						""")
				.query(ProjectStore::mapProject)
				.list();
	}

	public boolean existsByWorkspacePath(String workspacePath) {
		Integer count = jdbcClient.sql("SELECT COUNT(*) FROM projects WHERE workspace_path = :workspacePath")
				.param("workspacePath", workspacePath)
				.query(Integer.class)
				.single();
		return count != null && count > 0;
	}

	public void insert(ProjectRecord project) {
		try {
			jdbcClient.sql("""
							INSERT INTO projects (
								id, name, description, workspace_path, workflow_recipe_id, status, created_at, updated_at
							)
							VALUES (:id, :name, :description, :workspacePath, :workflowRecipeId, :status, :createdAt, :updatedAt)
							""")
					.param("id", project.id())
					.param("name", project.name())
					.param("description", project.description())
					.param("workspacePath", project.workspacePath())
					.param("workflowRecipeId", project.workflowRecipeId())
					.param("status", project.status())
					.param("createdAt", project.createdAt().toString())
					.param("updatedAt", project.updatedAt().toString())
					.update();
		}
		catch (DuplicateKeyException exception) {
			throw new DuplicateProjectException(project.workspacePath());
		}
	}

	public void insertWorkflowRoles(List<ProjectWorkflowRoleRecord> roles) {
		for (ProjectWorkflowRoleRecord role : roles) {
			jdbcClient.sql("""
							INSERT INTO project_workflow_roles (
								project_id, role_id, name, description, primary_steps, enabled, created_at, updated_at
							)
							VALUES (
								:projectId, :roleId, :name, :description, :primarySteps, :enabled, :createdAt, :updatedAt
							)
							""")
					.param("projectId", role.projectId())
					.param("roleId", role.id())
					.param("name", role.name())
					.param("description", role.description())
					.param("primarySteps", writePrimarySteps(role.primarySteps()))
					.param("enabled", role.enabled() ? 1 : 0)
					.param("createdAt", role.createdAt().toString())
					.param("updatedAt", role.updatedAt().toString())
					.update();
		}
	}

	public List<ProjectWorkflowRoleRecord> findWorkflowRoles(String projectId) {
		return jdbcClient.sql("""
						SELECT project_id, role_id, name, description, primary_steps, enabled, created_at, updated_at
						FROM project_workflow_roles
						WHERE project_id = :projectId
						ORDER BY created_at ASC, role_id ASC
						""")
				.param("projectId", projectId)
				.query(this::mapWorkflowRole)
				.list();
	}

	private static ProjectRecord mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ProjectRecord(
				resultSet.getString("id"),
				resultSet.getString("name"),
				resultSet.getString("description"),
				resultSet.getString("workspace_path"),
				resultSet.getString("workflow_recipe_id"),
				resultSet.getString("status"),
				Instant.parse(resultSet.getString("created_at")),
				Instant.parse(resultSet.getString("updated_at"))
		);
	}

	private ProjectWorkflowRoleRecord mapWorkflowRole(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ProjectWorkflowRoleRecord(
				resultSet.getString("project_id"),
				resultSet.getString("role_id"),
				resultSet.getString("name"),
				resultSet.getString("description"),
				readPrimarySteps(resultSet.getString("primary_steps")),
				resultSet.getInt("enabled") == 1,
				Instant.parse(resultSet.getString("created_at")),
				Instant.parse(resultSet.getString("updated_at"))
		);
	}

	private String writePrimarySteps(List<String> primarySteps) {
		try {
			return objectMapper.writeValueAsString(primarySteps);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Unable to serialize project workflow role primary steps.", exception);
		}
	}

	private List<String> readPrimarySteps(String primarySteps) {
		if (primarySteps == null || primarySteps.equals("[]")) {
			return List.of();
		}
		try {
			return objectMapper.readValue(primarySteps, new TypeReference<List<String>>() {
			});
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Unable to deserialize project workflow role primary steps.", exception);
		}
	}
}
