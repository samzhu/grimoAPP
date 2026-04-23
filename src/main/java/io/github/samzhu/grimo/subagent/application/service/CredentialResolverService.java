package io.github.samzhu.grimo.subagent.application.service;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.subagent.application.port.in.CredentialResolverUseCase;
import io.github.samzhu.grimo.subagent.application.port.out.CredentialPort;
import io.github.samzhu.grimo.subagent.application.port.out.SettingPort;
import io.github.samzhu.grimo.subagent.domain.Credential;
import io.github.samzhu.grimo.subagent.domain.CredentialStrategy;

/**
 * Resolves the best credential for a given provider based on the
 * configured strategy (PRIORITY/RANDOM) and expiry (S030).
 */
@Service
class CredentialResolverService implements CredentialResolverUseCase {

    private final CredentialPort credentialPort;
    private final SettingPort settingPort;

    CredentialResolverService(CredentialPort credentialPort, SettingPort settingPort) {
        this.credentialPort = credentialPort;
        this.settingPort = settingPort;
    }

    @Override
    public Optional<Credential> resolve(String provider) {
        var strategyStr = settingPort.get("credential-strategy").orElse("PRIORITY");
        var strategy = CredentialStrategy.valueOf(strategyStr);

        var candidates = credentialPort.findByProviderOrderBySortOrder(provider)
                .stream()
                .filter(c -> !c.isExpired())
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return switch (strategy) {
            case PRIORITY -> Optional.of(candidates.getFirst());
            case RANDOM -> Optional.of(
                    candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
        };
    }
}
