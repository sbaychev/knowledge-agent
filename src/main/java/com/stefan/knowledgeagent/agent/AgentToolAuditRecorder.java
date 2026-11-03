package com.stefan.knowledgeagent.agent;

import com.stefan.knowledgeagent.agent.execution.AgentExecutionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class AgentToolAuditRecorder {

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "lookup_order",
            "lookup_shipment_by_order",
            "lookup_payment_by_order",
            "lookup_refunds_by_order",
            "search_knowledge_base"
    );

    private static final AgentToolAuditRecorder NOOP = new AgentToolAuditRecorder();

    private final AgentExecutionContextHolder contextHolder;
    private final AgentExecutionService executionService;
    private final boolean enabled;

    @Autowired
    public AgentToolAuditRecorder(
            AgentExecutionContextHolder contextHolder,
            AgentExecutionService executionService
    ) {

        this.contextHolder = contextHolder;
        this.executionService = executionService;
        this.enabled = true;
    }

    private AgentToolAuditRecorder() {

        this.contextHolder = AgentExecutionContextHolder.noop();
        this.executionService = null;
        this.enabled = false;
    }

    public static AgentToolAuditRecorder noop() {
        return NOOP;
    }

    public <T> T record(
            String toolName,
            Supplier<T> operation
    ) {

        if (!enabled) {
            return operation.get();
        }

        if (!ALLOWED_TOOLS.contains(toolName)) {

            throw new IllegalArgumentException(
                    "Unregistered tool name: " + toolName
            );
        }

        UUID executionId = contextHolder.currentExecutionId().orElse(null);

        /*
         * Direct tool use outside agent execution remains supported.
         * No execution means there is nothing to associate the event with.
         */
        if (executionId == null) {

            return operation.get();
        }

        UUID toolCallId = UUID.randomUUID();

        /*
         * Fail closed if we cannot persist the invocation start.
         * The tool must not run without its start audit record.
         */
        executionService.recordToolCallStarted(
                executionId,
                toolCallId,
                toolName
        );

        long startedAt = System.nanoTime();

        T result;

        try {
            result = operation.get();

        } catch (RuntimeException | Error failure) {

            long durationMs = elapsedMillis(startedAt);

            try {
                executionService.recordToolCallFailed(
                        executionId,
                        toolCallId,
                        toolName,
                        durationMs
                );

            } catch (RuntimeException | Error auditFailure) {

                failure.addSuppressed(auditFailure);
            }

            throw failure;
        }

        /*
         * Do not classify a persistence failure here as a tool failure.
         * If success-event persistence fails, the STARTED event remains
         * and the outer execution will be marked failed.
         */
        executionService.recordToolCallSucceeded(
                executionId,
                toolCallId,
                toolName,
                elapsedMillis(startedAt)
        );

        return result;
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(
                0L,
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startedAt
                )
        );
    }
}
