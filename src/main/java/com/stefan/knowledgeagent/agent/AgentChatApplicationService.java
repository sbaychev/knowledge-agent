package com.stefan.knowledgeagent.agent;

import com.stefan.knowledgeagent.agent.execution.AgentExecutionService;
import com.stefan.knowledgeagent.agent.execution.AgentExecutionStatus;
import com.stefan.knowledgeagent.api.dto.AgentChatRequest;
import com.stefan.knowledgeagent.api.dto.AgentChatResponse;
import com.stefan.knowledgeagent.common.exception.AgentExecutionRejectedException;
import com.stefan.knowledgeagent.common.exception.AgentExecutionTimeoutException;
import com.stefan.knowledgeagent.common.exception.AgentToolLimitExceededException;
import com.stefan.knowledgeagent.conversation.ConversationService;
import com.stefan.knowledgeagent.observability.RequestCorrelationFilter;
import com.stefan.knowledgeagent.order.OrderSupportAgentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AgentChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatApplicationService.class);

    private final ConversationService conversationService;
    private final OrderSupportAgentService agentService;
    private final AgentExecutionService executionService;

    public AgentChatApplicationService(
            ConversationService conversationService,
            OrderSupportAgentService agentService,
            AgentExecutionService executionService
    ) {

        this.conversationService = conversationService;
        this.agentService = agentService;
        this.executionService = executionService;

    }

    public AgentChatResponse chat(AgentChatRequest request) {

        /*
         * An invalid or closed conversation must be rejected before
         * a new agent execution is created.
         */
        UUID conversationId =
                conversationService.getOrCreateActiveId(
                        request.conversationId()
                );

        String correlationId =
                MDC.get(RequestCorrelationFilter.MDC_KEY);

        /*
         * The start record commits before the model invocation starts.
         */
        UUID executionId = executionService.start(
                conversationId,
                correlationId
        );

        try {

            String answer = agentService.answer(
                    request.message(),
                    conversationId.toString(),
                    executionId
            );

            executionService.markSucceeded(executionId);

            return new AgentChatResponse(
                    conversationId,
                    executionId,
                    answer
            );

        } catch (RuntimeException | Error failure) {

            recordFailureSafely(executionId, failure);

            /*
             * Preserve the original exception so the existing
             * ApiExceptionHandler continues mapping HTTP errors.
             */
            throw failure;
        }
    }

    private void recordFailureSafely(
            UUID executionId,
            Throwable failure
    ) {
        FailureClassification classification = classify(failure);

        try {
            executionService.markFailed(
                    executionId,
                    classification.status(),
                    classification.code()
            );
        } catch (RuntimeException | Error auditFailure) {

            /*
             * A failure to write diagnostic metadata must not hide
             * the original LLM/tool/execution exception.
             */
            log.error(
                    "Could not persist terminal state for agent execution {}",
                    executionId,
                    auditFailure
            );
        }
    }

    private static FailureClassification classify(Throwable failure) {

        if (failure instanceof AgentExecutionTimeoutException) {
            return new FailureClassification(
                    AgentExecutionStatus.TIMED_OUT,
                    "AGENT_EXECUTION_TIMEOUT"
            );
        }

        if (failure instanceof AgentExecutionRejectedException) {
            return new FailureClassification(
                    AgentExecutionStatus.REJECTED,
                    "AGENT_EXECUTION_REJECTED"
            );
        }

        if (failure instanceof AgentToolLimitExceededException) {
            return new FailureClassification(
                    AgentExecutionStatus.FAILED,
                    "AGENT_TOOL_LIMIT_EXCEEDED"
            );
        }

        if (failure instanceof TransientAiException
                || failure instanceof NonTransientAiException) {
            return new FailureClassification(
                    AgentExecutionStatus.FAILED,
                    "AI_PROVIDER_ERROR"
            );
        }

        return new FailureClassification(
                AgentExecutionStatus.FAILED,
                "AGENT_EXECUTION_FAILED"
        );
    }

    private record FailureClassification(
            AgentExecutionStatus status,
            String code
    ) {
    }
}
