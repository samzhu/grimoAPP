package io.github.samzhu.grimo.session;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the H2 schema for S018 tables.
 * Pure JUnit — no Spring context required.
 */
class SchemaTest {

    private static Connection connection;

    @BeforeAll
    static void initSchema() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:schema_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    @DisplayName("[S018] AC-12: grimo_project table exists with expected columns")
    void projectTableExists() throws Exception {
        // Given — schema.sql has been executed
        DatabaseMetaData meta = connection.getMetaData();

        // When / Then — grimo_project has id, name, work_dir
        try (ResultSet rs = meta.getColumns(null, null, "grimo_project", "id")) {
            assertThat(rs.next()).as("grimo_project.id exists").isTrue();
        }
        try (ResultSet rs = meta.getColumns(null, null, "grimo_project", "name")) {
            assertThat(rs.next()).as("grimo_project.name exists").isTrue();
        }
        try (ResultSet rs = meta.getColumns(null, null, "grimo_project", "work_dir")) {
            assertThat(rs.next()).as("grimo_project.work_dir exists").isTrue();
        }
    }

    @Test
    @DisplayName("[S018] AC-12: grimo_task table exists with FK to grimo_project")
    void taskTableExistsWithFk() throws Exception {
        // Given — schema.sql has been executed
        DatabaseMetaData meta = connection.getMetaData();

        // When / Then — grimo_task exists
        try (ResultSet rs = meta.getColumns(null, null, "grimo_task", "id")) {
            assertThat(rs.next()).as("grimo_task.id exists").isTrue();
        }

        // And — FK project_id → grimo_project.id
        try (ResultSet rs = meta.getImportedKeys(null, null, "grimo_task")) {
            boolean found = false;
            while (rs.next()) {
                if ("project_id".equalsIgnoreCase(rs.getString("FKCOLUMN_NAME"))
                        && "grimo_project".equalsIgnoreCase(rs.getString("PKTABLE_NAME"))) {
                    found = true;
                    break;
                }
            }
            assertThat(found).as("FK grimo_task.project_id → grimo_project(id)").isTrue();
        }
    }

    @Test
    @DisplayName("[S018] AC-12: grimo_session has session_type and project_id columns")
    void sessionTableHasNewColumns() throws Exception {
        // Given — schema.sql has been executed
        DatabaseMetaData meta = connection.getMetaData();

        // When / Then — session_type exists and is NOT NULL
        try (ResultSet rs = meta.getColumns(null, null, "grimo_session", "session_type")) {
            assertThat(rs.next()).as("session_type column exists").isTrue();
            assertThat(rs.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNoNulls);
        }

        // And — project_id exists and IS nullable
        try (ResultSet rs = meta.getColumns(null, null, "grimo_session", "project_id")) {
            assertThat(rs.next()).as("project_id column exists").isTrue();
            assertThat(rs.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
        }
    }

    @Test
    @DisplayName("[S018] AC-12: grimo_session_event uses Spring AI naming (message_type, message_content)")
    void sessionEventUsesSpringAiNaming() throws Exception {
        // Given — schema.sql has been executed
        DatabaseMetaData meta = connection.getMetaData();

        // When / Then — message_type exists (not event_type)
        try (ResultSet rs = meta.getColumns(null, null, "grimo_session_event", "message_type")) {
            assertThat(rs.next()).as("message_type column exists").isTrue();
        }
        // And — message_content exists (not payload_json)
        try (ResultSet rs = meta.getColumns(null, null, "grimo_session_event", "message_content")) {
            assertThat(rs.next()).as("message_content column exists").isTrue();
        }
        // And — provider per-event
        try (ResultSet rs = meta.getColumns(null, null, "grimo_session_event", "provider")) {
            assertThat(rs.next()).as("provider column exists").isTrue();
        }
        // And — model per-event
        try (ResultSet rs = meta.getColumns(null, null, "grimo_session_event", "model")) {
            assertThat(rs.next()).as("model column exists").isTrue();
        }
    }
}
