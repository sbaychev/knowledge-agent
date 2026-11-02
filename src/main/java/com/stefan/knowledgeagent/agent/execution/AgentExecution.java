package com.stefan.knowledgeagent.agent.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.Index;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "agent_executions",
        indexes = {
                @Index(
                        name = "idx_agent_executions_conversation_started",
                        columnList = "conversation_id, started_at"
                ),
                @Index(
                        name = "idx_agent_executions_status_started",
                        columnList = "status, started_at"
                )
        }
)
public class AgentExecution {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(
            name = "conversation_id",
            nullable = false,
            updatable = false
    )
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentExecutionStatus status;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "last_event_sequence", nullable = false)
    private int lastEventSequence;

    @Version
    private long version;

    protected AgentExecution() {
        // Required by JPA.
    }

    public AgentExecution(
            UUID id,
            UUID conversationId,
            String correlationId
    ) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.conversationId = Objects.requireNonNull(
                conversationId,
                "conversationId must not be null"
        );

        this.correlationId = correlationId;
        this.status = AgentExecutionStatus.RUNNING;
        this.startedAt = Instant.now();
        this.lastEventSequence = 0;
    }

    public int nextEventSequence() {

        lastEventSequence = Math.incrementExact(lastEventSequence);

        return lastEventSequence;
    }

    public void markSucceeded() {
        finish(AgentExecutionStatus.SUCCEEDED, null);
    }

    public void markFailed(
            AgentExecutionStatus terminalStatus,
            String failureCode
    ) {

        if (terminalStatus == null
                || !terminalStatus.isFailureTerminal()) {

            throw new IllegalArgumentException(
                    "A failure status must be FAILED, TIMED_OUT or REJECTED."
            );
        }

        if (failureCode == null
                || !failureCode.matches("[A-Z0-9_]{1,64}")) {

            throw new IllegalArgumentException(
                    "failureCode must be a stable uppercase code."
            );
        }

        finish(terminalStatus, failureCode);
    }

    private void finish(
            AgentExecutionStatus terminalStatus,
            String failureCode
    ) {

        if (status != AgentExecutionStatus.RUNNING) {

            throw new IllegalStateException(
                    "Execution is already terminal: " + status
            );
        }

        if (terminalStatus == null || !terminalStatus.isTerminal()) {

            throw new IllegalArgumentException(
                    "Execution must transition to a terminal status."
            );
        }

        Instant completed = Instant.now();

        this.status = terminalStatus;
        this.completedAt = completed;
        this.durationMs = Math.max(
                0L,
                Duration.between(startedAt, completed).toMillis()
        );
        this.failureCode = failureCode;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public AgentExecutionStatus getStatus() {
        return status;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public int getLastEventSequence() {
        return lastEventSequence;
    }

    public long getVersion() {
        return version;
    }
}
