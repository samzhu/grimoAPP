package io.github.samzhu.grimo.subagent.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Credential for subagent execution — stores OAuth tokens or API keys
 * for injection into Docker containers (S030).
 *
 * @param id             NanoIds.compact()
 * @param label          user-defined label, e.g. "personal-max"
 * @param provider       target CLI, e.g. "claude"
 * @param credentialType "oauth_token" or "api_key"
 * @param secretValue    the actual token/key value
 * @param sortOrder      priority ordering (1 = highest)
 * @param expiresAt      token expiry (setup-token: +1 year), null = never
 * @param createdAt      creation timestamp
 */
public record Credential(
    String id,
    String label,
    String provider,
    String credentialType,
    String secretValue,
    int sortOrder,
    @Nullable Instant expiresAt,
    Instant createdAt
) {

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Mask secret for display: "sk-ant-***...***".
     */
    public String maskedSecret() {
        if (secretValue.length() <= 10) return "***";
        return secretValue.substring(0, 6) + "***..."
                + secretValue.substring(secretValue.length() - 3);
    }
}
