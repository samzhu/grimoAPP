package io.github.samzhu.grimo.session.application.port.out;

import java.util.Optional;

import io.github.samzhu.grimo.session.domain.SessionProjection;

/**
 * Outbound port for the session projection table.
 */
public interface SessionProjectionPort {

    void upsert(SessionProjection projection);

    Optional<SessionProjection> findById(String sessionId);
}
