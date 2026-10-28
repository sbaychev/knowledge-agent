package com.stefan.knowledgeagent.common.exception;

import java.util.Objects;

public final class AgentToolLimitExceededException extends RuntimeException {

    public enum Reason {

        ITERATION_LIMIT("iteration"),
        TOOL_CALL_LIMIT("tool_call");

        private final String metricTag;

        Reason(String metricTag) {
            this.metricTag = metricTag;
        }

        public String metricTag() {
            return metricTag;
        }
    }

    private final Reason reason;

    public AgentToolLimitExceededException(Reason reason) {

        super(messageFor(reason));

        this.reason = Objects.requireNonNull(reason);
    }

    public Reason reason() {
        return reason;
    }

    private static String messageFor(Reason reason) {

        return switch (Objects.requireNonNull(reason)) {
            case ITERATION_LIMIT -> "The agent exceeded its configured iteration limit.";
            case TOOL_CALL_LIMIT -> "The agent exceeded its configured tool-call limit.";
        };
    }
}
