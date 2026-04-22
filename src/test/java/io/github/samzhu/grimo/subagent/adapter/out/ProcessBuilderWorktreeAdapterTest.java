package io.github.samzhu.grimo.subagent.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.samzhu.grimo.subagent.domain.WorktreeException;
import io.github.samzhu.grimo.subagent.domain.WorktreeInfo;

/**
 * Unit tests for {@link ProcessBuilderWorktreeAdapter} — requires a real
 * {@code git} binary on the host (no Docker). Tests create a throwaway
 * git repo in {@code @TempDir} to exercise the hybrid ProcessBuilder +
 * JGit pattern.
 */
class ProcessBuilderWorktreeAdapterTest {

    @TempDir
    Path tempDir;

    /** A bare-minimum git repo with one commit, used as the "project". */
    private Path projectDir;

    /** Worktrees land here instead of ~/.grimo/worktrees/ during tests. */
    private Path worktreesRoot;

    private ProcessBuilderWorktreeAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        // Given — a git project with at least one commit
        projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);

        var pb1 = new ProcessBuilder("git", "init", "--initial-branch=main")
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        run(pb1);

        // Configure git user for commits
        run(new ProcessBuilder("git", "config", "user.email", "test@grimo.dev")
                .directory(projectDir.toFile()).redirectErrorStream(true));
        run(new ProcessBuilder("git", "config", "user.name", "Grimo Test")
                .directory(projectDir.toFile()).redirectErrorStream(true));

        // Create initial commit
        Path readme = projectDir.resolve("README.md");
        Files.writeString(readme, "# Test Project\n");
        run(new ProcessBuilder("git", "add", ".")
                .directory(projectDir.toFile()).redirectErrorStream(true));
        run(new ProcessBuilder("git", "commit", "-m", "initial commit")
                .directory(projectDir.toFile()).redirectErrorStream(true));

        // Override grimo.home so worktrees go into tempDir
        worktreesRoot = tempDir.resolve("grimo-home").resolve("worktrees");
        Files.createDirectories(worktreesRoot);
        System.setProperty("grimo.home", tempDir.resolve("grimo-home").toString());

        adapter = new ProcessBuilderWorktreeAdapter();
    }

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("grimo.home");
    }

    @Test
    @DisplayName("S027 AC-1: create() builds valid worktree with correct branch")
    void ac1_createBuildsValidWorktreeWithCorrectBranch() throws Exception {
        // When
        WorktreeInfo info = adapter.create("task123abc", projectDir, "main");

        // Then — worktree directory exists
        assertThat(info.checkoutPath()).exists();
        assertThat(info.checkoutPath()).isDirectory();

        // And — branch name follows convention
        assertThat(info.branchName()).isEqualTo("grimo/task-task123abc");

        // And — taskId is preserved
        assertThat(info.taskId()).isEqualTo("task123abc");

        // And — it is a valid git worktree (has .git file pointing to main repo)
        Path gitFile = info.checkoutPath().resolve(".git");
        assertThat(gitFile).exists();
        String gitFileContent = Files.readString(gitFile);
        assertThat(gitFileContent).contains("gitdir:");

        // And — worktree content matches main branch (README.md exists)
        assertThat(info.checkoutPath().resolve("README.md")).exists();

        // And — branch was created in the main repo
        var result = run(new ProcessBuilder("git", "branch", "--list", "grimo/task-task123abc")
                .directory(projectDir.toFile()).redirectErrorStream(true));
        assertThat(result.trim()).contains("grimo/task-task123abc");
    }

    @Test
    @DisplayName("S027 AC-2: openJGit() returns working Git object for worktree")
    void ac2_openJGitReturnsWorkingGitObjectForWorktree() throws Exception {
        // Given — a created worktree
        WorktreeInfo info = adapter.create("task123abc", projectDir, "main");

        // When
        try (Git git = adapter.openJGit(info)) {

            // Then — status is clean
            var status = git.status().call();
            assertThat(status.isClean()).isTrue();

            // And — worktree path points to correct location
            Path workTree = git.getRepository().getWorkTree().toPath();
            assertThat(workTree.toRealPath()).isEqualTo(info.checkoutPath().toRealPath());
        }
    }

    @Test
    @DisplayName("S027 AC-3: diff() returns unified diff containing changed file")
    void ac3_diffReturnsUnifiedDiffContainingChangedFile() throws Exception {
        // Given — a worktree with a new file hello.txt staged
        WorktreeInfo info = adapter.create("taskdiff01", projectDir, "main");
        Files.writeString(info.checkoutPath().resolve("hello.txt"), "Hello Grimo!\n");
        run(new ProcessBuilder("git", "add", "hello.txt")
                .directory(info.checkoutPath().toFile()).redirectErrorStream(true));

        // When
        String diff = adapter.diff(info);

        // Then — the diff contains hello.txt
        assertThat(diff).contains("hello.txt");
    }

    @Test
    @DisplayName("S027 AC-4: remove() deletes worktree directory and cleans .git/worktrees/")
    void ac4_removeDeletesWorktreeDirectoryAndCleansGitWorktrees() throws Exception {
        // Given — a created worktree
        WorktreeInfo info = adapter.create("taskrm001", projectDir, "main");
        assertThat(info.checkoutPath()).exists();

        // When
        adapter.remove(info);

        // Then — worktree directory no longer exists
        assertThat(info.checkoutPath()).doesNotExist();

        // And — main repo's .git/worktrees/ entry is cleaned
        Path metadataEntry = info.projectGitDir().resolve("worktrees");
        if (Files.exists(metadataEntry)) {
            // If worktrees dir still exists, it should not contain our entry
            try (var entries = Files.list(metadataEntry)) {
                boolean hasOurEntry = entries.anyMatch(p -> {
                    try {
                        Path gitdirFile = p.resolve("gitdir");
                        if (Files.exists(gitdirFile)) {
                            String content = Files.readString(gitdirFile).trim();
                            Path linkedCheckout = Path.of(content).getParent();
                            return linkedCheckout != null
                                    && linkedCheckout.toAbsolutePath().toString()
                                            .contains("taskrm001");
                        }
                    } catch (Exception ignored) { }
                    return false;
                });
                assertThat(hasOurEntry).isFalse();
            }
        }
    }

    @Test
    @DisplayName("S027 AC-5: list() returns all worktrees with correct info")
    void ac5_listReturnsAllWorktreesWithCorrectInfo() throws Exception {
        // Given — 2 worktrees for the same project
        adapter.create("tasklistA1", projectDir, "main");
        adapter.create("tasklistB2", projectDir, "main");

        // When
        var worktrees = adapter.list(projectDir);

        // Then — returns 2 entries
        assertThat(worktrees).hasSize(2);

        // And — each has correct checkoutPath and branchName
        assertThat(worktrees)
                .extracting(WorktreeInfo::branchName)
                .containsExactlyInAnyOrder(
                        "grimo/task-tasklistA1",
                        "grimo/task-tasklistB2");
        assertThat(worktrees)
                .extracting(w -> w.checkoutPath().getFileName().toString())
                .containsExactlyInAnyOrder("tasklistA1", "tasklistB2");
    }

    @Test
    @DisplayName("S027 AC-6: constructor throws WorktreeException when git CLI not found")
    void ac6_constructorThrowsWhenGitCliNotFound() {
        // Given — a git command path that does not exist
        // When / Then
        assertThatThrownBy(() -> new ProcessBuilderWorktreeAdapter("/nonexistent/git"))
                .isInstanceOf(WorktreeException.class)
                .hasMessageContaining("git CLI not found");
    }

    /** Runs a process and returns stdout as a string; throws on non-zero exit. */
    private String run(ProcessBuilder pb) throws IOException, InterruptedException {
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed (exit " + exitCode + "): " + output);
        }
        return output;
    }
}
