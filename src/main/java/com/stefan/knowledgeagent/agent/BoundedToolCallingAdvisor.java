package com.stefan.knowledgeagent.agent;

import com.stefan.knowledgeagent.common.exception.AgentToolLimitExceededException;
import com.stefan.knowledgeagent.observability.AgentObservability;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingManager;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class BoundedToolCallingAdvisor extends ToolCallingAdvisor {

    static final String TURN_STATE_KEY =
            BoundedToolCallingAdvisor.class.getName() + ".turnState";

    private final int maxIterations;
    private final AgentObservability observability;

    public BoundedToolCallingAdvisor(
            ToolCallingManager toolCallingManager,
            int maxIterations,
            AgentObservability observability
    ) {
        super(
                toolCallingManager,
                DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER,
                ToolCallingAdvisor.DEFAULT_ORDER,
                true
        );

        if (maxIterations < 1) {
            throw new IllegalArgumentException(
                    "agent.limits.max-iterations must be positive."
            );
        }

        this.maxIterations = maxIterations;
        this.observability = observability;
    }

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest request,
            CallAdvisorChain chain
    ) {
        ChatClientResponse response =
                super.adviseCall(request, chain);

        return rejectFrameworkToolLimit(response);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request,
            StreamAdvisorChain chain
    ) {
        return super.adviseStream(request, chain)
                .map(this::rejectFrameworkToolLimit);
    }

    @Override
    protected ChatClientRequest doInitializeLoop(
            ChatClientRequest request,
            CallAdvisorChain chain
    ) {
        return request.mutate()
                .context(TURN_STATE_KEY, new TurnState())
                .build();
    }

    @Override
    protected ChatClientRequest doInitializeLoopStream(
            ChatClientRequest request,
            StreamAdvisorChain chain
    ) {
        return request.mutate()
                .context(TURN_STATE_KEY, new TurnState())
                .build();
    }

    @Override
    protected ChatClientResponse doAfterCall(
            ChatClientResponse response,
            CallAdvisorChain chain
    ) {

        enforceIterationLimit(response);

        return response;
    }

    @Override
    protected ChatClientResponse doAfterStream(
            ChatClientResponse response,
            StreamAdvisorChain chain
    ) {

        enforceIterationLimit(response);

        return response;
    }

    private void enforceIterationLimit(
            ChatClientResponse response
    ) {

        ChatResponse chatResponse = response.chatResponse();

        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return;
        }

        TurnState state = turnState(response.context());

        int requestedIteration = state.increment();

        observability.recordToolCallingIteration();

        /*
         * ToolCallingAdvisor invokes this hook before executing the
         * tool calls requested by this model response.
         *
         * Therefore, the over-limit batch is rejected before its
         * tools are executed.
         */
        if (requestedIteration > maxIterations) {

            observability.recordToolLimitExceeded("iteration");

            throw new AgentToolLimitExceededException(
                    AgentToolLimitExceededException.Reason.ITERATION_LIMIT
            );
        }
    }

    ChatClientResponse rejectFrameworkToolLimit(
            ChatClientResponse response
    ) {

        ChatResponse chatResponse = response.chatResponse();

        if (chatResponse == null) {
            return response;
        }

        boolean limitExceeded = chatResponse.getResults()
                .stream()
                .map(Generation::getMetadata)
                .map(ChatGenerationMetadata::getFinishReason)
                .anyMatch(ToolCallLimitExceededException.FINISH_REASON::equals);

        if (limitExceeded) {

            observability.recordToolLimitExceeded("tool_call");

            throw new AgentToolLimitExceededException(
                    AgentToolLimitExceededException.Reason.TOOL_CALL_LIMIT
            );
        }

        return response;
    }

    private TurnState turnState(
            Map<String, Object> context
    ) {

        Object state = context.get(TURN_STATE_KEY);

        if (state instanceof TurnState turnState) {
            return turnState;
        }

        throw new IllegalStateException(
                "Tool-calling turn state was not propagated through the advisor chain."
        );
    }

    static final class TurnState {

        private final AtomicInteger requestedToolRounds =
                new AtomicInteger();

        int increment() {
            return requestedToolRounds.incrementAndGet();
        }
    }
}
