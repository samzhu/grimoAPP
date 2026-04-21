package io.github.samzhu.grimo.session;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentSession;
import org.springaicommunity.agents.model.AgentSessionRegistry;
import org.springaicommunity.agents.model.AgentSessionStatus;
import org.springframework.context.ApplicationEventPublisher;

import io.github.samzhu.grimo.session.application.port.out.ProviderMetadataExtractor;
import io.github.samzhu.grimo.session.internal.RecordingAgentSession;
import io.github.samzhu.grimo.session.internal.RecordingAgentSessionRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests RecordingAgentSessionRegistry wrapping behavior (S018 update).
 */
class RecordingRegistryTest {

    private static final String SESSION_ID = "stub-session-id-42";
    private static final Path WORK_DIR = Path.of("/tmp/grimo-test");

    private RecordingAgentSessionRegistry registry;

    @BeforeEach
    void setUp() {
        ApplicationEventPublisher publisher = event -> {};

        AgentSessionRegistry stubRegistry = new AgentSessionRegistry() {
            private final AgentSession stubSession = new StubAgentSession();

            @Override
            public AgentSession create(Path workingDirectory) { return stubSession; }
            @Override
            public Optional<AgentSession> find(String sessionId) {
                if (SESSION_ID.equals(sessionId)) return Optional.of(stubSession);
                return Optional.empty();
            }
            @Override public void evict(String sessionId) {}
            @Override public void evictStale(Duration inactiveSince) {}
        };

        registry = new RecordingAgentSessionRegistry(stubRegistry, publisher, List.of());
    }

    @Test
    @DisplayName("[S018] AC-12: create() returns RecordingAgentSession with matching sessionId")
    void createReturnsRecordingWrapper() {
        // When
        AgentSession session = registry.create(WORK_DIR);

        // Then
        assertThat(session).isInstanceOf(RecordingAgentSession.class);
        assertThat(session.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(session.getWorkingDirectory()).isEqualTo(WORK_DIR);
    }

    @Test
    @DisplayName("[S018] AC-12: createRecordedSession() carries sessionType and projectId")
    void createRecordedSessionCarriesMetadata() {
        // When
        AgentSession session = registry.createRecordedSession(
                WORK_DIR, "PROJECT", "proj-abc");

        // Then
        assertThat(session).isInstanceOf(RecordingAgentSession.class);
        assertThat(session.getSessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    @DisplayName("[S018] AC-12: find() returns RecordingAgentSession wrapping found session")
    void findReturnsRecordingWrapper() {
        // When
        Optional<AgentSession> found = registry.find(SESSION_ID);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(RecordingAgentSession.class);
        assertThat(found.get().getSessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    @DisplayName("[S018] AC-12: find() for unknown sessionId returns empty")
    void findUnknownReturnsEmpty() {
        // When
        Optional<AgentSession> found = registry.find("unknown-id");

        // Then
        assertThat(found).isEmpty();
    }

    private static class StubAgentSession implements AgentSession {
        @Override public String getSessionId() { return SESSION_ID; }
        @Override public Path getWorkingDirectory() { return WORK_DIR; }
        @Override public AgentSessionStatus getStatus() { return AgentSessionStatus.ACTIVE; }
        @Override public AgentResponse prompt(String message) { return null; }
        @Override public AgentSession resume() { return this; }
        @Override public AgentSession fork() { throw new UnsupportedOperationException(); }
        @Override public void close() {}
    }
}
