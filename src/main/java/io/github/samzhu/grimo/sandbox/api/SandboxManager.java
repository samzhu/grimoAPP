package io.github.samzhu.grimo.sandbox.api;

import java.util.List;
import java.util.Optional;
import org.springaicommunity.sandbox.Sandbox;

/**
 * 管理多個並行沙箱的生命週期。每個沙箱以 Docker 容器 ID 識別。
 * 沙箱是長期存活的 CLI agent 隔離層，支援多次 exec() 互動。
 *
 * <p>設計說明：以容器 ID（而非 TaskId）作為 handle，因為同一任務
 * 可能建立多個沙箱（如同時使用不同模型），且沙箱生命週期不一定
 * 與任務生命週期一致。
 */
public interface SandboxManager {

    /**
     * 建立並啟動一個新的沙箱容器。
     *
     * @param config 映像名稱、主機掛載路徑、容器掛載路徑
     * @return 已啟動的 Sandbox 實例（可多次 exec）
     * @throws org.springaicommunity.sandbox.SandboxException 容器啟動失敗
     */
    Sandbox create(SandboxConfig config);

    /**
     * 依容器 ID 查詢存活的沙箱。
     *
     * @param containerId Docker 容器 ID
     * @return 若存活則 Optional.of(sandbox)；否則 Optional.empty()
     */
    Optional<Sandbox> get(String containerId);

    /**
     * 關閉並移除指定容器。主機掛載目錄保留。
     * 對已關閉的 containerId 呼叫為 no-op。
     *
     * @param containerId Docker 容器 ID
     */
    void close(String containerId);

    /**
     * 列出所有存活沙箱的容器 ID。
     *
     * @return 不可變的容器 ID 列表
     */
    List<String> listActive();
}
