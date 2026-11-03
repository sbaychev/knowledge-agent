package com.stefan.knowledgeagent.agent.execution;

public enum AgentExecutionEventType {

    EXECUTION_STARTED,
    EXECUTION_SUCCEEDED,
    EXECUTION_FAILED,
    EXECUTION_TIMED_OUT,
    EXECUTION_REJECTED,

    TOOL_CALL_STARTED,
    TOOL_CALL_SUCCEEDED,
    TOOL_CALL_FAILED

}
