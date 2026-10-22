package com.stefan.knowledgeagent.api.dto;

import com.stefan.knowledgeagent.payment.Payment;
import com.stefan.knowledgeagent.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(

        Long id,
        Long oderId,
        BigDecimal amount,
        BigDecimal amountRefunded,
        BigDecimal remainingRefundableAmount,
        String currency,
        String providerReference,
        PaymentStatus status,
        Instant capturedAt,
        Instant createdAt

) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getAmountRefunded(),
                payment.getRemainingRefundableAmount(),
                payment.getCurrency(),
                payment.getProviderReference(),
                payment.getStatus(),
                payment.getCapturedAt(),
                payment.getCreatedAt()
        );
    }

}
