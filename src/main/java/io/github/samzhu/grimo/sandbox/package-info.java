/**
 * Grimo :: Sandbox — hosts {@code SandboxPort} (S003) and its
 * Testcontainers-backed adapter for spawning bind-mounted Docker
 * containers used by sub-agent execution and CLI invocations.
 *
 * <p>Empty in S002. The first concrete type lands with S003.
 *
 * <p>{@code allowedDependencies = {}} starts as the strictest white-list
 * (no cross-module access permitted). When S003 needs access to
 * {@code core} types it does so for free (core is OPEN). Other
 * dependencies are added by the consuming spec at the moment the
 * cross-module reference is introduced — see Cross-Module Communication
 * Policy in {@code architecture.md} §1 / {@code development-standards.md}
 * §13.
 */
@ApplicationModule(
    displayName = "Grimo :: Sandbox",
    allowedDependencies = {}
)
package io.github.samzhu.grimo.sandbox;

import org.springframework.modulith.ApplicationModule;
