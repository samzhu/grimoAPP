package io.github.samzhu.grimo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack E2E integration test: HTTP → Spring → real Claude CLI → Session recording → H2.
 *
 * <p>Requires {@code claude} CLI on PATH with valid login.
 * Skipped on CI ({@code CI=true}) and when claude is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Requires local claude CLI + valid login")
class ChatEndToEndIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    /** Session ID created by AC-1, shared by AC-2 through AC-4. */
    private String sharedSessionId;

    /** Tokens-in after first turn (AC-3), used by AC-4 to verify accumulation. */
    private long firstTurnTokensIn;

    private static boolean claudeAvailable() {
        try {
            var process = new ProcessBuilder("which", "claude")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    void checkClaude() {
        Assumptions.assumeTrue(claudeAvailable(),
                "claude CLI not found on PATH — skipping IT");
    }

    // ── AC-1: New GRIMO session with real Claude ──────────────────────

    @Test
    @Order(1)
    @DisplayName("[S024] AC-1: new GRIMO session with real claude → HTTP 200")
    void ac1_newGrimoSessionWithRealClaude() throws Exception {
        // Given — claude CLI is available (checked in @BeforeAll)

        // When
        MvcResult result = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Reply with exactly: GRIMO_E2E_OK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.response").isNotEmpty())
                .andReturn();

        // Then — capture sessionId for subsequent tests
        var body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        sharedSessionId = (String) body.get("sessionId");

        assertThat(sharedSessionId).isNotBlank();
        assertThat((String) body.get("response")).isNotBlank();
    }

    // ── AC-2: Session events persisted to H2 ─────────────────────────

    @Test
    @Order(2)
    @DisplayName("[S024] AC-2: session events persisted to H2")
    void ac2_sessionEventsPersisted() throws Exception {
        // Given — AC-1 completed
        assertThat(sharedSessionId).as("AC-1 must run first").isNotNull();

        // When
        MvcResult result = mockMvc.perform(
                        get("/api/sessions/{id}/events", sharedSessionId))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        var events = objectMapper.readValue(
                result.getResponse().getContentAsString(), List.class);

        assertThat(events).hasSize(2);

        @SuppressWarnings("unchecked")
        var userEvent = (Map<String, Object>) events.get(0);
        @SuppressWarnings("unchecked")
        var assistantEvent = (Map<String, Object>) events.get(1);

        // USER event
        assertThat(userEvent.get("messageType")).isEqualTo("USER");
        assertThat((String) userEvent.get("messageContent"))
                .contains("GRIMO_E2E_OK");

        // ASSISTANT event
        assertThat(assistantEvent.get("messageType")).isEqualTo("ASSISTANT");
        assertThat((String) assistantEvent.get("messageContent")).isNotBlank();

        // ASSISTANT event has provider (USER event does not — by design)
        assertThat((String) assistantEvent.get("provider")).isNotBlank();
    }

    // ── AC-3: Session projection materialized ────────────────────────

    @Test
    @Order(3)
    @DisplayName("[S024] AC-3: session projection materialized correctly")
    void ac3_sessionProjectionMaterialized() throws Exception {
        // Given — AC-1 completed
        assertThat(sharedSessionId).as("AC-1 must run first").isNotNull();

        // When
        MvcResult result = mockMvc.perform(
                        get("/api/sessions/{id}", sharedSessionId))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        @SuppressWarnings("unchecked")
        var projection = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(((Number) projection.get("turnCount")).intValue()).isEqualTo(1);
        assertThat(((Number) projection.get("totalDurationMs")).longValue()).isGreaterThan(0);
        assertThat(projection.get("status")).isEqualTo("ACTIVE");
        assertThat(projection.get("sessionType")).isEqualTo("GRIMO");
        assertThat((String) projection.get("currentEventId")).isNotBlank();

        // Save tokensIn for AC-4 accumulation check
        firstTurnTokensIn = ((Number) projection.get("totalTokensIn")).longValue();
        assertThat(firstTurnTokensIn)
                .as("totalTokensIn should be > 0 after real Claude response")
                .isGreaterThan(0);
    }

    // ── AC-4: Multi-turn → parent-child chain ────────────────────────

    @Test
    @Order(4)
    @DisplayName("[S024] AC-4: multi-turn parent-child chain + turn_count increment")
    void ac4_multiTurnParentChildChain() throws Exception {
        // Given — AC-1 completed, session has 1 turn
        assertThat(sharedSessionId).as("AC-1 must run first").isNotNull();

        // When — second turn
        mockMvc.perform(post("/api/chat/{sessionId}", sharedSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "What was my previous message?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").isNotEmpty());

        // Then — verify events
        MvcResult eventsResult = mockMvc.perform(
                        get("/api/sessions/{id}/events", sharedSessionId))
                .andExpect(status().isOk())
                .andReturn();

        var events = objectMapper.readValue(
                eventsResult.getResponse().getContentAsString(), List.class);
        assertThat(events).hasSize(4);

        // Verify linear parent-child chain
        @SuppressWarnings("unchecked")
        var firstEvent = (Map<String, Object>) events.get(0);
        assertThat(firstEvent.get("parentEventId"))
                .as("root event has null parentEventId").isNull();

        for (int i = 1; i < events.size(); i++) {
            @SuppressWarnings("unchecked")
            var current = (Map<String, Object>) events.get(i);
            @SuppressWarnings("unchecked")
            var previous = (Map<String, Object>) events.get(i - 1);
            assertThat(current.get("parentEventId"))
                    .as("event[%d].parentEventId should point to event[%d].id", i, i - 1)
                    .isEqualTo(previous.get("id"));
        }

        // Then — verify projection
        MvcResult projResult = mockMvc.perform(
                        get("/api/sessions/{id}", sharedSessionId))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        var projection = objectMapper.readValue(
                projResult.getResponse().getContentAsString(), Map.class);

        assertThat(((Number) projection.get("turnCount")).intValue()).isEqualTo(2);
        assertThat(((Number) projection.get("totalTokensIn")).longValue())
                .as("tokens should accumulate across turns")
                .isGreaterThan(firstTurnTokensIn);

        // currentEventId should be the last ASSISTANT event
        @SuppressWarnings("unchecked")
        var lastEvent = (Map<String, Object>) events.get(events.size() - 1);
        assertThat(projection.get("currentEventId"))
                .isEqualTo(lastEvent.get("id"));
    }

    // ── AC-5: Project-scoped session ──────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("[S024] AC-5: project-scoped session")
    void ac5_projectScopedSession() throws Exception {
        // Given — create a project via REST API
        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "e2e-test-proj", "workDir": "%s"}
                                """.formatted(tempDir.toAbsolutePath())))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        var projectBody = objectMapper.readValue(
                projectResult.getResponse().getContentAsString(), Map.class);
        String projectId = (String) projectBody.get("id");

        // When — create chat with projectId
        MvcResult chatResult = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "hello", "projectId": "%s"}
                                """.formatted(projectId)))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        var chatBody = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), Map.class);
        String projectSessionId = (String) chatBody.get("sessionId");

        // Then — verify session is PROJECT-scoped
        MvcResult sessionResult = mockMvc.perform(
                        get("/api/sessions/{id}", projectSessionId))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        var session = objectMapper.readValue(
                sessionResult.getResponse().getContentAsString(), Map.class);

        assertThat(session.get("sessionType")).isEqualTo("PROJECT");
        assertThat(session.get("projectId")).isEqualTo(projectId);

        // Then — verify session list filters
        MvcResult byTypeResult = mockMvc.perform(
                        get("/api/sessions").param("sessionType", "PROJECT"))
                .andExpect(status().isOk())
                .andReturn();

        var byTypeList = objectMapper.readValue(
                byTypeResult.getResponse().getContentAsString(), List.class);
        @SuppressWarnings("unchecked")
        var sessionIds = ((List<Map<String, Object>>) byTypeList).stream()
                .map(m -> m.get("id"))
                .toList();
        assertThat(sessionIds).contains(projectSessionId);

        MvcResult byProjectResult = mockMvc.perform(
                        get("/api/sessions").param("projectId", projectId))
                .andExpect(status().isOk())
                .andReturn();

        var byProjectList = objectMapper.readValue(
                byProjectResult.getResponse().getContentAsString(), List.class);
        @SuppressWarnings("unchecked")
        var projSessionIds = ((List<Map<String, Object>>) byProjectList).stream()
                .map(m -> m.get("id"))
                .toList();
        assertThat(projSessionIds).contains(projectSessionId);
    }

    // ── AC-6: Session close ──────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("[S024] AC-6: session close → DELETE 204, subsequent POST 404")
    void ac6_sessionClose() throws Exception {
        // Given — create a separate session for this test (don't affect shared session)
        MvcResult createResult = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "hello"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        var body = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Map.class);
        String closeSessionId = (String) body.get("sessionId");

        // When — close
        mockMvc.perform(delete("/api/chat/{sessionId}", closeSessionId))
                .andExpect(status().isNoContent());

        // Then — subsequent prompt returns 404
        mockMvc.perform(post("/api/chat/{sessionId}", closeSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "hello"}
                                """))
                .andExpect(status().isNotFound());
    }
}
