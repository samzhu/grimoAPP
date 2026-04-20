package io.github.samzhu.grimo.agent.adapter.in.cli;

import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.github.samzhu.grimo.agent.application.port.in.MainAgentChatUseCase;
import io.github.samzhu.grimo.agent.domain.ChatSessionException;
import io.github.samzhu.grimo.skills.application.port.in.SkillProjectionUseCase;

@Component
class ChatCommandRunner implements ApplicationRunner {

    private final MainAgentChatUseCase chatUseCase;
    private final SkillProjectionUseCase skillProjection;

    ChatCommandRunner(MainAgentChatUseCase chatUseCase,
                      SkillProjectionUseCase skillProjection) {
        this.chatUseCase = chatUseCase;
        this.skillProjection = skillProjection;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("chat")) {
            return;
        }
        Path workDir = Path.of("").toAbsolutePath();
        skillProjection.projectToWorkDir(workDir);
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
