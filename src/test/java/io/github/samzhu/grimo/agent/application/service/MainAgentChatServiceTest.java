package io.github.samzhu.grimo.agent.application.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springaicommunity.agents.claude.ClaudeSessionConnector;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentSession;
import org.springaicommunity.agents.model.AgentSessionRegistry;

import io.github.samzhu.grimo.agent.domain.ChatSessionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainAgentChatServiceTest {

    private final AgentSessionRegistry registry = mock(AgentSessionRegistry.class);
    private final MainAgentChatService service = new MainAgentChatService(registry);

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;

    @AfterEach
    void restoreStreams() {
        System.setIn(originalIn);
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("[S007] AC-3: CLI not found wraps IllegalStateException as ChatSessionException with install message")
    void cliNotFoundThrowsChatSessionExceptionWithInstallMessage() {
        // Given
        when(registry.create(any(Path.class)))
                .thenThrow(new IllegalStateException("claude not found"));

        // When / Then
        assertThatThrownBy(() -> service.startChat(Path.of("/tmp")))
                .isInstanceOf(ChatSessionException.class)
                .hasMessageContaining("npm install -g @anthropic-ai/claude-code")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("[S007] AC-1: REPL prompts session and prints response, multi-turn preserves context")
    void replPromptsSessionAndPrintsResponse() {
        // Given
        var session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("test-session-1");
        when(registry.create(any(Path.class))).thenReturn(session);

        var response1 = AgentResponse.builder()
                .results(List.of(new AgentGeneration("Hi there!")))
                .build();
        var response2 = AgentResponse.builder()
                .results(List.of(new AgentGeneration("I remember you said hello.")))
                .build();
        when(session.prompt("hello")).thenReturn(response1);
        when(session.prompt("do you remember?")).thenReturn(response2);

        System.setIn(new ByteArrayInputStream("hello\ndo you remember?\n/exit\n".getBytes(StandardCharsets.UTF_8)));
        var outCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));

        // When
        service.startChat(Path.of("/tmp"));

        // Then
        var output = outCapture.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Hi there!");
        assertThat(output).contains("I remember you said hello.");
        verify(session).close();
    }

    @Test
    @DisplayName("[S007] AC-2: /exit cleanly closes session")
    void exitCommandClosesSession() {
        // Given
        var session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("test-session-2");
        when(registry.create(any(Path.class))).thenReturn(session);

        System.setIn(new ByteArrayInputStream("/exit\n".getBytes(StandardCharsets.UTF_8)));
        System.setOut(new PrintStream(new ByteArrayOutputStream()));

        // When
        service.startChat(Path.of("/tmp"));

        // Then
        verify(session).close();
        verify(session, never()).prompt(anyString());
    }

    @Test
    @DisplayName("[S007] AC-2: Ctrl+D (EOF) cleanly closes session")
    void eofClosesSession() {
        // Given
        var session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("test-session-3");
        when(registry.create(any(Path.class))).thenReturn(session);

        // Empty input = immediate EOF
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(new PrintStream(new ByteArrayOutputStream()));

        // When
        service.startChat(Path.of("/tmp"));

        // Then
        verify(session).close();
        verify(session, never()).prompt(anyString());
    }

    @Test
    @DisplayName("[S007] AC-1: blank lines are skipped, not sent to session")
    void blankLinesSkipped() {
        // Given
        var session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("test-session-4");
        when(registry.create(any(Path.class))).thenReturn(session);

        System.setIn(new ByteArrayInputStream("\n  \n/exit\n".getBytes(StandardCharsets.UTF_8)));
        System.setOut(new PrintStream(new ByteArrayOutputStream()));

        // When
        service.startChat(Path.of("/tmp"));

        // Then
        verify(session, never()).prompt(anyString());
        verify(session).close();
    }

    @Test
    @DisplayName("[S011] AC-2: resumeChat falls back to new session when no previous session exists")
    void resumeChatFallsBackToNewSession() {
        // Given: ClaudeSessionConnector.continueLastSession throws (no prior session)
        var fallbackSession = mock(AgentSession.class);
        when(fallbackSession.getSessionId()).thenReturn("fallback-session");
        when(registry.create(any(Path.class))).thenReturn(fallbackSession);

        System.setIn(new ByteArrayInputStream("/exit\n".getBytes(StandardCharsets.UTF_8)));
        var outCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));

        try (MockedStatic<ClaudeSessionConnector> connector = mockStatic(ClaudeSessionConnector.class)) {
            connector.when(() -> ClaudeSessionConnector.continueLastSession(
                    any(Path.class), any(Duration.class), isNull(), isNull()))
                    .thenThrow(new IllegalStateException("No previous session"));

            // When
            service.resumeChat(Path.of("/tmp"));
        }

        // Then: prints fallback message
        var output = outCapture.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("No previous session found, starting new session");

        // And: falls back to registry.create (new session)
        verify(registry).create(any(Path.class));
        verify(fallbackSession).close();
    }
}
