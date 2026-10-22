package com.stefan.knowledgeagent.shipment;

import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.shipment.exception.InvalidShipmentStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, length = 100)
    private String carrier;

    @Column(nullable = false, length = 150)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant shippedAt;

    private Instant deliveredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Shipment() {
        // Required by JPA.
    }

    public Shipment(Order order, String carrier, String trackingNumber) {

        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (carrier == null || carrier.isBlank()) {
            throw new IllegalArgumentException("carrier must not be blank");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "trackingNumber must not be blank"
            );
        }

        this.order = order;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.status = ShipmentStatus.IN_TRANSIT;
        this.shippedAt = Instant.now();

    }

    public void markDelivered() {

        if (status != ShipmentStatus.IN_TRANSIT) {
            throw new InvalidShipmentStateException(
                    "Shipment " + id
                            + " cannot be delivered from status " + status
            );
        }

        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
