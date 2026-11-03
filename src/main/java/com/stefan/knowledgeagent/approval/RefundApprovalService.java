
package com.stefan.knowledgeagent.approval;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.approval.exception.InvalidRefundApprovalStateException;
import com.stefan.knowledgeagent.approval.exception.RefundApprovalSelfApprovalException;
import com.stefan.knowledgeagent.payment.Payment;
import com.stefan.knowledgeagent.payment.PaymentRepository;
import com.stefan.knowledgeagent.payment.PaymentStatus;
import com.stefan.knowledgeagent.payment.Refund;
import com.stefan.knowledgeagent.payment.RefundService;
import com.stefan.knowledgeagent.payment.exception.InvalidPaymentStateException;
import com.stefan.knowledgeagent.payment.exception.RefundIdempotencyConflictException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RefundApprovalService {

    private static final String NOT_FOUND = "REFUND_APPROVAL_NOT_FOUND";
    private static final String APPROVAL_IDEMPOTENCY_PREFIX = "refund-approval-";

    private final RefundApprovalRequestRepository requestRepository;
    private final RefundApprovalEventRepository eventRepository;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final Duration requestTtl;

    public RefundApprovalService(
            RefundApprovalRequestRepository requestRepository,
            RefundApprovalEventRepository eventRepository,
            PaymentRepository paymentRepository,
            RefundService refundService,
            @Value("${approval.refund.expiration:PT24H}") Duration requestTtl
    ) {

        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.paymentRepository = paymentRepository;
        this.refundService = refundService;

        if (requestTtl == null || requestTtl.isZero() || requestTtl.isNegative()) {

            throw new IllegalArgumentException(
                    "approval.refund.expiration must be positive."
            );
        }

        this.requestTtl = requestTtl;
    }

    @Transactional
    public RefundApprovalRequest requestRefund(
            String requesterSubject,
            Long paymentId,
            BigDecimal amount,
            String idempotencyKey,
            String reason,
            UUID executionId
    ) {

        String requester = requireSubject(requesterSubject);

        validateRequest(paymentId, amount, idempotencyKey, reason);

        String normalizedKey = idempotencyKey.trim();
        String normalizedReason = reason.trim();

        var existing = requestRepository.findByRequesterSubjectAndIdempotencyKey(
                requester,
                normalizedKey
        );

        if (existing.isPresent()) {

            RefundApprovalRequest previous = existing.get();

            if (!previous.representsSameRequest(
                    paymentId,
                    amount,
                    normalizedReason
            )) {

                throw new RefundIdempotencyConflictException(
                        "Approval idempotency key was already used with different parameters."
                );
            }

            return previous;
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PAYMENT_NOT_FOUND",
                        "Payment " + paymentId + " does not exist."
                ));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {

            throw new InvalidPaymentStateException(
                    "Payment " + paymentId + " is already fully refunded."
            );
        }

        Instant now = Instant.now();

        RefundApprovalRequest approval = new RefundApprovalRequest(
                UUID.randomUUID(),
                paymentId,
                executionId,
                requester,
                normalizedKey,
                amount,
                payment.getCurrency(),
                normalizedReason,
                now,
                now.plus(requestTtl)
        );

        requestRepository.saveAndFlush(approval);

        appendEvent(
                approval,
                RefundApprovalEventType.REFUND_APPROVAL_REQUESTED,
                requester,
                Map.of(
                        "paymentId", paymentId,
                        "amount", amount.toPlainString(),
                        "currency", payment.getCurrency(),
                        "expiresAt", approval.getExpiresAt().toString()
                )
        );

        return approval;
    }

    @Transactional
    public RefundApprovalRequest getForActor(
            UUID approvalId,
            String actorSubject,
            boolean approver
    ) {

        RefundApprovalRequest approval = findLocked(approvalId);

        String actor = requireSubject(actorSubject);

        if (!approver && !approval.getRequesterSubject().equals(actor)) {

            // Do not reveal whether another user's approval request exists.
            throw notFound(approvalId);
        }

        expireIfDue(approval, Instant.now());

        return approval;
    }

    @Transactional(readOnly = true)
    public Page<RefundApprovalRequest> listPending(Pageable pageable) {

        return requestRepository.findByStatusAndExpiresAtAfterOrderByCreatedAtAsc(
                RefundApprovalStatus.PENDING,
                Instant.now(),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public List<RefundApprovalEvent> getEvents(UUID approvalId) {

        if (!requestRepository.existsById(approvalId)) {

            throw notFound(approvalId);
        }

        return eventRepository
                .findByApprovalRequestIdOrderBySequenceNumberAsc(approvalId);
    }

    @Transactional
    public RefundApprovalRequest approve(
            UUID approvalId,
            String approverSubject
    ) {

        String approver = requireSubject(approverSubject);

        RefundApprovalRequest approval = findLocked(approvalId);

        if (approval.getStatus() == RefundApprovalStatus.SUBMITTED
                && approver.equals(approval.getApproverSubject())) {

            // A retry by the same approver is safe and does not submit another refund.
            return approval;
        }

        expireIfDue(approval, Instant.now());

        if (approval.getStatus() == RefundApprovalStatus.EXPIRED) {

            return approval;
        }

        if (approval.getStatus() != RefundApprovalStatus.PENDING) {

            throw invalidState(approval);
        }

        if (approval.getRequesterSubject().equals(approver)) {

            throw new RefundApprovalSelfApprovalException();
        }

        Refund refund = refundService.requestRefund(
                approval.getPaymentId(),
                approval.getAmount(),
                APPROVAL_IDEMPOTENCY_PREFIX + approval.getId(),
                approval.getReason()
        );

        Instant now = Instant.now();

        approval.markSubmitted(approver, refund.getId(), now);

        appendEvent(
                approval,
                RefundApprovalEventType.REFUND_APPROVAL_APPROVED,
                approver,
                Map.of(
                        "paymentId", approval.getPaymentId(),
                        "amount", approval.getAmount().toPlainString(),
                        "currency", approval.getCurrency()
                )
        );

        appendEvent(
                approval,
                RefundApprovalEventType.REFUND_SUBMITTED,
                approver,
                Map.of(
                        "refundId", refund.getId(),
                        "refundStatus", refund.getStatus().name()
                )
        );

        return approval;
    }

    @Transactional
    public RefundApprovalRequest reject(
            UUID approvalId,
            String approverSubject,
            String rejectionReason
    ) {

        String approver = requireSubject(approverSubject);
        String normalizedReason = requireText(
                rejectionReason,
                "rejectionReason",
                500
        );

        RefundApprovalRequest approval = findLocked(approvalId);

        if (approval.getStatus() == RefundApprovalStatus.REJECTED
                && approver.equals(approval.getApproverSubject())
                && normalizedReason.equals(approval.getRejectionReason())) {

            return approval;
        }

        expireIfDue(approval, Instant.now());

        if (approval.getStatus() == RefundApprovalStatus.EXPIRED) {

            return approval;
        }

        if (approval.getStatus() != RefundApprovalStatus.PENDING) {

            throw invalidState(approval);
        }

        Instant now = Instant.now();

        approval.markRejected(approver, normalizedReason, now);

        appendEvent(
                approval,
                RefundApprovalEventType.REFUND_APPROVAL_REJECTED,
                approver,
                Map.of("decision", "REJECTED")
        );

        return approval;
    }

    private void expireIfDue(
            RefundApprovalRequest approval,
            Instant now
    ) {

        if (approval.getStatus() == RefundApprovalStatus.PENDING
                && !approval.getExpiresAt().isAfter(now)) {

            approval.markExpired(now);

            appendEvent(
                    approval,
                    RefundApprovalEventType.REFUND_APPROVAL_EXPIRED,
                    "system",
                    Map.of("decision", "EXPIRED")
            );
        }
    }

    private void appendEvent(
            RefundApprovalRequest approval,
            RefundApprovalEventType type,
            String actorSubject,
            Map<String, Object> payload
    ) {

        int sequence = approval.nextEventSequence();

        eventRepository.save(new RefundApprovalEvent(
                approval.getId(),
                sequence,
                type,
                actorSubject,
                payload
        ));

        requestRepository.save(approval);
    }

    private RefundApprovalRequest findLocked(UUID approvalId) {

        Objects.requireNonNull(approvalId, "approvalId must not be null");

        return requestRepository.findLockedById(approvalId)
                .orElseThrow(() -> notFound(approvalId));
    }

    private static ResourceNotFoundException notFound(UUID approvalId) {

        return new ResourceNotFoundException(
                NOT_FOUND,
                "Refund approval " + approvalId + " does not exist."
        );
    }

    private static InvalidRefundApprovalStateException invalidState(
            RefundApprovalRequest approval
    ) {

        return new InvalidRefundApprovalStateException(
                "Refund approval " + approval.getId()
                        + " cannot be decided from status "
                        + approval.getStatus() + "."
        );
    }

    private static void validateRequest(
            Long paymentId,
            BigDecimal amount,
            String idempotencyKey,
            String reason
    ) {

        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException("paymentId must be positive.");
        }

        if (amount == null || amount.signum() <= 0
                || amount.scale() > 4
                || amount.precision() > 19) {

            throw new IllegalArgumentException(
                    "Refund amount must be positive and fit NUMERIC(19,4)."
            );
        }

        requireText(idempotencyKey, "idempotencyKey", 100);
        requireText(reason, "reason", 500);
    }

    private static String requireSubject(String subject) {
        return requireText(subject, "authenticated subject", 128);
    }

    private static String requireText(
            String value,
            String field,
            int maxLength
    ) {

        if (value == null || value.isBlank() || value.trim().length() > maxLength) {

            throw new IllegalArgumentException(
                    field + " must contain 1-" + maxLength + " characters."
            );
        }
        return value.trim();
    }
}
