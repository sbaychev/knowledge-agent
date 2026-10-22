package com.stefan.knowledgeagent.payment.exception;

public class InvalidRefundStateException extends RuntimeException {
    public InvalidRefundStateException(String message) {
        super(message);
    }
}
