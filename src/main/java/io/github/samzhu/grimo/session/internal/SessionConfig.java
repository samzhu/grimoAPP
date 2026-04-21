package io.github.samzhu.grimo.session.internal;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Session module bean wiring. Provides an {@link ObjectMapper} if none
 * is already available in the application context (e.g. when
 * spring-boot-starter-web is not present).
 */
@Configuration
class SessionConfig {

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
