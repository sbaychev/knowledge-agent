package com.stefan.knowledgeagent.tools;

import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.order.OrderItem;
import com.stefan.knowledgeagent.order.OrderRepository;
import com.stefan.knowledgeagent.order.OrderSupportAgentService;
import com.stefan.knowledgeagent.payment.PaymentService;
import com.stefan.knowledgeagent.payment.RefundRepository;
import com.stefan.knowledgeagent.shipment.ShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderSupportAgentToolCallingTest {

    private static final long ORDER_ID = 1L;

    private static final String CONVERSATION_ID =
            "tool-calling-test-conversation";

    private static final String FINAL_ANSWER =
            "Order #1 is SHIPPED.";

    @Test
    void executesOrderToolThenReturnsFinalModelAnswer() {
        // Arrange: a real Order entity and the repository used by the tool.
        OrderRepository orderRepository = mock(OrderRepository.class);

        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(1L);

        Order order = new Order(customer, "EUR");

        order.addItem(new OrderItem(
                "SKU-LAPTOP-001",
                "Laptop",
                1,
                new BigDecimal("650.00")
        ));

        order.startProcessing();
        order.markShipped();

        // The entity is not persisted in this unit test.
        ReflectionTestUtils.setField(order, "id", ORDER_ID);

        when(orderRepository.findDetailedById(ORDER_ID))
                .thenReturn(Optional.of(order));

        // Use the real application tool classes.
        OrderLookupTool orderLookupTool =
                new OrderLookupTool(orderRepository);

        ShipmentLookupTool shipmentLookupTool =
                new ShipmentLookupTool(
                        mock(ShipmentService.class),
                        orderRepository
                );

        PaymentService paymentService = mock(PaymentService.class);

        PaymentLookupTool paymentLookupTool =
                new PaymentLookupTool(
                        paymentService,
                        orderRepository
                );

        RefundLookupTool refundLookupTool =
                new RefundLookupTool(
                        paymentService,
                        mock(RefundRepository.class),
                        orderRepository
                );

        // The model first requests lookup_order, then returns the answer.
        ScriptedChatModel chatModel = new ScriptedChatModel(
                toolCallResponse(
                        "call-order-1",
                        "lookup_order",
                        """
                        {"orderId":1}
                        """
                ),
                textResponse(FINAL_ANSWER)
        );

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();

        OrderSupportAgentService service =
                new OrderSupportAgentService(
                        ChatClient.builder(chatModel),
                        chatMemory,
                        orderLookupTool,
                        shipmentLookupTool,
                        paymentLookupTool,
                        refundLookupTool
                );

        // Act: invoke the actual service and actual ChatClient tool loop.
        String answer = service.answer(
                "What is the current status of order 1?",
                CONVERSATION_ID
        );

        // Assert: the final response came from the second model call.
        assertThat(answer).isEqualTo(FINAL_ANSWER);
        assertThat(chatModel.callCount()).isEqualTo(2);

        // The tool actually executed against the mocked repository.
        verify(orderRepository).findDetailedById(ORDER_ID);
    }

    private static ChatResponse toolCallResponse(
            String callId,
            String toolName,
            String arguments
    ) {
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall(
                        callId,
                        "function",
                        toolName,
                        arguments
                );

        AssistantMessage assistantMessage =
                AssistantMessage.builder()
                        .toolCalls(List.of(toolCall))
                        .build();

        return new ChatResponse(
                List.of(new Generation(assistantMessage))
        );
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text)))
        );
    }

    /**
     * Deterministic ChatModel for testing the tool-calling loop.
     * It never makes network requests.
     */
    private static final class ScriptedChatModel implements ChatModel {

        private final Queue<ChatResponse> responses = new ArrayDeque<>();
        private int calls;

        private ScriptedChatModel(ChatResponse... responses) {
            Collections.addAll(this.responses, responses);
        }

        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls++;

            ChatResponse response = responses.poll();

            if (response == null) {
                throw new IllegalStateException(
                        "Unexpected ChatModel call number: " + calls
                );
            }

            return response;
        }

        private int callCount() {
            return calls;
        }
    }
}
