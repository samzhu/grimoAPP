package io.github.samzhu.grimo.subagent.adapter.out;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import io.github.samzhu.grimo.subagent.domain.ExecutionStatus;
import io.github.samzhu.grimo.subagent.domain.TaskExecution;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTaskExecutionAdapterTest {

    private static Connection connection;
    private static JdbcTemplate jdbc;
    private JdbcTaskExecutionAdapter adapter;

    @BeforeAll
    static void initDb() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:execution_adapter_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void setUp() {
        // Clear in FK order
        jdbc.execute("DELETE FROM grimo_task_execution");
        jdbc.execute("UPDATE grimo_session SET current_event_id = NULL");
        jdbc.execute("DELETE FROM grimo_session_event");
        jdbc.execute("DELETE FROM grimo_session");
        jdbc.execute("DELETE FROM grimo_task");
        jdbc.execute("DELETE FROM grimo_project");
        adapter = new JdbcTaskExecutionAdapter(jdbc);

        // Seed: project + task for FK
        jdbc.update("""
                INSERT INTO grimo_project (id, name, work_dir, created_at, updated_at)
                VALUES ('proj1234abcd', 'myapp', '/tmp/myapp', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""");
        jdbc.update("""
                INSERT INTO grimo_task (id, task_number, project_id, title, status, priority, created_at, updated_at)
                VALUES ('task12345678', 42, 'proj1234abcd', 'test task', 'OPEN', 'MEDIUM',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""");
    }

    @Test
    @DisplayName("[S028] AC-1/AC-8: save PENDING execution and findById")
    void ac1_savePendingAndFindById() {
        // Given
        var now = Instant.now();
        var execution = new TaskExecution("exec12345678", "task12345678", 42,
                ExecutionStatus.PENDING, "Add hello.txt", null, null, null,
                null, null, null, null, null, now);

        // When
        adapter.save(execution);
        var found = adapter.findById("exec12345678");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("exec12345678");
        assertThat(found.get().taskId()).isEqualTo("task12345678");
        assertThat(found.get().taskNumber()).isEqualTo(42);
        assertThat(found.get().status()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(found.get().prompt()).isEqualTo("Add hello.txt");
        assertThat(found.get().branch()).isNull();
        assertThat(found.get().agentResponse()).isNull();
    }

    @Test
    @DisplayName("[S028] AC-2/AC-3: update to RUNNING then SUCCEEDED with response and diff")
    void ac2_ac3_transitionToSucceeded() {
        // Given
        var now = Instant.now();
        var pending = new TaskExecution("exec22222222", "task12345678", 42,
                ExecutionStatus.PENDING, "Refactor code", null, null, null,
                null, null, null, null, null, now);
        adapter.save(pending);

        // When — transition to RUNNING
        var running = pending.withRunning("container-abc", "grimo/task-task12345678",
                "/home/user/.grimo/worktrees/task12345678");
        adapter.save(running);

        // Then — verify RUNNING state
        var foundRunning = adapter.findById("exec22222222");
        assertThat(foundRunning).isPresent();
        assertThat(foundRunning.get().status()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(foundRunning.get().containerId()).isEqualTo("container-abc");
        assertThat(foundRunning.get().branch()).isEqualTo("grimo/task-task12345678");
        assertThat(foundRunning.get().startedAt()).isNotNull();

        // When — transition to SUCCEEDED
        var succeeded = running.withSucceeded("{\"result\":\"ok\"}", "diff --git a/hello.txt ...");
        adapter.save(succeeded);

        // Then — verify SUCCEEDED state
        var foundSucceeded = adapter.findById("exec22222222");
        assertThat(foundSucceeded).isPresent();
        assertThat(foundSucceeded.get().status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(foundSucceeded.get().agentResponse()).contains("result");
        assertThat(foundSucceeded.get().diff()).contains("hello.txt");
        assertThat(foundSucceeded.get().finishedAt()).isNotNull();
    }

    @Test
    @DisplayName("[S028] AC-4: update to FAILED with error message")
    void ac4_transitionToFailed() {
        // Given
        var now = Instant.now();
        var pending = new TaskExecution("exec33333333", "task12345678", 42,
                ExecutionStatus.PENDING, "Bad prompt", null, null, null,
                null, null, null, null, null, now);
        adapter.save(pending);
        var running = pending.withRunning("container-xyz", "grimo/task-task12345678",
                "/home/user/.grimo/worktrees/task12345678");
        adapter.save(running);

        // When
        var failed = running.withFailed("Container image not found: grimo-runtime:0.0.1-SNAPSHOT");
        adapter.save(failed);

        // Then
        var found = adapter.findById("exec33333333");
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(found.get().errorMessage()).contains("Container image not found");
        assertThat(found.get().finishedAt()).isNotNull();
        assertThat(found.get().agentResponse()).isNull();
    }

    @Test
    @DisplayName("[S028] AC-8: findByTaskId returns all executions ordered by created_at DESC")
    void ac8_findByTaskId() {
        // Given
        var now = Instant.now();
        adapter.save(new TaskExecution("execAAAAAAAA", "task12345678", 42,
                ExecutionStatus.FAILED, "first attempt", null, null, null,
                null, null, "error", null, now, now));
        adapter.save(new TaskExecution("execBBBBBBBB", "task12345678", 42,
                ExecutionStatus.SUCCEEDED, "second attempt", "grimo/task-x", "/w", "c",
                "{}", "diff", null, now, now, now.plusSeconds(1)));

        // When
        var results = adapter.findByTaskId("task12345678");

        // Then
        assertThat(results).hasSize(2);
        // Most recent first (created_at DESC)
        assertThat(results.get(0).id()).isEqualTo("execBBBBBBBB");
        assertThat(results.get(1).id()).isEqualTo("execAAAAAAAA");
    }

    @Test
    @DisplayName("[S028] findById returns empty for nonexistent ID")
    void findByIdReturnsEmptyForMissing() {
        // When
        var found = adapter.findById("nonexistent12");

        // Then
        assertThat(found).isEmpty();
    }
}
