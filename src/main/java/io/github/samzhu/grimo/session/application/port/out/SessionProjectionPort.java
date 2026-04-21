package io.github.samzhu.grimo.session.application.port.out;

import java.util.List;
import java.util.Optional;

import io.github.samzhu.grimo.session.domain.SessionProjection;

/**
 * Outbound port for the session projection table (S018 expanded).
 */
public interface SessionProjectionPort {

    void upsert(SessionProjection projection);

    Optional<SessionProjection> findById(String sessionId);

    List<SessionProjection> findAll();

    List<SessionProjection> findByProjectId(String projectId);

    List<SessionProjection> findBySessionType(String sessionType);

    void updateCurrentEventId(String sessionId, String currentEventId);
}
