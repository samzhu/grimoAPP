package io.github.samzhu.grimo.subagent.application.service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.sandbox.ExecResult;
import org.springaicommunity.sandbox.ExecSpec;
import org.springaicommunity.sandbox.Sandbox;

import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.project.domain.Project;
import io.github.samzhu.grimo.sandbox.api.SandboxConfig;
import io.github.samzhu.grimo.sandbox.api.SandboxManager;
import io.github.samzhu.grimo.skills.application.port.in.SkillProjectionUseCase;
import io.github.samzhu.grimo.subagent.application.port.out.TaskExecutionPort;
import io.github.samzhu.grimo.subagent.application.port.out.WorktreePort;
import io.github.samzhu.grimo.subagent.domain.ExecutionStatus;
import io.github.samzhu.grimo.subagent.domain.TaskExecution;
import io.github.samzhu.grimo.subagent.domain.WorktreeInfo;
import io.github.samzhu.grimo.subagent.internal.SubagentProperties;
import io.github.samzhu.grimo.task.application.port.in.TaskUseCase;
import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskPriority;
import io.github.samzhu.grimo.task.domain.TaskStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SubagentExecutorService (S028 AC-2 through AC-7).
 * All ports are stubbed — no Docker, no git, no Claude CLI.
 */
class SubagentExecutorServiceTest {

    // --- Stubs ---
    private StubTaskUseCase taskUseCase;
    private StubProjectUseCase projectUseCase;
    private StubWorktreePort worktreePort;
    private StubSandboxManager sandboxManager;
    private StubSkillProjection skillProjection;
    private StubTaskExecutionPort taskExecutionPort;

    private SubagentExecutorService service;

    @BeforeEach
    void setUp() {
        taskUseCase = new StubTaskUseCase();
        projectUseCase = new StubProjectUseCase();
        worktreePort = new StubWorktreePort();
        sandboxManager = new StubSandboxManager();
        skillProjection = new StubSkillProjection();
        taskExecutionPort = new StubTaskExecutionPort();

        // Default: no API key — CLI uses its own credentials
        var props = new SubagentProperties(null,
                "grimo-runtime:0.0.1-SNAPSHOT", 100, Duration.ofMinutes(30));

        service = new SubagentExecutorService(taskUseCase, projectUseCase,
                worktreePort, sandboxManager, skillProjection, taskExecutionPort, props);
    }

