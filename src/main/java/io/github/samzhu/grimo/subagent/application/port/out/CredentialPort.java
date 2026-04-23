package io.github.samzhu.grimo.subagent.application.port.out;

import java.util.List;
import java.util.Optional;

import io.github.samzhu.grimo.subagent.domain.Credential;

/**
 * Outbound port for credential persistence (S030).
 */
public interface CredentialPort {

    void save(Credential credential);

    Optional<Credential> findById(String id);

    List<Credential> findAll();

    List<Credential> findByProviderOrderBySortOrder(String provider);

    void deleteById(String id);
}
