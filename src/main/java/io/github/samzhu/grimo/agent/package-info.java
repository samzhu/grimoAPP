/**
 * Grimo :: Agent — hosts the main-agent chat use case (S018 REST API).
 *
 * <p>S018: {@code MainAgentChatUseCase} creates/resumes sessions via
 * {@code SessionRecordingPort} (session::api). No longer drives a
 * terminal REPL — returns {@code AgentSession} for REST controllers
 * to prompt.
 *
 * <p>S016: Skill projection via {@code SkillProjectionUseCase}
 * (skills::api).
 */
@ApplicationModule(
    displayName = "Grimo :: Agent",
    allowedDependencies = { "skills::api", "session::api", "project::api" }
)
package io.github.samzhu.grimo.agent;

import org.springframework.modulith.ApplicationModule;
