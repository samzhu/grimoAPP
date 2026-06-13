package io.github.samzhu.grimo.project;

import static org.hamcrest.Matchers.notNullValue;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S013 API acceptance tests for selecting a Project path through a native dialog bridge.
 *
 * <p>The native OS dialog is replaced by a fake gateway so automated tests verify
 * the HTTP contract without opening a real desktop window.
 *
 * @see NativeFolderDialogController
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite:file:s013-native-folder-dialog-api?mode=memory&cache=shared",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class NativeFolderDialogApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private FakeNativeFolderDialogGateway gateway;

	@Test
	@DisplayName("AC-S013-2: returns selected validated absolute projectPath")
	void returnsSelectedValidatedAbsoluteProjectPath(@TempDir Path tempDir) throws Exception {
		Path selectedDirectory = Files.createDirectory(tempDir.resolve("repo-a"));
		gateway.nextSelection = new NativeFolderSelection.Selected(selectedDirectory);

		mockMvc.perform(post("/api/native-folder-dialogs/project-path")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "initialPath": "%s",
								  "title": "選擇 Project 資料夾"
								}
								""".formatted(tempDir)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selected").value(true))
				.andExpect(jsonPath("$.projectPath").value(selectedDirectory.toAbsolutePath().normalize().toString()))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("AC-S013-3: returns selected false when user cancels native dialog")
	void returnsSelectedFalseWhenUserCancelsNativeDialog(@TempDir Path tempDir) throws Exception {
		gateway.nextSelection = new NativeFolderSelection.Cancelled();

		mockMvc.perform(post("/api/native-folder-dialogs/project-path")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "initialPath": "%s",
								  "title": "選擇 Project 資料夾"
								}
								""".formatted(tempDir)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selected").value(false))
				.andExpect(jsonPath("$.projectPath").doesNotExist())
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("AC-S013-4: returns recoverable fallback error when native dialog is unavailable")
	void returnsRecoverableFallbackErrorWhenNativeDialogIsUnavailable() throws Exception {
		gateway.nextSelection = new NativeFolderSelection.Unavailable("無法開啟系統資料夾選擇器，請手動貼上路徑");

		mockMvc.perform(post("/api/native-folder-dialogs/project-path")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("無法開啟系統資料夾選擇器，請手動貼上路徑"));
	}

	@Test
	@DisplayName("AC-S013-4: rejects invalid selected directory without creating Project")
	void rejectsInvalidSelectedDirectoryWithoutCreatingProject(@TempDir Path tempDir) throws Exception {
		int projectCountBefore = countProjects();
		gateway.nextSelection = new NativeFolderSelection.Selected(tempDir.resolve("missing-repo"));

		mockMvc.perform(post("/api/native-folder-dialogs/project-path")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("請選擇有效的本機資料夾"));

		org.assertj.core.api.Assertions.assertThat(countProjects()).isEqualTo(projectCountBefore);
	}

	@Test
	@DisplayName("AC-S013-6: native dialog bridge returns only path selection data")
	void nativeDialogBridgeReturnsOnlyPathSelectionData(@TempDir Path tempDir) throws Exception {
		Path selectedDirectory = Files.createDirectory(tempDir.resolve("repo-b"));
		int projectCountBefore = countProjects();
		gateway.nextSelection = new NativeFolderSelection.Selected(selectedDirectory);

		mockMvc.perform(post("/api/native-folder-dialogs/project-path")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selected").value(true))
				.andExpect(jsonPath("$.projectPath", notNullValue()))
				.andExpect(jsonPath("$.name").doesNotExist())
				.andExpect(jsonPath("$.description").doesNotExist())
				.andExpect(jsonPath("$.workflowRecipeId").doesNotExist())
				.andExpect(jsonPath("$.content").doesNotExist())
				.andExpect(jsonPath("$.error").doesNotExist());

		org.assertj.core.api.Assertions.assertThat(countProjects()).isEqualTo(projectCountBefore);
	}

	private int countProjects() {
		return jdbcClient.sql("SELECT COUNT(*) FROM projects")
				.query(Integer.class)
				.single();
	}

	@TestConfiguration
	static class NativeFolderDialogApiTestConfig {

		@Bean
		@Primary
		FakeNativeFolderDialogGateway fakeNativeFolderDialogGateway() {
			return new FakeNativeFolderDialogGateway();
		}
	}

	static class FakeNativeFolderDialogGateway implements NativeFolderDialogGateway {

		private NativeFolderSelection nextSelection = new NativeFolderSelection.Cancelled();

		@Override
		public NativeFolderSelection chooseDirectory(NativeFolderDialogOptions options) {
			return nextSelection;
		}
	}
}
