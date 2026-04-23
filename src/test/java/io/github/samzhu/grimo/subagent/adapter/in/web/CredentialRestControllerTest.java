package io.github.samzhu.grimo.subagent.adapter.in.web;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.samzhu.grimo.subagent.application.port.in.CredentialUseCase;
import io.github.samzhu.grimo.subagent.application.port.out.SettingPort;
import io.github.samzhu.grimo.subagent.domain.Credential;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CredentialRestController.class)
class CredentialRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CredentialUseCase credentialUseCase;

    @MockitoBean
    private SettingPort settingPort;

    private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");
    private static final Instant EXPIRES = NOW.plus(365, ChronoUnit.DAYS);

    @Test
    @DisplayName("[S030] AC-1: POST /api/credentials returns 201 with masked secret")
    void ac1_createCredential() throws Exception {
        // Given
        var credential = new Credential("cred1234abcd", "personal-max", "claude",
                "oauth_token", "sk-ant-oat01-abcdefghij", 1, EXPIRES, NOW);
        when(credentialUseCase.create(eq("personal-max"), eq("claude"), eq("oauth_token"),
                eq("sk-ant-oat01-abcdefghij"), any()))
                .thenReturn(credential);

        // When/Then
        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": "personal-max", "provider": "claude",
                                 "credentialType": "oauth_token",
                                 "secretValue": "sk-ant-oat01-abcdefghij",
                                 "expiresAt": "2027-04-23T10:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("cred1234abcd")))
                .andExpect(jsonPath("$.label", is("personal-max")))
                .andExpect(jsonPath("$.provider", is("claude")))
                .andExpect(jsonPath("$.credentialType", is("oauth_token")))
                .andExpect(jsonPath("$.maskedSecret", is("sk-ant***...hij")))
                .andExpect(jsonPath("$.secretValue").doesNotExist());
    }

    @Test
    @DisplayName("[S030] AC-2: GET /api/credentials returns list with masked secrets")
    void ac2_listCredentials() throws Exception {
        // Given
        var cred1 = new Credential("cred1", "personal-max", "claude", "oauth_token",
                "sk-ant-oat01-aaaa", 1, EXPIRES, NOW);
        var cred2 = new Credential("cred2", "work-team", "claude", "oauth_token",
                "sk-ant-oat01-bbbb", 2, EXPIRES, NOW);
        when(credentialUseCase.listAll()).thenReturn(List.of(cred1, cred2));

        // When/Then
        mockMvc.perform(get("/api/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].maskedSecret", is("sk-ant***...aaa")))
                .andExpect(jsonPath("$[1].maskedSecret", is("sk-ant***...bbb")))
                .andExpect(jsonPath("$[0].secretValue").doesNotExist())
                .andExpect(jsonPath("$[1].secretValue").doesNotExist());
    }

    @Test
    @DisplayName("[S030] AC-8: DELETE /api/credentials/{id} returns 204")
    void ac8_deleteCredential() throws Exception {
        // When/Then
        mockMvc.perform(delete("/api/credentials/cred1234abcd"))
                .andExpect(status().isNoContent());

        verify(credentialUseCase).delete("cred1234abcd");
    }

    @Test
    @DisplayName("[S030] AC-9: PUT /api/settings/credential-strategy updates strategy")
    void ac9_updateStrategy() throws Exception {
        // When/Then
        mockMvc.perform(put("/api/settings/credential-strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value": "RANDOM"}"""))
                .andExpect(status().isOk());

        verify(settingPort).set("credential-strategy", "RANDOM");
    }

    @Test
    @DisplayName("[S030] GET /api/settings/credential-strategy returns current strategy")
    void getStrategy() throws Exception {
        // Given
        when(settingPort.get("credential-strategy")).thenReturn(Optional.of("RANDOM"));

        // When/Then
        mockMvc.perform(get("/api/settings/credential-strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is("RANDOM")));
    }

    @Test
    @DisplayName("[S030] GET /api/settings/credential-strategy returns PRIORITY as default")
    void getStrategyDefault() throws Exception {
        // Given — no setting
        when(settingPort.get("credential-strategy")).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/settings/credential-strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is("PRIORITY")));
    }

    @Test
    @DisplayName("[S030] PUT /api/credentials/{id}/sort-order updates sort order")
    void updateSortOrder() throws Exception {
        // When/Then
        mockMvc.perform(put("/api/credentials/cred1234abcd/sort-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sortOrder": 3}"""))
                .andExpect(status().isOk());

        verify(credentialUseCase).updateSortOrder("cred1234abcd", 3);
    }
}
