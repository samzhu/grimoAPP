package io.github.samzhu.grimo.agent.adapter.in.web;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.agents.model.AgentSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.samzhu.grimo.agent.application.port.in.MainAgentChatUseCase;
import io.github.samzhu.grimo.agent.domain.ChatSessionException;
import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.skills.application.port.in.SkillProjectionUseCase;

@RestController
@RequestMapping("/api/chat")
class ChatController {

    private final MainAgentChatUseCase chatUseCase;
    private final ProjectUseCase projectUseCase;
    private final SkillProjectionUseCase skillProjection;
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    ChatController(MainAgentChatUseCase chatUseCase,
                   ProjectUseCase projectUseCase,
                   SkillProjectionUseCase skillProjection) {
        this.chatUseCase = chatUseCase;
        this.projectUseCase = projectUseCase;
        this.skillProjection = skillProjection;
    }

    record ChatRequest(String message, @Nullable String projectId, @Nullable String workDir) {}
    record ChatResponse(String sessionId, String response) {}

    @PostMapping
    ChatResponse newChat(@RequestBody ChatRequest req) {
        String sessionType = req.projectId() != null ? "PROJECT" : "GRIMO";
        Path workDir = resolveWorkDir(req.projectId(), req.workDir());

        skillProjection.projectToWorkDir(workDir);
        AgentSession session = chatUseCase.createSession(workDir, sessionType, req.projectId());
        sessions.put(session.getSessionId(), session);

        var response = session.prompt(req.message());
        return new ChatResponse(session.getSessionId(), response.getText());
    }

    @PostMapping("/resume")
    ChatResponse resumeChat(@RequestBody ChatRequest req) {
        Path workDir = resolveWorkDir(req.projectId(), req.workDir());

        skillProjection.projectToWorkDir(workDir);
        AgentSession session = chatUseCase.resumeSession(workDir);
        sessions.put(session.getSessionId(), session);

        var response = session.prompt(req.message());
        return new ChatResponse(session.getSessionId(), response.getText());
    }

    @PostMapping("/{sessionId}")
    ChatResponse continueChat(@PathVariable String sessionId,
                              @RequestBody ChatRequest req) {
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }
        var response = session.prompt(req.message());
        return new ChatResponse(sessionId, response.getText());
    }

    @DeleteMapping("/{sessionId}")
    ResponseEntity<Void> closeSession(@PathVariable String sessionId) {
        AgentSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
        return ResponseEntity.noContent().build();
    }

    private Path resolveWorkDir(@Nullable String projectId, @Nullable String workDir) {
        if (projectId != null) {
            return projectUseCase.findById(projectId)
                    .map(p -> Path.of(p.workDir()))
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        }
        if (workDir != null) {
            return Path.of(workDir);
        }
        return Path.of("").toAbsolutePath();
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<Map<String, String>> handleSessionNotFound(SessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ChatSessionException.class)
    ResponseEntity<Map<String, String>> handleChatError(ChatSessionException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage()));
    }

    static class SessionNotFoundException extends RuntimeException {
        SessionNotFoundException(String sessionId) {
            super("Session not found: " + sessionId);
        }
    }
}
