package io.github.samzhu.grimo.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S004 API acceptance tests for Project-owned manual Task creation.
 *
 * <p>These tests exercise the HTTP behavior and persisted SQLite rows that the
 * Task board depends on: create a BACKLOG Task, copy its Task Workflow, keep
 * workflow execution inactive, and list Tasks by Project.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite:file:s004-task-api?mode=memory&cache=shared",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class TaskApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@TempDir
	private Path tempDir;

	@Test
	@DisplayName("AC-S004-1: creates a Project-owned BACKLOG Task with copied workflow and no active run")
	void createsBacklogTaskWithCopiedWorkflow() throws Exception {
		String projectId = createProject("s004-task-api", "web-service-development");
		String request = """
				{
				  "title": "  補上 Task API  ",
				  "body": "  建立 backend API 並接 CreateTaskDialog  ",
				  "labels": ["backend", " enhancement ", "", "backend"],
				  "state": "READY",
				  "source": "external",
				  "workflowRecipeId": "research",
				  "skill": "backend-engineer"
				}
				""";

		String taskResponse = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.matchesPattern(
						"/api/projects/" + projectId + "/tasks/[0-9A-HJKMNP-TV-Z]{13}")))
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.projectId").value(projectId))
				.andExpect(jsonPath("$.title").value("補上 Task API"))
				.andExpect(jsonPath("$.body").value("建立 backend API 並接 CreateTaskDialog"))
				.andExpect(jsonPath("$.description").value("建立 backend API 並接 CreateTaskDialog"))
				.andExpect(jsonPath("$.state").value("BACKLOG"))
				.andExpect(jsonPath("$.source").value("manual"))
				.andExpect(jsonPath("$.workflowRecipeId").value("web-service-development"))
				.andExpect(jsonPath("$.workflowSummary.currentStep", nullValue()))
				.andExpect(jsonPath("$.workflowSummary.qualityScore", nullValue()))
				.andExpect(jsonPath("$.acceptance", hasSize(0)))
				.andExpect(jsonPath("$.gaps", hasSize(0)))
				.andExpect(jsonPath("$.evidence", hasSize(0)))
				.andExpect(jsonPath("$.labels", hasSize(2)))
				.andExpect(jsonPath("$.labels", hasItem("backend")))
				.andExpect(jsonPath("$.labels", hasItem("enhancement")))
				.andExpect(jsonPath("$.commentCount").value(0))
				.andExpect(jsonPath("$.skill").doesNotExist())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String taskId = JsonPath.read(taskResponse, "$.id");

		assertThat(singleInt("""
				SELECT COUNT(*)
				FROM tasks
				WHERE id = :taskId
				  AND project_id = :projectId
				  AND title = '補上 Task API'
				  AND body = '建立 backend API 並接 CreateTaskDialog'
				  AND state = 'BACKLOG'
				  AND source = 'manual'
				  AND workflow_recipe_id = 'web-service-development'
				""", taskId, projectId)).isEqualTo(1);
		assertThat(singleInt("SELECT COUNT(*) FROM task_workflows WHERE task_id = :taskId", taskId, projectId))
				.isEqualTo(1);
		assertThat(singleInt("""
				SELECT COUNT(*)
				FROM task_workflow_steps steps
				JOIN task_workflows workflow ON workflow.id = steps.task_workflow_id
				WHERE workflow.task_id = :taskId
				""", taskId, projectId)).isEqualTo(11);
		assertThat(singleInt("SELECT COUNT(*) FROM task_workflow_runs WHERE task_id = :taskId", taskId, projectId))
				.isZero();
	}

	@Test
	@DisplayName("AC-S004-2: lists only Tasks for the selected Project")
	void listsOnlyTasksForSelectedProject() throws Exception {
		String projectA = createProject("s004-list-a", "web-service-development");
		String projectB = createProject("s004-list-b", "research");

		createTask(projectA, "A newest", "frontend");
		createTask(projectB, "B hidden", "backend");
		createTask(projectA, "A oldest", "qa");

		mockMvc.perform(get("/api/projects/{projectId}/tasks", projectA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[*].projectId", everyItem(org.hamcrest.Matchers.equalTo(projectA))))
				.andExpect(jsonPath("$.content[*].title", hasItem("A newest")))
				.andExpect(jsonPath("$.content[*].title", hasItem("A oldest")))
				.andExpect(jsonPath("$.content[*].title", not(hasItem("B hidden"))))
				.andExpect(jsonPath("$.content[*].workflowSummary", hasSize(2)));
	}

	@Test
	@DisplayName("AC-S004-3: rejects invalid Task create without persisted rows")
	void rejectsInvalidCreateWithoutRows() throws Exception {
		int taskRowsBeforeMissingProject = singleInt("SELECT COUNT(*) FROM tasks");

		mockMvc.perform(post("/api/projects/{projectId}/tasks", "missing-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "找不到 Project 的 Task",
								  "body": "",
								  "labels": []
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("找不到 Project"));
		assertThat(singleInt("SELECT COUNT(*) FROM tasks")).isEqualTo(taskRowsBeforeMissingProject);
		assertThatThrownBy(() -> jdbcClient.sql("""
						INSERT INTO tasks (
							id, project_id, title, body, source, state, workflow_recipe_id, labels, created_at, updated_at
						)
						VALUES (
							'orphan-task', 'missing-project', 'orphan', '', 'manual', 'BACKLOG',
							'web-service-development', '[]', '2026-06-04T00:00:00Z', '2026-06-04T00:00:00Z'
						)
						""")
				.update())
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("FOREIGN KEY constraint failed");

		String projectId = createProject("s004-invalid", "web-service-development");
		int taskRowsBeforeBlankTitle = singleInt("SELECT COUNT(*) FROM tasks");

		mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "   ",
								  "body": "blank title",
								  "labels": ["backend"]
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("請填寫 Task 標題"));
		assertThat(singleInt("SELECT COUNT(*) FROM tasks")).isEqualTo(taskRowsBeforeBlankTitle);
	}

	private String createProject(String name, String workflowRecipeId) throws Exception {
		Path projectPath = Files.createDirectory(tempDir.resolve(name));
		String response = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "S004 test Project",
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

	private String createTask(String projectId, String title, String label) throws Exception {
		String response = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "%s",
								  "body": "project scoped list",
								  "labels": ["%s"]
								}
								""".formatted(title, label)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private int singleInt(String sql, String taskId, String projectId) {
		return jdbcClient.sql(sql)
				.param("taskId", taskId)
				.param("projectId", projectId)
				.query(Integer.class)
				.single();
	}

	private int singleInt(String sql) {
		return jdbcClient.sql(sql)
				.query(Integer.class)
				.single();
	}
}
