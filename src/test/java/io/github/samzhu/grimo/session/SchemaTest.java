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
 * Verifies the H2 schema for S017 event-sourced session tables.
 * Pure JUnit — no Spring context required. Uses the same H2
 * connection parameters as production (MODE=PostgreSQL,
 * DATABASE_TO_LOWER=TRUE).
 */
class SchemaTest {

    private static Connection connection;

    @BeforeAll
    static void initSchema() throws Exception {
        // Match production H2 config from application.yaml
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:schema_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    @DisplayName("[S017] AC-5: grimo_session has parent_id VARCHAR(36) nullable column")
    void ac5_parentIdColumnExists() throws Exception {
        // Given — schema.sql has been executed
        DatabaseMetaData meta = connection.getMetaData();

        // When — query column metadata (lowercase due to DATABASE_TO_LOWER=TRUE)
        try (ResultSet rs = meta.getColumns(null, null, "grimo_session", "parent_id")) {
            // Then — column exists and is nullable VARCHAR(36)
            assertThat(rs.next()).as("parent_id column exists").isTrue();
            assertThat(rs.getString("TYPE_NAME")).satisfiesAnyOf(
                    t -> assertThat(t).containsIgnoringCase("VARCHAR"),
                    t -> assertThat(t).containsIgnoringCase("VARYING"));
            assertThat(rs.getInt("COLUMN_SIZE")).isEqualTo(36);
            assertThat(rs.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
        }
    }

    @Test
    @DisplayName("[S017] AC-5: grimo_session has fork_turn INT nullable column")
    void ac5_forkTurnColumnExists() throws Exception {
        // Given — schema.sql has been executed
        DatabaseMetaData meta = connection.getMetaData();

        // When — query column metadata
        try (ResultSet rs = meta.getColumns(null, null, "grimo_session", "fork_turn")) {
            // Then — column exists and is nullable INT
            assertThat(rs.next()).as("fork_turn column exists").isTrue();
            assertThat(rs.getString("TYPE_NAME")).containsIgnoringCase("INT");
            assertThat(rs.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
        }
    }

    @Test
    @DisplayName("[S017] AC-5: parent_id has FOREIGN KEY referencing grimo_session(id)")
    void ac5_parentIdForeignKey() throws Exception {
        // Given — schema.sql has been executed
        DatabaseMetaData meta = connection.getMetaData();

        // When — query imported keys for grimo_session
        try (ResultSet rs = meta.getImportedKeys(null, null, "grimo_session")) {
            // Then — FK exists: parent_id → grimo_session.id
            boolean found = false;
            while (rs.next()) {
                if ("parent_id".equalsIgnoreCase(rs.getString("FKCOLUMN_NAME"))
                        && "id".equalsIgnoreCase(rs.getString("PKCOLUMN_NAME"))
                        && "grimo_session".equalsIgnoreCase(rs.getString("PKTABLE_NAME"))) {
                    found = true;
                    break;
                }
            }
            assertThat(found).as("FK parent_id → grimo_session(id)").isTrue();
        }
    }
}
