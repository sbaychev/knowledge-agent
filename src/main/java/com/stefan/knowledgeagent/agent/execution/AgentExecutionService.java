package com.stefan.knowledgeagent.agent.execution;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AgentExecutionService {

    private final AgentExecutionRepository executionRepository;
    private final AgentExecutionEventRepository eventRepository;

    public AgentExecutionService(
            AgentExecutionRepository executionRepository,
            AgentExecutionEventRepository eventRepository
    ) {

        this.executionRepository = executionRepository;
        this.eventRepository = eventRepository;

    }

    @Transactional
    public UUID start(
            UUID conversationId,
            String correlationId
    ) {

        Objects.requireNonNull(
                conversationId,
                "conversationId must not be null"
        );

        String normalizedCorrelationId =
                normalizeCorrelationId(correlationId);

        AgentExecution execution = new AgentExecution(
                UUID.randomUUID(),
                conversationId,
                normalizedCorrelationId
        );

        /*
         * Flush first so the parent execution exists in PostgreSQL
         * before the event with its foreign key is inserted.
         *
         * The transaction is still open; execution creation and its
         * initial event commit atomically.
         */
        executionRepository.saveAndFlush(execution);

        appendEvent(
                execution,
                AgentExecutionEventType.EXECUTION_STARTED,
                Map.of(
                        "agentType", "order-support",
                        "status", AgentExecutionStatus.RUNNING.name()
                )
        );

        return execution.getId();
    }

    @Transactional
    public void markSucceeded(UUID executionId) {
        finish(
                executionId,
                AgentExecutionStatus.SUCCEEDED,
                null,
                AgentExecutionEventType.EXECUTION_SUCCEEDED,
                Map.of(
                        "outcome", "success"
                )
        );
    }

    @Transactional
    public void markFailed(
            UUID executionId,
            AgentExecutionStatus terminalStatus,
            String failureCode
    ) {
        if (terminalStatus == null
                || !terminalStatus.isFailureTerminal()) {
            throw new IllegalArgumentException(
                    "Failure status must be FAILED, TIMED_OUT or REJECTED."
            );
        }

        if (failureCode == null
                || !failureCode.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException(
                    "failureCode must be a stable uppercase code."
            );
        }

        AgentExecutionEventType eventType = switch (terminalStatus) {
            case FAILED -> AgentExecutionEventType.EXECUTION_FAILED;
            case TIMED_OUT -> AgentExecutionEventType.EXECUTION_TIMED_OUT;
            case REJECTED -> AgentExecutionEventType.EXECUTION_REJECTED;
            default -> throw new IllegalArgumentException(
                    "Unsupported failure status: " + terminalStatus
            );
        };

        finish(
                executionId,
                terminalStatus,
                failureCode,
                eventType,
                Map.of(
                        "failureCode", failureCode
                )
        );
    }

    @Transactional
    public void recordToolCallStarted(
            UUID executionId,
            UUID toolCallId,
            String toolName
    ) {
        appendToolCallEvent(
                executionId,
                AgentExecutionEventType.TOOL_CALL_STARTED,
                toolCallId,
                toolName,
                null
        );
    }

    @Transactional
    public void recordToolCallSucceeded(
            UUID executionId,
            UUID toolCallId,
            String toolName,
            long durationMs
    ) {
        appendToolCallEvent(
                executionId,
                AgentExecutionEventType.TOOL_CALL_SUCCEEDED,
                toolCallId,
                toolName,
                durationMs
        );
    }

    @Transactional
    public void recordToolCallFailed(
            UUID executionId,
            UUID toolCallId,
            String toolName,
            long durationMs
    ) {

        appendToolCallEvent(
                executionId,
                AgentExecutionEventType.TOOL_CALL_FAILED,
                toolCallId,
                toolName,
                durationMs
        );
    }

    private void appendToolCallEvent(
            UUID executionId,
            AgentExecutionEventType eventType,
            UUID toolCallId,
            String toolName,
            Long durationMs
    ) {

        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(toolCallId, "toolCallId");

        if (toolName == null
                || !toolName.matches("[a-z][a-z0-9_]{0,63}")) {

            throw new IllegalArgumentException("Invalid tool name.");

        }

        if (durationMs != null && durationMs < 0) {

            throw new IllegalArgumentException("durationMs must not be negative.");

        }

        AgentExecution execution = executionRepository
                .findLockedById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AGENT_EXECUTION_NOT_FOUND: ",
                        executionId.toString()
                ));

        if (execution.getStatus() != AgentExecutionStatus.RUNNING) {

            throw new IllegalStateException(
                    "Cannot append tool event to terminal execution: "
                            + execution.getStatus()
            );
        }

        Map<String, Object> payload = new java.util.LinkedHashMap<>();

        payload.put("toolCallId", toolCallId.toString());
        payload.put("toolName", toolName);

        if (durationMs != null) {
            payload.put("durationMs", durationMs);
        }

        appendEvent(execution, eventType, payload);
    }

    private void finish(
            UUID executionId,
            AgentExecutionStatus terminalStatus,
            String failureCode,
            AgentExecutionEventType eventType,
            Map<String, Object> payload
    ) {

        AgentExecution execution = executionRepository
                .findLockedById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AGENT_EXECUTION_NOT_FOUND: ", executionId.toString()
                ));

        if (terminalStatus == AgentExecutionStatus.SUCCEEDED) {

            execution.markSucceeded();

        } else {

            execution.markFailed(terminalStatus, failureCode);
        }

        appendEvent(execution, eventType, payload);

        executionRepository.save(execution);
    }

    private void appendEvent(
            AgentExecution execution,
            AgentExecutionEventType eventType,
            Map<String, Object> payload
    ) {
        int sequenceNumber = execution.nextEventSequence();

        AgentExecutionEvent event = new AgentExecutionEvent(
                execution.getId(),
                sequenceNumber,
                eventType,
                payload
        );

        eventRepository.save(event);
        executionRepository.save(execution);
    }

    private static String normalizeCorrelationId(String correlationId) {
        if (correlationId == null
                || !correlationId.matches("[A-Za-z0-9._-]{1,64}")) {
            return null;
        }

        return correlationId;
    }
}
