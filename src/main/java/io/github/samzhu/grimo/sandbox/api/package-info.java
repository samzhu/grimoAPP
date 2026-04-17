/**
 * Sandbox 模組的公開 API 套件。
 *
 * <p>此套件包含供其他模組消費的型別：{@code SandboxManager}（生命週期管理介面）
 * 與 {@code SandboxConfig}（建立參數 record）。消費者模組在
 * {@code allowedDependencies} 中宣告 {@code "sandbox :: api"} 即可存取。
 */
@NamedInterface("api")
package io.github.samzhu.grimo.sandbox.api;

import org.springframework.modulith.NamedInterface;
