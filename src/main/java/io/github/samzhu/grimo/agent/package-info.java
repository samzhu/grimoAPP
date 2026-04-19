/**
 * Grimo :: Agent — hosts the main-agent interactive chat use case
 * ({@code grimo chat}, S007). Uses {@code AgentSessionRegistry} to
 * maintain a persistent host {@code claude} CLI process, with
 * {@code AgentSession.prompt()} driving multi-turn conversation via
 * a terminal REPL.
 *
 * <p>S008+ will switch to containerised claude-code inside
 * {@code grimo-runtime}.
 *
 * <p>{@code allowedDependencies = {}} — all imports come from library
 * dependencies (agent-client, Spring Boot), no cross-module references.
 */
@ApplicationModule(
    displayName = "Grimo :: Agent",
    allowedDependencies = {}
)
package io.github.samzhu.grimo.agent;

import org.springframework.modulith.ApplicationModule;
