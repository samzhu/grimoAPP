package io.github.samzhu.grimo.project;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S001 API acceptance tests for Project onboarding.
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

	@Test
	@DisplayName("AC-S001-1 exposes workflow recipe catalog")
	void exposesWorkflowRecipeCatalog() throws Exception {
		mockMvc.perform(get("/api/workflow-recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem("coding")))
				.andExpect(jsonPath("$[*].name", hasItem("開發工作流")));
	}

	@Test
	@DisplayName("AC-S001-2 creates and lists project")
	void createsAndListsProject() throws Exception {
		String request = """
				{
				  "name": "grimoAPP",
				  "description": "本機 AI 開發工作台",
				  "folderPath": "/Users/samzhu/workspace/github-samzhu/grimoAPP",
				  "workflowRecipeId": "coding"
				}
				""";

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.name").value("grimoAPP"))
				.andExpect(jsonPath("$.folderPath").value("/Users/samzhu/workspace/github-samzhu/grimoAPP"))
				.andExpect(jsonPath("$.workflowRecipeId").value("coding"))
				.andExpect(jsonPath("$.workflowRecipeName").value("開發工作流"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name", hasItem("grimoAPP")))
				.andExpect(jsonPath("$[*].folderPath", hasItem("/Users/samzhu/workspace/github-samzhu/grimoAPP")));
	}

	@Test
	@DisplayName("AC-S001-3 rejects duplicate folder path")
	void rejectsDuplicateFolderPath() throws Exception {
		String request = """
				{
				  "name": "duplicate-source",
				  "description": "first",
				  "folderPath": "/tmp/grimo-duplicate",
				  "workflowRecipeId": "coding"
				}
				""";

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("這個資料夾已綁定到既有專案"));
	}

	@Test
	@DisplayName("AC-S001-3 rejects invalid project payload")
	void rejectsInvalidProjectPayload() throws Exception {
		String request = """
				{
				  "name": "",
				  "description": "missing name",
				  "folderPath": "",
				  "workflowRecipeId": "unknown"
				}
				""";

		mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("請填寫專案名稱與對應資料夾"));
	}
}
