package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.payment.exception.InvalidPaymentStateException;
import com.stefan.knowledgeagent.payment.exception.RefundAmountExceededException;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "amount_refunded", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountRefunded = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_reference", nullable = false, unique = true, length = 150)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Payment() {
        // Required by JPA
    }

    public Payment(
            Order order,
            BigDecimal amount,
            String currency,
            String providerReference) {

        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "currency must be a three-letter uppercase code"
            );
        }

        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("providerReference must not be blank");
        }

        if (!currency.equals(order.getCurrency())) {
            throw new IllegalArgumentException("Payment currency must match order currency");
        }

        this.order = order;
        this.amount = amount;
        this.currency = currency;
        this.providerReference = providerReference;
        this.amountRefunded = BigDecimal.ZERO;
        this.status = PaymentStatus.CAPTURED;
        this.capturedAt = Instant.now();
    }

    void registerRefund(BigDecimal refundAmount) {

        if (refundAmount == null || refundAmount.signum() <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }

        if (status == PaymentStatus.REFUNDED) {
            throw new InvalidPaymentStateException("Payment " + id + " is already fully refunded");
        }

        BigDecimal newAmountRefunded = amountRefunded.add(refundAmount);

        if (newAmountRefunded.compareTo(amount) > 0) {
            throw new RefundAmountExceededException("Refund exceeds the remaining refundable amount");
        }

        this.amountRefunded = newAmountRefunded;
        this.status = newAmountRefunded.compareTo(amount) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAmountRefunded() {
        return amountRefunded;
    }

    public BigDecimal getRemainingRefundableAmount() {
        return amount.subtract(amountRefunded);
    }

    public String getCurrency() {
        return currency;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
