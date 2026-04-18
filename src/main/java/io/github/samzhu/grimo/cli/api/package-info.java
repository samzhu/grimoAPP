/**
 * Public API of the CLI module — exposed to sibling modules via
 * {@code allowedDependencies = { "cli :: api" }}.
 *
 * <p>Design note: this named interface lets {@code agent} (S007) and
 * {@code subagent} (S010) consume {@link ContainerizedAgentModelFactory}
 * without seeing internal implementation details.
 */
@NamedInterface("api")
package io.github.samzhu.grimo.cli.api;

import org.springframework.modulith.NamedInterface;
