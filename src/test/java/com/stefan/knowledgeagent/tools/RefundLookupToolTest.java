package com.stefan.knowledgeagent.tools;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.order.OrderRepository;
import com.stefan.knowledgeagent.payment.Payment;
import com.stefan.knowledgeagent.payment.PaymentService;
import com.stefan.knowledgeagent.payment.Refund;
import com.stefan.knowledgeagent.payment.RefundRepository;
import com.stefan.knowledgeagent.payment.RefundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundLookupToolTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private RefundLookupTool tool;

    @Test
    void returnsRecentRefundDetails() {

        long orderId = 42L;

        Payment payment = mock(Payment.class);
        Refund refund = mock(Refund.class);

        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(paymentService.getPaymentForOrder(orderId)).thenReturn(payment);
        when(payment.getId()).thenReturn(9L);

        when(refundRepository.findTop10ByPaymentIdOrderByCreatedAtDesc(9L))
                .thenReturn(List.of(refund));

        when(refund.getId()).thenReturn(11L);
        when(refund.getStatus()).thenReturn(RefundStatus.COMPLETED);
        when(refund.getAmount()).thenReturn(new BigDecimal("25.00"));
        when(refund.getCurrency()).thenReturn("EUR");
        when(refund.getReason()).thenReturn("Returned item");
        when(refund.getCreatedAt())
                .thenReturn(Instant.parse("2026-10-20T10:00:00Z"));
        when(refund.getCompletedAt())
                .thenReturn(Instant.parse("2026-10-21T10:00:00Z"));

        String result = tool.lookupRefundsByOrder(orderId);

        assertThat(result)
                .contains("Order ID: 42")
                .contains("Payment ID: 9")
                .contains("Refund ID: 11")
                .contains("Status: COMPLETED")
                .contains("Amount: 25.00 EUR")
                .contains("Reason: Returned item");
    }

    @Test
    void reportsNoRefundsWhenPaymentHasNoRefundRecords() {

        Payment payment = mock(Payment.class);

        when(orderRepository.existsById(42L)).thenReturn(true);
        when(paymentService.getPaymentForOrder(42L)).thenReturn(payment);
        when(payment.getId()).thenReturn(9L);

        when(refundRepository.findTop10ByPaymentIdOrderByCreatedAtDesc(9L))
                .thenReturn(List.of());

        assertThat(tool.lookupRefundsByOrder(42L))
                .contains("NO_REFUNDS_FOUND");
    }

    @Test
    void reportsNoRefundsWhenPaymentDoesNotExist() {

        when(orderRepository.existsById(42L)).thenReturn(true);
        when(paymentService.getPaymentForOrder(42L)).thenThrow(
                new ResourceNotFoundException(
                        "PAYMENT_NOT_FOUND", "No payment"
                )
        );

        assertThat(tool.lookupRefundsByOrder(42L))
                .contains("NO_REFUNDS_FOUND")
                .contains("no payment record");

        verifyNoInteractions(refundRepository);
    }
}
