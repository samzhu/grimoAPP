package io.github.samzhu.grimo.project.adapter.out;

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

import io.github.samzhu.grimo.project.domain.Project;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests JdbcProjectAdapter with real H2 + JDBC.
 * Pure JUnit — no Spring context.
 */
class JdbcProjectAdapterTest {

    private static Connection connection;
    private static JdbcTemplate jdbc;
    private JdbcProjectAdapter adapter;

    @BeforeAll
    static void initDb() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:project_adapter_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void setUp() {
        // Given — clean table
        jdbc.execute("DELETE FROM grimo_session_event");
        jdbc.execute("DELETE FROM grimo_session");
        jdbc.execute("DELETE FROM grimo_task");
        jdbc.execute("DELETE FROM grimo_project");
        adapter = new JdbcProjectAdapter(jdbc);
    }

    @Test
    @DisplayName("[S018] AC-1: save and findById")
    void saveAndFindById() {
        // Given
        var now = Instant.now();
        var project = new Project("abc123def456", "grimoAPP", "/tmp/test", "desc", now, now);

        // When
        adapter.save(project);
        var found = adapter.findById("abc123def456");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("grimoAPP");
        assertThat(found.get().workDir()).isEqualTo("/tmp/test");
        assertThat(found.get().description()).isEqualTo("desc");
    }

    @Test
    @DisplayName("[S018] AC-1: findAll returns all projects")
    void findAll() {
        // Given
        var now = Instant.now();
        adapter.save(new Project("aaa111bbb222", "proj1", "/tmp/p1", null, now, now));
        adapter.save(new Project("ccc333ddd444", "proj2", "/tmp/p2", null, now, now));

        // When
        var all = adapter.findAll();

        // Then
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("[S018] AC-2: findByName returns project")
    void findByName() {
        // Given
        var now = Instant.now();
        adapter.save(new Project("abc123def456", "grimoAPP", "/tmp/test", null, now, now));

        // When
        var found = adapter.findByName("grimoAPP");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("abc123def456");
    }

    @Test
    @DisplayName("[S018] AC-1: deleteById removes project")
    void deleteById() {
        // Given
        var now = Instant.now();
        adapter.save(new Project("abc123def456", "grimoAPP", "/tmp/test", null, now, now));

        // When
        adapter.deleteById("abc123def456");

        // Then
        assertThat(adapter.findById("abc123def456")).isEmpty();
    }

    @Test
    @DisplayName("[S018] AC-1: save updates existing project (MERGE INTO)")
    void saveUpdatesExisting() {
        // Given
        var now = Instant.now();
        adapter.save(new Project("abc123def456", "grimoAPP", "/tmp/test", null, now, now));

        // When
        var later = Instant.now();
        adapter.save(new Project("abc123def456", "grimoAPP", "/tmp/test", "updated", now, later));

        // Then
        var found = adapter.findById("abc123def456");
        assertThat(found).isPresent();
        assertThat(found.get().description()).isEqualTo("updated");
    }
}
