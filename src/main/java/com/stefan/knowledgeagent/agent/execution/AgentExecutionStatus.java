package com.stefan.knowledgeagent.agent.execution;

public enum AgentExecutionStatus {

    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    REJECTED;

    public boolean isTerminal() {
        return this != RUNNING;
    }

    public boolean isFailureTerminal() {

        return this == FAILED
                || this == TIMED_OUT
                || this == REJECTED;
    }
}
