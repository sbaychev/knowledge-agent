package com.stefan.knowledgeagent.common.exception;

import java.time.Duration;

public class AgentExecutionTimeoutException extends RuntimeException {

    private final Duration timeout;

    public AgentExecutionTimeoutException(
            Duration timeout,
            Throwable cause
    ) {
        super("Agent execution exceeded its configured deadline.", cause);
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }

}
