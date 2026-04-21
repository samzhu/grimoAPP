package io.github.samzhu.grimo.session.application.port.in;

import org.springaicommunity.agents.model.AgentSession;

/**
 * Wraps any {@link AgentSession} in a recording decorator that
 * publishes {@code TurnRecorded} events on each {@code prompt()} call.
 *
 * <p>Used by the {@code agent} module when it obtains a session
 * outside the normal {@code AgentSessionRegistry.create()} path
 * (e.g. {@code ClaudeSessionConnector.continueLastSession()} for
 * {@code --resume}).
 */
public interface SessionRecordingPort {

    /**
     * Wrap the given raw session in a recording decorator.
     * If the session is already recording, returns it unchanged.
     */
    AgentSession wrapForRecording(AgentSession raw);
}
