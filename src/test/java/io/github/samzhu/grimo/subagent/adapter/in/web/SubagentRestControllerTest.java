package io.github.samzhu.grimo.subagent.adapter.in.web;

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

import io.github.samzhu.grimo.subagent.application.port.in.DelegateTaskUseCase;
import io.github.samzhu.grimo.subagent.domain.ExecutionStatus;
import io.github.samzhu.grimo.subagent.domain.TaskExecution;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubagentRestController.class)
class SubagentRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DelegateTaskUseCase delegateTaskUseCase;

    private static final Instant NOW = Instant.parse("2026-04-22T10:00:00Z");

    @Test
    @DisplayName("[S028] AC-1: POST /api/tasks/42/execute returns 202 Accepted")
    void ac1_postExecuteReturns202() throws Exception {
        // Given
        var execution = new TaskExecution("exec12345678", "task1", 42,
                ExecutionStatus.PENDING, "Add hello.txt", null, null, null,
                null, null, null, null, null, NOW);
        when(delegateTaskUseCase.execute(eq(42), eq("Add hello.txt")))
                .thenReturn(execution);

        // When/Then
        mockMvc.perform(post("/api/tasks/42/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "Add hello.txt"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", is("exec12345678")))
                .andExpect(jsonPath("$.taskNumber", is(42)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.prompt", is("Add hello.txt")));
    }

    @Test
    @DisplayName("[S028] AC-8: GET /api/tasks/42/executions/{id} returns execution details")
    void ac8_getExecutionReturnsDetails() throws Exception {
        // Given
        var execution = new TaskExecution("exec12345678", "task1", 42,
                ExecutionStatus.SUCCEEDED, "Add hello.txt",
                "grimo/task-task1", "/home/.grimo/worktrees/task1", "container-abc",
                "{\"result\":\"ok\"}", "diff --git a/hello.txt ...", null,
                NOW, NOW.plusSeconds(30), NOW);
        when(delegateTaskUseCase.findExecution("exec12345678"))
                .thenReturn(Optional.of(execution));

        // When/Then
        mockMvc.perform(get("/api/tasks/42/executions/exec12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("exec12345678")))
                .andExpect(jsonPath("$.status", is("SUCCEEDED")))
                .andExpect(jsonPath("$.agentResponse", is("{\"result\":\"ok\"}")))
                .andExpect(jsonPath("$.diff", is("diff --git a/hello.txt ...")))
                .andExpect(jsonPath("$.branch", is("grimo/task-task1")));
    }

    @Test
    @DisplayName("[S028] AC-8: GET /api/tasks/42/executions/{id} returns 404 for missing")
    void ac8_getExecutionReturns404ForMissing() throws Exception {
        // Given
        when(delegateTaskUseCase.findExecution("nonexistent")).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/tasks/42/executions/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[S028] GET /api/tasks/42/executions returns execution list")
    void listExecutions() throws Exception {
        // Given
        var exec1 = new TaskExecution("execAAAAAAAA", "task1", 42,
                ExecutionStatus.FAILED, "first", null, null, null,
                null, null, "error", null, NOW, NOW);
        var exec2 = new TaskExecution("execBBBBBBBB", "task1", 42,
                ExecutionStatus.SUCCEEDED, "second", "b", "/w", "c",
                "{}", "diff", null, NOW, NOW, NOW);
        when(delegateTaskUseCase.listExecutions(42)).thenReturn(List.of(exec2, exec1));

        // When/Then
        mockMvc.perform(get("/api/tasks/42/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("execBBBBBBBB")))
                .andExpect(jsonPath("$[1].id", is("execAAAAAAAA")));
    }

    @Test
    @DisplayName("[S028] POST /api/tasks/99/execute returns 404 for missing task")
    void executeReturns404ForMissingTask() throws Exception {
        // Given
        when(delegateTaskUseCase.execute(eq(99), eq("test")))
                .thenThrow(new IllegalArgumentException("Task #99 not found"));

        // When/Then
        mockMvc.perform(post("/api/tasks/99/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "test"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Task #99 not found")));
    }

    @Test
    @DisplayName("[S028] POST execute returns 409 for non-OPEN task")
    void executeReturns409ForNonOpenTask() throws Exception {
        // Given
        when(delegateTaskUseCase.execute(eq(42), eq("test")))
                .thenThrow(new IllegalStateException("Task #42 is IN_PROGRESS, expected OPEN"));

        // When/Then
        mockMvc.perform(post("/api/tasks/42/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "test"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Task #42 is IN_PROGRESS, expected OPEN")));
    }
}
