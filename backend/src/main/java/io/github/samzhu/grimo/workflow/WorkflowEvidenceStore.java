package io.github.samzhu.grimo.workflow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.samzhu.grimo.task.WorkflowSummaryResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQLite-backed persistence for Task Workflow copy and execution evidence rows.
 *
 * @see TaskWorkflowService
 */
@Repository
public class WorkflowEvidenceStore {

	private final JdbcClient jdbcClient;

	public WorkflowEvidenceStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public void insertTaskWorkflow(TaskWorkflowRecord workflow) {
		jdbcClient.sql("""
						INSERT INTO task_workflows (
							id, task_id, source_type, source_ref, source_hash, created_at, updated_at
						)
						VALUES (
							:id, :taskId, :sourceType, :sourceRef, :sourceHash, :createdAt, :updatedAt
						)
						""")
				.param("id", workflow.id())
				.param("taskId", workflow.taskId())
				.param("sourceType", workflow.sourceType())
				.param("sourceRef", workflow.sourceRef())
				.param("sourceHash", workflow.sourceHash())
				.param("createdAt", workflow.createdAt().toString())
				.param("updatedAt", workflow.updatedAt().toString())
				.update();
	}

	public void insertTaskWorkflowSteps(List<TaskWorkflowStepRecord> steps) {
		for (TaskWorkflowStepRecord step : steps) {
			jdbcClient.sql("""
							INSERT INTO task_workflow_steps (
								id, task_workflow_id, step_key, step_label, task_state, step_order, created_at, updated_at
							)
							VALUES (
								:id, :taskWorkflowId, :stepKey, :stepLabel, :taskState, :stepOrder, :createdAt, :updatedAt
							)
							""")
					.param("id", step.id())
					.param("taskWorkflowId", step.taskWorkflowId())
					.param("stepKey", step.stepKey())
					.param("stepLabel", step.stepLabel())
					.param("taskState", step.taskState())
					.param("stepOrder", step.stepOrder())
					.param("createdAt", step.createdAt().toString())
					.param("updatedAt", step.updatedAt().toString())
					.update();
		}
	}

	public Optional<TaskWorkflowRecord> findTaskWorkflowByTaskId(String taskId) {
		return jdbcClient.sql("""
						SELECT id, task_id, source_type, source_ref, source_hash, created_at, updated_at
						FROM task_workflows
						WHERE task_id = :taskId
						""")
				.param("taskId", taskId)
				.query(this::mapTaskWorkflow)
				.optional();
	}

	public List<TaskWorkflowStepRecord> findTaskWorkflowSteps(String taskWorkflowId) {
		return jdbcClient.sql("""
						SELECT id, task_workflow_id, step_key, step_label, task_state, step_order, created_at, updated_at
						FROM task_workflow_steps
						WHERE task_workflow_id = :taskWorkflowId
						ORDER BY step_order ASC
						""")
				.param("taskWorkflowId", taskWorkflowId)
				.query(this::mapTaskWorkflowStep)
				.list();
	}

	public Optional<TaskWorkflowRunRecord> findActiveRunByTaskId(String taskId) {
		return jdbcClient.sql("""
						SELECT id, task_id, task_workflow_id, state, started_at, completed_at, created_at, updated_at
						FROM task_workflow_runs
						WHERE task_id = :taskId
						  AND state = 'ACTIVE'
						ORDER BY updated_at DESC
						LIMIT 1
						""")
				.param("taskId", taskId)
				.query(this::mapWorkflowRun)
				.optional();
	}

