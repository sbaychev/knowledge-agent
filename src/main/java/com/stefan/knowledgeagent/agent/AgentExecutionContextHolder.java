package com.stefan.knowledgeagent.agent;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AgentExecutionContextHolder {

    private static final AgentExecutionContextHolder NOOP = new AgentExecutionContextHolder(false);

    private final ThreadLocal<UUID> currentExecutionId = new ThreadLocal<>();

    private final boolean enabled;

    public AgentExecutionContextHolder() {
        this(true);
    }

    private AgentExecutionContextHolder(boolean enabled) {
        this.enabled = enabled;
    }

    public static AgentExecutionContextHolder noop() {
        return NOOP;
    }

    public Scope bind(UUID executionId) {

        if (!enabled || executionId == null) {
            return () -> {
            };
        }

        UUID previous = currentExecutionId.get();

        currentExecutionId.set(executionId);

        return () -> {

            if (previous == null) {

                currentExecutionId.remove();

            } else {

                currentExecutionId.set(previous);

            }
        };
    }

    public Optional<UUID> currentExecutionId() {

        if (!enabled) {

            return Optional.empty();

        }

        return Optional.ofNullable(currentExecutionId.get());
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
