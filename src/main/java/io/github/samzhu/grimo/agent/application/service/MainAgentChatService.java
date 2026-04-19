package io.github.samzhu.grimo.agent.application.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.claude.ClaudeSessionConnector;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentSession;
import org.springaicommunity.agents.model.AgentSessionRegistry;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.agent.application.port.in.MainAgentChatUseCase;
import io.github.samzhu.grimo.agent.domain.ChatSessionException;

@Service
class MainAgentChatService implements MainAgentChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(MainAgentChatService.class);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    private final AgentSessionRegistry sessionRegistry;

    MainAgentChatService(AgentSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void startChat(Path workingDirectory) {
        AgentSession session;
        try {
            session = sessionRegistry.create(workingDirectory);
        } catch (IllegalStateException e) {
            throw new ChatSessionException(
                    "Claude CLI not found. Install: npm install -g @anthropic-ai/claude-code", e);
        }

        log.info("Chat session started (sessionId={})", session.getSessionId());
        runRepl(session);
    }

    @Override
    public void resumeChat(Path workingDirectory) {
        AgentSession session;
        try {
            session = ClaudeSessionConnector.continueLastSession(
                    workingDirectory, SESSION_TIMEOUT, null, null);
            log.info("Chat session resumed (sessionId={})", session.getSessionId());
        } catch (Exception e) {
            log.debug("Resume failed, falling back to new session", e);
            System.out.println("No previous session found, starting new session");
            try {
                session = sessionRegistry.create(workingDirectory);
                log.info("Chat session started (sessionId={})", session.getSessionId());
            } catch (IllegalStateException ex) {
                throw new ChatSessionException(
                        "Claude CLI not found. Install: npm install -g @anthropic-ai/claude-code", ex);
            }
        }
        runRepl(session);
    }

    private void runRepl(AgentSession session) {
        try (session) {
            var reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while (true) {
                System.out.print("you> ");
                System.out.flush();
                line = reader.readLine();
                if (line == null) break;                          // Ctrl+D (EOF)
                if ("/exit".equals(line.trim())
                        || "/quit".equals(line.trim())) break;
                if (line.isBlank()) continue;

                try {
                    AgentResponse response = session.prompt(line);
                    System.out.println(response.getText());
                } catch (Exception e) {
                    log.error("Session error", e);
                    System.err.println("Session terminated: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
