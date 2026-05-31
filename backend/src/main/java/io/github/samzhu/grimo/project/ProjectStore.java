package io.github.samzhu.grimo.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQLite-backed persistence for Grimo Projects.
 *
 * @see ProjectService
 */
@Repository
public class ProjectStore {

	private final JdbcClient jdbcClient;

	public ProjectStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<ProjectRecord> findAll() {
		return jdbcClient.sql("""
						SELECT id, name, description, folder_path, workflow_recipe_id, status, created_at, updated_at
						FROM projects
						ORDER BY created_at DESC
						""")
				.query(ProjectStore::mapProject)
				.list();
	}

	public boolean existsByFolderPath(String folderPath) {
		Integer count = jdbcClient.sql("SELECT COUNT(*) FROM projects WHERE folder_path = :folderPath")
				.param("folderPath", folderPath)
				.query(Integer.class)
				.single();
		return count != null && count > 0;
	}

	public void insert(ProjectRecord project) {
		try {
			jdbcClient.sql("""
							INSERT INTO projects (
								id, name, description, folder_path, workflow_recipe_id, status, created_at, updated_at
							)
							VALUES (:id, :name, :description, :folderPath, :workflowRecipeId, :status, :createdAt, :updatedAt)
							""")
					.param("id", project.id())
					.param("name", project.name())
					.param("description", project.description())
					.param("folderPath", project.folderPath())
					.param("workflowRecipeId", project.workflowRecipeId())
					.param("status", project.status())
					.param("createdAt", project.createdAt().toString())
					.param("updatedAt", project.updatedAt().toString())
					.update();
		}
		catch (DuplicateKeyException exception) {
			throw new DuplicateProjectException(project.folderPath());
		}
	}

	private static ProjectRecord mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ProjectRecord(
				resultSet.getString("id"),
				resultSet.getString("name"),
				resultSet.getString("description"),
				resultSet.getString("folder_path"),
				resultSet.getString("workflow_recipe_id"),
				resultSet.getString("status"),
				Instant.parse(resultSet.getString("created_at")),
				Instant.parse(resultSet.getString("updated_at"))
		);
	}
}
