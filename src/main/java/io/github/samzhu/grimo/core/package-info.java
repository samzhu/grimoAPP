/**
 * Grimo :: Core — shared domain primitives (records, enums, path
 * utilities) consumed by every other module.
 *
 * <p>Marked {@link org.springframework.modulith.ApplicationModule.Type#OPEN}
 * because every sibling module references core types directly; consumers
 * do not need to declare core in their {@code allowedDependencies}.
 * OPEN also excludes core from Modulith's cycle-detection pass, which is
 * the correct semantics for a shared-kernel module that everyone depends
 * on (S002 §2 D3).
 */
@ApplicationModule(
    displayName = "Grimo :: Core",
    type = ApplicationModule.Type.OPEN
)
package io.github.samzhu.grimo.core;

import org.springframework.modulith.ApplicationModule;
