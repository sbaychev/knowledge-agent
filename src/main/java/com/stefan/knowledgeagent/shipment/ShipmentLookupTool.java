package com.stefan.knowledgeagent.shipment;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.order.OrderRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ShipmentLookupTool {

    private final ShipmentService shipmentService;
    private final OrderRepository orderRepository;

    public ShipmentLookupTool(ShipmentService shipmentService, OrderRepository orderRepository) {
        this.shipmentService = shipmentService;
        this.orderRepository = orderRepository;
    }

    @Tool(
            name = "lookup_shipment_by_order",
            description = """
                    Look up shipment details for an order using its internal numeric
                    order ID. Returns shipment status, carrier, tracking number,
                    shipped timestamp and delivered timestamp. Read-only.
                    """
    )
    public String lookupShipmentByOrder(
            @ToolParam(description = "The internal numeric ID of the order")
            long orderId
    ) {

        if (orderId <= 0) {
            return "INVALID_ORDER_ID: The order ID must be positive.";
        }

        if (!orderRepository.existsById(orderId)) {
            return "ORDER_NOT_FOUND: No order exists with ID "
                    + orderId + ".";
        }

        final Shipment shipment;

        try {

            shipment = shipmentService.getShipmentForOrder(orderId);

        } catch (ResourceNotFoundException exception) {

            if (!"SHIPMENT_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }

            return "SHIPMENT_NOT_FOUND: Order " + orderId
                    + " exists, but it has no shipment record.";
        }

        String deliveredAt = shipment.getDeliveredAt() == null
                ? "Not delivered"
                : shipment.getDeliveredAt().toString();

        return """
                Shipment found.
                Order ID: %d
                Shipment ID: %d
                Status: %s
                Carrier: %s
                Tracking number: %s
                Shipped at: %s
                Delivered at: %s
                """.formatted(
                orderId,
                shipment.getId(),
                shipment.getStatus().name(),
                shipment.getCarrier(),
                shipment.getTrackingNumber(),
                shipment.getShippedAt(),
                deliveredAt
        );
    }

}
