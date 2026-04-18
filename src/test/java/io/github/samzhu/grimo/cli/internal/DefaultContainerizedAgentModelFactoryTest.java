package io.github.samzhu.grimo.cli.internal;

import io.github.samzhu.grimo.core.domain.ProviderId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.claude.ClaudeAgentModel;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.agents.model.StreamingAgentModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultContainerizedAgentModelFactoryTest {

    private final WrapperScriptGenerator scriptGenerator = new WrapperScriptGenerator();
    private final DefaultContainerizedAgentModelFactory factory =
            new DefaultContainerizedAgentModelFactory(scriptGenerator);

    @AfterEach
    void cleanup() {
        scriptGenerator.cleanup("container-123");
    }

    @Test
    @DisplayName("[S005] AC-infrastructure: create CLAUDE returns ClaudeAgentModel implementing AgentModel + StreamingAgentModel")
    void createClaudeReturnsCorrectType() {
        // Given / When
        AgentModel model = factory.create(ProviderId.CLAUDE, "container-123");

        // Then
        assertThat(model).isInstanceOf(ClaudeAgentModel.class);
        assertThat(model).isInstanceOf(AgentModel.class);
        assertThat(model).isInstanceOf(StreamingAgentModel.class);
    }

    @Test
    @DisplayName("[S005] AC-infrastructure: create with null containerId throws IllegalArgumentException")
    void createWithNullContainerIdThrows() {
        assertThatThrownBy(() -> factory.create(ProviderId.CLAUDE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[S005] AC-infrastructure: create with blank containerId throws IllegalArgumentException")
    void createWithBlankContainerIdThrows() {
        assertThatThrownBy(() -> factory.create(ProviderId.CLAUDE, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
