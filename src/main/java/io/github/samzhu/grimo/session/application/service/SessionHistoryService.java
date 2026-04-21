package io.github.samzhu.grimo.session.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.session.application.port.in.SessionHistoryUseCase;
import io.github.samzhu.grimo.session.application.port.out.SessionEventPort;
import io.github.samzhu.grimo.session.application.port.out.SessionProjectionPort;
import io.github.samzhu.grimo.session.domain.EventFilter;
import io.github.samzhu.grimo.session.domain.SessionEvent;
import io.github.samzhu.grimo.session.domain.SessionProjection;

@Service
class SessionHistoryService implements SessionHistoryUseCase {

    private final SessionEventPort eventStore;
    private final SessionProjectionPort projectionStore;

    SessionHistoryService(SessionEventPort eventStore, SessionProjectionPort projectionStore) {
        this.eventStore = eventStore;
        this.projectionStore = projectionStore;
    }

    @Override
    public List<SessionEvent> getEvents(String sessionId) {
        return eventStore.findBySessionId(sessionId);
    }

    @Override
    public List<SessionEvent> getEvents(String sessionId, EventFilter filter) {
        return eventStore.findBySessionId(sessionId, filter);
    }

    @Override
    public Optional<SessionProjection> findById(String sessionId) {
        return projectionStore.findById(sessionId);
    }
}
