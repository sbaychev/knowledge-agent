package com.stefan.knowledgeagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AgentExecutionConfiguration {

    @Bean(
            name = "agentExecutionExecutor",
            destroyMethod = "shutdown"
    )
    public ThreadPoolExecutor agentExecutionExecutor(
            @Value("${agent.execution.pool.core-size:4}")
            int coreSize,
            @Value("${agent.execution.pool.max-size:8}")
            int maxSize,
            @Value("${agent.execution.pool.queue-capacity:16}")
            int queueCapacity,
            @Value("${agent.execution.pool.keep-alive:60s}")
            Duration keepAlive
    ) {

        if (coreSize < 1) {

            throw new IllegalArgumentException(
                    "agent.execution.pool.core-size must be positive."
            );
        }

        if (maxSize < coreSize) {

            throw new IllegalArgumentException(
                    "agent.execution.pool.max-size must be >= core-size."
            );
        }

        if (queueCapacity < 1) {

            throw new IllegalArgumentException(
                    "agent.execution.pool.queue-capacity must be positive."
            );
        }

        if (keepAlive == null
                || keepAlive.isZero()
                || keepAlive.isNegative()) {

            throw new IllegalArgumentException(
                    "agent.execution.pool.keep-alive must be positive."
            );
        }

        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                keepAlive.toNanos(),
                TimeUnit.NANOSECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform()
                        .name("agent-execution-", 0)
                        .factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
