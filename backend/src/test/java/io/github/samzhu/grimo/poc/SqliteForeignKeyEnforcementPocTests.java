package io.github.samzhu.grimo.poc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * S004/S009 persistence POC: verifies that Grimo's SQLite JDBC runtime can
 * enforce parent-child ownership with PRAGMA foreign_keys=ON.
 *
 * <p>Run with: ./gradlew test --tests '*SqliteForeignKeyEnforcementPocTests'
 *
 * <p>Passing means Task -> Project and workflow evidence -> Task foreign keys
 * can be enforced at the database level after each connection enables the
 * SQLite foreign key pragma.
 */
class SqliteForeignKeyEnforcementPocTests {

	@TempDir
	private Path tempDir;

	@Test
	@DisplayName("SQLite JDBC rejects orphan child rows when foreign key enforcement is enabled")
	void sqliteJdbcRejectsOrphansWhenForeignKeysAreEnabled() throws Exception {
		var database = tempDir.resolve("foreign-key-poc.sqlite");

		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			connection.createStatement().execute("PRAGMA foreign_keys = ON");

			try (var result = connection.createStatement().executeQuery("PRAGMA foreign_keys")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isEqualTo(1);
			}

			createProjectAndTaskTables(connection);

			assertThatThrownBy(() -> connection.createStatement().executeUpdate("""
					INSERT INTO tasks(id, project_id, title)
					VALUES ('task-orphan', 'missing-project', 'Orphan Task')
					"""))
					.isInstanceOf(SQLException.class)
					.hasMessageContaining("FOREIGN KEY constraint failed");
		}
	}

	@Test
	@DisplayName("SQLite JDBC allows orphan child rows when foreign key enforcement is disabled")
	void sqliteJdbcAllowsOrphansWhenForeignKeysAreDisabled() throws Exception {
		var database = tempDir.resolve("foreign-key-disabled-poc.sqlite");

		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
			connection.createStatement().execute("PRAGMA foreign_keys = OFF");
			createProjectAndTaskTables(connection);

			var inserted = connection.createStatement().executeUpdate("""
					INSERT INTO tasks(id, project_id, title)
					VALUES ('task-orphan', 'missing-project', 'Orphan Task')
					""");

			assertThat(inserted).isEqualTo(1);
		}
	}

	private void createProjectAndTaskTables(java.sql.Connection connection) throws SQLException {
		connection.createStatement().execute("""
				CREATE TABLE projects (
					id TEXT PRIMARY KEY,
					name TEXT NOT NULL
				)
				""");
		connection.createStatement().execute("""
				CREATE TABLE tasks (
					id TEXT PRIMARY KEY,
					project_id TEXT NOT NULL,
					title TEXT NOT NULL,
					FOREIGN KEY (project_id) REFERENCES projects(id)
				)
				""");
	}
}
