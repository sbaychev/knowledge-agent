package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.payment.exception.RefundAmountExceededException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PaymentTest {

    private Payment newPayment(String amount) {

        Order order = new Order(mock(Customer.class), "EUR");

        return new Payment(
                order,
                new BigDecimal(amount),
                "EUR",
                "PAYMENT-REF-001"
        );
    }

    @Test
    void shouldRegisterPartialRefund() {

        Payment payment = newPayment("100.00");

        payment.registerRefund(new BigDecimal("25.00"));

        assertEquals(
                PaymentStatus.PARTIALLY_REFUNDED,
                payment.getStatus()
        );
        assertEquals(
                0,
                new BigDecimal("25.00")
                        .compareTo(payment.getAmountRefunded())
        );
        assertEquals(
                0,
                new BigDecimal("75.00")
                        .compareTo(payment.getRemainingRefundableAmount())
        );
    }

    @Test
    void shouldMarkPaymentRefundedWhenFullAmountIsRefunded() {

        Payment payment = newPayment("100.00");

        payment.registerRefund(new BigDecimal("100.00"));

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        payment.getRemainingRefundableAmount()
                )
        );
    }

    @Test
    void shouldRejectRefundAbovePaymentAmount() {

        Payment payment = newPayment("100.00");

        assertThrows(
                RefundAmountExceededException.class,
                () -> payment.registerRefund(new BigDecimal("100.01"))
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(payment.getAmountRefunded())
        );
        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
    }
}
