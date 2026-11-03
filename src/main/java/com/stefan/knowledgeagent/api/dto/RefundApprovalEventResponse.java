package com.stefan.knowledgeagent.api.dto;

import com.stefan.knowledgeagent.approval.RefundApprovalEvent;
import com.stefan.knowledgeagent.approval.RefundApprovalEventType;

import java.time.Instant;
import java.util.Map;

public record RefundApprovalEventResponse(
        int sequenceNumber,
        RefundApprovalEventType eventType,
        String actorSubject,
        Instant occurredAt,
        int payloadVersion,
        Map<String, Object> payload
) {

    public static RefundApprovalEventResponse from(
            RefundApprovalEvent event
    ) {
        return new RefundApprovalEventResponse(
                event.getSequenceNumber(),
                event.getEventType(),
                event.getActorSubject(),
                event.getOccurredAt(),
                event.getPayloadVersion(),
                event.getPayload()
        );
    }
}
