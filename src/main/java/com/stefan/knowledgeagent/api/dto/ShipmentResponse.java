package com.stefan.knowledgeagent.api.dto;

import com.stefan.knowledgeagent.shipment.Shipment;
import com.stefan.knowledgeagent.shipment.ShipmentStatus;

import java.time.Instant;

public record ShipmentResponse(

        Long id,
        Long orderId,
        String carrier,
        String trackingNumber,
        ShipmentStatus status,
        Instant shippedAt,
        Instant deliveredAt,
        Instant createdAt

) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getOrder().getId(),
                shipment.getCarrier(),
                shipment.getTrackingNumber(),
                shipment.getStatus(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt(),
                shipment.getCreatedAt()
        );
    }
}
