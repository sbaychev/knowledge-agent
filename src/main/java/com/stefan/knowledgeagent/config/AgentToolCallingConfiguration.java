package com.stefan.knowledgeagent.config;

import com.stefan.knowledgeagent.agent.BoundedToolCallingAdvisor;
import com.stefan.knowledgeagent.observability.AgentObservability;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.model.tool.ToolCallingManager;

@Configuration
public class AgentToolCallingConfiguration {

    @Bean
    public BoundedToolCallingAdvisor boundedToolCallingAdvisor(
            ObservationRegistry observationRegistry,
            AgentObservability observability,
            @Value("${spring.ai.tools.limits.max-calls-per-tool-default:8}")
            int maxCallsPerTool,
            @Value("${spring.ai.tools.limits.max-total-tool-calls:15}")
            int maxTotalToolCalls,
            @Value("${spring.ai.tools.limits.on-limit-exceeded:THROW}")
            ToolCallLimitBehavior limitBehavior,
            @Value("${agent.limits.max-iterations:8}")
            int maxIterations
    ) {

        ToolCallingManager manager = createToolCallingManager(
                observationRegistry,
                maxCallsPerTool,
                maxTotalToolCalls,
                limitBehavior
        );

        return new BoundedToolCallingAdvisor(
                manager,
                maxIterations,
                observability
        );
    }

    static ToolCallingManager createToolCallingManager(

            ObservationRegistry observationRegistry,
            int maxCallsPerTool,
            int maxTotalToolCalls,
            ToolCallLimitBehavior limitBehavior
    ) {
        validateLimit(
                "spring.ai.tools.limits.max-calls-per-tool-default",
                maxCallsPerTool
        );

        validateLimit(
                "spring.ai.tools.limits.max-total-tool-calls",
                maxTotalToolCalls
        );

        /*
         * THROW is required for a consistent API-level limit error.
         * BoundedToolCallingAdvisor translates Spring AI's special
         * limit generation into AgentToolLimitExceededException.
         */
        if (limitBehavior != ToolCallLimitBehavior.THROW) {

            throw new IllegalArgumentException(
                    "spring.ai.tools.limits.on-limit-exceeded must be THROW "
                            + "when using BoundedToolCallingAdvisor."
            );
        }

        var builder = ToolCallingManager.builder()
                .observationRegistry(observationRegistry);

        if (maxCallsPerTool == -1) {
            builder.unlimitedCallsPerTool();
        } else {
            builder.maxCallsPerTool(maxCallsPerTool);
        }

        if (maxTotalToolCalls == -1) {
            builder.unlimitedTotalToolCalls();
        } else {
            builder.maxTotalToolCalls(maxTotalToolCalls);
        }

        return builder
                .onLimitExceeded(limitBehavior)
                .build();
    }

    private static void validateLimit(
            String propertyName,
            int value
    ) {

        if (value != -1 && value < 1) {
            throw new IllegalArgumentException(
                    propertyName + " must be positive or -1 for unlimited."
            );
        }
    }
}
