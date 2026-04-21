package io.github.samzhu.grimo.session.application.port.in;

import java.util.List;
import java.util.Optional;

import io.github.samzhu.grimo.session.domain.EventFilter;
import io.github.samzhu.grimo.session.domain.SessionEvent;
import io.github.samzhu.grimo.session.domain.SessionProjection;

/**
 * Inbound port for querying session history (S018 expanded).
 */
public interface SessionHistoryUseCase {

    List<SessionEvent> getEvents(String sessionId);

    List<SessionEvent> getEvents(String sessionId, EventFilter filter);

    Optional<SessionProjection> findById(String sessionId);

    List<SessionProjection> listAll();

    List<SessionProjection> findByProjectId(String projectId);

    List<SessionProjection> findBySessionType(String sessionType);

    /**
     * Returns the conversation path for a session's current branch.
     * Reads {@code current_event_id} from the projection, then walks
     * the tree to root via recursive CTE.
     */
    List<SessionEvent> getConversationPath(String sessionId);
}
