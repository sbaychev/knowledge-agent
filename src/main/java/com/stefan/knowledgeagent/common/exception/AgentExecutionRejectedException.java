package com.stefan.knowledgeagent.common.exception;

public class AgentExecutionRejectedException extends RuntimeException {

    public AgentExecutionRejectedException(Throwable cause) {

        super("Agent execution capacity is exhausted.", cause);
    }

    public AgentExecutionRejectedException(
            String message,
            Throwable cause
    ) {

        super(message, cause);
    }
}
