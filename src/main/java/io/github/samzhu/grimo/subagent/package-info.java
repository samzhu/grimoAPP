/**
 * Grimo :: Subagent — hosts {@code DelegateTaskUseCase} (S008–S010):
 * structured task delegation, worktree-mounted sandbox spawn, in-sandbox
 * YOLO CLI execution, and diff review handoff back to the user.
 *
 * <p>Empty in S002. The first concrete type lands with S008.
 *
 * <p>{@code allowedDependencies = {}} starts as the strictest white-list
 * — see Cross-Module Communication Policy in
 * {@code development-standards.md} §13.
 */
@ApplicationModule(
    displayName = "Grimo :: Subagent",
    allowedDependencies = {}
)
package io.github.samzhu.grimo.subagent;

import org.springframework.modulith.ApplicationModule;
