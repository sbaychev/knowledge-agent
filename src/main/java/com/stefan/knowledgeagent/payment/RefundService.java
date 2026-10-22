package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.payment.exception.InvalidPaymentStateException;
import com.stefan.knowledgeagent.payment.exception.InvalidRefundStateException;
import com.stefan.knowledgeagent.payment.exception.RefundAmountExceededException;
import com.stefan.knowledgeagent.payment.exception.RefundIdempotencyConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;

@Service
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public static final String REFUND_NOT_FOUND = "REFUND_NOT_FOUND";
    public static final String REFUND = "Refund ";
    public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
    public static final String PAYMENT = "Payment ";
    public static final String DOES_NOT_EXIST = " does not exist";

    public RefundService(PaymentRepository paymentRepository, RefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    @Transactional
    public Refund requestRefund(
            Long paymentId,
            BigDecimal amount,
            String idempotencyKey,
            String reason) {

        validateRequest(amount, idempotencyKey, reason);

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                PAYMENT_NOT_FOUND,
                                PAYMENT + paymentId + DOES_NOT_EXIST
                        ));

        String normalizedKey = idempotencyKey.trim();
        String normalizedReason = reason.trim();

        Optional<Refund> existing =
                refundRepository.findByPaymentIdAndIdempotencyKey(
                        paymentId,
                        normalizedKey
                );

        if (existing.isPresent()) {

            Refund previous = existing.get();

            boolean sameRequest =
                    previous.getAmount().compareTo(amount) == 0
                            && previous.getReason().equals(normalizedReason);

            if (!sameRequest) {
                throw new RefundIdempotencyConflictException(
                        "Idempotency key was already used with different parameters"
                );
            }

            return previous;
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new InvalidPaymentStateException(
                    PAYMENT + paymentId + " is already fully refunded"
            );
        }

        BigDecimal reserved = refundRepository.sumReservedAmount(
                paymentId,
                EnumSet.of(
                        RefundStatus.REQUESTED,
                        RefundStatus.COMPLETED
                )
        );

        if (reserved == null) {
            reserved = BigDecimal.ZERO;
        }

        BigDecimal available = payment.getAmount().subtract(reserved);

        if (amount.compareTo(available) > 0) {
            throw new RefundAmountExceededException(
                    "Requested " + amount
                            + " " + payment.getCurrency()
                            + ", but only " + available
                            + " " + payment.getCurrency()
                            + " remains refundable"
            );
        }

        Refund refund = new Refund(
                payment,
                amount,
                payment.getCurrency(),
                normalizedKey,
                normalizedReason
        );

        return refundRepository.save(refund);
    }

    @Transactional
    public Refund completeRefund(Long refundId) {

        Refund initialRefund = refundRepository.findById(refundId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                REFUND_NOT_FOUND,
                                REFUND + refundId + DOES_NOT_EXIST
                        ));

        Long paymentId = initialRefund.getPayment().getId();

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                PAYMENT_NOT_FOUND,
                                PAYMENT + paymentId + DOES_NOT_EXIST
                        ));

        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                REFUND_NOT_FOUND,
                                REFUND + refundId + DOES_NOT_EXIST
                        ));

        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw new InvalidRefundStateException(
                    REFUND + refundId
                            + " cannot be completed from status "
                            + refund.getStatus()
            );
        }

        payment.registerRefund(refund.getAmount());
        refund.complete();

        // Both entities are managed by JPA.
        // Their changes commit within the same transaction.
        return refund;
    }

    @Transactional
    public Refund failRefund(Long refundId) {
        Refund initialRefund = refundRepository.findById(refundId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                REFUND_NOT_FOUND,
                                REFUND + refundId + DOES_NOT_EXIST
                        ));

        Long paymentId = initialRefund.getPayment().getId();

        paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                PAYMENT_NOT_FOUND,
                                PAYMENT + paymentId + DOES_NOT_EXIST
                        ));

        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                REFUND_NOT_FOUND,
                                REFUND + refundId + DOES_NOT_EXIST
                        ));

        refund.fail();

        // FAILED is excluded from the reserved amount query.
        return refund;
    }

    private void validateRequest(
            BigDecimal amount,
            String idempotencyKey,
            String reason) {

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero"
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must contain 1-100 characters"
            );
        }

        if (reason == null || reason.isBlank()
                || reason.trim().length() > 500) {
            throw new IllegalArgumentException(
                    "Reason must contain 1-500 characters"
            );
        }
    }

    @Transactional(readOnly = true)
    public Refund getRefund(Long refundId) {

        return refundRepository.findById(refundId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                REFUND_NOT_FOUND,
                                REFUND + refundId + DOES_NOT_EXIST
                        ));
    }

}
