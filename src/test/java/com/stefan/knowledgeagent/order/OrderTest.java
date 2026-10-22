package com.stefan.knowledgeagent.order;

import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.customer.CustomerStatus;
import com.stefan.knowledgeagent.order.exception.InvalidOrderStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OrderTest {

    private Order newOrder() {
        return new Order(mock(Customer.class), "EUR");
    }

    @Test
    void shouldStartInCreatedState() {

        Order order = newOrder();

        assertEquals(OrderStatus.CREATED, order.getStatus());
    }

    @Test
    void shouldStartProcessingWhenCreated() {

        Order order = newOrder();

        order.startProcessing();

        assertEquals(OrderStatus.PROCESSING, order.getStatus());
    }

    @Test
    void shouldNotStartProcessingTwice() {
        Order order = newOrder();
        order.startProcessing();

        assertThrows(
                InvalidOrderStateException.class,
                order::startProcessing
        );
    }

    @Test
    void shouldNotShipOrderBeforeProcessing() {
        Order order = newOrder();

        assertThrows(
                InvalidOrderStateException.class,
                order::markShipped
        );
    }

    @Test
    void shouldMarkDeliveredAfterShipping() {
        Order order = newOrder();
        order.startProcessing();
        order.markShipped();

        order.markDelivered();

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void shouldNotMarkOrderDeliveredBeforeShipping() {
        Order order = newOrder();

        assertThrows(
                InvalidOrderStateException.class,
                order::markDelivered
        );
    }

    @Test
    void shouldCancelCreatedOrder() {
        Order order = newOrder();

        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void shouldCancelProcessingOrder() {

        Order order = newOrder();
        order.startProcessing();

        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }


    @Test
    void shouldNotCancelDeliveredOrder() {
        Order order = newOrder();
        order.startProcessing();
        order.markShipped();
        order.markDelivered();

        assertThrows(
                InvalidOrderStateException.class,
                order::cancel
        );
    }

    @Test
    void shouldNotCancelAlreadyCancelledOrder() {
        Order order = newOrder();
        order.cancel();

        assertThrows(
                InvalidOrderStateException.class,
                order::cancel
        );
    }

    @Test
    void shouldCalculateTotalFromItems() {

        Customer customer = new Customer(
                "C-1001",
                "John Smith",
                "john@example.com",
                CustomerStatus.ACTIVE,
                Instant.now()
        );

        Order order = new Order(customer, "EUR");

        order.addItem(new OrderItem(
                        "P-1",
                        "MacBook Pro",
                        1,
                        new BigDecimal("600")
                )
        );

        order.addItem(new OrderItem(
                        "P-2",
                        "USB-C Adapter",
                        2,
                        new BigDecimal("60")
                )
        );

        assertThat(order.getTotalAmount())
                .isEqualByComparingTo("720");
    }

    @Test
    void shouldNotCancelShippedOrder() {

        // arrange order in SHIPPED state
        Customer customer = new Customer(
                "C-1001",
                "John Smith",
                "john@example.com",
                CustomerStatus.ACTIVE,
                Instant.now()
        );

        // arrange
        Order order = new Order(customer, "EUR");

        order.startProcessing();

        order.markShipped();

        // act
        // assert exception
        // act & assert
        assertThrows(
                InvalidOrderStateException.class,
                order::cancel
        );
    }
}
