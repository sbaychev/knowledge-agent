package com.stefan.knowledgeagent.common.exception;

public class AgentInputTooLongException extends RuntimeException {

    private final int maxCharacters;

    public AgentInputTooLongException(int maxCharacters) {

        super("Agent message exceeds the maximum length.");
        this.maxCharacters = maxCharacters;
    }

    public int getMaxCharacters() {
        return maxCharacters;
    }
}
