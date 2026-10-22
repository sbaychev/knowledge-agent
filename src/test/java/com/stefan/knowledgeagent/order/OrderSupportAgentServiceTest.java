package com.stefan.knowledgeagent.order;

import com.stefan.knowledgeagent.payment.PaymentLookupTool;
import com.stefan.knowledgeagent.payment.RefundLookupTool;
import com.stefan.knowledgeagent.shipment.ShipmentLookupTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSupportAgentServiceTest {

    private static final String USER_MESSAGE =
            "Give me a complete status summary for order 1.";

    private static final String CONVERSATION_ID =
            "ac519fa5-aec4-4aa5-86b5-b326e8cc6f70";

    private static final String MODEL_ANSWER =
            "Order #1 is shipped and fully paid.";

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private ChatClient.AdvisorSpec advisorSpec;

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private OrderLookupTool orderLookupTool;

    @Mock
    private ShipmentLookupTool shipmentLookupTool;

    @Mock
    private PaymentLookupTool paymentLookupTool;

    @Mock
    private RefundLookupTool refundLookupTool;

    private OrderSupportAgentService service;

    @BeforeEach
    void setUp() {
        doReturn(chatClientBuilder)
                .when(chatClientBuilder)
                .defaultAdvisors(any(Advisor[].class));

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);

        when(requestSpec.tools(
                orderLookupTool,
                shipmentLookupTool,
                paymentLookupTool,
                refundLookupTool
        )).thenReturn(requestSpec);

        when(advisorSpec.param(anyString(), any()))
                .thenReturn(advisorSpec);

        doAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> configurer =
                    invocation.getArgument(0);

            configurer.accept(advisorSpec);
            return requestSpec;
        }).when(requestSpec).advisors(
                org.mockito.ArgumentMatchers
                        .<Consumer<ChatClient.AdvisorSpec>>any()
        );

        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(MODEL_ANSWER);

        service = new OrderSupportAgentService(
                chatClientBuilder,
                chatMemory,
                orderLookupTool,
                shipmentLookupTool,
                paymentLookupTool,
                refundLookupTool
        );
    }

    @Test
    void registersMemoryAdvisorAndAllReadOnlyTools() {
        String answer = service.answer(USER_MESSAGE, CONVERSATION_ID);

        assertThat(answer).isEqualTo(MODEL_ANSWER);

        verify(chatClient).prompt();
        verify(requestSpec).system(anyString());
        verify(requestSpec).user(USER_MESSAGE);

        verify(requestSpec).tools(
                orderLookupTool,
                shipmentLookupTool,
                paymentLookupTool,
                refundLookupTool
        );

        verify(requestSpec).advisors(
                org.mockito.ArgumentMatchers
                        .<Consumer<ChatClient.AdvisorSpec>>any()
        );

        verify(advisorSpec).param(
                ChatMemory.CONVERSATION_ID,
                CONVERSATION_ID
        );

        verify(requestSpec).call();
        verify(responseSpec).content();

        ArgumentCaptor<Advisor[]> advisorCaptor =
                ArgumentCaptor.forClass(Advisor[].class);

        verify(chatClientBuilder)
                .defaultAdvisors(advisorCaptor.capture());

        assertThat(advisorCaptor.getValue())
                .hasSize(1);

        assertThat(advisorCaptor.getValue()[0])
                .isInstanceOf(MessageChatMemoryAdvisor.class);
    }

    @Test
    void includesAllToolDescriptionsInSystemPrompt() {
        service.answer(USER_MESSAGE, CONVERSATION_ID);

        ArgumentCaptor<String> promptCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(requestSpec).system(promptCaptor.capture());

        assertThat(promptCaptor.getValue())
                .contains("lookup_order")
                .contains("lookup_shipment_by_order")
                .contains("lookup_payment_by_order")
                .contains("lookup_refunds_by_order")
                .contains("read-only");
    }
}
