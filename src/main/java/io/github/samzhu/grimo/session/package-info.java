/**
 * Grimo :: Session — event-sourced session memory (S017).
 *
 * <p>{@code RecordingAgentSession} decorator intercepts
 * {@code AgentSession.prompt()}, publishing {@code TurnRecorded} events
 * that are persisted asynchronously to an H2 event store
 * ({@code grimo_session_event}) with a projection table
 * ({@code grimo_session}).
 *
 * <p>Provider-agnostic: {@code ProviderMetadataExtractor} SPI
 * dynamically identifies the CLI provider and extracts token metadata.
 * Adding a new provider requires only one class.
 */
@ApplicationModule(
    displayName = "Grimo :: Session",
    allowedDependencies = { "core" }
)
package io.github.samzhu.grimo.session;

import org.springframework.modulith.ApplicationModule;
