package io.github.samzhu.grimo.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S009 API acceptance tests for Task board workflow summary projections.
 *
 * <p>The tests prove `workflowSummary` comes from active workflow evidence rows:
 * two Tasks return different current steps and scores, while a BACKLOG Task
 * still returns null summary values.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite:file:s009-workflow-summary?mode=memory&cache=shared",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class WorkflowSummaryProjectionTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private TaskWorkflowTransitionService transitionService;

	@TempDir
	private Path tempDir;

	@Test
	@DisplayName("AC-S009-2: Task list projects current step and latest quality score from workflow evidence")
	void taskListProjectsSummaryFromWorkflowEvidence() throws Exception {
		String projectId = createProject("s009-summary", "web-service-development");
		String discussTaskId = createTask(projectId, "Discuss summary");
		String devTaskId = createTask(projectId, "Dev summary");
		String backlogTaskId = createTask(projectId, "Backlog summary stays empty");

		TaskWorkflowRunRecord discussRun = transitionService.openChatForBacklogTask(discussTaskId);
		TaskWorkflowRunRecord devRun = transitionService.openChatForBacklogTask(devTaskId);
		String discussStepId = runStepId(discussRun.id(), "discuss");
		String devStepId = runStepId(devRun.id(), "dev");
		insertQualityRun(discussStepId, "discuss-attempt-1", 1, 7.0);
		insertQualityRun(discussStepId, "discuss-attempt-2", 2, 8.5);
		moveActiveStepToDev(devRun.id(), devTaskId);
		insertQualityRun(devStepId, "dev-attempt-1", 1, 9.0);
		insertQualityRun(devStepId, "dev-attempt-2", 2, 10.0);

		mockMvc.perform(get("/api/projects/{projectId}/tasks", projectId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(3)))
				.andExpect(jsonPath("$.content[*].id", hasItem(discussTaskId)))
				.andExpect(jsonPath("$.content[*].id", hasItem(devTaskId)))
				.andExpect(jsonPath("$.content[*].id", hasItem(backlogTaskId)));
		String response = mockMvc.perform(get("/api/projects/{projectId}/tasks", projectId))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertWorkflowSummary(response, discussTaskId, "Discuss", 8.5);
		assertWorkflowSummary(response, devTaskId, "Dev", 10.0);
		assertWorkflowSummary(response, backlogTaskId, null, null);
		assertThat(taskColumns()).doesNotContain("step", "score", "workflow_summary");
	}

	@Test
	@DisplayName("AC-S009-2: BACKLOG Task does not project planned workflow copy as progress")
	void backlogTaskDoesNotProjectPlannedWorkflowCopyAsProgress() throws Exception {
		String projectId = createProject("s009-backlog-summary", "web-service-development");
		String taskId = createTask(projectId, "Backlog copied workflow only");

		mockMvc.perform(get("/api/projects/{projectId}/tasks", projectId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(taskId))
				.andExpect(jsonPath("$.content[0].workflowSummary.currentStep", nullValue()))
				.andExpect(jsonPath("$.content[0].workflowSummary.qualityScore", nullValue()));
	}

	private String createProject(String name, String workflowRecipeId) throws Exception {
		Path projectPath = Files.createDirectory(tempDir.resolve(name));
		String response = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "S009 summary Project",
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
								  "body": "S009 summary evidence",
								  "labels": ["backend"]
								}
								""".formatted(title)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private void moveActiveStepToDev(String runId, String taskId) {
		jdbcClient.sql("""
						UPDATE task_workflow_run_steps
						SET state = CASE WHEN step_key = 'dev' THEN 'ACTIVE' ELSE 'PASSED' END,
						    started_at = '2026-06-04T00:00:00Z',
						    completed_at = CASE WHEN step_key = 'dev' THEN null ELSE '2026-06-04T00:00:00Z' END,
						    updated_at = '2026-06-04T00:00:00Z'
						WHERE workflow_run_id = :runId
						""")
				.param("runId", runId)
				.update();
		jdbcClient.sql("""
						UPDATE tasks
						SET state = 'RUNNING',
						    updated_at = '2026-06-04T00:00:00Z'
						WHERE id = :taskId
						""")
				.param("taskId", taskId)
				.update();
	}

	private String runStepId(String runId, String stepKey) {
		return jdbcClient.sql("""
						SELECT id
						FROM task_workflow_run_steps
						WHERE workflow_run_id = :runId
						  AND step_key = :stepKey
						""")
				.param("runId", runId)
				.param("stepKey", stepKey)
				.query(String.class)
				.single();
	}

	private void insertQualityRun(String runStepId, String id, int attempt, double score) {
		jdbcClient.sql("""
						INSERT INTO task_workflow_quality_runs (
							id, workflow_run_step_id, attempt, output_summary, output_ref,
							review_summary, quality_score, fix_summary, created_at, updated_at
						)
						VALUES (
							:id, :runStepId, :attempt, 'output', null,
							'review', :score, 'fix', '2026-06-04T00:00:00Z', '2026-06-04T00:00:00Z'
						)
						""")
				.param("id", id)
				.param("runStepId", runStepId)
				.param("attempt", attempt)
				.param("score", score)
				.update();
	}

	private void assertWorkflowSummary(String response, String taskId, String expectedStep, Double expectedScore) {
		List<String> ids = JsonPath.read(response, "$.content[*].id");
		int index = ids.indexOf(taskId);
		assertThat(index).isNotNegative();
		assertThat(JsonPath.<String>read(response, "$.content[%d].workflowSummary.currentStep".formatted(index)))
				.isEqualTo(expectedStep);
		assertThat(JsonPath.<Double>read(response, "$.content[%d].workflowSummary.qualityScore".formatted(index)))
				.isEqualTo(expectedScore);
	}

	private List<String> taskColumns() {
		return jdbcClient.sql("PRAGMA table_info(tasks)")
				.query((resultSet, rowNumber) -> resultSet.getString("name"))
				.list();
	}
}
