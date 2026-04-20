/**
 * Grimo :: Agent — hosts the main-agent interactive chat use case
 * ({@code grimo chat}, S007) and session resume ({@code grimo chat --resume},
 * S011). Uses {@code AgentSessionRegistry} to maintain a persistent host
 * {@code claude} CLI process, with {@code AgentSession.prompt()} driving
 * multi-turn conversation via a terminal REPL.
 *
 * <p>Session resume uses {@code ClaudeSessionConnector.continueLastSession()}
 * (same-package helper) to pass {@code --continue} to the Claude CLI,
 * automatically restoring the most recent session in the working directory.
 * Falls back to a new session if no prior session exists.
 *
 * <p>S016: Before starting chat, projects enabled skills from
 * {@code ~/.grimo/skills/} to {@code <workdir>/.claude/skills/} via
 * {@code SkillProjectionUseCase} (skills::api).
 *
 * <p>S008+ will switch to containerised claude-code inside
 * {@code grimo-runtime}.
 */
@ApplicationModule(
    displayName = "Grimo :: Agent",
    allowedDependencies = { "skills::api" }
)
package io.github.samzhu.grimo.agent;

import org.springframework.modulith.ApplicationModule;
