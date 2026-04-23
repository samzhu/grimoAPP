package io.github.samzhu.grimo.subagent.adapter.out;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.samzhu.grimo.subagent.application.port.out.TaskExecutionPort;
import io.github.samzhu.grimo.subagent.domain.ExecutionStatus;
import io.github.samzhu.grimo.subagent.domain.TaskExecution;

@Repository
class JdbcTaskExecutionAdapter implements TaskExecutionPort {

    private final JdbcTemplate jdbc;

    JdbcTaskExecutionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(TaskExecution e) {
        jdbc.update("""
                MERGE INTO grimo_task_execution (id, task_id, task_number,
                    execution_status, prompt, branch, worktree_path, container_id,
                    agent_response, diff_summary, error_message,
                    started_at, finished_at, created_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.id(), e.taskId(), e.taskNumber(),
                e.status().name(), e.prompt(), e.branch(), e.worktreePath(), e.containerId(),
                e.agentResponse(), e.diff(), e.errorMessage(),
                e.startedAt() != null ? Timestamp.from(e.startedAt()) : null,
                e.finishedAt() != null ? Timestamp.from(e.finishedAt()) : null,
                Timestamp.from(e.createdAt()));
    }

    @Override
    public Optional<TaskExecution> findById(String id) {
        var results = jdbc.query(
                "SELECT * FROM grimo_task_execution WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<TaskExecution> findByTaskId(String taskId) {
        return jdbc.query(
                "SELECT * FROM grimo_task_execution WHERE task_id = ? ORDER BY created_at DESC",
                this::mapRow, taskId);
    }

    private TaskExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
        var startedAt = rs.getTimestamp("started_at");
        var finishedAt = rs.getTimestamp("finished_at");
        return new TaskExecution(
                rs.getString("id"),
                rs.getString("task_id"),
                rs.getInt("task_number"),
                ExecutionStatus.valueOf(rs.getString("execution_status")),
                rs.getString("prompt"),
                rs.getString("branch"),
                rs.getString("worktree_path"),
                rs.getString("container_id"),
                rs.getString("agent_response"),
                rs.getString("diff_summary"),
                rs.getString("error_message"),
                startedAt != null ? startedAt.toInstant() : null,
                finishedAt != null ? finishedAt.toInstant() : null,
                rs.getTimestamp("created_at").toInstant());
    }
}
