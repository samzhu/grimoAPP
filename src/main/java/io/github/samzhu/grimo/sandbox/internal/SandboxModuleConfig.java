package io.github.samzhu.grimo.sandbox.internal;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Sandbox 模組配置。啟用此套件的元件掃描，確保
 * {@link TestcontainersSandboxManager} 被 Spring 管理。
 *
 * <p>設計說明：目前僅需 {@code @ComponentScan}；未來若加入
 * max-concurrent semaphore 或 shutdown hook 等 bean 定義時在此擴充。
 */
@Configuration
@ComponentScan
class SandboxModuleConfig {
}
