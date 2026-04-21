package io.github.samzhu.grimo.session.adapter.out;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.samzhu.grimo.session.application.port.out.SessionEventPort;
import io.github.samzhu.grimo.session.domain.EventFilter;
import io.github.samzhu.grimo.session.domain.MessageType;
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
                (id, session_id, message_type, message_content, message_data,
                 provider, model, metadata, synthetic, branch, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.id(), e.sessionId(), e.messageType().name(),
                e.messageContent(), e.messageData(),
                e.provider(), e.model(), e.metadata(),
                e.synthetic(), e.branch(), Timestamp.from(e.createdAt()));
    }

    @Override
    public List<SessionEvent> findBySessionId(String sessionId) {
        return jdbc.query("""
                SELECT * FROM grimo_session_event
                WHERE session_id = ? ORDER BY created_at ASC""",
                this::mapRow, sessionId);
    }

    @Override
    public List<SessionEvent> findBySessionId(String sessionId, EventFilter filter) {
        // Basic implementation — full filter support deferred
        return findBySessionId(sessionId);
    }

    private SessionEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SessionEvent(
                rs.getString("id"),
                rs.getString("session_id"),
                MessageType.valueOf(rs.getString("message_type")),
                rs.getString("message_content"),
                rs.getString("message_data"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("metadata"),
                rs.getBoolean("synthetic"),
                rs.getString("branch"),
                rs.getTimestamp("created_at").toInstant());
    }
}
