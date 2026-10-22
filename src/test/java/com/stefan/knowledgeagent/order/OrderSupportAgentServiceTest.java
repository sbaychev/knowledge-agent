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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSupportAgentServiceTest {

    private static final String USER_MESSAGE =
            "Give me a complete status summary for order 1.";

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

        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(MODEL_ANSWER);

        service = new OrderSupportAgentService(
                chatClientBuilder,
                orderLookupTool,
                shipmentLookupTool,
                paymentLookupTool,
                refundLookupTool
        );
    }

    @Test
    void registersAllReadOnlyToolsAndReturnsModelAnswer() {

        String answer = service.answer(USER_MESSAGE);

        assertThat(answer).isEqualTo(MODEL_ANSWER);

        verify(chatClient).prompt();
        verify(requestSpec).user(USER_MESSAGE);
        verify(requestSpec).tools(
                orderLookupTool,
                shipmentLookupTool,
                paymentLookupTool,
                refundLookupTool
        );
        verify(requestSpec).call();
        verify(responseSpec).content();
    }

    @Test
    void includesAllToolDescriptionsInSystemPrompt() {

        service.answer(USER_MESSAGE);

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
