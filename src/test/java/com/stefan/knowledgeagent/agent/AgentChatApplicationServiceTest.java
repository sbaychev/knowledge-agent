package com.stefan.knowledgeagent.agent;

import com.stefan.knowledgeagent.agent.execution.AgentExecutionService;
import com.stefan.knowledgeagent.agent.execution.AgentExecutionStatus;
import com.stefan.knowledgeagent.api.dto.AgentChatRequest;
import com.stefan.knowledgeagent.api.dto.AgentChatResponse;
import com.stefan.knowledgeagent.conversation.ConversationService;
import com.stefan.knowledgeagent.order.OrderSupportAgentService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.slf4j.MDC;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatApplicationServiceTest {

    private static final UUID CONVERSATION_ID =
            UUID.fromString(
                    "a8edb27b-9a19-4822-8c19-fd01adf6f017"
            );

    private static final UUID EXECUTION_ID =
            UUID.fromString(
                    "d622ac40-c006-49b1-9d43-882dca25f91c"
            );

    @Mock
    private ConversationService conversationService;

    @Mock
    private OrderSupportAgentService agentService;

    @Mock
    private AgentExecutionService executionService;

    private AgentChatApplicationService service;

    @BeforeEach
    void setUp() {

        MDC.clear();

        service = new AgentChatApplicationService(
                conversationService,
                agentService,
                executionService
        );
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void successfulExecutionReturnsBothIdentifiers() {

        when(conversationService.getOrCreateActiveId(null))
                .thenReturn(CONVERSATION_ID);

        when(executionService.start(
                eq(CONVERSATION_ID),
                isNull()
        )).thenReturn(EXECUTION_ID);

        when(agentService.answer(
                "Check order 1.",
                CONVERSATION_ID.toString()
        )).thenReturn("Order 1 is shipped.");

        AgentChatResponse response = service.chat(
                new AgentChatRequest("Check order 1.", null)
        );

        assertThat(response.conversationId())
                .isEqualTo(CONVERSATION_ID);

        assertThat(response.executionId())
                .isEqualTo(EXECUTION_ID);

        assertThat(response.answer())
                .isEqualTo("Order 1 is shipped.");

        verify(executionService).markSucceeded(EXECUTION_ID);

        verify(executionService, never()).markFailed(
                any(),
                any(),
                any()
        );
    }

    @Test
    void failedExecutionIsRecordedAndOriginalExceptionIsPropagated() {

        RuntimeException originalFailure =
                new IllegalStateException(
                        "Synthetic provider failure."
                );

        when(conversationService.getOrCreateActiveId(null))
                .thenReturn(CONVERSATION_ID);

        when(executionService.start(
                eq(CONVERSATION_ID),
                isNull()
        )).thenReturn(EXECUTION_ID);

        when(agentService.answer(
                "Check order 1.",
                CONVERSATION_ID.toString()
        )).thenThrow(originalFailure);

        assertThatThrownBy(() -> service.chat(
                new AgentChatRequest("Check order 1.", null)
        )).isSameAs(originalFailure);

        verify(executionService).markFailed(
                EXECUTION_ID,
                AgentExecutionStatus.FAILED,
                "AGENT_EXECUTION_FAILED"
        );

        verify(executionService, never())
                .markSucceeded(EXECUTION_ID);
    }
}
