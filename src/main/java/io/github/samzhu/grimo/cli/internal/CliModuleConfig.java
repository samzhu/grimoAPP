package io.github.samzhu.grimo.cli.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean registration for CLI module internals.
 *
 * <p>Design note: {@link WrapperScriptGenerator} is not a {@code @Component}
 * itself (it has no Spring annotations in its API), so we register it
 * here. {@link DefaultContainerizedAgentModelFactory} is a {@code @Service}
 * and auto-discovered.
 */
@Configuration
class CliModuleConfig {

    @Bean
    WrapperScriptGenerator wrapperScriptGenerator() {
        return new WrapperScriptGenerator();
    }
}
