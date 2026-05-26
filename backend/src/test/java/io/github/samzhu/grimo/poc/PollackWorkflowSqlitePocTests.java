package io.github.samzhu.grimo.poc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import io.github.markpollack.workflow.batch.AgentStepExecution;
import io.github.markpollack.workflow.batch.AgentStepExecutionReadRepository;
import io.github.markpollack.workflow.batch.AgentStepExecutionWriteRepository;
import io.github.markpollack.workflow.batch.BatchStatus;
import io.github.markpollack.workflow.batch.CheckpointingStepRunner;
import io.github.markpollack.workflow.batch.JdbcTraceRecorder;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import io.github.markpollack.workflow.flows.workflow.StepTransition;
import io.github.markpollack.workflow.patterns.graph.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;

/**
 * ADR-001 的 POC：驗證 Pollack Agent Workflow 的 JDBC durability path
 * 可以使用 SQLite，支撐 Grimo local-first MVP。
 *
 * 執行方式：./gradlew test --tests '*PollackWorkflowSqlitePocTests'
 * 通過代表 CheckpointingStepRunner、JdbcTraceRecorder 與 WAL mode 足以讓
 * SQLite 繼續作為 local checkpoint / trace candidate。
 */
@SpringBootTest(classes = PollackWorkflowSqlitePocTests.PocApplication.class)
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:sqlite:${java.io.tmpdir}/grimo-pollack-workflow-poc.sqlite",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false"
})
class PollackWorkflowSqlitePocTests {

	@Autowired
	private AgentStepExecutionReadRepository readRepository;

	@Autowired
	private AgentStepExecutionWriteRepository writeRepository;

	@Autowired
	private DataSource dataSource;

	@Test
	void checkpointingStepRunnerSkipsCompletedStepFromSqliteCheckpoint() {
		var runner = new CheckpointingStepRunner(readRepository, writeRepository);
		var ctx = AgentContext.withRunId("sqlite-checkpoint-poc");
		var invocations = new AtomicInteger();
		Step<String, String> step = Step.named("poc.discuss", (agentContext, input) -> {
			invocations.incrementAndGet();
			return input + " -> discussed";
		});

		String first = runner.execute(step, ctx, "idea");
		String second = runner.execute(step, ctx, "changed input should be ignored");

		assertThat(first).isEqualTo("idea -> discussed");
		assertThat(second).isEqualTo("idea -> discussed");
		assertThat(invocations).hasValue(1);
		assertThat(readRepository.findByRunIdAndStepName("sqlite-checkpoint-poc", "poc.discuss"))
				.get()
				.extracting(AgentStepExecution::getStatus, AgentStepExecution::getOutputPayload)
				.containsExactly(BatchStatus.COMPLETED, "idea -> discussed");
	}

	@Test
	void sqliteCanUseWalModeForLocalCheckpointDatabase() throws Exception {
		try (Connection connection = dataSource.getConnection();
				var statement = connection.createStatement();
				var result = statement.executeQuery("PRAGMA journal_mode=WAL")) {
			assertThat(result.next()).isTrue();
			assertThat(result.getString(1)).isEqualToIgnoringCase("wal");
		}
	}

	@Test
	void jdbcTraceRecorderDefaultDdlCanStoreTraceInSqlite(@TempDir java.nio.file.Path tempDir) {
		var sqlite = new DriverManagerDataSource("jdbc:sqlite:" + tempDir.resolve("trace-default.sqlite"));
		var recorder = new JdbcTraceRecorder(sqlite);
		var runId = "sqlite-trace-default-" + UUID.randomUUID();
		var transition = new StepTransition(
				runId,
				"poc-workflow",
				null,
				"poc.discuss",
				Instant.now(),
				Duration.ofMillis(12),
				0,
				0.0,
				NodeType.DETERMINISTIC,
				"poc");

		recorder.record(transition);

		assertThat(recorder.getTrace(runId))
				.singleElement()
				.satisfies(saved -> {
					assertThat(saved.fromStep()).isNull();
					assertThat(saved.toStep()).isEqualTo("poc.discuss");
					assertThat(saved.tokensUsed()).isZero();
					assertThat(saved.costUsd()).isZero();
					assertThat(saved.label()).isEqualTo("poc");
				});
	}

	@Test
	void sqlitePocDatabaseFileIsCreated(@TempDir java.nio.file.Path tempDir) throws Exception {
		var database = tempDir.resolve("poc.sqlite");
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database)) {
			assertThat(connection.isValid(1)).isTrue();
		}

		assertThat(Files.exists(database)).isTrue();
	}

	@SpringBootApplication
	@EntityScan(basePackageClasses = AgentStepExecution.class)
	@EnableJpaRepositories(basePackageClasses = {
			AgentStepExecutionReadRepository.class,
			AgentStepExecutionWriteRepository.class
	})
	static class PocApplication {
	}

	@Configuration(proxyBeanMethods = false)
	static class PocConfiguration {
	}

}
