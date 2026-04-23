package io.github.samzhu.grimo.subagent.application.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.sandbox.ExecSpec;
import org.springaicommunity.sandbox.Sandbox;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.core.domain.NanoIds;
import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.sandbox.api.SandboxConfig;
import io.github.samzhu.grimo.sandbox.api.SandboxManager;
import io.github.samzhu.grimo.skills.application.port.in.SkillProjectionUseCase;
import io.github.samzhu.grimo.subagent.application.port.in.CredentialResolverUseCase;
import io.github.samzhu.grimo.subagent.application.port.in.DelegateTaskUseCase;
import io.github.samzhu.grimo.subagent.application.port.out.TaskExecutionPort;
import io.github.samzhu.grimo.subagent.application.port.out.WorktreePort;
import io.github.samzhu.grimo.subagent.domain.ExecutionStatus;
import io.github.samzhu.grimo.subagent.domain.TaskExecution;
import io.github.samzhu.grimo.subagent.domain.WorktreeInfo;
import io.github.samzhu.grimo.subagent.internal.SubagentProperties;
import io.github.samzhu.grimo.task.application.port.in.TaskUseCase;
import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskStatus;

/**
 * Orchestrates subagent execution: task validation → worktree creation →
 * skill projection → Docker sandbox → Claude Code YOLO → diff collection
 * → status updates. Background execution on virtual threads (S028).
 */
