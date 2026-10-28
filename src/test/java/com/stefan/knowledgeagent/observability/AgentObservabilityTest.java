package com.stefan.knowledgeagent.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentObservabilityTest {

    private SimpleMeterRegistry registry;
    private AgentObservability observability;

    @BeforeEach
    void setUp() {

        registry = new SimpleMeterRegistry();
        observability = new AgentObservability(registry);
    }

    @Test
    void recordsSuccessfulAgentRequestAndDuration() {

        String result = observability.recordAgentRequest(
                () -> "answer"
        );

        assertThat(result).isEqualTo("answer");

        assertThat(registry.get("knowledge.agent.requests")
                .tag("outcome", "success")
                .counter()
                .count())
                .isEqualTo(1.0);

        assertThat(registry.get("knowledge.agent.request.duration")
                .tag("outcome", "success")
                .timer()
                .count())
                .isEqualTo(1L);
    }

    @Test
    void recordsFailedAgentRequestAndRethrowsException() {

        assertThatThrownBy(() ->
                observability.recordAgentRequest(() -> {
                    throw new IllegalStateException("test failure");
                })
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("test failure");

        assertThat(registry.get("knowledge.agent.requests")
                .tag("outcome", "failure")
                .counter()
                .count())
                .isEqualTo(1.0);

        assertThat(registry.get("knowledge.agent.request.duration")
                .tag("outcome", "failure")
                .timer()
                .count())
                .isEqualTo(1L);
    }

    @Test
    void recordsToolCallWithBoundedToolName() {

        String result = observability.recordToolCall(
                "lookup_order",
                () -> "Order found"
        );

        assertThat(result).isEqualTo("Order found");

        assertThat(registry.get("knowledge.agent.tool.calls")
                .tag("tool", "lookup_order")
                .tag("outcome", "success")
                .counter()
                .count())
                .isEqualTo(1.0);

        assertThat(registry.get("knowledge.agent.tool.duration")
                .tag("tool", "lookup_order")
                .tag("outcome", "success")
                .timer()
                .count())
                .isEqualTo(1L);
    }

    @Test
    void mapsUnknownToolNamesToUnknownMetricTag() {

        observability.recordToolCall(
                "some-user-controlled-value",
                () -> "result"
        );

        assertThat(registry.get("knowledge.agent.tool.calls")
                .tag("tool", "unknown")
                .tag("outcome", "success")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    @Test
    void recordsToolIterationsAndLimitBreaches() {

        observability.recordToolCallingIteration();
        observability.recordToolCallingIteration();

        observability.recordToolLimitExceeded("iteration");
        observability.recordToolLimitExceeded("tool_call");
        observability.recordToolLimitExceeded("untrusted-input");

        assertThat(registry.get("knowledge.agent.tool.iterations")
                .counter()
                .count())
                .isEqualTo(2.0);

        assertThat(registry.get("knowledge.agent.tool.limit.exceeded")
                .tag("limit", "iteration")
                .counter()
                .count())
                .isEqualTo(1.0);

        assertThat(registry.get("knowledge.agent.tool.limit.exceeded")
                .tag("limit", "tool_call")
                .counter()
                .count())
                .isEqualTo(1.0);

        assertThat(registry.get("knowledge.agent.tool.limit.exceeded")
                .tag("limit", "unknown")
                .counter()
                .count())
                .isEqualTo(1.0);
    }
}
