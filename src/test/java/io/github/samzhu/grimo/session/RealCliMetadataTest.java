package io.github.samzhu.grimo.session;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentGenerationMetadata;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentResponseMetadata;
import org.springaicommunity.agents.model.AgentSession;
import org.springaicommunity.agents.model.AgentSessionStatus;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import io.github.samzhu.grimo.session.adapter.out.JdbcSessionEventAdapter;
import io.github.samzhu.grimo.session.adapter.out.JdbcSessionProjectionAdapter;
import io.github.samzhu.grimo.session.application.port.out.ProviderMetadataExtractor;
import io.github.samzhu.grimo.session.application.service.TurnRecorder;
import io.github.samzhu.grimo.session.events.TurnRecorded;
import io.github.samzhu.grimo.session.internal.ClaudeMetadataExtractor;
import io.github.samzhu.grimo.session.internal.RecordingAgentSession;
import io.github.samzhu.grimo.session.internal.RecordingAgentSessionRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test: RecordingAgentSession → TurnRecorded → TurnRecorder → H2.
 * Uses metadata that mimics real Claude CLI (empty model, phaseCapture key).
 */
class RealCliMetadataTest {

    private static final String SESSION_ID = "sess-realcli-test";
    private static Connection connection;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void initDb() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:realcli_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void cleanTables() {
        jdbc.execute("DELETE FROM grimo_session_event");
        jdbc.execute("DELETE FROM grimo_session");
    }

    @Test
    @DisplayName("[S017] E2E-BUG-2+3: real Claude CLI metadata (empty model, phaseCapture) detected correctly")
    void realCliMetadata_providerDetectedAndTokensExtracted() {
        // Given — stub session returning response that mimics REAL Claude CLI:
        //   getModel() = "" (empty), meta contains "phaseCapture" key,
        //   "inputTokens"=3 (Integer), "outputTokens"=5 (Integer)
        var extractors = List.<ProviderMetadataExtractor>of(new ClaudeMetadataExtractor());
        var turnRecorder = new TurnRecorder(
                new JdbcSessionEventAdapter(jdbc),
                new JdbcSessionProjectionAdapter(jdbc),
                new ObjectMapper());

        // Wire: publish event → TurnRecorder.on() synchronously
        ApplicationEventPublisher syncPublisher = event -> {
            if (event instanceof TurnRecorded tr) {
                turnRecorder.on(tr);
            }
        };

        var stubSession = new StubAgentSession(buildRealCliResponse("HELLO"));
        var registry = new RecordingAgentSessionRegistry(
                new StubRegistry(stubSession), syncPublisher, extractors);

        // When — create session and prompt
        AgentSession session = registry.create(Path.of("/tmp/test"));
        session.prompt("reply with one word: HELLO");

        // Then — ASSISTANT event has provider:"claude" and correct tokens
        var metaJson = jdbc.queryForObject(
                "SELECT metadata_json FROM grimo_session_event WHERE event_type = 'ASSISTANT'",
                String.class);
        assertThat(metaJson).contains("\"provider\":\"claude\"");
        assertThat(metaJson).contains("\"tokensIn\"");
        assertThat(metaJson).contains("\"tokensOut\"");

        // And — projection has provider=claude, tokens > 0
        var proj = jdbc.queryForMap("SELECT * FROM grimo_session WHERE id = ?", SESSION_ID);
        assertThat(proj.get("provider")).isEqualTo("claude");
        assertThat(((Number) proj.get("total_tokens_in")).longValue()).isEqualTo(3L);
        assertThat(((Number) proj.get("total_tokens_out")).longValue()).isEqualTo(5L);
    }

    /**
     * Build AgentResponse mimicking REAL Claude CLI output:
     * - getModel() returns "" (empty)
     * - HashMap contains "inputTokens", "outputTokens", "phaseCapture"
     */
    private AgentResponse buildRealCliResponse(String text) {
        // Real CLI puts tokens + phaseCapture directly in the HashMap
        var providerFields = Map.<String, Object>of(
                "inputTokens", 3,           // Integer, not Long!
                "outputTokens", 5,          // Integer, not Long!
                "phaseCapture", "stub-phase-capture-object");

        // Real CLI: getModel() returns "" (empty string)
        var metadata = new AgentResponseMetadata(
                "",                          // ← empty model!
                Duration.ofMillis(3471),
                SESSION_ID,
                providerFields);

        var genMeta = new AgentGenerationMetadata("SUCCESS", Map.of());
        var generation = new AgentGeneration(text, genMeta);
        return AgentResponse.builder()
                .metadata(metadata)
                .results(List.of(generation))
                .build();
    }

    // --- stubs ---

    private static class StubAgentSession implements AgentSession {
        private final AgentResponse response;
        StubAgentSession(AgentResponse response) { this.response = response; }
        @Override public String getSessionId() { return SESSION_ID; }
        @Override public Path getWorkingDirectory() { return Path.of("/tmp/test"); }
        @Override public AgentSessionStatus getStatus() { return AgentSessionStatus.ACTIVE; }
        @Override public AgentResponse prompt(String message) { return response; }
        @Override public AgentSession resume() { return this; }
        @Override public AgentSession fork() { throw new UnsupportedOperationException(); }
        @Override public void close() {}
    }

    private record StubRegistry(AgentSession session) implements org.springaicommunity.agents.model.AgentSessionRegistry {
        @Override public AgentSession create(Path p) { return session; }
        @Override public java.util.Optional<AgentSession> find(String id) { return java.util.Optional.empty(); }
        @Override public void evict(String id) {}
        @Override public void evictStale(Duration d) {}
    }
}
