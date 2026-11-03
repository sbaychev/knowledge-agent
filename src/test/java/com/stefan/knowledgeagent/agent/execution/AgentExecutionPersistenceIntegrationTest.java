package com.stefan.knowledgeagent.agent.execution;

import com.stefan.knowledgeagent.PostgresTestConfiguration;
import com.stefan.knowledgeagent.conversation.Conversation;
import com.stefan.knowledgeagent.conversation.ConversationRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class AgentExecutionPersistenceIntegrationTest {

    @Autowired
    private AgentExecutionService executionService;

    @Autowired
    private AgentExecutionRepository executionRepository;

    @Autowired
    private AgentExecutionEventRepository eventRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    void persistsSuccessfulExecutionAndOrderedLifecycleEvents() {

        UUID conversationId = createConversation();

        UUID executionId = executionService.start(
                conversationId,
                "integration-test-001"
        );

        executionService.markSucceeded(executionId);

        AgentExecution execution = executionRepository
                .findById(executionId)
                .orElseThrow();

        assertThat(execution.getConversationId())
                .isEqualTo(conversationId);

        assertThat(execution.getCorrelationId())
                .isEqualTo("integration-test-001");

        assertThat(execution.getStatus())
                .isEqualTo(AgentExecutionStatus.SUCCEEDED);

        assertThat(execution.getStartedAt()).isNotNull();
        assertThat(execution.getCompletedAt()).isNotNull();
        assertThat(execution.getDurationMs()).isNotNull();
        assertThat(execution.getFailureCode()).isNull();
        assertThat(execution.getLastEventSequence()).isEqualTo(2);

        List<AgentExecutionEvent> events = eventRepository
                .findByExecutionIdOrderBySequenceNumberAsc(executionId);

        assertThat(events)
                .extracting(AgentExecutionEvent::getSequenceNumber)
                .containsExactly(1, 2);

        assertThat(events)
                .extracting(AgentExecutionEvent::getEventType)
                .containsExactly(
                        AgentExecutionEventType.EXECUTION_STARTED,
                        AgentExecutionEventType.EXECUTION_SUCCEEDED
                );

        assertThat(events.getFirst().getPayload())
                .containsEntry("agentType", "order-support");

        assertThat(events)
                .allSatisfy(event -> {
                    assertThat(event.getPayload())
                            .doesNotContainKeys(
                                    "prompt",
                                    "answer",
                                    "message",
                                    "arguments"
                            );
                });
    }

    @Test
    void persistsFailureCodeWithoutStoringExceptionMessage() {

        UUID conversationId = createConversation();

        UUID executionId = executionService.start(
                conversationId,
                "integration-test-002"
        );

        executionService.markFailed(
                executionId,
                AgentExecutionStatus.FAILED,
                "AI_PROVIDER_ERROR"
        );

        AgentExecution execution = executionRepository
                .findById(executionId)
                .orElseThrow();

        assertThat(execution.getStatus())
                .isEqualTo(AgentExecutionStatus.FAILED);

        assertThat(execution.getFailureCode())
                .isEqualTo("AI_PROVIDER_ERROR");

        assertThat(execution.getCompletedAt()).isNotNull();

        List<AgentExecutionEvent> events = eventRepository
                .findByExecutionIdOrderBySequenceNumberAsc(executionId);

        assertThat(events)
                .extracting(AgentExecutionEvent::getEventType)
                .containsExactly(
                        AgentExecutionEventType.EXECUTION_STARTED,
                        AgentExecutionEventType.EXECUTION_FAILED
                );

        assertThat(events.getLast().getPayload())
                .containsEntry("failureCode", "AI_PROVIDER_ERROR")
                .doesNotContainKeys(
                        "exceptionMessage",
                        "stackTrace",
                        "prompt",
                        "answer"
                );
    }

    @Test
    void rejectsRepeatedTerminalTransition() {

        UUID conversationId = createConversation();

        UUID executionId = executionService.start(
                conversationId,
                "integration-test-003"
        );

        executionService.markSucceeded(executionId);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> executionService.markSucceeded(executionId)
        )).isInstanceOf(IllegalStateException.class);

        AgentExecution execution = executionRepository
                .findById(executionId)
                .orElseThrow();

        assertThat(execution.getStatus())
                .isEqualTo(AgentExecutionStatus.SUCCEEDED);

        assertThat(execution.getLastEventSequence())
                .isEqualTo(2);
    }

    @Test
    void persistsOrderedToolInvocationEvents() {

        UUID conversationId = createConversation();

        UUID executionId = executionService.start(
                conversationId,
                "integration-test-tool-audit"
        );

        UUID toolCallId = UUID.randomUUID();

        executionService.recordToolCallStarted(
                executionId,
                toolCallId,
                "lookup_order"
        );

        executionService.recordToolCallSucceeded(
                executionId,
                toolCallId,
                "lookup_order",
                18L
        );

        executionService.markSucceeded(executionId);

        List<AgentExecutionEvent> events = eventRepository
                .findByExecutionIdOrderBySequenceNumberAsc(executionId);

        assertThat(events)
                .extracting(AgentExecutionEvent::getSequenceNumber)
                .containsExactly(1, 2, 3, 4);

        assertThat(events)
                .extracting(AgentExecutionEvent::getEventType)
                .containsExactly(
                        AgentExecutionEventType.EXECUTION_STARTED,
                        AgentExecutionEventType.TOOL_CALL_STARTED,
                        AgentExecutionEventType.TOOL_CALL_SUCCEEDED,
                        AgentExecutionEventType.EXECUTION_SUCCEEDED
                );

        AgentExecutionEvent started = events.get(1);
        AgentExecutionEvent completed = events.get(2);

        assertThat(started.getPayload())
                .containsEntry("toolCallId", toolCallId.toString())
                .containsEntry("toolName", "lookup_order")
                .doesNotContainKeys("arguments", "result", "output");

        assertThat(completed.getPayload())
                .containsEntry("toolCallId", toolCallId.toString())
                .containsEntry("toolName", "lookup_order")
                .doesNotContainKeys("arguments", "result", "output");

        Object durationMs = completed.getPayload().get("durationMs");

        assertThat(durationMs).isInstanceOf(Number.class);

        assertThat(((Number) durationMs).longValue()).isEqualTo(18L);
    }

    private UUID createConversation() {

        UUID conversationId = UUID.randomUUID();

        conversationRepository.saveAndFlush(
                Conversation.start(conversationId)
        );

        return conversationId;
    }
}
