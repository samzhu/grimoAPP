package io.github.samzhu.grimo.task.adapter.out;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.samzhu.grimo.task.application.port.out.TaskPort;
import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskPriority;
import io.github.samzhu.grimo.task.domain.TaskSource;
import io.github.samzhu.grimo.task.domain.TaskStatus;

@Repository
class JdbcTaskAdapter implements TaskPort {

    private final JdbcTemplate jdbc;

    JdbcTaskAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Task t) {
        jdbc.update("""
                MERGE INTO grimo_task (id, task_number, project_id, title, body,
                    status, priority, labels_json, source_type, source_ref,
                    created_at, updated_at, closed_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                t.id(), t.taskNumber(), t.projectId(), t.title(), t.body(),
                t.status().name(), t.priority().name(), t.labelsJson(),
                t.source() != null ? t.source().type() : null,
                t.source() != null ? t.source().ref() : null,
                Timestamp.from(t.createdAt()), Timestamp.from(t.updatedAt()),
                t.closedAt() != null ? Timestamp.from(t.closedAt()) : null);
    }

    @Override
    public Optional<Task> findById(String id) {
        var results = jdbc.query("SELECT * FROM grimo_task WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Optional<Task> findByNumber(int taskNumber) {
        var results = jdbc.query("SELECT * FROM grimo_task WHERE task_number = ?",
                this::mapRow, taskNumber);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<Task> findAll() {
        return jdbc.query("SELECT * FROM grimo_task ORDER BY task_number ASC", this::mapRow);
    }

    @Override
    public List<Task> findByProjectId(@Nullable String projectId) {
        return jdbc.query("SELECT * FROM grimo_task WHERE project_id = ? ORDER BY task_number ASC",
                this::mapRow, projectId);
    }

    @Override
    public List<Task> findByStatus(TaskStatus status) {
        return jdbc.query("SELECT * FROM grimo_task WHERE status = ? ORDER BY task_number ASC",
                this::mapRow, status.name());
    }

    @Override
    public List<Task> findOrphan() {
        return jdbc.query(
                "SELECT * FROM grimo_task WHERE project_id IS NULL ORDER BY task_number ASC",
                this::mapRow);
    }

    @Override
    public int nextTaskNumber() {
        return jdbc.queryForObject("SELECT NEXT VALUE FOR grimo_task_number_seq", Integer.class);
    }

    @Override
    public void deleteByNumber(int taskNumber) {
        jdbc.update("DELETE FROM grimo_task WHERE task_number = ?", taskNumber);
    }

    private Task mapRow(ResultSet rs, int rowNum) throws SQLException {
        var sourceType = rs.getString("source_type");
        var sourceRef = rs.getString("source_ref");
        TaskSource source = sourceType != null ? new TaskSource(sourceType, sourceRef) : null;

        var closedAt = rs.getTimestamp("closed_at");
        return new Task(
                rs.getString("id"),
                rs.getInt("task_number"),
                rs.getString("project_id"),
                rs.getString("title"),
                rs.getString("body"),
                TaskStatus.valueOf(rs.getString("status")),
                TaskPriority.valueOf(rs.getString("priority")),
                rs.getString("labels_json"),
                source,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                closedAt != null ? closedAt.toInstant() : null);
    }
}
