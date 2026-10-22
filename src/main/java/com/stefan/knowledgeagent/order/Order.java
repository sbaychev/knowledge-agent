package com.stefan.knowledgeagent.order;

import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.order.exception.InvalidOrderStateException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Order() {
        // JPA
    }

    public Order(Customer customer, String currency) {

        this.customer = customer;
        this.currency = currency;
        this.status = OrderStatus.CREATED;
        this.totalAmount = BigDecimal.ZERO;

    }

    public void addItem(OrderItem item) {

        if (item == null) {
            return;
        }

        items.add(item);
        item.setOrder(this);

        recalculateTotal();
    }

    public void removeItem(OrderItem item) {

        if (item == null) {
            return;
        }

        if (items.remove(item)) {
            item.setOrder(null);
            recalculateTotal();
        }
    }

    private void recalculateTotal() {

        this.totalAmount = items.stream()
                .map(OrderItem::calculateTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void cancel() {

        if (status == OrderStatus.SHIPPED ||
                status == OrderStatus.DELIVERED ||
                status == OrderStatus.CANCELLED) {

            throw new InvalidOrderStateException(
                    "Order " + id + " cannot be cancelled from status " + status
            );
        }

        status = OrderStatus.CANCELLED;
    }

    public void startProcessing() {

        if (status != OrderStatus.CREATED) {

            throw new InvalidOrderStateException(
                    "Order " + id
                            + " cannot start processing from status " + status
            );
        }

        status = OrderStatus.PROCESSING;
    }

    public void markShipped() {

        if (status != OrderStatus.PROCESSING) {

            throw new InvalidOrderStateException(
                    "Order " + id
                            + " cannot be shipped from status " + status
            );
        }

        status = OrderStatus.SHIPPED;
    }

    public void markDelivered() {

        if (status != OrderStatus.SHIPPED) {

            throw new InvalidOrderStateException(
                    "Order " + id
                            + " cannot be delivered from status " + status
            );
        }

        status = OrderStatus.DELIVERED;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
