package io.github.samzhu.grimo.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S009 storage acceptance tests for starting workflow evidence from a BACKLOG Task.
 *
 * <p>The tests create real S004 Project-owned Tasks, then exercise the backend
 * transition that first Chat will call: update the Task to DEFINING, create one
 * active workflow run, copy execution steps, and protect evidence with SQLite
 * constraints.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite:file:s009-workflow-evidence?mode=memory&cache=shared",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class WorkflowEvidenceStoreTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private TaskWorkflowTransitionService transitionService;

	@TempDir
	private Path tempDir;

	@Test
	@DisplayName("AC-S009-1: first Chat starts one active workflow run from copied Task Workflow steps")
	void firstChatStartsActiveWorkflowRunFromCopiedTaskWorkflowSteps() throws Exception {
		String projectId = createProject("s009-first-chat", "web-service-development");
		String firstTaskId = createTask(projectId, "第一次 Chat 啟動 workflow");

		assertThat(singleInt("SELECT COUNT(*) FROM task_workflow_runs WHERE task_id = :taskId", firstTaskId))
				.isZero();

		TaskWorkflowRunRecord firstRun = transitionService.openChatForBacklogTask(firstTaskId);

		assertThat(taskState(firstTaskId)).isEqualTo("DEFINING");
		assertThat(singleInt("""
				SELECT COUNT(*)
				FROM task_workflow_runs
				WHERE task_id = :taskId
				  AND state = 'ACTIVE'
				""", firstTaskId)).isEqualTo(1);
		assertThat(singleInt("""
				SELECT COUNT(*)
				FROM task_workflow_run_steps
				WHERE workflow_run_id = :runId
				""", firstRun.id())).isEqualTo(9);
		assertRunStep(firstRun.id(), "discuss", "Discuss", "DEFINING", 10, "ACTIVE");
		assertRunStep(firstRun.id(), "explore", "Explore", "DEFINING", 20, "PENDING");
		assertRunStep(firstRun.id(), "dev", "Dev", "RUNNING", 70, "PENDING");

		jdbcClient.sql("""
						UPDATE task_workflow_steps
						SET step_label = 'Changed Discuss'
						WHERE step_key = 'discuss'
						  AND task_workflow_id = :taskWorkflowId
						""")
				.param("taskWorkflowId", firstRun.taskWorkflowId())
				.update();
		assertThat(runStepLabel(firstRun.id(), "discuss")).isEqualTo("Discuss");

		TaskWorkflowRunRecord reopenedRun = transitionService.openChatForBacklogTask(firstTaskId);
		assertThat(reopenedRun.id()).isEqualTo(firstRun.id());
		assertThat(singleInt("""
				SELECT COUNT(*)
				FROM task_workflow_runs
				WHERE task_id = :taskId
				  AND state = 'ACTIVE'
				""", firstTaskId)).isEqualTo(1);

		String secondTaskId = createTask(projectId, "第二個 Task 有自己的 run");
		TaskWorkflowRunRecord secondRun = transitionService.openChatForBacklogTask(secondTaskId);
		assertThat(secondRun.id()).isNotEqualTo(firstRun.id());
		assertRunStep(secondRun.id(), "dev", "Dev", "RUNNING", 70, "PENDING");
	}

	@Test
	@DisplayName("AC-S009-1: workflow evidence rejects orphan rows and duplicate active evidence")
	void rejectsOrphanRowsAndDuplicateActiveEvidence() throws Exception {
		String projectId = createProject("s009-constraints", "web-service-development");
		String taskId = createTask(projectId, "workflow evidence constraints");
		TaskWorkflowRunRecord run = transitionService.openChatForBacklogTask(taskId);
		String runStepId = singleString("""
				SELECT id
				FROM task_workflow_run_steps
				WHERE workflow_run_id = :runId
				  AND step_key = 'discuss'
				""", run.id());

		assertThatThrownBy(() -> insertRun("missing-task", run.taskWorkflowId(), "orphan-run"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("FOREIGN KEY constraint failed");
		assertThatThrownBy(() -> insertRun(taskId, run.taskWorkflowId(), "duplicate-active-run"))
				.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertRunStep("missing-run", "orphan-run-step"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("FOREIGN KEY constraint failed");
		assertThatThrownBy(() -> insertQualityRun("missing-run-step", "orphan-quality-run", 1))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("FOREIGN KEY constraint failed");

		insertQualityRun(runStepId, "quality-attempt-1", 1);
		assertThatThrownBy(() -> insertQualityRun(runStepId, "quality-attempt-1-duplicate", 1))
				.isInstanceOf(DataAccessException.class);
	}

	private String createProject(String name, String workflowRecipeId) throws Exception {
		Path projectPath = Files.createDirectory(tempDir.resolve(name));
		String response = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "S009 test Project",
								  "projectPath": "%s",
								  "workflowRecipeId": "%s"
								}
								""".formatted(name, projectPath, workflowRecipeId)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String createTask(String projectId, String title) throws Exception {
		String response = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "%s",
								  "body": "S009 workflow evidence",
								  "labels": ["backend"]
								}
								""".formatted(title)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private void assertRunStep(
			String runId,
			String stepKey,
			String stepLabel,
			String taskState,
			int stepOrder,
			String state
	) {
		assertThat(singleInt("""
				SELECT COUNT(*)
				FROM task_workflow_run_steps
				WHERE workflow_run_id = :runId
				  AND step_key = :stepKey
				  AND step_label = :stepLabel
				  AND task_state = :taskState
				  AND step_order = :stepOrder
				  AND state = :state
				""", runId, stepKey, stepLabel, taskState, stepOrder, state)).isEqualTo(1);
	}

	private void insertRun(String taskId, String taskWorkflowId, String id) {
		jdbcClient.sql("""
						INSERT INTO task_workflow_runs (
							id, task_id, task_workflow_id, state, started_at, completed_at, created_at, updated_at
						)
						VALUES (
							:id, :taskId, :taskWorkflowId, 'ACTIVE',
							'2026-06-04T00:00:00Z', null, '2026-06-04T00:00:00Z', '2026-06-04T00:00:00Z'
						)
						""")
				.param("id", id)
				.param("taskId", taskId)
				.param("taskWorkflowId", taskWorkflowId)
				.update();
	}

	private void insertRunStep(String runId, String id) {
		jdbcClient.sql("""
						INSERT INTO task_workflow_run_steps (
							id, workflow_run_id, step_key, step_label, task_state, step_order,
							state, started_at, completed_at, created_at, updated_at
						)
						VALUES (
							:id, :runId, 'orphan', 'Orphan', 'DEFINING', 10,
							'ACTIVE', '2026-06-04T00:00:00Z', null,
							'2026-06-04T00:00:00Z', '2026-06-04T00:00:00Z'
						)
						""")
				.param("id", id)
				.param("runId", runId)
				.update();
	}

	private void insertQualityRun(String runStepId, String id, int attempt) {
		jdbcClient.sql("""
						INSERT INTO task_workflow_quality_runs (
							id, workflow_run_step_id, attempt, output_summary, output_ref,
							review_summary, quality_score, fix_summary, created_at, updated_at
						)
						VALUES (
							:id, :runStepId, :attempt, 'output', null,
							'review', 8.5, 'fix', '2026-06-04T00:00:00Z', '2026-06-04T00:00:00Z'
						)
						""")
				.param("id", id)
				.param("runStepId", runStepId)
				.param("attempt", attempt)
				.update();
	}

	private String taskState(String taskId) {
		return singleString("SELECT state FROM tasks WHERE id = :taskId", taskId);
	}

	private String runStepLabel(String runId, String stepKey) {
		return singleString("""
				SELECT step_label
				FROM task_workflow_run_steps
				WHERE workflow_run_id = :runId
				  AND step_key = :stepKey
				""", runId, stepKey);
	}

	private int singleInt(String sql, String taskId) {
		return jdbcClient.sql(sql)
				.param("taskId", taskId)
				.param("runId", taskId)
				.query(Integer.class)
				.single();
	}

	private int singleInt(
			String sql,
			String runId,
			String stepKey,
			String stepLabel,
			String taskState,
			int stepOrder,
			String state
	) {
		return jdbcClient.sql(sql)
				.param("runId", runId)
				.param("stepKey", stepKey)
				.param("stepLabel", stepLabel)
				.param("taskState", taskState)
				.param("stepOrder", stepOrder)
				.param("state", state)
				.query(Integer.class)
				.single();
	}

	private String singleString(String sql, String id) {
		return jdbcClient.sql(sql)
				.param("taskId", id)
				.param("runId", id)
				.param("runStepId", id)
				.query(String.class)
				.single();
	}

	private String singleString(String sql, String runId, String stepKey) {
		return jdbcClient.sql(sql)
				.param("runId", runId)
				.param("stepKey", stepKey)
				.query(String.class)
				.single();
	}
}
