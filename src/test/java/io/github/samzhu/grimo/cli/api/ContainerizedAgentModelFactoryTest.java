package io.github.samzhu.grimo.cli.api;

import io.github.samzhu.grimo.cli.internal.StubContainerizedAgentModelFactory;
import io.github.samzhu.grimo.core.domain.ProviderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.agents.model.AgentTaskRequest;
import org.springaicommunity.agents.model.StreamingAgentModel;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerizedAgentModelFactoryTest {

    private final ContainerizedAgentModelFactory factory = new StubContainerizedAgentModelFactory();

    @Test
    @DisplayName("[S005] AC-1: StubFactory sync call returns non-empty successful AgentResponse")
    void stubFactorySyncCallReturnsSuccessfulResponse() {
        // Given
        AgentModel model = factory.create(ProviderId.CLAUDE, "stub-container-id");

        // When
        var request = AgentTaskRequest.builder("hello", Path.of("/work")).build();
        var response = model.call(request);

        // Then
        assertThat(response.getText()).isNotBlank();
        assertThat(response.isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("[S005] AC-1: StubFactory returns StreamingAgentModel that emits non-empty responses")
    void stubFactoryStreamingReturnsNonEmptyResponses() {
        // Given
        AgentModel model = factory.create(ProviderId.CLAUDE, "stub-container-id");

        // When — verify it also implements StreamingAgentModel
        assertThat(model).isInstanceOf(StreamingAgentModel.class);
        var streaming = (StreamingAgentModel) model;
        var request = AgentTaskRequest.builder("hello", Path.of("/work")).build();
        var responses = streaming.stream(request).collectList().block();

        // Then
        assertThat(responses).isNotEmpty();
        assertThat(responses).allSatisfy(r -> assertThat(r.getText()).isNotBlank());
    }
}