	public Map<String, WorkflowSummaryResponse> findWorkflowSummariesByProjectId(String projectId) {
		// S009: board summary ranks BLOCKED before ACTIVE before PENDING, then reads the selected step's latest score.
		return jdbcClient.sql("""
						WITH selected_steps AS (
							SELECT
								run.task_id,
								step.id AS step_id,
								step.step_label,
								ROW_NUMBER() OVER (
									PARTITION BY run.task_id
									ORDER BY
										CASE step.state
											WHEN 'BLOCKED' THEN 1
											WHEN 'ACTIVE' THEN 2
											WHEN 'PENDING' THEN 3
											ELSE 4
										END,
										step.step_order
								) AS selected_rank
							FROM task_workflow_runs run
							JOIN tasks task ON task.id = run.task_id
							JOIN task_workflow_run_steps step ON step.workflow_run_id = run.id
							WHERE task.project_id = :projectId
							  AND run.state = 'ACTIVE'
							  AND step.state IN ('BLOCKED', 'ACTIVE', 'PENDING')
						),
						latest_quality AS (
							SELECT
								workflow_run_step_id,
								quality_score,
								ROW_NUMBER() OVER (
									PARTITION BY workflow_run_step_id
									ORDER BY attempt DESC
								) AS latest_rank
							FROM task_workflow_quality_runs
						)
						SELECT
							selected_steps.task_id,
							selected_steps.step_label,
							latest_quality.quality_score
						FROM selected_steps
						LEFT JOIN latest_quality
							ON latest_quality.workflow_run_step_id = selected_steps.step_id
						   AND latest_quality.latest_rank = 1
						WHERE selected_steps.selected_rank = 1
						""")
				.param("projectId", projectId)
				.query((resultSet, rowNumber) -> Map.entry(
						resultSet.getString("task_id"),
						new WorkflowSummaryResponse(
								resultSet.getString("step_label"),
								readNullableDouble(resultSet, "quality_score")
						)
				))
				.list()
				.stream()
				.collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
	}

	public Optional<TaskWorkflowDetailResponse> findWorkflowDetail(String projectId, String taskId) {
		Optional<WorkflowDetailHeader> header = jdbcClient.sql("""
						SELECT
							task.id AS task_id,
							task.project_id,
							workflow.source_type,
							workflow.source_ref,
							workflow.source_hash,
							run.id AS workflow_run_id
						FROM tasks task
						JOIN task_workflows workflow ON workflow.task_id = task.id
						LEFT JOIN task_workflow_runs run
							ON run.task_id = task.id
						   AND run.state = 'ACTIVE'
						WHERE task.id = :taskId
						  AND task.project_id = :projectId
						""")
				.param("projectId", projectId)
				.param("taskId", taskId)
				.query(this::mapWorkflowDetailHeader)
				.optional();
		return header.map(value -> new TaskWorkflowDetailResponse(
				value.taskId(),
				value.projectId(),
				value.workflowRunId(),
				value.workflowSource(),
				value.workflowRunId() == null ? List.of() : findWorkflowStepEvidence(value.workflowRunId())
		));
	}

	public void insertWorkflowRun(TaskWorkflowRunRecord run) {
		jdbcClient.sql("""
						INSERT INTO task_workflow_runs (
							id, task_id, task_workflow_id, state, started_at, completed_at, created_at, updated_at
						)
						VALUES (
							:id, :taskId, :taskWorkflowId, :state, :startedAt, :completedAt, :createdAt, :updatedAt
						)
						""")
				.param("id", run.id())
				.param("taskId", run.taskId())
				.param("taskWorkflowId", run.taskWorkflowId())
				.param("state", run.state())
				.param("startedAt", run.startedAt().toString())
				.param("completedAt", run.completedAt() == null ? null : run.completedAt().toString())
				.param("createdAt", run.createdAt().toString())
				.param("updatedAt", run.updatedAt().toString())
				.update();
	}

	public void insertWorkflowRunSteps(List<TaskWorkflowRunStepRecord> steps) {
		for (TaskWorkflowRunStepRecord step : steps) {
			jdbcClient.sql("""
							INSERT INTO task_workflow_run_steps (
								id, workflow_run_id, step_key, step_label, task_state, step_order,
								state, started_at, completed_at, created_at, updated_at
							)
							VALUES (
								:id, :workflowRunId, :stepKey, :stepLabel, :taskState, :stepOrder,
								:state, :startedAt, :completedAt, :createdAt, :updatedAt
							)
							""")
					.param("id", step.id())
					.param("workflowRunId", step.workflowRunId())
					.param("stepKey", step.stepKey())
					.param("stepLabel", step.stepLabel())
					.param("taskState", step.taskState())
					.param("stepOrder", step.stepOrder())
					.param("state", step.state())
					.param("startedAt", step.startedAt() == null ? null : step.startedAt().toString())
					.param("completedAt", step.completedAt() == null ? null : step.completedAt().toString())
					.param("createdAt", step.createdAt().toString())
					.param("updatedAt", step.updatedAt().toString())
					.update();
		}
	}

	private TaskWorkflowRecord mapTaskWorkflow(ResultSet resultSet, int rowNumber) throws SQLException {
		return new TaskWorkflowRecord(
				resultSet.getString("id"),
				resultSet.getString("task_id"),
				resultSet.getString("source_type"),
				resultSet.getString("source_ref"),
				resultSet.getString("source_hash"),
				Instant.parse(resultSet.getString("created_at")),
				Instant.parse(resultSet.getString("updated_at"))
		);
	}

