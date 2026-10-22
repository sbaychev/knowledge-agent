package com.stefan.knowledgeagent.shipment;

import com.stefan.knowledgeagent.PostgresTestConfiguration;
import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.customer.CustomerRepository;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.order.OrderRepository;
import com.stefan.knowledgeagent.order.OrderService;
import com.stefan.knowledgeagent.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class ShipmentIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentService shipmentService;

    @Test
    void shouldPersistShipmentAndOrderStatus() {

        // Arrange
        Order order = createProcessingOrder();

        // Act
        Shipment created = shipmentService.dispatchOrder(
                order.getId(),
                "DHL",
                "TRACK-" + UUID.randomUUID()
        );

        // Assert: reload from PostgreSQL after the service transaction.
        Order persistedOrder = orderRepository.findById(order.getId())
                .orElseThrow();

        Shipment persistedShipment =
                shipmentRepository.findByOrderId(order.getId())
                        .orElseThrow();

        assertNotNull(created.getId());
        assertEquals(OrderStatus.SHIPPED, persistedOrder.getStatus());
        assertEquals(ShipmentStatus.IN_TRANSIT, persistedShipment.getStatus());
        assertEquals(order.getId(), persistedShipment.getOrder().getId());
    }

    @Test
    void shouldRollbackOrderStatusIfShipmentInsertFails() {

        // Arrange
        Order order = createProcessingOrder();

        // Act: carrier exceeds the database column length.
        assertThrows(
                RuntimeException.class,
                () -> shipmentService.dispatchOrder(
                        order.getId(),
                        "C".repeat(101),
                        "TRACK-ROLLBACK"
                )
        );

        // Assert: the transaction did not leave the order marked SHIPPED.
        Order persistedOrder = orderRepository.findById(order.getId())
                .orElseThrow();

        assertEquals(OrderStatus.PROCESSING, persistedOrder.getStatus());

        assertTrue(
                shipmentRepository.findByOrderId(order.getId()).isEmpty()
        );
    }

    private Order createProcessingOrder() {

        Customer customer = createCustomer();

        Order order = orderRepository.saveAndFlush(
                new Order(customer, "EUR")
        );

        orderService.startProcessing(order.getId());

        return orderRepository.findById(order.getId())
                .orElseThrow();
    }

    private Customer createCustomer() {

        String uniqueId = UUID.randomUUID().toString();

        Long customerId = jdbcTemplate.queryForObject(
                """
                INSERT INTO customer
                    (external_id, name, email, status, created_at)
                VALUES
                    (?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                "IT-" + uniqueId,
                "Shipment Integration Test",
                uniqueId + "@example.test",
                "ACTIVE"
        );

        return customerRepository.findById(customerId)
                .orElseThrow();
    }
}
