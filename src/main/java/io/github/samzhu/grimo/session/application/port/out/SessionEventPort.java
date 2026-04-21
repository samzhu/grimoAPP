package io.github.samzhu.grimo.session.application.port.out;

import java.util.List;

import io.github.samzhu.grimo.session.domain.EventFilter;
import io.github.samzhu.grimo.session.domain.SessionEvent;

/**
 * Outbound port for the session event store. Aligns with
 * spring-ai-session {@code SessionRepository.appendEvent / findEvents}.
 */
public interface SessionEventPort {

    void append(SessionEvent event);

    List<SessionEvent> findBySessionId(String sessionId);

    List<SessionEvent> findBySessionId(String sessionId, EventFilter filter);
}