	private TaskWorkflowStepRecord mapTaskWorkflowStep(ResultSet resultSet, int rowNumber) throws SQLException {
		return new TaskWorkflowStepRecord(
				resultSet.getString("id"),
				resultSet.getString("task_workflow_id"),
				resultSet.getString("step_key"),
				resultSet.getString("step_label"),
				resultSet.getString("task_state"),
				resultSet.getInt("step_order"),
				Instant.parse(resultSet.getString("created_at")),
				Instant.parse(resultSet.getString("updated_at"))
		);
	}

	private TaskWorkflowRunRecord mapWorkflowRun(ResultSet resultSet, int rowNumber) throws SQLException {
		return new TaskWorkflowRunRecord(
				resultSet.getString("id"),
				resultSet.getString("task_id"),
				resultSet.getString("task_workflow_id"),
				resultSet.getString("state"),
				Instant.parse(resultSet.getString("started_at")),
				parseNullableInstant(resultSet.getString("completed_at")),
				Instant.parse(resultSet.getString("created_at")),
				Instant.parse(resultSet.getString("updated_at"))
		);
	}

	private WorkflowDetailHeader mapWorkflowDetailHeader(ResultSet resultSet, int rowNumber) throws SQLException {
		return new WorkflowDetailHeader(
				resultSet.getString("task_id"),
				resultSet.getString("project_id"),
				resultSet.getString("workflow_run_id"),
				new WorkflowSourceResponse(
						resultSet.getString("source_type"),
						resultSet.getString("source_ref"),
						resultSet.getString("source_hash")
				)
		);
	}

	private List<WorkflowStepEvidenceResponse> findWorkflowStepEvidence(String workflowRunId) {
		return jdbcClient.sql("""
						WITH latest_quality AS (
							SELECT
								workflow_run_step_id,
								attempt,
								quality_score,
								review_summary,
								fix_summary,
								updated_at,
								ROW_NUMBER() OVER (
									PARTITION BY workflow_run_step_id
									ORDER BY attempt DESC
								) AS latest_rank
							FROM task_workflow_quality_runs
						)
						SELECT
							step.step_key,
							step.step_label,
							step.task_state,
							step.step_order,
							step.state,
							step.started_at,
							step.completed_at,
							latest_quality.attempt,
							latest_quality.quality_score,
							latest_quality.review_summary,
							latest_quality.fix_summary,
							latest_quality.updated_at AS quality_updated_at
						FROM task_workflow_run_steps step
						LEFT JOIN latest_quality
							ON latest_quality.workflow_run_step_id = step.id
						   AND latest_quality.latest_rank = 1
						WHERE step.workflow_run_id = :workflowRunId
						ORDER BY step.step_order ASC
						""")
				.param("workflowRunId", workflowRunId)
				.query(this::mapWorkflowStepEvidence)
				.list()
				.stream()
				.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
	}

	private WorkflowStepEvidenceResponse mapWorkflowStepEvidence(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new WorkflowStepEvidenceResponse(
				resultSet.getString("step_key"),
				resultSet.getString("step_label"),
				resultSet.getString("task_state"),
				resultSet.getInt("step_order"),
				resultSet.getString("state"),
				parseNullableInstant(resultSet.getString("started_at")),
				parseNullableInstant(resultSet.getString("completed_at")),
				mapQualitySummary(resultSet)
		);
	}

	private WorkflowQualitySummaryResponse mapQualitySummary(ResultSet resultSet) throws SQLException {
		int attempt = resultSet.getInt("attempt");
		if (resultSet.wasNull()) {
			return null;
		}
		Double score = readNullableDouble(resultSet, "quality_score");
		return new WorkflowQualitySummaryResponse(
				attempt,
				score,
				score != null && score > 9,
				resultSet.getString("review_summary"),
				resultSet.getString("fix_summary"),
				Instant.parse(resultSet.getString("quality_updated_at"))
		);
	}

	private Instant parseNullableInstant(String value) {
		return value == null ? null : Instant.parse(value);
	}

	private Double readNullableDouble(ResultSet resultSet, String column) throws SQLException {
		double value = resultSet.getDouble(column);
		return resultSet.wasNull() ? null : value;
	}

	private record WorkflowDetailHeader(
			String taskId,
			String projectId,
			String workflowRunId,
			WorkflowSourceResponse workflowSource
	) {
	}
}
