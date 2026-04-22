package io.github.samzhu.grimo.subagent.domain;

import java.nio.file.Path;

/**
 * Value object carrying the essential coordinates of a git worktree
 * created for a subagent task.
 *
 * @param taskId        NanoId of the owning task (e.g. "k7m3p2q9r4s1")
 * @param branchName    branch created for this worktree (e.g. "grimo/task-k7m3p2q9r4s1")
 * @param checkoutPath  filesystem path to the worktree checkout (e.g. ~/.grimo/worktrees/k7m3p2q9r4s1/)
 * @param projectGitDir path to the main repository's .git/ directory
 */
public record WorktreeInfo(
    String taskId,
    String branchName,
    Path checkoutPath,
    Path projectGitDir
) {}
