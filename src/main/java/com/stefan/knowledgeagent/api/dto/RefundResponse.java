package com.stefan.knowledgeagent.api.dto;

import com.stefan.knowledgeagent.payment.Refund;
import com.stefan.knowledgeagent.payment.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(

        Long id,
        Long paymentId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        String reason,
        RefundStatus status,
        Instant createdAt,
        Instant completedAt,
        Instant failedAt

) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getIdempotencyKey(),
                refund.getReason(),
                refund.getStatus(),
                refund.getCreatedAt(),
                refund.getCompletedAt(),
                refund.getFailedAt()
        );
    }
}
