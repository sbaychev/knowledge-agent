package com.stefan.knowledgeagent.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentExecutionConfigurationTest {

    private final AgentExecutionConfiguration configuration =
            new AgentExecutionConfiguration();

    @Test
    void createsExecutorUsingConfiguredPoolSettings() {

        ThreadPoolExecutor executor =
                configuration.agentExecutionExecutor(
                        2,
                        5,
                        7,
                        Duration.ofSeconds(30)
                );

        try {

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(5);

            assertThat(executor.getKeepAliveTime(TimeUnit.SECONDS))
                    .isEqualTo(30);

            assertThat(executor.getQueue())
                    .isInstanceOf(ArrayBlockingQueue.class);

            ArrayBlockingQueue<?> queue =
                    (ArrayBlockingQueue<?>) executor.getQueue();

            assertThat(queue.remainingCapacity()).isEqualTo(7);

            assertThat(executor.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);

        } finally {

            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNonPositiveCoreSize() {

        assertThatThrownBy(() ->
                configuration.agentExecutionExecutor(
                        0, 4, 8, Duration.ofSeconds(10)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("core-size");
    }

    @Test
    void rejectsMaximumSizeBelowCoreSize() {

        assertThatThrownBy(() ->
                configuration.agentExecutionExecutor(
                        4, 2, 8, Duration.ofSeconds(10)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-size");
    }

    @Test
    void rejectsInvalidQueueCapacity() {

        assertThatThrownBy(() ->
                configuration.agentExecutionExecutor(
                        2, 4, 0, Duration.ofSeconds(10)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queue-capacity");
    }

    @Test
    void rejectsNonPositiveKeepAlive() {

        assertThatThrownBy(() ->
                configuration.agentExecutionExecutor(
                        2, 4, 8, Duration.ZERO
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keep-alive");
    }
}
