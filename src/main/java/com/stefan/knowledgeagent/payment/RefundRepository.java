package com.stefan.knowledgeagent.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentIdAndIdempotencyKey(
            Long paymentId,
            String idempotencyKey
    );

    @Query("""
                select sum(r.amount)
                from Refund r
                where r.payment.id = :paymentId
                    and r.status in :statuses
            """)
    BigDecimal sumReservedAmount(
            @Param("paymentId") Long paymentId,
            @Param("statuses") Collection<RefundStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :refundId")
    Optional<Refund> findByIdForUpdate(
            @Param("refundId") Long refundId
    );

}