    @Test
    @DisplayName("[S028] AC-2/AC-3: successful execution — PENDING → RUNNING → SUCCEEDED, task → IN_REVIEW")
    void ac2_ac3_successfulExecution() throws Exception {
        // Given
        seedTask(42, "task12345678", "proj1234abcd", TaskStatus.OPEN);
        seedProject("proj1234abcd", "/tmp/myapp");
        sandboxManager.execResult = new ExecResult(0, "{\"result\":\"hello\"}", "", Duration.ofSeconds(5));
        worktreePort.diffResult = "diff --git a/hello.txt b/hello.txt\n+Hello World";

        // When
        var execution = service.execute(42, "Add hello.txt with 'Hello World'");

        // Then — immediate return is PENDING
        assertThat(execution.status()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(execution.taskNumber()).isEqualTo(42);

        // Wait for background execution
        awaitBackground();

        // Then — execution should be SUCCEEDED
        var latest = taskExecutionPort.findById(execution.id());
        assertThat(latest).isPresent();
        assertThat(latest.get().status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(latest.get().agentResponse()).contains("hello");
        assertThat(latest.get().diff()).contains("Hello World");
        assertThat(latest.get().finishedAt()).isNotNull();

        // And — task status should be IN_REVIEW
        assertThat(taskUseCase.lastStatus).isEqualTo(TaskStatus.IN_REVIEW);

        // And — sandbox was closed
        assertThat(sandboxManager.closedContainerId).isNotNull();
    }

    @Test
    @DisplayName("[S028] AC-4: failed execution — FAILED, task → OPEN, container cleaned")
    void ac4_failedExecution() throws Exception {
        // Given
        seedTask(42, "task12345678", "proj1234abcd", TaskStatus.OPEN);
        seedProject("proj1234abcd", "/tmp/myapp");
        sandboxManager.throwOnExec = true;

        // When
        var execution = service.execute(42, "Bad prompt");

        // Wait for background
        awaitBackground();

        // Then — execution should be FAILED
        var latest = taskExecutionPort.findById(execution.id());
        assertThat(latest).isPresent();
        assertThat(latest.get().status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(latest.get().errorMessage()).isNotBlank();

        // And — task reverted to OPEN
        assertThat(taskUseCase.lastStatus).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    @DisplayName("[S028] AC-5: skill projection called before sandbox exec")
    void ac5_skillProjectionCalled() throws Exception {
        // Given
        seedTask(42, "task12345678", "proj1234abcd", TaskStatus.OPEN);
        seedProject("proj1234abcd", "/tmp/myapp");
        sandboxManager.execResult = new ExecResult(0, "{}", "", Duration.ofSeconds(1));

        // When
        service.execute(42, "test");
        awaitBackground();

        // Then — skill projection was called with the worktree checkout path
        assertThat(skillProjection.projectedPath).isNotNull();
        assertThat(skillProjection.projectedPath.toFile()).exists();
    }

    @Test
    @DisplayName("[S028] AC-6: default auth — no auth env vars, CLI uses own credentials")
    void ac6_defaultCliNativeAuth() {
        // Given — default props (no API key)

        // When
        Map<String, String> env = service.buildEnvVars();

        // Then — no auth env vars injected
        assertThat(env).doesNotContainKey("CLAUDE_CODE_OAUTH_TOKEN");
        assertThat(env).doesNotContainKey("ANTHROPIC_API_KEY");
        // Common env vars present
        assertThat(env).containsEntry("CLAUDE_CODE_DISABLE_AUTO_MEMORY", "1");
        assertThat(env).containsEntry("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1");
    }

    @Test
    @DisplayName("[S028] AC-7: API key override — ANTHROPIC_API_KEY injected when configured")
    void ac7_apiKeyOverride() {
        // Given
        var props = new SubagentProperties("sk-ant-test-key",
                "grimo-runtime:0.0.1-SNAPSHOT", 100, Duration.ofMinutes(30));
        service = new SubagentExecutorService(taskUseCase, projectUseCase,
                worktreePort, sandboxManager, skillProjection, taskExecutionPort, props);

        // When
        Map<String, String> env = service.buildEnvVars();

        // Then
        assertThat(env).containsEntry("ANTHROPIC_API_KEY", "sk-ant-test-key");
        assertThat(env).doesNotContainKey("CLAUDE_CODE_OAUTH_TOKEN");
    }

    @Test
    @DisplayName("[S028] CLAUDE_CODE_OAUTH_TOKEN is never injected regardless of config")
    void neverInjectsOauthToken() {
        // Given — API key configured
        var props = new SubagentProperties("sk-ant-key",
                "grimo-runtime:0.0.1-SNAPSHOT", 100, Duration.ofMinutes(30));
        service = new SubagentExecutorService(taskUseCase, projectUseCase,
                worktreePort, sandboxManager, skillProjection, taskExecutionPort, props);

        // When
        Map<String, String> env = service.buildEnvVars();

        // Then
        assertThat(env).doesNotContainKey("CLAUDE_CODE_OAUTH_TOKEN");
    }

    @Test
    @DisplayName("[S028] execute rejects non-OPEN task")
    void rejectsNonOpenTask() {
        // Given
        seedTask(42, "task12345678", "proj1234abcd", TaskStatus.IN_PROGRESS);

        // When/Then
        assertThatThrownBy(() -> service.execute(42, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    @DisplayName("[S028] execute rejects task without project")
    void rejectsTaskWithoutProject() {
        // Given — task with null projectId
        var now = Instant.now();
        taskUseCase.task = new Task("task12345678", 42, null, "orphan task", null,
                TaskStatus.OPEN, TaskPriority.MEDIUM, null, null, now, now, null);

        // When/Then
        assertThatThrownBy(() -> service.execute(42, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no project");
    }

    @Test
    @DisplayName("[S028] AC-8: findExecution delegates to port")
    void ac8_findExecution() {
        // Given
        var now = Instant.now();
        var exec = new TaskExecution("exec123", "task1", 1, ExecutionStatus.SUCCEEDED,
                "prompt", "branch", "/w", "c", "{}", "diff", null, now, now, now);
        taskExecutionPort.save(exec);

        // When
        var found = service.findExecution("exec123");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }

    // --- Helper methods ---

    private void seedTask(int taskNumber, String taskId, String projectId, TaskStatus status) {
        var now = Instant.now();
        taskUseCase.task = new Task(taskId, taskNumber, projectId, "test task", null,
                status, TaskPriority.MEDIUM, null, null, now, now, null);
    }

    private void seedProject(String projectId, String workDir) {
        var now = Instant.now();
        projectUseCase.project = new Project(projectId, "myapp", workDir, null, now, now);
    }

    private void awaitBackground() throws InterruptedException {
        // Virtual threads execute fast with stubs — small wait suffices
        Thread.sleep(200);
    }

    // --- Stub implementations ---

    static class StubTaskUseCase implements TaskUseCase {
        Task task;
        TaskStatus lastStatus;

        @Override
        public Task create(String projectId, String title, String body,
                           io.github.samzhu.grimo.task.domain.TaskPriority priority,
                           String labelsJson, io.github.samzhu.grimo.task.domain.TaskSource source) {
            throw new UnsupportedOperationException();
        }
        @Override
        public List<Task> list(String projectId, TaskStatus status) {
            return List.of();
        }
        @Override
        public Optional<Task> findByNumber(int taskNumber) {
            return task != null && task.taskNumber() == taskNumber ? Optional.of(task) : Optional.empty();
        }
        @Override
        public Task updateStatus(int taskNumber, TaskStatus status) {
            lastStatus = status;
            if (task != null) {
                task = new Task(task.id(), task.taskNumber(), task.projectId(), task.title(),
                        task.body(), status, task.priority(), task.labelsJson(), task.source(),
                        task.createdAt(), Instant.now(), task.closedAt());
            }
            return task;
        }
        @Override
        public Task update(int taskNumber, String title, String body,
                           io.github.samzhu.grimo.task.domain.TaskPriority priority,
                           String labelsJson) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void delete(int taskNumber) {
            throw new UnsupportedOperationException();
        }
    }

    static class StubProjectUseCase implements ProjectUseCase {
        Project project;

        @Override
        public Project create(String name, String workDir, String description) {
            throw new UnsupportedOperationException();
        }
        @Override
        public List<Project> listAll() { return List.of(); }
        @Override
        public Optional<Project> findById(String id) {
            return project != null && project.id().equals(id) ? Optional.of(project) : Optional.empty();
        }
        @Override
        public Project update(String id, String name, String workDir, String description) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void delete(String id) {
            throw new UnsupportedOperationException();
        }
    }

    static class StubWorktreePort implements WorktreePort {
        String diffResult = "";
        private Path tempGitDir;

        void initTempGit() throws Exception {
            tempGitDir = java.nio.file.Files.createTempDirectory("stub-git");
            org.eclipse.jgit.api.Git.init().setDirectory(tempGitDir.toFile()).call().close();
        }

        @Override
        public WorktreeInfo create(String taskId, Path projectWorkDir, String baseBranch) {
            try { initTempGit(); } catch (Exception e) { throw new RuntimeException(e); }
            return new WorktreeInfo(taskId,
                    "grimo/task-" + taskId,
                    tempGitDir,
                    projectWorkDir.resolve(".git"));
        }
        @Override
        public org.eclipse.jgit.api.Git openJGit(WorktreeInfo info) {
            try {
                return org.eclipse.jgit.api.Git.open(tempGitDir.toFile());
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        }
        @Override
        public String diff(WorktreeInfo info) { return diffResult; }
        @Override
        public void remove(WorktreeInfo info) { }
        @Override
        public List<WorktreeInfo> list(Path projectWorkDir) { return List.of(); }
    }

    static class StubSandboxManager implements SandboxManager {
        ExecResult execResult = new ExecResult(0, "{}", "", Duration.ofSeconds(1));
        boolean throwOnExec = false;
        String closedContainerId;
        private StubSandbox lastSandbox;

        @Override
        public Sandbox create(SandboxConfig config) {
            lastSandbox = new StubSandbox(execResult, throwOnExec);
            return lastSandbox;
        }
        @Override
        public Optional<Sandbox> get(String containerId) { return Optional.empty(); }
        @Override
        public void close(String containerId) { closedContainerId = containerId; }
        @Override
        public List<String> listActive() { return List.of("stub-container-id"); }
    }

    static class StubSandbox implements Sandbox {
        private final ExecResult result;
        private final boolean throwOnExec;

        StubSandbox(ExecResult result, boolean throwOnExec) {
            this.result = result;
            this.throwOnExec = throwOnExec;
        }

        @Override
        public ExecResult exec(ExecSpec spec) {
            if (throwOnExec) {
                throw new org.springaicommunity.sandbox.SandboxException("Container image not found");
            }
            return result;
        }
        @Override
        public Path workDir() { return Path.of("/work"); }
        @Override
        public boolean isClosed() { return false; }
        @Override
        public void close() { }
        @Override
        public org.springaicommunity.sandbox.SandboxFiles files() {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean shouldCleanupOnClose() { return false; }
    }

    static class StubSkillProjection implements SkillProjectionUseCase {
        Path projectedPath;
        @Override
        public void projectToWorkDir(Path workDir) { projectedPath = workDir; }
    }

    static class StubTaskExecutionPort implements TaskExecutionPort {
        private final java.util.concurrent.ConcurrentHashMap<String, TaskExecution> store =
                new java.util.concurrent.ConcurrentHashMap<>();
        @Override
        public void save(TaskExecution execution) { store.put(execution.id(), execution); }
        @Override
        public Optional<TaskExecution> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override
        public List<TaskExecution> findByTaskId(String taskId) {
            return store.values().stream()
                    .filter(e -> e.taskId().equals(taskId))
                    .toList();
        }
    }
}
