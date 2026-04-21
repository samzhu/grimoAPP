/**
 * Grimo :: Task — generic work item management (S018).
 * Tasks can belong to a project or be Grimo-level (no project).
 */
@ApplicationModule(
    displayName = "Grimo :: Task",
    allowedDependencies = { "core", "project::api" }
)
package io.github.samzhu.grimo.task;

import org.springframework.modulith.ApplicationModule;
