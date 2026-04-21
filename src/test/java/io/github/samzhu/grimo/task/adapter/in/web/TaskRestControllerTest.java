package io.github.samzhu.grimo.task.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.samzhu.grimo.task.application.port.in.TaskUseCase;
import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskPriority;
import io.github.samzhu.grimo.task.domain.TaskStatus;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskRestController.class)
class TaskRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskUseCase taskUseCase;

    private static final Instant NOW = Instant.parse("2026-04-21T10:00:00Z");

    private Task sampleTask(int num, String projectId, TaskStatus status) {
        return new Task("t1a2b3c4d5e6", num, projectId, "修 UserService bug", null,
                status, TaskPriority.HIGH, null, null, NOW, NOW,
                status.isTerminal() ? NOW : null);
    }

    @Test
    @DisplayName("[S018] AC-3: POST /api/tasks with projectId returns 201")
    void createWithProject_returns201() throws Exception {
        // Given
        when(taskUseCase.create(eq("P1"), eq("修 UserService bug"), isNull(),
                eq(TaskPriority.HIGH), isNull(), isNull()))
                .thenReturn(sampleTask(1, "P1", TaskStatus.OPEN));

        // When / Then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"P1","title":"修 UserService bug","priority":"HIGH"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskNumber", is(1)))
                .andExpect(jsonPath("$.projectId", is("P1")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.priority", is("HIGH")));
    }

    @Test
    @DisplayName("[S018] AC-4: POST /api/tasks without projectId returns 201 with null projectId")
    void createWithoutProject_returns201() throws Exception {
        // Given
        var orphanTask = new Task("t2a3b4c5d6e7", 2, null, "研究 Spring AI 最新版",
                null, TaskStatus.OPEN, TaskPriority.MEDIUM, null, null, NOW, NOW, null);
        when(taskUseCase.create(isNull(), eq("研究 Spring AI 最新版"), isNull(),
                isNull(), isNull(), isNull()))
                .thenReturn(orphanTask);

        // When / Then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"研究 Spring AI 最新版"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskNumber", is(2)))
                .andExpect(jsonPath("$.projectId").value(nullValue()));
    }

    @Test
    @DisplayName("[S018] AC-3: GET /api/tasks?projectId=P1 returns filtered list")
    void listByProject() throws Exception {
        // Given
        when(taskUseCase.list(eq("P1"), isNull()))
                .thenReturn(List.of(sampleTask(1, "P1", TaskStatus.OPEN)));

        // When / Then
        mockMvc.perform(get("/api/tasks").param("projectId", "P1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectId", is("P1")));
    }

    @Test
    @DisplayName("[S018] AC-4: GET /api/tasks?projectId=none returns orphan tasks")
    void listOrphan() throws Exception {
        // Given
        var orphan = new Task("t2a3b4c5d6e7", 2, null, "研究 Spring AI",
                null, TaskStatus.OPEN, TaskPriority.MEDIUM, null, null, NOW, NOW, null);
        when(taskUseCase.list(eq("none"), isNull())).thenReturn(List.of(orphan));

        // When / Then
        mockMvc.perform(get("/api/tasks").param("projectId", "none"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectId").value(nullValue()));
    }

    @Test
    @DisplayName("[S018] AC-5: PATCH status to DONE sets closedAt")
    void updateStatusToDone() throws Exception {
        // Given
        when(taskUseCase.updateStatus(1, TaskStatus.DONE))
                .thenReturn(sampleTask(1, "P1", TaskStatus.DONE));

        // When / Then
        mockMvc.perform(patch("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DONE")))
                .andExpect(jsonPath("$.closedAt", notNullValue()));
    }

    @Test
    @DisplayName("[S018] AC-11: GET /api/tasks/999 returns 404")
    void findByNumberNotFound_returns404() throws Exception {
        // Given
        when(taskUseCase.findByNumber(999)).thenReturn(Optional.empty());

        // When / Then
        mockMvc.perform(get("/api/tasks/999"))
                .andExpect(status().isNotFound());
    }
}
