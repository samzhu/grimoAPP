package io.github.samzhu.grimo.subagent.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.samzhu.grimo.subagent.domain.Credential;

/**
 * Inbound port for credential CRUD operations (S030).
 */
public interface CredentialUseCase {

    Credential create(String label, String provider, String credentialType,
                      String secretValue, @Nullable Instant expiresAt);

    List<Credential> listAll();

    Optional<Credential> findById(String id);

    void delete(String id);

    void updateSortOrder(String id, int newSortOrder);
}