@Service
class SubagentExecutorService implements DelegateTaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubagentExecutorService.class);

    private final TaskUseCase taskUseCase;
    private final ProjectUseCase projectUseCase;
    private final WorktreePort worktreePort;
    private final SandboxManager sandboxManager;
    private final SkillProjectionUseCase skillProjection;
    private final TaskExecutionPort taskExecutionPort;
    private final CredentialResolverUseCase credentialResolver;
    private final SubagentProperties props;
    private final ExecutorService executor;

    SubagentExecutorService(TaskUseCase taskUseCase,
                            ProjectUseCase projectUseCase,
                            WorktreePort worktreePort,
                            SandboxManager sandboxManager,
                            SkillProjectionUseCase skillProjection,
                            TaskExecutionPort taskExecutionPort,
                            CredentialResolverUseCase credentialResolver,
                            SubagentProperties props) {
        this.taskUseCase = taskUseCase;
        this.projectUseCase = projectUseCase;
        this.worktreePort = worktreePort;
        this.sandboxManager = sandboxManager;
        this.skillProjection = skillProjection;
        this.taskExecutionPort = taskExecutionPort;
        this.credentialResolver = credentialResolver;
        this.props = props;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public TaskExecution execute(int taskNumber, String prompt) {
        // 1. Validate task exists and is OPEN
        Task task = taskUseCase.findByNumber(taskNumber)
                .orElseThrow(() -> new IllegalArgumentException("Task #" + taskNumber + " not found"));
        if (task.status() != TaskStatus.OPEN) {
            throw new IllegalStateException("Task #" + taskNumber + " is " + task.status() + ", expected OPEN");
        }

        // 2. Resolve project workDir
        String projectId = task.projectId();
        if (projectId == null) {
            throw new IllegalStateException("Task #" + taskNumber + " has no project — cannot create worktree");
        }
        var project = projectUseCase.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project " + projectId + " not found"));

        // 3. Create PENDING execution
        var execution = new TaskExecution(
                NanoIds.compact(), task.id(), taskNumber,
                ExecutionStatus.PENDING, prompt,
                null, null, null, null, null, null, null, null, Instant.now());
        taskExecutionPort.save(execution);

        // 4. Transition task to IN_PROGRESS
        taskUseCase.updateStatus(taskNumber, TaskStatus.IN_PROGRESS);

        // 5. Spawn background execution
        String executionId = execution.id();
        Path projectWorkDir = Path.of(project.workDir());
        executor.submit(() -> executeInBackground(executionId, task.id(), taskNumber,
                prompt, projectWorkDir));

        return execution;
    }

    @Override
    public Optional<TaskExecution> findExecution(String executionId) {
        return taskExecutionPort.findById(executionId);
    }

    @Override
    public List<TaskExecution> listExecutions(int taskNumber) {
        Task task = taskUseCase.findByNumber(taskNumber)
                .orElseThrow(() -> new IllegalArgumentException("Task #" + taskNumber + " not found"));
        return taskExecutionPort.findByTaskId(task.id());
    }

    private void executeInBackground(String executionId, String taskId, int taskNumber,
                                     String prompt, Path projectWorkDir) {
        WorktreeInfo worktreeInfo = null;
        String containerId = null;
        try {
            // 7. Create worktree
            worktreeInfo = worktreePort.create(taskId, projectWorkDir, "main");
            log.info("S028: worktree created at {}", worktreeInfo.checkoutPath());

            // 8. Project skills to worktree
            skillProjection.projectToWorkDir(worktreeInfo.checkoutPath());

            // 9. Create sandbox with worktree mounted at /work
            var sandboxConfig = new SandboxConfig(
                    props.image(), worktreeInfo.checkoutPath(), "/work");
            Sandbox sandbox = sandboxManager.create(sandboxConfig);
            containerId = sandboxManager.listActive().stream()
                    .reduce((first, second) -> second)
                    .orElse("unknown");

            // 10. Update execution to RUNNING
            var running = taskExecutionPort.findById(executionId).orElseThrow();
            running = running.withRunning(containerId,
                    worktreeInfo.branchName(),
                    worktreeInfo.checkoutPath().toString());
            taskExecutionPort.save(running);

            // 11-12. Build env vars and execute claude CLI
            // Note: --dangerously-skip-permissions is blocked for root (container default).
            // Use --allowedTools whitelist instead — security is guaranteed by Docker sandbox.
            Map<String, String> envVars = buildEnvVars();
            ExecSpec execSpec = ExecSpec.builder()
                    .command("claude", "-p", prompt,
                            "--allowedTools", "Bash", "Edit", "Write", "Read", "Glob", "Grep",
                            "--max-turns", String.valueOf(props.maxTurns()),
                            "--output-format", "json")
                    .env(envVars)
                    .timeout(props.timeout())
                    .build();
            var result = sandbox.exec(execSpec);
            log.info("S028: claude exec completed, exitCode={}", result.exitCode());

            // 13. Stage all changes (including new files) then collect diff
            try (var git = worktreePort.openJGit(worktreeInfo)) {
                git.add().addFilepattern(".").call();
            }
            String diff = worktreePort.diff(worktreeInfo);

            // 14-16. Update execution to SUCCEEDED, task to IN_REVIEW
            var succeeded = running.withSucceeded(result.stdout(), diff);
            taskExecutionPort.save(succeeded);
            taskUseCase.updateStatus(taskNumber, TaskStatus.IN_REVIEW);

            // 17. Close sandbox (container removed, worktree kept for S029 review)
            sandboxManager.close(containerId);
            log.info("S028: execution {} succeeded", executionId);

        } catch (Exception e) {
            log.error("S028: execution {} failed", executionId, e);
            // Update execution to FAILED
            taskExecutionPort.findById(executionId).ifPresent(exec -> {
                var failed = exec.withFailed(e.getMessage());
                taskExecutionPort.save(failed);
            });
            // Revert task to OPEN
            taskUseCase.updateStatus(taskNumber, TaskStatus.OPEN);
            // Cleanup sandbox
            if (containerId != null) {
                try { sandboxManager.close(containerId); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * Builds environment variables for Claude Code execution (S028 D2, S030 D3).
     *
     * <p>Auth priority: API Key > Credential Pool > CLI native.
     * <ol>
     *   <li>If {@code grimo.subagent.api-key} is configured → {@code ANTHROPIC_API_KEY}</li>
     *   <li>If credential pool has a matching credential → inject based on type
     *       ({@code oauth_token} → {@code CLAUDE_CODE_OAUTH_TOKEN},
     *        {@code api_key} → {@code ANTHROPIC_API_KEY})</li>
     *   <li>Otherwise → no auth env var (CLI native credentials fallback)</li>
     * </ol>
     */
    Map<String, String> buildEnvVars() {
        var env = new HashMap<String, String>();

        // Auth priority: API Key > Credential Pool > CLI native (S030 D3)
        String apiKey = props.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            env.put("ANTHROPIC_API_KEY", apiKey);
        } else {
            credentialResolver.resolve("claude").ifPresent(cred -> {
                switch (cred.credentialType()) {
                    case "oauth_token" -> env.put("CLAUDE_CODE_OAUTH_TOKEN", cred.secretValue());
                    case "api_key"     -> env.put("ANTHROPIC_API_KEY", cred.secretValue());
                }
            });
        }

        // Common env vars for subagent (D2)
        env.put("CLAUDE_CODE_DISABLE_AUTO_MEMORY", "1");
        env.put("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1");
        // Note: CLAUDE_CODE_DISABLE_CLAUDE_MDS is intentionally NOT set —
        // subagent should read worktree's CLAUDE.md for project context (D2)

        return env;
    }
}
