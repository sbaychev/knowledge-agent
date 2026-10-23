package com.stefan.knowledgeagent.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class AgentObservability {

    private static final Logger log =
            LoggerFactory.getLogger(AgentObservability.class);

    private static final String AGENT_REQUESTS =
            "knowledge.agent.requests";

    private static final String AGENT_REQUEST_DURATION =
            "knowledge.agent.request.duration";

    private static final String TOOL_CALLS =
            "knowledge.agent.tool.calls";

    private static final String TOOL_DURATION =
            "knowledge.agent.tool.duration";

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";

    public static final String OUTCOME = "outcome";

    private static final Set<String> KNOWN_TOOLS = Set.of(
            "lookup_order",
            "lookup_shipment_by_order",
            "lookup_payment_by_order",
            "lookup_refunds_by_order"
    );

    @Autowired
    public AgentObservability(MeterRegistry meterRegistry) {
        this(meterRegistry, true);
    }

    private static final AgentObservability NOOP =
            new AgentObservability(Metrics.globalRegistry, false);

    private final MeterRegistry meterRegistry;
    private final boolean enabled;

    private AgentObservability(
            MeterRegistry meterRegistry,
            boolean enabled
    ) {

        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
    }

    /**
     * Compatibility constructor for tests that manually instantiate
     * the tools or agent service without a Spring application context.
     */
    public static AgentObservability noop() {
        return NOOP;
    }

    public <T> T recordAgentRequest(Supplier<T> operation) {
        return record(
                operation,
                AGENT_REQUESTS,
                AGENT_REQUEST_DURATION,
                null
        );
    }

    public <T> T recordToolCall(
            String toolName,
            Supplier<T> operation
    ) {

        String safeToolName = KNOWN_TOOLS.contains(toolName)
                ? toolName
                : "unknown";

        return record(
                operation,
                TOOL_CALLS,
                TOOL_DURATION,
                safeToolName
        );
    }

    private <T> T record(
            Supplier<T> operation,
            String counterName,
            String timerName,
            String toolName
    ) {

        if (!enabled) {
            return operation.get();
        }

        long startedAt = System.nanoTime();

        String outcome = OUTCOME_SUCCESS;
        String failureType = "none";

        try {

            return operation.get();

        } catch (RuntimeException | Error throwable) {

            outcome = OUTCOME_FAILURE;
            failureType = throwable.getClass().getSimpleName();

            throw throwable;

        } finally {

            long elapsedNanos = System.nanoTime() - startedAt;

            if (toolName == null) {
                meterRegistry.counter(
                        counterName,
                        OUTCOME,
                        outcome
                ).increment();

                Timer.builder(timerName)
                        .tag(OUTCOME, outcome)
                        .register(meterRegistry)
                        .record(elapsedNanos, TimeUnit.NANOSECONDS);

                logCompletion(
                        "Agent request",
                        outcome,
                        elapsedNanos,
                        failureType
                );
            } else {

                meterRegistry.counter(
                        counterName,
                        "tool",
                        toolName,
                        OUTCOME,
                        outcome
                ).increment();

                Timer.builder(timerName)
                        .tag("tool", toolName)
                        .tag(OUTCOME, outcome)
                        .register(meterRegistry)
                        .record(elapsedNanos, TimeUnit.NANOSECONDS);

                logCompletion(
                        "Agent tool " + toolName,
                        outcome,
                        elapsedNanos,
                        failureType
                );
            }
        }
    }

    private void logCompletion(
            String operation,
            String outcome,
            long elapsedNanos,
            String failureType
    ) {

        long durationMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

        if (OUTCOME_SUCCESS.equals(outcome)) {

            log.info(
                    "{} completed: outcome={}, durationMs={}",
                    operation,
                    outcome,
                    durationMs
            );
        } else {

            log.warn(
                    "{} failed: outcome={}, durationMs={}, failureType={}",
                    operation,
                    outcome,
                    durationMs,
                    failureType
            );
        }
    }
}
