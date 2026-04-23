package io.github.samzhu.grimo.subagent.application.port.in;

import java.util.Optional;

import io.github.samzhu.grimo.subagent.domain.Credential;

/**
 * Inbound port for resolving the best credential for a given provider (S030).
 * Selection respects the configured strategy (PRIORITY/RANDOM) and skips
 * expired credentials.
 */
public interface CredentialResolverUseCase {

    /**
     * Resolve the best credential for the given provider.
     *
     * @param provider e.g. "claude"
     * @return the selected credential, or empty if none available
     */
    Optional<Credential> resolve(String provider);
}
