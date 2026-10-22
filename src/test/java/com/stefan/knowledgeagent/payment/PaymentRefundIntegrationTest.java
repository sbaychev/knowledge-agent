package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.PostgresTestConfiguration;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.order.OrderService;
import com.stefan.knowledgeagent.payment.exception.RefundAmountExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class PaymentRefundIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundService refundService;

    @Autowired
    private RefundRepository refundRepository;

    @Test
    void shouldReserveAmountsIdempotentlyAndCompleteFullRefund() {

        // Arrange: create a real customer, order and captured payment.
        Long customerId = createCustomer();

        Order order = orderService.createOrder(
                customerId,
                List.of(
                        new OrderService.CreateOrderItem(
                                "SKU-100",
                                "Test product",
                                1,
                                new BigDecimal("100.00")
                        )
                ),
                "EUR"
        );

        Payment payment = paymentService.recordCapturedPayment(
                order.getId(),
                "PAY-" + UUID.randomUUID()
        );

        // Act: reserve 70 EUR.
        Refund first = refundService.requestRefund(
                payment.getId(),
                new BigDecimal("70.00"),
                "refund-70",
                "First partial refund"
        );

        // Retrying the same request must not create another refund.
        Refund repeated = refundService.requestRefund(
                payment.getId(),
                new BigDecimal("70.00"),
                "refund-70",
                "First partial refund"
        );

        assertEquals(first.getId(), repeated.getId());
        assertEquals(1, refundRepository
                .findByPaymentIdAndIdempotencyKey(
                        payment.getId(), "refund-70"
                ).stream().count());

        // Only 30 EUR remains reserved or refundable.
        assertThrows(
                RefundAmountExceededException.class,
                () -> refundService.requestRefund(
                        payment.getId(),
                        new BigDecimal("40.00"),
                        "refund-too-large",
                        "Excess refund"
                )
        );

        // The remaining 30 EUR is still allowed.
        Refund second = refundService.requestRefund(
                payment.getId(),
                new BigDecimal("30.00"),
                "refund-30",
                "Final partial refund"
        );

        assertEquals(RefundStatus.REQUESTED, first.getStatus());
        assertEquals(RefundStatus.REQUESTED, second.getStatus());

        // Simulate successful confirmation from the payment provider.
        refundService.completeRefund(first.getId());
        refundService.completeRefund(second.getId());

        // Assert: reload the persisted state.
        Payment persistedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        Refund persistedFirst = refundRepository
                .findById(first.getId())
                .orElseThrow();

        Refund persistedSecond = refundRepository
                .findById(second.getId())
                .orElseThrow();

        assertEquals(PaymentStatus.REFUNDED, persistedPayment.getStatus());
        assertEquals(
                0,
                new BigDecimal("100.00")
                        .compareTo(persistedPayment.getAmountRefunded())
        );
        assertEquals(RefundStatus.COMPLETED, persistedFirst.getStatus());
        assertEquals(RefundStatus.COMPLETED, persistedSecond.getStatus());
    }

    private Long createCustomer() {

        String uniqueId = UUID.randomUUID().toString();

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO customer (
                    external_id,
                    name,
                    email,
                    status,
                    created_at
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                "PAYMENT-IT-" + uniqueId,
                "Payment Integration Test",
                uniqueId + "@example.test",
                "ACTIVE"
        );
    }
}
