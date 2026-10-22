package com.stefan.knowledgeagent.shipment;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.order.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;

    public static final String SHIPMENT_NOT_FOUND = "SHIPMENT_NOT_FOUND";
    public static final String SHIPMENT = "Shipment ";
    public static final String DOES_NOT_EXIST = " does not exist";

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            OrderRepository orderRepository) {

        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Shipment dispatchOrder(
            Long orderId,
            String carrier,
            String trackingNumber) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "ORDER_NOT_FOUND",
                                "Order " + orderId + DOES_NOT_EXIST
                        ));

        Shipment shipment = new Shipment(order, carrier, trackingNumber);

        // Enforces the Order state transition.
        order.markShipped();

        // The Order is already managed by JPA.
        // Its status change and the Shipment insert commit together.
        return shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment markDelivered(Long shipmentId) {

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                SHIPMENT_NOT_FOUND,
                                SHIPMENT + shipmentId + DOES_NOT_EXIST
                        ));

        shipment.markDelivered();

        // Keep the order and shipment states consistent.
        shipment.getOrder().markDelivered();

        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getShipment(Long shipmentId) {

        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                SHIPMENT_NOT_FOUND,
                                SHIPMENT + shipmentId + DOES_NOT_EXIST
                        ));
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentForOrder(Long orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                SHIPMENT_NOT_FOUND,
                                "No shipment exists for order " + orderId
                        ));
    }
}
