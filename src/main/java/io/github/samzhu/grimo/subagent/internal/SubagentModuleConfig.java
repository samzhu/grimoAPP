package io.github.samzhu.grimo.subagent.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Module configuration for the subagent module (S028).
 * Enables {@link SubagentProperties} binding.
 */
@Configuration
@EnableConfigurationProperties(SubagentProperties.class)
class SubagentModuleConfig {
}
