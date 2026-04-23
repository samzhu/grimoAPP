package io.github.samzhu.grimo.subagent.domain;

/**
 * Credential selection strategy for credential pool (S030).
 */
public enum CredentialStrategy {
    /** Select the unexpired credential with the lowest sort_order. */
    PRIORITY,
    /** Select a random unexpired credential. */
    RANDOM
}
