package io.github.samzhu.grimo.session.application.port.in;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.agents.model.AgentSession;

/**
 * Wraps any {@link AgentSession} in a recording decorator that
 * publishes {@code TurnRecorded} events on each {@code prompt()} call.
 *
 * <p>S018: Added {@link #createRecordedSession} for creating sessions
 * with explicit session type and project binding.
 */
public interface SessionRecordingPort {

    /**
     * Wrap the given raw session in a recording decorator with default
     * metadata (sessionType=GRIMO, projectId=null).
     * If the session is already recording, returns it unchanged.
     */
    AgentSession wrapForRecording(AgentSession raw);

    /**
     * Create a new recorded session with explicit session type and
     * project binding. Bypasses auto-wrapping to set metadata correctly.
     *
     * @param workDir     working directory for the session
     * @param sessionType "GRIMO" or "PROJECT"
     * @param projectId   bound project ID (null for GRIMO)
     * @return a recording-decorated AgentSession
     */
    AgentSession createRecordedSession(Path workDir, String sessionType,
                                       @Nullable String projectId);
}
