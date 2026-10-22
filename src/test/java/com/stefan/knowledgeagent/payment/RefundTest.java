package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.payment.exception.InvalidRefundStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class RefundTest {

    private Refund newRefund() {

        Order order = new Order(mock(Customer.class), "EUR");

        Payment payment = new Payment(
                order,
                new BigDecimal("100.00"),
                "EUR",
                "PAYMENT-REF-001"
        );

        return new Refund(
                payment,
                new BigDecimal("25.00"),
                "EUR",
                "refund-key-001",
                "Customer returned an item"
        );
    }

    @Test
    void shouldStartRequested() {

        Refund refund = newRefund();

        assertEquals(RefundStatus.REQUESTED, refund.getStatus());
    }

    @Test
    void shouldCompleteRequestedRefund() {

        Refund refund = newRefund();

        refund.complete();

        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertNotNull(refund.getCompletedAt());
        assertNull(refund.getFailedAt());
    }

    @Test
    void shouldFailRequestedRefund() {

        Refund refund = newRefund();

        refund.fail();

        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertNotNull(refund.getFailedAt());
        assertNull(refund.getCompletedAt());
    }

    @Test
    void shouldNotCompleteRefundTwice() {

        Refund refund = newRefund();
        refund.complete();

        assertThrows(
                InvalidRefundStateException.class,
                refund::complete
        );
    }

    @Test
    void shouldNotFailCompletedRefund() {

        Refund refund = newRefund();
        refund.complete();

        assertThrows(
                InvalidRefundStateException.class,
                refund::fail
        );
    }
}
