package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.payment.exception.InvalidRefundStateException;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds", uniqueConstraints = {
        @UniqueConstraint(name = "uq_refunds_payment_idempotency", columnNames = {"payment_id", "idempotency_key"})
})
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    protected Refund() {
        // needed for JPA
    }

    public Refund(
            Payment payment,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String reason) {

        if (payment == null) {
            throw new IllegalArgumentException("payment must not be null");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        if (currency == null || !currency.equals(payment.getCurrency())) {
            throw new IllegalArgumentException("Refund currency must match payment currency");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 100) {
            throw new IllegalArgumentException("idempotencyKey must contain 1-100 characters");
        }

        if (reason == null || reason.isBlank()
                || reason.trim().length() > 500) {
            throw new IllegalArgumentException("reason must contain 1-500 characters");
        }

        this.payment = payment;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey.trim();
        this.reason = reason.trim();
        this.status = RefundStatus.REQUESTED;
    }

    public void complete() {

        requireRequested();

        this.status = RefundStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail() {

        requireRequested();

        this.status = RefundStatus.FAILED;
        this.failedAt = Instant.now();
    }

    private void requireRequested() {

        if (status != RefundStatus.REQUESTED) {
            throw new InvalidRefundStateException("Refund " + id + " cannot change from status " + status);
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReason() {
        return reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

}
