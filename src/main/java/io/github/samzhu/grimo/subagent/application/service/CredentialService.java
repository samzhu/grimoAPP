package io.github.samzhu.grimo.subagent.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.core.domain.NanoIds;
import io.github.samzhu.grimo.subagent.application.port.in.CredentialUseCase;
import io.github.samzhu.grimo.subagent.application.port.out.CredentialPort;
import io.github.samzhu.grimo.subagent.domain.Credential;

/**
 * CRUD operations for credential pool (S030).
 */
@Service
class CredentialService implements CredentialUseCase {

    private final CredentialPort credentialPort;

    CredentialService(CredentialPort credentialPort) {
        this.credentialPort = credentialPort;
    }

    @Override
    public Credential create(String label, String provider, String credentialType,
                             String secretValue, @Nullable Instant expiresAt) {
        // Auto-assign sortOrder: max existing + 1
        int nextOrder = credentialPort.findAll().stream()
                .mapToInt(Credential::sortOrder)
                .max()
                .orElse(0) + 1;

        var credential = new Credential(
                NanoIds.compact(), label, provider, credentialType,
                secretValue, nextOrder, expiresAt, Instant.now());
        credentialPort.save(credential);
        return credential;
    }

    @Override
    public List<Credential> listAll() {
        return credentialPort.findAll();
    }

    @Override
    public Optional<Credential> findById(String id) {
        return credentialPort.findById(id);
    }

    @Override
    public void delete(String id) {
        credentialPort.deleteById(id);
    }

    @Override
    public void updateSortOrder(String id, int newSortOrder) {
        credentialPort.findById(id).ifPresent(existing -> {
            var updated = new Credential(
                    existing.id(), existing.label(), existing.provider(),
                    existing.credentialType(), existing.secretValue(),
                    newSortOrder, existing.expiresAt(), existing.createdAt());
            credentialPort.save(updated);
        });
    }
}
