package io.github.samzhu.grimo.session;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import io.github.samzhu.grimo.session.adapter.out.JdbcSessionEventAdapter;
import io.github.samzhu.grimo.session.adapter.out.JdbcSessionProjectionAdapter;
import io.github.samzhu.grimo.session.application.service.TurnRecorder;
import io.github.samzhu.grimo.session.events.TurnRecorded;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests recursive CTE conversation path query and branching scenarios.
 * Pure JUnit — no Spring context.
 */
class ConversationPathTest {

    private static final String SESSION_ID = "sess-cte-test";
    private static Connection connection;
    private static JdbcTemplate jdbc;

    private JdbcSessionEventAdapter eventAdapter;
    private JdbcSessionProjectionAdapter projectionAdapter;
    private TurnRecorder turnRecorder;

    @BeforeAll
    static void initDb() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:cte_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("UPDATE grimo_session SET current_event_id = NULL");
        jdbc.execute("DELETE FROM grimo_session_event");
        jdbc.execute("DELETE FROM grimo_session");

        eventAdapter = new JdbcSessionEventAdapter(jdbc);
        projectionAdapter = new JdbcSessionProjectionAdapter(jdbc);
        turnRecorder = new TurnRecorder(eventAdapter, projectionAdapter, new ObjectMapper());
    }

    @Test
    @DisplayName("[S023] AC-4: recursive CTE returns conversation path from leaf to root, ordered by created_at ASC")
    void conversationPathFromLeafToRoot() {
        // Given — record 3 turns (6 events in linear chain)
        for (int i = 0; i < 3; i++) {
            turnRecorder.on(buildTurn("msg-" + i, "resp-" + i));
        }

        // Get the current leaf (last ASSISTANT event)
        var currentEventId = jdbc.queryForObject(
                "SELECT current_event_id FROM grimo_session WHERE id = ?",
                String.class, SESSION_ID);

        // When — recursive CTE from leaf to root
        var path = eventAdapter.findConversationPath(currentEventId);

        // Then — 6 events, root first (created_at ASC), leaf last
        assertThat(path).hasSize(6);
        assertThat(path.getFirst().parentEventId()).isNull(); // root
        assertThat(path.getFirst().messageType().name()).isEqualTo("USER");
        assertThat(path.getLast().id()).isEqualTo(currentEventId); // leaf
        assertThat(path.getLast().messageType().name()).isEqualTo("ASSISTANT");
    }

    @Test
    @DisplayName("[S023] AC-5: branching — same parent, different children, CTE follows correct branch")
    void branchingScenario() {
        // Given — record 1 turn: evt-1(USER) → evt-2(ASSISTANT)
        turnRecorder.on(buildTurn("hello", "Hi!"));

        var allEvents = jdbc.queryForList(
                "SELECT id, message_type FROM grimo_session_event ORDER BY created_at ASC");
        var userEventId = (String) allEvents.get(0).get("id");      // evt-1 (USER)
        var assistantEventId = (String) allEvents.get(1).get("id");  // evt-2 (ASSISTANT)

        // When — insert a SECOND ASSISTANT as a sibling of evt-2 (regenerate)
        // Both evt-2 and evt-3 have parent = userEventId (evt-1)
        var branchEventId = "branch-evt-3";
        var now = Instant.now();
        jdbc.update("""
                INSERT INTO grimo_session_event
                (id, session_id, parent_event_id, message_type, message_content,
                 synthetic, created_at)
                VALUES (?, ?, ?, 'ASSISTANT', 'Alternative response', false, ?)""",
                branchEventId, SESSION_ID, userEventId, Timestamp.from(now));

        // Then — CTE from evt-3 (the branch) returns [evt-1, evt-3], NOT evt-2
        var branchPath = eventAdapter.findConversationPath(branchEventId);
        assertThat(branchPath).hasSize(2);
        assertThat(branchPath.get(0).id()).isEqualTo(userEventId);
        assertThat(branchPath.get(1).id()).isEqualTo(branchEventId);
        // evt-2 is NOT in this path
        assertThat(branchPath).noneMatch(e -> e.id().equals(assistantEventId));

        // And — CTE from evt-2 (original) returns [evt-1, evt-2]
        var originalPath = eventAdapter.findConversationPath(assistantEventId);
        assertThat(originalPath).hasSize(2);
        assertThat(originalPath.get(0).id()).isEqualTo(userEventId);
        assertThat(originalPath.get(1).id()).isEqualTo(assistantEventId);
    }

    private TurnRecorded buildTurn(String userMsg, String assistantMsg) {
        return new TurnRecorded(
                SESSION_ID, "GRIMO", null,
                userMsg, assistantMsg,
                "claude", "claude-sonnet-4-6",
                2000, "SUCCESS", 100, 50);
    }
}
