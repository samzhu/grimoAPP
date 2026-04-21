package io.github.samzhu.grimo.session.application.port.in;

import java.util.List;
import java.util.Optional;

import io.github.samzhu.grimo.session.domain.EventFilter;
import io.github.samzhu.grimo.session.domain.SessionEvent;
import io.github.samzhu.grimo.session.domain.SessionProjection;

/**
 * Inbound port for querying session history. Method names align with
 * spring-ai-session {@code SessionService}.
 */
public interface SessionHistoryUseCase {

    List<SessionEvent> getEvents(String sessionId);

    List<SessionEvent> getEvents(String sessionId, EventFilter filter);

    Optional<SessionProjection> findById(String sessionId);
}
