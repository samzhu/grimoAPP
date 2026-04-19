package io.github.samzhu.grimo.agent.adapter.in.cli;

import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.github.samzhu.grimo.agent.application.port.in.MainAgentChatUseCase;
import io.github.samzhu.grimo.agent.domain.ChatSessionException;

@Component
class ChatCommandRunner implements ApplicationRunner {

    private final MainAgentChatUseCase chatUseCase;

    ChatCommandRunner(MainAgentChatUseCase chatUseCase) {
        this.chatUseCase = chatUseCase;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("chat")) {
            return;
        }
        Path workDir = Path.of("").toAbsolutePath();
        try {
            if (args.containsOption("resume")) {
                chatUseCase.resumeChat(workDir);
            } else {
                chatUseCase.startChat(workDir);
            }
        } catch (ChatSessionException e) {
            System.err.println(e.getMessage());
        }
    }
}
