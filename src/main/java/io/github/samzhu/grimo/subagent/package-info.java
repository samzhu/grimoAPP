/**
 * Grimo :: Subagent — worktree lifecycle, sandbox execution, diff review.
 *
 * <p>S027 lands {@code WorktreePort} — hybrid ProcessBuilder + JGit
 * worktree management for per-task isolation. Uses {@code core} for
 * {@code GrimoHomePaths.worktrees()}.
 */
@ApplicationModule(
    displayName = "Grimo :: Subagent",
    allowedDependencies = { "core" }
)
package io.github.samzhu.grimo.subagent;

import org.springframework.modulith.ApplicationModule;
