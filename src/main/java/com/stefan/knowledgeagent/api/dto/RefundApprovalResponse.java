package com.stefan.knowledgeagent.api.dto;

import com.stefan.knowledgeagent.approval.RefundApprovalEvent;
import com.stefan.knowledgeagent.approval.RefundApprovalRequest;
import com.stefan.knowledgeagent.approval.RefundApprovalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundApprovalResponse(
        UUID id,
        Long paymentId,
        UUID executionId,
        BigDecimal amount,
        String currency,
        String reason,
        String requesterSubject,
        String approverSubject,
        RefundApprovalStatus status,
        String rejectionReason,
        Long refundId,
        Instant createdAt,
        Instant expiresAt,
        Instant decidedAt,
        Instant submittedAt,
        List<RefundApprovalEventResponse> events
) {

    public static RefundApprovalResponse from(
            RefundApprovalRequest request,
            List<RefundApprovalEvent> events
    ) {

        return new RefundApprovalResponse(
                request.getId(),
                request.getPaymentId(),
                request.getExecutionId(),
                request.getAmount(),
                request.getCurrency(),
                request.getReason(),
                request.getRequesterSubject(),
                request.getApproverSubject(),
                request.getStatus(),
                request.getRejectionReason(),
                request.getRefundId(),
                request.getCreatedAt(),
                request.getExpiresAt(),
                request.getDecidedAt(),
                request.getSubmittedAt(),
                events.stream()
                        .map(RefundApprovalEventResponse::from)
                        .toList()
        );
    }
}
