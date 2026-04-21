package io.github.samzhu.grimo.session.adapter.out;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.samzhu.grimo.session.application.port.out.SessionEventPort;
import io.github.samzhu.grimo.session.domain.EventFilter;
import io.github.samzhu.grimo.session.domain.EventType;
import io.github.samzhu.grimo.session.domain.SessionEvent;

@Repository
public class JdbcSessionEventAdapter implements SessionEventPort {

    private final JdbcTemplate jdbc;

    public JdbcSessionEventAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(SessionEvent e) {
        jdbc.update("""
                INSERT INTO grimo_session_event
                (event_id, session_id, turn_number, event_type,
                 payload_json, metadata_json, synthetic, branch, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.eventId(), e.sessionId(), e.turnNumber(),
                e.eventType().name(), e.payloadJson(), e.metadataJson(),
                e.synthetic(), e.branch(), Timestamp.from(e.createdAt()));
    }

    @Override
    public List<SessionEvent> findBySessionId(String sessionId) {
        return jdbc.query("""
                SELECT * FROM grimo_session_event
                WHERE session_id = ? ORDER BY sequence ASC""",
                this::mapRow, sessionId);
    }

    @Override
    public List<SessionEvent> findBySessionId(String sessionId, EventFilter filter) {
        // Basic implementation — full filter support deferred to when needed
        return findBySessionId(sessionId);
    }

    private SessionEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SessionEvent(
                rs.getLong("sequence"),
                rs.getString("event_id"),
                rs.getString("session_id"),
                rs.getInt("turn_number"),
                EventType.valueOf(rs.getString("event_type")),
                rs.getString("payload_json"),
                rs.getString("metadata_json"),
                rs.getBoolean("synthetic"),
                rs.getString("branch"),
                rs.getTimestamp("created_at").toInstant());
    }
}
