package io.github.samzhu.grimo.session.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.session.application.port.out.SessionEventPort;
import io.github.samzhu.grimo.session.application.port.out.SessionProjectionPort;
import io.github.samzhu.grimo.session.domain.MessageType;
import io.github.samzhu.grimo.session.domain.SessionEvent;
import io.github.samzhu.grimo.session.domain.SessionProjection;
import io.github.samzhu.grimo.session.domain.SessionStatus;
import io.github.samzhu.grimo.session.events.TurnRecorded;

/**
 * Event listener that persists conversation turns to the event store
 * and updates the session projection (S018 redesign).
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

        // 1. Upsert projection FIRST (FK: events reference session)
        updateProjection(event, now);

        // 2. USER event
        eventStore.append(new SessionEvent(
                UUID.randomUUID().toString(), event.sessionId(),
                MessageType.USER,
                event.userMessage(), null,
                null, null, null,
                false, null, now));

        // 3. ASSISTANT event
        var metaMap = new LinkedHashMap<String, Object>();
        metaMap.put("durationMs", event.durationMs());
        if (event.finishReason() != null) metaMap.put("finishReason", event.finishReason());
        if (event.tokensIn() > 0) metaMap.put("tokensIn", event.tokensIn());
        if (event.tokensOut() > 0) metaMap.put("tokensOut", event.tokensOut());

        eventStore.append(new SessionEvent(
                UUID.randomUUID().toString(), event.sessionId(),
                MessageType.ASSISTANT,
                event.assistantMessage(), null,
                event.provider(), event.model(), toJson(metaMap),
                false, null, now));
    }

    private void updateProjection(TurnRecorded event, Instant now) {
        var existing = projectionStore.findById(event.sessionId());

        if (existing.isPresent()) {
            var prev = existing.get();
            projectionStore.upsert(new SessionProjection(
                    event.sessionId(),
                    prev.sessionType(), prev.projectId(),
                    SessionStatus.ACTIVE,
                    prev.turnCount() + 1,
                    prev.totalTokensIn() + event.tokensIn(),
                    prev.totalTokensOut() + event.tokensOut(),
                    prev.totalDurationMs() + event.durationMs(),
                    prev.eventVersion() + 2,
                    prev.workDir(),
                    prev.createdAt(),
                    now));
        } else {
            projectionStore.upsert(new SessionProjection(
                    event.sessionId(),
                    event.sessionType(), event.projectId(),
                    SessionStatus.ACTIVE,
                    1, event.tokensIn(), event.tokensOut(), event.durationMs(),
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
