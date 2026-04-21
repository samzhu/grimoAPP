package io.github.samzhu.grimo.task.adapter.out;

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

import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskPriority;
import io.github.samzhu.grimo.task.domain.TaskSource;
import io.github.samzhu.grimo.task.domain.TaskStatus;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTaskAdapterTest {

    private static Connection connection;
    private static JdbcTemplate jdbc;
    private JdbcTaskAdapter adapter;

    @BeforeAll
    static void initDb() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:task_adapter_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM grimo_session_event");
        jdbc.execute("DELETE FROM grimo_session");
        jdbc.execute("DELETE FROM grimo_task");
        jdbc.execute("DELETE FROM grimo_project");
        adapter = new JdbcTaskAdapter(jdbc);

        // Insert a project for FK
        jdbc.update("""
                INSERT INTO grimo_project (id, name, work_dir, created_at, updated_at)
                VALUES ('proj1234abcd', 'testProject', '/tmp/test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""");
    }

    @Test
    @DisplayName("[S018] AC-3: save and findByNumber with project")
    void saveAndFindByNumber() {
        // Given
        var now = Instant.now();
        var task = new Task("task12345678", 1, "proj1234abcd", "fix bug", null,
                TaskStatus.OPEN, TaskPriority.HIGH, null, null, now, now, null);

        // When
        adapter.save(task);
        var found = adapter.findByNumber(1);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("fix bug");
        assertThat(found.get().projectId()).isEqualTo("proj1234abcd");
        assertThat(found.get().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(found.get().priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("[S018] AC-4: save orphan task (null projectId)")
    void saveOrphanTask() {
        // Given
        var now = Instant.now();
        var task = new Task("task22222222", 2, null, "research AI", null,
                TaskStatus.OPEN, TaskPriority.MEDIUM, null, null, now, now, null);

        // When
        adapter.save(task);
        var orphans = adapter.findOrphan();

        // Then
        assertThat(orphans).hasSize(1);
        assertThat(orphans.getFirst().projectId()).isNull();
    }

    @Test
    @DisplayName("[S018] AC-5: update status to DONE with closedAt")
    void updateStatusWithClosedAt() {
        // Given
        var now = Instant.now();
        adapter.save(new Task("task33333333", 3, null, "do something", null,
                TaskStatus.OPEN, TaskPriority.MEDIUM, null, null, now, now, null));

        // When — update to DONE
        var later = Instant.now();
        adapter.save(new Task("task33333333", 3, null, "do something", null,
                TaskStatus.DONE, TaskPriority.MEDIUM, null, null, now, later, later));

        // Then
        var found = adapter.findByNumber(3);
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(TaskStatus.DONE);
        assertThat(found.get().closedAt()).isNotNull();
    }

    @Test
    @DisplayName("[S018] AC-3: nextTaskNumber returns incrementing values")
    void nextTaskNumber() {
        // When
        int n1 = adapter.nextTaskNumber();
        int n2 = adapter.nextTaskNumber();

        // Then
        assertThat(n2).isGreaterThan(n1);
    }

    @Test
    @DisplayName("[S018] AC-3: save task with source metadata")
    void saveWithSource() {
        // Given
        var now = Instant.now();
        var source = new TaskSource("CHAT", "sess-abc-123");
        var task = new Task("task44444444", 4, "proj1234abcd", "from chat", null,
                TaskStatus.OPEN, TaskPriority.MEDIUM, "[\"bug\"]", source, now, now, null);

        // When
        adapter.save(task);
        var found = adapter.findByNumber(4);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().source()).isNotNull();
        assertThat(found.get().source().type()).isEqualTo("CHAT");
        assertThat(found.get().source().ref()).isEqualTo("sess-abc-123");
        assertThat(found.get().labelsJson()).isEqualTo("[\"bug\"]");
    }
}
