package com.stefan.knowledgeagent.payment.exception;

public class RefundAmountExceededException extends RuntimeException {
    public RefundAmountExceededException(String message) {
        super(message);
    }
}
