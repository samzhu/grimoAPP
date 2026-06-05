package io.github.samzhu.grimo.task;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * SQLite-backed persistence for Task root rows.
 *
 * @see TaskService
 */
@Repository
public class TaskStore {

	private final JdbcClient jdbcClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public TaskStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public void insert(TaskRecord task) {
		jdbcClient.sql("""
						INSERT INTO tasks (
							id, project_id, title, body, source, state, workflow_recipe_id, labels, created_at, updated_at
						)
						VALUES (
							:id, :projectId, :title, :body, :source, :state, :workflowRecipeId, :labels, :createdAt, :updatedAt
						)
						""")
				.param("id", task.id())
				.param("projectId", task.projectId())
				.param("title", task.title())
				.param("body", task.body())
				.param("source", task.source())
				.param("state", task.state())
				.param("workflowRecipeId", task.workflowRecipeId())
				.param("labels", writeLabels(task.labels()))
				.param("createdAt", task.createdAt().toString())
				.param("updatedAt", task.updatedAt().toString())
				.update();
	}

	public List<TaskRecord> findByProjectId(String projectId) {
		return jdbcClient.sql("""
						SELECT id, project_id, title, body, source, state, workflow_recipe_id, labels, created_at, updated_at
						FROM tasks
						WHERE project_id = :projectId
						ORDER BY updated_at DESC, id DESC
						""")
				.param("projectId", projectId)
				.query(this::mapTask)
				.list();
	}

	public Optional<TaskRecord> findById(String taskId) {
		return jdbcClient.sql("""
						SELECT id, project_id, title, body, source, state, workflow_recipe_id, labels, created_at, updated_at
						FROM tasks
						WHERE id = :taskId
						""")
				.param("taskId", taskId)
				.query(this::mapTask)
				.optional();
	}

	public int moveState(String taskId, String fromState, String toState, Instant now) {
		return jdbcClient.sql("""
						UPDATE tasks
						SET state = :toState,
						    updated_at = :updatedAt
						WHERE id = :taskId
						  AND state = :fromState
						""")
				.param("taskId", taskId)
				.param("fromState", fromState)
				.param("toState", toState)
				.param("updatedAt", now.toString())
				.update();
	}

	private TaskRecord mapTask(ResultSet resultSet, int rowNumber) throws SQLException {
		return new TaskRecord(
				resultSet.getString("id"),
				resultSet.getString("project_id"),
				resultSet.getString("title"),
				resultSet.getString("body"),
				resultSet.getString("source"),
				resultSet.getString("state"),
				resultSet.getString("workflow_recipe_id"),
				readLabels(resultSet.getString("labels")),
				Instant.parse(resultSet.getString("created_at")),
				Instant.parse(resultSet.getString("updated_at"))
		);
	}

	private String writeLabels(List<String> labels) {
		try {
			return objectMapper.writeValueAsString(labels);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Unable to serialize task labels.", exception);
		}
	}

	private List<String> readLabels(String labels) {
		if (labels == null || labels.equals("[]")) {
			return List.of();
		}
		try {
			return objectMapper.readValue(labels, new TypeReference<List<String>>() {
			});
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Unable to deserialize task labels.", exception);
		}
	}
}
