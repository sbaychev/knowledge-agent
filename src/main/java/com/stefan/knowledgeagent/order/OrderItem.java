package com.stefan.knowledgeagent.order;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 100)
    private String productId;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    protected OrderItem() {
        // JPA
    }

    public OrderItem(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice) {

        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException(
                    "productId must not be blank"
            );
        }

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException(
                    "productName must not be blank"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be greater than zero"
            );
        }

        if (unitPrice == null ||
                unitPrice.signum() < 0) {

            throw new IllegalArgumentException(
                    "unitPrice must not be negative"
            );
        }

        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    BigDecimal calculateTotal() {
        return unitPrice.multiply(
                BigDecimal.valueOf(quantity)
        );
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
