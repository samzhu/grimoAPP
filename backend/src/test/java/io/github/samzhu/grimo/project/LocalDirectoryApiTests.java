package io.github.samzhu.grimo.project;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
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
	@DisplayName("AC-S014-5: creates one child directory and returns its absolute path")
	void createsOneChildDirectoryAndReturnsItsAbsolutePath(@TempDir Path tempDir) throws Exception {
		Path child = tempDir.resolve("new-tooling");

		mockMvc.perform(post("/api/local-directories")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parentPath": "%s",
								  "name": "new-tooling"
								}
								""".formatted(tempDir)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("new-tooling"))
				.andExpect(jsonPath("$.path").value(child.toString()));

		org.assertj.core.api.Assertions.assertThat(Files.isDirectory(child)).isTrue();
	}

	@Test
	@DisplayName("AC-S014-5: rejects blank folder name")
	void rejectsBlankFolderName(@TempDir Path tempDir) throws Exception {
		mockMvc.perform(post("/api/local-directories")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parentPath": "%s",
								  "name": "   "
								}
								""".formatted(tempDir)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("請輸入資料夾名稱"));

		try (var children = Files.list(tempDir)) {
			org.assertj.core.api.Assertions.assertThat(children).isEmpty();
		}
	}

	@Test
	@DisplayName("AC-S014-5: rejects path-like folder name")
	void rejectsPathLikeFolderName(@TempDir Path tempDir) throws Exception {
		mockMvc.perform(post("/api/local-directories")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parentPath": "%s",
								  "name": "apps/grimoAPP"
								}
								""".formatted(tempDir)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("資料夾名稱只能是一層資料夾名稱"));

		mockMvc.perform(post("/api/local-directories")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parentPath": "%s",
								  "name": ".."
								}
								""".formatted(tempDir)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("資料夾名稱只能是一層資料夾名稱"));
	}

	@Test
	@DisplayName("AC-S014-5: rejects duplicate folder name without overwrite")
	void rejectsDuplicateFolderNameWithoutOverwrite(@TempDir Path tempDir) throws Exception {
		Path existing = Files.createDirectory(tempDir.resolve("grimoAPP"));

		mockMvc.perform(post("/api/local-directories")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parentPath": "%s",
								  "name": "grimoAPP"
								}
								""".formatted(tempDir)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("資料夾已存在"));

		org.assertj.core.api.Assertions.assertThat(Files.isDirectory(existing)).isTrue();
	}

	@Test
	@DisplayName("AC-S014-5: rejects invalid parent path")
	void rejectsInvalidParentPath(@TempDir Path tempDir) throws Exception {
		Path file = Files.writeString(tempDir.resolve("notes.txt"), "not a directory");

		mockMvc.perform(post("/api/local-directories")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parentPath": "%s",
								  "name": "grimoAPP"
								}
								""".formatted(file)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("無法建立資料夾"));
	}

	@Test
	@DisplayName("AC-S014-2: creates and lists default Grimo project root")
	void createsAndListsDefaultGrimoProjectRoot(@TempDir Path tempHome) throws Exception {
		String originalUserHome = System.getProperty("user.home");
		System.setProperty("user.home", tempHome.toString());
		try {
			Path defaultRoot = tempHome.resolve(".grimo").resolve("projects");
			Path projectDirectory = Files.createDirectories(defaultRoot.resolve("grimoAPP"));
			Files.createDirectory(defaultRoot.resolve("AlphaTool"));
			Files.writeString(defaultRoot.resolve("README.md"), "not a directory");

			mockMvc.perform(get("/api/local-directories"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.path").value(defaultRoot.toString()))
					.andExpect(jsonPath("$.parentPath").value(tempHome.resolve(".grimo").toString()))
					.andExpect(jsonPath("$.directories[*].name", hasItem("grimoAPP")))
					.andExpect(jsonPath("$.directories[*].path", hasItem(projectDirectory.toString())))
					.andExpect(jsonPath("$.directories[*].name", hasItem("AlphaTool")))
					.andExpect(jsonPath("$.directories[*].name", not(hasItem("README.md"))));
		}
		finally {
			System.setProperty("user.home", originalUserHome);
		}
	}

	@Test
	@DisplayName("AC-S014-3: lists home and default shortcut locations")
	void listsHomeAndDefaultShortcutLocations(@TempDir Path tempHome) throws Exception {
		String originalUserHome = System.getProperty("user.home");
		System.setProperty("user.home", tempHome.toString());
		try {
			Path defaultRoot = tempHome.resolve(".grimo").resolve("projects");

			mockMvc.perform(get("/api/local-directories").param("location", "home"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.path").value(tempHome.toString()));

			mockMvc.perform(get("/api/local-directories").param("location", "default"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.path").value(defaultRoot.toString()));
		}
		finally {
			System.setProperty("user.home", originalUserHome);
		}
	}

	@Test
	@DisplayName("AC-S014-3: rejects ambiguous path and location request")
	void rejectsAmbiguousPathAndLocationRequest(@TempDir Path tempHome) throws Exception {
		mockMvc.perform(get("/api/local-directories")
						.param("path", tempHome.toString())
						.param("location", "home"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("請選擇一種資料夾位置"));
	}

	@Test
	@DisplayName("AC-S002-4 lists immediate child directories")
	void listsImmediateChildDirectories(@TempDir Path tempDir) throws Exception {
		Path projectDirectory = Files.createDirectory(tempDir.resolve("grimoAPP"));
		Files.createDirectory(tempDir.resolve("AlphaTool"));
		Files.writeString(tempDir.resolve("notes.txt"), "not a directory");

		mockMvc.perform(get("/api/local-directories").param("path", tempDir.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.path").value(tempDir.toString()))
				.andExpect(jsonPath("$.directories[*].name", hasItem("grimoAPP")))
				.andExpect(jsonPath("$.directories[*].path", hasItem(projectDirectory.toString())))
				.andExpect(jsonPath("$.directories[*].name", hasItem("AlphaTool")))
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
