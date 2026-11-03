
package com.stefan.knowledgeagent.approval;

import com.stefan.knowledgeagent.approval.exception.InvalidRefundApprovalStateException;
import com.stefan.knowledgeagent.approval.exception.RefundApprovalSelfApprovalException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "refund_approval_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refund_approval_requester_idempotency",
                        columnNames = {"requester_subject", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uk_refund_approval_refund",
                        columnNames = {"refund_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_refund_approval_requests_pending_expiry",
                        columnList = "status, expires_at, created_at"
                ),
                @Index(
                        name = "idx_refund_approval_requests_payment_created",
                        columnList = "payment_id, created_at"
                )
        }
)
public class RefundApprovalRequest {

    public static final String APPROVER_SUBJECT = "approverSubject";
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "execution_id", updatable = false)
    private UUID executionId;

    @Column(
            name = "requester_subject",
            nullable = false,
            length = 128,
            updatable = false
    )
    private String requesterSubject;

    @Column(name = "approver_subject", length = 128)
    private String approverSubject;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String idempotencyKey;

    @Column(
            nullable = false,
            precision = 19,
            scale = 4,
            updatable = false
    )
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RefundApprovalStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "refund_id")
    private Long refundId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "last_event_sequence", nullable = false)
    private int lastEventSequence;

    @Version
    @Column(nullable = false)
    private long version;

    protected RefundApprovalRequest() {
        // Required by JPA.
    }

    public RefundApprovalRequest(
            UUID id,
            Long paymentId,
            UUID executionId,
            String requesterSubject,
            String idempotencyKey,
            BigDecimal amount,
            String currency,
            String reason,
            Instant createdAt,
            Instant expiresAt
    ) {

        this.id = Objects.requireNonNull(id);
        this.paymentId = Objects.requireNonNull(paymentId);
        this.executionId = executionId;
        this.requesterSubject =
                requireSubject(requesterSubject, "requesterSubject");
        this.idempotencyKey =
                requireText(idempotencyKey, "idempotencyKey", 100);
        this.amount = Objects.requireNonNull(amount);
        this.currency = requireCurrency(currency);
        this.reason = requireText(reason, "reason", 500);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);

        if (amount.signum() <= 0) {

            throw new IllegalArgumentException("Refund approval amount must be positive.");
        }

        if (!expiresAt.isAfter(createdAt)) {

            throw new IllegalArgumentException("Approval expiry must be after creation.");
        }

        this.status = RefundApprovalStatus.PENDING;
        this.lastEventSequence = 0;
    }

    public boolean representsSameRequest(
            Long requestedPaymentId,
            BigDecimal requestedAmount,
            String requestedReason
    ) {

        return paymentId.equals(requestedPaymentId)
                && amount.compareTo(requestedAmount) == 0
                && reason.equals(requestedReason);
    }

    public int nextEventSequence() {

        lastEventSequence = Math.incrementExact(lastEventSequence);

        return lastEventSequence;
    }

    public void markSubmitted(
            String approver,
            Long submittedRefundId,
            Instant now
    ) {

        requirePending();
        requireDifferentApprover(approver);

        this.status = RefundApprovalStatus.SUBMITTED;
        this.approverSubject =
                requireSubject(approver, APPROVER_SUBJECT);
        this.refundId = Objects.requireNonNull(submittedRefundId);
        this.decidedAt = Objects.requireNonNull(now);
        this.submittedAt = now;
        this.rejectionReason = null;
    }

    public void markRejected(
            String approver,
            String rejectionReason,
            Instant now
    ) {

        requirePending();
        requireDifferentApprover(approver);

        this.status = RefundApprovalStatus.REJECTED;
        this.approverSubject =
                requireSubject(approver, APPROVER_SUBJECT);
        this.rejectionReason =
                requireText(rejectionReason, "rejectionReason", 500);
        this.decidedAt = Objects.requireNonNull(now);
    }

    public void markExpired(Instant now) {
        requirePending();
        this.status = RefundApprovalStatus.EXPIRED;
        this.decidedAt = Objects.requireNonNull(now);
    }

    private void requirePending() {

        if (status != RefundApprovalStatus.PENDING) {

            throw new InvalidRefundApprovalStateException(
                    "Refund approval " + id
                            + " cannot transition from " + status + "."
            );
        }
    }

    private void requireDifferentApprover(String approver) {

        String normalizedApprover =
                requireSubject(approver, APPROVER_SUBJECT);

        if (requesterSubject.equals(normalizedApprover)) {

            throw new RefundApprovalSelfApprovalException();
        }
    }

    private static String requireSubject(String value, String field) {
        return requireText(value, field, 128);
    }

    private static String requireText(
            String value,
            String field,
            int maxLength
    ) {

        if (value == null
                || value.isBlank()
                || value.trim().length() > maxLength) {

            throw new IllegalArgumentException(
                    field + " must contain 1-" + maxLength + " characters."
            );
        }

        return value.trim();
    }

    private static String requireCurrency(String value) {

        if (value == null || !value.matches("[A-Z]{3}")) {

            throw new IllegalArgumentException(
                    "currency must be a three-letter uppercase code."
            );
        }

        return value;
    }

    public UUID getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public String getRequesterSubject() {
        return requesterSubject;
    }

    public String getApproverSubject() {
        return approverSubject;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }

    public RefundApprovalStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getRefundId() {
        return refundId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public int getLastEventSequence() {
        return lastEventSequence;
    }

    public long getVersion() {
        return version;
    }
}
