/**
 * Grimo :: Agent — hosts the main-agent CLI passthrough use case
 * ({@code grimo chat}, S007). Wires user stdin/stdout to a containerised
 * {@code claude-code} process running inside the {@code grimo-runtime}
 * image.
 *
 * <p>Empty in S002. The first concrete type lands with S007.
 *
 * <p>{@code allowedDependencies = {}} starts as the strictest white-list
 * — see Cross-Module Communication Policy in
 * {@code development-standards.md} §13.
 */
@ApplicationModule(
    displayName = "Grimo :: Agent",
    allowedDependencies = {}
)
package io.github.samzhu.grimo.agent;

import org.springframework.modulith.ApplicationModule;
