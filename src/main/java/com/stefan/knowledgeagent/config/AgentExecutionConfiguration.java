package com.stefan.knowledgeagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            @Value("${agent.execution.max-concurrent-requests:8}")
            int maxConcurrentRequests,
            @Value("${agent.execution.queue-capacity:16}")
            int queueCapacity
    ) {

        if (maxConcurrentRequests < 1) {

            throw new IllegalArgumentException(
                    "max-concurrent-requests must be positive."
            );
        }

        if (queueCapacity < 1) {

            throw new IllegalArgumentException(
                    "queue-capacity must be positive."
            );
        }

        return new ThreadPoolExecutor(
                maxConcurrentRequests,
                maxConcurrentRequests,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform()
                        .name("agent-execution-", 0)
                        .factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
