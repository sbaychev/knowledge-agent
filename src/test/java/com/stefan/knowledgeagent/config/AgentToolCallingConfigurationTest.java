package com.stefan.knowledgeagent.config;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolCallingConfigurationTest {

    @Test
    void enforcesTotalToolCallLimitAcrossDifferentTools() {

        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();

        ToolCallback first = callback("tool_a", firstCalls);
        ToolCallback second = callback("tool_b", secondCalls);

        ToolCallingManager manager =
                AgentToolCallingConfiguration.createToolCallingManager(
                        ObservationRegistry.NOOP,
                        8,
                        1,
                        ToolCallLimitBehavior.THROW
                );

        assertThatThrownBy(() ->
                manager.executeToolCalls(
                        prompt(List.of(first, second)),
                        toolResponse("tool_a", "tool_b")
                )
        ).isInstanceOfSatisfying(
                ToolCallLimitExceededException.class,
                exception -> {
                    assertThat(exception.getToolName()).isNull();
                    assertThat(exception.getLimit()).isEqualTo(1);
                }
        );

        assertThat(firstCalls).hasValue(1);
        assertThat(secondCalls).hasValue(0);
    }

    @Test
    void enforcesPerToolLimit() {

        AtomicInteger calls = new AtomicInteger();

        ToolCallback callback = callback("tool_a", calls);

        ToolCallingManager manager =
                AgentToolCallingConfiguration.createToolCallingManager(
                        ObservationRegistry.NOOP,
                        1,
                        10,
                        ToolCallLimitBehavior.THROW
                );

        assertThatThrownBy(() ->
                manager.executeToolCalls(
                        prompt(List.of(callback)),
                        toolResponse("tool_a", "tool_a")
                )
        ).isInstanceOfSatisfying(
                ToolCallLimitExceededException.class,
                exception -> {
                    assertThat(exception.getToolName())
                            .isEqualTo("tool_a");
                    assertThat(exception.getLimit()).isEqualTo(1);
                }
        );

        assertThat(calls).hasValue(1);
    }

    private Prompt prompt(List<ToolCallback> callbacks) {

        return new Prompt(
                List.of(new UserMessage("Execute the requested tools.")),
                ToolCallingChatOptions.builder()
                        .toolCallbacks(callbacks)
                        .build()
        );
    }

    private ChatResponse toolResponse(String... names) {

        List<AssistantMessage.ToolCall> calls =
                java.util.stream.IntStream.range(0, names.length)
                        .mapToObj(index -> new AssistantMessage.ToolCall(
                                "call-" + index,
                                "function",
                                names[index],
                                "{}"
                        ))
                        .toList();

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(calls)
                .build();

        return new ChatResponse(
                List.of(new Generation(assistantMessage))
        );
    }

    private ToolCallback callback(
            String name,
            AtomicInteger invocationCount
    ) {

        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description("A test tool.")
                .inputSchema("""
                        {
                          "type": "object",
                          "properties": {}
                        }
                        """)
                .build();

        return new ToolCallback() {

            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                invocationCount.incrementAndGet();
                return "ok";
            }
        };
    }
}
