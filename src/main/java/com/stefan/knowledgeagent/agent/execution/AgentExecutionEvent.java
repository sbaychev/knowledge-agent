package com.stefan.knowledgeagent.agent.execution;

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
        name = "agent_execution_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_agent_execution_event_sequence",
                        columnNames = {"execution_id", "sequence_number"}
                )
        }
)
public class AgentExecutionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "execution_id",
            nullable = false,
            updatable = false
    )
    private UUID executionId;

    @Column(
            name = "sequence_number",
            nullable = false,
            updatable = false
    )
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "event_type",
            nullable = false,
            length = 32,
            updatable = false
    )
    private AgentExecutionEventType eventType;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private Instant occurredAt;

    @Column(
            name = "payload_version",
            nullable = false,
            updatable = false
    )
    private int payloadVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "payload_json",
            nullable = false,
            columnDefinition = "jsonb",
            updatable = false
    )
    private Map<String, Object> payload;

    protected AgentExecutionEvent() {
        // Required by JPA.
    }

    public AgentExecutionEvent(
            UUID executionId,
            int sequenceNumber,
            AgentExecutionEventType eventType,
            Map<String, Object> payload
    ) {

        this.executionId = Objects.requireNonNull(executionId);
        this.eventType = Objects.requireNonNull(eventType);

        if (sequenceNumber < 1) {

            throw new IllegalArgumentException(
                    "sequenceNumber must be positive."
            );
        }

        this.sequenceNumber = sequenceNumber;
        this.occurredAt = Instant.now();
        this.payloadVersion = 1;
        this.payload = payload == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(payload);
    }

    public Long getId() {
        return id;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public AgentExecutionEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public int getPayloadVersion() {
        return payloadVersion;
    }

    public Map<String, Object> getPayload() {

        if (payload == null) {

            return Map.of();
        }

        return Collections.unmodifiableMap(payload);
    }
}
