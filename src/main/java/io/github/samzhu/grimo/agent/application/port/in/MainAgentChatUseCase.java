package io.github.samzhu.grimo.agent.application.port.in;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.agents.model.AgentSession;

/**
 * Creates or resumes main-agent chat sessions (S018 redesign).
 * Returns an {@link AgentSession} for the caller to prompt — no
 * longer drives a blocking REPL internally.
 */
public interface MainAgentChatUseCase {

    /**
     * Create a new chat session.
     *
     * @param workDir     working directory (claude's project root)
     * @param sessionType "GRIMO" or "PROJECT"
     * @param projectId   bound project ID (null for GRIMO)
     * @return a recording-decorated AgentSession ready for prompting
     */
    AgentSession createSession(Path workDir, String sessionType,
                               @Nullable String projectId);

    /**
     * Resume the most recent session in the working directory.
     * Falls back to a new GRIMO session if no previous session exists.
     *
     * @param workDir working directory (claude's project root)
     * @return a recording-decorated AgentSession ready for prompting
     */
    AgentSession resumeSession(Path workDir);
}
