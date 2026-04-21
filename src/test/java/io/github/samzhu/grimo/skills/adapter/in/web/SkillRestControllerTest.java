package io.github.samzhu.grimo.skills.adapter.in.web;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.samzhu.grimo.skills.application.port.in.SkillProjectionUseCase;
import io.github.samzhu.grimo.skills.application.port.in.SkillRegistryUseCase;
import io.github.samzhu.grimo.skills.domain.SkillEntry;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkillRestController.class)
class SkillRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillRegistryUseCase registry;

    @MockitoBean
    private SkillProjectionUseCase projection;

    private SkillEntry greetSkill(boolean enabled) {
        var skill = new SkillsTool.Skill("/tmp/skills/greet",
                java.util.Map.of("name", "greet", "description", "A greeting skill"),
                "# Greet skill content");
        return new SkillEntry(skill, enabled);
    }

    @Test
    @DisplayName("[S018] AC-10: GET /api/skills returns skill list")
    void listSkills() throws Exception {
        // Given
        when(registry.list()).thenReturn(List.of(greetSkill(true)));

        // When / Then
        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("greet")))
                .andExpect(jsonPath("$[0].enabled", is(true)))
                .andExpect(jsonPath("$[0].description", is("A greeting skill")));
    }

    @Test
    @DisplayName("[S018] AC-10: PUT /api/skills/greet/disable disables skill")
    void disableSkill() throws Exception {
        // When / Then
        mockMvc.perform(put("/api/skills/greet/disable"))
                .andExpect(status().isOk());

        verify(registry).disable("greet");
    }

    @Test
    @DisplayName("[S018] AC-10: PUT /api/skills/greet/enable enables skill")
    void enableSkill() throws Exception {
        // When / Then
        mockMvc.perform(put("/api/skills/greet/enable"))
                .andExpect(status().isOk());

        verify(registry).enable("greet");
    }

    @Test
    @DisplayName("[S018] AC-10: POST /api/skills/project projects skills to workDir")
    void projectSkills() throws Exception {
        // When / Then
        mockMvc.perform(post("/api/skills/project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workDir":"/tmp/test"}"""))
                .andExpect(status().isOk());

        verify(projection).projectToWorkDir(java.nio.file.Path.of("/tmp/test"));
    }

    @Test
    @DisplayName("[S018] AC-11: PUT /api/skills/nonexistent/enable returns 404")
    void enableNonexistent_returns404() throws Exception {
        // Given
        doThrow(new IllegalArgumentException("Skill not found: nonexistent"))
                .when(registry).enable("nonexistent");

        // When / Then
        mockMvc.perform(put("/api/skills/nonexistent/enable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Skill not found: nonexistent")));
    }
}
