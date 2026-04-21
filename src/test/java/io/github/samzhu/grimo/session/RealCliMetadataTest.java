package io.github.samzhu.grimo.session;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
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
 * E2E: RecordingAgentSession -> TurnRecorded -> TurnRecorder -> H2.
 * Uses metadata that mimics real Claude CLI (S018 redesign).
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
        jdbc.execute("UPDATE grimo_session SET current_event_id = NULL");
        jdbc.execute("DELETE FROM grimo_session_event");
        jdbc.execute("DELETE FROM grimo_session");
    }

    @Test
    @DisplayName("[S018] E2E: real Claude CLI metadata detected, projection has sessionType")
    void realCliMetadata_providerDetectedAndTokensExtracted() {
        // Given
        var extractors = List.<ProviderMetadataExtractor>of(new ClaudeMetadataExtractor());
        var turnRecorder = new TurnRecorder(
                new JdbcSessionEventAdapter(jdbc),
                new JdbcSessionProjectionAdapter(jdbc),
                new ObjectMapper());

        ApplicationEventPublisher syncPublisher = event -> {
            if (event instanceof TurnRecorded tr) {
                turnRecorder.on(tr);
            }
        };

        var stubSession = new StubAgentSession(buildRealCliResponse("HELLO"));
        var registry = new RecordingAgentSessionRegistry(
                new StubRegistry(stubSession), syncPublisher, extractors);

        // When
        AgentSession session = registry.create(Path.of("/tmp/test"));
        session.prompt("reply with one word: HELLO");

        // Then — ASSISTANT event has provider and metadata
        var events = jdbc.queryForList(
                "SELECT * FROM grimo_session_event WHERE message_type = 'ASSISTANT'");
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().get("provider")).isEqualTo("claude");
        var metaJson = (String) events.getFirst().get("metadata");
        assertThat(metaJson).contains("\"durationMs\"");
        assertThat(metaJson).contains("\"tokensIn\"");
        assertThat(metaJson).contains("\"tokensOut\"");

        // And — projection has sessionType=GRIMO, tokens > 0
        var proj = jdbc.queryForMap("SELECT * FROM grimo_session WHERE id = ?", SESSION_ID);
        assertThat(proj.get("session_type")).isEqualTo("GRIMO");
        assertThat(proj.get("project_id")).isNull();
        assertThat(((Number) proj.get("total_tokens_in")).longValue()).isEqualTo(3L);
        assertThat(((Number) proj.get("total_tokens_out")).longValue()).isEqualTo(5L);
    }

    private AgentResponse buildRealCliResponse(String text) {
        var providerFields = Map.<String, Object>of(
                "inputTokens", 3,
                "outputTokens", 5,
                "phaseCapture", "stub-phase-capture-object");

        var metadata = new AgentResponseMetadata(
                "", Duration.ofMillis(3471), SESSION_ID, providerFields);

        var genMeta = new AgentGenerationMetadata("SUCCESS", Map.of());
        var generation = new AgentGeneration(text, genMeta);
        return AgentResponse.builder()
                .metadata(metadata)
                .results(List.of(generation))
                .build();
    }

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
