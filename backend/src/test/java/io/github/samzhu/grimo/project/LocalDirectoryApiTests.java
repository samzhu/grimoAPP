package io.github.samzhu.grimo.project;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * S002 API acceptance tests for the local directory picker.
 *
 * <p>These tests prove the browser can read one local directory level through
 * Spring Boot without reading file contents or relying on browser-only file
 * handles.
 *
 * @see LocalDirectoryController
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite:file:s002-local-directory-api?mode=memory&cache=shared",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class LocalDirectoryApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("AC-S002-4 lists immediate child directories")
	void listsImmediateChildDirectories(@TempDir Path tempDir) throws Exception {
		Path projectDirectory = Files.createDirectory(tempDir.resolve("grimoAPP"));
		Files.writeString(tempDir.resolve("notes.txt"), "not a directory");

		mockMvc.perform(get("/api/local-directories").param("path", tempDir.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.path").value(tempDir.toString()))
				.andExpect(jsonPath("$.directories[*].name", hasItem("grimoAPP")))
				.andExpect(jsonPath("$.directories[*].path", hasItem(projectDirectory.toString())))
				.andExpect(jsonPath("$.directories[*].name", not(hasItem("notes.txt"))));
	}

	@Test
	@DisplayName("AC-S002-5 rejects invalid directory path")
	void rejectsInvalidDirectoryPath(@TempDir Path tempDir) throws Exception {
		Path file = Files.writeString(tempDir.resolve("notes.txt"), "not a directory");

		mockMvc.perform(get("/api/local-directories").param("path", file.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("請選擇有效的本機資料夾"));
	}
}
