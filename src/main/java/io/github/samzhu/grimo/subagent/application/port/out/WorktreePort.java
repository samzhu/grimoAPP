package io.github.samzhu.grimo.subagent.application.port.out;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;

import io.github.samzhu.grimo.subagent.domain.WorktreeInfo;

/**
 * Outbound port for git worktree lifecycle management.
 *
 * <p>Hybrid design (S027): native {@code git} CLI via ProcessBuilder
 * handles worktree add/remove/list (JGit 7.6.0 lacks these APIs —
 * Eclipse Bug 477475). JGit {@code FileRepository} handles typed
 * diff/commit/status on already-created worktrees.
 */
public interface WorktreePort {

    /**
     * Creates a new git worktree branched from {@code baseBranch}.
     *
     * <p>Equivalent to:
     * {@code git -C <projectWorkDir> worktree add -b grimo/task-<taskId> <path> <baseBranch>}
     *
     * @param taskId         unique task identifier (used as directory name)
     * @param projectWorkDir path to the project working directory (must be a git repo)
     * @param baseBranch     branch to base the worktree on (e.g. "main")
     * @return info about the created worktree
     */
    WorktreeInfo create(String taskId, Path projectWorkDir, String baseBranch);

    /**
     * Opens the linked worktree as a JGit {@link Git} instance for typed
     * operations (status, diff, commit).
     *
     * <p>Uses {@code FileRepository} pointed at the worktree metadata
     * directory ({@code .git/worktrees/<name>/}), which resolves the
     * checkout path via {@code guessWorkTreeOrFail()}.
     *
     * @param info worktree coordinates returned by {@link #create}
     * @return a JGit Git handle — caller should close when done
     */
    Git openJGit(WorktreeInfo info);

    /**
     * Returns a unified diff of all uncommitted changes in the worktree.
     *
     * <p>Equivalent to: {@code git -C <checkoutPath> diff HEAD}
     *
     * @param info worktree coordinates
     * @return unified diff string (empty string if no changes)
     */
    String diff(WorktreeInfo info);

    /**
     * Removes the worktree checkout directory and cleans up the metadata
     * entry under {@code .git/worktrees/}. The associated branch
     * ({@code grimo/task-<taskId>}) is <strong>not</strong> deleted —
     * callers may retain it for history or delete it separately.
     *
     * <p>Equivalent to: {@code git -C <projectWorkDir> worktree remove --force <checkoutPath>}
     *
     * @param info worktree coordinates
     */
    void remove(WorktreeInfo info);

    /**
     * Lists all linked worktrees for the given project.
     *
     * <p>Parses output of: {@code git -C <projectWorkDir> worktree list --porcelain}
     *
     * @param projectWorkDir path to the project working directory
     * @return list of worktree info (excludes the main worktree)
     */
    List<WorktreeInfo> list(Path projectWorkDir);
}
