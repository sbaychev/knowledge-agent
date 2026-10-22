package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.payment.exception.RefundAmountExceededException;
import com.stefan.knowledgeagent.payment.exception.RefundIdempotencyConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Long REFUND_ID = 20L;
    private static final String IDEMPOTENCY_KEY = "refund-key-001";
    private static final String REASON = "Customer returned an item";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    private RefundService refundService;
    private Payment payment;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                paymentRepository,
                refundRepository
        );

        Order order = new Order(mock(Customer.class), "EUR");

        payment = new Payment(
                order,
                new BigDecimal("100.00"),
                "EUR",
                "PAYMENT-REF-001"
        );

        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
    }

    @Test
    void shouldRequestRefundAndReserveAmount() {

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByPaymentIdAndIdempotencyKey(
                PAYMENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(refundRepository.sumReservedAmount(
                eq(PAYMENT_ID), anyCollection()))
                .thenReturn(BigDecimal.ZERO);

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Refund refund = refundService.requestRefund(
                PAYMENT_ID,
                new BigDecimal("25.00"),
                IDEMPOTENCY_KEY,
                REASON
        );

        assertEquals(RefundStatus.REQUESTED, refund.getStatus());
        assertEquals(0, new BigDecimal("25.00").compareTo(refund.getAmount()));

        // The amount is reserved, not yet recorded as successfully refunded.
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(payment.getAmountRefunded())
        );

        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void shouldReturnExistingRefundForIdenticalIdempotentRequest() {

        Refund existing = new Refund(
                payment,
                new BigDecimal("25.00"),
                "EUR",
                IDEMPOTENCY_KEY,
                REASON
        );

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByPaymentIdAndIdempotencyKey(
                PAYMENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        Refund result = refundService.requestRefund(
                PAYMENT_ID,
                new BigDecimal("25.0"),
                IDEMPOTENCY_KEY,
                REASON
        );

        assertSame(existing, result);

        verify(refundRepository, never()).save(any(Refund.class));
        verify(refundRepository, never()).sumReservedAmount(
                eq(PAYMENT_ID), anyCollection()
        );
    }

    @Test
    void shouldRejectIdempotencyKeyReusedWithDifferentAmount() {

        Refund existing = new Refund(
                payment,
                new BigDecimal("25.00"),
                "EUR",
                IDEMPOTENCY_KEY,
                REASON
        );

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByPaymentIdAndIdempotencyKey(
                PAYMENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        assertThrows(
                RefundIdempotencyConflictException.class,
                () -> refundService.requestRefund(
                        PAYMENT_ID,
                        new BigDecimal("30.00"),
                        IDEMPOTENCY_KEY,
                        REASON
                )
        );

        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void shouldRejectRefundAboveRemainingAvailableAmount() {

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByPaymentIdAndIdempotencyKey(
                PAYMENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        // 90 EUR is already requested or completed.
        when(refundRepository.sumReservedAmount(
                eq(PAYMENT_ID), anyCollection()))
                .thenReturn(new BigDecimal("90.00"));

        assertThrows(
                RefundAmountExceededException.class,
                () -> refundService.requestRefund(
                        PAYMENT_ID,
                        new BigDecimal("20.00"),
                        IDEMPOTENCY_KEY,
                        REASON
                )
        );

        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void shouldUpdatePaymentWhenRefundCompletes() {

        Refund requestedRefund = new Refund(
                payment,
                new BigDecimal("25.00"),
                "EUR",
                IDEMPOTENCY_KEY,
                REASON
        );

        ReflectionTestUtils.setField(
                requestedRefund, "id", REFUND_ID
        );

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(requestedRefund));

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByIdForUpdate(REFUND_ID))
                .thenReturn(Optional.of(requestedRefund));

        Refund result = refundService.completeRefund(REFUND_ID);

        assertEquals(RefundStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());

        assertEquals(
                0,
                new BigDecimal("25.00")
                        .compareTo(payment.getAmountRefunded())
        );

        assertEquals(
                PaymentStatus.PARTIALLY_REFUNDED,
                payment.getStatus()
        );
    }
}
