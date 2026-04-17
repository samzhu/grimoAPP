package io.github.samzhu.grimo.sandbox.api;

import java.nio.file.Path;

/**
 * 沙箱建立參數。映像名稱由呼叫者決定（S003 測試用 alpine:3.21，
 * S004 落地後改用 grimo-runtime）。
 *
 * @param imageName         Docker 映像名稱（如 "alpine:3.21"）
 * @param hostMountPath     主機端掛載目錄（必須已存在）
 * @param containerMountPath 容器內掛載路徑（慣例 "/work"）
 */
public record SandboxConfig(
    String imageName,
    Path hostMountPath,
    String containerMountPath
) {
    public SandboxConfig {
        if (imageName == null || imageName.isBlank()) {
            throw new IllegalArgumentException("imageName must not be blank");
        }
        if (hostMountPath == null) {
            throw new IllegalArgumentException("hostMountPath must not be null");
        }
        if (containerMountPath == null || containerMountPath.isBlank()) {
            throw new IllegalArgumentException("containerMountPath must not be blank");
        }
    }
}
