package com.stefan.knowledgeagent.agent;

import com.stefan.knowledgeagent.common.exception.AgentExecutionRejectedException;
import com.stefan.knowledgeagent.common.exception.AgentExecutionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentExecutionDeadlineTest {

    private ThreadPoolExecutor executor;

    @AfterEach
    void tearDown() {

        if (executor != null) {
            executor.shutdownNow();
        }

    }

    @Test
    void returnsResultWhenOperationCompletesWithinDeadline() {

        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1)
        );

        AgentExecutionDeadline deadline =
                new AgentExecutionDeadline(executor, Duration.ofSeconds(2));

        assertThat(deadline.execute(() -> "completed"))
                .isEqualTo("completed");
    }

    @Test
    void timesOutAndInterruptsRunningOperation() throws Exception {

        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1)
        );

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean wasInterrupted = new AtomicBoolean();

        AgentExecutionDeadline deadline =
                new AgentExecutionDeadline(executor, Duration.ofMillis(100));

        assertThatThrownBy(() -> deadline.execute(() -> {
            started.countDown();

            try {

                Thread.sleep(10_000);
                return "too late";

            } catch (InterruptedException exception) {

                wasInterrupted.set(true);
                interrupted.countDown();
                Thread.currentThread().interrupt();

                throw new IllegalStateException(exception);
            }
        })).isInstanceOf(AgentExecutionTimeoutException.class);

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(wasInterrupted).isTrue();
    }

    @Test
    void rejectsTaskWhenExecutorHasNoCapacity() throws Exception {
        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>()
        );

        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);

        executor.execute(() -> {
            blockerStarted.countDown();

            try {

                releaseBlocker.await();

            } catch (InterruptedException exception) {

                Thread.currentThread().interrupt();
            }
        });

        assertThat(blockerStarted.await(1, TimeUnit.SECONDS)).isTrue();

        AgentExecutionDeadline deadline =
                new AgentExecutionDeadline(executor, Duration.ofSeconds(1));

        try {

            assertThatThrownBy(() -> deadline.execute(() -> "not accepted"))
                    .isInstanceOf(AgentExecutionRejectedException.class);

        } finally {
            releaseBlocker.countDown();
        }
    }
}
