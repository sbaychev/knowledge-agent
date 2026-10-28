package com.stefan.knowledgeagent.agent;

import com.stefan.knowledgeagent.common.exception.AgentExecutionRejectedException;
import com.stefan.knowledgeagent.common.exception.AgentExecutionTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
public class AgentExecutionDeadline {

    private static final AgentExecutionDeadline DIRECT =
            new AgentExecutionDeadline();

    private final ThreadPoolExecutor executor;
    private final Duration timeout;
    private final boolean direct;

    @Autowired
    public AgentExecutionDeadline(
            @Qualifier("agentExecutionExecutor")
            ThreadPoolExecutor executor,
            @Value("${agent.execution.timeout:90s}")
            Duration timeout
    ) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    "agent.execution.timeout must be positive."
            );
        }

        this.executor = executor;
        this.timeout = timeout;
        this.direct = false;
    }

    private AgentExecutionDeadline() {
        this.executor = null;
        this.timeout = Duration.ZERO;
        this.direct = true;
    }

    /**
     * Compatibility path for existing tests that manually construct
     * OrderSupportAgentService without a Spring context.
     */
    public static AgentExecutionDeadline direct() {
        return DIRECT;
    }

    public String execute(Supplier<String> operation) {
        if (direct) {
            return operation.get();
        }

        final Future<String> future;

        try {
            future = executor.submit(operation::get);
        } catch (RejectedExecutionException exception) {
            throw new AgentExecutionRejectedException(exception);
        }

        try {
            return future.get(
                    timeout.toNanos(),
                    TimeUnit.NANOSECONDS
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AgentExecutionTimeoutException(
                    timeout,
                    exception
            );
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();

            throw new AgentExecutionRejectedException(
                    "Agent execution was interrupted.",
                    exception
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw new IllegalStateException(
                    "Agent execution failed.",
                    cause
            );
        }
    }
}
