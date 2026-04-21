package io.github.samzhu.grimo.agent.application.service;

import java.nio.file.Path;
import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.claude.ClaudeSessionConnector;
import org.springaicommunity.agents.model.AgentSession;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.agent.application.port.in.MainAgentChatUseCase;
import io.github.samzhu.grimo.agent.domain.ChatSessionException;
import io.github.samzhu.grimo.session.application.port.in.SessionRecordingPort;

@Service
class MainAgentChatService implements MainAgentChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(MainAgentChatService.class);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    private final SessionRecordingPort recordingPort;

    MainAgentChatService(SessionRecordingPort recordingPort) {
        this.recordingPort = recordingPort;
    }

    @Override
    public AgentSession createSession(Path workDir, String sessionType,
                                      @Nullable String projectId) {
        try {
            AgentSession session = recordingPort.createRecordedSession(
                    workDir, sessionType, projectId);
            log.info("Chat session created (sessionId={}, type={}, projectId={})",
                    session.getSessionId(), sessionType, projectId);
            return session;
        } catch (IllegalStateException e) {
            throw new ChatSessionException(
                    "Claude CLI not found. Install: npm install -g @anthropic-ai/claude-code", e);
        }
    }

    @Override
    public AgentSession resumeSession(Path workDir) {
        try {
            AgentSession raw = ClaudeSessionConnector.continueLastSession(
                    workDir, SESSION_TIMEOUT, null, null);
            AgentSession session = recordingPort.wrapForRecording(raw);
            log.info("Chat session resumed (sessionId={})", session.getSessionId());
            return session;
        } catch (Exception e) {
            log.debug("Resume failed, falling back to new session", e);
            return createSession(workDir, "GRIMO", null);
        }
    }
}
