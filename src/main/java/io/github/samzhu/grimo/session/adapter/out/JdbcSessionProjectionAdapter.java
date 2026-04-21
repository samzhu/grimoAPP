package io.github.samzhu.grimo.session.adapter.out;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.samzhu.grimo.session.application.port.out.SessionProjectionPort;
import io.github.samzhu.grimo.session.domain.SessionProjection;
import io.github.samzhu.grimo.session.domain.SessionStatus;

@Repository
public class JdbcSessionProjectionAdapter implements SessionProjectionPort {

    private final JdbcTemplate jdbc;

    public JdbcSessionProjectionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(SessionProjection p) {
        jdbc.update("""
                MERGE INTO grimo_session (id, session_type, project_id, status,
                    turn_count, total_tokens_in, total_tokens_out, total_duration_ms,
                    event_version, current_event_id, work_dir, created_at, last_active_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                p.id(), p.sessionType(), p.projectId(), p.status().name(),
                p.turnCount(), p.totalTokensIn(), p.totalTokensOut(), p.totalDurationMs(),
                p.eventVersion(), p.currentEventId(), p.workDir(),
                Timestamp.from(p.createdAt()), Timestamp.from(p.lastActiveAt()));
    }

    @Override
    public Optional<SessionProjection> findById(String sessionId) {
        List<SessionProjection> results = jdbc.query(
                "SELECT * FROM grimo_session WHERE id = ?",
                this::mapRow, sessionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<SessionProjection> findAll() {
        return jdbc.query(
                "SELECT * FROM grimo_session ORDER BY last_active_at DESC",
                this::mapRow);
    }

    @Override
    public List<SessionProjection> findByProjectId(String projectId) {
        return jdbc.query(
                "SELECT * FROM grimo_session WHERE project_id = ? ORDER BY last_active_at DESC",
                this::mapRow, projectId);
    }

    @Override
    public List<SessionProjection> findBySessionType(String sessionType) {
        return jdbc.query(
                "SELECT * FROM grimo_session WHERE session_type = ? ORDER BY last_active_at DESC",
                this::mapRow, sessionType);
    }

    @Override
    public void updateCurrentEventId(String sessionId, String currentEventId) {
        jdbc.update("UPDATE grimo_session SET current_event_id = ? WHERE id = ?",
                currentEventId, sessionId);
    }

    private SessionProjection mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SessionProjection(
                rs.getString("id"),
                rs.getString("session_type"),
                rs.getString("project_id"),
                SessionStatus.valueOf(rs.getString("status")),
                rs.getInt("turn_count"),
                rs.getLong("total_tokens_in"),
                rs.getLong("total_tokens_out"),
                rs.getLong("total_duration_ms"),
                rs.getLong("event_version"),
                rs.getString("current_event_id"),
                rs.getString("work_dir"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_active_at").toInstant());
    }
}
