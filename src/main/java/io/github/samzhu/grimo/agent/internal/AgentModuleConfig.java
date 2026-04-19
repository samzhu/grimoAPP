package io.github.samzhu.grimo.agent.internal;

import java.time.Duration;

import org.springaicommunity.agents.claude.ClaudeAgentSessionRegistry;
import org.springaicommunity.agents.model.AgentSessionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AgentModuleConfig {

    @Bean
    AgentSessionRegistry claudeSessionRegistry() {
        return ClaudeAgentSessionRegistry.builder()
                .timeout(Duration.ofMinutes(30))
                .build();
    }
}
