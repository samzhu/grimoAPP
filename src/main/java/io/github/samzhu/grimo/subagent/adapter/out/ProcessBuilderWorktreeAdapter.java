package io.github.samzhu.grimo.subagent.adapter.out;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.samzhu.grimo.core.domain.GrimoHomePaths;
import io.github.samzhu.grimo.subagent.application.port.out.WorktreePort;
import io.github.samzhu.grimo.subagent.domain.WorktreeException;
import io.github.samzhu.grimo.subagent.domain.WorktreeInfo;

/**
 * Hybrid ProcessBuilder + JGit implementation of {@link WorktreePort}.
 *
 * <p>Native {@code git} CLI (via ProcessBuilder) handles worktree
 * add/remove/list — JGit 7.6.0 lacks these APIs (Eclipse Bug 477475).
 * JGit {@code FileRepository} handles typed operations on already-created
 * worktrees — the same pattern used by JGit's own {@code LinkedWorktreeTest}.
 */
@Component
public class ProcessBuilderWorktreeAdapter implements WorktreePort {

    private static final Logger log = LoggerFactory.getLogger(ProcessBuilderWorktreeAdapter.class);

    private static final String BRANCH_PREFIX = "grimo/task-";

    private final String gitCommand;

    /** Default constructor — uses {@code git} from the system PATH. */
    public ProcessBuilderWorktreeAdapter() {
        this("git");
    }

    /**
     * Constructor with explicit git command path — used by tests to inject
     * a non-existent path for AC-6 fast-fail verification.
     *
     * @param gitCommand path or name of the git binary
     * @throws WorktreeException if the git binary is not found or not executable
     */
    ProcessBuilderWorktreeAdapter(String gitCommand) {
        this.gitCommand = gitCommand;
        verifyGitAvailable();
    }

    @Override
    public WorktreeInfo create(String taskId, Path projectWorkDir, String baseBranch) {
        Path worktreePath = GrimoHomePaths.worktrees().resolve(taskId);
        String branchName = BRANCH_PREFIX + taskId;
        Path projectGitDir = resolveGitDir(projectWorkDir);

        // git -C <projectWorkDir> worktree add -b <branch> <path> <baseBranch>
        runGit(projectWorkDir,
                "worktree", "add",
                "-b", branchName,
                worktreePath.toString(),
                baseBranch);

        log.debug("Created worktree taskId={} at {} on branch {}", taskId, worktreePath, branchName);
        return new WorktreeInfo(taskId, branchName, worktreePath, projectGitDir);
    }

    @Override
    public Git openJGit(WorktreeInfo info) {
        Path metadataDir = resolveWorktreeMetadataDir(info.projectGitDir(), info.checkoutPath());
        try {
            var repo = new FileRepository(metadataDir.toFile());
            return new Git(repo);
        } catch (IOException e) {
            throw new WorktreeException("Failed to open JGit for worktree: " + info.taskId(), e);
        }
    }

    @Override
    public String diff(WorktreeInfo info) {
        // git -C <checkoutPath> diff HEAD
        return runGit(info.checkoutPath(), "diff", "HEAD");
    }

    @Override
    public void remove(WorktreeInfo info) {
        // Derive projectWorkDir from projectGitDir (its parent).
        Path projectWorkDir = info.projectGitDir().getParent();
        runGit(projectWorkDir, "worktree", "remove", "--force", info.checkoutPath().toString());
        log.debug("Removed worktree taskId={} at {}", info.taskId(), info.checkoutPath());
    }

    @Override
    public List<WorktreeInfo> list(Path projectWorkDir) {
        Path projectGitDir = resolveGitDir(projectWorkDir);
        String output = runGit(projectWorkDir, "worktree", "list", "--porcelain");
        return parsePorcelainWorktreeList(output, projectGitDir, projectWorkDir);
    }

    /**
     * Verifies the git CLI is reachable by running {@code git --version}.
     * Called once at construction time (D3 — fast-fail).
     */
    private void verifyGitAvailable() {
        try {
            var pb = new ProcessBuilder(gitCommand, "--version")
                    .redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new WorktreeException("git CLI not found: " + gitCommand + " --version exited with " + exitCode);
            }
        } catch (IOException e) {
            throw new WorktreeException("git CLI not found: " + gitCommand, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorktreeException("git CLI not found: interrupted while checking " + gitCommand, e);
        }
    }

