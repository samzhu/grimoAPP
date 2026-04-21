/**
 * Session module events — exposed to other modules via
 * {@code allowedDependencies = { "session::events" }}.
 *
 * <p>Future consumers (e.g. {@code cost} module) can listen to
 * {@link io.github.samzhu.grimo.session.events.TurnRecorded} without
 * depending on internal session types.
 */
@NamedInterface("events")
package io.github.samzhu.grimo.session.events;

import org.springframework.modulith.NamedInterface;
