package io.github.samzhu.grimo.agent.adapter.in.cli;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import io.github.samzhu.grimo.agent.application.port.in.MainAgentChatUseCase;
import io.github.samzhu.grimo.agent.domain.ChatSessionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChatCommandRunnerTest {

    private final MainAgentChatUseCase chatUseCase = mock(MainAgentChatUseCase.class);
    private final ChatCommandRunner runner = new ChatCommandRunner(chatUseCase);

    @Test
    @DisplayName("[S007] AC-3: 'chat' subcommand detected calls startChat")
    void chatSubcommandCallsStartChat() {
        // Given
        var args = new DefaultApplicationArguments("chat");

        // When
        runner.run(args);

        // Then
        verify(chatUseCase).startChat(any(Path.class));
    }

    @Test
    @DisplayName("[S007] AC-3: non-chat args does not call startChat")
    void nonChatArgsDoesNotCallStartChat() {
        // Given
        var args = new DefaultApplicationArguments("other");

        // When
        runner.run(args);

        // Then
        verify(chatUseCase, never()).startChat(any(Path.class));
    }

    @Test
    @DisplayName("[S007] AC-3: ChatSessionException prints to stderr without stacktrace")
    void chatSessionExceptionPrintsToStderrWithoutStacktrace() {
        // Given
        var errorMessage = "Claude CLI not found. Install: npm install -g @anthropic-ai/claude-code";
        doThrow(new ChatSessionException(errorMessage, new IllegalStateException("not found")))
                .when(chatUseCase).startChat(any(Path.class));
        var args = new DefaultApplicationArguments("chat");

        // When — capture stderr
        var originalErr = System.err;
        var baos = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(baos));

        try {
            runner.run(args);
        } finally {
            System.setErr(originalErr);
        }

        // Then
        var stderr = baos.toString();
        assertThat(stderr).contains(errorMessage);
        assertThat(stderr).doesNotContain("at io.github.samzhu");
    }
}
