
package com.stefan.knowledgeagent.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "refund_approval_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refund_approval_event_sequence",
                        columnNames = {
                                "approval_request_id",
                                "sequence_number"
                        }
                )
        }
)
public class RefundApprovalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "approval_request_id",
            nullable = false,
            updatable = false
    )
    private UUID approvalRequestId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private RefundApprovalEventType eventType;

    @Column(name = "actor_subject", length = 128, updatable = false)
    private String actorSubject;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "payload_version", nullable = false, updatable = false)
    private int payloadVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "payload_json",
            nullable = false,
            columnDefinition = "jsonb",
            updatable = false
    )
    private Map<String, Object> payload;

    protected RefundApprovalEvent() {
        // Required by JPA.
    }

    public RefundApprovalEvent(
            UUID approvalRequestId,
            int sequenceNumber,
            RefundApprovalEventType eventType,
            String actorSubject,
            Map<String, Object> payload
    ) {

        this.approvalRequestId = Objects.requireNonNull(approvalRequestId);
        this.eventType = Objects.requireNonNull(eventType);

        if (sequenceNumber < 1) {

            throw new IllegalArgumentException("sequenceNumber must be positive.");
        }

        this.sequenceNumber = sequenceNumber;
        this.actorSubject = actorSubject;
        this.occurredAt = Instant.now();
        this.payloadVersion = 1;
        this.payload = payload == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(payload);
    }

    public Long getId() {
        return id;
    }

    public UUID getApprovalRequestId() {
        return approvalRequestId;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public RefundApprovalEventType getEventType() {
        return eventType;
    }

    public String getActorSubject() {
        return actorSubject;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public int getPayloadVersion() {
        return payloadVersion;
    }

    public Map<String, Object> getPayload() {

        return payload == null
                ? Map.of()
                : Collections.unmodifiableMap(payload);
    }
}
