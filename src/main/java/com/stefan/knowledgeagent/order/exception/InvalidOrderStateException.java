package com.stefan.knowledgeagent.order.exception;

public class InvalidOrderStateException extends RuntimeException {

    private final String code;

    public InvalidOrderStateException(String code) {

        this.code = code;

    }

    public String getCode() {
        return code;
    }

}
