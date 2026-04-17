package io.github.samzhu.grimo.sandbox.internal;

import io.github.samzhu.grimo.sandbox.api.SandboxConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springaicommunity.sandbox.ExecResult;
import org.springaicommunity.sandbox.ExecSpec;
import org.springaicommunity.sandbox.Sandbox;
import org.springaicommunity.sandbox.SandboxException;
import org.springaicommunity.sandbox.SandboxFiles;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

/**
 * {@link Sandbox} 實作，使用 Testcontainers {@link GenericContainer} +
 * {@code withFileSystemBind} 將主機目錄 bind-mount 至容器 {@code /work}。
 *
 * <p>設計說明：不使用 {@code DockerSandbox}（0.9.1 建構子啟動容器，無法在
 * {@code start()} 前注入 bind-mount）。自行包裝 {@code GenericContainer}
 * 只需約 30 行 exec 委派。見 spec S003 §2.1 D2。
 */
class BindMountSandbox implements Sandbox {

    private static final String SHELL_COMMAND_SENTINEL = "__SHELL_COMMAND__";

    private final GenericContainer<?> container;
    private final SandboxConfig config;
    private final HostMountedSandboxFiles sandboxFiles;
    private volatile boolean closed = false;

    BindMountSandbox(SandboxConfig config) {
        this.config = config;
        this.container = new GenericContainer<>(config.imageName())
                .withFileSystemBind(
                        config.hostMountPath().toString(),
                        config.containerMountPath(),
                        BindMode.READ_WRITE)
                .withCommand("sleep", "infinity");
        this.container.start();
        this.sandboxFiles = new HostMountedSandboxFiles(config.hostMountPath(), this);
    }

    @Override
    public ExecResult exec(ExecSpec spec) {
        if (closed) {
            throw new SandboxException("Sandbox is closed");
        }
        var start = Instant.now();
        try {
            String[] cmd = buildCommand(spec.command(), spec.env());
            var result = container.execInContainer(cmd);
            var duration = Duration.between(start, Instant.now());
            return new ExecResult(
                    result.getExitCode(),
                    result.getStdout(),
                    result.getStderr(),
                    duration);
        } catch (IOException | InterruptedException e) {
            throw new SandboxException("exec failed", e);
        }
    }

    @Override
    public Path workDir() {
        return Path.of(config.containerMountPath());
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            container.stop();
        }
    }

    @Override
    public SandboxFiles files() {
        return sandboxFiles;
    }

    @Override
    public boolean shouldCleanupOnClose() {
        // bind-mount 的主機目錄由呼叫者管理，sandbox 不自動清理
        return false;
    }

    /** 取得 Docker 容器 ID，供 {@code SandboxManager} 追蹤使用。 */
    String getContainerId() {
        return container.getContainerId();
    }

    /**
     * 將 ExecSpec 的 command + env 轉為 execInContainer 可用的字串陣列。
     * 若有環境變數，包裝為 bash -c 腳本以注入 export 指令。
     * 若 command 含 __SHELL_COMMAND__ sentinel，改寫為 bash -c。
     */
    private String[] buildCommand(List<String> command, Map<String, String> env) {
        // 處理 shellCommand() sentinel
        if (command.size() == 2 && SHELL_COMMAND_SENTINEL.equals(command.getFirst())) {
            String shellCmd = command.get(1);
            if (env.isEmpty()) {
                return new String[]{"bash", "-c", shellCmd};
            }
            return new String[]{"bash", "-c", buildEnvExports(env) + shellCmd};
        }

        if (env.isEmpty()) {
            return command.toArray(String[]::new);
        }

        // 有環境變數：包裝為 bash -c "export ...; exec 'arg1' 'arg2' ..."
        var sb = new StringBuilder(buildEnvExports(env));
        sb.append("exec");
        for (var arg : command) {
            sb.append(" '").append(arg.replace("'", "'\\''")).append("'");
        }
        return new String[]{"bash", "-c", sb.toString()};
    }

    private String buildEnvExports(Map<String, String> env) {
        var sb = new StringBuilder();
        env.forEach((k, v) ->
                sb.append("export ").append(k).append("='")
                        .append(v.replace("'", "'\\''"))
                        .append("'; "));
        return sb.toString();
    }
}
