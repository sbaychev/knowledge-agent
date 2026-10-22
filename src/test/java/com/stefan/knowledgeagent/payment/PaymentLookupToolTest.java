package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.order.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLookupToolTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentLookupTool tool;

    @Test
    void returnsPaymentAndRefundSummary() {

        long orderId = 42L;

        Payment payment = mock(Payment.class);

        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(paymentService.getPaymentForOrder(orderId)).thenReturn(payment);
        when(payment.getId()).thenReturn(9L);
        when(payment.getStatus()).thenReturn(PaymentStatus.PARTIALLY_REFUNDED);
        when(payment.getAmount()).thenReturn(new BigDecimal("120.00"));
        when(payment.getAmountRefunded()).thenReturn(new BigDecimal("20.00"));
        when(payment.getCurrency()).thenReturn("EUR");
        when(payment.getCapturedAt())
                .thenReturn(Instant.parse("2026-10-20T10:00:00Z"));

        String result = tool.lookupPaymentByOrder(orderId);

        assertThat(result)
                .contains("Payment ID: 9")
                .contains("Status: PARTIALLY_REFUNDED")
                .contains("Amount: 120.00 EUR")
                .contains("Amount refunded: 20.00 EUR");
    }

    @Test
    void distinguishesUnknownOrder() {

        when(orderRepository.existsById(999L)).thenReturn(false);

        assertThat(tool.lookupPaymentByOrder(999L))
                .contains("ORDER_NOT_FOUND");

        verifyNoInteractions(paymentService);
    }

    @Test
    void reportsExistingOrderWithoutPayment() {

        when(orderRepository.existsById(42L)).thenReturn(true);
        when(paymentService.getPaymentForOrder(42L)).thenThrow(
                new ResourceNotFoundException(
                        "PAYMENT_NOT_FOUND", "No payment"
                )
        );

        assertThat(tool.lookupPaymentByOrder(42L))
                .contains("PAYMENT_NOT_FOUND")
                .contains("exists, but no payment is recorded");
    }
}
