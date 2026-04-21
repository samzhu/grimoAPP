package io.github.samzhu.grimo.session.domain;

/**
 * Discriminator for session event records.
 *
 * <p>{@code USER} and {@code ASSISTANT} are real conversation events.
 * {@code SUMMARY} is a synthetic event produced by compaction (S014).
 */
public enum EventType {
    USER, ASSISTANT, SUMMARY
}
