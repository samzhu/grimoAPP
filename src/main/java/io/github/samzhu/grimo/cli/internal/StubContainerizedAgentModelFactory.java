package io.github.samzhu.grimo.cli.internal;

import io.github.samzhu.grimo.cli.api.ContainerizedAgentModelFactory;
import io.github.samzhu.grimo.core.domain.ProviderId;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentGenerationMetadata;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentTaskRequest;
import org.springaicommunity.agents.model.StreamingAgentModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Test stub that returns canned responses without any Docker or CLI
 * interaction. Used by T0/T1 tests and by upper modules that need a
 * predictable {@link ContainerizedAgentModelFactory} in slice tests.
 *
 * <p>Design note: the returned model implements both {@link AgentModel}
 * and {@link StreamingAgentModel} to mirror Claude's real capabilities,
 * letting callers test streaming paths without Docker.
 */
public class StubContainerizedAgentModelFactory implements ContainerizedAgentModelFactory {

    private static final String DEFAULT_TEXT = "[stub] Hello from containerised %s (container: %s)";

    @Override
    public AgentModel create(ProviderId provider, String containerId) {
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalArgumentException("containerId must not be blank");
        }
        return new StubAgentModel(provider, containerId);
    }

    /**
     * Implements both sync and streaming interfaces with canned responses.
     */
    private static final class StubAgentModel implements AgentModel, StreamingAgentModel {

        private final String responseText;

        @Override
        public boolean isAvailable() {
            return true;
        }

        StubAgentModel(ProviderId provider, String containerId) {
            this.responseText = DEFAULT_TEXT.formatted(provider.name(), containerId);
        }

        @Override
        public AgentResponse call(AgentTaskRequest request) {
            return buildResponse();
        }

        @Override
        public Flux<AgentResponse> stream(AgentTaskRequest request) {
            return Flux.just(buildResponse());
        }

        private AgentResponse buildResponse() {
            var metadata = new AgentGenerationMetadata("SUCCESS", Map.of());
            var generation = new AgentGeneration(responseText, metadata);
            return new AgentResponse(List.of(generation));
        }
    }
}
