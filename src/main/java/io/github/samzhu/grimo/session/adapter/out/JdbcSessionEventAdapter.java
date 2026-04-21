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
                (id, session_id, parent_event_id, message_type, message_content,
                 message_data, provider, model, metadata, synthetic, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.id(), e.sessionId(), e.parentEventId(),
                e.messageType().name(),
                e.messageContent(), e.messageData(),
                e.provider(), e.model(), e.metadata(),
                e.synthetic(), Timestamp.from(e.createdAt()));
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
        return findBySessionId(sessionId);
    }

    @Override
    public List<SessionEvent> findConversationPath(String leafEventId) {
        // H2 WITH RECURSIVE: walk from leaf to root via parent_event_id.
        // Anchor columns must be CAST explicitly (H2 defaults all to VARCHAR).
        return jdbc.query("""
                WITH RECURSIVE conversation(
                    id, session_id, parent_event_id, message_type,
                    message_content, message_data, provider, model,
                    metadata, synthetic, created_at, depth
                ) AS (
                    SELECT CAST(id AS VARCHAR(36)),
                           CAST(session_id AS VARCHAR(36)),
                           CAST(parent_event_id AS VARCHAR(36)),
                           CAST(message_type AS VARCHAR(20)),
                           CAST(message_content AS CLOB),
                           CAST(message_data AS CLOB),
                           CAST(provider AS VARCHAR(20)),
                           CAST(model AS VARCHAR(100)),
                           CAST(metadata AS CLOB),
                           CAST(synthetic AS BOOLEAN),
                           CAST(created_at AS TIMESTAMP),
                           CAST(0 AS INT)
                    FROM grimo_session_event
                    WHERE id = ?
                    UNION ALL
                    SELECT e.id, e.session_id, e.parent_event_id, e.message_type,
                           e.message_content, e.message_data, e.provider, e.model,
                           e.metadata, e.synthetic, e.created_at, c.depth + 1
                    FROM grimo_session_event e
                    JOIN conversation c ON e.id = c.parent_event_id
                )
                SELECT * FROM conversation ORDER BY depth DESC""",
                this::mapRow, leafEventId);
    }

    private SessionEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SessionEvent(
                rs.getString("id"),
                rs.getString("session_id"),
                rs.getString("parent_event_id"),
                MessageType.valueOf(rs.getString("message_type")),
                rs.getString("message_content"),
                rs.getString("message_data"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("metadata"),
                rs.getBoolean("synthetic"),
                rs.getTimestamp("created_at").toInstant());
    }
}
