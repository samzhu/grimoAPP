package io.github.samzhu.grimo.session.domain;

/**
 * Discriminator for session event records, aligned with Spring AI
 * {@code MessageType}. Replaces S017's {@code EventType}.
 */
public enum MessageType {
    USER, ASSISTANT, SYSTEM, TOOL
}
