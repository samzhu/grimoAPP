/**
 * Grimo :: Subagent — worktree lifecycle, sandbox execution, diff review.
 *
 * <p>S027 lands {@code WorktreePort} — hybrid ProcessBuilder + JGit
 * worktree management for per-task isolation. S028 adds the execution
 * pipeline: {@code DelegateTaskUseCase} orchestrates worktree creation,
 * Docker sandbox, Claude Code YOLO execution, and diff collection.
 */
@ApplicationModule(
    displayName = "Grimo :: Subagent",
    allowedDependencies = { "core", "sandbox :: api", "task :: api", "project :: api", "skills :: api" }
)
package io.github.samzhu.grimo.subagent;

import org.springframework.modulith.ApplicationModule;
