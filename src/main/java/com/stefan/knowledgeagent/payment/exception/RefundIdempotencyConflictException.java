package com.stefan.knowledgeagent.payment.exception;

public class RefundIdempotencyConflictException extends RuntimeException {
    public RefundIdempotencyConflictException(String message) {
        super(message);
    }
}
