package io.github.samzhu.grimo.session.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.samzhu.grimo.session.application.port.in.SessionHistoryUseCase;
import io.github.samzhu.grimo.session.domain.MessageType;
import io.github.samzhu.grimo.session.domain.SessionEvent;
import io.github.samzhu.grimo.session.domain.SessionProjection;
import io.github.samzhu.grimo.session.domain.SessionStatus;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionRestController.class)
class SessionRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionHistoryUseCase sessionHistory;

    private static final Instant NOW = Instant.parse("2026-04-21T10:00:00Z");

    private SessionProjection grimoSession() {
        return new SessionProjection("sess-1", "GRIMO", null,
                SessionStatus.ACTIVE, 5, 3200, 1800, 12500, 10, null, NOW, NOW);
    }

    private SessionProjection projectSession() {
        return new SessionProjection("sess-2", "PROJECT", "P1",
                SessionStatus.ACTIVE, 2, 1100, 600, 4200, 4, null, NOW, NOW);
    }

    @Test
    @DisplayName("[S018] AC-9: GET /api/sessions returns all sessions")
    void listAll() throws Exception {
        // Given
        when(sessionHistory.listAll()).thenReturn(List.of(grimoSession(), projectSession()));

        // When / Then
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sessionType", is("GRIMO")))
                .andExpect(jsonPath("$[1].sessionType", is("PROJECT")));
    }

    @Test
    @DisplayName("[S018] AC-9: GET /api/sessions?sessionType=PROJECT filters by type")
    void filterBySessionType() throws Exception {
        // Given
        when(sessionHistory.findBySessionType("PROJECT")).thenReturn(List.of(projectSession()));

        // When / Then
        mockMvc.perform(get("/api/sessions").param("sessionType", "PROJECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sessionType", is("PROJECT")));
    }

    @Test
    @DisplayName("[S018] AC-9: GET /api/sessions?projectId=P1 filters by project")
    void filterByProjectId() throws Exception {
        // Given
        when(sessionHistory.findByProjectId("P1")).thenReturn(List.of(projectSession()));

        // When / Then
        mockMvc.perform(get("/api/sessions").param("projectId", "P1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectId", is("P1")));
    }

    @Test
    @DisplayName("[S018] AC-9: GET /api/sessions/{id} returns session details")
    void findById() throws Exception {
        // Given
        when(sessionHistory.findById("sess-1")).thenReturn(Optional.of(grimoSession()));

        // When / Then
        mockMvc.perform(get("/api/sessions/sess-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("sess-1")))
                .andExpect(jsonPath("$.sessionType", is("GRIMO")));
    }

    @Test
    @DisplayName("[S018] AC-9: GET /api/sessions/{id}/events returns event list")
    void getEvents() throws Exception {
        // Given
        when(sessionHistory.findById("sess-1")).thenReturn(Optional.of(grimoSession()));
        var event = new SessionEvent("evt-1", "sess-1", MessageType.USER,
                "hello", null, null, null, null, false, null, NOW);
        when(sessionHistory.getEvents("sess-1")).thenReturn(List.of(event));

        // When / Then
        mockMvc.perform(get("/api/sessions/sess-1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].messageType", is("USER")));
    }

    @Test
    @DisplayName("[S018] AC-11: GET /api/sessions/nonexistent returns 404")
    void findByIdNotFound() throws Exception {
        // Given
        when(sessionHistory.findById("nonexistent")).thenReturn(Optional.empty());

        // When / Then
        mockMvc.perform(get("/api/sessions/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
