package io.github.samzhu.grimo.poc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import io.github.markpollack.agentmemory.FileSystemMemoryStore;
import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.hooks.spi.AgentHookProvider;
import io.github.markpollack.journal.event.CustomEvent;
import io.github.markpollack.journal.storage.JsonFileStorage;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.fs.FileExistsJudge;
import io.github.markpollack.sandbox.ExecSpec;
import io.github.markpollack.sandbox.LocalSandbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-001 與 docs/grimo/references/agentworks.md 的 POC：確認 Pollack
 * AgentWorks artifacts 哪些真的擁有 storage surface，哪些只是 API surface。
 *
 * 執行方式：./gradlew test --tests '*PollackAgentWorksStorageSurfacePocTests'
 * 通過代表 SQLite work 應集中在 workflow-batch 與 Grimo evidence tables；
 * Journal / Memory 在 MVP 可先維持 file-backed。
 */
class PollackAgentWorksStorageSurfacePocTests {

	@Test
	void agentWorksArtifactsResolveToBomManagedVersions() throws Exception {
		assertArtifactVersion("io.github.markpollack", "workflow-batch", "0.5.0");
		assertArtifactVersion("io.github.markpollack", "workflow-temporal", "0.5.0");
		assertArtifactVersion("io.github.markpollack", "journal-core", "1.1.0");
		assertArtifactVersion("io.github.markpollack", "agent-hooks-core", "0.6.2");
		assertArtifactVersion("io.github.markpollack", "agent-client-core", "0.18.0");
		assertArtifactVersion("io.github.markpollack", "agent-judge-core", "0.10.0");
		assertArtifactVersion("io.github.markpollack", "agent-sandbox-core", "0.9.2");
		assertArtifactVersion("com.agentclientprotocol", "acp-core", "0.10.0");
	}

	@Test
	void unavailableBomManagedArtifactsUseExplicitReleasedVersions() throws Exception {
		assertArtifactVersion("io.github.markpollack", "memory-core", "0.1.0");
		assertArtifactVersion("io.github.markpollack", "experiment-core", "0.2.0");
	}

	@Test
	void journalCorePersistsEventsToJsonFilesNotSqlite(@TempDir java.nio.file.Path tempDir) {
		var storage = new JsonFileStorage(tempDir.resolve("journal"));

		storage.appendEvent(
				"sqlite-poc",
				"run-1",
				CustomEvent.of("quality-loop-rated", Map.of("quality_score", 9.5)));

		assertThat(storage.loadEvents("sqlite-poc", "run-1"))
				.singleElement()
				.isInstanceOf(CustomEvent.class)
				.satisfies(event -> assertThat(((CustomEvent) event).attributes())
						.containsEntry("quality_score", 9.5));
		assertThat(Files.exists(tempDir.resolve("journal"))).isTrue();
	}

	@Test
	void memoryCorePersistsMemoryToFilesystemNotSqlite(@TempDir java.nio.file.Path tempDir) {
		var memory = new FileSystemMemoryStore(tempDir.resolve("memory"));

		memory.append("Discuss step captured user goal", Map.of("step", "Discuss"));

		assertThat(memory.entries())
				.singleElement()
				.satisfies(entry -> {
					assertThat(entry.content()).contains("Discuss step");
					assertThat(entry.metadata()).containsEntry("step", "Discuss");
				});
		assertThat(memory.retrieve(200)).contains("Discuss step captured user goal");
	}

	@Test
	void judgeAndSandboxRunWithoutDatabase(@TempDir java.nio.file.Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("README.md"), "Grimo");
		var judgment = new FileExistsJudge("README.md")
				.judge(JudgmentContext.builder()
						.goal("README exists")
						.workspace(tempDir)
						.executionTime(Duration.ZERO)
						.build());

		assertThat(judgment.pass()).isTrue();

		try (var sandbox = new LocalSandbox(tempDir)) {
			var result = sandbox.exec(ExecSpec.of("sh", "-c", "printf grimo"));

			assertThat(result.success()).isTrue();
			assertThat(result.stdout()).isEqualTo("grimo");
		}
	}

	@Test
	void agentClientHooksAndAcpCoreExposePlainApiSurfacesWithoutSqliteAdapters() {
		assertThat(AgentClient.class).isInterface();
		assertThat(AgentHookProvider.class).isInterface();
		assertThat(AcpSchema.class).isNotNull();
	}

	private static void assertArtifactVersion(String groupId, String artifactId, String expectedVersion) throws IOException {
		var resource = "META-INF/maven/%s/%s/pom.properties".formatted(groupId, artifactId);
		var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
		assertThat(stream).as(resource).isNotNull();

		var properties = new Properties();
		properties.load(stream);
		assertThat(properties.getProperty("version")).isEqualTo(expectedVersion);
	}

}
