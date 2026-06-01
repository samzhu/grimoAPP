package io.github.samzhu.grimo.project;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

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
 * S001/S002 API acceptance tests for Project onboarding.
 *
 * <p>These tests exercise the HTTP behavior a frontend user depends on:
 * workflow recipe discovery, Project creation, duplicate protection and
 * validation messages.
 *
 * @see io.github.samzhu.grimo.GrimoApplication
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite:file:s001-project-api?mode=memory&cache=shared",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class ProjectApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@TempDir
	private Path tempDir;

	@Test
	@DisplayName("AC-S002-1: workflow recipes return collection content with per-recipe roles")
	void exposesWorkflowRecipeCatalog() throws Exception {
		mockMvc.perform(get("/api/workflow-recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(4)))
				.andExpect(jsonPath("$.content[*].id",
						hasItems("web-service-development", "coding", "research", "content")))
				.andExpect(jsonPath("$.content[0].roles[*].name",
						hasItems(
								"Product Manager",
								"Architect",
								"Frontend Engineer",
								"Backend Engineer",
								"QA Reviewer",
								"Release Engineer"
						)))
				.andExpect(jsonPath("$.content[?(@.id == 'research')].roles", hasItem(hasSize(0))));
	}

	@Test
	@DisplayName("AC-S002-2: Web 服務開發 recipe exposes steps and quality loop summary")
	void exposesWebServiceDevelopmentRecipeDefinition() throws Exception {
		mockMvc.perform(get("/api/workflow-recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value("web-service-development"))
				.andExpect(jsonPath("$.content[0].name").value("Web 服務開發"))
				.andExpect(jsonPath("$.content[0].steps[*].name",
						hasItems("Discuss", "Explore", "Prototype", "Spec", "Usage", "Tkt", "Dev", "AI Review", "Human Review")))
				.andExpect(jsonPath("$.content[0].steps[*].name", not(hasItem("Review"))))
				.andExpect(jsonPath("$.content[0].steps[0].taskState").value("DEFINING"))
				.andExpect(jsonPath("$.content[0].steps[0].phase").doesNotExist())
				.andExpect(jsonPath("$.content[0].steps[0].optional").doesNotExist())
				.andExpect(jsonPath("$.content[0].qualityLoopSummary")
						.value("Review → Rating → Fix until quality_score > 9"))
				.andExpect(jsonPath("$.content[0].qualityGateSummary").doesNotExist())
				.andExpect(jsonPath("$.content[0].roles[*].id",
						hasItems(
								"product-manager",
								"architect",
								"frontend-engineer",
								"backend-engineer",
								"qa-reviewer",
								"release-engineer"
						)))
				.andExpect(jsonPath("$.content[0].roles[?(@.id == 'product-manager')].name",
						hasItem("Product Manager")))
				.andExpect(jsonPath("$.content[0].roles[?(@.id == 'product-manager')].description",
						hasItem("釐清產品目標、MVP、使用情境與 acceptance。")))
				.andExpect(jsonPath("$.content[0].roles[*].primarySteps").isNotEmpty());
	}

	@Test
	@DisplayName("AC-S002-9: Project list returns collection content with created Project")
	void createsAndListsProject() throws Exception {
		String projectPath = existingDirectory("grimoAPP").toString();
		String request = """
				{
				  "name": "grimoAPP",
				  "description": "本機 AI 開發工作台",
				  "projectPath": "%s",
				  "workflowRecipeId": "coding"
				}
				""".formatted(projectPath);

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.name").value("grimoAPP"))
				.andExpect(jsonPath("$.projectPath").value(projectPath))
				.andExpect(jsonPath("$.workspacePath").doesNotExist())
				.andExpect(jsonPath("$.folderPath").doesNotExist())
				.andExpect(jsonPath("$.workflowRecipeId").value("coding"))
				.andExpect(jsonPath("$.workflowRecipeName").value("開發工作流"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].name", hasItem("grimoAPP")))
				.andExpect(jsonPath("$.content[*].projectPath", hasItem(projectPath)))
				.andExpect(jsonPath("$.content[0].workspacePath").doesNotExist())
				.andExpect(jsonPath("$.content[0].folderPath").doesNotExist());
	}

	@Test
	@DisplayName("AC-S003-6: rejects duplicate normalized projectPath without persisted rows")
	void rejectsDuplicateWorkspacePath() throws Exception {
		String projectPath = existingDirectory("duplicate").toString();
		String request = """
				{
				  "name": "duplicate-source",
				  "description": "first",
				  "projectPath": "%s",
				  "workflowRecipeId": "coding"
				}
				""".formatted(projectPath);

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("這個專案路徑已經建立過 Project"));

		Integer projectCount = jdbcClient.sql("SELECT COUNT(*) FROM projects WHERE workspace_path = :projectPath")
				.param("projectPath", projectPath)
				.query(Integer.class)
				.single();
		org.assertj.core.api.Assertions.assertThat(projectCount).isEqualTo(1);
	}

	@Test
	@DisplayName("AC-S001-3 rejects invalid project payload")
	void rejectsInvalidProjectPayload() throws Exception {
		String request = """
				{
				  "name": "",
				  "description": "missing name",
				  "workflowRecipeId": "unknown"
				}
				""";

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("請填寫專案名稱與專案工作流"));
	}

	@Test
	@DisplayName("AC-S003-3: creates Project with generated projectPath when request omits projectPath")
	void createsProjectWithGeneratedProjectPathWhenRequestOmitsProjectPath() throws Exception {
		String originalUserHome = System.getProperty("user.home");
		System.setProperty("user.home", tempDir.toString());
		String projectName = "S003 generated path";
		String request = """
				{
				  "name": "%s",
				  "description": "未填專案路徑",
				  "workflowRecipeId": "web-service-development"
				}
				""".formatted(projectName);

		try {
			mockMvc.perform(post("/api/projects")
							.contentType(MediaType.APPLICATION_JSON)
							.content(request))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.projectPath", matchesPattern(".*/\\.grimo/projects/[0-9A-HJKMNP-TV-Z]{13}$")))
					.andExpect(jsonPath("$.workspacePath").doesNotExist())
					.andExpect(jsonPath("$.projectPathSource").doesNotExist())
					.andExpect(jsonPath("$.backendPathReady").doesNotExist())
					.andExpect(jsonPath("$.projectDataPath").doesNotExist());
		}
		finally {
			System.setProperty("user.home", originalUserHome);
		}

		Integer projectCount = jdbcClient.sql("SELECT COUNT(*) FROM projects WHERE name = :name")
				.param("name", projectName)
				.query(Integer.class)
				.single();
		org.assertj.core.api.Assertions.assertThat(projectCount).isEqualTo(1);
	}

	@Test
	@DisplayName("AC-S003-5: rejects invalid projectPath without persisted rows")
	void rejectsInvalidProjectPathWithoutPersistedRows() throws Exception {
		String projectName = "S003 invalid path";
		Path invalidPath = tempDir.resolve("missing-repo");
		String request = """
				{
				  "name": "%s",
				  "description": "不存在的本機資料夾",
				  "projectPath": "%s",
				  "workflowRecipeId": "web-service-development"
				}
				""".formatted(projectName, invalidPath);

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("請輸入有效的本機資料夾路徑"));

		assertNoRowsForProjectName(projectName);
	}

	@Test
	@DisplayName("AC-S002-8: Project create snapshots Web service development workflow roles")
	void createsProjectWithWorkflowRoleSettings() throws Exception {
		String projectPath = existingDirectory("role-settings").toString();
		String request = """
				{
				  "name": "S002 role settings",
				  "description": "角色設定驗收",
				  "projectPath": "%s",
				  "workflowRecipeId": "web-service-development"
				}
				""".formatted(projectPath);

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", matchesPattern("^[0-9A-HJKMNP-TV-Z]{13}$")))
				.andExpect(jsonPath("$.projectPath").value(projectPath))
				.andExpect(jsonPath("$.workspacePath").doesNotExist())
				.andExpect(jsonPath("$.folderPath").doesNotExist())
				.andExpect(jsonPath("$.workflowRecipeId").value("web-service-development"))
				.andExpect(jsonPath("$.workflowRecipeName").value("Web 服務開發"))
				.andExpect(jsonPath("$.workflowRoles", hasSize(6)))
				.andExpect(jsonPath("$.workflowRoles[*].id",
						hasItems(
								"product-manager",
								"architect",
								"frontend-engineer",
								"backend-engineer",
								"qa-reviewer",
								"release-engineer"
						)));

		Integer count = jdbcClient.sql("""
						SELECT COUNT(*)
						FROM project_workflow_roles roles
						JOIN projects projects ON projects.id = roles.project_id
						WHERE projects.workspace_path = :projectPath
						""")
				.param("projectPath", projectPath)
				.query(Integer.class)
				.single();
		org.assertj.core.api.Assertions.assertThat(count).isEqualTo(6);
	}

	@Test
	@DisplayName("AC-S002-10: Project id uses 13-character TSID")
	void createsProjectWithTsid() throws Exception {
		String projectPath = existingDirectory("short-id").toString();
		String request = """
				{
				  "name": "S002 short id",
				  "description": "短 ID 驗收",
				  "projectPath": "%s",
				  "workflowRecipeId": "research"
				}
				""".formatted(projectPath);

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", matchesPattern("^[0-9A-HJKMNP-TV-Z]{13}$")));
	}

	@Test
	@DisplayName("AC-S002-6/8 creates Project with empty role settings for recipes without roles")
	void createsProjectWithEmptyRoleSettingsForRecipesWithoutRoles() throws Exception {
		String projectPath = existingDirectory("empty-role-settings").toString();
		String request = """
				{
				  "name": "S002 empty roles",
				  "description": "空角色驗收",
				  "projectPath": "%s",
				  "workflowRecipeId": "research"
				}
				""".formatted(projectPath);

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.workflowRecipeId").value("research"))
				.andExpect(jsonPath("$.workflowRoles", hasSize(0)));

		Integer count = jdbcClient.sql("""
						SELECT COUNT(*)
						FROM project_workflow_roles roles
						JOIN projects projects ON projects.id = roles.project_id
						WHERE projects.workspace_path = :projectPath
						""")
				.param("projectPath", projectPath)
				.query(Integer.class)
				.single();
		org.assertj.core.api.Assertions.assertThat(count).isZero();
	}

	private Path existingDirectory(String name) throws Exception {
		return Files.createDirectories(tempDir.resolve(name)).toAbsolutePath().normalize();
	}

	private void assertNoRowsForProjectName(String projectName) {
		Integer projectCount = jdbcClient.sql("SELECT COUNT(*) FROM projects WHERE name = :name")
				.param("name", projectName)
				.query(Integer.class)
				.single();
		Integer roleCount = jdbcClient.sql("""
						SELECT COUNT(*)
						FROM project_workflow_roles roles
						JOIN projects projects ON projects.id = roles.project_id
						WHERE projects.name = :name
						""")
				.param("name", projectName)
				.query(Integer.class)
				.single();
		org.assertj.core.api.Assertions.assertThat(projectCount).isZero();
		org.assertj.core.api.Assertions.assertThat(roleCount).isZero();
	}
}
