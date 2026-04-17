package io.github.samzhu.grimo.sandbox.internal;

import io.github.samzhu.grimo.sandbox.api.SandboxConfig;
import io.github.samzhu.grimo.sandbox.api.SandboxManager;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springaicommunity.sandbox.Sandbox;
import org.springframework.stereotype.Service;

/**
 * {@link SandboxManager} 實作，以 {@link ConcurrentHashMap} 追蹤存活的
 * {@link BindMountSandbox} 實例。
 *
 * <p>設計說明：ConcurrentHashMap 提供並行安全的 O(1) 查詢。S010 加入排程策略
 * （max-concurrent semaphore、shutdown 有序關閉）時擴充此結構。見 spec S003 §2.2。
 */
@Service
class TestcontainersSandboxManager implements SandboxManager {

    private final ConcurrentHashMap<String, BindMountSandbox> registry =
            new ConcurrentHashMap<>();

    @Override
    public Sandbox create(SandboxConfig config) {
        var sandbox = new BindMountSandbox(config);
        registry.put(sandbox.getContainerId(), sandbox);
        return sandbox;
    }

    @Override
    public Optional<Sandbox> get(String containerId) {
        return Optional.ofNullable(registry.get(containerId));
    }

    @Override
    public void close(String containerId) {
        var sandbox = registry.remove(containerId);
        if (sandbox != null) {
            sandbox.close();
        }
    }

    @Override
    public List<String> listActive() {
        return List.copyOf(registry.keySet());
    }
}
