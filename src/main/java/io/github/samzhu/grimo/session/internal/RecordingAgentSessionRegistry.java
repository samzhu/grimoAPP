package io.github.samzhu.grimo.session.internal;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.agents.model.AgentSession;
import org.springaicommunity.agents.model.AgentSessionRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import io.github.samzhu.grimo.session.application.port.in.SessionRecordingPort;
import io.github.samzhu.grimo.session.application.port.out.ProviderMetadataExtractor;

/**
 * {@code @Primary} decorator around any {@link AgentSessionRegistry}.
 * Every session created or found is transparently wrapped in a
 * {@link RecordingAgentSession} that publishes turn events.
 *
 * <p>S018: Added {@link #createRecordedSession} for explicit session
 * type and project binding via {@link SessionRecordingPort}.
 */
@Primary
@Component
public class RecordingAgentSessionRegistry implements AgentSessionRegistry, SessionRecordingPort {

    private final AgentSessionRegistry delegate;
    private final ApplicationEventPublisher eventPublisher;
    private final List<ProviderMetadataExtractor> extractors;

    public RecordingAgentSessionRegistry(AgentSessionRegistry delegate,
                                         ApplicationEventPublisher eventPublisher,
                                         List<ProviderMetadataExtractor> extractors) {
        this.delegate = delegate;
        this.eventPublisher = eventPublisher;
        this.extractors = extractors;
    }

    @Override
    public AgentSession create(Path workingDirectory) {
        AgentSession raw = delegate.create(workingDirectory);
        return new RecordingAgentSession(raw, eventPublisher, extractors, "GRIMO", null);
    }

    @Override
    public Optional<AgentSession> find(String sessionId) {
        return delegate.find(sessionId)
                .map(s -> new RecordingAgentSession(s, eventPublisher, extractors, "GRIMO", null));
    }

    @Override
    public void evict(String sessionId) {
        delegate.evict(sessionId);
    }

    @Override
    public void evictStale(Duration inactiveSince) {
        delegate.evictStale(inactiveSince);
    }

    @Override
    public AgentSession wrapForRecording(AgentSession raw) {
        if (raw instanceof RecordingAgentSession) {
            return raw;
        }
        return new RecordingAgentSession(raw, eventPublisher, extractors, "GRIMO", null);
    }

    @Override
    public AgentSession createRecordedSession(Path workDir, String sessionType,
                                               @Nullable String projectId) {
        AgentSession raw = delegate.create(workDir);
        return new RecordingAgentSession(raw, eventPublisher, extractors, sessionType, projectId);
    }
}
