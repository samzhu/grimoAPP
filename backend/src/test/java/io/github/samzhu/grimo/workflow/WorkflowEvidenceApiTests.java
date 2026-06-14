package io.github.samzhu.grimo.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S009 API acceptance tests for read-only workflow detail evidence.
 *
 * <p>The tests prove Task detail can read ordered workflow execution steps and
 * latest Quality Loop summaries, while Project isolation and read-only routing
 * prevent public clients from reading or writing the wrong evidence.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite:file:s009-workflow-detail?mode=memory&cache=shared",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class WorkflowEvidenceApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private TaskWorkflowTransitionService transitionService;

	@TempDir
	private Path tempDir;

	@Test
	@DisplayName("AC-S009-3: workflow detail returns ordered step evidence and latest quality result")
	void workflowDetailReturnsOrderedStepsAndLatestQualityResult() throws Exception {
		String projectId = createProject("s009-detail", "web-service-development");
		String taskId = createTask(projectId, "Workflow detail evidence");
		TaskWorkflowRunRecord run = transitionService.openChatForBacklogTask(taskId);
		String discussStepId = runStepId(run.id(), "discuss");
		insertQualityRun(discussStepId, "detail-discuss-attempt-1", 1, 7.0, "缺欄位範例", "補 request/response");
		insertQualityRun(discussStepId, "detail-discuss-attempt-2", 2, 8.5, "仍缺 commentCount 邊界", "補 projection boundary");

		mockMvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/workflow", projectId, taskId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taskId").value(taskId))
				.andExpect(jsonPath("$.projectId").value(projectId))
				.andExpect(jsonPath("$.workflowRunId").value(run.id()))
				.andExpect(jsonPath("$.workflowSource.type").value("RECIPE"))
				.andExpect(jsonPath("$.workflowSource.ref").value("web-service-development"))
				.andExpect(jsonPath("$.workflowSource.hash", nullValue()))
				.andExpect(jsonPath("$.steps", hasSize(11)))
				.andExpect(jsonPath("$.steps[0].stepKey").value("discuss"))
				.andExpect(jsonPath("$.steps[0].stepLabel").value("Discuss"))
				.andExpect(jsonPath("$.steps[0].taskState").value("DEFINING"))
				.andExpect(jsonPath("$.steps[0].stepOrder").value(10))
				.andExpect(jsonPath("$.steps[0].state").value("ACTIVE"))
				.andExpect(jsonPath("$.steps[0].qualitySummary.latestAttempt").value(2))
				.andExpect(jsonPath("$.steps[0].qualitySummary.latestScore").value(8.5))
				.andExpect(jsonPath("$.steps[0].qualitySummary.passed").value(false))
				.andExpect(jsonPath("$.steps[0].qualitySummary.latestReviewSummary").value("仍缺 commentCount 邊界"))
				.andExpect(jsonPath("$.steps[0].qualitySummary.latestFixSummary").value("補 projection boundary"))
				.andExpect(jsonPath("$.steps[1].stepKey").value("explore"))
				.andExpect(jsonPath("$.steps[1].qualitySummary", nullValue()))
				.andExpect(jsonPath("$.steps[6].stepKey").value("dev"))
				.andExpect(jsonPath("$.steps[6].taskState").value("RUNNING"));
	}

	@Test
	@DisplayName("AC-S009-4: workflow detail cannot be read across Project boundaries")
	void workflowDetailCannotBeReadAcrossProjects() throws Exception {
		String projectAId = createProject("s009-project-a", "web-service-development");
		String taskAId = createTask(projectAId, "Project A Task");
		transitionService.openChatForBacklogTask(taskAId);
		String projectBId = createProject("s009-project-b", "web-service-development");

		mockMvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/workflow", projectBId, taskAId))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("AC-S009-5: workflow evidence write operations are not public API")
	void workflowEvidenceWriteOperationsAreNotPublicApi() throws Exception {
		String projectId = createProject("s009-read-only", "web-service-development");
		String taskId = createTask(projectId, "Read only workflow");
		transitionService.openChatForBacklogTask(taskId);
		int runRowsBefore = countRows("task_workflow_runs");
		int stepRowsBefore = countRows("task_workflow_run_steps");
		int qualityRowsBefore = countRows("task_workflow_quality_runs");

		mockMvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/workflow", projectId, taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "stepKey": "dev",
								  "qualityScore": 10
								}
								"""))
				.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}/workflow", projectId, taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/workflow", projectId, taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}/workflow", projectId, taskId))
				.andExpect(status().isMethodNotAllowed());

		assertThat(countRows("task_workflow_runs")).isEqualTo(runRowsBefore);
		assertThat(countRows("task_workflow_run_steps")).isEqualTo(stepRowsBefore);
		assertThat(countRows("task_workflow_quality_runs")).isEqualTo(qualityRowsBefore);
	}

	private String createProject(String name, String workflowRecipeId) throws Exception {
		Path projectPath = Files.createDirectory(tempDir.resolve(name));
		String response = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "S009 detail Project",
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
								  "body": "S009 detail evidence",
								  "labels": ["backend"]
								}
								""".formatted(title)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.id");
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

	private void insertQualityRun(
			String runStepId,
			String id,
			int attempt,
			double score,
			String reviewSummary,
			String fixSummary
	) {
		jdbcClient.sql("""
						INSERT INTO task_workflow_quality_runs (
							id, workflow_run_step_id, attempt, output_summary, output_ref,
							review_summary, quality_score, fix_summary, created_at, updated_at
						)
						VALUES (
							:id, :runStepId, :attempt, 'step output', null,
							:reviewSummary, :score, :fixSummary,
							'2026-06-05T00:00:00Z', '2026-06-05T00:00:00Z'
						)
						""")
				.param("id", id)
				.param("runStepId", runStepId)
				.param("attempt", attempt)
				.param("score", score)
				.param("reviewSummary", reviewSummary)
				.param("fixSummary", fixSummary)
				.update();
	}

	private int countRows(String tableName) {
		return jdbcClient.sql("SELECT COUNT(*) FROM " + tableName)
				.query(Integer.class)
				.single();
	}
}
