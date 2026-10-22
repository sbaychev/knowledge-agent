package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.CreateShipmentRequest;
import com.stefan.knowledgeagent.api.dto.ShipmentResponse;
import com.stefan.knowledgeagent.shipment.Shipment;
import com.stefan.knowledgeagent.shipment.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping("/orders/{orderId}/shipment")
    public ResponseEntity<ShipmentResponse> dispatchOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CreateShipmentRequest request) {

        Shipment shipment = shipmentService.dispatchOrder(
                orderId,
                request.carrier(),
                request.trackingNumber()
        );

        return ResponseEntity
                .created(URI.create("/api/v1/shipments/" + shipment.getId()))
                .body(ShipmentResponse.from(shipment));
    }

    @GetMapping("/shipments/{shipmentId}")
    public ShipmentResponse getShipment(
            @PathVariable Long shipmentId) {

        return ShipmentResponse.from(
                shipmentService.getShipment(shipmentId)
        );
    }

    @GetMapping("/orders/{orderId}/shipment")
    public ShipmentResponse getShipmentForOrder(
            @PathVariable Long orderId) {

        return ShipmentResponse.from(
                shipmentService.getShipmentForOrder(orderId)
        );
    }

    @PostMapping("/shipments/{shipmentId}/delivery")
    public ResponseEntity<Void> markDelivered(
            @PathVariable Long shipmentId) {

        shipmentService.markDelivered(shipmentId);

        return ResponseEntity.noContent().build();
    }
}
