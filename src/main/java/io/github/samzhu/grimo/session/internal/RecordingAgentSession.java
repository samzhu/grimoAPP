package io.github.samzhu.grimo.session.internal;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentSession;
import org.springaicommunity.agents.model.AgentSessionStatus;
import org.springframework.context.ApplicationEventPublisher;

import io.github.samzhu.grimo.session.application.port.out.ProviderMetadataExtractor;
import io.github.samzhu.grimo.session.events.TurnRecorded;

/**
 * Decorator around {@link AgentSession} that publishes
 * {@link TurnRecorded} events after each {@code prompt()} call.
 * Carries session metadata (sessionType, projectId) for S018.
 */
public class RecordingAgentSession implements AgentSession {

    private final AgentSession delegate;
    private final ApplicationEventPublisher eventPublisher;
    private final List<ProviderMetadataExtractor> extractors;
    private final String sessionType;
    private final @Nullable String projectId;

    RecordingAgentSession(AgentSession delegate,
                          ApplicationEventPublisher eventPublisher,
                          List<ProviderMetadataExtractor> extractors,
                          String sessionType,
                          @Nullable String projectId) {
        this.delegate = delegate;
        this.eventPublisher = eventPublisher;
        this.extractors = extractors;
        this.sessionType = sessionType;
        this.projectId = projectId;
    }

    @Override
    public AgentResponse prompt(String message) {
        AgentResponse response = delegate.prompt(message);
        eventPublisher.publishEvent(buildEvent(message, response));
        return response;
    }

    private TurnRecorded buildEvent(String userMessage, AgentResponse response) {
        var meta = response.getMetadata();
        var genMeta = response.getResult() != null ? response.getResult().getMetadata() : null;

        String provider = extractors.stream()
                .filter(e -> e.supports(response))
                .findFirst()
                .map(ProviderMetadataExtractor::providerName)
                .orElse("unknown");

        long tokensIn = 0;
        long tokensOut = 0;
        Map<String, Object> tokens = extractors.stream()
                .filter(e -> e.supports(response))
                .findFirst()
                .map(e -> e.extractTokens(response))
                .orElse(Map.of());
        if (tokens.containsKey("tokensIn"))
            tokensIn = ((Number) tokens.get("tokensIn")).longValue();
        if (tokens.containsKey("tokensOut"))
            tokensOut = ((Number) tokens.get("tokensOut")).longValue();

        return new TurnRecorded(
                getSessionId(),
                sessionType,
                projectId,
                userMessage,
                response.getText(),
                provider,
                meta.getModel(),
                meta.getDuration().toMillis(),
                genMeta != null ? genMeta.getFinishReason() : null,
                tokensIn,
                tokensOut);
    }

    @Override
    public AgentSession fork() {
        AgentSession forked = delegate.fork();
        return new RecordingAgentSession(forked, eventPublisher, extractors, sessionType, projectId);
    }

    @Override
    public AgentSession resume() {
        AgentSession resumed = delegate.resume();
        return new RecordingAgentSession(resumed, eventPublisher, extractors, sessionType, projectId);
    }

    @Override
    public String getSessionId() { return delegate.getSessionId(); }

    @Override
    public Path getWorkingDirectory() { return delegate.getWorkingDirectory(); }

    @Override
    public AgentSessionStatus getStatus() { return delegate.getStatus(); }

    @Override
    public void close() { delegate.close(); }
}
