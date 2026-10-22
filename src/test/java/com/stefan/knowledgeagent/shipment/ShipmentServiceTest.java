package com.stefan.knowledgeagent.shipment;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.order.OrderRepository;
import com.stefan.knowledgeagent.order.OrderStatus;
import com.stefan.knowledgeagent.order.exception.InvalidOrderStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ShipmentService shipmentService;

    @Test
    void shouldDispatchProcessingOrder() {
        // Arrange
        Long orderId = 42L;

        Order order = new Order(mock(Customer.class), "EUR");
        order.startProcessing();

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Shipment shipment = shipmentService.dispatchOrder(
                orderId,
                "DHL",
                "TRACK-4821"
        );

        // Assert
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.getStatus());
        assertEquals("DHL", shipment.getCarrier());
        assertEquals("TRACK-4821", shipment.getTrackingNumber());

        verify(shipmentRepository).save(any(Shipment.class));
    }

    @Test
    void shouldNotDispatchOrderThatIsNotProcessing() {
        // Arrange
        Long orderId = 42L;

        Order order = new Order(mock(Customer.class), "EUR");

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        // Act + Assert
        assertThrows(
                InvalidOrderStateException.class,
                () -> shipmentService.dispatchOrder(
                        orderId,
                        "DHL",
                        "TRACK-4821"
                )
        );

        verify(shipmentRepository, never()).save(any(Shipment.class));
        assertEquals(OrderStatus.CREATED, order.getStatus());
    }

    @Test
    void shouldMarkShipmentAndOrderDelivered() {
        // Arrange
        Long shipmentId = 100L;

        Order order = new Order(mock(Customer.class), "EUR");
        order.startProcessing();
        order.markShipped();

        Shipment shipment = new Shipment(
                order,
                "DHL",
                "TRACK-4821"
        );

        when(shipmentRepository.findById(shipmentId))
                .thenReturn(Optional.of(shipment));

        // Act
        Shipment result = shipmentService.markDelivered(shipmentId);

        // Assert
        assertEquals(ShipmentStatus.DELIVERED, result.getStatus());
        assertNotNull(result.getDeliveredAt());
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void shouldNotDispatchNonExistingOrder() {

        Long orderId = 999L;

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> shipmentService.dispatchOrder(
                        orderId, "DHL", "TRACK-999"
                )
        );

        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    @Test
    void shouldNotDeliverNonExistingShipment() {

        Long shipmentId = 999L;

        when(shipmentRepository.findById(shipmentId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> shipmentService.markDelivered(shipmentId)
        );
    }
}
