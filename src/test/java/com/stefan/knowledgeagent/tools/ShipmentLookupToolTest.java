package com.stefan.knowledgeagent.tools;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.order.OrderRepository;
import com.stefan.knowledgeagent.shipment.Shipment;
import com.stefan.knowledgeagent.shipment.ShipmentService;
import com.stefan.knowledgeagent.shipment.ShipmentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentLookupToolTest {

    @Mock
    private ShipmentService shipmentService;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ShipmentLookupTool tool;

    @Test
    void returnsShipmentDetailsForExistingOrder() {

        long orderId = 42L;

        Shipment shipment = mock(Shipment.class);

        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(shipmentService.getShipmentForOrder(orderId)).thenReturn(shipment);
        when(shipment.getId()).thenReturn(7L);
        when(shipment.getStatus()).thenReturn(ShipmentStatus.IN_TRANSIT);
        when(shipment.getCarrier()).thenReturn("DHL");
        when(shipment.getTrackingNumber()).thenReturn("TRACK-42");
        when(shipment.getShippedAt())
                .thenReturn(Instant.parse("2026-10-20T10:00:00Z"));

        String result = tool.lookupShipmentByOrder(orderId);

        assertThat(result)
                .contains("Shipment ID: 7")
                .contains("Status: IN_TRANSIT")
                .contains("Carrier: DHL")
                .contains("Tracking number: TRACK-42")
                .contains("Delivered at: Not delivered");

        verify(shipmentService).getShipmentForOrder(orderId);
    }

    @Test
    void distinguishesUnknownOrder() {

        when(orderRepository.existsById(999L)).thenReturn(false);

        assertThat(tool.lookupShipmentByOrder(999L))
                .contains("ORDER_NOT_FOUND");

        verifyNoInteractions(shipmentService);
    }

    @Test
    void reportsExistingOrderWithoutShipment() {

        when(orderRepository.existsById(42L)).thenReturn(true);
        when(shipmentService.getShipmentForOrder(42L)).thenThrow(
                new ResourceNotFoundException(
                        "SHIPMENT_NOT_FOUND", "No shipment"
                )
        );

        assertThat(tool.lookupShipmentByOrder(42L))
                .contains("SHIPMENT_NOT_FOUND")
                .contains("exists, but it has no shipment record");
    }

    @Test
    void rejectsNonPositiveOrderIdWithoutDatabaseAccess() {

        assertThat(tool.lookupShipmentByOrder(0L))
                .contains("INVALID_ORDER_ID");

        verifyNoInteractions(orderRepository, shipmentService);
    }
}
