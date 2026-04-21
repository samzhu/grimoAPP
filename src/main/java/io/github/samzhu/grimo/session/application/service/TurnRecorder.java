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
 * and updates the session projection. S023: builds parent-child chain
 * via {@code parentEventId} and updates {@code currentEventId}.
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

        // 1. Read current leaf pointer from projection (null for first turn)
        String currentLeaf = projectionStore.findById(event.sessionId())
                .map(SessionProjection::currentEventId)
                .orElse(null);

        // 2. USER event — parent = session's current leaf
        String userEventId = UUID.randomUUID().toString();

        // 3. ASSISTANT event — parent = USER event just created
        String assistantEventId = UUID.randomUUID().toString();

        var metaMap = new LinkedHashMap<String, Object>();
        metaMap.put("durationMs", event.durationMs());
        if (event.finishReason() != null) metaMap.put("finishReason", event.finishReason());
        if (event.tokensIn() > 0) metaMap.put("tokensIn", event.tokensIn());
        if (event.tokensOut() > 0) metaMap.put("tokensOut", event.tokensOut());

        // 4. Upsert projection FIRST with OLD current_event_id (FK: events reference session)
        updateProjection(event, currentLeaf, now);

        // 5. Insert USER event
        eventStore.append(new SessionEvent(
                userEventId, event.sessionId(), currentLeaf,
                MessageType.USER,
                event.userMessage(), null,
                null, null, null,
                false, now));

        // 6. Insert ASSISTANT event
        eventStore.append(new SessionEvent(
                assistantEventId, event.sessionId(), userEventId,
                MessageType.ASSISTANT,
                event.assistantMessage(), null,
                event.provider(), event.model(), toJson(metaMap),
                false, now));

        // 7. NOW update current_event_id to new leaf (events exist, FK satisfied)
        projectionStore.updateCurrentEventId(event.sessionId(), assistantEventId);
    }

    private void updateProjection(TurnRecorded event, String newCurrentEventId,
                                  Instant now) {
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
                    newCurrentEventId,
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
                    newCurrentEventId,
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
