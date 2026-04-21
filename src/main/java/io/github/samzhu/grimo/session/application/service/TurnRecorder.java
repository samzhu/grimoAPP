package io.github.samzhu.grimo.session.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.session.application.port.out.SessionEventPort;
import io.github.samzhu.grimo.session.application.port.out.SessionProjectionPort;
import io.github.samzhu.grimo.session.domain.EventType;
import io.github.samzhu.grimo.session.domain.SessionEvent;
import io.github.samzhu.grimo.session.domain.SessionProjection;
import io.github.samzhu.grimo.session.domain.SessionStatus;
import io.github.samzhu.grimo.session.events.TurnRecorded;

/**
 * Event listener that persists conversation turns to the event store
 * and updates the session projection. Each turn produces two events
 * (USER + ASSISTANT) and an incremental projection update.
 */
@Service
public class TurnRecorder {

    private final SessionEventPort eventStore;
    private final SessionProjectionPort projectionStore;
    private final ObjectMapper objectMapper;

    public TurnRecorder(SessionEventPort eventStore,
                        SessionProjectionPort projectionStore,
                        ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.projectionStore = projectionStore;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void on(TurnRecorded event) {
        var now = Instant.now();

        // 1. USER event
        eventStore.append(new SessionEvent(
                null, UUID.randomUUID().toString(), event.sessionId(),
                event.turnNumber(), EventType.USER,
                toJson(Map.of("text", event.userMessage())),
                null, false, null, now));

        // 2. ASSISTANT event
        var metaMap = new LinkedHashMap<String, Object>();
        metaMap.put("model", event.model());
        metaMap.put("durationMs", event.durationMs());
        metaMap.put("finishReason", event.finishReason());
        metaMap.put("provider", event.provider());
        if (event.tokensIn() > 0) metaMap.put("tokensIn", event.tokensIn());
        if (event.tokensOut() > 0) metaMap.put("tokensOut", event.tokensOut());

        eventStore.append(new SessionEvent(
                null, UUID.randomUUID().toString(), event.sessionId(),
                event.turnNumber(), EventType.ASSISTANT,
                toJson(Map.of("text", event.assistantMessage())),
                toJson(metaMap),
                false, null, now));

        // 3. Update projection
        updateProjection(event.sessionId(), event.provider(),
                event.tokensIn(), event.tokensOut(), event.durationMs(), now);
    }

    private void updateProjection(String sessionId, String provider,
                                  long tokensIn, long tokensOut, long durationMs, Instant now) {
        var existing = projectionStore.findById(sessionId);

        if (existing.isPresent()) {
            var prev = existing.get();
            projectionStore.upsert(new SessionProjection(
                    sessionId, prev.parentId(), prev.forkTurn(), provider,
                    SessionStatus.ACTIVE,
                    prev.turnCount() + 1,
                    prev.totalTokensIn() + tokensIn,
                    prev.totalTokensOut() + tokensOut,
                    prev.totalDurationMs() + durationMs,
                    prev.eventVersion() + 2,
                    prev.workDir(),
                    prev.createdAt(),
                    now));
        } else {
            projectionStore.upsert(new SessionProjection(
                    sessionId, null, null, provider,
                    SessionStatus.ACTIVE,
                    1, tokensIn, tokensOut, durationMs,
                    2,
                    null,
                    now, now));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }
}
