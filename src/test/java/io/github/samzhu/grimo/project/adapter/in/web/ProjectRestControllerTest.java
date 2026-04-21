package io.github.samzhu.grimo.project.adapter.in.web;

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

import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.project.domain.DuplicateProjectNameException;
import io.github.samzhu.grimo.project.domain.Project;
import io.github.samzhu.grimo.project.domain.ProjectNotFoundException;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectRestController.class)
class ProjectRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectUseCase projectUseCase;

    private static final Instant NOW = Instant.parse("2026-04-21T10:00:00Z");

    private Project sampleProject() {
        return new Project("k7m3p2q9r4s1", "grimoAPP", "/tmp/grimo-test", null, NOW, NOW);
    }

    @Test
    @DisplayName("[S018] AC-1: POST /api/projects returns 201 with project")
    void createProject_returns201() throws Exception {
        // Given
        when(projectUseCase.create(eq("grimoAPP"), eq("/tmp/grimo-test"), isNull()))
                .thenReturn(sampleProject());

        // When / Then
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"grimoAPP","workDir":"/tmp/grimo-test"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("k7m3p2q9r4s1")))
                .andExpect(jsonPath("$.name", is("grimoAPP")))
                .andExpect(jsonPath("$.workDir", is("/tmp/grimo-test")));
    }

    @Test
    @DisplayName("[S018] AC-1: GET /api/projects returns list")
    void listProjects_returnsList() throws Exception {
        // Given
        when(projectUseCase.listAll()).thenReturn(List.of(sampleProject()));

        // When / Then
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("grimoAPP")));
    }

    @Test
    @DisplayName("[S018] AC-1: GET /api/projects/{id} returns project")
    void findById_returnsProject() throws Exception {
        // Given
        when(projectUseCase.findById("k7m3p2q9r4s1")).thenReturn(Optional.of(sampleProject()));

        // When / Then
        mockMvc.perform(get("/api/projects/k7m3p2q9r4s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("grimoAPP")));
    }

    @Test
    @DisplayName("[S018] AC-1: PATCH /api/projects/{id} returns updated project")
    void updateProject_returnsUpdated() throws Exception {
        // Given
        var updated = new Project("k7m3p2q9r4s1", "grimoAPP", "/tmp/grimo-test",
                "AI agent harness", NOW, NOW);
        when(projectUseCase.update(eq("k7m3p2q9r4s1"), isNull(), isNull(), eq("AI agent harness")))
                .thenReturn(updated);

        // When / Then
        mockMvc.perform(patch("/api/projects/k7m3p2q9r4s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"AI agent harness"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("AI agent harness")));
    }

    @Test
    @DisplayName("[S018] AC-1: DELETE /api/projects/{id} returns 204")
    void deleteProject_returns204() throws Exception {
        // When / Then
        mockMvc.perform(delete("/api/projects/k7m3p2q9r4s1"))
                .andExpect(status().isNoContent());

        verify(projectUseCase).delete("k7m3p2q9r4s1");
    }

    @Test
    @DisplayName("[S018] AC-2: POST duplicate name returns 409")
    void createDuplicateName_returns409() throws Exception {
        // Given
        when(projectUseCase.create(any(), any(), any()))
                .thenThrow(new DuplicateProjectNameException("grimoAPP"));

        // When / Then
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"grimoAPP","workDir":"/tmp/other"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Project name already exists: grimoAPP")));
    }

    @Test
    @DisplayName("[S018] AC-11: GET nonexistent project returns 404")
    void findByIdNotFound_returns404() throws Exception {
        // Given
        when(projectUseCase.findById("nonexistent")).thenReturn(Optional.empty());

        // When / Then
        mockMvc.perform(get("/api/projects/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Project not found: nonexistent")));
    }

    @Test
    @DisplayName("[S018] AC-11: DELETE nonexistent project returns 404")
    void deleteNotFound_returns404() throws Exception {
        // Given
        doThrow(new ProjectNotFoundException("nonexistent"))
                .when(projectUseCase).delete("nonexistent");

        // When / Then
        mockMvc.perform(delete("/api/projects/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
