package io.github.samzhu.grimo.agent.application.port.in;

import java.nio.file.Path;

/**
 * Starts a main-agent interactive chat session.
 * Blocks until the user exits (/exit or Ctrl+D).
 */
public interface MainAgentChatUseCase {

    /**
     * @param workingDirectory the user's working directory (claude's project root)
     * @throws io.github.samzhu.grimo.agent.domain.ChatSessionException
     *         if claude CLI is not installed or session fails to start
     */
    void startChat(Path workingDirectory);
}
