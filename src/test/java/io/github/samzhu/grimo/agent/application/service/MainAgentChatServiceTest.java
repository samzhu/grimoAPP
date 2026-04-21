package io.github.samzhu.grimo.agent.application.service;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springaicommunity.agents.claude.ClaudeSessionConnector;
import org.springaicommunity.agents.model.AgentSession;

import io.github.samzhu.grimo.agent.domain.ChatSessionException;
import io.github.samzhu.grimo.session.application.port.in.SessionRecordingPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainAgentChatServiceTest {

    private final SessionRecordingPort recordingPort = mock(SessionRecordingPort.class);
    private final MainAgentChatService service = new MainAgentChatService(recordingPort);

    @Test
    @DisplayName("[S018] AC-12: createSession returns AgentSession with correct metadata")
    void createSessionReturnsAgentSession() {
        // Given
        var session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("new-session-1");
        when(recordingPort.createRecordedSession(any(Path.class), eq("GRIMO"), isNull()))
                .thenReturn(session);

        // When
        AgentSession result = service.createSession(Path.of("/tmp"), "GRIMO", null);

        // Then
        assertThat(result.getSessionId()).isEqualTo("new-session-1");
        verify(recordingPort).createRecordedSession(Path.of("/tmp"), "GRIMO", null);
    }

    @Test
    @DisplayName("[S018] AC-12: createSession with PROJECT type passes projectId")
    void createSessionProjectType() {
        // Given
        var session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("project-session-1");
        when(recordingPort.createRecordedSession(any(Path.class), eq("PROJECT"), eq("proj-abc")))
                .thenReturn(session);

        // When
        AgentSession result = service.createSession(Path.of("/tmp"), "PROJECT", "proj-abc");

        // Then
        assertThat(result.getSessionId()).isEqualTo("project-session-1");
        verify(recordingPort).createRecordedSession(Path.of("/tmp"), "PROJECT", "proj-abc");
    }

    @Test
    @DisplayName("[S018] AC-12: CLI not found wraps as ChatSessionException")
    void cliNotFoundThrowsChatSessionException() {
        // Given
        when(recordingPort.createRecordedSession(any(Path.class), any(), any()))
                .thenThrow(new IllegalStateException("claude not found"));

        // When / Then
        assertThatThrownBy(() -> service.createSession(Path.of("/tmp"), "GRIMO", null))
                .isInstanceOf(ChatSessionException.class)
                .hasMessageContaining("npm install -g @anthropic-ai/claude-code");
    }

    @Test
    @DisplayName("[S018] AC-12: resumeSession wraps via SessionRecordingPort")
    void resumeSessionWrapsSession() {
        // Given
        var wrappedSession = mock(AgentSession.class);
        when(wrappedSession.getSessionId()).thenReturn("resumed-1");
        when(recordingPort.wrapForRecording(any(AgentSession.class))).thenReturn(wrappedSession);

        try (MockedStatic<ClaudeSessionConnector> connector = mockStatic(ClaudeSessionConnector.class)) {
            var claudeSession = mock(org.springaicommunity.agents.claude.ClaudeAgentSession.class);
            when(claudeSession.getSessionId()).thenReturn("resumed-1");
            connector.when(() -> ClaudeSessionConnector.continueLastSession(
                    any(Path.class), any(Duration.class), isNull(), isNull()))
                    .thenReturn(claudeSession);

            // When
            AgentSession result = service.resumeSession(Path.of("/tmp"));

            // Then
            assertThat(result.getSessionId()).isEqualTo("resumed-1");
            verify(recordingPort).wrapForRecording(claudeSession);
        }
    }

    @Test
    @DisplayName("[S018] AC-12: resumeSession falls back to new GRIMO session")
    void resumeSessionFallsBack() {
        // Given
        var fallbackSession = mock(AgentSession.class);
        when(fallbackSession.getSessionId()).thenReturn("fallback-1");
        when(recordingPort.createRecordedSession(any(Path.class), eq("GRIMO"), isNull()))
                .thenReturn(fallbackSession);

        try (MockedStatic<ClaudeSessionConnector> connector = mockStatic(ClaudeSessionConnector.class)) {
            connector.when(() -> ClaudeSessionConnector.continueLastSession(
                    any(Path.class), any(Duration.class), isNull(), isNull()))
                    .thenThrow(new IllegalStateException("No previous session"));

            // When
            AgentSession result = service.resumeSession(Path.of("/tmp"));

            // Then
            assertThat(result.getSessionId()).isEqualTo("fallback-1");
            verify(recordingPort).createRecordedSession(Path.of("/tmp"), "GRIMO", null);
        }
    }
}
