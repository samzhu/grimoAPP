package io.github.samzhu.grimo.session;

import java.sql.Connection;
import java.sql.DriverManager;

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
 * Tests TurnRecorder with real H2 + JDBC adapters (S018 redesign).
 * Pure JUnit — no Spring context.
 */
class TurnRecorderTest {

    private static final String SESSION_ID = "sess-test-abc-1234";
    private static Connection connection;
    private static JdbcTemplate jdbc;

    private TurnRecorder turnRecorder;

    @BeforeAll
    static void initDb() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:turn_recorder_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        var ds = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(ds);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void setUp() {
        // Given — clean tables between tests
        jdbc.execute("DELETE FROM grimo_session_event");
        jdbc.execute("DELETE FROM grimo_session");

        var eventAdapter = new JdbcSessionEventAdapter(jdbc);
        var projectionAdapter = new JdbcSessionProjectionAdapter(jdbc);
        var objectMapper = new ObjectMapper();

        turnRecorder = new TurnRecorder(eventAdapter, projectionAdapter, objectMapper);
    }

    @Test
    @DisplayName("[S018] AC-12: single turn produces USER + ASSISTANT events with Spring AI naming")
    void singleTurnProducesUserAndAssistantEvents() {
        // Given — a TurnRecorded event
        var event = buildTurnEvent("hello", "Hi there", 100, 50, 2000);

        // When
        turnRecorder.on(event);

        // Then — 2 events in grimo_session_event
        var events = jdbc.queryForList("SELECT * FROM grimo_session_event");
        assertThat(events).hasSize(2);

        // Find USER and ASSISTANT events by message_type
        var userEvent = events.stream()
                .filter(e -> "USER".equals(e.get("message_type"))).findFirst().orElseThrow();
        var assistantEvent = events.stream()
                .filter(e -> "ASSISTANT".equals(e.get("message_type"))).findFirst().orElseThrow();

        // USER event
        assertThat((String) userEvent.get("message_content")).isEqualTo("hello");
        assertThat(userEvent.get("metadata")).isNull();
        assertThat(userEvent.get("provider")).isNull();
        assertThat(userEvent.get("synthetic")).isEqualTo(false);
        assertThat(userEvent.get("session_id")).isEqualTo(SESSION_ID);

        // ASSISTANT event
        assertThat((String) assistantEvent.get("message_content")).isEqualTo("Hi there");
        assertThat(assistantEvent.get("provider")).isEqualTo("claude");
        assertThat(assistantEvent.get("model")).isEqualTo("claude-sonnet-4-6");

        // event IDs are valid UUIDs and unique
        var id1 = (String) userEvent.get("id");
        var id2 = (String) assistantEvent.get("id");
        assertThat(id1).matches("[0-9a-f\\-]{36}");
        assertThat(id2).matches("[0-9a-f\\-]{36}");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("[S018] AC-12: 3 turns produce 6 events ordered by created_at")
    void multiTurnAppendOnly() {
        // Given / When — record 3 turns
        for (int i = 0; i < 3; i++) {
            turnRecorder.on(buildTurnEvent("msg-" + i, "response-" + i, 100, 50, 2000));
        }

        // Then — 6 events total
        var events = jdbc.queryForList("SELECT * FROM grimo_session_event ORDER BY created_at ASC");
        assertThat(events).hasSize(6);
    }

    @Test
    @DisplayName("[S018] AC-12: session projection with sessionType and projectId")
    void projectionWithSessionTypeAndProjectId() {
        // Given / When — record 3 turns
        for (int i = 0; i < 3; i++) {
            turnRecorder.on(buildTurnEvent("msg-" + i, "resp-" + i, 100, 50, 2000));
        }

        // Then — projection row exists with S018 fields
        var projections = jdbc.queryForList("SELECT * FROM grimo_session WHERE id = ?", SESSION_ID);
        assertThat(projections).hasSize(1);

        var proj = projections.get(0);
        assertThat(proj.get("id")).isEqualTo(SESSION_ID);
        assertThat(proj.get("session_type")).isEqualTo("GRIMO");
        assertThat(proj.get("project_id")).isNull();
        assertThat(proj.get("turn_count")).isEqualTo(3);
        assertThat(((Number) proj.get("total_tokens_in")).longValue()).isEqualTo(300L);
        assertThat(((Number) proj.get("total_tokens_out")).longValue()).isEqualTo(150L);
        assertThat(((Number) proj.get("total_duration_ms")).longValue()).isEqualTo(6000L);
        assertThat(proj.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[S018] AC-12: ASSISTANT metadata contains durationMs and token counts")
    void assistantMetadataContainsExpectedFields() {
        // Given
        var event = buildTurnEvent("hello", "Hi", 200, 100, 3000);

        // When
        turnRecorder.on(event);

        // Then
        var metaJson = jdbc.queryForObject(
                "SELECT metadata FROM grimo_session_event WHERE message_type = 'ASSISTANT'",
                String.class);
        assertThat(metaJson).contains("\"durationMs\"");
        assertThat(metaJson).contains("\"tokensIn\"");
        assertThat(metaJson).contains("\"tokensOut\"");
    }

    private TurnRecorded buildTurnEvent(String userMsg, String assistantMsg,
                                        long tokensIn, long tokensOut, long durationMs) {
        return new TurnRecorded(
                SESSION_ID, "GRIMO", null,
                userMsg, assistantMsg,
                "claude", "claude-sonnet-4-6",
                durationMs, "SUCCESS",
                tokensIn, tokensOut);
    }
}
