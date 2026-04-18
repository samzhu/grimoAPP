/**
 * Grimo :: CLI — hosts {@code AgentCliPort} (S005) and its
 * {@code docker exec}-backed adapter for streaming token output from the
 * three containerised CLIs (claude, codex, gemini).
 *
 * <p>Empty in S002. The first concrete type lands with S005.
 *
 * <p>{@code allowedDependencies = {}} starts as the strictest white-list.
 * Each consuming spec adds itself when a real cross-module reference
 * emerges — see Cross-Module Communication Policy in
 * {@code development-standards.md} §13.
 */
@ApplicationModule(
    displayName = "Grimo :: CLI",
    allowedDependencies = { "core" }
)
package io.github.samzhu.grimo.cli;

import org.springframework.modulith.ApplicationModule;
