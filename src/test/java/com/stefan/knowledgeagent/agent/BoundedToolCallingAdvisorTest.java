package com.stefan.knowledgeagent.agent;

import com.stefan.knowledgeagent.common.exception.AgentToolLimitExceededException;
import com.stefan.knowledgeagent.observability.AgentObservability;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BoundedToolCallingAdvisorTest {

    @Test
    void rejectsToolCallingRoundAfterConfiguredMaximum() {

        BoundedToolCallingAdvisor advisor = createAdvisor(2);
        BoundedToolCallingAdvisor.TurnState state =
                new BoundedToolCallingAdvisor.TurnState();

        advisor.doAfterCall(toolCallResponse(state), mock(CallAdvisorChain.class));
        advisor.doAfterCall(toolCallResponse(state), mock(CallAdvisorChain.class));

        assertThatThrownBy(() ->
                advisor.doAfterCall(
                        toolCallResponse(state),
                        mock(CallAdvisorChain.class)
                )
        ).isInstanceOfSatisfying(
                AgentToolLimitExceededException.class,
                exception -> assertThat(exception.reason())
                        .isEqualTo(
                                AgentToolLimitExceededException.Reason.ITERATION_LIMIT
                        )
        );
    }

    @Test
    void allowsFinalTextAnswerAtIterationLimit() {

        BoundedToolCallingAdvisor advisor = createAdvisor(2);
        BoundedToolCallingAdvisor.TurnState state =
                new BoundedToolCallingAdvisor.TurnState();

        advisor.doAfterCall(toolCallResponse(state), mock(CallAdvisorChain.class));
        advisor.doAfterCall(toolCallResponse(state), mock(CallAdvisorChain.class));

        ChatClientResponse finalResponse = ChatClientResponse.builder()
                .chatResponse(new ChatResponse(
                        List.of(new Generation(
                                new AssistantMessage("The answer is complete.")
                        ))
                ))
                .context(Map.of(
                        BoundedToolCallingAdvisor.TURN_STATE_KEY,
                        state
                ))
                .build();

        assertThat(advisor.doAfterCall(
                finalResponse,
                mock(CallAdvisorChain.class)
        )).isSameAs(finalResponse);
    }

    @Test
    void translatesSpringAiToolLimitGenerationToApplicationException() {

        BoundedToolCallingAdvisor advisor = createAdvisor(2);

        ChatGenerationMetadata metadata =
                ChatGenerationMetadata.builder()
                        .finishReason(
                                ToolCallLimitExceededException.FINISH_REASON
                        )
                        .build();

        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage("Tool call limit exceeded."),
                        metadata
                ))
        );

        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        assertThatThrownBy(() ->
                advisor.rejectFrameworkToolLimit(response)
        ).isInstanceOfSatisfying(
                AgentToolLimitExceededException.class,
                exception -> assertThat(exception.reason())
                        .isEqualTo(
                                AgentToolLimitExceededException.Reason.TOOL_CALL_LIMIT
                        )
        );
    }

    private BoundedToolCallingAdvisor createAdvisor(int maxIterations) {

        ToolCallingManager manager = ToolCallingManager.builder()
                .observationRegistry(ObservationRegistry.NOOP)
                .build();

        return new BoundedToolCallingAdvisor(
                manager,
                maxIterations,
                AgentObservability.noop()
        );
    }

    private ChatClientResponse toolCallResponse(
            BoundedToolCallingAdvisor.TurnState state
    ) {

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "lookup_order",
                        "{}"
                )))
                .build();

        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(
                        List.of(new Generation(assistantMessage))
                ))
                .context(Map.of(
                        BoundedToolCallingAdvisor.TURN_STATE_KEY,
                        state
                ))
                .build();
    }
}
