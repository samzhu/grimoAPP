package io.github.samzhu.grimo.agent.adapter.in.web;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.samzhu.grimo.agent.application.port.in.MainAgentChatUseCase;
import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.project.domain.Project;
import io.github.samzhu.grimo.skills.application.port.in.SkillProjectionUseCase;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MainAgentChatUseCase chatUseCase;

    @MockitoBean
    private ProjectUseCase projectUseCase;

    @MockitoBean
    private SkillProjectionUseCase skillProjection;

    private AgentSession stubSession(String sessionId, String responseText) {
        var session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn(sessionId);
        var response = AgentResponse.builder()
                .results(List.of(new AgentGeneration(responseText)))
                .build();
        when(session.prompt(any())).thenReturn(response);
        return session;
    }

    @Test
    @DisplayName("[S018] AC-6: POST /api/chat creates GRIMO session")
    void newChat_grimoSession() throws Exception {
        // Given
        var session = stubSession("sess-grimo-1", "Hello! How can I help?");
        when(chatUseCase.createSession(any(Path.class), eq("GRIMO"), isNull()))
                .thenReturn(session);

        // When / Then
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", is("sess-grimo-1")))
                .andExpect(jsonPath("$.response", is("Hello! How can I help?")));

        verify(chatUseCase).createSession(any(Path.class), eq("GRIMO"), isNull());
    }

    @Test
    @DisplayName("[S018] AC-7: POST /api/chat with projectId creates PROJECT session")
    void newChat_projectSession() throws Exception {
        // Given
        var project = new Project("P1", "grimoAPP", "/tmp/grimo-test", null,
                Instant.now(), Instant.now());
        when(projectUseCase.findById("P1")).thenReturn(Optional.of(project));
        var session = stubSession("sess-proj-1", "Hi from project!");
        when(chatUseCase.createSession(eq(Path.of("/tmp/grimo-test")), eq("PROJECT"), eq("P1")))
                .thenReturn(session);

        // When / Then
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","projectId":"P1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", is("sess-proj-1")));

        verify(chatUseCase).createSession(eq(Path.of("/tmp/grimo-test")), eq("PROJECT"), eq("P1"));
    }

    @Test
    @DisplayName("[S018] AC-8: POST /api/chat/{sessionId} continues conversation")
    void continueChat() throws Exception {
        // Given — first create a session
        var session = stubSession("sess-cont-1", "First response");
        when(chatUseCase.createSession(any(), any(), any())).thenReturn(session);

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"message":"hello"}"""));

        // When — continue with the session
        when(session.prompt("follow up")).thenReturn(
                AgentResponse.builder()
                        .results(List.of(new AgentGeneration("I remember!")))
                        .build());

        mockMvc.perform(post("/api/chat/sess-cont-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"follow up"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("I remember!")));
    }

    @Test
    @DisplayName("[S018] AC-11: POST /api/chat/nonexistent-session returns 404")
    void continueNonexistentSession_returns404() throws Exception {
        // When / Then
        mockMvc.perform(post("/api/chat/nonexistent-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hi"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Session not found: nonexistent-session")));
    }

    @Test
    @DisplayName("[S018] AC-8: POST /api/chat/resume resumes latest session")
    void resumeChat() throws Exception {
        // Given
        var session = stubSession("sess-resume-1", "Resumed!");
        when(chatUseCase.resumeSession(any(Path.class))).thenReturn(session);

        // When / Then
        mockMvc.perform(post("/api/chat/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"continue"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", is("sess-resume-1")))
                .andExpect(jsonPath("$.response", is("Resumed!")));
    }
}
