package io.github.samzhu.grimo.session;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

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
 * Tests TurnRecorder with real H2 + JDBC adapters.
 * Pure JUnit — no Spring context. All wired manually with embedded H2.
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
    @DisplayName("[S017] AC-1: single turn produces USER + ASSISTANT events")
    void ac1_singleTurnProducesUserAndAssistantEvents() {
        // Given — a TurnRecorded event with Claude metadata
        var event = buildTurnEvent(1, "hello", "Hi there", 100, 50, 2000);

        // When — TurnRecorder processes the event
        turnRecorder.on(event);

        // Then — 2 events in grimo_session_event
        var events = jdbc.queryForList("SELECT * FROM grimo_session_event ORDER BY sequence");
        assertThat(events).hasSize(2);

        // First event: USER
        var userEvent = events.get(0);
        assertThat(userEvent.get("event_type")).isEqualTo("USER");
        assertThat(userEvent.get("turn_number")).isEqualTo(1);
        assertThat((String) userEvent.get("payload_json")).contains("hello");
        assertThat(userEvent.get("metadata_json")).isNull();
        assertThat(userEvent.get("synthetic")).isEqualTo(false);
        assertThat(userEvent.get("session_id")).isEqualTo(SESSION_ID);

        // Second event: ASSISTANT
        var assistantEvent = events.get(1);
        assertThat(assistantEvent.get("event_type")).isEqualTo("ASSISTANT");
        assertThat(assistantEvent.get("turn_number")).isEqualTo(1);
        assertThat((String) assistantEvent.get("payload_json")).contains("Hi there");
        assertThat(assistantEvent.get("synthetic")).isEqualTo(false);
        assertThat(assistantEvent.get("session_id")).isEqualTo(SESSION_ID);

        // ASSISTANT metadata contains model, durationMs, finishReason
        var metaJson = (String) assistantEvent.get("metadata_json");
        assertThat(metaJson).contains("claude-sonnet-4-20250514");
        assertThat(metaJson).contains("durationMs");
        assertThat(metaJson).contains("SUCCESS");

        // event_ids are valid UUIDs and unique
        var id1 = (String) userEvent.get("event_id");
        var id2 = (String) assistantEvent.get("event_id");
        assertThat(id1).matches("[0-9a-f\\-]{36}");
        assertThat(id2).matches("[0-9a-f\\-]{36}");
        assertThat(id1).isNotEqualTo(id2);

        // created_at is a real timestamp
        assertThat(userEvent.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("[S017] AC-2: 3 turns produce 6 events with ascending sequence")
    void ac2_multiTurnAppendOnly() {
        // Given / When — record 3 turns
        for (int turn = 1; turn <= 3; turn++) {
            turnRecorder.on(buildTurnEvent(turn, "msg-" + turn, "response-" + turn, 100, 50, 2000));
        }

        // Then — 6 events total
        var events = jdbc.queryForList("SELECT * FROM grimo_session_event ORDER BY sequence");
        assertThat(events).hasSize(6);

        // turn_numbers: 1,1,2,2,3,3
        var turnNumbers = events.stream()
                .map(e -> (Integer) e.get("turn_number"))
                .toList();
        assertThat(turnNumbers).containsExactly(1, 1, 2, 2, 3, 3);

        // sequence strictly ascending
        var sequences = events.stream()
                .map(e -> ((Number) e.get("sequence")).longValue())
                .toList();
        for (int i = 1; i < sequences.size(); i++) {
            assertThat(sequences.get(i)).isGreaterThan(sequences.get(i - 1));
        }
    }

    @Test
    @DisplayName("[S017] AC-3: session projection auto-materialized after 3 turns")
    void ac3_projectionAutoMaterialized() {
        // Given / When — record 3 turns
        for (int turn = 1; turn <= 3; turn++) {
            turnRecorder.on(buildTurnEvent(turn, "msg-" + turn, "resp-" + turn, 100, 50, 2000));
        }

        // Then — projection row exists with aggregated values
        var projections = jdbc.queryForList("SELECT * FROM grimo_session WHERE id = ?", SESSION_ID);
        assertThat(projections).hasSize(1);

        var proj = projections.get(0);
        assertThat(proj.get("id")).isEqualTo(SESSION_ID);
        assertThat(proj.get("turn_count")).isEqualTo(3);
        assertThat(((Number) proj.get("total_tokens_in")).longValue()).isEqualTo(300L);
        assertThat(((Number) proj.get("total_tokens_out")).longValue()).isEqualTo(150L);
        assertThat(((Number) proj.get("total_duration_ms")).longValue()).isEqualTo(6000L);
        assertThat(proj.get("status")).isEqualTo("ACTIVE");

        // last_active_at >= created_at
        var createdAt = (java.sql.Timestamp) proj.get("created_at");
        var lastActiveAt = (java.sql.Timestamp) proj.get("last_active_at");
        assertThat(lastActiveAt).isAfterOrEqualTo(createdAt);
    }

    @Test
    @DisplayName("[S017] AC-7: provider-agnostic metadata via ClaudeMetadataExtractor")
    void ac7_providerAgnosticMetadata() {
        // Given — event with Claude provider metadata (pre-extracted)
        var event = buildTurnEvent(1, "hello", "Hi", 200, 100, 3000);

        // When — TurnRecorder processes event
        turnRecorder.on(event);

        // Then — ASSISTANT event metadata_json contains provider:"claude"
        var metaJson = jdbc.queryForObject(
                "SELECT metadata_json FROM grimo_session_event WHERE event_type = 'ASSISTANT'",
                String.class);
        assertThat(metaJson).contains("\"provider\":\"claude\"");
        assertThat(metaJson).contains("\"tokensIn\"");
        assertThat(metaJson).contains("\"tokensOut\"");

        // grimo_session.provider = "claude"
        var provider = jdbc.queryForObject(
                "SELECT provider FROM grimo_session WHERE id = ?",
                String.class, SESSION_ID);
        assertThat(provider).isEqualTo("claude");
    }

    private TurnRecorded buildTurnEvent(int turn, String userMsg, String assistantMsg,
                                        long tokensIn, long tokensOut, long durationMs) {
        return new TurnRecorded(
                SESSION_ID, turn, userMsg, assistantMsg,
                "claude-sonnet-4-20250514", durationMs, "SUCCESS",
                "claude", tokensIn, tokensOut);
    }
}
