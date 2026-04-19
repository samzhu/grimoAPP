package org.springaicommunity.agents.claude;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.claude.agent.sdk.ClaudeClient;
import org.springaicommunity.claude.agent.sdk.ClaudeSyncClient;
import org.springaicommunity.claude.agent.sdk.hooks.HookRegistry;
import org.springaicommunity.claude.agent.sdk.parsing.ParsedMessage;
import org.springaicommunity.claude.agent.sdk.transport.CLIOptions;

import io.github.markpollack.journal.claude.PhaseCapture;
import io.github.markpollack.journal.claude.SessionLogParser;

/**
 * Bridge for session resume — accesses package-private
 * {@link ClaudeAgentSession} constructor to create sessions with
 * {@code --continue} or {@code --resume} CLI flags.
 *
 * <p>Temporary bridge until upstream {@code agent-client} adds
 * {@code AgentSessionRegistry.reconnect()}.
 *
 * <p>Lives in {@code org.springaicommunity.agents.claude} package
 * to access the package-private constructor of
 * {@link ClaudeAgentSession}.
 */
public final class ClaudeSessionConnector {

    private static final Logger log = LoggerFactory.getLogger(ClaudeSessionConnector.class);

    private ClaudeSessionConnector() {}

    /**
     * Resumes the most recent session in the given working directory.
     * Uses Claude CLI's {@code --continue} flag.
     *
     * @param workingDirectory the project root directory
     * @param timeout          CLI timeout (nullable — defaults to SDK default)
     * @param claudePath       path to claude binary (nullable — auto-discover)
     * @param hookRegistry     hook registry (nullable — empty registry)
     * @return a live {@link ClaudeAgentSession} connected to the resumed CLI process
     * @throws IllegalStateException if CLI is not available or session init fails
     */
    public static ClaudeAgentSession continueLastSession(
            Path workingDirectory,
            Duration timeout,
            String claudePath,
            HookRegistry hookRegistry) {

        CLIOptions options = CLIOptions.builder()
                .continueConversation(true)
                .build();

        HookRegistry hooks = hookRegistry != null ? hookRegistry : new HookRegistry();

        var clientBuilder = ClaudeClient.sync(options)
                .workingDirectory(workingDirectory)
                .hookRegistry(hooks);

        if (timeout != null) {
            clientBuilder.timeout(timeout);
        }
        if (claudePath != null) {
            clientBuilder.claudePath(claudePath);
        }

        ClaudeSyncClient client = clientBuilder.build();

        client.connect();
        Iterator<ParsedMessage> initResponse = client.receiveResponse();
        PhaseCapture capture = SessionLogParser.parse(initResponse, "session-continue", "");
        String sessionId = capture.sessionId();

        if (sessionId == null || sessionId.isEmpty() || "default".equals(sessionId)) {
            client.close();
            throw new IllegalStateException(
                    "Failed to resume session — no session ID returned from CLI");
        }

        log.info("Continued session {} in {}", sessionId, workingDirectory);

        return new ClaudeAgentSession(
                sessionId, workingDirectory, client,
                timeout, claudePath, hooks);
    }
}