    /**
     * Parses {@code git worktree list --porcelain} output into {@link WorktreeInfo}
     * entries, excluding the main worktree.
     */
    private List<WorktreeInfo> parsePorcelainWorktreeList(
            String output, Path projectGitDir, Path projectWorkDir) {
        var result = new java.util.ArrayList<WorktreeInfo>();
        String[] blocks = output.split("\n\n");

        for (String block : blocks) {
            String[] lines = block.strip().split("\n");
            String worktreePath = null;
            String branchRef = null;

            for (String line : lines) {
                if (line.startsWith("worktree ")) {
                    worktreePath = line.substring("worktree ".length());
                } else if (line.startsWith("branch ")) {
                    branchRef = line.substring("branch ".length());
                }
            }

            if (worktreePath == null || branchRef == null) {
                continue;
            }

            // Skip the main worktree (its path equals the project workdir)
            Path checkoutPath = Path.of(worktreePath);
            try {
                if (checkoutPath.toRealPath().equals(projectWorkDir.toRealPath())) {
                    continue;
                }
            } catch (IOException e) {
                continue;
            }

            String branchName = branchRef.startsWith("refs/heads/")
                    ? branchRef.substring("refs/heads/".length())
                    : branchRef;

            String taskId = branchName.startsWith(BRANCH_PREFIX)
                    ? branchName.substring(BRANCH_PREFIX.length())
                    : checkoutPath.getFileName().toString();

            result.add(new WorktreeInfo(taskId, branchName, checkoutPath, projectGitDir));
        }
        return result;
    }

    private Path resolveGitDir(Path projectWorkDir) {
        Path dotGit = projectWorkDir.resolve(".git");
        if (Files.isDirectory(dotGit)) {
            return dotGit;
        }
        try {
            String content = Files.readString(dotGit).trim();
            if (content.startsWith("gitdir:")) {
                return Path.of(content.substring("gitdir:".length()).trim());
            }
        } catch (IOException e) {
            throw new WorktreeException("Cannot resolve .git directory for: " + projectWorkDir, e);
        }
        throw new WorktreeException("Cannot resolve .git directory for: " + projectWorkDir);
    }

    private Path resolveWorktreeMetadataDir(Path projectGitDir, Path checkoutPath) {
        Path worktreesDir = projectGitDir.resolve("worktrees");
        if (!Files.isDirectory(worktreesDir)) {
            throw new WorktreeException("No worktrees directory found at: " + worktreesDir);
        }
        try (var entries = Files.list(worktreesDir)) {
            for (Path entry : entries.toList()) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                Path gitdirFile = entry.resolve("gitdir");
                if (Files.exists(gitdirFile)) {
                    String gitdirContent = Files.readString(gitdirFile).trim();
                    Path linkedCheckout = Path.of(gitdirContent).getParent();
                    if (linkedCheckout != null
                            && linkedCheckout.toRealPath().equals(checkoutPath.toRealPath())) {
                        return entry;
                    }
                }
            }
        } catch (IOException e) {
            throw new WorktreeException("Failed to scan worktree metadata: " + worktreesDir, e);
        }
        throw new WorktreeException("No worktree metadata found for checkout path: " + checkoutPath);
    }

    /** Runs a git command via ProcessBuilder; throws {@link WorktreeException} on failure. */
    private String runGit(Path workDir, String... args) {
        var command = new java.util.ArrayList<String>();
        command.add(gitCommand);
        command.add("-C");
        command.add(workDir.toString());
        command.addAll(List.of(args));

        try {
            var pb = new ProcessBuilder(command)
                    .redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new WorktreeException(
                        "git command failed (exit %d): %s\nOutput: %s"
                                .formatted(exitCode, String.join(" ", command), output));
            }
            return output;
        } catch (IOException e) {
            throw new WorktreeException("Failed to execute git command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorktreeException("Git command interrupted: " + String.join(" ", command), e);
        }
    }
}
